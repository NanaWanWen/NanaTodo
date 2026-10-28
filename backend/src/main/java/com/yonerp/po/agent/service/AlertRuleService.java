package com.yonerp.po.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yonerp.po.agent.dto.AgentAlertVO;
import com.yonerp.po.agent.entity.AgentAlert;
import com.yonerp.po.agent.entity.AgentAlertAction;
import com.yonerp.po.agent.entity.AgentAlertRule;
import com.yonerp.po.agent.mapper.AgentAlertActionMapper;
import com.yonerp.po.agent.mapper.AgentAlertMapper;
import com.yonerp.po.agent.mapper.AgentAlertRuleMapper;
import com.yonerp.po.agent.rule.AlertCandidate;
import com.yonerp.po.agent.rule.AlertRuleEvaluator;
import com.yonerp.po.ap.entity.PoApSettlement;
import com.yonerp.po.ap.mapper.ApSettlementMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 预警规则服务
 * <ul>
 *   <li>加载启用的规则并路由到对应评估器</li>
 *   <li>去重（生命周期去重：同一规则+同一业务对象的未处理/未忽略预警只生成一次）</li>
 *   <li>持久化并触发通知</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertRuleService {

    private final AgentAlertRuleMapper ruleMapper;
    private final AgentAlertMapper alertMapper;
    private final AgentAlertActionMapper alertActionMapper;
    private final ApSettlementMapper settlementMapper;
    private final List<AlertRuleEvaluator> evaluators;
    private final AlertNotifyService notifyService;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    /** 扫描间隔（分钟），用于并发锁 TTL 联动，避免锁早于扫描周期过期导致重复生成 */
    @org.springframework.beans.factory.annotation.Value("${yonerp.agent.alert-scan-interval-minutes:5}")
    private int scanIntervalMinutes;

    /**
     * 执行所有启用的规则扫描，返回新生成的预警数量
     */
    public int scanAll() {
        int total = 0;
        List<AgentAlertRule> rules = ruleMapper.selectList(
                new LambdaQueryWrapper<AgentAlertRule>()
                        .eq(AgentAlertRule::getEnabled, 1)
        );
        if (rules.isEmpty()) {
            log.warn("No enabled alert rules found, skip scan");
            return 0;
        }
        for (AgentAlertRule rule : rules) {
            total += scanRule(rule);
        }
        closeStaleMismatchAlerts();
        return total;
    }

    /**
     * 关闭失效的差异预警（“处理中”闭环的关键一环）
     * <p>
     * 结算单已登记处理进入等待态（WAITING_RECEIPT/WAITING_INVOICE/WAITING_ORDER），
     * 或已被放行/匹配/关闭时，历史上生成的未处理“发票不符”预警继续挂着会重复打扰。
     * 这里自动关闭并留痕；若后续重新匹配后差异仍然存在，下次扫描会重新生成。
     */
    private void closeStaleMismatchAlerts() {
        try {
            List<PoApSettlement> resolved = settlementMapper.selectList(
                    new LambdaQueryWrapper<PoApSettlement>()
                            .in(PoApSettlement::getMatchStatus,
                                    "MATCHED", "CLOSED",
                                    "WAITING_RECEIPT", "WAITING_INVOICE", "WAITING_ORDER")
                            .eq(PoApSettlement::getDeleted, 0)
            );
            if (resolved.isEmpty()) {
                return;
            }
            List<Long> refIds = resolved.stream().map(PoApSettlement::getId).toList();
            List<AgentAlert> stale = alertMapper.selectList(
                    new LambdaQueryWrapper<AgentAlert>()
                            .eq(AgentAlert::getRuleCode, "INVOICE_MISMATCH")
                            .eq(AgentAlert::getHandled, 0)
                            .eq(AgentAlert::getIgnored, 0)
                            .in(AgentAlert::getRefId, refIds)
            );
            for (AgentAlert alert : stale) {
                alert.setHandled(1);
                alert.setHandleBy("system");
                alert.setHandleTime(LocalDateTime.now());
                alert.setHandleNote("差异已登记处理或已匹配，预警自动关闭；重新匹配后仍有差异将再次生成");
                alertMapper.updateById(alert);
                AgentAlertAction action = new AgentAlertAction();
                action.setAlertId(alert.getAlertId());
                action.setAction("HANDLE");
                action.setOperator("system");
                action.setNote(alert.getHandleNote());
                action.setCreateTime(LocalDateTime.now());
                alertActionMapper.insert(action);
                // 清除去重锁，使「重新匹配后差异再现」时能在下一轮扫描立即重新生成预警（否则要等锁 TTL 过期）
                redisTemplate.delete("alert:dedup:" + alert.getRuleCode() + ":" + alert.getRefId());
            }
            if (!stale.isEmpty()) {
                log.info("关闭失效差异预警 {} 条", stale.size());
            }
        } catch (Exception e) {
            log.warn("关闭失效差异预警失败: {}", e.getMessage());
        }
    }

    /**
     * 执行单个规则扫描
     */
    public int scanRule(AgentAlertRule rule) {
        AlertRuleEvaluator evaluator = findEvaluator(rule.getRuleCode());
        if (evaluator == null) {
            log.warn("No evaluator found for rule: {}", rule.getRuleCode());
            return 0;
        }
        List<AlertCandidate> candidates = evaluator.evaluate(rule);
        if (candidates.isEmpty()) return 0;

        int generated = 0;
        for (AlertCandidate c : candidates) {
            if (isDuplicate(c.getRule().getRuleCode(), c.getRefId())) continue;
            AgentAlertVO alert = createAlert(c);
            storeAlert(alert);
            notifyService.notify(alert);
            generated++;
        }
        return generated;
    }

    // ==================== 规则 CRUD ====================

    public List<AgentAlertRule> listAll() {
        return ruleMapper.selectList(null);
    }

    public AgentAlertRule create(AgentAlertRule rule) {
        rule.setCreateTime(LocalDateTime.now());
        ruleMapper.insert(rule);
        return rule;
    }

    public AgentAlertRule update(Long id, AgentAlertRule rule) {
        rule.setId(id);
        rule.setUpdateTime(LocalDateTime.now());
        AgentAlertRule before = ruleMapper.selectById(id);
        // 规则关键配置变更或被禁用 → 清除该规则名下历史预警（未修改的规则不受影响）
        boolean needPurge = before != null
                && (isRuleEffectiveChanged(before, rule) || isDisabled(rule));
        if (needPurge) {
            int removed = removeAlertsByRule(id, before.getRuleCode());
            log.info("Alert rule [{}] changed or disabled, purged {} alerts",
                    before.getRuleCode(), removed);
        }
        ruleMapper.updateById(rule);
        // 规则仍启用 → 按新配置立即重新评估，实现「改 A 则 A 的预警按新规则重建」
        if (needPurge && !isDisabled(rule)) {
            AgentAlertRule merged = mergeForRebuild(before, rule);
            int generated = scanRule(merged);
            log.info("Alert rule [{}] rebuilt with new config, generated {} alerts",
                    merged.getRuleCode(), generated);
        }
        return rule;
    }

    /**
     * 合并出用于重建评估的规则对象：以数据库原记录为基础，仅覆盖本次实际传入的字段。
     * <p>
     * 目的：调用方可能只提交阈值（如仅改 threshold），若不合并，ruleCode 缺失会导致
     * 无法定位评估器、重建不执行。
     * </p>
     */
    private AgentAlertRule mergeForRebuild(AgentAlertRule before, AgentAlertRule patch) {
        AgentAlertRule merged = new AgentAlertRule();
        merged.setId(before.getId());
        merged.setRuleCode(before.getRuleCode());
        merged.setRuleName(before.getRuleName());
        merged.setAlertType(before.getAlertType());
        merged.setBizDomain(before.getBizDomain());
        merged.setOwnerRole(before.getOwnerRole());
        merged.setNotifyChannel(before.getNotifyChannel());
        merged.setEnabled(patch.getEnabled() != null ? patch.getEnabled() : before.getEnabled());
        merged.setThreshold(patch.getThreshold() != null ? patch.getThreshold() : before.getThreshold());
        merged.setThresholdUnit(
                patch.getThresholdUnit() != null ? patch.getThresholdUnit() : before.getThresholdUnit());
        merged.setLevel(patch.getLevel() != null ? patch.getLevel() : before.getLevel());
        return merged;
    }

    public void delete(Long id) {
        AgentAlertRule before = ruleMapper.selectById(id);
        if (before != null) {
            int removed = removeAlertsByRule(id, before.getRuleCode());
            log.info("Alert rule [{}] deleted, purged {} alerts", before.getRuleCode(), removed);
        }
        ruleMapper.deleteById(id);
    }

    /**
     * 判断规则的关键配置是否发生实质变更（阈值 / 阈值单位 / 级别 / 启用状态）
     */
    private boolean isRuleEffectiveChanged(AgentAlertRule before, AgentAlertRule after) {
        // 仅比较「本次实际传入且发生变化」的字段，避免未传字段被误判为变更而误清除预警
        if (after.getThreshold() != null && !Objects.equals(before.getThreshold(), after.getThreshold())) {
            return true;
        }
        if (after.getThresholdUnit() != null
                && !Objects.equals(before.getThresholdUnit(), after.getThresholdUnit())) {
            return true;
        }
        if (after.getLevel() != null && !Objects.equals(before.getLevel(), after.getLevel())) {
            return true;
        }
        return after.getEnabled() != null && !Objects.equals(before.getEnabled(), after.getEnabled());
    }

    private boolean isDisabled(AgentAlertRule rule) {
        return rule.getEnabled() != null && rule.getEnabled() == 0;
    }

    /**
     * 清除指定规则名下的全部预警（兼容历史数据 ruleId 为空的情况，同时按 ruleCode 兜底）
     */
    private int removeAlertsByRule(Long ruleId, String ruleCode) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AgentAlert> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        if (ruleId != null) {
            wrapper.eq(AgentAlert::getRuleId, ruleId);
            if (ruleCode != null) {
                wrapper.or().eq(AgentAlert::getRuleCode, ruleCode);
            }
        } else if (ruleCode != null) {
            wrapper.eq(AgentAlert::getRuleCode, ruleCode);
        } else {
            return 0;
        }
        int removed = alertMapper.delete(wrapper);
        // 预警已清除，对应的去重锁也应释放，否则重建阶段会被锁误判为「重复」而生成不出来
        clearDedupLocks(ruleCode);
        return removed;
    }

    /**
     * 清除指定规则名下的 Redis 去重锁
     * <p>
     * 去重锁的作用是防止并发扫描重复插入；当规则变更已清除历史预警时，
     * 同名业务对象应允许被重新评估与提醒，故需同步释放锁。
     * </p>
     */
    private void clearDedupLocks(String ruleCode) {
        if (ruleCode == null) {
            return;
        }
        try {
            Set<String> keys = redisTemplate.keys("alert:dedup:" + ruleCode + ":*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("Cleared {} dedup locks for rule [{}]", keys.size(), ruleCode);
            }
        } catch (Exception e) {
            log.warn("Clear dedup locks failed for rule [{}]: {}", ruleCode, e.getMessage());
        }
    }

    private AlertRuleEvaluator findEvaluator(String ruleCode) {
        for (AlertRuleEvaluator e : evaluators) {
            if (e.supports(ruleCode)) return e;
        }
        return null;
    }

    /**
     * 生命周期去重：只对「未处理且未忽略」的预警参与去重。
     * 已处理或已忽略的预警不计入，允许业务问题再现时重新生成预警。
     */
    private boolean isDuplicate(String ruleCode, Long refId) {
        // ① 数据库权威去重：同一规则 + 同一业务对象存在未处理且未忽略的预警 → 不再重复提醒
        Long count = alertMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AgentAlert>()
                        .eq(AgentAlert::getRuleCode, ruleCode)
                        .eq(AgentAlert::getRefId, refId)
                        .eq(AgentAlert::getHandled, 0)
                        .eq(AgentAlert::getIgnored, 0)
        );
        if (count != null && count > 0) {
            return true;
        }
        // ② 无历史预警时，Redis 仅作并发防重；TTL 取扫描间隔的 3 倍，避免锁提前过期
        String lockKey = "alert:dedup:" + ruleCode + ":" + refId;
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", Duration.ofMinutes(Math.max(1, scanIntervalMinutes) * 3L));
        return !Boolean.TRUE.equals(acquired);
    }

    private AgentAlertVO createAlert(AlertCandidate c) {
        AgentAlertVO alert = new AgentAlertVO();
        alert.setAlertId(UUID.randomUUID().toString());
        alert.setAlertType(c.getRule().getAlertType());
        alert.setBizDomain(c.getRule().getBizDomain());
        alert.setRuleCode(c.getRule().getRuleCode());
        alert.setRuleId(c.getRule().getId());
        alert.setLevel(c.getLevel() != null ? c.getLevel() : c.getRule().getLevel());
        alert.setTitle(c.getTitle());
        alert.setContent(c.getDetail() + "\n【处理建议】" + defaultAdvice(c.getRule().getAlertType()));
        alert.setRefId(c.getRefId());
        alert.setRefNo(c.getRefNo());
        alert.setRefType(c.getRefType());
        alert.setActionPath(c.getActionPath());
        alert.setCreateTime(LocalDateTime.now());
        return alert;
    }

    private String defaultAdvice(String alertType) {
        return switch (alertType) {
            case "OVERDUE_ORDER" -> "建议：打开关联订单，联系当前审批节点或供应商确认延期原因；更新预计到货日期并记录跟进结果。";
            case "LOW_STOCK" -> "建议：核对可用库存与未到货采购单，确认需求后创建补货订单；不要仅依据预警自动下单。";
            case "PRICE_ABNORMAL" -> "建议：核实价格变动原因，是否为供应商调价或录入错误，必要时发起比价流程。";
            case "APPROVAL_TIMEOUT" -> "建议：打开关联订单查看审批进度，提醒当前节点审批人及时处理，避免采购计划延误。";
            case "OVERSTOCK" -> "建议：评估物料周转率，优先消化呆滞库存；调整安全库存设定，避免长期占用资金。";
            case "INVOICE_MISMATCH" -> "建议：核对订单、收货单、发票三单明细，确认差异原因（数量/单价/税率），必要时联系供应商更正发票或发起差异处理流程。";
            case "PAYMENT_OVERDUE" -> "建议：核对合同付款条款，确认发票是否已到账；及时安排付款，维护供应商关系。";
            case "ACCRUAL_PENDING" -> "建议：核实暂估对应的收货单是否已经收到发票，催促供应商尽快开票；长期未回冲需排查发票丢失或供应商异常。";
            default -> "建议：打开关联业务单据核实数据，确认后按系统流程处理。";
        };
    }

    private void storeAlert(AgentAlertVO vo) {
        try {
            AgentAlert entity = toEntity(vo);
            alertMapper.insert(entity);
            log.info("Alert stored: id={} type={}", vo.getAlertId(), vo.getAlertType());
        } catch (Exception e) {
            log.error("Failed to store alert: {}", e.getMessage());
        }
    }

    private AgentAlert toEntity(AgentAlertVO vo) {
        AgentAlert e = new AgentAlert();
        e.setAlertId(vo.getAlertId());
        e.setAlertType(vo.getAlertType());
        e.setBizDomain(vo.getBizDomain());
        e.setRuleCode(vo.getRuleCode());
        e.setRuleId(vo.getRuleId());
        e.setLevel(vo.getLevel());
        e.setTitle(vo.getTitle());
        e.setContent(vo.getContent());
        e.setRefId(vo.getRefId());
        e.setRefNo(vo.getRefNo());
        e.setRefType(vo.getRefType());
        e.setActionPath(vo.getActionPath());
        e.setHandled(0);
        e.setIgnored(0);
        e.setReadFlag(0);
        e.setNotified(0);
        e.setCreateTime(vo.getCreateTime() != null ? vo.getCreateTime() : LocalDateTime.now());
        return e;
    }
}
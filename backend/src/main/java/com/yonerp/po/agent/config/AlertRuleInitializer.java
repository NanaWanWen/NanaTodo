package com.yonerp.po.agent.config;

import com.yonerp.po.agent.entity.AgentAlertRule;
import com.yonerp.po.agent.mapper.AgentAlertRuleMapper;
import com.yonerp.po.agent.rule.AlertRuleEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 预警规则初始化器
 * <p>
 * 启动时检查内置规则是否存在，不存在则自动插入（含默认阈值/单位/级别）。
 * 已存在的规则不会覆盖，业务角色可在「预警规则配置」页面自助调整。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertRuleInitializer {

    private final AgentAlertRuleMapper ruleMapper;
    private final List<AlertRuleEvaluator> evaluators;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        for (AlertRuleEvaluator evaluator : evaluators) {
            long count = ruleMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AgentAlertRule>()
                            .eq(AgentAlertRule::getRuleCode, evaluator.ruleCode())
            );
            if (count > 0) {
                log.info("Alert rule already exists: {}", evaluator.ruleCode());
                continue;
            }
            AgentAlertRule rule = new AgentAlertRule();
            rule.setRuleCode(evaluator.ruleCode());
            rule.setRuleName(evaluator.defaultRuleName());
            rule.setBizDomain(evaluator.bizDomain());
            rule.setAlertType(evaluator.alertType());
            rule.setEnabled(1);
            rule.setNotifyChannel("INAPP");
            rule.setCreateTime(LocalDateTime.now());
            applyDefaults(rule);
            ruleMapper.insert(rule);
            log.info("Initialized built-in alert rule: {} (threshold={} {})",
                    rule.getRuleCode(), rule.getThreshold(), rule.getThresholdUnit());
        }
    }

    /**
     * 按规则编码预置默认阈值、单位与级别，供评估器消费
     */
    private void applyDefaults(AgentAlertRule rule) {
        switch (rule.getRuleCode()) {
            case "OVERDUE_ORDER" -> {
                rule.setThreshold(new BigDecimal("0"));
                rule.setThresholdUnit("DAY");
                rule.setLevel("WARNING");
                rule.setRemark("已审批订单预计到货日期已过即预警；逾期超阈值 2 倍升级为严重。");
                rule.setOwnerRole("BUYER");
            }
            case "APPROVAL_TIMEOUT" -> {
                rule.setThreshold(new BigDecimal("24"));
                rule.setThresholdUnit("HOUR");
                rule.setLevel("WARNING");
                rule.setRemark("审批中订单滞留超过阈值小时数即预警；超 2 倍升级为严重。");
                rule.setOwnerRole("PURCHASE_MANAGER");
            }
            case "PRICE_ABNORMAL" -> {
                rule.setThreshold(new BigDecimal("10"));
                rule.setThresholdUnit("PERCENT");
                rule.setLevel("WARNING");
                rule.setRemark("订单明细单价与物料参考价偏差超过阈值即预警；超 2 倍升级为严重。");
                rule.setOwnerRole("BUYER");
            }
            case "LOW_STOCK" -> {
                rule.setThreshold(new BigDecimal("1.0"));
                rule.setThresholdUnit("MULTIPLE");
                rule.setLevel("WARNING");
                rule.setRemark("库存低于安全库存×阈值系数即预警；库存为 0 升级为严重。");
                rule.setOwnerRole("BUYER");
            }
            case "OVERSTOCK" -> {
                rule.setThreshold(new BigDecimal("3"));
                rule.setThresholdUnit("MULTIPLE");
                rule.setLevel("WARNING");
                rule.setRemark("库存超过安全库存×阈值倍数视为超储，请评估周转率。");
                rule.setOwnerRole("BUYER");
            }
            case "INVOICE_MISMATCH" -> {
                rule.setThreshold(new BigDecimal("0"));
                rule.setThresholdUnit("AMOUNT");
                rule.setLevel("WARNING");
                rule.setRemark("三单匹配存在真差异（HOLD/PARTIAL_QTY/PARTIAL_AMOUNT）即预警；差异超容差 2 倍升级为严重。");
                rule.setOwnerRole("PURCHASE_MANAGER");
            }
            case "PAYMENT_OVERDUE" -> {
                rule.setThreshold(new BigDecimal("7"));
                rule.setThresholdUnit("DAY");
                rule.setLevel("WARNING");
                rule.setRemark("待付款单滞留超过阈值天数即预警；超 2 倍升级为严重。");
                rule.setOwnerRole("PURCHASE_MANAGER");
            }
            case "ACCRUAL_PENDING" -> {
                rule.setThreshold(new BigDecimal("30"));
                rule.setThresholdUnit("DAY");
                rule.setLevel("WARNING");
                rule.setRemark("暂估应付超过阈值天数未回冲即预警；超 2 倍升级为严重。");
                rule.setOwnerRole("PURCHASE_MANAGER");
            }
            default -> {
                rule.setLevel("WARNING");
            }
        }
    }
}
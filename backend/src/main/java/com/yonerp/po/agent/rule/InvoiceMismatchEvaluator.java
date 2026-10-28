package com.yonerp.po.agent.rule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yonerp.po.agent.entity.AgentAlertRule;
import com.yonerp.po.ap.entity.PoApSettlement;
import com.yonerp.po.ap.mapper.ApSettlementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 三单匹配差异规则评估器（用友式）
 * <p>
 * 判断口径：只报「真差异」，即 match_status IN ('HOLD', 'PARTIAL_QTY', 'PARTIAL_AMOUNT')
 * 且差异金额超过阈值（threshold，单位 AMOUNT，缺省 0 表示任何差异均触发）的记录。
 * UNMATCHED（票未到）不视为差异，不触发预警。
 * 差异超过容差 2 倍时升级为 CRITICAL，用于提示财务人员核查发票/收货/订单三单差异。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class InvoiceMismatchEvaluator implements AlertRuleEvaluator {

    private static final BigDecimal DEFAULT_THRESHOLD = BigDecimal.ZERO;
    private static final int SCAN_LIMIT = 200;

    private final ApSettlementMapper settlementMapper;

    @Override
    public String ruleCode() {
        return "INVOICE_MISMATCH";
    }

    @Override
    public String bizDomain() {
        return "FINANCE";
    }

    @Override
    public String alertType() {
        return "INVOICE_MISMATCH";
    }

    @Override
    public String defaultRuleName() {
        return "三单匹配差异预警";
    }

    @Override
    public List<AlertCandidate> evaluate(AgentAlertRule rule) {
        List<AlertCandidate> candidates = new ArrayList<>();
        BigDecimal threshold = rule.getThreshold() != null ? rule.getThreshold() : DEFAULT_THRESHOLD;
        try {
            List<PoApSettlement> settlements = settlementMapper.selectList(
                    new LambdaQueryWrapper<PoApSettlement>()
                            .in(PoApSettlement::getMatchStatus, "HOLD", "PARTIAL_QTY", "PARTIAL_AMOUNT")
                            .eq(PoApSettlement::getDeleted, 0)
                            .last("LIMIT " + SCAN_LIMIT)
            );
            for (PoApSettlement s : settlements) {
                BigDecimal diff = s.getDiffAmount() != null ? s.getDiffAmount() : BigDecimal.ZERO;
                BigDecimal qtyDiff = s.getQtyDiff() != null ? s.getQtyDiff() : BigDecimal.ZERO;
                if (threshold.compareTo(BigDecimal.ZERO) > 0
                        && diff.abs().compareTo(threshold) <= 0) continue;

                String statusText = switch (s.getMatchStatus()) {
                    case "HOLD" -> "挂起待处理";
                    case "PARTIAL_QTY" -> "数量差异";
                    case "PARTIAL_AMOUNT" -> "金额差异";
                    default -> "差异";
                };
                // 差异描述：金额差异为主；纯数量差异（金额为 0）时突出短收数量，避免出现"差异金额 0.00"的误读
                String diffDesc;
                if (diff.abs().compareTo(BigDecimal.ZERO) > 0) {
                    diffDesc = String.format("差异金额 %s 元", diff.toPlainString());
                } else if (qtyDiff.abs().compareTo(BigDecimal.ZERO) > 0) {
                    diffDesc = String.format("数量短收 %s（金额暂无差异，待补收后结算）", qtyDiff.toPlainString());
                } else {
                    diffDesc = "存在待处理差异";
                }
                String thresholdDesc = threshold.compareTo(BigDecimal.ZERO) > 0
                        ? String.format("（预警阈值 %s）", threshold.toPlainString())
                        : "";
                String detail = String.format(
                        "应付对账记录（订单 %s，结算单号 %s）当前状态 %s，%s%s，差异类型 %s，请核对订单/收货/发票三单明细。",
                        s.getOrderId(), s.getMatchNo(), statusText, diffDesc, thresholdDesc,
                        s.getDiffType() != null ? s.getDiffType() : "未知"
                );
                AlertCandidate candidate = AlertCandidate.of(
                        rule, s.getId(), String.valueOf(s.getOrderId()),
                        "INVOICE", "/ap-settlements",
                        "三单匹配差异预警", detail
                );
                BigDecimal tolerance = s.getTolerance() != null ? s.getTolerance() : BigDecimal.ZERO;
                if (tolerance.compareTo(BigDecimal.ZERO) > 0
                        && diff.abs().compareTo(tolerance.multiply(BigDecimal.valueOf(2))) > 0) {
                    candidate.setLevel("CRITICAL");
                }
                candidates.add(candidate);
            }
        } catch (Exception e) {
            // 评估失败不影响其他规则
        }
        return candidates;
    }
}

package com.yonerp.po.agent.rule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yonerp.po.agent.entity.AgentAlertRule;
import com.yonerp.po.payment.entity.PoPayment;
import com.yonerp.po.payment.mapper.PaymentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 付款逾期规则评估器
 * <p>
 * 判断口径（参考用友应付账款逾期预警）：付款单处于 PENDING 状态，且自创建时间起已超过阈值天数
 * （threshold，单位 DAY，缺省 7 天）仍未执行付款。超过阈值 2 倍升级为 CRITICAL，
 * 用于提醒财务人员及时安排付款，避免供应商账期纠纷。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class PaymentOverdueEvaluator implements AlertRuleEvaluator {

    private static final BigDecimal DEFAULT_THRESHOLD_DAYS = new BigDecimal("7");
    private static final int SCAN_LIMIT = 200;

    private final PaymentMapper paymentMapper;

    @Override
    public String ruleCode() {
        return "PAYMENT_OVERDUE";
    }

    @Override
    public String bizDomain() {
        return "FINANCE";
    }

    @Override
    public String alertType() {
        return "PAYMENT_OVERDUE";
    }

    @Override
    public String defaultRuleName() {
        return "付款逾期预警";
    }

    @Override
    public List<AlertCandidate> evaluate(AgentAlertRule rule) {
        List<AlertCandidate> candidates = new ArrayList<>();
        BigDecimal thresholdDays = rule.getThreshold() != null ? rule.getThreshold() : DEFAULT_THRESHOLD_DAYS;
        LocalDateTime now = LocalDateTime.now();
        try {
            List<PoPayment> payments = paymentMapper.selectList(
                    new LambdaQueryWrapper<PoPayment>()
                            .eq(PoPayment::getStatus, "PENDING")
                            .eq(PoPayment::getDeleted, 0)
                            .last("LIMIT " + SCAN_LIMIT)
            );
            for (PoPayment p : payments) {
                if (p.getCreateTime() == null) continue;
                long pendingDays = ChronoUnit.DAYS.between(p.getCreateTime(), now);
                if (BigDecimal.valueOf(pendingDays).compareTo(thresholdDays) <= 0) continue;

                String detail = String.format(
                        "付款单 %s（金额 %s）自 %s 创建至今已滞留 %d 天（阈值 %s 天），请及时安排付款。",
                        p.getPaymentNo(), p.getPaymentAmount() != null ? p.getPaymentAmount() : "-",
                        p.getCreateTime().toString().replace('T', ' '), pendingDays, thresholdDays.toPlainString()
                );
                AlertCandidate candidate = AlertCandidate.of(
                        rule, p.getId(), p.getPaymentNo(),
                        "PAYMENT", "/payments",
                        "付款逾期预警", detail
                );
                if (BigDecimal.valueOf(pendingDays)
                        .compareTo(thresholdDays.multiply(BigDecimal.valueOf(2))) > 0) {
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
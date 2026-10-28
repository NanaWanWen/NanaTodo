package com.yonerp.po.agent.rule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yonerp.po.agent.entity.AgentAlertRule;
import com.yonerp.po.order.entity.PoOrder;
import com.yonerp.po.order.mapper.PoOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 审批超时规则评估器
 * <p>
 * 判断口径（参考用友审批时效预警）：订单处于 APPROVING 状态，且自最近提交审批时间起
 * 已超过阈值小时数（threshold，单位 HOUR，缺省 24 小时）仍未完成审批。
 * 超过阈值 2 倍升级为 CRITICAL，用于提醒审批人处理积压单据。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class ApprovalTimeoutEvaluator implements AlertRuleEvaluator {

    /** 审批超时阈值（小时），缺省 24 小时 */
    private static final BigDecimal DEFAULT_THRESHOLD_HOURS = new BigDecimal("24");
    private static final int SCAN_LIMIT = 500;

    private final PoOrderMapper orderMapper;

    @Override
    public String ruleCode() {
        return "APPROVAL_TIMEOUT";
    }

    @Override
    public String bizDomain() {
        return "PURCHASE";
    }

    @Override
    public String alertType() {
        return "APPROVAL_TIMEOUT";
    }

    @Override
    public String defaultRuleName() {
        return "审批超时预警";
    }

    @Override
    public List<AlertCandidate> evaluate(AgentAlertRule rule) {
        List<AlertCandidate> candidates = new ArrayList<>();
        BigDecimal thresholdHours = rule.getThreshold() != null ? rule.getThreshold() : DEFAULT_THRESHOLD_HOURS;
        LocalDateTime now = LocalDateTime.now();
        try {
            List<PoOrder> orders = orderMapper.selectList(
                    new LambdaQueryWrapper<PoOrder>()
                            .eq(PoOrder::getStatus, "APPROVING")
                            .isNotNull(PoOrder::getSubmitTime)
                            .eq(PoOrder::getDeleted, 0)
                            .last("LIMIT " + SCAN_LIMIT)
            );
            for (PoOrder order : orders) {
                long pendingHours = ChronoUnit.HOURS.between(order.getSubmitTime(), now);
                if (BigDecimal.valueOf(pendingHours).compareTo(thresholdHours) <= 0) continue;

                String nodeText = order.getCurrentNode() != null
                        ? "当前处于第 " + order.getCurrentNode() + " 级审批节点" : "审批中";
                String detail = String.format(
                        "订单 %s 自 %s 提交审批后已滞留 %d 小时（阈值 %s 小时），%s，请审批人及时处理。",
                        order.getOrderNo(), order.getSubmitTime().toString().replace('T', ' '),
                        pendingHours, thresholdHours.toPlainString(), nodeText
                );
                AlertCandidate candidate = AlertCandidate.of(
                        rule, order.getId(), order.getOrderNo(),
                        "ORDER", "/orders/" + order.getId(),
                        "审批超时预警", detail
                );
                if (BigDecimal.valueOf(pendingHours)
                        .compareTo(thresholdHours.multiply(BigDecimal.valueOf(2))) > 0) {
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

package com.yonerp.po.agent.rule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yonerp.po.agent.entity.AgentAlertRule;
import com.yonerp.po.order.entity.PoOrder;
import com.yonerp.po.order.mapper.PoOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 逾期订单规则评估器
 * <p>
 * 判断口径（参考用友采购订单逾期预警）：已审批（APPROVED）但预计到货日期已过、尚未收货的订单。
 * 逾期天数超过阈值（threshold，单位 DAY）才命中；逾期超过阈值 2 倍时升级为 CRITICAL。
 * local（阈值）由「预警规则配置」页面自助维护，无需改代码。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class OverdueOrderEvaluator implements AlertRuleEvaluator {

    /** 触发预警的逾期天数容忍值（阈值缺省时使用） */
    private static final BigDecimal DEFAULT_TOLERANCE_DAYS = BigDecimal.ZERO;

    /** 单次扫描最多检查的订单数，防止数据量过大拖慢扫描 */
    private static final int SCAN_LIMIT = 500;

    private final PoOrderMapper orderMapper;

    @Override
    public String ruleCode() {
        return "OVERDUE_ORDER";
    }

    @Override
    public String bizDomain() {
        return "PURCHASE";
    }

    @Override
    public String alertType() {
        return "OVERDUE_ORDER";
    }

    @Override
    public String defaultRuleName() {
        return "逾期订单预警";
    }

    @Override
    public List<AlertCandidate> evaluate(AgentAlertRule rule) {
        List<AlertCandidate> candidates = new ArrayList<>();
        LocalDate today = LocalDate.now();
        BigDecimal toleranceDays = rule.getThreshold() != null ? rule.getThreshold() : DEFAULT_TOLERANCE_DAYS;
        try {
            List<PoOrder> orders = orderMapper.selectList(
                    new LambdaQueryWrapper<PoOrder>()
                            .eq(PoOrder::getStatus, "APPROVED")
                            .isNotNull(PoOrder::getExpectDate)
                            .lt(PoOrder::getExpectDate, today)
                            .eq(PoOrder::getDeleted, 0)
                            .orderByAsc(PoOrder::getExpectDate)
                            .last("LIMIT " + SCAN_LIMIT)
            );
            for (PoOrder order : orders) {
                long overdueDays = ChronoUnit.DAYS.between(order.getExpectDate(), today);
                // 未超过容忍阈值，不触发
                if (BigDecimal.valueOf(overdueDays).compareTo(toleranceDays) <= 0) continue;

                String detail = String.format(
                        "订单 %s 预计到货日期 %s，已逾期 %d 天仍未收货，请及时催货或更新到货计划。",
                        order.getOrderNo(), order.getExpectDate(), overdueDays
                );
                AlertCandidate candidate = AlertCandidate.of(
                        rule, order.getId(), order.getOrderNo(),
                        "ORDER", "/orders/" + order.getId(),
                        "逾期订单预警", detail
                );
                // 逾期超过阈值 2 倍升级为严重
                if (toleranceDays.compareTo(BigDecimal.ZERO) > 0
                        && BigDecimal.valueOf(overdueDays).compareTo(toleranceDays.multiply(BigDecimal.valueOf(2))) > 0) {
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

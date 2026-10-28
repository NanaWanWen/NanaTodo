package com.yonerp.po.agent.rule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yonerp.po.agent.entity.AgentAlertRule;
import com.yonerp.po.base.entity.Material;
import com.yonerp.po.base.mapper.MaterialMapper;
import com.yonerp.po.order.entity.PoOrder;
import com.yonerp.po.order.entity.PoOrderItem;
import com.yonerp.po.order.mapper.PoOrderItemMapper;
import com.yonerp.po.order.mapper.PoOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 价格异常规则评估器
 * <p>
 * 判断口径（参考用友采购价格预警）：订单明细不含税单价与物料参考价（base_material.ref_price）偏差
 * 超过阈值（threshold，单位 PERCENT，缺省 10%）即触发；偏差超过阈值 2 倍升级为 CRITICAL。
 * 仅检查未取消（DRAFT/APPROVING/APPROVED）的订单明细。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class PriceAbnormalEvaluator implements AlertRuleEvaluator {

    /** 偏差百分比阈值（缺省 10%） */
    private static final BigDecimal DEFAULT_THRESHOLD = new BigDecimal("10");
    /** 每次扫描最多检查订单数 */
    private static final int SCAN_LIMIT = 200;

    private final PoOrderMapper orderMapper;
    private final PoOrderItemMapper itemMapper;
    private final MaterialMapper materialMapper;

    @Override
    public String ruleCode() {
        return "PRICE_ABNORMAL";
    }

    @Override
    public String bizDomain() {
        return "PURCHASE";
    }

    @Override
    public String alertType() {
        return "PRICE_ABNORMAL";
    }

    @Override
    public String defaultRuleName() {
        return "价格异常预警";
    }

    @Override
    public List<AlertCandidate> evaluate(AgentAlertRule rule) {
        List<AlertCandidate> candidates = new ArrayList<>();
        BigDecimal threshold = rule.getThreshold() != null ? rule.getThreshold() : DEFAULT_THRESHOLD;
        try {
            List<PoOrder> orders = orderMapper.selectList(
                    new LambdaQueryWrapper<PoOrder>()
                            .in(PoOrder::getStatus, "APPROVED", "APPROVING")
                            .eq(PoOrder::getDeleted, 0)
                            .last("LIMIT " + SCAN_LIMIT)
            );
            for (PoOrder order : orders) {
                List<PoOrderItem> items = itemMapper.selectList(
                        new LambdaQueryWrapper<PoOrderItem>()
                                .eq(PoOrderItem::getOrderId, order.getId())
                                .eq(PoOrderItem::getDeleted, 0)
                );
                for (PoOrderItem item : items) {
                    if (item.getPrice() == null || item.getMaterialId() == null) continue;
                    Material material = materialMapper.selectById(item.getMaterialId());
                    if (material == null || material.getRefPrice() == null
                            || material.getRefPrice().compareTo(BigDecimal.ZERO) <= 0) continue;

                    BigDecimal deviation = item.getPrice().subtract(material.getRefPrice())
                            .divide(material.getRefPrice(), 4, BigDecimal.ROUND_HALF_UP)
                            .multiply(new BigDecimal("100"));
                    if (deviation.abs().compareTo(threshold) <= 0) continue;

                    String materialCode = material.getMaterialCode() != null
                            ? material.getMaterialCode() : String.valueOf(item.getMaterialId());
                    String materialName = item.getMaterialName() != null ? item.getMaterialName()
                            : (material.getMaterialName() != null ? material.getMaterialName() : "-");
                    String detail = String.format(
                            "订单 %s 中物料 %s（%s）采购单价 %s，参考单价 %s，偏差 %.1f%%（阈值 %s%%），请核实。",
                            order.getOrderNo(), materialCode, materialName,
                            item.getPrice(), material.getRefPrice(), deviation.abs(), threshold
                    );
                    AlertCandidate candidate = AlertCandidate.of(
                            rule, order.getId(), order.getOrderNo(),
                            "ORDER", "/orders/" + order.getId(),
                            "价格异常预警", detail
                    );
                    if (deviation.abs().compareTo(threshold.multiply(new BigDecimal("2"))) > 0) {
                        candidate.setLevel("CRITICAL");
                    }
                    candidates.add(candidate);
                }
            }
        } catch (Exception e) {
            // 评估失败不影响其他规则
        }
        return candidates;
    }
}

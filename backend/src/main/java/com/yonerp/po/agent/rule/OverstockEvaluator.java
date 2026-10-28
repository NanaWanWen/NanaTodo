package com.yonerp.po.agent.rule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yonerp.po.agent.entity.AgentAlertRule;
import com.yonerp.po.inventory.entity.PoInventory;
import com.yonerp.po.inventory.mapper.InventoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 超储预警规则评估器
 * <p>
 * 判断口径（参考用友超储预警）：当前库存超过「安全库存 × 阈值倍数」即触发。
 * 阈值单位 MULTIPLE，缺省 3.0 表示超过安全库存 3 倍视为超储。
 * 仅预警，不阻断业务流程，由库管员/采购员复核周转率后调整安全库存或执行呆滞料处理。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class OverstockEvaluator implements AlertRuleEvaluator {

    /** 默认倍数：超过安全库存 3 倍视为超储 */
    private static final BigDecimal DEFAULT_MULTIPLE = new BigDecimal("3");

    private final InventoryMapper inventoryMapper;

    @Override
    public String ruleCode() {
        return "OVERSTOCK";
    }

    @Override
    public String bizDomain() {
        return "INVENTORY";
    }

    @Override
    public String alertType() {
        return "OVERSTOCK";
    }

    @Override
    public String defaultRuleName() {
        return "超储预警";
    }

    @Override
    public List<AlertCandidate> evaluate(AgentAlertRule rule) {
        List<AlertCandidate> candidates = new ArrayList<>();
        BigDecimal multiple = rule.getThreshold() != null ? rule.getThreshold() : DEFAULT_MULTIPLE;
        if (multiple.compareTo(BigDecimal.ZERO) <= 0) multiple = DEFAULT_MULTIPLE;
        try {
            List<PoInventory> inventories = inventoryMapper.selectList(
                    new LambdaQueryWrapper<PoInventory>().eq(PoInventory::getDeleted, 0)
            );
            for (PoInventory inv : inventories) {
                BigDecimal quantity = inv.getQuantity() != null ? inv.getQuantity() : BigDecimal.ZERO;
                BigDecimal safetyStock = inv.getSafetyStock() != null ? inv.getSafetyStock() : BigDecimal.ZERO;
                BigDecimal upperLine = safetyStock.multiply(multiple);
                if (quantity.compareTo(upperLine) <= 0) continue;

                String detail = String.format(
                        "物料 %s（%s）当前库存 %s，超过安全库存 %s 的 %s 倍（超储线 %s），请评估周转率并考虑呆滞料处理。",
                        inv.getMaterialCode(), inv.getMaterialName(), quantity, safetyStock, multiple, upperLine
                );
                candidates.add(AlertCandidate.of(
                        rule, inv.getMaterialId(), inv.getMaterialCode(),
                        "MATERIAL", "/inventory",
                        "超储预警", detail
                ));
            }
        } catch (Exception e) {
            // 评估失败不影响其他规则
        }
        return candidates;
    }
}

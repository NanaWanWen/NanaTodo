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
 * 低库存规则评估器
 * <p>
 * 判断口径（参考用友安全库存预警）：当前库存低于「安全库存 × 阈值系数」即命中。
 * 阈值单位 MULTIPLE，缺省 1.0 表示低于安全库存本身即预警；调大（如 1.2）可提前预警。
 * 库存为 0 或负数视为断货，直接升级为 CRITICAL。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class LowStockEvaluator implements AlertRuleEvaluator {

    /** 默认阈值系数：低于安全库存即预警 */
    private static final BigDecimal DEFAULT_RATIO = BigDecimal.ONE;

    private final InventoryMapper inventoryMapper;

    @Override
    public String ruleCode() {
        return "LOW_STOCK";
    }

    @Override
    public String bizDomain() {
        return "INVENTORY";
    }

    @Override
    public String alertType() {
        return "LOW_STOCK";
    }

    @Override
    public String defaultRuleName() {
        return "安全库存预警";
    }

    @Override
    public List<AlertCandidate> evaluate(AgentAlertRule rule) {
        List<AlertCandidate> candidates = new ArrayList<>();
        BigDecimal ratio = rule.getThreshold() != null ? rule.getThreshold() : DEFAULT_RATIO;
        if (ratio.compareTo(BigDecimal.ZERO) <= 0) ratio = DEFAULT_RATIO;
        try {
            List<PoInventory> inventories = inventoryMapper.selectList(
                    new LambdaQueryWrapper<PoInventory>().eq(PoInventory::getDeleted, 0)
            );
            for (PoInventory inv : inventories) {
                BigDecimal quantity = inv.getQuantity() != null ? inv.getQuantity() : BigDecimal.ZERO;
                BigDecimal safetyStock = inv.getSafetyStock() != null ? inv.getSafetyStock() : BigDecimal.ZERO;
                BigDecimal triggerLine = safetyStock.multiply(ratio);
                if (quantity.compareTo(triggerLine) >= 0) continue;

                boolean stockOut = quantity.compareTo(BigDecimal.ZERO) <= 0;
                String detail = stockOut
                        ? String.format("物料 %s（%s）库存已耗尽（当前 %s，安全库存 %s），存在断料停工风险，请立即补货。",
                        inv.getMaterialCode(), inv.getMaterialName(), quantity, safetyStock)
                        : String.format("物料 %s（%s）当前库存 %s，低于安全库存 %s（预警线 %s），建议及时补货。",
                        inv.getMaterialCode(), inv.getMaterialName(), quantity, safetyStock, triggerLine);

                AlertCandidate candidate = AlertCandidate.of(
                        rule, inv.getMaterialId(), inv.getMaterialCode(),
                        "MATERIAL", "/inventory",
                        stockOut ? "库存断料预警" : "安全库存预警", detail
                );
                if (stockOut) candidate.setLevel("CRITICAL");
                candidates.add(candidate);
            }
        } catch (Exception e) {
            // 评估失败不影响其他规则
        }
        return candidates;
    }
}

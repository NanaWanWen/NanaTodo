package com.yonerp.po.ap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yonerp.po.ap.entity.PoApSettlement;
import com.yonerp.po.ap.entity.PoApSettlementItem;
import com.yonerp.po.ap.entity.PoMatchRule;
import com.yonerp.po.ap.mapper.ApSettlementItemMapper;
import com.yonerp.po.ap.mapper.ApSettlementMapper;
import com.yonerp.po.ap.mapper.MatchRuleMapper;
import com.yonerp.po.ap.service.MatchEngine;
import com.yonerp.po.base.entity.Material;
import com.yonerp.po.base.mapper.MaterialMapper;
import com.yonerp.po.invoice.entity.PoInvoice;
import com.yonerp.po.invoice.mapper.InvoiceMapper;
import com.yonerp.po.order.entity.PoOrder;
import com.yonerp.po.order.entity.PoOrderItem;
import com.yonerp.po.order.mapper.PoOrderItemMapper;
import com.yonerp.po.order.mapper.PoOrderMapper;
import com.yonerp.po.receipt.entity.PoReceipt;
import com.yonerp.po.receipt.entity.PoReceiptItem;
import com.yonerp.po.receipt.mapper.ReceiptItemMapper;
import com.yonerp.po.receipt.mapper.ReceiptMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 匹配引擎实现（用友式）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchEngineImpl implements MatchEngine {

    private static final DateTimeFormatter DATE_PATTERN = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final ApSettlementMapper settlementMapper;
    private final ApSettlementItemMapper settlementItemMapper;
    private final MatchRuleMapper matchRuleMapper;
    private final PoOrderMapper orderMapper;
    private final PoOrderItemMapper orderItemMapper;
    private final ReceiptMapper receiptMapper;
    private final ReceiptItemMapper receiptItemMapper;
    private final InvoiceMapper invoiceMapper;
    private final MaterialMapper materialMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PoApSettlement matchByOrder(Long orderId) {
        PoOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            log.warn("三单匹配跳过：订单 {} 不存在", orderId);
            return null;
        }

        // 获取收货单（支持一单多收：同一订单可有多张收货单，金额合并参与匹配）。
        // 仅统计「已确认」收货单：草稿与已取消的不计入净额（与数量池口径一致）
        List<PoReceipt> receipts = receiptMapper.selectList(
            new LambdaQueryWrapper<PoReceipt>()
                .eq(PoReceipt::getOrderId, orderId)
                .eq(PoReceipt::getStatus, "CONFIRMED")
                .orderByAsc(PoReceipt::getReceiptDate)
        );
        // 同一订单可有多张发票（红冲+重开）：按「有效发票」净额参与匹配，红字发票金额为负。
        // 有效 = 已核对/已付款/已红冲原票（红冲抵扣链）；未核对草稿与已作废发票不参与匹配
        List<PoInvoice> invoices = invoiceMapper.selectList(
            new LambdaQueryWrapper<PoInvoice>()
                .eq(PoInvoice::getOrderId, orderId)
                .in(PoInvoice::getInvoiceStatus, "CHECKED", "PAID", "RED_FLUSHED")
        );

        if (receipts.isEmpty() || invoices.isEmpty()) {
            log.info("三单匹配跳过：订单 {} 缺少收货单或发票", orderId);
            return null;
        }
        BigDecimal netInvoice = invoices.stream()
            .map(PoInvoice::getTotalAmount)
            .reduce(ZERO, BigDecimal::add);
        BigDecimal receiptTotal = receipts.stream()
            .map(PoReceipt::getTotalAmount)
            .reduce(ZERO, BigDecimal::add);

        // 获取订单明细
        List<PoOrderItem> orderItems = orderItemMapper.selectList(
            new LambdaQueryWrapper<PoOrderItem>().eq(PoOrderItem::getOrderId, orderId)
        );

        // 获取全部收货明细（跨多张收货单，支持分批收货/合单开票）
        List<PoReceiptItem> receiptItems = new ArrayList<>();
        for (PoReceipt r : receipts) {
            receiptItems.addAll(receiptItemMapper.selectList(
                new LambdaQueryWrapper<PoReceiptItem>().eq(PoReceiptItem::getReceiptId, r.getId())
            ));
        }

        // 获取匹配规则（供应商 → 物料分类 → 币种 → 全局，逐级回退）
        // 物料分类取订单首个明细的物料分类（混合物料订单以首要物料为准）
        String materialCategory = null;
        if (!orderItems.isEmpty() && orderItems.get(0).getMaterialId() != null) {
            Material firstMaterial = materialMapper.selectById(orderItems.get(0).getMaterialId());
            if (firstMaterial != null) {
                materialCategory = firstMaterial.getCategory();
            }
        }
        PoMatchRule rule = getMatchRule(order.getSupplierId(), materialCategory, order.getCurrency());

        // 幂等：同一订单只保留一条有效结算单，已存在则原地更新，避免重复匹配产生重复结算单
        PoApSettlement exist = settlementMapper.selectOne(
            new LambdaQueryWrapper<PoApSettlement>()
                .eq(PoApSettlement::getOrderId, orderId)
                .notIn(PoApSettlement::getMatchStatus, "CLOSED", "REVERSED")
                .orderByDesc(PoApSettlement::getId)
                .last("LIMIT 1")
        );
        boolean isNew = exist == null;

        PoApSettlement settlement = isNew ? new PoApSettlement() : exist;
        if (isNew) {
            settlement.setMatchNo(generateMatchNo());
        }
        settlement.setOrderId(orderId);
        settlement.setReceiptId(receipts.get(0).getId());
        settlement.setInvoiceId(invoices.get(invoices.size() - 1).getId());
        settlement.setSupplierId(order.getSupplierId());
        settlement.setOrderAmount(order.getTotalAmount());
        settlement.setReceiptAmount(receiptTotal);
        settlement.setInvoiceAmount(netInvoice);
        settlement.setMatchRuleCode(rule.getRuleCode());
        settlement.setCurrency(order.getCurrency());
        settlement.setMatchedTime(LocalDateTime.now());
        if (isNew) {
            settlement.setMatchType("AUTO");
        }

        // 计算差异
        String origResolution = settlement.getResolution();
        calculateDifferences(settlement, orderItems, receiptItems, rule);

        // 保存结算单
        if (isNew) {
            settlementMapper.insert(settlement);
        } else {
            settlementMapper.updateById(settlement);
            // 行级明细整体重建，保证与最新单据一致
            settlementItemMapper.delete(
                new LambdaQueryWrapper<PoApSettlementItem>()
                    .eq(PoApSettlementItem::getSettlementId, settlement.getId())
            );
        }

        // 异步处理动作（补收货/退票重开/调价）登记且最终命中 MATCHED：显式清空处理动作
        // （updateById 会忽略 null 字段，故此处单独更新）
        if (!isNew && "MATCHED".equals(settlement.getMatchStatus()) && isWaitingResolution(origResolution)) {
            settlementMapper.update(null, new LambdaUpdateWrapper<PoApSettlement>()
                .eq(PoApSettlement::getId, settlement.getId())
                .set(PoApSettlement::getResolution, null));
        }

        // 创建行级明细（发票按收货金额比例分摊到行：当前发票为主表级，无发票明细）
        createSettlementItems(settlement.getId(), orderItems, receiptItems,
            netInvoice, receiptTotal);

        log.info("三单匹配完成：orderId={}, matchNo={}, status={}, diff={}", 
            orderId, settlement.getMatchNo(), settlement.getMatchStatus(), settlement.getDiffAmount());

        return settlement;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<PoApSettlement> matchByPool(Long supplierId, Long materialId) {
        // 数量池勾对：查该供应商下已确认收货、且包含指定物料的收货单，
        // 以订单为最小勾对单元逐单执行三单匹配，天然支持一单多收、一票多单的聚合勾对
        List<PoApSettlement> results = new ArrayList<>();
        List<PoReceipt> receipts = receiptMapper.selectList(
            new LambdaQueryWrapper<PoReceipt>()
                .eq(PoReceipt::getSupplierId, supplierId)
                .eq(PoReceipt::getStatus, "CONFIRMED")
                .orderByAsc(PoReceipt::getReceiptDate)
        );
        for (PoReceipt receipt : receipts) {
            if (materialId != null) {
                long cnt = receiptItemMapper.selectCount(
                    new LambdaQueryWrapper<PoReceiptItem>()
                        .eq(PoReceiptItem::getReceiptId, receipt.getId())
                        .eq(PoReceiptItem::getMaterialId, materialId)
                );
                if (cnt == 0) {
                    continue;
                }
            }
            try {
                PoApSettlement settlement = matchByOrder(receipt.getOrderId());
                if (settlement != null) {
                    results.add(settlement);
                }
            } catch (Exception e) {
                log.error("数量池勾对失败：orderId={}", receipt.getOrderId(), e);
            }
        }
        log.info("数量池勾对完成：supplierId={}, materialId={}, 结算单数={}",
            supplierId, materialId, results.size());
        return results;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<PoApSettlement> matchBatch(List<Long> orderIds) {
        List<PoApSettlement> results = new ArrayList<>();
        for (Long orderId : orderIds) {
            try {
                PoApSettlement settlement = matchByOrder(orderId);
                if (settlement != null) {
                    results.add(settlement);
                }
            } catch (Exception e) {
                log.error("批量匹配失败：orderId={}", orderId, e);
            }
        }
        return results;
    }

    @Override
    public String generateMatchNo() {
        String date = LocalDate.now().format(DATE_PATTERN);
        String prefix = "MATCH" + date;
        
        // 查询当天最大序号
        LambdaQueryWrapper<PoApSettlement> wrapper = new LambdaQueryWrapper<>();
        wrapper.likeRight(PoApSettlement::getMatchNo, prefix);
        wrapper.orderByDesc(PoApSettlement::getMatchNo);
        wrapper.last("LIMIT 1");
        
        PoApSettlement last = settlementMapper.selectOne(wrapper);
        
        int seq = 1;
        if (last != null && last.getMatchNo() != null) {
            String lastNo = last.getMatchNo();
            String seqStr = lastNo.substring(prefix.length());
            try {
                seq = Integer.parseInt(seqStr) + 1;
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        
        return prefix + String.format("%04d", seq);
    }

    /**
     * 获取匹配规则（用友式四级回退：供应商 → 物料分类 → 币种 → 全局，最后内置兜底）
     */
    private PoMatchRule getMatchRule(Long supplierId, String materialCategory, String currency) {
        // 1. 供应商级规则
        if (supplierId != null) {
            PoMatchRule supplierRule = matchRuleMapper.selectOne(
                new LambdaQueryWrapper<PoMatchRule>()
                    .eq(PoMatchRule::getScopeType, "SUPPLIER")
                    .eq(PoMatchRule::getScopeValue, String.valueOf(supplierId))
                    .eq(PoMatchRule::getEnabled, 1)
                    .eq(PoMatchRule::getDeleted, 0)
            );
            if (supplierRule != null) {
                return supplierRule;
            }
        }

        // 2. 物料分类级规则
        if (materialCategory != null && !materialCategory.isBlank()) {
            PoMatchRule categoryRule = matchRuleMapper.selectOne(
                new LambdaQueryWrapper<PoMatchRule>()
                    .eq(PoMatchRule::getScopeType, "MATERIAL_CATEGORY")
                    .eq(PoMatchRule::getScopeValue, materialCategory)
                    .eq(PoMatchRule::getEnabled, 1)
                    .eq(PoMatchRule::getDeleted, 0)
            );
            if (categoryRule != null) {
                return categoryRule;
            }
        }

        // 3. 币种级规则
        if (currency != null && !currency.isBlank()) {
            PoMatchRule currencyRule = matchRuleMapper.selectOne(
                new LambdaQueryWrapper<PoMatchRule>()
                    .eq(PoMatchRule::getScopeType, "CURRENCY")
                    .eq(PoMatchRule::getScopeValue, currency)
                    .eq(PoMatchRule::getEnabled, 1)
                    .eq(PoMatchRule::getDeleted, 0)
            );
            if (currencyRule != null) {
                return currencyRule;
            }
        }

        // 4. 全局规则
        PoMatchRule globalRule = matchRuleMapper.selectOne(
            new LambdaQueryWrapper<PoMatchRule>()
                .eq(PoMatchRule::getScopeType, "GLOBAL")
                .eq(PoMatchRule::getEnabled, 1)
                .eq(PoMatchRule::getDeleted, 0)
        );

        if (globalRule == null) {
            // 返回默认规则
            globalRule = new PoMatchRule();
            globalRule.setRuleCode("DEFAULT");
            globalRule.setAmountTolerance(new BigDecimal("0.01"));
            globalRule.setAmountToleranceUnit("PERCENT");
            globalRule.setQtyTolerance(ZERO);
            globalRule.setQtyToleranceUnit("PERCENT");
        }

        return globalRule;
    }

    /**
     * 计算差异并判定匹配状态
     */
    private void calculateDifferences(PoApSettlement settlement, 
                                      List<PoOrderItem> orderItems,
                                      List<PoReceiptItem> receiptItems,
                                      PoMatchRule rule) {
        // 登记信息（处理人/时间/意见）在重匹配前保留，退票重开保持等待态时用于回写
        String registeredReason = settlement.getHoldReason();

        BigDecimal receiptAmount = settlement.getReceiptAmount();
        BigDecimal invoiceAmount = settlement.getInvoiceAmount();

        // 金额差异（发票 - 收货）
        BigDecimal diffAmount = invoiceAmount.subtract(receiptAmount).abs();
        settlement.setDiffAmount(diffAmount);

        // 数量差异（实收 - 订购，取绝对值）
        BigDecimal qtyDiff = ZERO;
        BigDecimal totalOrderedQty = ZERO;
        if (!orderItems.isEmpty() && !receiptItems.isEmpty()) {
            totalOrderedQty = orderItems.stream()
                .map(PoOrderItem::getQty)
                .reduce(ZERO, BigDecimal::add);
            BigDecimal totalReceivedQty = receiptItems.stream()
                .map(PoReceiptItem::getReceivedQty)
                .reduce(ZERO, BigDecimal::add);
            qtyDiff = totalReceivedQty.subtract(totalOrderedQty).abs();
        }
        settlement.setQtyDiff(qtyDiff);

        // 单价差异和税额差异暂设为 0（行级明细中按行计算）
        settlement.setPriceDiff(ZERO);
        settlement.setTaxDiff(ZERO);

        // 计算容差（金额 + 数量）
        BigDecimal tolerance = calculateTolerance(receiptAmount, rule);
        settlement.setTolerance(tolerance);
        BigDecimal qtyTolerance = calculateQtyTolerance(totalOrderedQty, rule);

        // 判定匹配状态
        String matchStatus = determineMatchStatus(diffAmount, qtyDiff, tolerance, qtyTolerance, rule);
        settlement.setMatchStatus(matchStatus);

        // 挂起必须记录原因，便于差异处理工作台与审计追溯
        if ("HOLD".equals(matchStatus)) {
            if (rule.getAutoSettle() == null || rule.getAutoSettle() != 1) {
                settlement.setHoldReason(String.format(
                    "匹配规则[%s]未启用自动结算，差异挂起待人工处理", rule.getRuleCode()));
            } else {
                settlement.setHoldReason(String.format(
                    "发票金额与收货金额差异 %s 元，超过容差 %s 元的 2 倍，挂起待审批",
                    diffAmount.toPlainString(), tolerance.toPlainString()));
            }
        } else {
            settlement.setHoldReason(null);
        }

        // 差异类型：数量差异优先，其次金额差异
        if (qtyDiff.compareTo(ZERO) > 0) {
            settlement.setDiffType("QTY");
        } else if (diffAmount.compareTo(ZERO) > 0) {
            settlement.setDiffType("AMOUNT");
        } else {
            settlement.setDiffType(null);
        }

        // 异步处理动作（补收货/退票重开/调价）登记后、重匹配仍未命中时，保持对应「等待态」：
        // 差异未解决前继续拦住付款，且工作台/预警识别"已有人在跟进"，不再反复刷预警；
        // 登记信息(hold_reason)保留以便审计追溯。（命中 MATCHED 的闭环与 resolution 清空在 matchByOrder 处理）
        if (!"MATCHED".equals(matchStatus) && isWaitingResolution(settlement.getResolution())) {
            settlement.setMatchStatus(waitingStatusOf(settlement.getResolution()));
            settlement.setHoldReason(registeredReason);
        }
    }

    /** 差异登记的异步处理动作：补收货/退票重开/调价——等待新单据/新发票/调价后重匹配闭环 */
    private boolean isWaitingResolution(String resolution) {
        return "ADD_RECEIPT".equals(resolution) || "REDO_INVOICE".equals(resolution) || "ADJUST_PRICE".equals(resolution);
    }

    /** 异步处理动作 → 等待态：补收货=等待收货单，退票重开=等待重开发票，调价=等待订单调价 */
    private String waitingStatusOf(String resolution) {
        if ("ADD_RECEIPT".equals(resolution)) {
            return "WAITING_RECEIPT";
        }
        if ("ADJUST_PRICE".equals(resolution)) {
            return "WAITING_ORDER";
        }
        return "WAITING_INVOICE";
    }

    /**
     * 计算金额容差（比例容差与绝对额容差按规则单位取值）
     */
    private BigDecimal calculateTolerance(BigDecimal baseAmount, PoMatchRule rule) {
        if (rule.getAmountTolerance() == null) {
            return ZERO;
        }

        BigDecimal tolerance = rule.getAmountTolerance();
        String unit = rule.getAmountToleranceUnit();

        if ("PERCENT".equals(unit)) {
            // 百分比容差
            return baseAmount.multiply(tolerance).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        } else {
            // 绝对额容差
            return tolerance;
        }
    }

    /**
     * 计算数量容差（比例按订购数量折算，绝对额直接取值）
     */
    private BigDecimal calculateQtyTolerance(BigDecimal orderedQty, PoMatchRule rule) {
        if (rule.getQtyTolerance() == null) {
            return ZERO;
        }

        BigDecimal tolerance = rule.getQtyTolerance();
        String unit = rule.getQtyToleranceUnit();

        if ("PERCENT".equals(unit) && orderedQty.compareTo(ZERO) > 0) {
            return orderedQty.multiply(tolerance).divide(new BigDecimal("100"), 3, RoundingMode.HALF_UP);
        }
        return tolerance;
    }

    /**
     * 判定匹配状态（用友式口径，按严重程度排序）：
     * 1. 无差异 → MATCHED
     * 2. 规则未启用自动结算 → HOLD（任何差异都挂起待人工）
     * 3. 金额差异超容差 2 倍 → HOLD（挂起待审批）
     * 4. 数量差异超数量容差 → PARTIAL_QTY（避免被金额容差吞掉）
     * 5. 金额差异超容差 → PARTIAL_AMOUNT
     * 6. 其余（含容差内差异）→ MATCHED
     */
    private String determineMatchStatus(BigDecimal diffAmount, BigDecimal qtyDiff,
                                        BigDecimal tolerance, BigDecimal qtyTolerance,
                                        PoMatchRule rule) {
        if (qtyDiff.compareTo(ZERO) == 0 && diffAmount.compareTo(ZERO) == 0) {
            return "MATCHED";
        }
        if (rule.getAutoSettle() == null || rule.getAutoSettle() != 1) {
            return "HOLD";
        }
        if (diffAmount.compareTo(tolerance.multiply(BigDecimal.valueOf(2))) > 0) {
            return "HOLD";
        }
        if (qtyDiff.compareTo(qtyTolerance) > 0) {
            return "PARTIAL_QTY";
        }
        if (diffAmount.compareTo(tolerance) > 0) {
            return "PARTIAL_AMOUNT";
        }
        return "MATCHED";
    }

    /**
     * 创建行级明细（订单-收货-发票三侧对照）
     * 发票为主表级凭证，按各行收货金额占比分摊到行，支持一单多收/合单开票的行级呈现
     */
    private void createSettlementItems(Long settlementId,
                                       List<PoOrderItem> orderItems,
                                       List<PoReceiptItem> receiptItems,
                                       BigDecimal invoiceAmount,
                                       BigDecimal receiptAmount) {
        // 发票相对收货的系数（单价上浮/下浮），用于分摊发票侧单价与金额
        BigDecimal factor = BigDecimal.ONE;
        if (receiptAmount != null && receiptAmount.compareTo(ZERO) > 0 && invoiceAmount != null) {
            factor = invoiceAmount.divide(receiptAmount, 6, RoundingMode.HALF_UP);
        }

        // 简化实现：按订单明细创建行
        for (PoOrderItem orderItem : orderItems) {
            PoApSettlementItem item = new PoApSettlementItem();
            item.setSettlementId(settlementId);
            item.setOrderItemId(orderItem.getId());
            item.setMaterialId(orderItem.getMaterialId());
            
            // 查找物料编码
            Material material = materialMapper.selectById(orderItem.getMaterialId());
            item.setMaterialCode(material != null ? material.getMaterialCode() : "UNKNOWN");
            item.setMaterialName(orderItem.getMaterialName());
            item.setOrderedQty(orderItem.getQty());
            item.setOrderedPrice(orderItem.getPrice());
            item.setOrderedAmount(orderItem.getAmount());

            // 查找对应的收货明细
            PoReceiptItem receiptItem = receiptItems.stream()
                .filter(r -> r.getOrderItemId().equals(orderItem.getId()))
                .findFirst()
                .orElse(null);

            if (receiptItem != null) {
                item.setReceiptItemId(receiptItem.getId());
                item.setReceivedQty(receiptItem.getReceivedQty());
                item.setReceivedPrice(receiptItem.getUnitPrice());
                item.setReceivedAmount(receiptItem.getAmount());

                // 发票侧按系数分摊
                BigDecimal invoicedQty = receiptItem.getReceivedQty();
                BigDecimal invoicedPrice = receiptItem.getUnitPrice().multiply(factor)
                    .setScale(4, RoundingMode.HALF_UP);
                BigDecimal invoicedAmount = receiptItem.getAmount().multiply(factor)
                    .setScale(2, RoundingMode.HALF_UP);
                item.setInvoicedQty(invoicedQty);
                item.setInvoicedPrice(invoicedPrice);
                item.setInvoicedAmount(invoicedAmount);

                // 计算行差异
                BigDecimal qtyDiff = receiptItem.getReceivedQty().subtract(orderItem.getQty());
                BigDecimal priceDiff = invoicedPrice.subtract(receiptItem.getUnitPrice());
                BigDecimal amountDiff = invoicedAmount.subtract(receiptItem.getAmount());

                item.setQtyDiff(qtyDiff);
                item.setPriceDiff(priceDiff);
                item.setAmountDiff(amountDiff);
                item.setTaxDiff(ZERO);

                // 行状态：数量差异优先，其次单价/金额差异
                if (qtyDiff.compareTo(ZERO) == 0 && amountDiff.compareTo(ZERO) == 0) {
                    item.setMatchStatus("MATCHED");
                } else if (qtyDiff.compareTo(ZERO) != 0) {
                    item.setMatchStatus("PARTIAL_QTY");
                    item.setDiffType("QTY");
                } else if (priceDiff.compareTo(ZERO) != 0) {
                    item.setMatchStatus("PARTIAL_AMOUNT");
                    item.setDiffType("PRICE");
                } else {
                    item.setMatchStatus("PARTIAL_AMOUNT");
                    item.setDiffType("AMOUNT");
                }
            } else {
                item.setReceivedQty(ZERO);
                item.setReceivedPrice(ZERO);
                item.setReceivedAmount(ZERO);
                item.setInvoicedQty(ZERO);
                item.setInvoicedPrice(ZERO);
                item.setInvoicedAmount(ZERO);
                item.setQtyDiff(orderItem.getQty().negate());
                item.setAmountDiff(orderItem.getAmount());
                item.setMatchStatus("UNMATCHED");
            }

            settlementItemMapper.insert(item);
        }
    }
}

package com.yonerp.po.ap.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 结算单行级明细
 */
@Data
@TableName("po_ap_settlement_item")
public class PoApSettlementItem {

    private Long id;

    /** 结算单ID */
    private Long settlementId;

    /** 订单明细ID */
    private Long orderItemId;

    /** 收货明细ID */
    private Long receiptItemId;

    /** 发票明细ID */
    private Long invoiceItemId;

    /** 物料ID */
    private Long materialId;

    /** 物料编码 */
    private String materialCode;

    /** 物料名称 */
    private String materialName;

    /** 订购数量 */
    private BigDecimal orderedQty;

    /** 实收数量 */
    private BigDecimal receivedQty;

    /** 开票数量 */
    private BigDecimal invoicedQty;

    /** 数量差异 */
    private BigDecimal qtyDiff;

    /** 订购单价 */
    private BigDecimal orderedPrice;

    /** 收货单价 */
    private BigDecimal receivedPrice;

    /** 开票单价 */
    private BigDecimal invoicedPrice;

    /** 单价差异 */
    private BigDecimal priceDiff;

    /** 订购金额 */
    private BigDecimal orderedAmount;

    /** 收货金额 */
    private BigDecimal receivedAmount;

    /** 开票金额 */
    private BigDecimal invoicedAmount;

    /** 金额差异 */
    private BigDecimal amountDiff;

    /** 税额差异 */
    private BigDecimal taxDiff;

    /** 差异类型：QTY/PRICE/TAX/EXCHANGE */
    private String diffType;

    /** 行匹配状态 */
    private String matchStatus;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

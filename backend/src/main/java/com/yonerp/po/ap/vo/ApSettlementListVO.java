package com.yonerp.po.ap.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 应付对账列表 VO（用友式）
 */
@Data
public class ApSettlementListVO {

    private Long id;

    /** 结算单号 */
    private String matchNo;

    private Long orderId;

    private String orderNo;

    private Long receiptId;

    private String receiptNo;

    private Long invoiceId;

    private String invoiceNo;

    private Long supplierId;

    private String supplierName;

    private BigDecimal orderAmount;

    private BigDecimal receiptAmount;

    private BigDecimal invoiceAmount;

    private BigDecimal diffAmount;

    private BigDecimal qtyDiff;

    private BigDecimal priceDiff;

    private BigDecimal taxDiff;

    /** 差异类型：QTY/PRICE/TAX/EXCHANGE */
    private String diffType;

    /**
     * 匹配状态：MATCHED/PARTIAL_QTY/PARTIAL_AMOUNT/HOLD/UNMATCHED
     * /WAITING_RECEIPT/WAITING_INVOICE/WAITING_ORDER/CLOSED/REVERSED
     */
    private String matchStatus;

    /** 匹配类型：AUTO/MANUAL/FORCE */
    private String matchType;

    /** 使用的匹配规则编码 */
    private String matchRuleCode;

    private String currency;

    private BigDecimal tolerance;

    private Long matchedBy;

    private String matchedByName;

    private LocalDateTime matchedTime;

    private String holdReason;

    private String resolution;

    private String remark;

    private LocalDateTime updateTime;
}

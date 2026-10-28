package com.yonerp.po.ap.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 应付对账 / 三单匹配（用友式）
 */
@Data
@TableName("po_ap_settlement")
public class PoApSettlement {

    private Long id;

    /** 结算单号 MATCHyyyyMMddNNNN */
    private String matchNo;

    /** 订单ID */
    private Long orderId;

    /** 收货单ID（可为空，支持一票多单） */
    private Long receiptId;

    /** 发票ID（可为空，支持分批收货） */
    private Long invoiceId;

    /** 供应商ID */
    private Long supplierId;

    /** 订单金额 */
    private BigDecimal orderAmount;

    /** 收货金额 */
    private BigDecimal receiptAmount;

    /** 发票金额 */
    private BigDecimal invoiceAmount;

    /** 差异金额(发票-收货) */
    private BigDecimal diffAmount;

    /** 数量差异 */
    private BigDecimal qtyDiff;

    /** 单价差异 */
    private BigDecimal priceDiff;

    /** 税额差异 */
    private BigDecimal taxDiff;

    /** 差异类型：QTY/PRICE/TAX/EXCHANGE */
    private String diffType;

    /**
     * 匹配状态：
     * MATCHED(三单一致) / PARTIAL_QTY(数量差异) / PARTIAL_AMOUNT(金额差异) / HOLD(超容差挂起)
     * UNMATCHED(票未到) / WAITING_RECEIPT(已登记补收货，等待新收货) / WAITING_INVOICE(已登记退票重开，等待新发票)
     * WAITING_ORDER(已登记调价，等待订单变更) / CLOSED(已关闭) / REVERSED(已红冲)
     */
    private String matchStatus;

    /** 匹配类型：AUTO/MANUAL/FORCE */
    private String matchType;

    /** 使用的匹配规则编码 */
    private String matchRuleCode;

    /** 匹配批次号 */
    private String matchBatch;

    /** 币种 */
    private String currency;

    /** 容差金额 */
    private BigDecimal tolerance;

    /** 匹配人ID */
    private Long matchedBy;

    /** 匹配时间 */
    private LocalDateTime matchedTime;

    /** 挂起原因 */
    private String holdReason;

    /** 处理方式：补收/退票/调价/损耗放行/审批强制 */
    private String resolution;

    private String remark;

    @Version
    private Integer version;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer deleted;
}

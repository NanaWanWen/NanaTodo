package com.yonerp.po.invoice.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 发票列表 VO
 */
@Data
public class InvoiceListVO {

    private Long id;

    private String invoiceNo;

    private Long receiptId;

    private Long orderId;

    private String orderNo;

    private Long supplierId;

    private String supplierName;

    private LocalDate invoiceDate;

    private BigDecimal invoiceAmount;

    private BigDecimal taxAmount;

    private BigDecimal totalAmount;

    private String invoiceStatus;

    /** 发票类型：BLUE 蓝字 / RED 红字 */
    private String invoiceType;

    /** 红蓝关联：被红冲原发票号 */
    private String relatedInvoiceNo;

    private LocalDateTime createTime;
}

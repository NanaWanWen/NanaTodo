package com.yonerp.po.invoice.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 采购发票
 */
@Data
@TableName("po_invoice")
public class PoInvoice {

    private Long id;

    /** 发票编号 INVyyyyMMddNNNN */
    private String invoiceNo;

    /** 收货单ID */
    private Long receiptId;

    /** 订单ID(冗余) */
    private Long orderId;

    /** 供应商ID(冗余) */
    private Long supplierId;

    /** 开票日期 */
    private LocalDate invoiceDate;

    /** 发票金额(不含税) */
    private BigDecimal invoiceAmount;

    /** 税额 */
    private BigDecimal taxAmount;

    /** 价税合计 */
    private BigDecimal totalAmount;

    /** 发票类型：BLUE 蓝字(正常) / RED 红字(红冲) */
    private String invoiceType;

    /** 红蓝关联：红字发票指向被红冲的原发票ID */
    private Long relatedInvoiceId;

    /** 被红冲原发票号（冗余，便于展示） */
    private String relatedInvoiceNo;

    /** 发票状态：UNCHECKED/CHECKED/PAID/RED_FLUSHED */
    private String invoiceStatus;

    private String remark;

    private Long createBy;

    private LocalDateTime createTime;

    private Long updateBy;

    private LocalDateTime updateTime;

    private Integer deleted;
}

package com.yonerp.po.invoice.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 创建发票请求
 */
@Data
public class InvoiceCreateCmd {

    /** 收货单ID */
    private Long receiptId;

    /** 开票日期 */
    private LocalDate invoiceDate;

    /** 不含税金额（退票重开等场景可手工录入；为空则按收货单自动计算） */
    private BigDecimal invoiceAmount;

    /** 税额（为空则按收货单行级税额自动计算） */
    private BigDecimal taxAmount;

    /** 发票类型：BLUE 蓝字(默认) / RED 红字(红冲) */
    private String invoiceType;

    /** 红蓝关联：红字发票指向被红冲的原发票ID（创建红字时由后端填充） */
    private Long relatedInvoiceId;

    /** 备注 */
    private String remark;
}

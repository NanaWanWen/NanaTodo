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

    /** 备注 */
    private String remark;
}

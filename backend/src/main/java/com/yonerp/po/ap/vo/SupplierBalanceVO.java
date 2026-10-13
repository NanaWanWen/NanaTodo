package com.yonerp.po.ap.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 供应商应付余额 VO
 */
@Data
public class SupplierBalanceVO {

    private Long supplierId;

    private String supplierName;

    /** 未付款发票金额合计 */
    private BigDecimal unpaidAmount;

    /** 未付款发票数 */
    private long unpaidInvoiceCount;

    /** 已付款金额合计 */
    private BigDecimal paidAmount;

    /** 应付余额 = 未付款 - 已付款 */
    private BigDecimal balance;
}

package com.yonerp.po.invoice.dto;

import lombok.Data;

/**
 * 发票分页查询
 */
@Data
public class InvoiceQuery {

    private String keyword;

    private String invoiceStatus;

    private Long supplierId;

    private Long orderId;

    private long pageNo;

    private long pageSize;
}

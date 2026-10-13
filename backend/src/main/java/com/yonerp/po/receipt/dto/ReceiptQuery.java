package com.yonerp.po.receipt.dto;

import lombok.Data;

/**
 * 收货单分页查询
 */
@Data
public class ReceiptQuery {

    private String keyword;

    private String status;

    private Long supplierId;

    private Long orderId;

    private long pageNo;

    private long pageSize;
}

package com.yonerp.po.payment.dto;

import lombok.Data;

/**
 * 付款单分页查询
 */
@Data
public class PaymentQuery {

    private String keyword;

    private String status;

    private Long supplierId;

    private Long orderId;

    private long pageNo;

    private long pageSize;
}

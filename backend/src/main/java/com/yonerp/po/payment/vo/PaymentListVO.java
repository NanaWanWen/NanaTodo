package com.yonerp.po.payment.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 付款单列表 VO
 */
@Data
public class PaymentListVO {

    private Long id;

    private String paymentNo;

    private Long invoiceId;

    private String invoiceNo;

    private Long orderId;

    private String orderNo;

    private Long supplierId;

    private String supplierName;

    private LocalDate paymentDate;

    private BigDecimal paymentAmount;

    private String paymentMethod;

    private String status;

    private LocalDateTime createTime;
}

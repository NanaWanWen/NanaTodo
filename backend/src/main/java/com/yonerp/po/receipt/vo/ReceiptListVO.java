package com.yonerp.po.receipt.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 收货单列表 VO
 */
@Data
public class ReceiptListVO {

    private Long id;

    private String receiptNo;

    private Long orderId;

    private String orderNo;

    private Long supplierId;

    private String supplierName;

    private LocalDate receiptDate;

    private BigDecimal totalQty;

    private BigDecimal totalAmount;

    private String status;

    private LocalDateTime createTime;
}

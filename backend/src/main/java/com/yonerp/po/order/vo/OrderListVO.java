package com.yonerp.po.order.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 订单列表视图
 */
@Data
public class OrderListVO {

    private Long id;

    private String orderNo;

    private String title;

    private Long supplierId;

    private String supplierName;

    private Long buyerId;

    private String buyerName;

    private LocalDate orderDate;

    private BigDecimal totalQty;

    private BigDecimal totalAmount;

    private String status;

    private Integer currentNode;

    private LocalDateTime createTime;
}

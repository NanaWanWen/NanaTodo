package com.yonerp.po.order.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单明细视图
 */
@Data
public class OrderItemVO {

    private Long id;

    private Integer lineNo;

    private Long materialId;

    private String materialName;

    private String spec;

    private String unit;

    private BigDecimal qty;

    private BigDecimal price;

    private BigDecimal taxRate;

    private BigDecimal amount;

    private String remark;
}

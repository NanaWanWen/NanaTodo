package com.yonerp.po.order.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单明细行请求（金额由服务端重算，不接受前端传入的金额）
 */
@Data
public class OrderItemRequest {

    private Long id;

    /** 行号：10/20/30 */
    private Integer lineNo;

    @NotNull(message = "物料不能为空")
    private Long materialId;

    @NotNull(message = "数量不能为空")
    @DecimalMin(value = "0.001", message = "数量必须大于 0")
    private BigDecimal qty;

    @NotNull(message = "单价不能为空")
    @DecimalMin(value = "0.01", message = "单价必须大于 0")
    private BigDecimal price;

    /** 税率(%) */
    private BigDecimal taxRate;

    private String remark;
}

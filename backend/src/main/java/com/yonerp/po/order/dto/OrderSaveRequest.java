package com.yonerp.po.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 订单保存请求（新建与编辑共用）
 */
@Data
public class OrderSaveRequest {

    @NotBlank(message = "订单标题不能为空")
    @Size(max = 128, message = "订单标题长度不能超过 128")
    private String title;

    @NotNull(message = "供应商不能为空")
    private Long supplierId;

    private String currency;

    @NotNull(message = "订单日期不能为空")
    private LocalDate orderDate;

    private LocalDate expectDate;

    private String remark;

    /** 乐观锁版本号（编辑时必填） */
    private Integer version;

    @Valid
    private List<OrderItemRequest> items;
}

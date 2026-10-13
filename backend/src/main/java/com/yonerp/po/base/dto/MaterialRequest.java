package com.yonerp.po.base.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 物料保存请求
 */
@Data
public class MaterialRequest {

    private Long id;

    @NotBlank(message = "物料编码不能为空")
    @Size(max = 32, message = "物料编码长度不能超过 32")
    private String materialCode;

    @NotBlank(message = "物料名称不能为空")
    @Size(max = 128, message = "物料名称长度不能超过 128")
    private String materialName;

    private String spec;

    @NotBlank(message = "计量单位不能为空")
    private String unit;

    private String category;

    @DecimalMin(value = "0", message = "参考单价不能为负数")
    private BigDecimal refPrice;

    @DecimalMin(value = "0", message = "税率不能为负数")
    private BigDecimal taxRate;

    private Integer status;
}

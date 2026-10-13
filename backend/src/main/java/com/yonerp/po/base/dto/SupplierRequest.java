package com.yonerp.po.base.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 供应商保存请求
 */
@Data
public class SupplierRequest {

    private Long id;

    @NotBlank(message = "供应商编码不能为空")
    @Size(max = 32, message = "供应商编码长度不能超过 32")
    private String supplierCode;

    @NotBlank(message = "供应商名称不能为空")
    @Size(max = 128, message = "供应商名称长度不能超过 128")
    private String supplierName;

    private String contactName;

    private String contactPhone;

    private String address;

    private Integer status;
}

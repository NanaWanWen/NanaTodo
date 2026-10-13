package com.yonerp.po.base.vo;

import lombok.Data;

/**
 * 供应商视图
 */
@Data
public class SupplierVO {

    private Long id;

    private String supplierCode;

    private String supplierName;

    private String contactName;

    private String contactPhone;

    private String address;

    private Integer status;
}

package com.yonerp.po.base.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 供应商
 */
@Data
@TableName("base_supplier")
public class Supplier {

    private Long id;

    private String supplierCode;

    private String supplierName;

    private String contactName;

    private String contactPhone;

    private String address;

    private Integer status;

    private Long createBy;

    private LocalDateTime createTime;

    private Long updateBy;

    private LocalDateTime updateTime;

    private Integer deleted;
}

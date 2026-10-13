package com.yonerp.po.base.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 物料视图
 */
@Data
public class MaterialVO {

    private Long id;

    private String materialCode;

    private String materialName;

    private String spec;

    private String unit;

    private String category;

    private BigDecimal refPrice;

    private BigDecimal taxRate;

    private Integer status;
}

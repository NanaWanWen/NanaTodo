package com.yonerp.po.base.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 物料
 */
@Data
@TableName("base_material")
public class Material {

    private Long id;

    private String materialCode;

    private String materialName;

    private String spec;

    private String unit;

    private String category;

    /** 参考单价（不含税） */
    private BigDecimal refPrice;

    /** 默认税率 */
    private BigDecimal taxRate;

    private Integer status;

    private Long createBy;

    private LocalDateTime createTime;

    private Long updateBy;

    private LocalDateTime updateTime;

    private Integer deleted;
}

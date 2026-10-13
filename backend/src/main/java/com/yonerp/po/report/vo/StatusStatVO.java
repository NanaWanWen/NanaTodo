package com.yonerp.po.report.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 按状态统计（饼图）
 */
@Data
public class StatusStatVO {

    private String status;

    private String statusName;

    private Long count;

    private BigDecimal amount;

    /** 图表色值 */
    private String color;
}

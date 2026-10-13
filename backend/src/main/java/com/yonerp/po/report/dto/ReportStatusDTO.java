package com.yonerp.po.report.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 按状态统计结果（SQL 聚合）
 */
@Data
public class ReportStatusDTO {

    private String status;

    private Long count;

    private BigDecimal amount;
}

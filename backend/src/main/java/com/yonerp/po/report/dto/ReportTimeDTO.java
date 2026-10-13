package com.yonerp.po.report.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 按时间统计结果（SQL 聚合）
 */
@Data
public class ReportTimeDTO {

    private String period;

    private Long count;

    private BigDecimal amount;
}

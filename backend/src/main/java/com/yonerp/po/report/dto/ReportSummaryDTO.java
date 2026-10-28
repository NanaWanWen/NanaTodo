package com.yonerp.po.report.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 汇总统计结果（SQL 聚合）
 */
@Data
public class ReportSummaryDTO {

    private Long totalCount;

    private BigDecimal totalAmount;

    private Long draftCount;

    private Long approvingCount;

    private BigDecimal approvingAmount;

    private Long approvedCount;

    private Long rejectedCount;

    private Long canceledCount;

    private Long completedCount;

    private BigDecimal completedAmount;
}

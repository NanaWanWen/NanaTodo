package com.yonerp.po.report.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 报表汇总卡
 */
@Data
public class ReportSummaryVO {

    private Long totalCount;

    private BigDecimal totalAmount;

    private Long draftCount;

    private Long approvingCount;

    private BigDecimal approvingAmount;

    private Long approvedCount;

    private Long rejectedCount;

    private Long canceledCount;
}

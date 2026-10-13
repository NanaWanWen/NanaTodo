package com.yonerp.po.agent.dto;

import lombok.Data;

/**
 * AI 建议 VO（辅助审批/发票校验/付款建议）
 */
@Data
public class SuggestionVO {

    private String type;       // APPROVAL / INVOICE_CHECK / PAYMENT
    private Long refId;
    private String refNo;
    private String suggestion;
    private String risk;       // LOW / MEDIUM / HIGH
    private Boolean aiGenerated;
}

package com.yonerp.po.agent.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 预警处理流水 VO（审计时间线）
 */
@Data
public class AlertActionVO {

    private Long id;

    private String alertId;

    /** CREATE/READ/ASSIGN/TRANSFER/HANDLE/IGNORE/RESTORE/ESCALATE */
    private String action;

    private String fromUser;

    private String toUser;

    private String operator;

    private Long operatorId;

    private String note;

    private LocalDateTime createTime;
}

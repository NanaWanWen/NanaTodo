package com.yonerp.po.agent.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 预警通知 VO
 */
@Data
public class AgentAlertVO {

    private String alertId;

    /** 预警类型：OVERDUE_ORDER / LOW_STOCK / PRICE_ABNORMAL */
    private String alertType;

    /** 严重级别：INFO / WARNING / CRITICAL */
    private String level;

    private String title;

    private String content;

    private Long refId;

    private String refNo;

    private LocalDateTime createTime;

    private Boolean handled;
}

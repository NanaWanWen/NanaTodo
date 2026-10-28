package com.yonerp.po.agent.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 预警通知 VO
 */
@Data
public class AgentAlertVO {

    private String alertId;

    /** 预警类型：OVERDUE_ORDER / LOW_STOCK / PRICE_ABNORMAL 等 */
    private String alertType;

    /** 业务域 PURCHASE/INVENTORY/FINANCE */
    private String bizDomain;

    /** 命中规则编码 */
    private String ruleCode;

    /** 命中规则ID */
    private Long ruleId;

    /** 严重级别：INFO / WARNING / CRITICAL */
    private String level;

    private String title;

    private String content;

    private Long refId;

    private String refNo;

    /** 引用单据类型 ORDER/RECEIPT/INVOICE/PAYMENT/MATERIAL */
    private String refType;

    private LocalDateTime createTime;

    private Boolean handled;

    private Boolean ignored;

    /** 是否已读 */
    private Boolean readFlag;

    /** 当前责任人用户ID */
    private Long assigneeId;

    /** 当前责任人 */
    private String assignee;

    private String handleBy;

    private LocalDateTime handleTime;

    /** 处理意见 */
    private String handleNote;

    private String actionPath;

    /** 处理流水（审计时间线） */
    private List<AlertActionVO> actions;
}

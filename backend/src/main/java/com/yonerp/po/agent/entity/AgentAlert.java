package com.yonerp.po.agent.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 预警记录（持久化，重启不丢、可追溯历史）
 */
@Data
@TableName("agent_alert")
public class AgentAlert {

    /** 预警ID（UUID） */
    @TableId
    private String alertId;

    /** 预警类型：OVERDUE_ORDER / LOW_STOCK / PRICE_ABNORMAL / SYSTEM 等 */
    private String alertType;

    /** 业务域：PURCHASE / INVENTORY / FINANCE */
    private String bizDomain;

    /** 命中规则编码 */
    private String ruleCode;

    /** 命中规则ID */
    private Long ruleId;

    /** 严重级别：INFO / WARNING / CRITICAL */
    private String level;

    private String title;

    private String content;

    /** 业务引用ID（订单ID / 物料ID） */
    private Long refId;

    /** 业务引用编号（订单号 / 物料编码） */
    private String refNo;

    /** 引用单据类型 ORDER/RECEIPT/INVOICE/PAYMENT/MATERIAL */
    private String refType;

    /** 前端跳转路径 */
    private String actionPath;

    /** 是否已处理：0未处理 1已处理 */
    private Integer handled;

    /** 是否已忽略（不再显示）：0显示 1已忽略 */
    private Integer ignored;

    /** 是否已读：0未读 1已读 */
    private Integer readFlag;

    /** 当前责任人用户ID */
    private Long assigneeId;

    /** 当前责任人 */
    private String assignee;

    private String handleBy;

    private LocalDateTime handleTime;

    /** 处理意见 */
    private String handleNote;

    /** 是否已推送通知：0否 1是 */
    private Integer notified;

    private LocalDateTime createTime;
}

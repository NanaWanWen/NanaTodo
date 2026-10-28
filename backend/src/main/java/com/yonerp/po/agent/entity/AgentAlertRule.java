package com.yonerp.po.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 预警规则配置（业务角色自助维护：阈值/级别/启停/通知对象）
 */
@Data
@TableName("agent_alert_rule")
public class AgentAlertRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 规则编码，与评估器绑定 */
    private String ruleCode;

    /** 规则名称 */
    private String ruleName;

    /** 业务域 PURCHASE/INVENTORY/FINANCE */
    private String bizDomain;

    /** 预警类型（与 agent_alert.alert_type 对应） */
    private String alertType;

    /** 预警级别 INFO/WARNING/CRITICAL */
    private String level;

    /** 阈值单位 DAY/PERCENT/AMOUNT/QTY */
    private String thresholdUnit;

    /** 阈值 */
    private BigDecimal threshold;

    /** 扩展参数(JSON) */
    private String extraConfig;

    /** 是否启用 0停用 1启用 */
    private Integer enabled;

    /** 扫描周期（留空用全局周期） */
    private String scanCron;

    /** 通知渠道，逗号分隔 INAPP/EMAIL/SSE */
    private String notifyChannel;

    /** 通知角色，逗号分隔 */
    private String notifyRoles;

    /** 规则归属角色（自助配置） */
    private String ownerRole;

    /** 备注 */
    private String remark;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}

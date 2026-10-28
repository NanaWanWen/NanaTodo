package com.yonerp.po.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 预警处理流水（指派/转办/处理/忽略/恢复，完整审计）
 */
@Data
@TableName("agent_alert_action")
public class AgentAlertAction {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 预警ID */
    private String alertId;

    /** CREATE/READ/ASSIGN/TRANSFER/HANDLE/IGNORE/RESTORE/ESCALATE */
    private String action;

    /** 操作前责任人 */
    private String fromUser;

    /** 操作后责任人 */
    private String toUser;

    /** 操作人 */
    private String operator;

    /** 操作人ID */
    private Long operatorId;

    /** 处理意见/备注 */
    private String note;

    /** 操作时间 */
    private LocalDateTime createTime;
}

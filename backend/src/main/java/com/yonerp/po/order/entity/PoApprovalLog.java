package com.yonerp.po.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批流水（历史留痕）
 */
@Data
@TableName("po_approval_log")
public class PoApprovalLog {

    private Long id;

    private Long orderId;

    private Integer nodeSeq;

    private String nodeRole;

    private Long operatorId;

    /** 动作：见 ApprovalAction */
    private String action;

    private String fromStatus;

    private String toStatus;

    private String opinion;

    private LocalDateTime operateTime;
}

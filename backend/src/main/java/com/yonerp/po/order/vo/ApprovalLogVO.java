package com.yonerp.po.order.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批流水视图（详情页时间线）
 */
@Data
public class ApprovalLogVO {

    private Long id;

    private Integer nodeSeq;

    private String nodeRole;

    private String action;

    private String actionName;

    private String fromStatus;

    private String toStatus;

    private String operatorName;

    private String opinion;

    private LocalDateTime operateTime;
}

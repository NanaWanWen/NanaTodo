package com.yonerp.po.order.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批任务视图
 */
@Data
public class ApprovalTaskVO {

    private Long id;

    private Integer nodeSeq;

    private String nodeRole;

    private String nodeName;

    private Long approverId;

    private String approverName;

    private String status;

    private LocalDateTime approveTime;

    private String opinion;
}

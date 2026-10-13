package com.yonerp.po.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批任务（当前节点待办）
 */
@Data
@TableName("po_approval_task")
public class PoApprovalTask {

    private Long id;

    private Long orderId;

    private Integer nodeSeq;

    private String nodeRole;

    private String nodeName;

    private Long approverId;

    /** 任务状态：见 TaskStatus */
    private String status;

    private LocalDateTime approveTime;

    private String opinion;

    @Version
    private Integer version;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

package com.yonerp.po.approval.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 待办审批视图
 */
@Data
public class ApprovalTodoVO {

    private Long taskId;

    private Long orderId;

    private String orderNo;

    private String title;

    private BigDecimal totalAmount;

    private String buyerName;

    private LocalDateTime submitTime;

    private Integer nodeSeq;

    private String nodeName;

    /** 是否超时（超过阈值小时仍未审批） */
    private boolean overtime;
}

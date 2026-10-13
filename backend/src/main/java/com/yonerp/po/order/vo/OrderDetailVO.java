package com.yonerp.po.order.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 订单详情视图：含明细、审批任务、审批流水与可执行动作
 */
@Data
public class OrderDetailVO {

    private Long id;

    private String orderNo;

    private String title;

    private Long supplierId;

    private String supplierName;

    private Long buyerId;

    private String buyerName;

    private String currency;

    private LocalDate orderDate;

    private LocalDate expectDate;

    private BigDecimal totalQty;

    private BigDecimal totalAmount;

    private String status;

    private Integer currentNode;

    private Integer version;

    private String remark;

    private LocalDateTime submitTime;

    private LocalDateTime finishTime;

    private LocalDateTime createTime;

    /** 物料明细 */
    private List<OrderItemVO> items = new ArrayList<>();

    /** 审批任务 */
    private List<ApprovalTaskVO> tasks = new ArrayList<>();

    /** 审批流水 */
    private List<ApprovalLogVO> logs = new ArrayList<>();

    /** 当前用户可执行的动作，前端据此渲染按钮 */
    private List<String> actions = new ArrayList<>();
}

package com.yonerp.po.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 采购订单主表
 */
@Data
@TableName("po_order")
public class PoOrder {

    private Long id;

    private String orderNo;

    private String title;

    private Long supplierId;

    private Long buyerId;

    private Long deptId;

    private String currency;

    private LocalDate orderDate;

    private LocalDate expectDate;

    private BigDecimal totalQty;

    private BigDecimal totalAmount;

    /** 订单状态：见 OrderStatus */
    private String status;

    /** 当前审批节点序号 */
    private Integer currentNode;

    private LocalDateTime submitTime;

    private LocalDateTime finishTime;

    @Version
    private Integer version;

    private String remark;

    private Long createBy;

    private LocalDateTime createTime;

    private Long updateBy;

    private LocalDateTime updateTime;

    private Integer deleted;
}

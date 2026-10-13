package com.yonerp.po.receipt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 采购收货单主表
 */
@Data
@TableName("po_receipt")
public class PoReceipt {

    private Long id;

    /** 收货单编号 RCPTyyyyMMddNNNN */
    private String receiptNo;

    /** 订单ID */
    private Long orderId;

    /** 供应商ID(冗余) */
    private Long supplierId;

    /** 收货日期 */
    private LocalDate receiptDate;

    /** 实收数量合计 */
    private BigDecimal totalQty;

    /** 含税金额合计 */
    private BigDecimal totalAmount;

    /** 状态：DRAFT/CONFIRMED/CANCELED */
    private String status;

    private String remark;

    private Long createBy;

    private LocalDateTime createTime;

    private Long updateBy;

    private LocalDateTime updateTime;

    private Integer deleted;
}

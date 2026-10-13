package com.yonerp.po.payment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 付款单
 */
@Data
@TableName("po_payment")
public class PoPayment {

    private Long id;

    /** 付款单编号 PAYyyyyMMddNNNN */
    private String paymentNo;

    /** 发票ID */
    private Long invoiceId;

    /** 订单ID(冗余) */
    private Long orderId;

    /** 供应商ID(冗余) */
    private Long supplierId;

    /** 付款日期 */
    private LocalDate paymentDate;

    /** 付款金额 */
    private BigDecimal paymentAmount;

    /** 付款方式：BANK_TRANSFER/ALIPAY/WECHAT/CASH */
    private String paymentMethod;

    /** 状态：PENDING/PAID/REJECTED */
    private String status;

    private String remark;

    private Long createBy;

    private LocalDateTime createTime;

    private Long updateBy;

    private LocalDateTime updateTime;

    private Integer deleted;
}

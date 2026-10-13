package com.yonerp.po.ap.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 应付对账 / 三单匹配
 */
@Data
@TableName("po_ap_settlement")
public class PoApSettlement {

    private Long id;

    /** 订单ID */
    private Long orderId;

    /** 收货单ID */
    private Long receiptId;

    /** 发票ID */
    private Long invoiceId;

    /** 订单金额 */
    private BigDecimal orderAmount;

    /** 收货金额 */
    private BigDecimal receiptAmount;

    /** 发票金额 */
    private BigDecimal invoiceAmount;

    /** 差异金额(发票-收货) */
    private BigDecimal diffAmount;

    /** 匹配状态：UNMATCHED/MATCHED/PARTIAL */
    private String matchStatus;

    /** 容差金额 */
    private BigDecimal tolerance;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer deleted;
}

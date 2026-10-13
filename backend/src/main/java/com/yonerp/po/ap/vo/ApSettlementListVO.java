package com.yonerp.po.ap.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 应付对账列表 VO
 */
@Data
public class ApSettlementListVO {

    private Long id;

    private Long orderId;

    private String orderNo;

    private Long receiptId;

    private String receiptNo;

    private Long invoiceId;

    private String invoiceNo;

    private BigDecimal orderAmount;

    private BigDecimal receiptAmount;

    private BigDecimal invoiceAmount;

    private BigDecimal diffAmount;

    private String matchStatus;

    private BigDecimal tolerance;

    private String remark;

    private LocalDateTime updateTime;
}

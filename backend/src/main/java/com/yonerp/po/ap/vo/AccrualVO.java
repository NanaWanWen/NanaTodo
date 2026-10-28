package com.yonerp.po.ap.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 暂估应付列表 VO（回填关联单据名称，保证列表数据完整可读）
 */
@Data
public class AccrualVO {

    private Long id;

    /** 暂估单号 */
    private String accrualNo;

    private Long receiptId;

    private String receiptNo;

    private Long orderId;

    private String orderNo;

    private Long supplierId;

    private String supplierName;

    private Long materialId;

    private String materialCode;

    private String materialName;

    private BigDecimal accrualQty;

    private BigDecimal accrualPrice;

    private BigDecimal accrualAmount;

    private LocalDate accrualDate;

    private String accrualPeriod;

    /** ACCRUED / REVERSED */
    private String status;

    private Long reverseInvoiceId;

    private String reverseInvoiceNo;

    private LocalDate reverseDate;

    private BigDecimal reverseAmount;

    private BigDecimal diffAmount;

    /** SINGLE / MONTHLY */
    private String accrualMode;

    private String remark;

    private LocalDateTime createTime;
}

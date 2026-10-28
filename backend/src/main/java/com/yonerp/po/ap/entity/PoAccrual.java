package com.yonerp.po.ap.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 暂估应付
 */
@Data
@TableName("po_accrual")
public class PoAccrual {

    private Long id;

    /** 暂估单号 ACCyyyyMMddNNNN */
    private String accrualNo;

    /** 收货单ID */
    private Long receiptId;

    /** 订单ID */
    private Long orderId;

    /** 供应商ID */
    private Long supplierId;

    /** 物料ID */
    private Long materialId;

    /** 暂估数量 */
    private BigDecimal accrualQty;

    /** 暂估单价 */
    private BigDecimal accrualPrice;

    /** 暂估金额 */
    private BigDecimal accrualAmount;

    /** 暂估日期 */
    private LocalDate accrualDate;

    /** 暂估期间（yyyy-MM） */
    private String accrualPeriod;

    /** 状态：ACCRUED/REVERSED */
    private String status;

    /** 回冲发票ID */
    private Long reverseInvoiceId;

    /** 回冲日期 */
    private LocalDate reverseDate;

    /** 回冲金额 */
    private BigDecimal reverseAmount;

    /** 暂估与实际差额 */
    private BigDecimal diffAmount;

    /** 暂估回冲方式：SINGLE/MONTHLY */
    private String accrualMode;

    private String remark;

    private Long createBy;

    private LocalDateTime createTime;

    private Long updateBy;

    private LocalDateTime updateTime;

    private Integer deleted;
}

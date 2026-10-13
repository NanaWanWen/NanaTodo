package com.yonerp.po.receipt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 采购收货单明细
 */
@Data
@TableName("po_receipt_item")
public class PoReceiptItem {

    private Long id;

    /** 收货单ID */
    private Long receiptId;

    /** 订单明细ID */
    private Long orderItemId;

    /** 物料ID */
    private Long materialId;

    /** 物料名称(冗余快照) */
    private String materialName;

    /** 规格(冗余) */
    private String spec;

    /** 单位(冗余) */
    private String unit;

    /** 订购数量 */
    private BigDecimal orderedQty;

    /** 实收数量 */
    private BigDecimal receivedQty;

    /** 不含税单价(冗余快照) */
    private BigDecimal unitPrice;

    /** 税率(%) */
    private BigDecimal taxRate;

    /** 税额 */
    private BigDecimal taxAmount;

    /** 含税金额 */
    private BigDecimal amount;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer deleted;
}

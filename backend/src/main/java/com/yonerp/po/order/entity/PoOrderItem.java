package com.yonerp.po.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 采购订单明细
 */
@Data
@TableName("po_order_item")
public class PoOrderItem {

    private Long id;

    private Long orderId;

    private Integer lineNo;

    private Long materialId;

    /** 物料名称（冗余快照） */
    private String materialName;

    private String spec;

    private String unit;

    private BigDecimal qty;

    /** 不含税单价 */
    private BigDecimal price;

    /** 税率(%) */
    private BigDecimal taxRate;

    /** 含税金额 */
    private BigDecimal amount;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer deleted;
}

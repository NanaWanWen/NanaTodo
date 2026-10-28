package com.yonerp.po.common.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 业务单据选项 VO（用于选择器下拉）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BizOption {

    /** 业务 ID */
    private Long id;

    /** 业务编号 */
    private String no;

    /** 主标签（如供应商名称、订单标题） */
    private String label;

    /** 副标签（如日期、金额） */
    private String subLabel;

    /** 状态 */
    private String status;

    /** 状态名称 */
    private String statusName;

    /** 金额（可选） */
    private BigDecimal amount;

    /** 日期（可选） */
    private String date;

    /** 供应商名称（可选） */
    private String supplierName;

    /** 采购员姓名（可选） */
    private String buyerName;

    /** 未收/未付数量或金额（可选，用于业务提示） */
    private BigDecimal remainQty;
}
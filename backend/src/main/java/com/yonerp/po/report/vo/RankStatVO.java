package com.yonerp.po.report.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 维度排行单行（供前端统计表与 TOP N 图表使用）
 */
@Data
public class RankStatVO {

    private String code;

    private String name;

    /** 订单数 */
    private Long orderCount;

    /** 数量合计 */
    private BigDecimal qty;

    /** 含税金额合计 */
    private BigDecimal amount;

    /** 金额占比（百分比，保留 2 位小数） */
    private BigDecimal percent;

    /** 穿透联查用的维度标识，前端用于拼 /orders?supplierId=xx */
    private String linkValue;
}

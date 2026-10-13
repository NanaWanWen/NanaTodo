package com.yonerp.po.report.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 维度排行统计结果（SQL 聚合）
 * groupBy = supplier / buyer / material / dept
 */
@Data
public class ReportRankDTO {

    /** 维度主键：供应商ID / 用户ID / 物料ID / 部门ID，供前端穿透联查 */
    private Long dimId;

    /** 维度编码：供应商编码 / 登录名 / 物料编码 / 部门字典码 */
    private String code;

    /** 维度名称：供应商名称 / 姓名 / 物料名称 / 部门名称 */
    private String name;

    /** 订单数 */
    private Long orderCount;

    /** 数量合计（物料维度取明细数量，其余取主表数量合计） */
    private BigDecimal qty;

    /** 含税金额合计 */
    private BigDecimal amount;
}

package com.yonerp.po.order.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 订单列表查询条件
 */
@Data
public class OrderQuery {

    /** 关键字：订单编号或标题 */
    private String keyword;

    /** 订单状态 */
    private String status;

    private Long supplierId;

    private Long buyerId;

    /** 部门ID（报表穿透联查） */
    private Long deptId;

    /** 物料ID（报表穿透联查，按明细匹配） */
    private Long materialId;

    private LocalDate startDate;

    private LocalDate endDate;

    private long pageNo = 1;

    private long pageSize = 20;
}

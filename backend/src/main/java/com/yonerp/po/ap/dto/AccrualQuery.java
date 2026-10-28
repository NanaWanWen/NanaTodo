package com.yonerp.po.ap.dto;

import lombok.Data;

/**
 * 暂估应付分页查询（用友式）
 */
@Data
public class AccrualQuery {

    /** 暂估期间（yyyy-MM） */
    private String period;

    /** 状态：ACCRUED / REVERSED */
    private String status;

    private Long supplierId;

    private Long orderId;

    /** 关键字（暂估单号） */
    private String keyword;

    private long pageNo = 1;

    private long pageSize = 20;
}

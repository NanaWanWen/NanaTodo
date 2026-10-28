package com.yonerp.po.ap.dto;

import lombok.Data;

/**
 * 应付对账分页查询（用友式）
 */
@Data
public class ApQuery {

    private Long orderId;

    private String matchStatus;

    private String matchType;

    private String diffType;

    private Long supplierId;

    private String keyword;

    private long pageNo;

    private long pageSize;
}

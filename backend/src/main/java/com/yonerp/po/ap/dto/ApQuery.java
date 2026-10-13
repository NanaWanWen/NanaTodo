package com.yonerp.po.ap.dto;

import lombok.Data;

/**
 * 应付对账分页查询
 */
@Data
public class ApQuery {

    private Long orderId;

    private String matchStatus;

    private Long supplierId;

    private long pageNo;

    private long pageSize;
}

package com.yonerp.po.receipt.service;

import com.yonerp.po.common.PageResult;
import com.yonerp.po.receipt.dto.ReceiptCreateCmd;
import com.yonerp.po.receipt.dto.ReceiptQuery;
import com.yonerp.po.receipt.vo.ReceiptDetailVO;
import com.yonerp.po.receipt.vo.ReceiptListVO;

/**
 * 采购收货服务
 */
public interface ReceiptService {

    /**
     * 分页列表
     */
    PageResult<ReceiptListVO> page(ReceiptQuery query);

    /**
     * 详情（含明细）
     */
    ReceiptDetailVO detail(Long id);

    /**
     * 创建收货单（关联订单，自动加库存）
     * 仅订单状态为 APPROVED 或 COMPLETED 时可创建
     *
     * @return 收货单 ID
     */
    Long create(ReceiptCreateCmd cmd);

    /**
     * 确认收货（DRAFT → CONFIRMED，触发库存入库）
     */
    void confirm(Long id);

    /**
     * 取消收货（DRAFT → CANCELED）
     */
    void cancel(Long id);
}

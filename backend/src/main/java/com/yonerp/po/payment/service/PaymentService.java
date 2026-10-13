package com.yonerp.po.payment.service;

import com.yonerp.po.common.PageResult;
import com.yonerp.po.payment.dto.PaymentCreateCmd;
import com.yonerp.po.payment.dto.PaymentQuery;
import com.yonerp.po.payment.vo.PaymentListVO;

/**
 * 付款单服务
 */
public interface PaymentService {

    /**
     * 分页列表
     */
    PageResult<PaymentListVO> page(PaymentQuery query);

    /**
     * 创建付款单（基于发票）
     *
     * @return 付款单 ID
     */
    Long create(PaymentCreateCmd cmd);

    /**
     * 确认付款（PENDING → PAID，发票状态同步为 PAID）
     */
    void confirm(Long id);

    /**
     * 驳回付款（PENDING → REJECTED）
     */
    void reject(Long id);
}

package com.yonerp.po.ap.service;

import com.yonerp.po.ap.dto.ApQuery;
import com.yonerp.po.ap.vo.ApSettlementListVO;
import com.yonerp.po.ap.vo.SupplierBalanceVO;
import com.yonerp.po.common.PageResult;

import java.util.List;

/**
 * 应付对账 / 三单匹配服务
 */
public interface ApSettlementService {

    /**
     * 分页列表
     */
    PageResult<ApSettlementListVO> page(ApQuery query);

    /**
     * 为指定订单执行三单匹配（订单-收货-发票金额校验，1% 容差）
     * 若订单尚无收货单或发票则跳过
     */
    void match(Long orderId);

    /**
     * 供应商应付余额
     */
    List<SupplierBalanceVO> supplierBalances();
}

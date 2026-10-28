package com.yonerp.po.ap.service;

import com.yonerp.po.ap.dto.ApQuery;
import com.yonerp.po.ap.dto.ForceSettleCmd;
import com.yonerp.po.ap.dto.ManualSettleCmd;
import com.yonerp.po.ap.dto.ResolveCmd;
import com.yonerp.po.ap.entity.PoApSettlement;
import com.yonerp.po.ap.vo.ApSettlementListVO;
import com.yonerp.po.ap.vo.SupplierBalanceVO;
import com.yonerp.po.common.PageResult;

import java.util.List;

/**
 * 应付对账 / 三单匹配服务（用友式）
 */
public interface ApSettlementService {

    /**
     * 分页列表
     */
    PageResult<ApSettlementListVO> page(ApQuery query);

    /**
     * 为指定订单执行三单匹配（兼容旧接口，内部调用 MatchEngine）
     */
    void match(Long orderId);

    /**
     * 批量自动结算（定时任务调用）
     */
    List<PoApSettlement> autoSettle(List<Long> orderIds);

    /**
     * 手工结算（前端勾选勾对）
     */
    PoApSettlement manualSettle(ManualSettleCmd cmd);

    /**
     * 强制匹配（需审批）
     */
    void forceSettle(Long settlementId, ForceSettleCmd cmd);

    /**
     * 差异处理（补收/退票/调价/损耗放行）
     */
    void resolveDiff(Long settlementId, ResolveCmd cmd);

    /**
     * 供应商应付余额
     */
    List<SupplierBalanceVO> supplierBalances();
}

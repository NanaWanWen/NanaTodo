package com.yonerp.po.ap.service;

import com.yonerp.po.ap.entity.PoApSettlement;

import java.util.List;

/**
 * 匹配引擎（用友式）
 */
public interface MatchEngine {

    /**
     * 按订单维度匹配（兼容旧接口）
     */
    PoApSettlement matchByOrder(Long orderId);

    /**
     * 按供应商+物料建数量池多对多勾对
     * （当前数据模型发票为主表级，池内以订单为最小勾对单元聚合，返回本池全部结算单）
     */
    List<PoApSettlement> matchByPool(Long supplierId, Long materialId);

    /**
     * 批量自动结算
     */
    List<PoApSettlement> matchBatch(List<Long> orderIds);

    /**
     * 生成结算单号
     */
    String generateMatchNo();
}

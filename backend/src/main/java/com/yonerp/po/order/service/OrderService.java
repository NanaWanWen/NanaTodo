package com.yonerp.po.order.service;

import com.yonerp.po.common.PageResult;
import com.yonerp.po.order.dto.OrderQuery;
import com.yonerp.po.order.dto.OrderSaveRequest;
import com.yonerp.po.order.entity.PoOrder;
import com.yonerp.po.order.vo.OrderDetailVO;
import com.yonerp.po.order.vo.OrderListVO;

import java.util.List;

/**
 * 采购订单服务
 */
public interface OrderService {

    /**
     * 分页列表（采购员仅看本人）
     */
    PageResult<OrderListVO> page(OrderQuery query);

    /**
     * 详情（含明细、审批任务、流水与可执行动作）
     */
    OrderDetailVO detail(Long id);

    /**
     * 新建草稿
     */
    Long create(OrderSaveRequest request);

    /**
     * 编辑（仅草稿或已驳回）
     */
    void update(Long id, OrderSaveRequest request);

    /**
     * 删除（仅草稿）
     */
    void delete(Long id);

    /**
     * 批量删除（仅草稿）
     */
    void deleteBatch(List<Long> ids);

    /**
     * 获取订单实体（供审批服务内部使用）
     */
    PoOrder getEntity(Long id);
}

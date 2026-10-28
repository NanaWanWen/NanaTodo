package com.yonerp.po.order.service;

import com.yonerp.po.common.PageResult;
import com.yonerp.po.order.dto.OrderItemRequest;
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
     * 调价（订单变更/修订）：仅调整明细单价并重算金额，触发三单匹配重算
     * （支撑差异处理「调价」动作闭环，不校验草稿状态）
     */
    void adjustPrice(Long id, List<OrderItemRequest> items);

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

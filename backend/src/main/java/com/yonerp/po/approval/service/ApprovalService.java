package com.yonerp.po.approval.service;

import com.yonerp.po.common.PageResult;
import com.yonerp.po.order.dto.ActionRequest;
import com.yonerp.po.approval.vo.ApprovalTodoVO;
import com.yonerp.po.order.vo.OrderDetailVO;

/**
 * 审批服务：状态流转与两级审批
 */
public interface ApprovalService {

    /**
     * 提交审批（草稿 / 已驳回 → 审批中）
     */
    void submit(Long orderId, ActionRequest request);

    /**
     * 撤回（审批中 → 草稿）
     */
    void withdraw(Long orderId, ActionRequest request);

    /**
     * 取消（草稿 / 已驳回 / 已审批管理员取消 → 已取消）
     */
    void cancel(Long orderId, ActionRequest request);

    /**
     * 审批通过
     */
    void approve(Long taskId, ActionRequest request);

    /**
     * 审批驳回（意见必填）
     */
    void reject(Long taskId, ActionRequest request);

    /**
     * 我的待办（按当前角色过滤节点）
     */
    PageResult<ApprovalTodoVO> todo(String keyword, long pageNo, long pageSize);

    /**
     * 当前角色待办数量（角标，带缓存）
     */
    long todoCount();

    /**
     * 提交后的订单详情（供前端刷新）
     */
    OrderDetailVO detailAfterAction(Long orderId);
}

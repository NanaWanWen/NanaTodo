package com.yonerp.po.approval;

import com.yonerp.po.common.BizException;
import com.yonerp.po.common.enums.OrderStatus;
import org.springframework.stereotype.Component;

/**
 * 订单状态机：状态流转规则的唯一校验入口（后端唯一真源）
 *
 * <pre>
 * DRAFT ──提交──> APPROVING ──两级通过──> APPROVED
 *   ^                │  └──驳回──> REJECTED ──重新提交──> APPROVING
 *   └──── 撤回 ───────┘
 * DRAFT / REJECTED ──取消──> CANCELED ；APPROVED ──异常取消(仅管理员)──> CANCELED
 * </pre>
 */
@Component
public class OrderStateMachine {

    /**
     * 校验是否可提交
     */
    public void ensureCanSubmit(OrderStatus from) {
        if (from != OrderStatus.DRAFT && from != OrderStatus.REJECTED) {
            throw BizException.statusNotAllow("仅草稿或已驳回的订单可以提交审批");
        }
    }

    /**
     * 校验是否可撤回
     */
    public void ensureCanWithdraw(OrderStatus from) {
        if (from != OrderStatus.APPROVING) {
            throw BizException.statusNotAllow("仅审批中的订单可以撤回");
        }
    }

    /**
     * 校验是否可取消（已审批订单仅管理员可异常取消）
     */
    public void ensureCanCancel(OrderStatus from, boolean admin) {
        if (from == OrderStatus.CANCELED) {
            throw BizException.statusNotAllow("订单已取消");
        }
        if (from == OrderStatus.APPROVED && !admin) {
            throw BizException.statusNotAllow("已审批的订单仅管理员可取消");
        }
        if (from == OrderStatus.APPROVING) {
            throw BizException.statusNotAllow("审批中的订单请先撤回再取消");
        }
    }

    /**
     * 校验是否可审批
     */
    public void ensureCanApprove(OrderStatus orderStatus) {
        if (orderStatus != OrderStatus.APPROVING) {
            throw BizException.statusNotAllow("仅审批中的订单可以审批");
        }
    }

    /**
     * 校验是否可编辑
     */
    public void ensureCanEdit(OrderStatus from) {
        if (from == null || !from.isEditable()) {
            throw BizException.statusNotAllow("仅草稿或已驳回的订单可以编辑");
        }
    }
}

package com.yonerp.po.common.enums;

import lombok.Getter;

/**
 * 订单动作类型（写入审批流水）
 */
@Getter
public enum ApprovalAction {

    /** 提交审批 */
    SUBMIT("SUBMIT", "提交审批"),
    /** 重新提交（驳回后） */
    RESUBMIT("RESUBMIT", "重新提交"),
    /** 审批通过 */
    APPROVE("APPROVE", "审批通过"),
    /** 审批驳回 */
    REJECT("REJECT", "审批驳回"),
    /** 撤回 */
    WITHDRAW("WITHDRAW", "撤回"),
    /** 取消 */
    CANCEL("CANCEL", "取消"),
    /** 超时提醒（仅标记，不改状态） */
    TIMEOUT_REMIND("TIMEOUT_REMIND", "超时提醒");

    private final String code;

    private final String name;

    ApprovalAction(String code, String name) {
        this.code = code;
        this.name = name;
    }
}

package com.yonerp.po.common.enums;

import lombok.Getter;

/**
 * 审批任务状态
 */
@Getter
public enum TaskStatus {

    /** 未激活：前置节点尚未通过 */
    WAITING("WAITING", "待流转"),
    /** 待审批：当前生效节点 */
    PENDING("PENDING", "待审批"),
    /** 已通过 */
    APPROVED("APPROVED", "已通过"),
    /** 已驳回 */
    REJECTED("REJECTED", "已驳回"),
    /** 已作废：单据驳回或撤回 */
    INVALID("INVALID", "已作废");

    private final String code;

    private final String name;

    TaskStatus(String code, String name) {
        this.code = code;
        this.name = name;
    }
}

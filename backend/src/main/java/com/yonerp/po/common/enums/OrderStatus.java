package com.yonerp.po.common.enums;

import lombok.Getter;

import java.util.Arrays;

/**
 * 采购订单状态（后端唯一真源，前端仅做展示映射）
 */
@Getter
public enum OrderStatus {

    /** 草稿：可编辑、可删除、可提交、可取消 */
    DRAFT("DRAFT", "草稿"),
    /** 审批中：两级串行审批，可撤回 */
    APPROVING("APPROVING", "审批中"),
    /** 已审批：终态 */
    APPROVED("APPROVED", "已审批"),
    /** 已驳回：可修改后重新提交 */
    REJECTED("REJECTED", "已驳回"),
    /** 已取消：终态 */
    CANCELED("CANCELED", "已取消"),
    /** 已完成：已付款终态 */
    COMPLETED("COMPLETED", "已完成");

    private final String code;

    private final String name;

    OrderStatus(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static OrderStatus of(String code) {
        return Arrays.stream(values())
                .filter(item -> item.code.equalsIgnoreCase(code))
                .findFirst()
                .orElse(null);
    }

    /** 是否为终态（不可再流转，管理员异常取消除外） */
    public boolean isFinal() {
        return this == APPROVED || this == CANCELED;
    }

    /** 是否可编辑（含明细） */
    public boolean isEditable() {
        return this == DRAFT || this == REJECTED;
    }
}

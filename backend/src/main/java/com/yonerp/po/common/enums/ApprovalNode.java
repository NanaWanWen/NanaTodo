package com.yonerp.po.common.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;

/**
 * 审批节点定义（两级串行：采购经理 → 总监）。扩展多级只需新增枚举项。
 */
@Getter
public enum ApprovalNode {

    /** 一级：采购经理 */
    MANAGER(1, "采购经理审批", RoleCode.PURCHASE_MANAGER),
    /** 二级：总监 */
    DIRECTOR(2, "总监审批", RoleCode.DIRECTOR);

    private final int seq;

    private final String name;

    private final RoleCode role;

    ApprovalNode(int seq, String name, RoleCode role) {
        this.seq = seq;
        this.name = name;
        this.role = role;
    }

    public static List<ApprovalNode> ordered() {
        return Arrays.stream(values())
                .sorted((a, b) -> Integer.compare(a.seq, b.seq))
                .toList();
    }

    public static ApprovalNode ofSeq(int seq) {
        return Arrays.stream(values())
                .filter(node -> node.seq == seq)
                .findFirst()
                .orElse(null);
    }

    /** 是否存在下一节点 */
    public boolean hasNext() {
        return ofSeq(this.seq + 1) != null;
    }

    public ApprovalNode next() {
        return ofSeq(this.seq + 1);
    }

    /** 是否为首个节点 */
    public boolean isFirst() {
        return this.seq == ordered().get(0).seq;
    }
}

package com.yonerp.po.common.enums;

import com.yonerp.po.common.BizException;
import lombok.Getter;

import java.util.Arrays;

/**
 * 角色编码
 */
@Getter
public enum RoleCode {

    /** 管理员 */
    ADMIN("ADMIN", "管理员"),
    /** 采购员 */
    BUYER("BUYER", "采购员"),
    /** 采购经理（一级审批） */
    PURCHASE_MANAGER("PURCHASE_MANAGER", "采购经理"),
    /** 总监（二级审批） */
    DIRECTOR("DIRECTOR", "总监");

    private final String code;

    private final String name;

    RoleCode(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static RoleCode of(String code) {
        return Arrays.stream(values())
                .filter(item -> item.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> BizException.param("未知角色：" + code));
    }

    public boolean match(String code) {
        return this.code.equalsIgnoreCase(code);
    }
}

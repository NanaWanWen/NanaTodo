package com.yonerp.po.security;

import com.yonerp.po.common.enums.RoleCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 当前登录用户上下文（由 Redis 会话解析而来）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentUser {

    private Long userId;

    private String username;

    private String realName;

    private Long deptId;

    /** 当前生效角色（可切换） */
    private RoleCode currentRole;

    /** 用户拥有的全部角色 */
    @Builder.Default
    private List<RoleCode> roles = new ArrayList<>();

    public boolean hasRole(RoleCode role) {
        return roles != null && roles.contains(role);
    }

    public boolean isAdmin() {
        return currentRole == RoleCode.ADMIN;
    }

    public boolean isBuyer() {
        return currentRole == RoleCode.BUYER;
    }

    /**
     * 数据可见范围：管理员与审批角色看全部，采购员仅本人
     */
    public boolean viewAll() {
        return currentRole != RoleCode.BUYER;
    }
}

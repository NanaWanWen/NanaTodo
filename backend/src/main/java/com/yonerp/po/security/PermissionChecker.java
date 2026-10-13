package com.yonerp.po.security;

import com.yonerp.po.common.BizException;
import com.yonerp.po.common.enums.RoleCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 权限校验工具：角色校验 + 数据归属校验（后端真实防线）
 */
@Component
@RequiredArgsConstructor
public class PermissionChecker {

    /**
     * 校验当前角色
     */
    public void requireRole(RoleCode... roles) {
        CurrentUser user = UserContext.required();
        for (RoleCode role : roles) {
            if (user.getCurrentRole() == role) {
                return;
            }
        }
        throw BizException.forbiddenRole();
    }

    /**
     * 数据归属校验：管理员可操作全部；采购员仅本人；审批角色不可编辑业务数据
     */
    public void requireOwnerOrAdmin(Long ownerId) {
        CurrentUser user = UserContext.required();
        if (user.isAdmin()) {
            return;
        }
        if (user.isBuyer() && ownerId != null && ownerId.equals(user.getUserId())) {
            return;
        }
        throw BizException.forbiddenData();
    }

    /**
     * 校验是否可查看目标数据：管理员与审批角色看全部，采购员仅本人
     */
    public void requireViewable(Long ownerId) {
        CurrentUser user = UserContext.required();
        if (user.viewAll()) {
            return;
        }
        if (ownerId != null && ownerId.equals(user.getUserId())) {
            return;
        }
        throw BizException.forbiddenData();
    }

    /**
     * 列表查询是否仅能看本人数据
     */
    public boolean onlySelf() {
        return !UserContext.required().viewAll();
    }
}

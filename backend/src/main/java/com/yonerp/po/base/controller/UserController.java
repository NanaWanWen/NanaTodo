package com.yonerp.po.base.controller;

import com.yonerp.po.base.service.UserService;
import com.yonerp.po.base.vo.RoleVO;
import com.yonerp.po.base.vo.UserBriefVO;
import com.yonerp.po.common.Result;
import com.yonerp.po.common.enums.RoleCode;
import com.yonerp.po.security.RequireRole;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户与角色管理（仅管理员）
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@RequireRole(RoleCode.ADMIN)
public class UserController {

    private final UserService userService;

    @GetMapping
    public Result<List<UserBriefVO>> list() {
        return Result.ok(userService.listUsers());
    }

    @GetMapping("/roles")
    public Result<List<RoleVO>> roles() {
        return Result.ok(userService.listRoles());
    }

    @PutMapping("/{id}/roles")
    public Result<Void> assignRoles(@PathVariable Long id, @RequestBody AssignRoleRequest request) {
        userService.assignRoles(id, request.getRoleIds());
        return Result.ok();
    }

    @Data
    public static class AssignRoleRequest {

        private List<Long> roleIds;
    }
}

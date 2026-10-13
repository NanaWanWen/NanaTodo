package com.yonerp.po.base.controller;

import com.yonerp.po.base.service.UserService;
import com.yonerp.po.base.vo.LoginVO;
import com.yonerp.po.base.vo.RoleVO;
import com.yonerp.po.base.vo.UserBriefVO;
import com.yonerp.po.common.BizException;
import com.yonerp.po.common.Result;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 认证与会话（简化鉴权：选择用户即登录，无密码与令牌）
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    /**
     * 演示用户列表（登录下拉）
     */
    @GetMapping("/users")
    public Result<List<UserBriefVO>> users() {
        return Result.ok(userService.listUsers());
    }

    /**
     * 登录：选择用户，写入 Redis 会话
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody LoginRequest request) {
        return Result.ok(userService.login(request.getUserId()));
    }

    /**
     * 切换当前角色
     */
    @PostMapping("/switch-role")
    public Result<LoginVO> switchRole(@RequestBody SwitchRoleRequest request) {
        return Result.ok(userService.switchRole(request.getUserId(), request.getRoleCode()));
    }

    /**
     * 退出登录
     */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestBody LoginRequest request) {
        userService.logout(request.getUserId());
        return Result.ok();
    }

    /**
     * 角色列表
     */
    @GetMapping("/roles")
    public Result<List<RoleVO>> roles() {
        return Result.ok(userService.listRoles());
    }

    @Data
    public static class LoginRequest {

        @NotNull(message = "用户ID不能为空")
        private Long userId;
    }

    @Data
    public static class SwitchRoleRequest {

        @NotNull(message = "用户ID不能为空")
        private Long userId;

        private String roleCode;

        public String getRoleCode() {
            if (roleCode == null || roleCode.isBlank()) {
                throw BizException.param("角色不能为空");
            }
            return roleCode;
        }
    }
}

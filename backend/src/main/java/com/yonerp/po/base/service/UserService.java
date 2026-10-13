package com.yonerp.po.base.service;

import com.yonerp.po.base.vo.LoginVO;
import com.yonerp.po.base.vo.RoleVO;
import com.yonerp.po.base.vo.UserBriefVO;
import com.yonerp.po.security.CurrentUser;

import java.util.List;

/**
 * 用户与角色服务
 */
public interface UserService {

    /**
     * 构建当前用户上下文（供拦截器回源数据库时使用）
     */
    CurrentUser loadCurrentUser(Long userId);

    /**
     * 登录（简化：选择用户即登录），写入 Redis 会话
     */
    LoginVO login(Long userId);

    /**
     * 切换当前角色
     */
    LoginVO switchRole(Long userId, String roleCode);

    /**
     * 退出登录
     */
    void logout(Long userId);

    /**
     * 用户列表（带缓存）
     */
    List<UserBriefVO> listUsers();

    /**
     * 角色列表
     */
    List<RoleVO> listRoles();

    /**
     * 分配角色
     */
    void assignRoles(Long userId, List<Long> roleIds);
}

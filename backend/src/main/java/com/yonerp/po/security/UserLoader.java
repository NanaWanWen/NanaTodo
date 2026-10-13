package com.yonerp.po.security;

/**
 * 用户信息加载器：供拦截器在会话缺失时回源数据库重建上下文
 */
public interface UserLoader {

    /**
     * 按用户 ID 构建当前用户上下文（含角色列表），用户不存在时抛异常
     */
    CurrentUser loadCurrentUser(Long userId);
}

package com.yonerp.po.security;

import com.yonerp.po.common.BizException;

/**
 * 当前用户上下文（ThreadLocal），请求结束后由拦截器清理
 */
public final class UserContext {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    /**
     * 获取当前用户，未登录时抛出 3001
     */
    public static CurrentUser required() {
        CurrentUser user = HOLDER.get();
        if (user == null) {
            throw BizException.unauthorized();
        }
        return user;
    }

    public static Long currentUserId() {
        return required().getUserId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}

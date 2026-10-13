package com.yonerp.po.security;

import com.yonerp.po.common.BizException;
import com.yonerp.po.common.enums.RoleCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * 认证拦截器：解析 X-User-Id → 读取 Redis 会话 → 注入当前用户上下文
 *
 * <p>本项目为简化鉴权（无密码、无令牌），但后端仍做真实角色与数据权限校验。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    /** 无需登录即可访问的路径 */
    private static final List<String> SKIP_PATTERNS = List.of(
            "/api/auth/", "/swagger-ui/", "/v3/api-docs", "/error", "/favicon.ico");

    private final SessionStore sessionStore;

    private final UserLoader userLoader;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        if (isSkip(path)) {
            return true;
        }
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        Long userId = parseUserId(request.getHeader("X-User-Id"));
        CurrentUser user = sessionStore.read(userId);
        if (user == null) {
            // Redis 不可用或会话过期：回源数据库重建
            user = userLoader.loadCurrentUser(userId);
            sessionStore.save(user);
        } else {
            sessionStore.renew(userId);
        }

        applyRole(user, request.getHeader("X-Role-Code"));
        UserContext.set(user);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }

    private Long parseUserId(String header) {
        if (header == null || header.isBlank()) {
            throw BizException.unauthorized();
        }
        try {
            return Long.parseLong(header.trim());
        } catch (NumberFormatException e) {
            throw BizException.unauthorized();
        }
    }

    private void applyRole(CurrentUser user, String roleHeader) {
        if (roleHeader != null && !roleHeader.isBlank()) {
            RoleCode role = RoleCode.of(roleHeader);
            if (!user.hasRole(role)) {
                throw BizException.forbiddenRole();
            }
            user.setCurrentRole(role);
            return;
        }
        if (user.getCurrentRole() == null && !user.getRoles().isEmpty()) {
            user.setCurrentRole(user.getRoles().get(0));
        }
    }

    private boolean isSkip(String path) {
        return SKIP_PATTERNS.stream().anyMatch(path::startsWith);
    }
}

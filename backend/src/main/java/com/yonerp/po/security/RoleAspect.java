package com.yonerp.po.security;

import com.yonerp.po.common.BizException;
import com.yonerp.po.common.enums.RoleCode;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * 角色注解切面：方法执行前校验当前角色
 */
@Slf4j
@Aspect
@Component
public class RoleAspect {

    @Around("@annotation(com.yonerp.po.security.RequireRole) || "
            + "@within(com.yonerp.po.security.RequireRole)")
    public Object checkRole(ProceedingJoinPoint point) throws Throwable {
        Method method = ((MethodSignature) point.getSignature()).getMethod();
        RequireRole annotation = method.getAnnotation(RequireRole.class);
        if (annotation == null) {
            annotation = method.getDeclaringClass().getAnnotation(RequireRole.class);
        }
        if (annotation == null) {
            return point.proceed();
        }

        CurrentUser user = UserContext.required();
        boolean matched = Arrays.stream(annotation.value())
                .anyMatch(role -> role == user.getCurrentRole());
        if (!matched) {
            log.warn("角色校验失败，userId={}, currentRole={}, required={}",
                    user.getUserId(), user.getCurrentRole(), Arrays.toString(annotation.value()));
            throw BizException.forbiddenRole();
        }
        return point.proceed();
    }

    /**
     * 校验当前用户是否拥有指定角色之一（供 Service 层调用）
     */
    public static void ensureRole(RoleCode... roles) {
        CurrentUser user = UserContext.required();
        boolean matched = Arrays.stream(roles).anyMatch(role -> role == user.getCurrentRole());
        if (!matched) {
            throw BizException.forbiddenRole();
        }
    }
}

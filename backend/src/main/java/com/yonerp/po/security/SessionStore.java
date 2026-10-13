package com.yonerp.po.security;

import com.yonerp.po.common.CacheService;
import com.yonerp.po.common.RedisKeys;
import com.yonerp.po.common.enums.RoleCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Redis 会话存储：存放当前用户与当前角色
 *
 * <p>Redis 不可用时不阻断业务，由 {@link AuthInterceptor} 回源数据库重建上下文。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionStore {

    private final CacheService cacheService;

    @Value("${yonerp.session-ttl:28800}")
    private long sessionTtlSeconds;

    public void save(CurrentUser user) {
        cacheService.set(RedisKeys.session(user.getUserId()), user, Duration.ofSeconds(sessionTtlSeconds), 0);
        cacheService.set(RedisKeys.sessionRoles(user.getUserId()), codes(user.getRoles()),
                Duration.ofSeconds(sessionTtlSeconds), 0);
    }

    @SuppressWarnings("unchecked")
    public CurrentUser read(Long userId) {
        return cacheService.get(RedisKeys.session(userId), CurrentUser.class).orElse(null);
    }

    @SuppressWarnings("unchecked")
    public List<RoleCode> readRoles(Long userId) {
        List<String> codes = cacheService.get(RedisKeys.sessionRoles(userId), List.class).orElse(null);
        if (codes == null) {
            return null;
        }
        return codes.stream().map(RoleCode::of).toList();
    }

    /**
     * 会话续期（滑动过期）
     */
    public void renew(Long userId) {
        cacheService.expire(RedisKeys.session(userId), Duration.ofSeconds(sessionTtlSeconds));
        cacheService.expire(RedisKeys.sessionRoles(userId), Duration.ofSeconds(sessionTtlSeconds));
    }

    public void remove(Long userId) {
        cacheService.delete(RedisKeys.session(userId), RedisKeys.sessionRoles(userId));
    }

    private List<String> codes(List<RoleCode> roles) {
        return roles == null ? List.of() : roles.stream().map(RoleCode::getCode).toList();
    }
}

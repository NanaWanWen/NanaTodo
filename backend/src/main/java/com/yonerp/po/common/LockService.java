package com.yonerp.po.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 锁与幂等服务：互斥锁、幂等键、限流
 *
 * <p>Redis 不可用时的策略：幂等与限流放行（由数据库乐观锁兜底），
 * 互斥锁放行但记录告警，避免 Redis 故障导致业务完全不可用。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LockService {

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;

    /**
     * 尝试加锁，成功返回令牌，失败返回 null
     */
    public String tryLock(String key, Duration ttl) {
        String token = UUID.randomUUID().toString();
        try {
            Boolean success = redisTemplate.opsForValue().setIfAbsent(key, token, ttl);
            return Boolean.TRUE.equals(success) ? token : null;
        } catch (Exception e) {
            log.warn("Redis 加锁失败，降级放行（依赖数据库乐观锁），key={}", key, e);
            return token;
        }
    }

    /**
     * 释放锁：校验令牌，防止误删他人锁
     */
    public void unlock(String key, String token) {
        if (key == null || token == null) {
            return;
        }
        try {
            redisTemplate.execute(UNLOCK_SCRIPT, List.of(key), token);
        } catch (Exception e) {
            log.warn("Redis 释放锁失败（等待 TTL 自动过期），key={}", key, e);
        }
    }

    /**
     * 幂等判定：返回 true 表示首次请求（已占位），false 表示重复提交
     */
    public boolean acquireIdempotent(String key, Duration ttl) {
        try {
            Boolean success = redisTemplate.opsForValue().setIfAbsent(key, "1", ttl);
            return Boolean.TRUE.equals(success);
        } catch (Exception e) {
            log.warn("Redis 幂等校验失败，降级放行，key={}", key, e);
            return true;
        }
    }

    /**
     * 固定窗口限流：返回 true 表示放行
     */
    public boolean tryAcquireRate(String key, long limit, Duration window) {
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, window);
            }
            return count == null || count <= limit;
        } catch (Exception e) {
            log.warn("Redis 限流失败，降级放行，key={}", key, e);
            return true;
        }
    }
}

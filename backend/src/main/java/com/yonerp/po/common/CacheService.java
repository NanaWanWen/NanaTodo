package com.yonerp.po.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 缓存服务（Cache-Aside，Redis 不可用时自动降级为直连数据库）
 *
 * <p>约定：所有 Redis 访问集中在此处，异常一律降级不阻断业务。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheService {

    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper;

    /**
     * 读取对象缓存
     */
    public <T> Optional<T> get(String key, Class<T> type) {
        return read(key).flatMap(json -> {
            try {
                return Optional.of(objectMapper.readValue(json, type));
            } catch (Exception e) {
                log.warn("缓存反序列化失败，key={}", key, e);
                return Optional.empty();
            }
        });
    }

    /**
     * 读取泛型对象缓存（如 List）
     */
    public <T> Optional<T> get(String key, TypeReference<T> typeRef) {
        return read(key).flatMap(json -> {
            try {
                return Optional.of(objectMapper.readValue(json, typeRef));
            } catch (Exception e) {
                log.warn("缓存反序列化失败，key={}", key, e);
                return Optional.empty();
            }
        });
    }

    public Optional<String> read(String key) {
        try {
            return Optional.ofNullable(redisTemplate.opsForValue().get(key));
        } catch (Exception e) {
            log.warn("Redis 读取失败，降级回源数据库，key={}", key, e);
            return Optional.empty();
        }
    }

    /**
     * 写入缓存，TTL 附加随机抖动防止雪崩
     */
    public void set(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), withJitter(ttl));
        } catch (Exception e) {
            log.warn("Redis 写入失败，忽略缓存，key={}", key, e);
        }
    }

    public void set(String key, Object value, Duration ttl, int jitterSeconds) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), withJitter(ttl, jitterSeconds));
        } catch (Exception e) {
            log.warn("Redis 写入失败，忽略缓存，key={}", key, e);
        }
    }

    public void delete(String... keys) {
        try {
            redisTemplate.delete(java.util.Arrays.asList(keys));
        } catch (Exception e) {
            log.warn("Redis 删除失败，key={}", (Object) keys, e);
        }
    }

    /**
     * 按前缀批量删除（SCAN，避免 KEYS 阻塞）
     */
    public void deleteByPrefix(String prefix) {
        try {
            Set<String> keys = redisTemplate.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.debug("按前缀清理缓存，prefix={}，数量={}", prefix, keys.size());
            }
        } catch (Exception e) {
            log.warn("Redis 前缀清理失败，prefix={}", prefix, e);
        }
    }

    /**
     * 自增计数，返回 null 表示 Redis 不可用（调用方需回退数据库方案）
     */
    public Long increment(String key, Duration ttl) {
        try {
            Long value = redisTemplate.opsForValue().increment(key);
            if (value != null && value == 1L) {
                redisTemplate.expire(key, ttl);
            }
            return value;
        } catch (Exception e) {
            log.warn("Redis 自增失败，key={}", key, e);
            return null;
        }
    }

    public void expire(String key, Duration ttl) {
        try {
            redisTemplate.expire(key, ttl);
        } catch (Exception e) {
            log.warn("Redis 续期失败，key={}", key, e);
        }
    }

    public Long getLong(String key) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            return value == null ? null : Long.parseLong(value);
        } catch (Exception e) {
            log.warn("Redis 读取计数失败，key={}", key, e);
            return null;
        }
    }

    public void setLong(String key, long value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, String.valueOf(value), ttl);
        } catch (Exception e) {
            log.warn("Redis 写入计数失败，key={}", key, e);
        }
    }

    /**
     * TTL 随机抖动：默认 0~60s
     */
    public static Duration withJitter(Duration ttl) {
        return withJitter(ttl, 60);
    }

    public static Duration withJitter(Duration ttl, int jitterSeconds) {
        if (jitterSeconds <= 0) {
            return ttl;
        }
        return ttl.plusSeconds(ThreadLocalRandom.current().nextInt(jitterSeconds + 1));
    }
}

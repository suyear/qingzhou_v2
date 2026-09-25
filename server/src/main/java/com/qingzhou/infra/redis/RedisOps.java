package com.qingzhou.infra.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Redis 封装。Token 刷新锁、Nonce 去重都走这里，避免业务代码散落 SET NX。
 */
@Component
@RequiredArgsConstructor
public class RedisOps {

    private final StringRedisTemplate stringRedisTemplate;

    public void set(String key, String value, Duration ttl) {
        stringRedisTemplate.opsForValue().set(key, value, ttl);
    }

    public String get(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    public Boolean delete(String key) {
        return stringRedisTemplate.delete(key);
    }

    public Boolean setIfAbsent(String key, String value, Duration ttl) {
        return stringRedisTemplate.opsForValue().setIfAbsent(key, value, ttl);
    }

    /** 计数器自增；首次写入时设置 TTL，用于固定窗口限流。 */
    public long increment(String key, Duration ttl) {
        Long value = stringRedisTemplate.opsForValue().increment(key);
        long count = value == null ? 0L : value;
        if (count == 1L && ttl != null) {
            stringRedisTemplate.expire(key, ttl);
        }
        return count;
    }

    /**
     * 简易分布式锁。持有 token 才能解锁，避免误删别人的锁。
     * Phase 2 TokenManager 会用它防止并发刷新 AccessToken。
     */
    public boolean tryLock(String key, String token, Duration ttl) {
        return Boolean.TRUE.equals(setIfAbsent(key, token, ttl));
    }

    /**
     * 抢锁失败则短暂等待后重试，给并发刷新场景下的等待方机会读到刚写入的缓存。
     */
    public boolean tryLock(String key, String token, Duration ttl, int retries, Duration wait) {
        int maxRetry = Math.max(0, retries);
        for (int i = 0; i <= maxRetry; i++) {
            if (tryLock(key, token, ttl)) {
                return true;
            }
            if (i < maxRetry) {
                sleep(wait);
            }
        }
        return false;
    }

    public void sleep(Duration wait) {
        try {
            Thread.sleep(Math.max(0, wait.toMillis()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void unlock(String key, String token) {
        String current = get(key);
        if (token.equals(current)) {
            delete(key);
        }
    }

    public <T> T withLock(String key, Duration ttl, Supplier<T> action) {
        String token = UUID.randomUUID().toString();
        if (!tryLock(key, token, ttl)) {
            throw new IllegalStateException("获取分布式锁失败: " + key);
        }
        try {
            return action.get();
        } finally {
            unlock(key, token);
        }
    }

    public boolean ping() {
        RedisConnectionFactory factory = stringRedisTemplate.getConnectionFactory();
        if (factory == null) {
            return false;
        }
        try (RedisConnection connection = factory.getConnection()) {
            return "PONG".equalsIgnoreCase(connection.ping());
        }
    }
}

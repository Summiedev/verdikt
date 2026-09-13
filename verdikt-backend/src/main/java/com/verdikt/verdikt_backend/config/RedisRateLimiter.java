package com.verdikt.verdikt_backend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisRateLimiter {

    private final StringRedisTemplate redisTemplate;

    public boolean tryConsume(String bucket, String subject, int capacity, Duration window) {
        String key = "verdikt:rate:" + bucket + ":" + digest(subject);
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, window);
            }
            return count != null && count <= capacity;
        } catch (RuntimeException ex) {
            log.error("Rate limiter unavailable for bucket={}", bucket, ex);
            return false;
        }
    }

    private String digest(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte part : hash) result.append(String.format("%02x", part));
            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}

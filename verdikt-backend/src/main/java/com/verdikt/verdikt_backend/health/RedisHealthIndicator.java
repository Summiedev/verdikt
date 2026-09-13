package com.verdikt.verdikt_backend.health;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RedisHealthIndicator {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisHealthIndicator(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public HealthStatus check() {
        try {
            String result = redisTemplate.getConnectionFactory().getConnection().ping();
            return "PONG".equalsIgnoreCase(result) ? HealthStatus.UP : HealthStatus.DOWN;
        } catch (Exception ex) {
            return HealthStatus.DOWN;
        }
    }

    public Map<String, Object> details() {
        try {
            String result = redisTemplate.getConnectionFactory().getConnection().ping();
            return Map.of(
                    "broker", "Redis",
                    "result", result,
                    "available", true
            );
        } catch (Exception ex) {
            return Map.of(
                    "broker", "Redis",
                    "available", false,
                    "error", ex.getMessage()
            );
        }
    }
}

package com.verdikt.verdikt_backend.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RedisHealthIndicator implements HealthIndicator {

    private final StringRedisTemplate redisTemplate;

    public RedisHealthIndicator(StringRedisTemplate redisTemplate) {
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

    @Override
    public Health health() {
        HealthStatus status = check();
        Health.Builder builder = status == HealthStatus.UP ? Health.up() : Health.down();
        return builder.withDetails(details()).build();
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

package com.verdikt.verdikt_backend.health;

import java.util.Map;

public record HealthResponse(
        String status,
        Map<String, Object> components,
        long timestamp
) {
}

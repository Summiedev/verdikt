package com.verdikt.verdikt_backend.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

@Component
public class HealthCheckRunner {

    private final JdbcTemplate jdbcTemplate;
    private final WebSocketBrokerHealthCheck webSocketBrokerHealthCheck;
    private final JvmMemoryHealthCheck jvmMemoryHealthCheck;

    public HealthCheckRunner(JdbcTemplate jdbcTemplate,
                             WebSocketBrokerHealthCheck webSocketBrokerHealthCheck,
                             JvmMemoryHealthCheck jvmMemoryHealthCheck) {
        this.jdbcTemplate = jdbcTemplate;
        this.webSocketBrokerHealthCheck = webSocketBrokerHealthCheck;
        this.jvmMemoryHealthCheck = jvmMemoryHealthCheck;
    }

    public ResponseEntity<HealthResponse> getHealthResponse(boolean includeDetails) {
        Map<String, Object> components = new HashMap<>();
        HealthStatus overall = HealthStatus.UP;

        Map<String, Object> dbDetails = checkDatabase(includeDetails);
        HealthStatus dbStatus = (HealthStatus) dbDetails.remove("status");
        if (includeDetails) {
            components.put("db", dbDetails);
        } else {
            components.put("db", Map.of("status", dbStatus.name()));
        }
        if (dbStatus != HealthStatus.UP) overall = dbStatus;

        Map<String, Object> diskDetails = checkDiskSpace(includeDetails);
        HealthStatus diskStatus = (HealthStatus) diskDetails.remove("status");
        if (includeDetails) {
            components.put("diskSpace", diskDetails);
        } else {
            components.put("diskSpace", Map.of("status", diskStatus.name()));
        }
        if (diskStatus != HealthStatus.UP) overall = diskStatus;

        Map<String, Object> wsDetails = checkWebSocket(includeDetails);
        HealthStatus wsStatus = (HealthStatus) wsDetails.remove("status");
        if (includeDetails) {
            components.put("websocketBroker", wsDetails);
        } else {
            components.put("websocketBroker", Map.of("status", wsStatus.name()));
        }
        if (wsStatus != HealthStatus.UP) overall = wsStatus;

        Map<String, Object> jvmDetails = checkJvmMemory(includeDetails);
        HealthStatus jvmStatus = (HealthStatus) jvmDetails.remove("status");
        if (includeDetails) {
            components.put("jvm", jvmDetails);
        } else {
            components.put("jvm", Map.of("status", jvmStatus.name()));
        }
        if (jvmStatus != HealthStatus.UP) overall = jvmStatus;

        HealthResponse response = new HealthResponse(
                overall.name(),
                components,
                Instant.now().toEpochMilli()
        );

        HttpStatus status = switch (overall) {
            case UP -> HttpStatus.OK;
            case DOWN, OUT_OF_SERVICE -> HttpStatus.SERVICE_UNAVAILABLE;
        };

        return ResponseEntity.status(status).body(response);
    }

    private Map<String, Object> checkDatabase(boolean includeDetails) {
        Map<String, Object> result = new HashMap<>();
        try {
            jdbcTemplate.execute("SELECT 1");
            result.put("status", HealthStatus.UP);
            if (includeDetails) {
                result.put("database", "PostgreSQL");
                result.put("result", "SELECT 1 succeeded");
            }
        } catch (Exception ex) {
            result.put("status", HealthStatus.DOWN);
            if (includeDetails) {
                result.put("error", ex.getMessage());
            }
        }
        return result;
    }

    private Map<String, Object> checkDiskSpace(boolean includeDetails) {
        Map<String, Object> result = new HashMap<>();
        File root = new File("/");
        long total = root.getTotalSpace();
        long free = root.getFreeSpace();
        long usable = root.getUsableSpace();

        if (total == 0) {
            result.put("status", HealthStatus.UP);
            if (includeDetails) {
                result.put("total", 0);
                result.put("free", free);
                result.put("usable", usable);
            }
            return result;
        }

        double freeRatio = (double) free / total;
        if (freeRatio < 0.10) {
            result.put("status", HealthStatus.OUT_OF_SERVICE);
            if (includeDetails) {
                result.put("reason", "Free disk space below 10%");
            }
        } else {
            result.put("status", HealthStatus.UP);
        }

        if (includeDetails) {
            result.put("total", total);
            result.put("free", free);
            result.put("usable", usable);
            result.put("freePercent", Math.round(freeRatio * 100));
        }
        return result;
    }

    private Map<String, Object> checkWebSocket(boolean includeDetails) {
        HealthStatus status = webSocketBrokerHealthCheck.check();
        Map<String, Object> result = new HashMap<>(webSocketBrokerHealthCheck.details());
        result.put("status", status);
        return result;
    }

    private Map<String, Object> checkJvmMemory(boolean includeDetails) {
        HealthStatus status = jvmMemoryHealthCheck.check();
        Map<String, Object> result = new HashMap<>(jvmMemoryHealthCheck.details());
        result.put("status", status);
        if (status == HealthStatus.OUT_OF_SERVICE) {
            result.put("reason", "JVM heap memory below 10% free");
        }
        return result;
    }
}

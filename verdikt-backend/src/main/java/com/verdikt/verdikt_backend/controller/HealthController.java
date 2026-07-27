package com.verdikt.verdikt_backend.controller;

import com.verdikt.verdikt_backend.health.HealthCheckRunner;
import com.verdikt.verdikt_backend.health.HealthResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final HealthCheckRunner healthCheckRunner;

    public HealthController(HealthCheckRunner healthCheckRunner) {
        this.healthCheckRunner = healthCheckRunner;
    }

    @GetMapping("/actuator/health")
    public ResponseEntity<HealthResponse> health(
            @RequestHeader(value = "Accept", required = false) String acceptHeader
    ) {
        boolean includeDetails = acceptHeader != null && acceptHeader.contains("application/json");
        return healthCheckRunner.getHealthResponse(includeDetails);
    }
}

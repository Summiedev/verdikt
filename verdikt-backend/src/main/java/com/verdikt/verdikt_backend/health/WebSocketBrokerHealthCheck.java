package com.verdikt.verdikt_backend.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component("websocketBroker")
public class WebSocketBrokerHealthCheck implements HealthIndicator {

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketBrokerHealthCheck(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public HealthStatus check() {
        if (messagingTemplate != null) {
            return HealthStatus.UP;
        }
        return HealthStatus.DOWN;
    }

    @Override
    public Health health() {
        Health.Builder builder = check() == HealthStatus.UP ? Health.up() : Health.down();
        return builder.withDetails(details()).build();
    }

    public Map<String, Object> details() {
        return Map.of(
                "broker", "SimpleBroker",
                "destinationPrefixes", "/topic",
                "available", messagingTemplate != null
        );
    }
}

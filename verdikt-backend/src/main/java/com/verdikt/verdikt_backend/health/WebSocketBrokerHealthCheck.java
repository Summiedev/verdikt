package com.verdikt.verdikt_backend.health;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class WebSocketBrokerHealthCheck {

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

    public Map<String, Object> details() {
        return Map.of(
                "broker", "SimpleBroker",
                "destinationPrefixes", "/topic",
                "available", messagingTemplate != null
        );
    }
}

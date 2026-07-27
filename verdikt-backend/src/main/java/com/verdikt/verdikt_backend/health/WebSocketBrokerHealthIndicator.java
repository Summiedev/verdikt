package com.verdikt.verdikt_backend.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class WebSocketBrokerHealthIndicator implements HealthIndicator {

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketBrokerHealthIndicator(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public Health health() {
        if (messagingTemplate != null) {
            return Health.up()
                    .withDetail("broker", "SimpleBroker")
                    .withDetail("destinationPrefixes", "/topic")
                    .build();
        }
        return Health.down()
                .withDetail("error", "SimpMessagingTemplate not available")
                .build();
    }
}

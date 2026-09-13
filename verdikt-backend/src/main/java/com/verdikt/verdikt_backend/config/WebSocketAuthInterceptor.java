package com.verdikt.verdikt_backend.config;

import com.verdikt.verdikt_backend.model.Player;
import com.verdikt.verdikt_backend.security.PlayerAuthentication;
import com.verdikt.verdikt_backend.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final Pattern ROOM_TOPIC = Pattern.compile("^/topic/room/([0-9a-fA-F-]{36})/(players|game|votes)$");
    private final PlayerRepository playerRepository;
    private final MeterRegistry meterRegistry;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            Player player = findPlayer(accessor.getFirstNativeHeader("X-Player-Token"));
            accessor.setUser(new PlayerAuthentication(player));
            counter("verdikt.websocket.connections", "Accepted WebSocket connections").increment();
        } else if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
            counter("verdikt.websocket.disconnects", "WebSocket disconnects").increment();
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeSubscription(accessor);
        }
        return message;
    }

    private Counter counter(String name, String description) {
        return Counter.builder(name).description(description).register(meterRegistry);
    }

    private Player findPlayer(String rawToken) {
        try {
            UUID token = UUID.fromString(rawToken == null ? "" : rawToken.trim());
            return playerRepository.findByTokenAndExpiresAtAfter(token, LocalDateTime.now())
                    .filter(Player::isActive)
                    .orElseThrow(() -> new MessagingException("Player session is invalid or expired."));
        } catch (IllegalArgumentException ex) {
            throw new MessagingException("Player session is invalid or expired.");
        }
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        if (!(accessor.getUser() instanceof PlayerAuthentication authentication)) {
            throw new MessagingException("Player authentication is required.");
        }
        Matcher matcher = ROOM_TOPIC.matcher(accessor.getDestination() == null ? "" : accessor.getDestination());
        if (!matcher.matches()) {
            throw new MessagingException("Subscription is not allowed.");
        }
        UUID roomId = UUID.fromString(matcher.group(1));
        Player player = authentication.getPlayer();
        if (player.getRoom() == null || !roomId.equals(player.getRoom().getId())) {
            throw new MessagingException("Subscription is not allowed for this room.");
        }
    }
}

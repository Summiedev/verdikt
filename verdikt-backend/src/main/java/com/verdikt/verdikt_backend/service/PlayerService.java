package com.verdikt.verdikt_backend.service;

import com.verdikt.verdikt_backend.config.CacheConstants;
import com.verdikt.verdikt_backend.exception.InvalidTokenException;
import com.verdikt.verdikt_backend.exception.PlayerNotFoundException;
import com.verdikt.verdikt_backend.exception.RoomNotFoundException;
import com.verdikt.verdikt_backend.exception.TokenExpiredException;
import com.verdikt.verdikt_backend.model.Player;
import com.verdikt.verdikt_backend.model.Room;
import com.verdikt.verdikt_backend.repository.PlayerRepository;
import com.verdikt.verdikt_backend.repository.RoomRepository;
import com.verdikt.verdikt_backend.websocket.WebSocketEventPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlayerService {

    private static final long DEFAULT_TTL_HOURS = 24;
    private static final long MAX_TOKEN_AGE_DAYS = 7;

    private final PlayerRepository playerRepository;
    private final RoomRepository roomRepository;
    private final RoomService roomService;
    private final WebSocketEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public Player getByToken(UUID token) {
        LocalDateTime now = LocalDateTime.now();
        Player player = playerRepository.findByTokenAndExpiresAtAfter(token, now)
                .orElseThrow(() -> new TokenExpiredException("Session not found or expired. Please join again."));
        return player;
    }

    @Transactional
    public Player refreshToken(UUID token) {
        Player player = playerRepository.findByToken(token)
                .orElseThrow(() -> new InvalidTokenException("Invalid token. Please join again."));

        LocalDateTime now = LocalDateTime.now();
        if (player.getExpiresAt().isBefore(now)) {
            throw new TokenExpiredException("Session expired. Please join again.");
        }

        long ageHours = ChronoUnit.HOURS.between(player.getJoinedAt(), now);
        if (ageHours >= MAX_TOKEN_AGE_DAYS * 24) {
            throw new TokenExpiredException("Session has reached maximum age. Please join again.");
        }

        player.setExpiresAt(now.plusHours(DEFAULT_TTL_HOURS));
        playerRepository.save(player);

        log.info("Token refreshed: player={} room={} newExpiry={}",
                player.getName(), player.getRoom().getCode(), player.getExpiresAt());

        return player;
    }

    @Transactional(readOnly = true)
    public List<Player> getAllInRoom(UUID roomId) {
        return playerRepository.findAllByRoomId(roomId);
    }

    @CacheEvict(value = CacheConstants.PLAYER_BY_TOKEN, key = "#token")
    @Transactional
    public void markInactive(UUID token) {
        Player player = getByToken(token);
        player.setActive(false);
        playerRepository.save(player);

        log.info("Player marked inactive: room={} player={}",
                player.getRoom().getCode(), player.getName());
        eventPublisher.publishPlayerStatusChanged(player.getRoom().getId(), player.getId(), player.isActive());
        if (player.isHost()) {
            roomService.handleHostDisconnect(player.getRoom().getId());
        }
    }

    @CacheEvict(value = CacheConstants.PLAYER_BY_TOKEN, key = "#token")
    @Transactional
    public void markActive(UUID token) {
        Player player = getByToken(token);
        player.setActive(true);
        playerRepository.save(player);

        roomService.handleHostReconnect(player.getRoom().getId(), player);

        log.info("Player marked active: room={} player={}",
                player.getRoom().getCode(), player.getName());
    }

    @CacheEvict(value = CacheConstants.PLAYER_BY_TOKEN, key = "#token")
    @Transactional
    public void leaveRoom(UUID token) {
        Player player = getByToken(token);
        Room room = player.getRoom();
        boolean wasHost = player.isHost();

        playerRepository.delete(player);

        log.info("Player left: room={} player={}", room.getCode(), player.getName());

        if (wasHost) {
            roomService.handleHostDisconnect(room.getId());
        }
    }

    @Transactional(readOnly = true)
    public boolean isPlayerInRoom(UUID token, UUID roomId) {
        return playerRepository.existsByTokenAndRoomId(token, roomId);
    }

    
}
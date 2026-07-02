package com.verdikt.verdikt_backend.service;

import com.verdikt.verdikt_backend.exception.PlayerNotFoundException;
import com.verdikt.verdikt_backend.exception.RoomNotFoundException;
import com.verdikt.verdikt_backend.model.Player;
import com.verdikt.verdikt_backend.model.Room;
import com.verdikt.verdikt_backend.repository.PlayerRepository;
import com.verdikt.verdikt_backend.repository.RoomRepository;
import com.verdikt.verdikt_backend.websocket.WebSocketEventPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlayerService {

    private final PlayerRepository playerRepository;
    private final RoomRepository roomRepository;
    private final RoomService roomService;
 private final WebSocketEventPublisher eventPublisher;
    @Transactional(readOnly = true)
    public Player getByToken(UUID token) {
        return playerRepository.findByToken(token)
                .orElseThrow(() -> new PlayerNotFoundException("Session not found. Please join again."));
    }

    @Transactional(readOnly = true)
    public List<Player> getAllInRoom(UUID roomId) {
        return playerRepository.findAllByRoomId(roomId);
    }

    // called when a socket disconnects — marks player inactive but doesn't remove them
    // this is the low-connectivity safety net: a dropped connection isn't treated as "left"
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

    // called when a socket reconnects or a poll/fallback request comes back in
    @Transactional
public void markActive(UUID token) {
    Player player = getByToken(token);
    player.setActive(true);
    playerRepository.save(player);

    roomService.handleHostReconnect(player.getRoom().getId(), player);

    log.info("Player marked active: room={} player={}",
            player.getRoom().getCode(), player.getName());
}

    // explicit "leave room" action, different from a connectivity drop
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
        return playerRepository.findByToken(token)
                .map(p -> p.getRoom().getId().equals(roomId))
                .orElse(false);
    }

    
}
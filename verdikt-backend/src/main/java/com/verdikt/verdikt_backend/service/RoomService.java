package com.verdikt.verdikt_backend.service;

import com.verdikt.verdikt_backend.dto.request.CreateRoomRequest;
import com.verdikt.verdikt_backend.dto.request.JoinRoomRequest;
import com.verdikt.verdikt_backend.dto.response.PlayerResponse;
import com.verdikt.verdikt_backend.dto.response.RoomResponse;
import com.verdikt.verdikt_backend.exception.*;
import com.verdikt.verdikt_backend.model.Player;
import com.verdikt.verdikt_backend.model.Room;
import com.verdikt.verdikt_backend.model.enums.RoomStatus;
import com.verdikt.verdikt_backend.repository.PlayerRepository;
import com.verdikt.verdikt_backend.repository.RoomRepository;
import com.verdikt.verdikt_backend.websocket.WebSocketEventPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.time.Instant;
@Service
@RequiredArgsConstructor
@Slf4j
public class RoomService {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O/1/I confusion
    private static final int CODE_LENGTH = 6;
    private static final int MAX_PLAYERS = 25;
    private static final SecureRandom RANDOM = new SecureRandom();
private final WebSocketEventPublisher eventPublisher;
    private final RoomRepository roomRepository;
    private final PlayerRepository playerRepository;

    @Transactional
    public RoomResponse createRoom(CreateRoomRequest request) {
        String code = generateUniqueCode();
        String roomName = sanitize(request.getName());
        String hostName = sanitize(request.getHostName());
        int questionCount = request.getQuestionCount() != null && request.getQuestionCount() > 0
                ? request.getQuestionCount()
                : 10;

        if (roomName.isBlank()) {
            throw new IllegalArgumentException("Room name is required.");
        }
        if (hostName.isBlank()) {
            throw new IllegalArgumentException("Host name is required.");
        }

        Room room = Room.builder()
        .code(code)
        .name(roomName)
        .voteMode(request.getVoteMode())
        .maxPlayers(MAX_PLAYERS)
        .maxQuestions(questionCount)
        .questionDurationSeconds(request.getQuestionDurationSeconds())
        .build();

        log.debug("Creating room entity: code={} name={} voteMode={} questionDurationSeconds={}",
                room.getCode(), room.getName(), room.getVoteMode(), room.getQuestionDurationSeconds());
        room = roomRepository.save(room);
        log.debug("Room persisted: id={} code={} status={} voteMode={} hostPlayerId={}",
                room.getId(), room.getCode(), room.getStatus(), room.getVoteMode(), room.getHostPlayerId());

        Player host = Player.builder()
        .room(room)
        .name(hostName)
        .isHost(true)
        .isOriginalHost(true)
        .build();

        log.debug("Creating host entity: roomId={} hostName={}", room.getId(), host.getName());
        host = playerRepository.save(host);
        log.debug("Host persisted: id={} token={} roomId={}", host.getId(), host.getToken(), room.getId());

        room.setHostPlayerId(host.getId());
        roomRepository.save(room);
        log.debug("Room host assigned: roomId={} hostPlayerId={}", room.getId(), room.getHostPlayerId());

        log.info("Room created: code={} name={} host={}", room.getCode(), room.getName(), host.getName());

        return toRoomResponse(room, List.of(host), host.getToken());
    }

    @Transactional
    public RoomResponse joinRoom(JoinRoomRequest request) {
        String playerName = sanitize(request.getPlayerName());
        if (playerName.isBlank()) {
            throw new IllegalArgumentException("Player name is required.");
        }

        Room room = roomRepository.findByCode(request.getCode().toUpperCase())
                .orElseThrow(() -> new RoomNotFoundException("Room not found. Check the code and try again."));

        validateRoomIsJoinable(room);

        if (playerRepository.existsByRoomIdAndName(room.getId(), playerName)) {
            throw new DuplicateNameException("That name is already taken in this room. Try another one.");
        }

        if (playerRepository.countByRoomId(room.getId()) >= room.getMaxPlayers()) {
            throw new RoomFullException("This room is full.");
        }

        Player player = Player.builder()
                .room(room)
                .name(playerName)
                .isHost(false)
                .build();

        player = playerRepository.save(player);

        log.info("Player joined: room={} player={}", room.getCode(), player.getName());
        eventPublisher.publishPlayerJoined(room.getId(), player.getId(), player.getName());
        List<Player> allPlayers = playerRepository.findAllByRoomId(room.getId());
        return toRoomResponse(room, allPlayers, player.getToken());
    }

    @Transactional
    public RoomResponse rejoinRoom(UUID token) {
        Player player = playerRepository.findByToken(token)
                .orElseThrow(() -> new PlayerNotFoundException("Session not found. Please join again."));

        Room room = player.getRoom();
        validateRoomNotExpired(room);

        List<Player> allPlayers = playerRepository.findAllByRoomId(room.getId());
        return toRoomResponse(room, allPlayers, player.getToken());
    }

    @Transactional
    public void handleHostDisconnect(UUID roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room not found."));

        List<Player> players = playerRepository.findAllByRoomId(roomId).stream()
                .filter(Player::isActive)
                .sorted((a, b) -> a.getJoinedAt().compareTo(b.getJoinedAt()))
                .collect(Collectors.toList());

        if (players.isEmpty()) return;

        Player newHost = players.get(0);
        newHost.setHost(true);
        playerRepository.save(newHost);

        room.setHostPlayerId(newHost.getId());
        roomRepository.save(room);

        log.info("Host reassigned: room={} newHost={}", room.getCode(), newHost.getName());
    }

    private void validateRoomIsJoinable(Room room) {
        validateRoomNotExpired(room);
        if (room.getStatus() != RoomStatus.WAITING) {
            throw new InvalidRoomStateException("This game has already started.");
        }
    }

    private void validateRoomNotExpired(Room room) {
        Instant expiresAt = room.getExpiresAt();
        Instant now = Instant.now();
        log.info("DEBUG expiry check: status={} expiresAt={} now={} isBefore={}",
                room.getStatus(), expiresAt, now,
                expiresAt != null && expiresAt.isBefore(now));
        if (room.getStatus() == RoomStatus.EXPIRED || (expiresAt != null && expiresAt.isBefore(now))) {
            throw new RoomExpiredException("This room has expired.");
        }
    }
    private String generateUniqueCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
            }
            code = sb.toString();
        } while (roomRepository.existsByCode(code));
        return code;
    }

    private String sanitize(String input) {
        if (input == null) return "";
        return input.trim().replaceAll("[<>\"'%;()&+]", "");
    }

    private RoomResponse toRoomResponse(Room room, List<Player> players, UUID playerToken) {
    return RoomResponse.builder()
            .id(room.getId())
            .code(room.getCode())
            .name(room.getName())
            .status(room.getStatus())
            .voteMode(room.getVoteMode())
            .maxPlayers(room.getMaxPlayers())
            .maxQuestions(room.getMaxQuestions())
            .questionDurationSeconds(room.getQuestionDurationSeconds())
            .players(players.stream().map(this::toPlayerResponse).collect(Collectors.toList()))
            .playerToken(playerToken)
            .build();
}

    private PlayerResponse toPlayerResponse(Player player) {
        return PlayerResponse.builder()
                .id(player.getId())
                .token(player.getToken())
                .name(player.getName())
                .isHost(player.isHost())
                .isActive(player.isActive())
                .build();
    }

    @Transactional
public void handleHostReconnect(UUID roomId, Player reconnectingPlayer) {
    if (!reconnectingPlayer.isOriginalHost()) return; // only original host gets auto-restored

    List<Player> players = playerRepository.findAllByRoomId(roomId);
    for (Player p : players) {
        if (p.isHost() && !p.getId().equals(reconnectingPlayer.getId())) {
            p.setHost(false);
            playerRepository.save(p);
        }
    }

    reconnectingPlayer.setHost(true);
    playerRepository.save(reconnectingPlayer);

    Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new RoomNotFoundException("Room not found."));
    room.setHostPlayerId(reconnectingPlayer.getId());
    roomRepository.save(room);

    log.info("Host restored: room={} originalHost={}", room.getCode(), reconnectingPlayer.getName());
}
}

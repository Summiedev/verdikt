package com.verdikt.verdikt_backend.service;

import com.verdikt.verdikt_backend.model.Player;
import com.verdikt.verdikt_backend.model.Room;
import com.verdikt.verdikt_backend.model.enums.RoomStatus;
import com.verdikt.verdikt_backend.repository.PlayerRepository;
import com.verdikt.verdikt_backend.repository.QuestionRepository;
import com.verdikt.verdikt_backend.repository.RoomQuestionRepository;
import com.verdikt.verdikt_backend.repository.RoomRepository;
import com.verdikt.verdikt_backend.websocket.WebSocketEventPublisher;
import com.verdikt.verdikt_backend.exception.InvalidRoomStateException;
import com.verdikt.verdikt_backend.exception.PlayerNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameServiceAuthorizationTest {

    @Mock private WebSocketEventPublisher eventPublisher;
    @Mock private RoomRepository roomRepository;
    @Mock private QuestionRepository questionRepository;
    @Mock private RoomQuestionRepository roomQuestionRepository;
    @Mock private PlayerRepository playerRepository;
    @Mock private CacheManager cacheManager;
    @Mock private BusinessMetricsService businessMetricsService;

    @InjectMocks private GameService gameService;

    @Test
    void hostTokenFromAnotherRoomCannotStartGame() {
        UUID roomId = UUID.randomUUID();
        UUID otherRoomId = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        Room room = Room.builder().id(roomId).status(RoomStatus.WAITING).build();
        Room otherRoom = Room.builder().id(otherRoomId).build();
        Player player = Player.builder().id(UUID.randomUUID()).room(otherRoom).isHost(true).token(token).build();

        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(playerRepository.findByTokenAndExpiresAtAfter(eq(token), any(LocalDateTime.class)))
                .thenReturn(Optional.of(player));

        assertThrows(PlayerNotFoundException.class, () -> gameService.startGame(roomId, token, null));
    }

    @Test
    void nonHostCannotAdvanceQuestion() {
        UUID roomId = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        Room room = Room.builder().id(roomId).hostPlayerId(UUID.randomUUID()).status(RoomStatus.IN_PROGRESS).build();
        Player player = Player.builder().id(playerId).room(room).isHost(false).isActive(true).token(token).build();

        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(playerRepository.findByTokenAndExpiresAtAfter(eq(token), any(LocalDateTime.class)))
                .thenReturn(Optional.of(player));

        InvalidRoomStateException error = assertThrows(
                InvalidRoomStateException.class,
                () -> gameService.advanceToNextQuestion(roomId, token)
        );
        assertEquals("NOT_HOST", error.getCode());
    }

    @Test
    void gameNeedsTwoActivePlayersBeforeStarting() {
        UUID roomId = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        Room room = Room.builder().id(roomId).hostPlayerId(hostId).status(RoomStatus.WAITING).build();
        Player host = Player.builder().id(hostId).room(room).isHost(true).isActive(true).token(token).build();

        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(playerRepository.findByTokenAndExpiresAtAfter(eq(token), any(LocalDateTime.class)))
                .thenReturn(Optional.of(host));
        when(playerRepository.findAllByRoomId(roomId)).thenReturn(List.of(host));

        InvalidRoomStateException error = assertThrows(
                InvalidRoomStateException.class,
                () -> gameService.startGame(roomId, token, null)
        );
        assertEquals("INVALID_ROOM_STATE", error.getCode());
    }
}

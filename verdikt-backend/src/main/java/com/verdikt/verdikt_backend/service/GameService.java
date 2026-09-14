package com.verdikt.verdikt_backend.service;

import com.verdikt.verdikt_backend.config.CacheConstants;
import com.verdikt.verdikt_backend.dto.request.StartGameRequest;
import com.verdikt.verdikt_backend.dto.response.CurrentQuestionResponse;
import com.verdikt.verdikt_backend.dto.response.QuestionPreviewResponse;
import com.verdikt.verdikt_backend.exception.*;
import com.verdikt.verdikt_backend.model.Player;
import com.verdikt.verdikt_backend.model.Question;
import com.verdikt.verdikt_backend.model.Room;
import com.verdikt.verdikt_backend.model.RoomQuestion;
import com.verdikt.verdikt_backend.model.enums.RoomStatus;
import com.verdikt.verdikt_backend.model.enums.SpiceLevel;
import com.verdikt.verdikt_backend.repository.PlayerRepository;
import com.verdikt.verdikt_backend.repository.QuestionRepository;
import com.verdikt.verdikt_backend.repository.RoomQuestionRepository;
import com.verdikt.verdikt_backend.repository.RoomRepository;
import com.verdikt.verdikt_backend.service.BusinessMetricsService;
import com.verdikt.verdikt_backend.websocket.WebSocketEventPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameService {

    private static final int DEFAULT_QUESTION_COUNT = 10;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final WebSocketEventPublisher eventPublisher;
    private final RoomRepository roomRepository;
    private final QuestionRepository questionRepository;
    private final RoomQuestionRepository roomQuestionRepository;
    private final PlayerRepository playerRepository;
    private final CacheManager cacheManager;
    private final BusinessMetricsService businessMetricsService;

    private void evictRoomCache(String code) {
        if (code != null && cacheManager.getCache(CacheConstants.ROOM_BY_CODE) != null) {
            cacheManager.getCache(CacheConstants.ROOM_BY_CODE).evict(code);
        }
    }

    @Caching(evict = {
            @CacheEvict(value = CacheConstants.NON_CUSTOM_QUESTIONS, key = "'all'"),
            @CacheEvict(value = CacheConstants.VOTE_STATE, key = "#roomId")
    })
    @Transactional
    public void startGame(UUID roomId, UUID hostToken, StartGameRequest request) {
        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room not found."));

        ensureRoomActive(room);
        evictRoomCache(room.getCode());

        Player host = requirePlayerInRoom(hostToken, roomId);
        if (!host.isHost() || !host.isActive() || !host.getId().equals(room.getHostPlayerId())) {
            throw new InvalidRoomStateException("NOT_HOST", "Only the host can start the game.");
        }

        if (room.getStatus() != RoomStatus.WAITING) {
            throw new InvalidRoomStateException("ROOM_ALREADY_STARTED", "Game has already started or finished.");
        }

        long activePlayers = playerRepository.findAllByRoomId(roomId).stream()
                .filter(Player::isActive)
                .count();
        if (activePlayers < 2) {
            throw new InvalidRoomStateException("INVALID_ROOM_STATE", "At least two active players are required to start.");
        }

        List<Question> finalQuestions = resolveQuestionsForGame(room, request);

        List<RoomQuestion> roomQuestions = IntStream.range(0, finalQuestions.size())
                .mapToObj(i -> RoomQuestion.builder()
                        .room(room)
                        .question(finalQuestions.get(i))
                        .orderIndex(i)
                        .isActive(i == 0)
                        .build())
                .collect(Collectors.toList());
        roomQuestionRepository.saveAll(roomQuestions);

        room.setStatus(RoomStatus.IN_PROGRESS);
        room.setCurrentQuestionStartedAt(Instant.now());
        roomRepository.save(room);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    eventPublisher.publishGameStarted(roomId);
                }
            });
        } else {
            eventPublisher.publishGameStarted(roomId);
        }

        businessMetricsService.incrementGamesStarted();

        log.info("Game started: room={} questionCount={}", room.getCode(), finalQuestions.size());
    }
    @CacheEvict(value = {CacheConstants.ACTIVE_QUESTION, CacheConstants.VOTE_STATE}, key = "#roomId")
    @Transactional
    public CurrentQuestionResponse advanceToNextQuestion(UUID roomId, UUID playerToken) {
        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room not found."));

        ensureRoomActive(room);
        evictRoomCache(room.getCode());

        Player requester = requirePlayerInRoom(playerToken, roomId);

        if (!requester.isHost() || !requester.isActive() || !requester.getId().equals(room.getHostPlayerId())) {
            throw new InvalidRoomStateException("NOT_HOST", "Only the host can advance to the next question.");
        }

        if (room.getStatus() != RoomStatus.IN_PROGRESS) {
            throw new InvalidRoomStateException("GAME_ALREADY_ENDED", "Game is not in progress.");
        }

        RoomQuestion current = roomQuestionRepository.findByRoomIdAndIsActiveTrue(roomId)
                .orElseThrow(() -> new InvalidRoomStateException("QUESTION_NOT_ACTIVE", "No active question found."));

        current.setActive(false);
        current.setAnswered(true);
        roomQuestionRepository.save(current);

        List<RoomQuestion> all = roomQuestionRepository.findAllByRoomIdOrderByOrderIndex(roomId);
        int nextIndex = current.getOrderIndex() + 1;

        if (nextIndex >= all.size()) {
            endGame(room);
            return null; // signals game ended
        }

        RoomQuestion next = all.get(nextIndex);
        next.setActive(true);
        roomQuestionRepository.save(next);

        room.setCurrentQuestionStartedAt(Instant.now());
        roomRepository.save(room);

        CurrentQuestionResponse response = toCurrentQuestionResponse(roomId, next);
        eventPublisher.publishQuestionAdvanced(roomId, response); // broadcast the clean DTO, not the entity

        log.info("Advanced question: room={} newIndex={}", room.getCode(), nextIndex);
        return response;
    }

    @Caching(evict = {
            @CacheEvict(value = CacheConstants.ROOM_BY_CODE, key = "#room.code"),
            @CacheEvict(value = CacheConstants.VOTE_STATE, key = "#room.id")
    })
    @Transactional
    public void endGame(Room room) {
        room.setStatus(RoomStatus.FINISHED);
        roomRepository.save(room);
        log.info("Game finished: room={}", room.getCode());
        eventPublisher.publishGameEnded(room.getId());
    }
    @Transactional(readOnly = true)
    public List<QuestionPreviewResponse> previewRandomQuestions(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("Question count must be at least 1.");
        }

        List<Question> questions = questionRepository.findRandomQuestions(count);
        if (questions.isEmpty()) {
            throw new NoQuestionsAvailableException("No built-in questions exist yet. Add your own questions to start.");
        }
        return questions.stream()
                .map(q -> QuestionPreviewResponse.builder()
                        .id(q.getId())
                        .text(q.getText())
                        .spiceLevel(q.getSpiceLevel().name())
                        .build())
                .collect(Collectors.toList());
    }
    @Cacheable(value = CacheConstants.ACTIVE_QUESTION, key = "#roomId")
    @Transactional(readOnly = true)
    public CurrentQuestionResponse getCurrentQuestion(UUID roomId) {
        RoomQuestion rq = roomQuestionRepository.findByRoomIdAndIsActiveTrue(roomId)
                .orElseThrow(() -> new InvalidRoomStateException("QUESTION_NOT_ACTIVE", "No active question right now."));
        return toCurrentQuestionResponse(roomId, rq);
    }

    @Transactional(readOnly = true)
    public CurrentQuestionResponse toCurrentQuestionResponse(UUID roomId, RoomQuestion rq) {
        Room room = rq.getRoom();
        if (room == null) {
            room = roomRepository.findById(roomId)
                    .orElseThrow(() -> new RoomNotFoundException("Room not found."));
        }
        int total = roomQuestionRepository.countByRoomId(roomId);
        return CurrentQuestionResponse.builder()
                .questionId(rq.getQuestion().getId())
                .text(rq.getQuestion().getText())
                .questionIndex(rq.getOrderIndex() + 1)
                .totalQuestions(total)
                .startedAt(room.getCurrentQuestionStartedAt())
                .build();
    }
    @CacheEvict(value = {CacheConstants.ACTIVE_QUESTION, CacheConstants.VOTE_STATE}, key = "#roomId")
    @Transactional
    public void endGameEarly(UUID roomId, UUID hostToken) {
        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room not found."));

        ensureRoomActive(room);
        evictRoomCache(room.getCode());

        Player requester = requirePlayerInRoom(hostToken, roomId);

        if (!requester.isHost() || !requester.isActive() || !requester.getId().equals(room.getHostPlayerId())) {
            throw new InvalidRoomStateException("NOT_HOST", "Only the host can end the game.");
        }

        if (room.getStatus() != RoomStatus.IN_PROGRESS) {
            throw new InvalidRoomStateException("GAME_ALREADY_ENDED", "Game isn't in progress.");
        }

        endGame(room);
    }

    private List<Question> pickRandomQuestions(List<Question> pool, int count) {
        List<Question> shuffled = new java.util.ArrayList<>(pool);
        for (int i = shuffled.size() - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            Question temp = shuffled.get(i);
            shuffled.set(i, shuffled.get(j));
            shuffled.set(j, temp);
        }
        return shuffled.subList(0, count);
    }

    private List<Question> resolveQuestionsForGame(Room room, StartGameRequest request) {
        List<Question> finalQuestions = new ArrayList<>();

        if (request != null && request.getSelectedQuestionIds() != null) {
            List<UUID> ids = request.getSelectedQuestionIds().stream()
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());

            if (!ids.isEmpty()) {
                List<Question> selected = questionRepository.findAllByIds(ids);
                for (Question q : selected) {
                    addUnique(finalQuestions, q);
                }
            }
        }

        if (request != null && request.getCustomQuestionTexts() != null) {
            Set<String> seenCustomTexts = new java.util.HashSet<>();
            for (String text : request.getCustomQuestionTexts()) {
                if (text == null) continue;
                String trimmed = text.trim();
                if (trimmed.length() < 5) continue;
                String dedupeKey = trimmed.toLowerCase(java.util.Locale.ROOT);
                if (!seenCustomTexts.add(dedupeKey)) continue;
                Question custom = Question.builder()
                        .text(trimmed)
                        .category("custom")
                        .spiceLevel(SpiceLevel.MEDIUM)
                        .isCustom(true)
                        .build();
                addUnique(finalQuestions, questionRepository.save(custom));
            }
        }

        if (!finalQuestions.isEmpty()) {
            return finalQuestions.size() > desiredQuestionCount(room)
                    ? finalQuestions.subList(0, desiredQuestionCount(room))
                    : finalQuestions;
        }

        int desiredCount = desiredQuestionCount(room);
        List<Question> randomQuestions = questionRepository.findRandomQuestions(desiredCount);
        if (randomQuestions.isEmpty()) {
            throw new NoQuestionsAvailableException("No built-in questions exist yet. Add your own questions to start.");
        }
        return randomQuestions;
    }

    private void addUnique(List<Question> target, Question candidate) {
        if (candidate == null) return;
        boolean alreadyAdded = target.stream().anyMatch(existing -> existing.getId().equals(candidate.getId()));
        if (!alreadyAdded) {
            target.add(candidate);
        }
    }

    private int desiredQuestionCount(Room room) {
        return room.getMaxQuestions() != null && room.getMaxQuestions() > 0
                ? room.getMaxQuestions()
                : DEFAULT_QUESTION_COUNT;
    }

    private Player requirePlayerInRoom(UUID token, UUID roomId) {
        if (token == null) {
            throw new PlayerNotFoundException("Player session not found.");
        }
        Player player = playerRepository.findByTokenAndExpiresAtAfter(token, LocalDateTime.now())
                .orElseThrow(() -> new PlayerNotFoundException("Player session not found or expired."));
        if (player.getRoom() == null || !roomId.equals(player.getRoom().getId())) {
            throw new PlayerNotFoundException("Player session does not belong to this room.");
        }
        return player;
    }

    private void ensureRoomActive(Room room) {
        if (room.getStatus() == RoomStatus.EXPIRED
                || (room.getExpiresAt() != null && room.getExpiresAt().isBefore(Instant.now()))) {
            throw new RoomExpiredException("This room has expired.");
        }
    }
}

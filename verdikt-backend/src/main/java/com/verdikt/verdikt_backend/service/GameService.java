package com.verdikt.verdikt_backend.service;

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
import com.verdikt.verdikt_backend.websocket.WebSocketEventPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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

    @Transactional
public void startGame(UUID roomId, UUID hostToken, StartGameRequest request) {
    Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new RoomNotFoundException("Room not found."));

    if (room.getStatus() != RoomStatus.WAITING) {
        throw new InvalidRoomStateException("Game has already started or finished.");
    }

    List<Question> finalQuestions = resolveQuestionsForGame(room, request);

    for (int i = 0; i < finalQuestions.size(); i++) {
        RoomQuestion rq = RoomQuestion.builder()
                .room(room)
                .question(finalQuestions.get(i))
                .orderIndex(i)
                .isActive(i == 0)
                .build();
        roomQuestionRepository.save(rq);
    }

    // questionDurationSeconds is already set on Room from creation — no change needed here
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

    log.info("Game started: room={} questionCount={}", room.getCode(), finalQuestions.size());
}
    @Transactional
    public CurrentQuestionResponse advanceToNextQuestion(UUID roomId, UUID playerToken) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room not found."));

        Player requester = playerRepository.findByToken(playerToken)
                .orElseThrow(() -> new PlayerNotFoundException("Player session not found."));

        if (!requester.isHost()) {
            throw new InvalidRoomStateException("Only the host can advance to the next question.");
        }

        if (room.getStatus() != RoomStatus.IN_PROGRESS) {
            throw new InvalidRoomStateException("Game is not in progress.");
        }

        RoomQuestion current = roomQuestionRepository.findByRoomIdAndIsActiveTrue(roomId)
                .orElseThrow(() -> new InvalidRoomStateException("No active question found."));

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

    @Transactional
    public void endGame(Room room) {
        room.setStatus(RoomStatus.FINISHED);
        roomRepository.save(room);
        log.info("Game finished: room={}", room.getCode());
        eventPublisher.publishGameEnded(room.getId());
    }
@Transactional(readOnly = true)
public List<QuestionPreviewResponse> previewRandomQuestions(int count) {
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
    @Transactional(readOnly = true)
    public CurrentQuestionResponse getCurrentQuestion(UUID roomId) {
        RoomQuestion rq = roomQuestionRepository.findByRoomIdAndIsActiveTrue(roomId)
                .orElseThrow(() -> new InvalidRoomStateException("No active question right now."));
        return toCurrentQuestionResponse(roomId, rq);
    }

    @Transactional(readOnly = true)
public CurrentQuestionResponse toCurrentQuestionResponse(UUID roomId, RoomQuestion rq) {
    int total = roomQuestionRepository.countByRoomId(roomId);
    Room room = roomRepository.findById(roomId).orElseThrow();   // ← ADD
    return CurrentQuestionResponse.builder()
            .questionId(rq.getQuestion().getId())
            .text(rq.getQuestion().getText())
            .questionIndex(rq.getOrderIndex() + 1)
            .totalQuestions(total)
            .startedAt(room.getCurrentQuestionStartedAt())        // ← ADD
            .build();
}
    @Transactional
public void endGameEarly(UUID roomId, UUID hostToken) {
    Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new RoomNotFoundException("Room not found."));

    Player requester = playerRepository.findByToken(hostToken)
            .orElseThrow(() -> new PlayerNotFoundException("Player session not found."));

    if (!requester.isHost()) {
        throw new InvalidRoomStateException("Only the host can end the game.");
    }

    if (room.getStatus() != RoomStatus.IN_PROGRESS) {
        throw new InvalidRoomStateException("Game isn't in progress.");
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
            for (UUID id : request.getSelectedQuestionIds()) {
                questionRepository.findById(id).ifPresent(question -> addUnique(finalQuestions, question));
            }
        }

        if (request != null && request.getCustomQuestionTexts() != null) {
            for (String text : request.getCustomQuestionTexts()) {
                if (text == null || text.trim().length() < 5) continue;
                Question custom = Question.builder()
                        .text(text.trim())
                        .category("custom")
                        .spiceLevel(SpiceLevel.MEDIUM)
                        .isCustom(true)
                        .build();
                addUnique(finalQuestions, questionRepository.save(custom));
            }
        }

        if (!finalQuestions.isEmpty()) {
            return finalQuestions;
        }

        int desiredCount = room.getMaxQuestions() != null && room.getMaxQuestions() > 0
                ? room.getMaxQuestions()
                : DEFAULT_QUESTION_COUNT;
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
}

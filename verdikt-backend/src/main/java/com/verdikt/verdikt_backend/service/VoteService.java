package com.verdikt.verdikt_backend.service;

import com.verdikt.verdikt_backend.config.CacheConstants;
import com.verdikt.verdikt_backend.dto.request.CastVoteRequest;
import com.verdikt.verdikt_backend.exception.*;
import com.verdikt.verdikt_backend.model.*;
import com.verdikt.verdikt_backend.model.enums.RoomStatus;
import com.verdikt.verdikt_backend.model.enums.VoteMode;
import com.verdikt.verdikt_backend.repository.*;
import com.verdikt.verdikt_backend.service.BusinessMetricsService;
import com.verdikt.verdikt_backend.websocket.WebSocketEventPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoteService {

    private final VoteRepository voteRepository;
    private final RoomRepository roomRepository;
    private final PlayerRepository playerRepository;
    private final QuestionRepository questionRepository;
    private final RoomQuestionRepository roomQuestionRepository;
    private final WebSocketEventPublisher eventPublisher;
    private final BusinessMetricsService businessMetricsService;
    // removed EntityManager — flush/clear inside a vote loop was causing unnecessary DB round trips

    @CacheEvict(value = CacheConstants.VOTE_STATE, key = "#roomId")
    @Transactional
    public List<Map<String, Object>> castVotes(UUID roomId, UUID voterToken, CastVoteRequest request) {
        if (request == null || request.getQuestionId() == null) {
            throw new IllegalArgumentException("Question is required.");
        }

        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room not found."));

        if (room.getStatus() != RoomStatus.IN_PROGRESS)
            throw new InvalidRoomStateException("Voting is not open right now.");

        Player voter = requireVoterInRoom(voterToken, roomId);

        Question question = questionRepository.findById(request.getQuestionId())
                .orElseThrow(() -> new InvalidRoomStateException("INVALID_VOTE", "Question not found."));

        RoomQuestion roomQuestion = roomQuestionRepository.findByRoomIdAndIsActiveTrue(roomId)
                .orElseThrow(() -> new InvalidRoomStateException("No active question right now."));

        if (!roomQuestion.getQuestion().getId().equals(question.getId()))
            throw new InvalidRoomStateException("QUESTION_NOT_ACTIVE", "This question is no longer active.");

        List<UUID> targetIds = request.getVotedForPlayerIds() == null ? List.of() : request.getVotedForPlayerIds();
        Set<UUID> desiredSelections = new LinkedHashSet<>(targetIds);
        Set<UUID> existingSelections = new HashSet<>(voteRepository.findVotedForIdsByRoomAndQuestionAndVoter(roomId, question.getId(), voter.getId()));

        if (desiredSelections.equals(existingSelections)) {
            return buildVoteState(
                    voteRepository.findAllByRoomIdAndQuestionIdWithVoterAndVotedFor(roomId, question.getId()),
                    room.getVoteMode() == com.verdikt.verdikt_backend.model.enums.VoteMode.PUBLIC
            );
        }

        List<UUID> toRemove = existingSelections.stream()
                .filter(id -> !desiredSelections.contains(id))
                .toList();
        if (!toRemove.isEmpty()) {
            voteRepository.deleteByRoomIdAndVoterIdAndQuestionIdAndVotedForIdIn(roomId, voter.getId(), question.getId(), toRemove);
        }

        if (desiredSelections.isEmpty()) {
            voteRepository.deleteByRoomIdAndVoterIdAndQuestionId(roomId, voter.getId(), question.getId());
        } else {
            List<UUID> desiredSelectionList = new ArrayList<>(desiredSelections);
            Map<UUID, Player> targetPlayers = playerRepository.findAllByIdWithRoom(desiredSelectionList)
                    .stream().collect(Collectors.toMap(Player::getId, p -> p));

            for (UUID votedForId : desiredSelections) {
                if (!existingSelections.contains(votedForId)) {
                    Player votedFor = targetPlayers.get(votedForId);
                    if (votedFor == null) {
                        throw new PlayerNotFoundException("Player doesn't exist.");
                    }
                    if (!votedFor.getRoom().getId().equals(roomId)) {
                        throw new InvalidRoomStateException("INVALID_VOTE", "Invalid vote target.");
                    }

                    Vote vote = Vote.builder()
                            .room(room)
                            .question(question)
                            .voter(voter)
                            .votedFor(votedFor)
                            .build();
                    voteRepository.save(vote);
                }
            }
        }

        List<Vote> currentVotes = voteRepository.findAllByRoomIdAndQuestionIdWithVoterAndVotedFor(roomId, question.getId());
        List<Map<String, Object>> authoritativeState = buildVoteState(currentVotes, room.getVoteMode() == com.verdikt.verdikt_backend.model.enums.VoteMode.PUBLIC);
        eventPublisher.publishVoteState(roomId, question.getId(), authoritativeState, room.getVoteMode());

        businessMetricsService.incrementVotesCast();

        log.info("Votes cast: room={} voter={} count={}", room.getCode(), voter.getName(), desiredSelections.size());
        return authoritativeState;
    }

    @Transactional(readOnly = true)
    public List<Vote> getVotesForQuestion(UUID roomId, UUID questionId) {
        return voteRepository.findAllByRoomIdAndQuestionIdWithVoterAndVotedFor(roomId, questionId);
    }

    @CacheEvict(value = CacheConstants.VOTE_STATE, key = "#roomId")
    @Transactional
    public List<Map<String, Object>> removeVote(UUID roomId, UUID voterToken, CastVoteRequest request) {
        if (request == null || request.getQuestionId() == null) {
            throw new IllegalArgumentException("Question is required.");
        }

        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room not found."));
        if (room.getStatus() != RoomStatus.IN_PROGRESS) {
            throw new InvalidRoomStateException("GAME_ALREADY_ENDED", "Voting is not open right now.");
        }
        Player voter = requireVoterInRoom(voterToken, roomId);
        RoomQuestion activeQuestion = roomQuestionRepository.findByRoomIdAndIsActiveTrue(roomId)
                .orElseThrow(() -> new InvalidRoomStateException("QUESTION_NOT_ACTIVE", "No active question right now."));
        if (!activeQuestion.getQuestion().getId().equals(request.getQuestionId())) {
            throw new InvalidRoomStateException("QUESTION_NOT_ACTIVE", "This question is no longer active.");
        }

        List<UUID> targets = request.getVotedForPlayerIds() == null ? List.of() : request.getVotedForPlayerIds();
        if (targets.isEmpty()) {
            voteRepository.deleteByRoomIdAndVoterIdAndQuestionId(roomId, voter.getId(), request.getQuestionId());
        } else {
            voteRepository.deleteByRoomIdAndVoterIdAndQuestionIdAndVotedForIdIn(
                    roomId, voter.getId(), request.getQuestionId(), targets);
        }

        List<Vote> currentVotes = voteRepository.findAllByRoomIdAndQuestionIdWithVoterAndVotedFor(roomId, request.getQuestionId());
        List<Map<String, Object>> authoritativeState = buildVoteState(currentVotes, room.getVoteMode() == com.verdikt.verdikt_backend.model.enums.VoteMode.PUBLIC);
        eventPublisher.publishVoteState(
                roomId,
                request.getQuestionId(),
                authoritativeState,
                room.getVoteMode()
        );
        return authoritativeState;
    }

    @Transactional(readOnly = true)
    public List<Map<String, String>> getCurrentVoteState(UUID roomId, UUID voterToken) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room not found."));
        requireVoterInRoom(voterToken, roomId);

        RoomQuestion active = roomQuestionRepository.findByRoomIdAndIsActiveTrue(roomId).orElse(null);
        if (active == null) return List.of();

        boolean isPublic = room.getVoteMode() == com.verdikt.verdikt_backend.model.enums.VoteMode.PUBLIC;

        List<Vote> votes = voteRepository.findAllByRoomIdAndQuestionIdWithVoterAndVotedFor(
                roomId, active.getQuestion().getId());

        List<Map<String, String>> result = votes.stream().map(v -> {
            Map<String, String> m = new HashMap<>();
            m.put("votedForId", v.getVotedFor().getId().toString());
            if (isPublic) {
                m.put("voterId", v.getVoter().getId().toString());
                m.put("voterName", v.getVoter().getName());
            }
            return m;
        }).collect(Collectors.toList());

        return result;
    }

    private List<Map<String, Object>> buildVoteState(List<Vote> votes, boolean isPublic) {
        List<Map<String, Object>> state = new ArrayList<>();
        for (Vote vote : votes) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("votedForId", vote.getVotedFor().getId().toString());
            if (isPublic) {
                entry.put("voterId", vote.getVoter().getId().toString());
                entry.put("voterName", vote.getVoter().getName());
            }
            state.add(entry);
        }
        return state;
    }

    private Player requireVoterInRoom(UUID token, UUID roomId) {
        if (token == null) {
            throw new PlayerNotFoundException("Player session not found.");
        }
        Player voter = playerRepository.findByTokenAndExpiresAtAfter(token, LocalDateTime.now())
                .orElseThrow(() -> new PlayerNotFoundException("Player session not found or expired."));
        if (voter.getRoom() == null || !roomId.equals(voter.getRoom().getId())) {
            throw new PlayerNotFoundException("Player session does not belong to this room.");
        }
        return voter;
    }
}

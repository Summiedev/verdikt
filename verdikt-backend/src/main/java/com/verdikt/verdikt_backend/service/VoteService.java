package com.verdikt.verdikt_backend.service;

import com.verdikt.verdikt_backend.dto.request.CastVoteRequest;
import com.verdikt.verdikt_backend.exception.*;
import com.verdikt.verdikt_backend.model.*;
import com.verdikt.verdikt_backend.model.enums.RoomStatus;
import com.verdikt.verdikt_backend.repository.*;
import com.verdikt.verdikt_backend.websocket.WebSocketEventPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
    // removed EntityManager — flush/clear inside a vote loop was causing unnecessary DB round trips

    @Transactional
    public List<Map<String, Object>> castVotes(UUID roomId, UUID voterToken, CastVoteRequest request) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room not found."));

        if (room.getStatus() != RoomStatus.IN_PROGRESS)
            throw new InvalidRoomStateException("Voting is not open right now.");

        Player voter = playerRepository.findByToken(voterToken)
                .orElseThrow(() -> new PlayerNotFoundException("Player session not found."));

        Question question = questionRepository.findById(request.getQuestionId())
                .orElseThrow(() -> new IllegalArgumentException("Question not found."));

        RoomQuestion roomQuestion = roomQuestionRepository.findByRoomIdAndIsActiveTrue(roomId)
                .orElseThrow(() -> new InvalidRoomStateException("No active question right now."));

        if (!roomQuestion.getQuestion().getId().equals(question.getId()))
            throw new InvalidRoomStateException("This question is no longer active.");

        List<UUID> targetIds = request.getVotedForPlayerIds() == null ? List.of() : request.getVotedForPlayerIds();
        Set<UUID> desiredSelections = new LinkedHashSet<>(targetIds);
        Set<UUID> existingSelections = new HashSet<>(voteRepository.findVotedForIdsByRoomAndQuestionAndVoter(roomId, question.getId(), voter.getId()));

        List<UUID> toRemove = existingSelections.stream()
                .filter(id -> !desiredSelections.contains(id))
                .toList();
        if (!toRemove.isEmpty()) {
            voteRepository.deleteByRoomIdAndVoterIdAndQuestionIdAndVotedForIdIn(roomId, voter.getId(), question.getId(), toRemove);
        }

        if (desiredSelections.isEmpty()) {
            voteRepository.deleteByRoomIdAndVoterIdAndQuestionId(roomId, voter.getId(), question.getId());
        } else {
            Map<UUID, Player> targetPlayers = playerRepository.findAllById(desiredSelections)
                    .stream().collect(Collectors.toMap(Player::getId, p -> p));

            for (UUID votedForId : desiredSelections) {
                if (!existingSelections.contains(votedForId)) {
                    Player votedFor = targetPlayers.get(votedForId);
                    if (votedFor == null) {
                        throw new PlayerNotFoundException("Player doesn't exist.");
                    }
                    if (!votedFor.getRoom().getId().equals(roomId)) {
                        throw new IllegalArgumentException("Invalid vote target.");
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

        voteRepository.flush();
        List<Vote> currentVotes = voteRepository.findAllByRoomIdAndQuestionId(roomId, question.getId());
        List<Map<String, Object>> authoritativeState = buildVoteState(currentVotes, room.getVoteMode() == com.verdikt.verdikt_backend.model.enums.VoteMode.PUBLIC);
        eventPublisher.publishVoteState(roomId, question.getId(), authoritativeState, room.getVoteMode());

        log.info("Votes cast: room={} voter={} count={}", room.getCode(), voter.getName(), desiredSelections.size());
        return authoritativeState;
    }

    @Transactional(readOnly = true)
    public List<Vote> getVotesForQuestion(UUID roomId, UUID questionId) {
        return voteRepository.findAllByRoomIdAndQuestionId(roomId, questionId);
    }

    @Transactional
    public List<Map<String, Object>> removeVote(UUID roomId, UUID voterToken, CastVoteRequest request) {
        if (request.getQuestionId() == null) return List.of();

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room not found."));
        Player voter = playerRepository.findByToken(voterToken)
                .orElseThrow(() -> new PlayerNotFoundException("Player not found."));

        List<UUID> targets = request.getVotedForPlayerIds() == null ? List.of() : request.getVotedForPlayerIds();
        if (targets.isEmpty()) {
            voteRepository.deleteByRoomIdAndVoterIdAndQuestionId(roomId, voter.getId(), request.getQuestionId());
        } else {
            voteRepository.deleteByRoomIdAndVoterIdAndQuestionIdAndVotedForIdIn(
                    roomId, voter.getId(), request.getQuestionId(), targets);
        }

        voteRepository.flush();

        List<Vote> currentVotes = voteRepository.findAllByRoomIdAndQuestionId(roomId, request.getQuestionId());
        List<Map<String, Object>> authoritativeState = buildVoteState(currentVotes, room.getVoteMode() == com.verdikt.verdikt_backend.model.enums.VoteMode.PUBLIC);
        eventPublisher.publishVoteState(
                roomId,
                request.getQuestionId(),
                authoritativeState,
                room.getVoteMode()
        );
        return authoritativeState;
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
}
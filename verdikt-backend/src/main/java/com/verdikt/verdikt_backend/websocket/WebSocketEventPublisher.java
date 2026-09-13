package com.verdikt.verdikt_backend.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    @Async("webSocketTaskExecutor")
    public void publishVoteCast(UUID roomId, UUID voterId, String voterName, UUID votedForId, String votedForName, boolean isPublic) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "VOTE_CAST");
            payload.put("votedForId", votedForId);
            payload.put("votedForName", votedForName);
            if (isPublic) {
                payload.put("voterId", voterId);
                payload.put("voterName", voterName);
            }
            messagingTemplate.convertAndSend("/topic/room/" + roomId + "/votes", (Object) payload);
        } catch (Exception ex) {
            log.error("Failed to publish VOTE_CAST: room={}", roomId, ex);
        }
    }

    @Async("webSocketTaskExecutor")
    public void publishVoteRemoved(UUID roomId, UUID voterId, String voterName, UUID votedForId, String votedForName, boolean isPublic) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "VOTE_REMOVED");
            payload.put("votedForId", votedForId);
            payload.put("votedForName", votedForName);
            if (isPublic) {
                payload.put("voterId", voterId);
                payload.put("voterName", voterName);
            }
            messagingTemplate.convertAndSend("/topic/room/" + roomId + "/votes", (Object) payload);
        } catch (Exception ex) {
            log.error("Failed to publish VOTE_REMOVED: room={}", roomId, ex);
        }
    }

    @Async("webSocketTaskExecutor")
    public void publishVoteState(UUID roomId, UUID questionId, List<Map<String, Object>> voteState, com.verdikt.verdikt_backend.model.enums.VoteMode voteMode) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "VOTE_STATE");
            payload.put("questionId", questionId);
            payload.put("voteMode", voteMode.name());
            payload.put("votes", voteState);
            messagingTemplate.convertAndSend("/topic/room/" + roomId + "/votes", (Object) payload);
        } catch (Exception ex) {
            log.error("Failed to publish VOTE_STATE: room={} question={}", roomId, questionId, ex);
        }
    }

    @Async("webSocketTaskExecutor")
    public void publishQuestionAdvanced(UUID roomId, Object questionResponse) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "QUESTION_ADVANCED");
            payload.put("question", questionResponse);
            messagingTemplate.convertAndSend("/topic/room/" + roomId + "/game", (Object) payload);
        } catch (Exception ex) {
            log.error("Failed to publish QUESTION_ADVANCED: room={}", roomId, ex);
        }
    }

    @Async("webSocketTaskExecutor")
    public void publishGameStarted(UUID roomId) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "GAME_STARTED");
            messagingTemplate.convertAndSend("/topic/room/" + roomId + "/game", (Object) payload);
        } catch (Exception ex) {
            log.error("Failed to publish GAME_STARTED: room={}", roomId, ex);
        }
    }

    @Async("webSocketTaskExecutor")
    public void publishGameEnded(UUID roomId) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "GAME_ENDED");
            messagingTemplate.convertAndSend("/topic/room/" + roomId + "/game", (Object) payload);
        } catch (Exception ex) {
            log.error("Failed to publish GAME_ENDED: room={}", roomId, ex);
        }
    }

    @Async("webSocketTaskExecutor")
    public void publishPlayerJoined(UUID roomId, UUID playerId, String playerName) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "PLAYER_JOINED");
            payload.put("playerId", playerId);
            payload.put("playerName", playerName);
            messagingTemplate.convertAndSend("/topic/room/" + roomId + "/players", (Object) payload);
        } catch (Exception ex) {
            log.error("Failed to publish PLAYER_JOINED: room={}", roomId, ex);
        }
    }

    @Async("webSocketTaskExecutor")
    public void publishPlayerStatusChanged(UUID roomId, UUID playerId, boolean isActive) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "PLAYER_STATUS_CHANGED");
            payload.put("playerId", playerId);
            payload.put("isActive", isActive);
            messagingTemplate.convertAndSend("/topic/room/" + roomId + "/players", (Object) payload);
        } catch (Exception ex) {
            log.error("Failed to publish PLAYER_STATUS_CHANGED: room={}", roomId, ex);
        }
    }
}
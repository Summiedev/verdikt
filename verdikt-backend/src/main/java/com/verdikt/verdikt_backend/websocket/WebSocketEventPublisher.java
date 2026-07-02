package com.verdikt.verdikt_backend.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class WebSocketEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

   public void publishVoteCast(UUID roomId, UUID voterId, String voterName, UUID votedForId, String votedForName, boolean isPublic) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("type", "VOTE_CAST");
    payload.put("votedForId", votedForId);
    payload.put("votedForName", votedForName);
    if (isPublic) {
        payload.put("voterId", voterId);
        payload.put("voterName", voterName);
    }
    messagingTemplate.convertAndSend("/topic/room/" + roomId + "/votes", (Object) payload);
}

public void publishVoteRemoved(UUID roomId, UUID voterId, String voterName, UUID votedForId, String votedForName, boolean isPublic) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("type", "VOTE_REMOVED");
    payload.put("votedForId", votedForId);
    payload.put("votedForName", votedForName);
    if (isPublic) {
        payload.put("voterId", voterId);
        payload.put("voterName", voterName);
    }
    messagingTemplate.convertAndSend("/topic/room/" + roomId + "/votes", (Object) payload);
}

public void publishVoteState(UUID roomId, UUID questionId, List<Map<String, Object>> voteState, com.verdikt.verdikt_backend.model.enums.VoteMode voteMode) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("type", "VOTE_STATE");
    payload.put("questionId", questionId);
    payload.put("voteMode", voteMode.name());
    payload.put("votes", voteState);
    messagingTemplate.convertAndSend("/topic/room/" + roomId + "/votes", (Object) payload);
}
public void publishQuestionAdvanced(UUID roomId, Object questionResponse) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("type", "QUESTION_ADVANCED");
    payload.put("question", questionResponse);

    messagingTemplate.convertAndSend("/topic/room/" + roomId + "/game", (Object) payload);
}
    public void publishGameStarted(UUID roomId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "GAME_STARTED");

        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/game", (Object) payload);
    }

    public void publishGameEnded(UUID roomId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "GAME_ENDED");

        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/game", (Object) payload);
    }

    public void publishPlayerJoined(UUID roomId, UUID playerId, String playerName) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "PLAYER_JOINED");
        payload.put("playerId", playerId);
        payload.put("playerName", playerName);

        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/players", (Object) payload);
    }

    public void publishPlayerStatusChanged(UUID roomId, UUID playerId, boolean isActive) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "PLAYER_STATUS_CHANGED");
        payload.put("playerId", playerId);
        payload.put("isActive", isActive);

        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/players", (Object) payload);
    }
}
package com.verdikt.verdikt_backend.dto.response;

import com.verdikt.verdikt_backend.model.enums.RoomStatus;
import com.verdikt.verdikt_backend.model.enums.VoteMode;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class RoomResponse {
    private UUID id;
    private String code;
    private String name;
    private RoomStatus status;
    private VoteMode voteMode;
    private int maxPlayers;
    private Integer maxQuestions;
    private Integer questionDurationSeconds; // null = no timer
    private List<PlayerResponse> players;
    private UUID playerToken;
}

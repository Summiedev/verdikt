package com.verdikt.verdikt_backend.dto.response.reportcard;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class PlayerTitleCardResponse {
    private UUID playerId;
    private String playerName;
    private List<String> titlesWon; // e.g ["Most Likely To Japa", "Biggest Heartbreaker"]
    private long totalVotesReceived;
}
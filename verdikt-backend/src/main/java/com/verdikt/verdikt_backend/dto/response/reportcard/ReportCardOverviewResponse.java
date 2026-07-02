package com.verdikt.verdikt_backend.dto.response.reportcard;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ReportCardOverviewResponse {
    private String roomName;
    private int totalPlayers;
    private int totalQuestions;
    private List<LeaderboardEntry> leaderboard; // ranked by total votes received
    private String verdict; // "THE GC HAS SPOKEN 🔥"

    @Data
    @Builder
    public static class LeaderboardEntry {
        private String playerName;
        private long totalVotesReceived;
        private String topTitle; // the question they won the most e.g "Most Likely To Japa"
    }
}
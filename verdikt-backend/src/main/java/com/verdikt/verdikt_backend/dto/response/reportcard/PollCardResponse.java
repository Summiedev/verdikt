package com.verdikt.verdikt_backend.dto.response.reportcard;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class PollCardResponse {

    private UUID questionId;
    private String questionText;
    private int orderIndex; // which slide number this is

    private List<PollResultEntry> results; // each person voted for, ranked by vote count

    @Data
    @Builder
    public static class PollResultEntry {
        private UUID playerId;
        private String playerName;
        private long voteCount;

        @JsonProperty("isWinner")
        private boolean isWinner; // most voted for this question

        // null if room is in ANONYMOUS mode — only shown in PUBLIC mode
        private List<String> votedByNames;
    }
}
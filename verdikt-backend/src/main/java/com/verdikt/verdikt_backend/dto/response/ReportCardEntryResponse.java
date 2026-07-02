package com.verdikt.verdikt_backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class ReportCardEntryResponse {
    private UUID playerId;
    private String playerName;
    private String questionText;  // the title they won e.g "Most Likely To Japa"
    private long voteCount;
}
package com.verdikt.verdikt_backend.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class CastVoteRequest {

    @NotNull(message = "Question ID is required")
    private UUID questionId;

    // list because WhatsApp style — you can vote for multiple people
    @NotNull(message = "You must vote for at least one person")
    @Size(max = 25, message = "You can select at most 25 players")
    private List<UUID> votedForPlayerIds;
}

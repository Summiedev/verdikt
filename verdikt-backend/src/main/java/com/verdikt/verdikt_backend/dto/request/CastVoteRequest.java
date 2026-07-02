package com.verdikt.verdikt_backend.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class CastVoteRequest {

    @NotNull(message = "Question ID is required")
    private UUID questionId;

    // list because WhatsApp style — you can vote for multiple people
    @NotNull(message = "You must vote for at least one person")
    private List<UUID> votedForPlayerIds;
}
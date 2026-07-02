package com.verdikt.verdikt_backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class PlayerResponse {
    private UUID id;
    private UUID token;
    private String name;

    @JsonProperty("isHost")
    private boolean isHost;

    @JsonProperty("isActive")
    private boolean isActive;
}
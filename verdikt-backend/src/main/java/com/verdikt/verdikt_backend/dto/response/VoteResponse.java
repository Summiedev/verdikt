package com.verdikt.verdikt_backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class VoteResponse {
    private UUID voterId;
    private String voterName;      // null if anonymous mode
    private UUID votedForId;
    private String votedForName;
    private UUID questionId;
}
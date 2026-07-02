package com.verdikt.verdikt_backend.dto.response;

import com.verdikt.verdikt_backend.model.enums.SpiceLevel;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class QuestionResponse {
    private UUID id;
    private String text;
    private String category;
    private SpiceLevel spiceLevel;
    private List<VoteResponse> votes; // populated after question is answered
}
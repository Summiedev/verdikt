package com.verdikt.verdikt_backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class QuestionPreviewResponse {
    private UUID id;
    private String text;
    private String spiceLevel;
}
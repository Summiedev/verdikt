package com.verdikt.verdikt_backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

import java.time.Instant;

@Data
@Builder
public class CurrentQuestionResponse {
    private UUID questionId;
    private String text;
    private int questionIndex;
    private int totalQuestions;
    private Instant startedAt;   
}
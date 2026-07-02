package com.verdikt.verdikt_backend.dto.request;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class StartGameRequest {

    // existing question IDs the host kept from the preview batch
    private List<UUID> selectedQuestionIds;

    // brand new questions the host typed themselves
    private List<String> customQuestionTexts;
}
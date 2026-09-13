package com.verdikt.verdikt_backend.dto.request;

import lombok.Data;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@Data
public class StartGameRequest {

    // existing question IDs the host kept from the preview batch
    @Size(max = 50, message = "Too many selected questions")
    private List<UUID> selectedQuestionIds;

    // brand new questions the host typed themselves
    @Size(max = 50, message = "Too many custom questions")
    private List<@Size(min = 5, max = 120, message = "Questions must be between 5 and 120 characters") String> customQuestionTexts;
}

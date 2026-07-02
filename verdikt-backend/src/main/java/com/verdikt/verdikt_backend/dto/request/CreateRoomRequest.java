package com.verdikt.verdikt_backend.dto.request;

import com.verdikt.verdikt_backend.model.enums.VoteMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateRoomRequest {

    @NotBlank(message = "Room name is required")
    @Size(min = 2, max = 30, message = "Room name must be between 2 and 30 characters")
    private String name;

    @NotBlank(message = "Host name is required")
    @Size(min = 2, max = 30, message = "Your name must be between 2 and 30 characters")
    private String hostName;

    @NotNull(message = "Vote mode is required")
    private VoteMode voteMode;

    // optional — null means no timer, host manually advances each question
    @Min(value = 10, message = "Timer must be at least 10 seconds")
    @Max(value = 300, message = "Timer can't exceed 5 minutes")
    private Integer questionDurationSeconds;
}
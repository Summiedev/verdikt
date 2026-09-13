package com.verdikt.verdikt_backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class JoinRoomRequest {

    @NotBlank(message = "Room code is required")
    @Size(min = 6, max = 6, message = "Room code must be 6 characters")
    @Pattern(regexp = "[A-Za-z0-9]{6}", message = "Room code must contain six letters or numbers")
    private String code;

    @NotBlank(message = "Your name is required")
    @Size(min = 2, max = 30, message = "Name must be between 2 and 30 characters")
    private String playerName;
}

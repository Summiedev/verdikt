package com.verdikt.verdikt_backend.controller;

import com.verdikt.verdikt_backend.dto.response.RoomResponse;
import com.verdikt.verdikt_backend.model.Player;
import com.verdikt.verdikt_backend.service.PlayerService;
import com.verdikt.verdikt_backend.service.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/players")
@RequiredArgsConstructor
public class PlayerController {

    private final PlayerService playerService;
    private final RoomService roomService;

    @PostMapping("/leave")
    public ResponseEntity<Void> leaveRoom(@RequestHeader("X-Player-Token") UUID playerToken) {
        playerService.leaveRoom(playerToken);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<RoomResponse> refreshToken(@RequestHeader("X-Player-Token") UUID playerToken) {
        Player player = playerService.refreshToken(playerToken);
        RoomResponse response = roomService.rejoinRoom(player.getToken());
        return ResponseEntity.ok(response);
    }
}
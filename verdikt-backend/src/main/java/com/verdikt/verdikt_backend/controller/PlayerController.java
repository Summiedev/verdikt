package com.verdikt.verdikt_backend.controller;

import com.verdikt.verdikt_backend.service.PlayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/players")
@RequiredArgsConstructor
public class PlayerController {

    private final PlayerService playerService;

    @PostMapping("/leave")
    public ResponseEntity<Void> leaveRoom(@RequestHeader("X-Player-Token") UUID playerToken) {
        playerService.leaveRoom(playerToken);
        return ResponseEntity.ok().build();
    }
}
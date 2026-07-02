package com.verdikt.verdikt_backend.controller;

import com.verdikt.verdikt_backend.dto.request.CreateRoomRequest;
import com.verdikt.verdikt_backend.dto.request.JoinRoomRequest;
import com.verdikt.verdikt_backend.dto.response.RoomResponse;
import com.verdikt.verdikt_backend.service.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @PostMapping
    public ResponseEntity<RoomResponse> createRoom(@Valid @RequestBody CreateRoomRequest request) {
        RoomResponse response = roomService.createRoom(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/join")
    public ResponseEntity<RoomResponse> joinRoom(@Valid @RequestBody JoinRoomRequest request) {
        RoomResponse response = roomService.joinRoom(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/rejoin")
public ResponseEntity<RoomResponse> rejoinRoom(@RequestHeader("X-Player-Token") UUID playerToken) {
    RoomResponse response = roomService.rejoinRoom(playerToken);
    return ResponseEntity.ok()
            .cacheControl(org.springframework.http.CacheControl.noStore())
            .body(response);
}
}
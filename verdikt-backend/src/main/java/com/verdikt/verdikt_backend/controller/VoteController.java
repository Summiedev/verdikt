package com.verdikt.verdikt_backend.controller;

import com.verdikt.verdikt_backend.dto.request.CastVoteRequest;
import com.verdikt.verdikt_backend.service.VoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/rooms/{roomId}/votes")
@RequiredArgsConstructor
public class VoteController {

    private final VoteService voteService;

    @PostMapping
    public ResponseEntity<List<Map<String, Object>>> castVotes(
            @PathVariable UUID roomId,
            @RequestHeader("X-Player-Token") UUID playerToken,
            @Valid @RequestBody CastVoteRequest request
    ) {
        return ResponseEntity.ok(voteService.castVotes(roomId, playerToken, request));
    }

    @DeleteMapping
    public ResponseEntity<List<Map<String, Object>>> removeVote(
            @PathVariable UUID roomId,
            @RequestHeader("X-Player-Token") UUID playerToken,
            @RequestBody CastVoteRequest request
    ) {
        return ResponseEntity.ok(voteService.removeVote(roomId, playerToken, request));
    }

    @GetMapping("/current")
    public ResponseEntity<List<Map<String, String>>> getCurrentVotes(
            @PathVariable UUID roomId,
            @RequestHeader("X-Player-Token") UUID playerToken
    ) {
        return ResponseEntity.ok(voteService.getCurrentVoteState(roomId, playerToken));
    }
}

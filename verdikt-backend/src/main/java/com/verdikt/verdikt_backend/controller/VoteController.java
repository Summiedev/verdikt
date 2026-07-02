package com.verdikt.verdikt_backend.controller;

import com.verdikt.verdikt_backend.dto.request.CastVoteRequest;
import com.verdikt.verdikt_backend.model.Room;
import com.verdikt.verdikt_backend.model.RoomQuestion;
import com.verdikt.verdikt_backend.model.Vote;
import com.verdikt.verdikt_backend.repository.RoomQuestionRepository;
import com.verdikt.verdikt_backend.repository.RoomRepository;
import com.verdikt.verdikt_backend.repository.VoteRepository;
import com.verdikt.verdikt_backend.service.VoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/rooms/{roomId}/votes")
@RequiredArgsConstructor
public class VoteController {

    private final VoteService voteService;
    private final RoomRepository roomRepository;
    private final RoomQuestionRepository roomQuestionRepository;
    private final VoteRepository voteRepository;

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
    Room room = roomRepository.findById(roomId).orElseThrow();
    RoomQuestion active = roomQuestionRepository.findByRoomIdAndIsActiveTrue(roomId).orElse(null);
    if (active == null) return ResponseEntity.ok(List.of());

    boolean isPublic = room.getVoteMode() == com.verdikt.verdikt_backend.model.enums.VoteMode.PUBLIC;

    List<Vote> votes = voteRepository.findAllByRoomIdAndQuestionId(roomId, active.getQuestion().getId());

    List<Map<String, String>> result = votes.stream().map(v -> {
        Map<String, String> m = new HashMap<>();
        m.put("votedForId", v.getVotedFor().getId().toString());
        if (isPublic) {
            m.put("voterId", v.getVoter().getId().toString());
            m.put("voterName", v.getVoter().getName());
        }
        return m;
    }).collect(Collectors.toList());

    return ResponseEntity.ok(result);
}
}
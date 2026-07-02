package com.verdikt.verdikt_backend.controller;

import com.verdikt.verdikt_backend.dto.request.StartGameRequest;
import com.verdikt.verdikt_backend.dto.response.CurrentQuestionResponse;
import com.verdikt.verdikt_backend.dto.response.QuestionPreviewResponse;
import com.verdikt.verdikt_backend.service.GameService;
import com.verdikt.verdikt_backend.service.PlayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/rooms/{roomId}/game")
@RequiredArgsConstructor
public class GameController {

    private final GameService gameService;
    private final PlayerService playerService;

    @PostMapping("/start")
    public ResponseEntity<Void> startGame(
            @PathVariable UUID roomId,
            @RequestHeader("X-Player-Token") UUID playerToken,
            @RequestBody(required = false) StartGameRequest request
    ) {
        gameService.startGame(roomId, playerToken, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/next-question")
    public ResponseEntity<CurrentQuestionResponse> advanceQuestion(
            @PathVariable UUID roomId,
            @RequestHeader("X-Player-Token") UUID playerToken
    ) {
        CurrentQuestionResponse next = gameService.advanceToNextQuestion(roomId, playerToken);
        if (next == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(next);
    }

    @GetMapping("/current-question")
public ResponseEntity<CurrentQuestionResponse> getCurrentQuestion(@PathVariable UUID roomId) {
    return ResponseEntity.ok()
            .cacheControl(org.springframework.http.CacheControl.noStore())
            .body(gameService.getCurrentQuestion(roomId));
}
@PostMapping("/end")
public ResponseEntity<Void> endGame(
        @PathVariable UUID roomId,
        @RequestHeader("X-Player-Token") UUID playerToken
) {
    gameService.endGameEarly(roomId, playerToken);
    return ResponseEntity.ok().build();
}
    @GetMapping("/preview-questions")
public ResponseEntity<List<QuestionPreviewResponse>> previewQuestions(
        @PathVariable UUID roomId,
        @RequestParam(defaultValue = "15") int count
) {
    return ResponseEntity.ok()
            .cacheControl(org.springframework.http.CacheControl.noStore())
            .body(gameService.previewRandomQuestions(count));
}
}
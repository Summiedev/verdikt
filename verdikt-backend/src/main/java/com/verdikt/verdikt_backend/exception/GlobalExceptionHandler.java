package com.verdikt.verdikt_backend.exception;

import com.verdikt.verdikt_backend.dto.response.ApiErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(RoomNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleRoomNotFound(RoomNotFoundException ex) {
        return respond(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(RoomExpiredException.class)
    public ResponseEntity<ApiErrorResponse> handleRoomExpired(RoomExpiredException ex) {
        return respond(HttpStatus.GONE, "ROOM_EXPIRED", ex.getMessage());
    }

    @ExceptionHandler(RoomFullException.class)
    public ResponseEntity<ApiErrorResponse> handleRoomFull(RoomFullException ex) {
        return respond(HttpStatus.CONFLICT, "ROOM_FULL", ex.getMessage());
    }

    @ExceptionHandler(DuplicateNameException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateName(DuplicateNameException ex) {
        return respond(HttpStatus.CONFLICT, "DUPLICATE_NAME", ex.getMessage());
    }

    @ExceptionHandler(InvalidRoomStateException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidState(InvalidRoomStateException ex) {
        HttpStatus status = switch (ex.getCode()) {
            case "NOT_HOST" -> HttpStatus.FORBIDDEN;
            case "ROOM_ALREADY_STARTED", "GAME_ALREADY_ENDED", "QUESTION_NOT_ACTIVE" -> HttpStatus.CONFLICT;
            case "INVALID_VOTE" -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.BAD_REQUEST;
        };
        return respond(status, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(PlayerNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handlePlayerNotFound(PlayerNotFoundException ex) {
        return respond(HttpStatus.UNAUTHORIZED, "INVALID_PLAYER_TOKEN", ex.getMessage());
    }

    @ExceptionHandler(TokenExpiredException.class)
    public ResponseEntity<ApiErrorResponse> handleTokenExpired(TokenExpiredException ex) {
        return respond(HttpStatus.UNAUTHORIZED, "INVALID_PLAYER_TOKEN", ex.getMessage());
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidToken(InvalidTokenException ex) {
        return respond(HttpStatus.UNAUTHORIZED, "INVALID_PLAYER_TOKEN", ex.getMessage());
    }

    @ExceptionHandler(NoQuestionsAvailableException.class)
    public ResponseEntity<ApiErrorResponse> handleNoQuestionsAvailable(NoQuestionsAvailableException ex) {
        return respond(HttpStatus.NOT_FOUND, "NO_QUESTIONS_AVAILABLE", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Invalid request.");
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String code = "playerToken".equals(ex.getName()) ? "INVALID_PLAYER_TOKEN" : "INVALID_REQUEST";
        return respond(HttpStatus.BAD_REQUEST, code, "The request contains an invalid value.");
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        String code = "X-Player-Token".equalsIgnoreCase(ex.getHeaderName())
                ? "INVALID_PLAYER_TOKEN" : "INVALID_REQUEST";
        return respond(HttpStatus.UNAUTHORIZED, code, "A valid player session is required.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return respond(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The request body is invalid.");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "The request contains an invalid value.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return respond(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Database constraint violation", ex);
        return respond(HttpStatus.CONFLICT, "DATA_INTEGRITY_VIOLATION", "That change conflicts with current room data.");
    }

    @ExceptionHandler(TransactionSystemException.class)
    public ResponseEntity<ApiErrorResponse> handleTransaction(TransactionSystemException ex) {
        log.error("Transaction failed", ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "TRANSACTION_FAILED", "The change could not be saved. Please try again.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResourceFound(NoResourceFoundException ex) {
        return respond(HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested resource was not found.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Something went wrong. Please try again.");
    }

    private ResponseEntity<ApiErrorResponse> respond(HttpStatus status, String code, String message) {
        ApiErrorResponse body = ApiErrorResponse.builder()
                .code(code)
                .message(message == null || message.isBlank() ? "Request failed." : message)
                .requestId(MDC.get("requestId"))
                .build();
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CACHE_CONTROL, "no-store");
        return ResponseEntity.status(status).headers(headers).body(body);
    }
}

package com.verdikt.verdikt_backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(RoomNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleRoomNotFound(RoomNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(RoomExpiredException.class)
public ResponseEntity<Map<String, Object>> handleRoomExpired(RoomExpiredException ex) {
    ex.printStackTrace();
    return buildResponse(HttpStatus.GONE, "ROOM_EXPIRED", ex.getMessage());
}

    @ExceptionHandler(RoomFullException.class)
    public ResponseEntity<Map<String, Object>> handleRoomFull(RoomFullException ex) {
        return buildResponse(HttpStatus.CONFLICT, "ROOM_FULL", ex.getMessage());
    }

    @ExceptionHandler(DuplicateNameException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateName(DuplicateNameException ex) {
        return buildResponse(HttpStatus.CONFLICT, "DUPLICATE_NAME", ex.getMessage());
    }

    @ExceptionHandler(InvalidRoomStateException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidState(InvalidRoomStateException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "INVALID_ROOM_STATE", ex.getMessage());
    }

    @ExceptionHandler(PlayerNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handlePlayerNotFound(PlayerNotFoundException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "PLAYER_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .orElse("Invalid request");
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        log.error("Illegal argument exception", ex);
        return buildResponse(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.error("Database constraint violation", ex);
        return buildResponse(HttpStatus.CONFLICT, "DATA_INTEGRITY_VIOLATION",
                "Database constraint violation. Check server logs for the exact column or foreign key.");
    }

    @ExceptionHandler(TransactionSystemException.class)
    public ResponseEntity<Map<String, Object>> handleTransaction(TransactionSystemException ex) {
        log.error("Transaction failed", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "TRANSACTION_FAILED",
                "Transaction failed. Check server logs for the exact cause.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Something went wrong. Please try again.");
    }
    

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String code, String message) {
    Map<String, Object> body = new HashMap<>();
    body.put("timestamp", LocalDateTime.now());
    body.put("status", status.value());
    body.put("errorCode", code);
    body.put("message", message);
    return ResponseEntity.status(status)
            .cacheControl(org.springframework.http.CacheControl.noStore())
            .body(body);
}
}

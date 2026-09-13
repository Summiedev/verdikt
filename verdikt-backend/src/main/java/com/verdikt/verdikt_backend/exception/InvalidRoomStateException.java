package com.verdikt.verdikt_backend.exception;

public class InvalidRoomStateException extends RuntimeException {
    private final String code;

    public InvalidRoomStateException(String message) {
        this("INVALID_ROOM_STATE", message);
    }

    public InvalidRoomStateException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}

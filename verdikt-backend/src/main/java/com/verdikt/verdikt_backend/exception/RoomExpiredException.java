package com.verdikt.verdikt_backend.exception;

public class RoomExpiredException extends RuntimeException {
    public RoomExpiredException(String message) {
        super(message);
    }
}
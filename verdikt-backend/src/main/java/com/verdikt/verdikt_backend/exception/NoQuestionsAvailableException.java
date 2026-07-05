package com.verdikt.verdikt_backend.exception;

public class NoQuestionsAvailableException extends RuntimeException {
    public NoQuestionsAvailableException(String message) {
        super(message);
    }
}

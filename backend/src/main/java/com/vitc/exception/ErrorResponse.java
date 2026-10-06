package com.vitc.exception;

import java.time.LocalDateTime;
import java.util.Map;

public record ErrorResponse(
        boolean success,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors,
        LocalDateTime timestamp) {

    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(false, status, error, message, path, null, LocalDateTime.now());
    }

    public static ErrorResponse of(int status, String error, String message, String path, Map<String, String> fieldErrors) {
        return new ErrorResponse(false, status, error, message, path, fieldErrors, LocalDateTime.now());
    }
}

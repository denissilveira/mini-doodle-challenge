package com.doodle.mini.shared.api;

import java.time.Instant;
import java.util.Map;

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        Map<String, String> violations) {

    public static ApiErrorResponse of(
            int status,
            String code,
            String message,
            String path) {

        return new ApiErrorResponse(
                Instant.now(),
                status,
                code,
                message,
                path,
                Map.of());
    }

    public static ApiErrorResponse withViolations(
            int status,
            String code,
            String message,
            String path,
            Map<String, String> violations) {

        return new ApiErrorResponse(
                Instant.now(),
                status,
                code,
                message,
                path,
                violations);
    }
}


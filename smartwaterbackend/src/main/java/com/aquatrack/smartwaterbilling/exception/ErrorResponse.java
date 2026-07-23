package com.aquatrack.smartwaterbilling.exception;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Standardised error response returned by {@link GlobalExceptionHandler}
 * for all API errors. The {@code fieldErrors} list is populated only
 * for validation failures (HTTP 400).
 */
@Getter
@Builder
public class ErrorResponse {

    private final LocalDateTime timestamp;
    private final int status;
    private final String error;
    private final String message;
    private final String path;

    /** Non-null only for @Valid / bean validation failures. */
    private final List<FieldError> fieldErrors;

    @Getter
    @Builder
    public static class FieldError {
        private final String field;
        private final Object rejectedValue;
        private final String message;
    }
}

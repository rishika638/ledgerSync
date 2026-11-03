package com.ledgersync.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String errorCode,
        String message,
        int status,
        Instant timestamp,
        String correlationId,
        Map<String, String> validationErrors
) {
    public static ErrorResponse of(String errorCode, String message, int status, String correlationId) {
        return new ErrorResponse(errorCode, message, status, Instant.now(), correlationId, null);
    }

    public static ErrorResponse of(String errorCode, String message, int status, String correlationId, Map<String, String> validationErrors) {
        return new ErrorResponse(errorCode, message, status, Instant.now(), correlationId, validationErrors);
    }
}

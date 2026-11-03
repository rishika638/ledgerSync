package com.ledgersync.common.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String CORRELATION_ID_KEY = "correlationId";

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex) {
        String correlationId = MDC.get(CORRELATION_ID_KEY);
        log.warn("Business exception occurred [correlationId={}]: {} - {}",
                correlationId, ex.getErrorCode(), ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                ex.getErrorCode(),
                ex.getMessage(),
                ex.getStatus().value(),
                correlationId
        );
        return ResponseEntity.status(ex.getStatus()).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        String correlationId = MDC.get(CORRELATION_ID_KEY);
        Map<String, String> errors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        log.warn("Validation failure [correlationId={}]: {}", correlationId, errors);
        ErrorResponse response = ErrorResponse.of(
                "VALIDATION_FAILED",
                "Request validation failed",
                HttpStatus.BAD_REQUEST.value(),
                correlationId,
                errors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        String correlationId = MDC.get(CORRELATION_ID_KEY);
        log.warn("Database constraint violation [correlationId={}]: {}", correlationId, ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                "DATA_INTEGRITY_VIOLATION",
                "A database constraint was violated. If this was a concurrent duplicate event, it was safely rejected.",
                HttpStatus.CONFLICT.value(),
                correlationId
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        String correlationId = MDC.get(CORRELATION_ID_KEY);
        log.warn("Illegal argument error [correlationId={}]: {}", correlationId, ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                "INVALID_REQUEST",
                ex.getMessage(),
                HttpStatus.BAD_REQUEST.value(),
                correlationId
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateException(IllegalStateException ex) {
        String correlationId = MDC.get(CORRELATION_ID_KEY);
        log.warn("Illegal state error [correlationId={}]: {}", correlationId, ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                "ILLEGAL_STATE",
                ex.getMessage(),
                HttpStatus.CONFLICT.value(),
                correlationId
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        String correlationId = MDC.get(CORRELATION_ID_KEY);
        log.error("Unhandled internal server error [correlationId={}]", correlationId, ex);

        ErrorResponse response = ErrorResponse.of(
                "INTERNAL_SERVER_ERROR",
                "An unexpected internal error occurred. Please contact system support.",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                correlationId
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}

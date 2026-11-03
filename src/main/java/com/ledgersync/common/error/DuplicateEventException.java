package com.ledgersync.common.error;

import org.springframework.http.HttpStatus;

public class DuplicateEventException extends BusinessException {
    public DuplicateEventException(String message) {
        super(message, HttpStatus.CONFLICT, "DUPLICATE_EVENT");
    }
}

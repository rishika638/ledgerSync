package com.ledgersync.common.error;

import org.springframework.http.HttpStatus;

public class InvariantViolationException extends BusinessException {
    public InvariantViolationException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, "INVARIANT_VIOLATION");
    }
}

package com.ledgersync.common.error;

import org.springframework.http.HttpStatus;

public class InvalidStateTransitionException extends BusinessException {
    public InvalidStateTransitionException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_STATE_TRANSITION");
    }
}

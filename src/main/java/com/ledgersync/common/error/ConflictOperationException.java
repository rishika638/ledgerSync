package com.ledgersync.common.error;

import org.springframework.http.HttpStatus;

public class ConflictOperationException extends BusinessException {
    public ConflictOperationException(String message) {
        super(message, HttpStatus.CONFLICT, "OPERATION_CONFLICT");
    }
}

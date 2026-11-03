package com.ledgersync.common.error;

import org.springframework.http.HttpStatus;

public class InvalidSignatureException extends BusinessException {
    public InvalidSignatureException(String message) {
        super(message, HttpStatus.UNAUTHORIZED, "INVALID_WEBHOOK_SIGNATURE");
    }
}

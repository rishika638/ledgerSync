package com.ledgersync.payment.model;

import java.util.Set;

public enum PaymentStatus {
    CREATED,
    AUTHORIZED,
    CAPTURED,
    PARTIALLY_REFUNDED,
    REFUNDED,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == REFUNDED || this == FAILED || this == CANCELLED;
    }

    public Set<PaymentStatus> getAllowedNextStates() {
        return switch (this) {
            case CREATED -> Set.of(AUTHORIZED, CAPTURED, FAILED, CANCELLED);
            case AUTHORIZED -> Set.of(CAPTURED, FAILED, CANCELLED);
            case CAPTURED -> Set.of(PARTIALLY_REFUNDED, REFUNDED);
            case PARTIALLY_REFUNDED -> Set.of(PARTIALLY_REFUNDED, REFUNDED);
            case REFUNDED, FAILED, CANCELLED -> Set.of();
        };
    }
}

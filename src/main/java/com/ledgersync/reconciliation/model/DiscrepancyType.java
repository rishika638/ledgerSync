package com.ledgersync.reconciliation.model;

public enum DiscrepancyType {
    MISSING_IN_LOCAL,
    MISSING_IN_REMOTE,
    STATUS_MISMATCH,
    AMOUNT_MISMATCH,
    UNEXPECTED_REFUND,
    STALE_UNRESOLVED
}

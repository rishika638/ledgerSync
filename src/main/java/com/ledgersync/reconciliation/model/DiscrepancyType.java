package com.ledgersync.reconciliation.model;

public enum DiscrepancyType {
    // Core 3-way reconciliation exception types
    MISSING_GATEWAY,
    MISSING_BANK,
    AMOUNT_MISMATCH,
    DUPLICATE_RECORD,
    FEE_MISMATCH,
    TAX_MISMATCH,
    PARTIAL_SETTLEMENT,
    DELAYED_SETTLEMENT,
    REFERENCE_MISMATCH,
    AMBIGUOUS_MATCH,

    // Existing webhook/provider discrepancy types (backward compatible)
    MISSING_IN_LOCAL,
    MISSING_IN_REMOTE,
    STATUS_MISMATCH,
    UNEXPECTED_REFUND,
    STALE_UNRESOLVED
}

-- V2__indexes_and_constraints.sql
-- Financial invariants, indexes, and partial queue indexes

-- Invariants on Payment Amounts
ALTER TABLE payment_transactions
    ADD CONSTRAINT chk_payment_amounts_non_negative
    CHECK (amount_cents >= 0 AND captured_amount_cents >= 0 AND refunded_amount_cents >= 0);

ALTER TABLE payment_transactions
    ADD CONSTRAINT chk_captured_lte_authorized
    CHECK (captured_amount_cents <= amount_cents);

ALTER TABLE payment_transactions
    ADD CONSTRAINT chk_refunded_lte_captured
    CHECK (refunded_amount_cents <= captured_amount_cents);

ALTER TABLE payment_transactions
    ADD CONSTRAINT chk_payment_status
    CHECK (status IN ('CREATED', 'AUTHORIZED', 'CAPTURED', 'PARTIALLY_REFUNDED', 'REFUNDED', 'FAILED', 'CANCELLED'));

-- Invariants on Refunds
ALTER TABLE refunds
    ADD CONSTRAINT chk_refund_amount_positive
    CHECK (amount_cents > 0);

ALTER TABLE refunds
    ADD CONSTRAINT chk_refund_status
    CHECK (status IN ('SUCCEEDED', 'FAILED'));

-- Invariants on Webhooks and Inbox
ALTER TABLE webhook_events
    ADD CONSTRAINT chk_webhook_status
    CHECK (status IN ('INGESTED', 'PROCESSED', 'DUPLICATE', 'FAILED'));

ALTER TABLE inbox_messages
    ADD CONSTRAINT chk_inbox_status
    CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'DEAD_LETTER'));

-- Inbox queue index for high-throughput polling (SKIP LOCKED)
CREATE INDEX idx_inbox_pending_poll
    ON inbox_messages (scheduled_at ASC, created_at ASC)
    WHERE status = 'PENDING';

CREATE INDEX idx_inbox_stuck_recovery
    ON inbox_messages (locked_at ASC)
    WHERE status = 'PROCESSING';

CREATE INDEX idx_inbox_dead_letter
    ON inbox_messages (updated_at DESC)
    WHERE status = 'DEAD_LETTER';

-- Audit and Payment Lookup Indexes
CREATE INDEX idx_payment_status_created ON payment_transactions (status, created_at DESC);
CREATE INDEX idx_payment_customer ON payment_transactions (customer_id);
CREATE INDEX idx_audit_resource ON audit_events (resource_type, resource_id, created_at DESC);
CREATE INDEX idx_audit_correlation ON audit_events (correlation_id);
CREATE INDEX idx_discrepancy_status ON reconciliation_discrepancies (status, severity);
CREATE INDEX idx_discrepancy_run ON reconciliation_discrepancies (run_id);

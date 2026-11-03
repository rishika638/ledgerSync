-- V1__init_schema.sql
-- Base tables for LedgerSync

CREATE TABLE webhook_events (
    id VARCHAR(64) PRIMARY KEY,
    provider VARCHAR(32) NOT NULL,
    provider_event_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    raw_payload TEXT NOT NULL,
    signature_header VARCHAR(512),
    ingested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(32) NOT NULL,
    CONSTRAINT uk_webhook_provider_event UNIQUE (provider, provider_event_id)
);

CREATE TABLE inbox_messages (
    id VARCHAR(64) PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL REFERENCES webhook_events(id) ON DELETE CASCADE,
    provider VARCHAR(32) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 5,
    scheduled_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_at TIMESTAMPTZ,
    locked_by VARCHAR(128),
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE outbox_messages (
    id VARCHAR(64) PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE payment_transactions (
    id VARCHAR(64) PRIMARY KEY,
    provider VARCHAR(32) NOT NULL,
    provider_payment_id VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(128) UNIQUE,
    customer_id VARCHAR(64),
    currency VARCHAR(3) NOT NULL,
    amount_cents BIGINT NOT NULL,
    captured_amount_cents BIGINT NOT NULL DEFAULT 0,
    refunded_amount_cents BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    provider_created_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_payment_provider_payment_id UNIQUE (provider, provider_payment_id)
);

CREATE TABLE refunds (
    id VARCHAR(64) PRIMARY KEY,
    payment_id VARCHAR(64) NOT NULL REFERENCES payment_transactions(id) ON DELETE RESTRICT,
    provider_refund_id VARCHAR(128) NOT NULL,
    amount_cents BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    reason VARCHAR(255),
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_refund_provider_refund_id UNIQUE (payment_id, provider_refund_id)
);

CREATE TABLE audit_events (
    id VARCHAR(64) PRIMARY KEY,
    action VARCHAR(64) NOT NULL,
    actor VARCHAR(128) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(128) NOT NULL,
    correlation_id VARCHAR(64),
    before_state TEXT,
    after_state TEXT,
    ip_address VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE reconciliation_runs (
    id VARCHAR(64) PRIMARY KEY,
    provider VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_remote_checked INT NOT NULL DEFAULT 0,
    total_local_checked INT NOT NULL DEFAULT 0,
    discrepancies_count INT NOT NULL DEFAULT 0,
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    error_message TEXT
);

CREATE TABLE reconciliation_discrepancies (
    id VARCHAR(64) PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL REFERENCES reconciliation_runs(id) ON DELETE CASCADE,
    payment_id VARCHAR(64),
    provider_payment_id VARCHAR(128),
    provider VARCHAR(32) NOT NULL,
    type VARCHAR(64) NOT NULL,
    severity VARCHAR(32) NOT NULL,
    evidence TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    resolution_notes TEXT,
    resolved_by VARCHAR(128),
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

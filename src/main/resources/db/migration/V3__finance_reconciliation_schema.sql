-- V3__finance_reconciliation_schema.sql
-- LedgerSync 2.0: Three-way reconciliation, AI investigations, resolution actions, and multi-source imports

-- 1. Import batches for Ledger, Razorpay settlements, and Bank statements
CREATE TABLE finance_import_batches (
    id VARCHAR(64) PRIMARY KEY,
    batch_name VARCHAR(128) NOT NULL,
    source_type VARCHAR(32) NOT NULL, -- LEDGER, RAZORPAY, BANK
    filename VARCHAR(255) NOT NULL,
    total_rows INT NOT NULL DEFAULT 0,
    valid_rows INT NOT NULL DEFAULT 0,
    invalid_rows INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'COMPLETED', -- PROCESSING, COMPLETED, FAILED
    error_summary TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_import_source_type CHECK (source_type IN ('LEDGER', 'RAZORPAY', 'BANK')),
    CONSTRAINT chk_import_status CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED'))
);

-- 2. Finance source records parsed from CSV imports
CREATE TABLE finance_records (
    id VARCHAR(64) PRIMARY KEY,
    batch_id VARCHAR(64) NOT NULL REFERENCES finance_import_batches(id) ON DELETE CASCADE,
    source_type VARCHAR(32) NOT NULL, -- LEDGER, RAZORPAY, BANK
    record_reference VARCHAR(128) NOT NULL,
    order_reference VARCHAR(128),
    secondary_reference VARCHAR(128), -- Bank UTR, ARN, or customer identifier
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    amount_minor BIGINT NOT NULL, -- Stored in minor currency units (cents / paise)
    fee_minor BIGINT NOT NULL DEFAULT 0,
    tax_minor BIGINT NOT NULL DEFAULT 0,
    net_amount_minor BIGINT NOT NULL DEFAULT 0,
    transaction_timestamp TIMESTAMPTZ NOT NULL,
    settlement_timestamp TIMESTAMPTZ,
    status VARCHAR(32) NOT NULL DEFAULT 'UNRECONCILED', -- UNRECONCILED, MATCHED, PARTIALLY_MATCHED, DISCREPANCY
    raw_metadata TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_record_source_type CHECK (source_type IN ('LEDGER', 'RAZORPAY', 'BANK')),
    CONSTRAINT chk_record_status CHECK (status IN ('UNRECONCILED', 'MATCHED', 'PARTIALLY_MATCHED', 'DISCREPANCY'))
);

-- 3. Extend reconciliation_runs for three-way settlement metrics
ALTER TABLE reconciliation_runs
    ADD COLUMN IF NOT EXISTS run_type VARCHAR(32) DEFAULT 'THREE_WAY_SETTLEMENT',
    ADD COLUMN IF NOT EXISTS total_ledger_records INT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS total_gateway_records INT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS total_bank_records INT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS matched_records_count INT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS unmatched_records_count INT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS precision_rate NUMERIC(5,2) DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS recall_rate NUMERIC(5,2) DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS match_rate NUMERIC(5,2) DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS unmatched_amount_minor BIGINT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS processing_duration_ms BIGINT DEFAULT 0;

-- 4. Extend reconciliation_discrepancies for 3-way financial evidence linkage
ALTER TABLE reconciliation_discrepancies
    ADD COLUMN IF NOT EXISTS amount_minor BIGINT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS expected_amount_minor BIGINT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS actual_amount_minor BIGINT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS currency VARCHAR(3) DEFAULT 'INR',
    ADD COLUMN IF NOT EXISTS ledger_record_id VARCHAR(64) REFERENCES finance_records(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS gateway_record_id VARCHAR(64) REFERENCES finance_records(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS bank_record_id VARCHAR(64) REFERENCES finance_records(id) ON DELETE SET NULL;

-- 5. Three-way reconciliation matches
CREATE TABLE reconciliation_matches (
    id VARCHAR(64) PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL REFERENCES reconciliation_runs(id) ON DELETE CASCADE,
    match_type VARCHAR(32) NOT NULL, -- EXACT_THREE_WAY, TWO_WAY_LEDGER_GATEWAY, TWO_WAY_GATEWAY_BANK, PARTIAL_SETTLEMENT, COMPOSITE
    match_status VARCHAR(32) NOT NULL DEFAULT 'AUTOMATIC_CONFIRMED', -- AUTOMATIC_CONFIRMED, AMBIGUOUS_FLAGGED, MANUALLY_VERIFIED
    ledger_record_id VARCHAR(64) REFERENCES finance_records(id) ON DELETE SET NULL,
    gateway_record_id VARCHAR(64) REFERENCES finance_records(id) ON DELETE SET NULL,
    bank_record_id VARCHAR(64) REFERENCES finance_records(id) ON DELETE SET NULL,
    ledger_amount_minor BIGINT,
    gateway_amount_minor BIGINT,
    bank_amount_minor BIGINT,
    fee_minor BIGINT DEFAULT 0,
    tax_minor BIGINT DEFAULT 0,
    confidence_score NUMERIC(5,2) NOT NULL DEFAULT 100.00,
    match_notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 6. AI Investigations powered by Google Gemini
CREATE TABLE ai_investigations (
    id VARCHAR(64) PRIMARY KEY,
    discrepancy_id VARCHAR(64) NOT NULL REFERENCES reconciliation_discrepancies(id) ON DELETE CASCADE,
    model_version VARCHAR(64) NOT NULL,
    prompt_version VARCHAR(32) NOT NULL,
    classification VARCHAR(64) NOT NULL,
    confidence_score NUMERIC(5,2) NOT NULL,
    summary TEXT NOT NULL,
    possible_causes TEXT NOT NULL, -- JSON array
    supporting_evidence TEXT NOT NULL, -- JSON object / array
    recommended_action VARCHAR(64) NOT NULL,
    action_reasoning TEXT NOT NULL,
    missing_evidence TEXT,
    requires_human_approval BOOLEAN NOT NULL DEFAULT true,
    validation_passed BOOLEAN NOT NULL DEFAULT true,
    raw_response TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 7. Human-in-the-loop resolution actions with complete audit linkage
CREATE TABLE resolution_actions (
    id VARCHAR(64) PRIMARY KEY,
    discrepancy_id VARCHAR(64) NOT NULL REFERENCES reconciliation_discrepancies(id) ON DELETE CASCADE,
    investigation_id VARCHAR(64) REFERENCES ai_investigations(id) ON DELETE SET NULL,
    action_type VARCHAR(64) NOT NULL, -- APPROVE_ADJUSTMENT, REJECT_INVESTIGATION, REQUEST_EVIDENCE, FORCE_MATCH, WRITE_OFF, INITIATE_CHARGEBACK_DISPUTE, RETRY_SETTLEMENT
    status VARCHAR(32) NOT NULL DEFAULT 'APPROVED', -- PENDING, APPROVED, REJECTED, EXECUTED, FAILED
    actor VARCHAR(128) NOT NULL,
    decision_notes TEXT,
    financial_adjustment_minor BIGINT DEFAULT 0,
    applied_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_resolution_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'EXECUTED', 'FAILED'))
);

-- Indexes for high-performance financial query patterns
CREATE INDEX idx_finance_record_lookup ON finance_records (source_type, record_reference);
CREATE INDEX idx_finance_record_order ON finance_records (order_reference);
CREATE INDEX idx_finance_record_secondary ON finance_records (secondary_reference);
CREATE INDEX idx_finance_record_batch ON finance_records (batch_id);
CREATE INDEX idx_finance_record_status ON finance_records (status);
CREATE INDEX idx_match_run_id ON reconciliation_matches (run_id);
CREATE INDEX idx_match_records ON reconciliation_matches (ledger_record_id, gateway_record_id, bank_record_id);
CREATE INDEX idx_ai_investigation_discrepancy ON ai_investigations (discrepancy_id);
CREATE INDEX idx_resolution_actions_discrepancy ON resolution_actions (discrepancy_id);
CREATE INDEX idx_discrepancy_severity_status ON reconciliation_discrepancies (severity, status);

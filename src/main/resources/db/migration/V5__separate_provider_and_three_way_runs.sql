UPDATE reconciliation_runs
SET run_type = 'PROVIDER_RECONCILIATION'
WHERE COALESCE(total_ledger_records, 0) = 0;

CREATE INDEX IF NOT EXISTS idx_reconciliation_runs_type_started
    ON reconciliation_runs (run_type, started_at DESC);

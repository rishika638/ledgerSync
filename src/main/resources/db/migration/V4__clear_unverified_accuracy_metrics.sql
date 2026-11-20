-- Precision and recall cannot be inferred from matcher output without labeled ground truth.
UPDATE reconciliation_runs
SET precision_rate = NULL,
    recall_rate = NULL
WHERE run_type = 'THREE_WAY_SETTLEMENT';

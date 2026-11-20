ALTER TABLE finance_import_batches
    ADD COLUMN content_sha256 VARCHAR(64);

CREATE UNIQUE INDEX uq_finance_import_source_content
    ON finance_import_batches (source_type, content_sha256)
    WHERE content_sha256 IS NOT NULL;

ALTER TABLE reconciliation_runs
    ADD COLUMN input_batch_ids TEXT NOT NULL DEFAULT '[]';

# CSV ingestion verification matrix

The importer accepts complete source snapshots for `LEDGER`, `RAZORPAY`, or `BANK`. It reads UTF-8 CSV records including quoted multiline fields, stores money as integer minor units, reports row-level validation errors, and identifies byte-identical repeat uploads by source and SHA-256. Duplicate rows inside one file are intentionally retained for reconciliation detection.

| Scenario | Evidence | Status | Notes |
|---|---|---|---|
| Valid LEDGER with exact minor units | `CsvImportServiceTest.importsAmountsAsExactMinorUnits` | PASS | Amount, fee, and tax are stored as integer paise. |
| Valid files for all three sources and UTF-8 BOM | `acceptsUtf8BomAndValidHeadersForAllThreeSources` | PASS | Verifies source-specific required headers. |
| Empty and header-only file | `rejectsEmptyHeaderOnlyAndWrongSourceFilesWithoutSavingRecords` | PASS | Rejected without persisting rows. |
| Wrong-source/missing required headers | Same rejection test | PASS | Missing-column diagnostics identify the required fields. |
| Sub-paise amount precision | `rejectsFractionsSmallerThanOneMinorUnitInsteadOfRounding` | PASS | Invalid rather than rounded. |
| Mixed valid and malformed amount/date rows | `keepsValidRowsAndReportsMalformedAmountsAndDatesByLine` | PASS | Valid rows remain eligible; invalid rows include physical CSV line numbers. |
| Malformed UTF-8 | `rejectsMalformedUtf8WithoutPersistingPartialRows` | PASS | File rejected before partial persistence. |
| Duplicate rows within one upload | `preservesDuplicateRowsWithinAFileForReconciliationDetection` | PASS | Duplicates remain visible to the matcher. |
| Quoted multiline fields and escaped quotes | `parsesQuotedMultilineFieldsAndEscapedQuotes` | PASS | Parser handles valid CSV quoting across physical lines. |
| Exact repeat upload | `repeatedIdenticalUploadReturnsOriginalBatchWithoutDuplicatingRecords` | PASS | Returns the original batch and does not insert duplicate records. |
| Wrong column count and invalid currency | `reportsColumnCountAndInvalidCurrencyPerPhysicalDataRow` | PASS | Row-level diagnostics are retained. |
| Database-backed commit/rollback on PostgreSQL | No disposable PostgreSQL test run | NOT VERIFIED | Current unit test repository behavior is mocked; existing DB was not modified. |
| Concurrent exact repeat upload | No concurrent import test | NOT VERIFIED | Unique index rejects a racing duplicate safely, but response behavior was not verified against PostgreSQL. |
| Alternate source-export dialects/non-UTF-8 encodings | No authorized real samples | NOT VERIFIED | UTF-8 is the supported encoding. |

## Reproduce

```powershell
.\mvnw.cmd '-Dtest=CsvImportServiceTest' test
```

Latest focused result: **10 tests passed, 0 failures/errors**. The full backend suite passed **53 tests** on 2026-11-21.

## Operational contract and manual checks

- One upload is treated as a complete snapshot for its selected source. Upload all rows for each of the three sources before reconciling; multiple partial batches are not combined.
- Identical file bytes for the same source are idempotent. Reordered or reformatted CSV bytes are treated as a distinct upload.
- Use a dedicated demo database to verify actual PostgreSQL row persistence, invalid-row batch history, transaction rollback, and provider-specific authorized exports. Do not reimport or reset persistent/shared data.

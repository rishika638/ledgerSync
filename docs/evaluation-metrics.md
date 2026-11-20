# Ground-truth evaluation

## Methodology and limits

`scripts/evaluate-ground-truth.ps1` authenticates and issues read-only API requests. It uses the latest three-way reconciliation run, requires the run's persisted three-source input-batch snapshot, and limits ledger cases to that run's LEDGER batch. It then compares each persisted match/exception classification with `demo-data/ground_truth.json`, which the matcher itself never reads.

The evaluator emits total cases, accuracy, per-category one-vs-rest TP/FP/FN/TN, precision, recall, F1, and the false-positive/false-negative case list. It refuses to report if the run has no three-batch snapshot, if duplicate ledger references make the case mapping ambiguous, or if any API result is truncated at the configured 500-record limit.

The included dataset is synthetic and in-sample. Generator category classes are assigned by fixed index ranges. A second seed/rendering of the same generator would reuse the same scenario template and would not be a genuinely independent held-out distribution. No held-out or production benchmark is currently available; no generalization claim is made.

## Historical result (pre batch-snapshot implementation)

The latest previously evaluated local run was `rec_1a1f5e8e229244960369ede1250`. It classified 165/165 synthetic cases correctly:

| Category | Cases |
|---|---:|
| Clean three-way match | 110 |
| Missing gateway | 8 |
| Missing bank | 8 |
| Amount mismatch | 7 |
| Duplicate record | 6 |
| Fee mismatch | 6 |
| Partial settlement | 6 |
| Delayed settlement | 6 |
| Reference mismatch | 5 |
| Ambiguous match | 3 |
| **Total** | **165** |

The run persisted 116 match rows and 55 exception rows. Six delayed-settlement cases have both a valid amount/reference match and a delay exception, so match-row count is not the number of clean cases. Clean-match detection was TP=110, FP=0, FN=0, TN=55, giving precision/recall/F1 of 1.0 on this sample only.

**This is historical evidence, not a result for the current batch-scoped code.** V6 has not been applied to the persistent local database, so its old run lacks batch provenance and is deliberately rejected by the current evaluator.

## Run on a dedicated demo database

1. Apply the additive Flyway migration V6 to a dedicated database (not a shared/persistent database).
2. Load complete LEDGER, RAZORPAY, and BANK demo snapshots.
3. Run a fresh three-way reconciliation.
4. Execute:

   ```powershell
   .\scripts\evaluate-ground-truth.ps1
   ```

The evaluator does not write benchmark metrics into reconciliation runs. Database- and machine-dependent processing duration is not a fixed benchmark. A complete output is required before reporting a current-code result.

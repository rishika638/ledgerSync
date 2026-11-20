# Three-way reconciliation verification matrix

The deterministic matcher uses the latest completed full import batch for each of LEDGER, RAZORPAY, and BANK. The run stores those three batch IDs as its input snapshot. It uses persisted financial records only; `demo-data/ground_truth.json` is read by the post-run evaluator and is not passed to reconciliation.

| Scenario | Evidence | Latest result | Automated coverage |
|---|---|---|---|
| Latest-batch isolation and persisted input snapshot | `ThreeWayReconciliationServiceIntegrationTest.reconcilesOnlyLatestSourceBatchesAndPersistsTheirSnapshot` | PASS | Spring/H2 integration test; older source batches are excluded. |
| Amount mismatch and exact minor-unit variance | `classifiesGrossAmountMismatchWithoutCreatingAMatch` | PASS | Uses integer paise and asserts the exact variance. |
| Missing gateway and bank records | `distinguishesMissingGatewayAndMissingBankRecords` | PASS | Both missing-source classes are checked in one isolated input snapshot. |
| Duplicate gateway transactions | `identifiesDuplicateGatewayTransactionsInsteadOfMatchingOne` | PASS | Duplicate capture candidates do not produce a match. |
| Partial and delayed settlement | `classifiesPartialAndDelayedSettlementCases` | PASS | Partial amount is asserted; a late settlement is matched and separately flagged. |
| Ambiguous duplicate bank credits | `refusesToReuseAmbiguousBankCredits` | PASS | Ambiguous bank records produce an exception and no forced match. |
| Repeated reconciliation run | `repeatedRunsKeepSeparateMatchRowsAndPreserveEarlierRunResults` | PASS | Each run retains one distinct match row and its stored matched count. |
| Clean three-way, missing source, amount mismatch, duplicate, fee, partial, delay, and reference classes | Historical synthetic demo evaluation | Historical in-sample counts documented below | Not a fresh PostgreSQL result for the new batch-scoped code. |
| Exact paise arithmetic | Integer-minor-unit data model and backend tests | PASS for tested code paths | Does not establish every fee/GST rounding boundary in the three-way matcher. |
| Repeated approval/concurrent approval side effects | `ResolutionServiceIntegrationTest` | PASS | Exactly one action and audit event are persisted for two concurrent attempts. |
| Arbitrary multi-tranche aggregation and all candidate permutations | No exhaustive evidence | NOT VERIFIED | Do not assume partial batches/tranches are aggregated; current contract is complete source snapshots. |
| V6 PostgreSQL migration and a new-code benchmark run | Not applied to the persistent local database | BLOCKED | No database migration or reload was authorized for existing data. |

## Historical evaluation (not current-code verification)

The prior in-sample demo run `rec_1a1f5e8e229244960369ede1250` classified 165/165 synthetic cases: 110 clean and 55 discrepancy cases. It persisted 116 match rows and 55 exception rows; six delayed cases were mathematically matched and separately flagged. Clean-match one-vs-rest counts were TP=110, FP=0, FN=0, TN=55. These counts are historical only and do not verify the new batch-scoped implementation.

No independent held-out dataset or production data exists. The sample's scenario categories are assigned by fixed generator index ranges, so a second rendering from the same generator would not constitute independent evaluation. Do not call these results production precision or generalization. See [evaluation-metrics.md](./evaluation-metrics.md).

## Reproduce focused tests

```powershell
.\mvnw.cmd '-Dtest=ThreeWayReconciliationServiceIntegrationTest,ResolutionServiceIntegrationTest' test
```

Latest focused tests passed: **10 tests, 0 failures/errors** across reconciliation and resolution integration tests. A new evaluator run requires a dedicated database with V6 applied, complete source batches, and a fresh reconciliation run:

```powershell
.\scripts\evaluate-ground-truth.ps1
```

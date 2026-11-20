# LedgerSync 2.0 Engineering Verification

**Verification date:** 2026-11-21  
**Scope:** Existing working tree and read-only health check of the existing local PostgreSQL/Redis service. No persistent data was reset, demo data reloaded, existing database migrated, cloud infrastructure provisioned, or Gemini key inspected/requested.

## Phase results

| Phase | Status | Evidence and remaining gap |
|---|---|---|
| 1. CSV ingestion | **PARTIAL** | `CsvImportServiceTest`: 10 tests pass for all sources, UTF-8 BOM, exact paise conversion, rejection of sub-paise precision, empty/header-only/wrong-source data, malformed amounts/dates, malformed UTF-8, duplicate rows, multiline quoted fields/escaped quotes, repeated exact-file upload, column count and currency diagnostics. Repository behavior is unit-tested with mocks; PostgreSQL persistence/rollback and concurrent duplicate-upload races remain unverified. |
| 2. Reconciliation | **PARTIAL** | H2/Spring integration tests verify latest completed batch per source, exact three-way matching, amount mismatch, missing gateway/bank records, duplicate gateway rows, partial/delayed settlement, ambiguous bank credits, and repeated runs preserving separate run results. Matching is deterministic and uses integer minor units. Existing in-sample evaluator results (below) are historical and predate the new batch snapshot behavior. Arbitrary multi-tranche aggregation, all fee/GST boundaries, and a fresh PostgreSQL-backed result are not verified. |
| 3. Evaluation | **PARTIAL** | The PowerShell evaluator computes accuracy, one-vs-rest TP/FP/FN/TN, precision, recall, F1, category counts, and failed-case details, and now requires a complete three-source batch snapshot. Existing run has no V6 snapshot, so the evaluator correctly refuses to report it as a new-code result. No independent held-out dataset exists; current synthetic category generation is fixed by index and does not provide an independent scenario distribution. |
| 4. AI, controls, reliability | **PARTIAL** | Full suite covers deterministic Gemini fallback, output validation, HMAC verification, payment invariants, inbox/retry/DLQ, and access controls. Three Spring/H2 integration tests verify transactional approval/audit, duplicate terminal-action rejection, concurrent approvals producing only one action/audit entry, and refusal of `FORCE_MATCH`. Live Gemini remains unverified. The existing PostgreSQL service was not migrated or exercised for this code change. |
| 5. Frontend and submission readiness | **PARTIAL** | TypeScript/Vite production build passes. A browser smoke navigated all eight primary routes and checked headings. Dashboard visual check confirmed the Net At Risk badge no longer overlaps its amount and chart labels are readable dates. The already-running backend predates this code and omits `totalExceptions`; UI now reports that count as unavailable rather than showing `undefined`. Detailed API interactions, mobile viewport, and saved screenshot evidence remain manual. AWS/Vercel are not deployed. |

## Executed evidence

| Check | Result |
|---|---|
| `.\mvnw.cmd test` | **PASS** — 53 tests, 0 failures, 0 errors, 0 skipped. Includes backend E2E, ingestion, reconciliation classifications and repeat runs, concurrent approval, webhook signature/fail-closed behavior, payment, DLQ/retry, and security tests. |
| `.\mvnw.cmd '-Dtest=ResolutionServiceIntegrationTest' test` | **PASS** — 3 tests, 0 failures/errors; approval/audit atomicity, repeated approval, force-match refusal, and concurrent duplicate submission. |
| `Set-Location frontend; npm run build` | **PASS** — TypeScript and Vite production build succeeded. Vite reports a 693.92 kB minified JavaScript chunk-size advisory. |
| PowerShell parser check for `scripts/evaluate-ground-truth.ps1` | **PASS** — no syntax errors. |
| `git diff --check` | **PASS** — no whitespace errors; Git reports line-ending normalization warnings for existing modified files. |
| `http://localhost:8080/actuator/health` | **PASS, read-only** — status `UP`; PostgreSQL and Redis components both `UP`. This is the already-running service, not a restart of the modified code. |
| Browser route/visual smoke | **PASS, limited** — dashboard, imports, reconciliation, transactions, exceptions, assistant, audit, and settings rendered their headings. Dashboard badge placement and date labels were visually checked. The stale backend contract mismatch was visibly surfaced as unavailable. |
| `.\scripts\evaluate-ground-truth.ps1` on current working-code run | **BLOCKED** — existing local run predates V6 and has no source-batch snapshot; evaluator refuses to silently use records from all historical batches. A new run requires applying additive V6 to a dedicated database and importing the demo data there. |
| Live Gemini | **NOT VERIFIED** — no key was read or printed and no provider request was made. |
| Existing PostgreSQL migration V6 | **NOT APPLIED** — the current database is persistent and the explicit data-preservation constraint prohibits running a migration against it without approval. Migration is additive: nullable import hash, partial unique index, and run batch-snapshot text column. |

## Historical benchmark evidence (not a current-code result)

A prior read-only run `rec_1a1f5e8e229244960369ede1250` classified 165/165 included synthetic cases: 110 clean cases and 55 discrepancy cases. It persisted 116 match rows and 55 exception rows; six delayed cases were mathematically matched and separately flagged. Clean-match one-vs-rest counts were TP=110, FP=0, FN=0, TN=55. This is in-sample synthetic evidence only, not a held-out or production accuracy estimate. It is not presented as a verified result for the new batch-scoped matcher. See [evaluation-metrics.md](./evaluation-metrics.md).

## Changes made for this verification

- Added multiline/escaped-quote CSV parsing, exact-file SHA-256 idempotency, physical-row diagnostics, and column/currency validation.
- Added V6 additive schema for content hashes and input-batch provenance; scoped matching to the latest completed batch per source and persist that three-source snapshot on each run.
- Aligned dashboard counts with latest-run match rows and distinguished active from total exceptions; made the metric-card badge responsive to long amounts.
- Added concurrency-safe discrepancy locking and terminal-state guards; rejected `FORCE_MATCH` so ambiguous records cannot be force-resolved; kept action, discrepancy update, and audit event in one transaction.
- Moved the Gemini key from URL query parameters to `x-goog-api-key`, removed response-body logging, discarded unverified model explanations, and preserved exact minor-unit formatting in prompts/fallback text.
- Added frontend fetch errors/retry controls to the dashboard, imports, reconciliation, exceptions, transactions, and audit screens.
- Updated the evaluator to use the run's source-batch snapshot rather than considering all historical ledger records.
- Added integration coverage for amount mismatch, missing gateway/bank, duplicate gateway records, partial/delayed settlement, and repeated-run result preservation.
- Removed Buildathon wording from the frontend top navigation and Settings workspace name; retained LedgerSync branding.
- Removed fixed API-key and webhook signing-key defaults from runtime configuration; webhook verifiers now fail closed when no signing secret is configured.
- Cleared example API-key and webhook-secret values from `.env.example`; configured test-only keys remain in test fixtures.

## Known limitations and safe reproduction

- A reconciliation run uses the latest completed full snapshot for each of LEDGER, RAZORPAY, and BANK. Split or incremental uploads are not merged; upload complete source snapshots before running.
- Exact byte-identical uploads for a source return the original batch; reordered/rewritten but logically identical CSVs are different content and may be imported. Concurrent identical upload races can be rejected by the database unique constraint rather than returning the prior batch.
- V6 has not been applied to the persistent local database. Use a fresh, dedicated demo database for migration and post-change evaluator verification; do not reset or migrate shared data.
- The held-out benchmark is blocked: generating a second set from the current fixed-by-index generator would not be an independent scenario distribution. Do not claim generalization from the included in-sample results.
- Investigation and finance-assistant fallback work without Gemini; provider availability, key validity, and live response behavior remain unverified.
- Audit events are application-recorded in the same transaction as the decision. They are not cryptographically immutable against a database administrator.
- AWS CloudFormation/ECS files remain a scaffold; no AWS/Vercel deployment or URL is claimed. No screenshots were created.

## Reproducible local commands

From the repository root in Windows PowerShell:

```powershell
.\scripts\run-local.ps1
```

In separate terminals:

```powershell
.\scripts\start-backend.ps1
```

```powershell
.\scripts\start-frontend.ps1
```

Run the code checks without touching persistent data:

```powershell
.\mvnw.cmd test
Set-Location frontend
npm run build
Set-Location ..
```

The evaluator is read-only and requires a newly migrated database, complete source batches, and a new three-way run:

```powershell
.\scripts\evaluate-ground-truth.ps1
```

## Manual verification still required

- On a dedicated disposable database: confirm Flyway V6, import each complete source snapshot, verify valid/invalid row persistence and duplicate upload history, run reconciliation, and run the evaluator.
- Verify CSV dialects from authorized real samples, idempotency race behavior, and partial database failures.
- Exercise the investigation → approval/rejection → audit flow through the UI with a fallback investigation; test live Gemini only after locally configuring an authorized key.
- Test unauthorized human actions with operator/admin accounts and inspect committed PostgreSQL audit/action rows.
- Traverse all eight named screens at desktop and narrow viewport widths; collect genuine screenshots only after checking for credentials or personal data.
- Complete AWS/Vercel secret, TLS, cost, and infrastructure review only if deployment is needed for submission.

## Five-minute demo preparation checklist

- [ ] Use a dedicated demo database; never reset or reload shared/persistent data.
- [ ] Confirm health, login, batch IDs, and displayed counts before recording.
- [ ] State whether the investigation uses Gemini or the deterministic fallback; do not imply live Gemini unless actually verified.
- [ ] Explain the distinction between matched rows, exceptions, active exceptions, and resolved exceptions.
- [ ] Show source evidence, a human decision, and its application-recorded audit entry.
- [ ] Describe synthetic benchmark results as in-sample only; do not claim independent accuracy.
- [ ] Avoid showing credentials, API keys, personal data, or cloud consoles containing secrets.
- [ ] Do not claim AWS/Vercel deployment unless completed.
- [ ] Record and review the video only after all manual checks. No video has been created.

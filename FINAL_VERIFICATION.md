# LedgerSync 2.0 Final Verification

> Historical verification record: this file preserves the earlier local end-to-end checks below. For the latest 2026-11-20 tests, in-sample evaluator run, phase statuses, and remaining gaps, see [docs/FINAL_ENGINEERING_VERIFICATION.md](./docs/FINAL_ENGINEERING_VERIFICATION.md). Actions recorded here were not repeated during that verification.

## Result

The local LedgerSync stack builds and runs end-to-end against PostgreSQL and Redis. The latest local demo reconciliation passed all 165 labeled classifications. The external Gemini API and cloud deployments were not verified and are not claimed.

Verification was performed on the existing local demo database. The PostgreSQL and Redis named volumes were preserved; no volume deletion or demo reset was performed. A local rejection-flow check added one resolution action and audit event to the demo data.

## Verification matrix

| Feature | Status | Evidence / limitation |
|---|---|---|
| Docker Compose configuration | PASS | `docker compose config -q` succeeded. PostgreSQL 16 is healthy on host port 5433; Redis 7 is healthy on host port 6380. |
| Spring Boot, PostgreSQL, Redis | PASS | `GET /actuator/health` returned `UP`; database and Redis components both reported `UP`. Actuator now reports Redis `7.4.11`, matching the Compose container. |
| Database migrations | PASS | `flyway_schema_history` shows versions 1 through 5 all successful, including `V5__separate_provider_and_three_way_runs.sql`. |
| Maven wrapper and backend tests | PASS | `.\mvnw.cmd test`: 31 tests, 0 failures, 0 errors. Includes end-to-end, monetary invariant, CSV minor-unit, Gemini fallback, reconciliation idempotency, webhook signature, retry/DLQ, and authorization tests. The Linux `mvnw` launcher also completed both Maven build stages inside the production image build. |
| Production Docker image | PASS | `docker build -t ledgersync:verification .` completed and tagged the local image. No container was deployed from this image. |
| Frontend type-check and production build | PASS | `npm run build` in `frontend` succeeded. Vite reports a non-blocking 690.66 kB minified JavaScript chunk warning. |
| Browser login and dashboard | PASS | Login and authenticated dashboard were rendered in the browser after the Vite host argument and CSRF changes. |
| Demo CSV loading and idempotency | PASS | The three demo sources load through the backend import path. Repeated demo load was verified idempotent. Latest source counts observed: 165 ledger, 163 Razorpay, 149 bank records. |
| CSV money parsing | PASS | `CsvImportServiceTest` verifies integer minor-unit conversion and rejects fractional minor units rather than rounding. Arbitrary user-supplied CSV files were not exhaustively tested. |
| Three-way reconciliation and labels | PASS | `.\scripts\evaluate-ground-truth.ps1` passed against run `rec_1a1f5c1b9cd9f24172ca4a84c30`: 165/165 cases classified as expected. Category details follow. |
| Dashboard metrics | PASS | Authenticated API returned match rate `70.30`, `precisionRate: null`, `recallRate: null`, and latest run ID above. Null precision/recall are intentional; no benchmark values are persisted. |
| Exception queue and transaction timeline | PASS | Authenticated API returned 55 latest-run exceptions; dashboard reported 54 unresolved/active. Transaction timeline returned `primaryRecord`, `relatedRecords`, `discrepancies`, and `auditLogs`. |
| Investigation fallback | PASS | Deterministic fallback is covered by `GeminiClientFallbackTest` and was exercised in the local finance flow. |
| Live external Gemini request | NOT TESTED | No Gemini credential was configured for this verification. Configure a server-side `GEMINI_API_KEY` and verify the provider label before presenting a live Gemini result. |
| Finance Assistant | PASS | Authenticated `/api/v1/finance/assistant/query` returned a response grounded in current finance metrics. |
| Human approval and audit | PASS | Approval flow was exercised in the local finance workflow; the resulting resolution and linked audit event were visible. |
| Human rejection and audit | PASS | A local operator rejection returned action status `REJECTED`, left the discrepancy `OPEN`, and added an audit event. |
| Authentication, CSRF, and role checks | PASS | Anonymous finance access returned 403; invalid login returned 401. Browser mutations use CSRF tokens. Operator attempts at admin-only reset/force operations returned 403. Destructive administrator reset was not run. |
| Credentialed CORS | PASS | Browser-origin preflight returned HTTP 200 with `Access-Control-Allow-Origin: http://localhost:5173` and `Access-Control-Allow-Credentials: true`. |
| Webhooks, retries, and DLQ | PASS (automated tests) | Webhook signature and inbox retry/dead-letter tests passed in the 31-test suite. External provider delivery was not tested. |
| AWS deployment | NOT DEPLOYED / BLOCKED | No cloud resources were provisioned. The CloudFormation template and ECS task definition remain an incomplete scaffold with unresolved placeholders and missing production resources. |
| Vercel deployment | NOT DEPLOYED | No Vercel account, domain, API URL, or production build environment was configured. |
| GitHub Actions | NOT RUN | The workflow runs verification on manual dispatch by default; deployment requires `deploy: true` and pre-provisioned resources. It was not executed in GitHub. |

### Ground-truth classification

| Category | Correct | Total |
|---|---:|---:|
| Clean three-way match | 110 | 110 |
| Missing gateway | 8 | 8 |
| Missing bank | 8 | 8 |
| Amount mismatch | 7 | 7 |
| Duplicate record | 6 | 6 |
| Fee mismatch | 6 | 6 |
| Partial settlement | 6 | 6 |
| Delayed settlement | 6 | 6 |
| Reference mismatch | 5 | 5 |
| Ambiguous match | 3 | 3 |
| **Total** | **165** | **165** |

The run persisted 116 match rows and 55 discrepancy rows. Six delayed-settlement records also satisfy the mathematical match and are independently flagged as delayed; they are not counted among the 110 clean cases. The result is a synthetic-label classification check, not a production accuracy guarantee. Reconciliation precision and recall remain `null`; no fixed latency claim is made.

## Local environment notes

- Start the stack from the repository root with `.\scripts\run-local.ps1`, `.\scripts\start-backend.ps1`, and `.\scripts\start-frontend.ps1` in separate PowerShell terminals.
- PostgreSQL uses host port 5433; the project Redis uses 6380. The host Redis service on 6379 was left untouched after detecting that it was a different Redis instance. The project Redis container was recreated on 6380 while retaining its named data volume.
- Local demo credentials are `operator` / `operator` and `admin` / `admin`; replace them outside local development. Do not commit `.env` or expose API keys.
- The `.env` file is local-only. Its Redis host port settings were aligned to 6380; secrets were not copied into this report.

## Remaining work before submission or production use

1. Perform the final manual browser check below on the user's machine and confirm the intended local credentials.
2. If demonstrating Gemini, configure a valid server-side key, run a real investigation, and report only the provider, evidence, and response actually returned. Keep the key out of Vite variables and browser assets.
3. Before any deployment, provision and secure the missing AWS resources, replace all ECS task-definition placeholders, create the Vercel project/domain, configure `VITE_API_BASE_URL`, and set the backend CORS origin and secure credentials. Follow [docs/deployment-guide.md](./docs/deployment-guide.md); do not treat the current AWS scaffold as turnkey.
4. Review the frontend bundle-size warning if production performance budgets require code splitting.

## Manual end-to-end verification

1. Start Docker Desktop, Java 21, and Node.js/npm. From the repository root run:

   ```powershell
   .\scripts\run-local.ps1
   ```

2. In separate PowerShell terminals run:

   ```powershell
   .\scripts\start-backend.ps1
   ```

   ```powershell
   .\scripts\start-frontend.ps1
   ```

3. Confirm `http://localhost:8080/actuator/health` reports database and Redis `UP`, then open `http://localhost:5173` and sign in as `operator`.
4. On **Imports**, load the demo dataset. On **Reconciliation**, run the three-way reconciliation and inspect the latest run. To verify all labels against persisted records, run:

   ```powershell
   .\scripts\evaluate-ground-truth.ps1
   ```

5. Open a current unresolved exception. Review its evidence, trigger an investigation, and check whether the response reports `RULE_BASED_FALLBACK` or a successfully configured Gemini provider. Do not assume Gemini was used.
6. On separate exceptions, test approval and rejection with notes appropriate to the action. Confirm status and the linked actor/action in **Audit Trail**.
7. Check **Transactions** and open a timeline; verify the related records, discrepancies, and audit entries. Ask the Finance Assistant for current exposure and compare it with the active dashboard metrics.
8. Use the `admin` role only when intentionally testing admin-only actions. Avoid demo reset unless prepared to remove the named demo imports and their linked records.

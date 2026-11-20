# LedgerSync — Historical Webhook Foundation Report

**Author**: Senior Distributed Systems & Backend Engineer  
**Project**: LedgerSync — Idempotent Payment Webhook Reconciliation & Event-Processing Engine  
**Language / Framework**: Java 21 / Spring Boot 3.3.4  
**Date**: October 2026  

> This historical report describes the webhook/payment foundation and is not a current release verification or deployment claim. For the current five-phase LedgerSync status, use [FINAL_ENGINEERING_VERIFICATION.md](./FINAL_ENGINEERING_VERIFICATION.md).

---

## 1. Executive Summary & Problem Space

Modern financial architectures must integrate with third-party payment gateways (such as Stripe and Razorpay) across untrusted, asynchronous networks. This environment presents five foundational distributed-systems failure modes:
1. **Network Retries & Duplicate Deliveries**: Payment providers operate on an *at-least-once* webhook delivery model. If an acknowledgment is delayed or dropped, duplicate webhooks are dispatched. Without idempotent barriers, this duplicates financial capture, inventory depletion, or user credit allocation.
2. **Out-of-Order & Delayed Delivery**: Network packet reordering or multi-path routing can cause a `payment_intent.succeeded` (capture) event to arrive before a `payment_intent.created` (authorization) event, or cause a stale failure event to arrive after a successful capture.
3. **Floating-Point Rounding Corruption**: Storing or computing monetary values using IEEE 754 floating-point representations (`double`, `float`) accumulates fractional rounding errors that corrupt accounting ledgers.
4. **Worker Crashes & Processing Lock Abandonment**: Workers executing asynchronous queue jobs may crash mid-flight due to node restarts, out-of-memory errors, or SIGKILL signals, leaving database jobs stranded in an uncompleted `PROCESSING` lock.
5. **Ledger Drift vs. Provider Settlement**: In rare scenarios, network partitions or gateway bugs result in dropped webhooks. Without independent reconciliation against authoritative remote settlement records, internal ledgers diverge from actual bank balances.

**LedgerSync** solves these challenges by implementing a modular monolith in Java 21 and Spring Boot 3.3.4, using PostgreSQL 16 for durable transactional inbox/outbox storage and row-level queue concurrency (`FOR UPDATE SKIP LOCKED`), Flyway for versioned database migrations, and Redis 7 for optional transient coordination.

---

## 2. Codebase Architecture & Module Map

```
e:\ledgerSync\
├── .github/workflows/ci.yml                   # Automated GitHub Actions build & test pipeline
├── docker-compose.yml                         # PostgreSQL 16 & Redis 7 local orchestration
├── pom.xml                                    # Production dependencies & build plugins
├── mvnw & mvnw.cmd                            # Self-bootstrapping Maven wrappers
├── .env.example                               # Environment template for secrets and config
├── scripts/
│   ├── run-local.ps1                          # Local Docker & execution bootstrapping
│   ├── test-webhook.ps1                       # HMAC-SHA256 signature generator & duplicate tester
│   └── simulate-traffic.ps1                   # Reconciliation, discrepancy, and DLQ simulation
├── src/main/java/com/ledgersync/
│   ├── LedgerSyncApplication.java             # Spring Boot main class
│   ├── common/
│   │   ├── id/IdGenerator.java                # Time-ordered collision-resistant identifier generator
│   │   ├── money/MonetaryAmount.java          # Exact minor-unit (cents) value object
│   │   ├── error/                             # BusinessException, InvariantViolationException, GlobalExceptionHandler
│   │   └── security/                          # SecurityConfig, ApiKeyAuthenticationFilter, ApiKeyAuthenticationToken
│   ├── ingestion/
│   │   ├── signature/                         # HmacUtils, ProviderSignatureVerifier, Stripe & Razorpay verifiers
│   │   ├── model/                             # WebhookEventEntity, WebhookEventStatus, PaymentProvider
│   │   ├── repository/                        # WebhookEventRepository
│   │   ├── dto/                               # StandardWebhookEvent, WebhookIngestionResponse
│   │   ├── service/                           # WebhookParser, WebhookIngestionService
│   │   └── controller/                        # WebhookIngestionController
│   ├── payment/
│   │   ├── model/                             # PaymentTransactionEntity, PaymentStatus, RefundEntity, RefundStatus
│   │   ├── repository/                        # PaymentTransactionRepository, RefundRepository
│   │   ├── service/                           # PaymentStateMachine, PaymentProcessingService
│   │   ├── dto/                               # PaymentResponse, RefundResponse
│   │   └── controller/                        # PaymentController
│   ├── queue/
│   │   ├── model/                             # InboxMessageEntity, InboxStatus, OutboxMessageEntity, OutboxStatus
│   │   ├── repository/                        # InboxMessageRepository (FOR UPDATE SKIP LOCKED), OutboxMessageRepository
│   │   ├── worker/                            # InboxWorker, AbandonedJobRecoveryWorker, OptionalRedisCoordinator
│   │   ├── service/                           # DeadLetterRedriveService
│   │   ├── dto/                               # InboxMessageResponse, RedriveResult
│   │   └── controller/                        # QueueManagementController
│   ├── reconciliation/
│   │   ├── model/                             # ReconciliationRunEntity, DiscrepancyEntity, Enums (Type, Severity, Status)
│   │   ├── repository/                        # ReconciliationRunRepository, DiscrepancyRepository
│   │   ├── provider/                          # PaymentProviderClient, RemotePaymentRecord, MockStripeClient, MockRazorpayClient
│   │   ├── service/                           # ReconciliationService, DiscrepancyResolutionService
│   │   ├── dto/                               # ReconciliationRunResponse, DiscrepancyResponse, ResolveDiscrepancyRequest
│   │   └── controller/                        # ReconciliationController
│   ├── audit/
│   │   ├── model/                             # AuditEventEntity (application-recorded audit history)
│   │   ├── repository/                        # AuditEventRepository
│   │   ├── service/                           # AuditService
│   │   └── controller/                        # AuditController
│   └── observability/
│       ├── CorrelationIdFilter.java           # W3C traceparent, X-Correlation-Id header & MDC propagation
│       └── MetricsConfig.java                 # Micrometer custom counters & Prometheus meters
└── src/main/resources/
    ├── application.yml                        # Production configuration
    └── db/migration/
        ├── V1__init_schema.sql                # Base tables & foreign keys
        └── V2__indexes_and_constraints.sql    # Check constraints, financial invariants & queue indexes
```

---

## 3. Database Schema, Indexes & Financial Invariants

All persistence definitions are managed in versioned Flyway migrations:

### 3.1 Relational Schema (`V1__init_schema.sql`)
1. **`webhook_events`**: Stores raw, unmutated webhook payloads before processing. Enforces unique constraint `uk_webhook_provider_event` on `(provider, provider_event_id)`.
2. **`inbox_messages`**: Transactional inbox for durable worker dispatch. Fields include `status` (`PENDING`, `PROCESSING`, `COMPLETED`, `DEAD_LETTER`), `attempt_count`, `max_attempts`, `scheduled_at`, `locked_at`, `locked_by`, and `last_error`.
3. **`outbox_messages`**: Transactional outbox pattern for reliable external notifications.
4. **`payment_transactions`**: Authoritative domain model storing currency, amounts in cents, status, and an optimistic locking `@Version` counter.
5. **`refunds`**: Subordinate refund entities enforcing `uk_refund_provider_refund_id` on `(payment_id, provider_refund_id)`.
6. **`audit_events`**: Application-recorded log with actor, action, resource type, and before/after state. It is not tamper-proof against database administrators.
7. **`reconciliation_runs` & `reconciliation_discrepancies`**: Audit ledger recording run metrics and detected discrepancies.

### 3.2 Financial Invariants & Partial Indexes (`V2__indexes_and_constraints.sql`)
```sql
-- Non-negative monetary amounts
ALTER TABLE payment_transactions
    ADD CONSTRAINT chk_payment_amounts_non_negative
    CHECK (amount_cents >= 0 AND captured_amount_cents >= 0 AND refunded_amount_cents >= 0);

-- Mathematical capture boundary
ALTER TABLE payment_transactions
    ADD CONSTRAINT chk_captured_lte_authorized
    CHECK (captured_amount_cents <= amount_cents);

-- Mathematical refund boundary
ALTER TABLE payment_transactions
    ADD CONSTRAINT chk_refunded_lte_captured
    CHECK (refunded_amount_cents <= captured_amount_cents);

-- High-throughput partial queue indexes
CREATE INDEX idx_inbox_pending_poll
    ON inbox_messages (scheduled_at ASC, created_at ASC)
    WHERE status = 'PENDING';

CREATE INDEX idx_inbox_stuck_recovery
    ON inbox_messages (locked_at ASC)
    WHERE status = 'PROCESSING';
```

---

## 4. REST API Endpoint Inventory

| Method | Endpoint | Authorization | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/webhooks/stripe` | Cryptographic Signature | Ingest signed Stripe webhook raw bytes |
| `POST` | `/api/v1/webhooks/razorpay` | Cryptographic Signature | Ingest signed Razorpay webhook raw bytes |
| `POST` | `/api/v1/webhooks/{provider}` | Cryptographic Signature | Ingest generic signed webhook |
| `GET` | `/api/v1/payments/{id}` | `ROLE_OPERATOR`, `ROLE_ADMIN` | Lookup payment by internal ID |
| `GET` | `/api/v1/payments/provider/{provider}/{id}` | `ROLE_OPERATOR`, `ROLE_ADMIN` | Lookup payment by provider payment ID |
| `GET` | `/api/v1/payments` | `ROLE_OPERATOR`, `ROLE_ADMIN` | Filtered, paginated transaction list |
| `GET` | `/api/v1/payments/{id}/refunds` | `ROLE_OPERATOR`, `ROLE_ADMIN` | List refunds for payment |
| `GET` | `/api/v1/queue/inbox` | `ROLE_OPERATOR`, `ROLE_ADMIN` | List inbox messages by status |
| `GET` | `/api/v1/queue/dead-letters` | `ROLE_OPERATOR`, `ROLE_ADMIN` | Query dead-letter queue |
| `POST` | `/api/v1/queue/redrive/{id}` | `ROLE_OPERATOR`, `ROLE_ADMIN` | Safe redrive of single dead-letter message |
| `POST` | `/api/v1/queue/redrive/all` | `ROLE_OPERATOR`, `ROLE_ADMIN` | Bulk redrive dead-letter messages |
| `POST` | `/api/v1/reconciliation/run` | `ROLE_OPERATOR`, `ROLE_ADMIN` | Trigger on-demand reconciliation run |
| `GET` | `/api/v1/reconciliation/runs` | `ROLE_OPERATOR`, `ROLE_ADMIN` | List reconciliation run history |
| `GET` | `/api/v1/reconciliation/discrepancies` | `ROLE_OPERATOR`, `ROLE_ADMIN` | Query detected discrepancies by status |
| `POST` | `/api/v1/reconciliation/discrepancies/{id}/resolve`| `ROLE_ADMIN` | Resolve discrepancy with investigation note |
| `GET` | `/api/v1/audit/resources/{type}/{id}` | `ROLE_OPERATOR`, `ROLE_ADMIN` | Query application-recorded audit history |
| `GET` | `/api/v1/audit/correlation/{correlationId}` | `ROLE_OPERATOR`, `ROLE_ADMIN` | Query audit records by correlation ID |
| `GET` | `/actuator/health` | Public | Liveness and readiness health checks |
| `GET` | `/actuator/prometheus` | Public | Prometheus Micrometer metrics scrape |

---

## 5. Security & Consistency Review

### 5.1 Constant-Time HMAC & Replay Prevention
- Signatures are calculated on raw incoming bytes before JSON deserialization to avoid encoding mismatches.
- Signatures are compared via `MessageDigest.isEqual` (`HmacUtils.constantTimeHexEquals`) in $O(N)$ constant time regardless of where a mismatch occurs, defeating side-channel timing attacks.
- Stripe timestamps are parsed and validated against `Math.abs(now - timestamp) <= 300s` to block replay attacks.

### 5.2 Transaction Isolation & Boundary Safety
- **Ingestion Transaction**: Ingestion writes `webhook_events` and `inbox_messages` atomically. If the database is unreachable, HTTP 500 is returned, prompting gateway retries.
- **Worker Execution Transaction**: Domain execution uses `@Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)`. If a domain rule fails (such as an over-refund invariant violation), the domain transaction rolls back cleanly without marking the inbox message state change as rollback-only. The worker then reliably schedules exponential backoff or flags `DEAD_LETTER`.
- **Row Locking**: Payment transactions use pessimistic write locks (`SELECT ... FOR UPDATE`) or optimistic version checks (`@Version`) to prevent concurrent modification lost updates.

### 5.3 Secrets & Data Privacy
- Secrets are sourced strictly from environment variables via Spring property placeholders (`${LEDGERSYNC_WEBHOOK_STRIPE_SECRET}`).
- No raw credit card PANs, CVVs, or bank credentials are ever accepted or persisted.

---

## 6. Asynchronous Worker Resilience & Redis Fallback

1. **SKIP LOCKED Polling**:
   Workers claim batches using native PostgreSQL `SELECT ... FOR UPDATE SKIP LOCKED`. Multiple worker processes running concurrently poll the same table without deadlocks or thread contention.
2. **Exponential Backoff with Full Jitter**:
   Failed jobs are rescheduled according to:
   $$\text{delay} = \min(60000\text{ ms}, 1000\text{ ms} \times 2^{\text{attempt}}) + \text{jitter}(100\text{ ms}..500\text{ ms})$$
   Full jitter prevents "thundering herd" retry spikes against the database.
3. **Dead-Letter Quarantine (DLQ)**:
   Jobs exceeding `max_attempts` (default 5) are transitioned to `DEAD_LETTER`. The `last_error` field captures the exception name and message. The job is excluded from ordinary polling until an authorized operator triggers a redrive.
4. **Crash Recovery**:
   If a worker process crashes while holding jobs in `PROCESSING`, `AbandonedJobRecoveryWorker` scans for locks older than 300 seconds and resets them to `PENDING` with an incremented attempt count.
5. **Redis Outage Survivability**:
   `OptionalRedisCoordinator` treats Redis strictly as an opportunistic lease accelerator. If Redis becomes unavailable, network-partitioned, or returns an error, the coordinator logs a warning and permits the worker to proceed natively using PostgreSQL `SKIP LOCKED`. Accepted events are never lost, and financial safety is maintained.

---

## 7. Independent Reconciliation Architecture

Reconciliation runs periodically or on-demand:
1. Queries the provider adapter (`MockStripeClient`, `MockRazorpayClient`) for settled transactions within a 7-day sliding window.
2. Compares authoritative records against local ledger rows:
   - **`MISSING_IN_LOCAL`** (Critical): Provider reports captured funds, but local database has no record (dropped webhook).
   - **`STATUS_MISMATCH`** (High): Status discrepancy between provider and local ledger (e.g. captured vs authorized).
   - **`AMOUNT_MISMATCH`** (Critical): Captured amount difference.
   - **`UNEXPECTED_REFUND`** (High): Provider reports refunds not reflected locally.
   - **`STALE_UNRESOLVED`** (Medium): Local payments in `CREATED` or `AUTHORIZED` for > 24 hours without settlement.
3. **Idempotent Storage**: Queries existing active discrepancies (`status IN ('OPEN', 'INVESTIGATING')`) for each payment ID and discrepancy type. Repeated runs never generate duplicate open discrepancies.
4. **Preservation of Evidence**: Discrepancies are not silently auto-corrected. Operator actions are recorded in `audit_events`; the application history is not a cryptographic write-once archive.

---

## 8. Verification Status & Test Execution Guide

### Status Summary
- **Source Code**: **`IMPLEMENTED`** (Defensively coded across all domains).
- **Automated Tests**: **47 passed** on 2026-11-21 via `.\mvnw.cmd test`; this does not verify production infrastructure, a live Gemini request, or the unapplied V6 migration on the persistent local database.

### Test Commands to Run
```powershell
# Run the complete test suite via Maven Wrapper
.\mvnw.cmd clean test

# Run individual targeted test classes
.\mvnw.cmd test -Dtest=WebhookSignatureVerificationTest
.\mvnw.cmd test -Dtest=PaymentStateMachineTest
.\mvnw.cmd test -Dtest=MonetaryInvariantTest
.\mvnw.cmd test -Dtest=InboxWorkerAndDeadLetterTest
.\mvnw.cmd test -Dtest=ReconciliationServiceTest
.\mvnw.cmd test -Dtest=SecurityAccessTest
.\mvnw.cmd test -Dtest=LedgerSyncEndToEndTest
```

---

## 9. Limitations & Prioritized Next Steps

1. **Production Gateway Connectors**: The current reconciliation provider adapters utilize deterministic in-memory simulator fixtures (`MockStripeClient`, `MockRazorpayClient`). Production deployment requires plugging in HTTP clients that call Stripe's `/v1/payment_intents` and Razorpay's `/v1/payments` with live API keys.
2. **Distributed APM Export**: W3C TraceContext headers (`traceparent`) and correlation IDs are propagated through MDC and HTTP headers. Exporting spans to OpenTelemetry collectors (OTLP/gRPC) can be added via the OpenTelemetry Java Agent.
3. **Partitioned Inboxes**: For hyperscale throughput exceeding tens of thousands of webhooks per second, PostgreSQL table partitioning by date/provider can be introduced on `webhook_events` and `inbox_messages`.

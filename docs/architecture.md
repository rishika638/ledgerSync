# LedgerSync — Technical Architecture & System Design

## 1. High-Level System Architecture

LedgerSync is a modular monolith in **Java 21** and **Spring Boot 3.3.x** with webhook/payment processing and CSV-based three-way finance reconciliation. Webhook delivery is treated as at-least-once: provider-scoped event identity, database transactions, and state checks reduce duplicate effects, but the system does not claim a global exactly-once guarantee.

```mermaid
flowchart TD
    subgraph External["External Payment Ecosystem"]
        direction TB
        Provider["Payment Providers<br/>(Stripe, Razorpay)"]
        Operator["Operations / Admin<br/>(API Consumer / Support)"]
    end

    subgraph Security["Edge & Ingress Security"]
        direction TB
        SigVer["ProviderSignatureVerifier<br/>(Raw HMAC-SHA256, Replay Window)"]
        AuthFilter["ApiKeyAuthenticationFilter<br/>(X-API-Key: INGESTION, OPERATOR, ADMIN)"]
        CorrFilter["CorrelationIdFilter<br/>(W3C traceparent, X-Correlation-Id, MDC)"]
    end

    subgraph App["LedgerSync Modular Monolith"]
        direction TB
        subgraph Ingestion["Ingestion Domain"]
            WebhookCtrl["WebhookIngestionController"]
            WebhookService["WebhookIngestionService"]
            Parser["WebhookParser"]
        end

        subgraph Queue["Durable Queue Domain"]
            InboxRepo["InboxMessageRepository<br/>(SKIP LOCKED Polling)"]
            Worker["InboxWorker<br/>(Bounded Backoff + Jitter)"]
            RecoveryWorker["AbandonedJobRecoveryWorker<br/>(Heartbeat Timeout Recovery)"]
            RedriveService["DeadLetterRedriveService<br/>(DLQ Replay)"]
        end

        subgraph Payment["Payment Domain"]
            StateMachine["PaymentStateMachine<br/>(Out-of-Order Dampening)"]
            PaymentService["PaymentProcessingService<br/>(Exact Money Arithmetic)"]
            PaymentCtrl["PaymentController"]
        end

        subgraph AuditDomain["Audit & Observability"]
            AuditService["AuditService<br/>(Transactional Application Audit History)"]
            MetricsConfig["MetricsConfig<br/>(Micrometer / Prometheus)"]
        end

        subgraph ReconciliationDomain["Reconciliation Domain"]
            ReconService["ReconciliationService<br/>(Scheduled & On-Demand Audit)"]
            DiscrepancyService["DiscrepancyResolutionService<br/>(Evidence Preservation)"]
            MockClients["PaymentProviderClient<br/>(Authoritative Settlement State)"]
        end
    end

    subgraph Persistence["Storage & Coordination Tier"]
        direction TB
        PG[(PostgreSQL 16 Engine<br/>Primary Source of Truth)]
        Redis[(Redis 7 Cluster<br/>Optional Lease Acceleration)]
    end

    Provider -->|Signed HTTPS POST| SigVer
    Operator -->|Authenticated HTTPS| AuthFilter
    SigVer --> WebhookCtrl
    AuthFilter --> PaymentCtrl
    AuthFilter --> ReconService
    CorrFilter --> App

    WebhookCtrl --> WebhookService
    WebhookService --> Parser
    WebhookService -->|Atomic Transaction 1| PG

    Worker -->|FOR UPDATE SKIP LOCKED| PG
    Worker -.->|Try Acquire Lease| Redis
    Worker --> PaymentService
    RecoveryWorker -->|Reset Stuck Jobs| PG
    RedriveService -->|Re-queue Dead Letters| PG

    PaymentService --> StateMachine
    PaymentService -->|Atomic Transaction 2| PG
    PaymentService --> AuditService
    AuditService --> PG

    ReconService --> MockClients
    ReconService --> PG
    DiscrepancyService --> PG
```

---

## 2. Webhook Ingestion & Processing Sequence

The ingestion flow decouples incoming HTTP receipt from domain processing. Incoming payloads are verified using constant-time cryptographic comparisons against raw bytes, checked against timestamp replay windows, and persisted into both `webhook_events` and `inbox_messages` in a single ACID database transaction before an HTTP acknowledgment is sent.

```mermaid
sequenceDiagram
    autonumber
    participant P as Payment Provider
    participant C as WebhookIngestionController
    participant S as WebhookIngestionService
    participant V as ProviderSignatureVerifier
    participant DB as PostgreSQL Database
    participant W as InboxWorker
    participant D as PaymentProcessingService
    participant A as AuditService

    P->>C: POST /api/v1/webhooks/stripe (Raw Bytes, Header: Stripe-Signature)
    C->>S: ingest(provider, rawBytes, signatureHeader)
    S->>V: verify(rawBytes, signatureHeader)
    Note over V: Extract t & v1<br/>Check Math.abs(now - t) <= 300s<br/>HmacSHA256(t + "." + rawBytes)<br/>constantTimeHexEquals()
    alt Invalid Signature or Expired Window
        V-->>S: false
        S-->>C: throw InvalidSignatureException
        C-->>P: HTTP 401 Unauthorized
    else Valid Cryptographic Signature
        V-->>S: true
        S->>DB: Check (provider, provider_event_id) exists
        alt Duplicate Delivery Detected
            DB-->>S: WebhookEventEntity found
            S-->>C: WebhookIngestionResponse(status: DUPLICATE)
            C-->>P: HTTP 200 OK (duplicate acknowledged, no financial effect)
        else Fresh Webhook Event
            S->>DB: BEGIN TX: Insert webhook_events + Insert inbox_messages (PENDING)
            DB-->>S: TX Committed
            S-->>C: WebhookIngestionResponse(status: ACCEPTED)
            C-->>P: HTTP 200 OK (Accepted for asynchronous processing)
        end
    end

    Note over W,DB: Background Asynchronous Processing Loop
    W->>DB: SELECT * FROM inbox_messages WHERE status = 'PENDING' LIMIT 25 FOR UPDATE SKIP LOCKED
    loop For each claimed message
        W->>DB: UPDATE inbox_messages SET status = 'PROCESSING', locked_at = NOW(), locked_by = workerId
        W->>D: processEvent(StandardWebhookEvent)
        D->>DB: SELECT * FROM payment_transactions WHERE provider_payment_id = ? FOR UPDATE
        D->>D: Evaluate State Machine & Invariant Invariants (cents, non-negative, refund <= captured)
        D->>DB: BEGIN TX (REQUIRES_NEW): UPDATE payment_transactions + INSERT refunds + INSERT outbox_messages
        D->>A: record("PROCESS_PAYMENT_EVENT", ...)
        A->>DB: INSERT audit_events (before_state, after_state)
        DB-->>D: Domain TX Committed
        W->>DB: UPDATE inbox_messages SET status = 'COMPLETED'
    end
```

---

## 3. Failure & Recovery Sequence

The asynchronous pipeline is resilient to transient failures, poison pills, worker node crashes, and network partitions.

```mermaid
sequenceDiagram
    autonumber
    participant W as InboxWorker
    participant R as AbandonedJobRecoveryWorker
    participant DB as PostgreSQL Database
    participant DLQ as DeadLetterRedriveService
    participant Admin as Operator / Admin

    Note over W,DB: Scenario A: Transient Failure & Exponential Backoff
    W->>DB: Claim pending message
    W->>W: processEvent() fails (e.g. database deadlock or transient lock timeout)
    W->>W: Calculate backoff: min(60s, 1000ms * 2^attempt) + jitter(100ms..500ms)
    alt attempt < maxAttempts (5)
        W->>DB: UPDATE inbox_messages SET status = 'PENDING', scheduled_at = NOW() + backoff, attempt_count = attempt + 1
        Note over W,DB: Job sleeps until scheduled_at without blocking worker thread
    else attempt >= maxAttempts
        W->>DB: UPDATE inbox_messages SET status = 'DEAD_LETTER', last_error = exceptionDetails
        Note over W,DB: Job quarantined in DLQ; alerts raised via Micrometer counter
    end

    Note over R,DB: Scenario B: Worker Node Hard Crash (SIGKILL / Out-Of-Memory)
    Note over W: Worker node terminates abruptly while job status = 'PROCESSING'
    R->>DB: SELECT * FROM inbox_messages WHERE status = 'PROCESSING' AND locked_at <= NOW() - 300s
    DB-->>R: Returns abandoned message
    R->>DB: UPDATE inbox_messages SET status = 'PENDING', locked_at = NULL, locked_by = NULL, attempt_count = attempt + 1

    Note over DLQ,Admin: Scenario C: Controlled Dead-Letter Redrive
    Admin->>DLQ: POST /api/v1/queue/redrive/{id} (X-API-Key: admin-secret-key)
    DLQ->>DB: SELECT * FROM inbox_messages WHERE id = ? AND status = 'DEAD_LETTER'
    DLQ->>DB: UPDATE inbox_messages SET status = 'PENDING', attempt_count = 0, scheduled_at = NOW()
    DLQ->>DB: INSERT audit_events (action: "REDRIVE_DEAD_LETTER", actor: admin)
    DLQ-->>Admin: HTTP 200 OK (Message re-queued without rewriting original history)
```

---

## 4. Independent Reconciliation Sequence

Reconciliation is an independent audit control loop that actively detects balance and status drift between local database records and authoritative provider settlement state.

```mermaid
sequenceDiagram
    autonumber
    participant Cron as Reconciliation Scheduler
    participant S as ReconciliationService
    participant C as PaymentProviderClient (Mock/Live)
    participant DB as PostgreSQL Database
    participant D as DiscrepancyResolutionService
    participant A as Admin Operator

    Cron->>S: scheduledReconciliation() / POST /api/v1/reconciliation/run?provider=STRIPE
    S->>DB: INSERT reconciliation_runs (status: STARTED)
    S->>C: fetchSettledTransactions(from: 7 days ago, to: now, limit: 50)
    C-->>S: List<RemotePaymentRecord>

    loop For each remote payment record
        S->>DB: SELECT * FROM payment_transactions WHERE provider_payment_id = ?
        alt Payment Not Found in Local Database
            Note over S: MISSING_IN_LOCAL (Dropped/Undelivered Webhook)
            S->>DB: Check if active discrepancy exists for provider_payment_id
            S->>DB: INSERT reconciliation_discrepancies (type: MISSING_IN_LOCAL, severity: CRITICAL, evidence: JSON)
        else Payment Found Locally
            Note over S: Compare Remote vs Local Status & Amounts
            alt Remote is CAPTURED and Local is AUTHORIZED
                S->>DB: INSERT reconciliation_discrepancies (type: STATUS_MISMATCH, severity: HIGH)
            else Remote captured_amount != Local captured_amount
                S->>DB: INSERT reconciliation_discrepancies (type: AMOUNT_MISMATCH, severity: CRITICAL)
            else Remote has unexpected refunds
                S->>DB: INSERT reconciliation_discrepancies (type: UNEXPECTED_REFUND, severity: HIGH)
            end
        end
    end

    Note over S,DB: Check Stale Local Transactions (> 24 hours without settlement)
    S->>DB: SELECT * FROM payment_transactions WHERE status IN ('CREATED', 'AUTHORIZED') AND created_at <= NOW() - 24h
    S->>DB: INSERT reconciliation_discrepancies (type: STALE_UNRESOLVED, severity: MEDIUM)
    S->>DB: UPDATE reconciliation_runs SET status = 'COMPLETED', discrepancies_count = N

    Note over D,A: Manual Investigation & Resolution (No Silent Mutation)
    A->>D: POST /api/v1/reconciliation/discrepancies/{id}/resolve
    D->>DB: UPDATE reconciliation_discrepancies SET status = 'RESOLVED', resolution_notes = notes, resolved_by = admin
    D->>DB: INSERT audit_events (action: "RESOLVE_DISCREPANCY", actor: admin)
    D-->>A: HTTP 200 OK (Discrepancy resolved with application-recorded audit event)
```

---

## 5. Payment Lifecycle & State Transition Diagram

The state machine explicitly restricts transitions and handles delayed, duplicate, and out-of-order events. Out-of-order events arriving for already captured or refunded payments are safely dampened (treated as NO-OPs) without corrupting financial records.

```mermaid
stateDiagram-v2
    [*] --> CREATED: Webhook payment_intent.created

    CREATED --> AUTHORIZED: payment.authorized
    CREATED --> CAPTURED: Direct capture (payment_intent.succeeded)
    CREATED --> FAILED: payment.failed
    CREATED --> CANCELLED: payment_intent.canceled

    AUTHORIZED --> CAPTURED: payment.captured / payment_intent.succeeded
    AUTHORIZED --> FAILED: authorization_failure
    AUTHORIZED --> CANCELLED: void / cancel

    CAPTURED --> PARTIALLY_REFUNDED: charge.refunded (partial amount)
    CAPTURED --> REFUNDED: charge.refunded (full amount)

    PARTIALLY_REFUNDED --> PARTIALLY_REFUNDED: additional partial refund
    PARTIALLY_REFUNDED --> REFUNDED: refund total equals captured amount

    note right of CAPTURED
        Out-of-Order Dampener:
        Late CREATED, AUTHORIZED,
        FAILED, or CANCELLED events
        are ignored as safe NO-OPs.
    end note

    note right of REFUNDED
        Terminal State:
        Cannot be transitioned away from.
        Stale events ignored.
    end note

    FAILED --> [*]: Terminal State
    CANCELLED --> [*]: Terminal State
    REFUNDED --> [*]: Terminal State
```

---

## 6. Component Responsibilities & Data Ownership

| Component | Responsibility | Data Ownership |
| :--- | :--- | :--- |
| **`WebhookIngestionService`** | Verifies HMAC, parses JSON payload, checks provider-scoped uniqueness, and writes to durable inbox. | Writes to `webhook_events`, `inbox_messages`. |
| **`InboxWorker`** | Polls database using `FOR UPDATE SKIP LOCKED`, coordinates retries with jitter, and updates message states. | Updates `inbox_messages`. |
| **`AbandonedJobRecoveryWorker`** | Detects crashed worker locks (`locked_at <= NOW() - 300s`) and resets jobs to `PENDING`. | Updates `inbox_messages`. |
| **`PaymentProcessingService`** | Validates monetary invariants, applies state transitions, records refunds, and enqueues outbox events. | Writes to `payment_transactions`, `refunds`, `outbox_messages`. |
| **`AuditService`** | Records application audit events in the same transaction as domain updates. | Writes to `audit_events`; database administrators can still alter rows. |
| **`ReconciliationService`** | Queries authoritative provider clients, identifies missing webhooks or drift, and creates discrepancies. | Writes to `reconciliation_runs`, `reconciliation_discrepancies`. |
| **`DiscrepancyResolutionService`** | Enforces admin-only resolution of discrepancies and preserves investigation evidence. | Updates `reconciliation_discrepancies`, writes to `audit_events`. |

---

## 7. Key Engineering Decisions & Trade-Offs

1. **PostgreSQL SKIP LOCKED vs Kafka**:
   - *Decision*: Built with PostgreSQL `FOR UPDATE SKIP LOCKED` for the transactional inbox.
   - *Trade-off*: Keeps the transactional inbox in PostgreSQL and avoids a separate broker dual-write path. Throughput limits have not been benchmarked.
2. **Exact Minor Units (`long amountMinor`) vs Floating Point (`double`)**:
   - *Decision*: Persisted financial amounts use integer minor units and CSV amounts are converted with decimal arithmetic; the finance path avoids binary floating-point money calculations.
3. **Redis as Acceleration vs Source of Truth**:
   - *Decision*: Redis is used strictly for transient leader lease coordination via `OptionalRedisCoordinator`.
   - *Trade-off*: PostgreSQL is the durable queue/state source; tests cover fallback behavior, but production outage recovery and throughput have not been load-tested.
4. **No Silent Auto-Correction**:
   - *Decision*: When reconciliation discovers discrepancies (e.g. `STATUS_MISMATCH` or `AMOUNT_MISMATCH`), the engine refuses to silently overwrite local balances.
   - *Trade-off*: Preserves source records and records decisions for review. The application audit history is not a cryptographic or database-admin-proof immutable ledger.

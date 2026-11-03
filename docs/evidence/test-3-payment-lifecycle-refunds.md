# Test 3 — Payment Lifecycle and Refund Processing

**Status: PASS**

## 1. Objective

Verify that LedgerSync correctly processes the payment lifecycle from creation through authorization and capture, supports partial and full refunds, and prevents a late failure event from incorrectly reversing a fully refunded payment.

The test validates:

- Payment creation.
- Payment authorization.
- Payment capture.
- Partial refund processing.
- Remaining balance refund processing.
- Cumulative refund amount validation.
- Terminal payment-state behavior.
- Persistence of payment, refund, and inbox processing results.

## 2. Test environment

- Application: LedgerSync Spring Boot application
- Database: PostgreSQL
- Webhook provider: Stripe
- Processing mechanism: PostgreSQL-backed inbox and asynchronous worker
- Payment processing: `PaymentProcessingService`
- State transition logic: `PaymentStateMachine`

The test used a dedicated payment identifier and provider event identifiers to avoid relying on unrelated existing payment records.

## 3. Implementation inspected

### PaymentProcessingService.java

The payment processing service is responsible for applying normalized webhook events to payment records.

Implementation observations:

- Payment processing runs in a separate transaction using `REQUIRES_NEW`.
- Transaction isolation is `READ_COMMITTED`.
- Existing payments are retrieved using the provider and provider payment ID.
- The service applies payment lifecycle changes.
- Refund events are processed separately from ordinary payment-state events.
- Successful processing writes an audit event.
- An outbox message is created within the payment-processing transaction.
- Duplicate refund identifiers are skipped.
- Non-positive refund amounts are skipped.
- Cumulative refunds cannot exceed the captured payment amount.
- The payment is marked `PARTIALLY_REFUNDED` when only part of the captured amount has been refunded.
- The payment is marked `REFUNDED` when the refunded amount reaches the captured amount.

### PaymentStateMachine.java

The state machine controls permitted payment status transitions.

Relevant inspected behavior:

- Repeated transitions to the current state are treated as no-ops.
- `REFUNDED`, `FAILED`, and `CANCELLED` are terminal states.
- Late `CREATED`, `AUTHORIZED`, `FAILED`, or `CANCELLED` events do not reverse a payment already in `CAPTURED` or `PARTIALLY_REFUNDED` state.
- A direct `CREATED` to `CAPTURED` transition is supported.
- Unknown event types map to `CREATED`.

The state machine does not compare provider event timestamps to establish event ordering. This test therefore validates the specific late-event behavior implemented by the current state machine, not a general timestamp-based stale-event ordering mechanism.

## 4. Test data

Provider: `STRIPE`

Provider Payment ID:

    pi_t3_a9c3b9e18651

Internal Payment ID:

    pay_1a19ea68b629709070b6a674605

Original payment amount:

    15000 cents USD

Lifecycle events:

| Sequence | Event | Expected payment status |
|---:|---|---|
| 1 | Payment created | CREATED |
| 2 | Payment authorized | AUTHORIZED |
| 3 | Payment captured | CAPTURED |
| 4 | Partial refund of 4000 cents | PARTIALLY_REFUNDED |
| 5 | Remaining refund of 11000 cents | REFUNDED |
| 6 | Late payment failure event | Remains REFUNDED |

## 5. Test execution — Payment lifecycle

The payment was processed through the following lifecycle:

### Step 1 — Payment creation

A payment-created event was processed for the dedicated Stripe payment.

Expected result:

- Payment record created.
- Status set to `CREATED`.

### Step 2 — Payment authorization

An authorization event was processed for the same payment.

Expected result:

- Existing payment identified using provider and provider payment ID.
- Payment status changed to `AUTHORIZED`.

### Step 3 — Payment capture

A capture event was processed.

Expected result:

- Payment status changed to `CAPTURED`.
- Captured amount recorded as 15000 cents.

### Step 4 — Partial refund

A refund event for 4000 cents was processed.

Expected result:

- Refund record persisted.
- Refunded amount updated to 4000 cents.
- Payment status changed to `PARTIALLY_REFUNDED`.

### Step 5 — Remaining refund

A second refund event for 11000 cents was processed.

Expected result:

- Second refund record persisted.
- Cumulative refunded amount became 15000 cents.
- Payment status changed to `REFUNDED`.

### Step 6 — Late failure event

A late failure event was processed after the payment had reached `REFUNDED`.

Expected result:

- The terminal `REFUNDED` status must not be reversed.
- Payment remains `REFUNDED`.

## 6. PostgreSQL payment verification

The final payment record was inspected in PostgreSQL.

Verified record:

| Field | Actual value |
|---|---|
| Internal Payment ID | `pay_1a19ea68b629709070b6a674605` |
| Provider | STRIPE |
| Provider Payment ID | `pi_t3_a9c3b9e18651` |
| Status | REFUNDED |
| Original amount | 15000 cents |
| Captured amount | 15000 cents |
| Refunded amount | 15000 cents |
| Version | 5 |

The final payment state and monetary totals matched the expected outcome.

## 7. Refund record verification

PostgreSQL contained two successful refund records associated with the payment.

| Refund | Amount |
|---|---:|
| Partial refund | 4000 cents |
| Remaining refund | 11000 cents |
| **Total refunded** | **15000 cents** |

The cumulative refund amount matched the captured amount.

No over-refund was observed in the final payment record.

## 8. Inbox processing verification

The inbox records associated with the lifecycle processing were inspected.

Actual processing summary:

| Inbox status | Count |
|---|---:|
| COMPLETED | 8 |
| PENDING | 0 |
| PROCESSING | 0 |
| DEAD_LETTER | 0 |
| **Total** | **8** |

The six lifecycle-related inbox entries were all verified as:

- Status: `COMPLETED`
- Attempts: `0`
- Last error: `NULL`

The total inbox count was eight because the database also contained two other inbox records.

### Correlation limitation

A query using the provider event IDs against `inbox_messages.event_id` returned zero matching records.

The reason identified during inspection was that `inbox_messages.event_id` stores the internal webhook event ID rather than the provider's external event ID.

The six corresponding lifecycle inbox entries were verified through their event types and processing results. However, an exact provider-event-ID-to-inbox-row correlation was not independently established by that query.

This limitation is recorded rather than treating the query as successful correlation evidence.

## 9. Expected versus actual

| Verification | Expected | Actual |
|---|---|---|
| Payment creation | CREATED | CREATED |
| Authorization | AUTHORIZED | AUTHORIZED |
| Capture | CAPTURED | CAPTURED |
| Captured amount | 15000 cents | 15000 cents |
| First refund | 4000 cents | 4000 cents |
| Partial refund status | PARTIALLY_REFUNDED | PARTIALLY_REFUNDED |
| Second refund | 11000 cents | 11000 cents |
| Cumulative refund | 15000 cents | 15000 cents |
| Final payment status | REFUNDED | REFUNDED |
| Late failure event | Must not reverse REFUNDED | Remained REFUNDED |
| Refund records | Two | Two |
| Lifecycle inbox processing | COMPLETED | COMPLETED |
| Lifecycle inbox attempts | 0 | 0 |
| Dead-letter messages | 0 | 0 |

## 10. Engineering observations and limitations

1. The test validates the implemented payment lifecycle and refund behavior for the tested Stripe event sequence.

2. Refund amounts are accumulated and checked against the captured amount. The service prevents cumulative refunds from exceeding the captured amount.

3. Duplicate refund identifiers are skipped by the processing implementation.

4. The test verifies a partial refund followed by a remaining-balance refund, resulting in a fully refunded payment.

5. The late failure event did not reverse the terminal `REFUNDED` state.

6. The state machine does not use provider timestamps to establish event ordering. This test does not prove general out-of-order event reconciliation.

7. Exact provider event ID to inbox row correlation was not independently demonstrated because the inbox stores the internal webhook event ID.

8. This is a local integration test. It does not establish behavior against live Stripe APIs or prove live provider settlement.

9. The test does not establish production-scale payment throughput or concurrency guarantees.

## 11. Final result

**TEST 3: PASS**

Evidence establishes that:

- The payment progressed through CREATED, AUTHORIZED, and CAPTURED.
- A partial refund of 4000 cents was processed.
- A second refund of 11000 cents completed the refund.
- Two refund records were persisted.
- The final refunded amount equaled the captured amount of 15000 cents.
- The payment remained REFUNDED after a late failure event.
- The lifecycle inbox entries completed without retries or processing errors.

The tested payment lifecycle and refund-processing path behaved as expected.

---

Test: 3 — Payment Lifecycle and Refund Processing  
Project: LedgerSync  
Evidence type: Local integration / database verification

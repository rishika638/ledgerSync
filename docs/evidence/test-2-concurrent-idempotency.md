# Test 2 — Concurrent Duplicate Webhook Delivery

**Status: PASS — tested local 10-request scenario**

## 1. Objective

Verify that ten simultaneous deliveries of the same Stripe webhook do not create duplicate webhook, inbox, or payment records.

## 2. Environment

- Application: LedgerSync Spring Boot
- Database: PostgreSQL 16.15
- Shell: PowerShell 5.1
- Concurrency mechanism: Start-Job
- Simultaneous requests: 10
- Provider: Stripe

## 3. Test identifiers

| Field | Value |
|---|---|
| Provider | STRIPE |
| Event ID | evt_concurrent_388d886a17b14eceb7be8c03423f8df3 |
| Payment ID | pi_concurrent_1eac91eb61464ee7b7897a5a78216626 |
| Event type | payment_intent.succeeded |
| Currency | USD |
| Amount | 25000 cents |

Ten simultaneous requests used the same event ID, payment ID, payload, and valid Stripe signature.

## 4. Baseline database state

| Table | Before |
|---|---:|
| webhook_events | 1 |
| inbox_messages | 1 |
| payment_transactions | 1 |

These were the pre-existing records from the earlier webhook test.

## 5. Actual API response distribution

| Result | Count |
|---|---:|
| HTTP 200 | 10 |
| ACCEPTED | 1 |
| DUPLICATE | 9 |
| HTTP 500 | 0 |

All responses returned the same internal event ID.

## 6. PostgreSQL verification

| Record / field | Actual |
|---|---|
| Webhook rows for test event | 1 |
| Inbox rows for test event | 1 |
| Payment rows for test payment | 1 |
| Webhook status | INGESTED |
| Inbox status | COMPLETED |
| Inbox attempts | 0 |
| Payment status | CAPTURED |
| Payment amount | 25000 |
| Captured amount | 25000 |

## 7. Expected versus actual

| Verification | Expected | Actual |
|---|---|---|
| Simultaneous requests | 10 | 10 |
| Accepted deliveries | 1 | 1 |
| Duplicate deliveries | 9 | 9 |
| HTTP server errors | 0 | 0 |
| Webhook records | 1 | 1 |
| Inbox records | 1 | 1 |
| Payment records | 1 | 1 |
| Final payment state | CAPTURED | CAPTURED |

## 8. Engineering observations and limitations

- The database uniqueness rule on provider and provider event ID provides persistence-level duplicate protection.
- The test used ten PowerShell background jobs.
- All ten requests returned HTTP 200.
- PostgreSQL contained one webhook, one inbox message, and one payment for the test identifiers.
- This is not a load test, stress benchmark, or throughput benchmark.
- It does not establish high-scale concurrency guarantees or multi-instance behavior.
- The original complete PowerShell job script and raw response transcript are not reproduced here.

## 9. Final result

**TEST 2: PASS — scoped to the local 10-request scenario.**

Ten simultaneous duplicate deliveries resulted in one accepted request and nine duplicate responses, with exactly one corresponding webhook, inbox message, and payment in PostgreSQL.

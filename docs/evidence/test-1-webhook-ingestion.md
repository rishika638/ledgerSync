# Test 1 — Webhook Ingestion, Signatures and Idempotency

**Status: PASS**

## 1. Objective

Verify valid Stripe webhook ingestion, duplicate delivery handling, signature validation, expired timestamp rejection, and PostgreSQL persistence.

## 2. Environment

- Application: LedgerSync Spring Boot
- Database: PostgreSQL 16.15
- Provider: Stripe
- Signature: HMAC-SHA256
- Timestamp tolerance: 300 seconds

## 3. Implementation inspected

- WebhookIngestionController.java
- WebhookIngestionService.java
- WebhookParser.java
- StripeSignatureVerifier.java
- RazorpaySignatureVerifier.java
- HmacUtils.java
- GlobalExceptionHandler.java

Endpoints:
- POST /api/v1/webhooks/stripe
- POST /api/v1/webhooks/razorpay
- POST /api/v1/webhooks/{provider}

Stripe signature verification uses HMAC-SHA256 over timestamp + raw body, supports multiple v1 signatures, uses constant-time comparison, and rejects expired timestamps.

Razorpay verification uses HMAC-SHA256 over the raw body. Stripe-style timestamp freshness validation is not claimed for Razorpay.

## 4. Test 1A — Valid webhook and duplicate delivery

| Field | Value |
|---|---|
| Provider | STRIPE |
| Event ID | evt_phase6_eed7dea17da5417495f77e0c3d0378f0 |
| Payment ID | pi_phase6_bd8c60ae6b80453fb97de0675194e8f1 |
| Event type | payment_intent.succeeded |
| Currency | USD |
| Amount | 15000 cents |

Actual API results:

| Request | HTTP | Result | Duplicate |
|---|---:|---|---|
| First delivery | 200 | ACCEPTED | false |
| Second delivery | 200 | DUPLICATE | true |

Both responses returned the same internal event ID.

PostgreSQL verification:

| Record | Actual |
|---|---|
| Webhook rows | 1 |
| Inbox rows | 1 |
| Payment rows | 1 |
| Webhook status | INGESTED |
| Inbox status | COMPLETED |
| Inbox attempts | 0/5 |
| Payment status | CAPTURED |
| Amount | 15000 |
| Captured amount | 15000 |

## 5. Test 1B — Invalid signature

An intentionally invalid Stripe signature was submitted.

Actual result:
- HTTP 401
- Error: INVALID_WEBHOOK_SIGNATURE

Database counts before and after:

| Table | Before | After |
|---|---:|---:|
| webhook_events | 1 | 1 |
| inbox_messages | 1 | 1 |
| payment_transactions | 1 | 1 |

No additional records were created.

## 6. Test 1C — Expired timestamp

A valid HMAC was generated using a timestamp approximately 10 minutes old.

Actual result:
- HTTP 401
- Error: INVALID_WEBHOOK_SIGNATURE
- Database counts unchanged.
- No additional records created.

## 7. Expected versus actual

| Verification | Expected | Actual |
|---|---|---|
| Valid webhook | Accepted | HTTP 200 |
| Duplicate delivery | Duplicate response | HTTP 200 DUPLICATE |
| Webhook persistence | One record | One |
| Inbox persistence | One record | One |
| Payment processing | Captured | CAPTURED |
| Invalid signature | Rejected | HTTP 401 |
| Expired timestamp | Rejected | HTTP 401 |
| Rejected request mutations | None | None observed |

## 8. Limitations

- Runtime evidence here covers Stripe.
- Razorpay implementation was inspected but is not claimed as runtime-tested by this test.
- This is local integration evidence, not an externally hosted Stripe integration.
- This test does not establish production-scale throughput.
- The original raw HTTP transcripts are not reproduced in this document.

## 9. Final result

**TEST 1: PASS**

Valid webhook ingestion, duplicate handling, invalid signature rejection, expired timestamp rejection, and PostgreSQL persistence behaved as expected.

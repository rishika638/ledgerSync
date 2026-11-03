# Test 6 — Reconciliation and Audit Verification

**Status: PASS**
**Environment:** Local LedgerSync application + PostgreSQL

## 1. Objective

Verify provider reconciliation runs, discrepancy detection/idempotency, manual discrepancy resolution, and audit persistence.

## 2. Existing reconciliation evidence

Initial database baseline:
- Reconciliation runs: 116
- Existing discrepancies: 3

Latest observed Stripe and Razorpay runs:
- Both COMPLETED.
- Stripe remote records checked: 2.
- Razorpay remote records checked: 1.
- Latest observed discrepancy count: 0.

The three pre-existing discrepancies were:
- Two Stripe MISSING_IN_LOCAL discrepancies.
- One Razorpay MISSING_IN_LOCAL discrepancy.
- All three were OPEN and CRITICAL.

The mock Stripe provider contains deterministic fixtures:
- pi_stripe_demo_001 — 10,000 cents, CAPTURED.
- pi_stripe_demo_missing_webhook — 7,500 cents, CAPTURED.

These are mock-provider fixtures, not live Stripe API records.

## 3. Reconciliation implementation verified

The reconciliation service:
- Fetches provider records for the last seven days.
- Checks for missing local payments.
- Detects status mismatches.
- Detects captured amount mismatches.
- Detects unexpected refunds.
- Detects stale CREATED/AUTHORIZED payments older than 24 hours.
- Avoids creating duplicate OPEN or INVESTIGATING discrepancies for the same provider payment and discrepancy type.

Existing unit tests cover MISSING_IN_LOCAL, STATUS_MISMATCH, active-discrepancy idempotency, STALE_UNRESOLVED, and discrepancy resolution/audit invocation.

## 4. Isolated resolution test

A synthetic discrepancy was inserted without modifying the three pre-existing demo discrepancies.

Test discrepancy:
- ID: dsc_test6_manual_001
- Provider: STRIPE
- Provider payment ID: pi_test6_manual_001
- Type: MISSING_IN_LOCAL
- Severity: CRITICAL
- Initial status: OPEN
- Evidence: Test 6 isolated resolution and audit verification

The insert returned:
`INSERT 0 1`

The admin-authenticated endpoint was called:

`POST /api/v1/reconciliation/discrepancies/dsc_test6_manual_001/resolve`

Request:
- status: RESOLVED
- resolution notes: Test 6 synthetic discrepancy manually resolved to verify reconciliation resolution and audit persistence.

The API returned:
- ID: dsc_test6_manual_001
- Status: RESOLVED
- Resolved by: admin
- Resolution notes populated
- resolvedAt populated
- updatedAt populated

## 5. PostgreSQL verification

The discrepancy row was queried after the API call.

Observed:
- status = RESOLVED
- resolved_by = admin
- resolution_notes persisted
- resolved_at populated

The corresponding audit row was also found:

- Audit ID: aud_1a1a31f5cb88feafc5b9e5f87f3
- Action: RESOLVE_DISCREPANCY
- Actor: admin
- Resource type: DISCREPANCY
- Resource ID: dsc_test6_manual_001

Before state:
`status=OPEN,resolvedBy=null`

After state:
`status=RESOLVED,resolvedBy=admin,notes=Test 6: Synthetic discrepancy manually resolved to verify reconciliation resolution and audit persistence.`

The audit row was persisted in PostgreSQL.

## 6. Security configuration observed

- Reconciliation run endpoint requires OPERATOR or ADMIN.
- Discrepancy resolution endpoint requires ADMIN.
- General API read operations require OPERATOR or ADMIN.
- API key authentication maps the admin key to ROLE_ADMIN and ROLE_OPERATOR.

## 7. Scope and limitations

- Provider reconciliation was exercised against local mock provider clients, not real Stripe or Razorpay APIs.
- The isolated discrepancy was inserted directly into PostgreSQL to test the actual resolution API and audit persistence without modifying pre-existing discrepancies.
- The three original demo discrepancies were left untouched.
- The latest observed reconciliation runs had zero local records checked; this test does not establish reconciliation coverage against a populated set of matching local provider payments.
- No claim is made here about live provider settlement correctness.

## 8. Result

**PASS** — Reconciliation run history and discrepancy behavior were inspected; the isolated discrepancy was successfully resolved through the authenticated application endpoint, and its corresponding audit event was verified in PostgreSQL.

# Test 7 — Security and Access Control

**Status: PASS**
**Environment:** Local LedgerSync application, Spring Security, PostgreSQL

## 1. Objective

Verify API-key authentication, role-based authorization, administrative access restrictions, and webhook signature enforcement.

## 2. Security configuration inspected

- Stateless Spring Security configuration.
- API key header: X-API-Key.
- API key authentication maps credentials to ROLE_INGESTION, ROLE_OPERATOR, and ROLE_ADMIN.
- Admin receives both ROLE_ADMIN and ROLE_OPERATOR.
- Webhook POST routes are permitted through API-key authorization and independently protected by provider signature verification.
- Reconciliation run requires OPERATOR or ADMIN.
- Discrepancy resolution requires ADMIN.
- General API routes require OPERATOR or ADMIN.

## 3. Live authentication probes

GET /api/v1/payments:

| Credential | HTTP status | Result |
|---|---:|---|
| No API key | 403 | Rejected |
| Invalid API key | 403 | Rejected |
| Ingestion key | 403 | Rejected |
| Operator key | 200 | Allowed |

## 4. Live authorization probes

Operator attempted to resolve:
`POST /api/v1/reconciliation/discrepancies/dsc_test6_manual_001/resolve`

Result: HTTP 403 Forbidden.

The existing discrepancy remained RESOLVED; the unauthorized request did not change it.

Operator triggered:
`POST /api/v1/reconciliation/run?provider=STRIPE`

Result: COMPLETED.

Run ID:
`rec_1a1a32252b042ad552cd2598d46`

Admin discrepancy resolution was successfully exercised in Test 6:
- Endpoint returned HTTP 200.
- Discrepancy status changed from OPEN to RESOLVED.
- Actor recorded as admin.

## 5. Webhook signature enforcement

A Stripe webhook request was sent without an API key and with an intentionally invalid Stripe signature.

Endpoint:
`POST /api/v1/webhooks/stripe`

Result: HTTP 401 Unauthorized.

This confirms the webhook route passes API-key authorization but invalid provider signatures are rejected by webhook signature verification.

This probe did not test acceptance of a valid signature; valid signature acceptance was covered separately in Test 1.

## 6. Automated security test coverage

Existing SecurityAccessTest includes:
- Missing API key rejected.
- Operator payment-read access allowed.
- Operator discrepancy-resolution access forbidden.
- Admin discrepancy-resolution access allowed.

## 7. Scope and limitations

- Tests were performed against the local application and configured development credentials.
- No production credentials or production environment were used.
- The webhook probe intentionally used an invalid signature; it does not independently prove valid-signature acceptance.
- The HTTP 403 response is the observed authorization behavior; no 401 behavior is claimed for protected API routes.

## 8. Result

**PASS** — Missing, invalid, and insufficiently privileged credentials were rejected; operator and admin permissions behaved as configured; webhook signature verification rejected an invalid signature without requiring an API key.

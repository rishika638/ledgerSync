# Test 8 — Observability

**Status: PASS WITH LIMITATION**

## 1. Objective
Verify application health indicators, custom Micrometer metrics, Prometheus export, and HTTP correlation/trace propagation.

## 2. Application Health
GET /actuator/health returned overall status UP.

Verified components:
- PostgreSQL: UP
- Redis: UP (version 8.2.5)
- Liveness: UP
- Readiness: UP
- Disk space: UP

## 3. Custom Metrics
GET /actuator/metrics exposed all six custom counters.

Observed values:

| Metric | Value |
|---|---:|
| ledgersync.webhooks.received | 23 |
| ledgersync.webhooks.duplicates | 10 |
| ledgersync.webhooks.invalid_signature | 3 |
| ledgersync.inbox.processed | 10 |
| ledgersync.inbox.dead_letters | 2 |
| ledgersync.reconciliation.discrepancies | 0 |

## 4. Prometheus Export
GET /actuator/prometheus successfully exposed all six custom counters.

Prometheus names:
- ledgersync_webhooks_received_total
- ledgersync_webhooks_duplicates_total
- ledgersync_webhooks_invalid_signature_total
- ledgersync_inbox_processed_total
- ledgersync_inbox_dead_letters_total
- ledgersync_reconciliation_discrepancies_total

Exported values matched the Actuator metrics endpoint.

## 5. Correlation ID and Trace Context
A GET request to /api/v1/payments was sent with:
- X-Correlation-Id: test8-correlation-2026
- Operator API authentication

Observed:
- HTTP 200
- Response X-Correlation-Id preserved the supplied value.
- Response traceparent was generated and returned.

CorrelationIdFilter also places correlationId, traceId, and spanId in SLF4J MDC and removes them in a finally block.

## 6. Logging Limitation
No explicit Logback configuration file or MDC pattern was found in application resources or application.yml.

Therefore, MDC population and cleanup are verified by source inspection, but actual inclusion of correlation and trace fields in emitted console logs has not been demonstrated.

## 7. Conclusion
Application health, custom metric registration, metric values, Prometheus exposition, and HTTP correlation/trace propagation passed the observed checks.

MDC-to-console-log formatting remains unverified and is recorded as a limitation rather than claimed as a completed test.

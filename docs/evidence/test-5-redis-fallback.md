# Test 5 — Redis Outage and PostgreSQL Fallback

**Status: PASS**

## 1. Objective

Verify that LedgerSync continues webhook ingestion and asynchronous payment processing when Redis becomes unavailable, using PostgreSQL as the fallback coordination and persistence mechanism.

Also verify that Redis can be restarted and application connectivity recovers without resetting or deleting Redis data.

## 2. Test environment

- Application: Spring Boot, http://localhost:8080
- Database: PostgreSQL
- Queue persistence: PostgreSQL inbox_messages
- Coordination: OptionalRedisCoordinator
- Worker: InboxWorker
- Redis container: ledgersync-redis
- PostgreSQL container: ledgersync-postgres

Existing application and database were preserved throughout the test.

## 3. Initial database baseline

Before the test:

| Table | Existing records |
|---|---:|
| webhook_events | 9 |
| inbox_messages | 9 |
| payment_transactions | 3 |

Redis connectivity initially returned:

    PONG

Application health initially reported:

    status: UP
    db: UP
    redis: UP

## 4. Implementation inspected

### OptionalRedisCoordinator.java

The coordinator attempts to acquire a Redis lease using:

    redisTemplate.opsForValue().setIfAbsent(key, workerId, ttl)

If Redis is unavailable or an exception occurs, it:

- Logs Redis coordination unavailability.
- Updates its Redis health state.
- Returns true to allow PostgreSQL fallback.
- Relies on PostgreSQL row-level locking.

### InboxWorker.java

The worker:

- Polls inbox messages every 2 seconds by default.
- Attempts Redis coordination.
- Continues to process batches.
- Claims messages using PostgreSQL SKIP LOCKED.
- Processes messages through the payment processing service.

PostgreSQL remains the persistence layer and database-level concurrency protection.

## 5. Test preparation

A valid Stripe payment_intent.succeeded webhook was generated using HMAC-SHA256 signature verification.

### Preliminary event

Provider event ID:

    evt_redis_outage_20261104004547

Internal event ID:

    evt_1a1a2d6c903c8a6411c411996e2

Payment ID:

    pi_redis_outage_20261104004547

Amount:

    27500 cents USD

Ingestion response:

    status: ACCEPTED
    duplicate: false
    message: Event ingested successfully

PostgreSQL subsequently showed:

- Inbox: COMPLETED
- Attempts: 0/5
- Payment: CAPTURED
- Amount: 27500 cents
- Captured amount: 27500 cents

**Caveat:** This preliminary event was submitted before Redis was stopped. It is not counted as independent proof of outage fallback.

## 6. Redis outage simulation

Redis was stopped using:

    docker stop ledgersync-redis

Docker confirmed that the Redis container was no longer running.

PostgreSQL and the Spring Boot application were left running.

## 7. Fresh webhook during Redis outage

A second unique Stripe webhook was generated and submitted while Redis remained stopped.

### Test identifiers

| Field | Value |
|---|---|
| Provider event ID | evt_redis_down_20261104010139 |
| Internal event ID | evt_1a1a2e5515c09d46cfeec14fc08 |
| Payment ID | pi_redis_down_20261104010139 |
| Amount | 39000 cents |
| Currency | USD |
| Event type | payment_intent.succeeded |

Ingestion response:

    status: ACCEPTED
    duplicate: false
    message: Event ingested successfully

The event was accepted while Redis was unavailable.

## 8. PostgreSQL inbox verification

SQL executed:

    SELECT id, event_id, status, attempt_count,
           max_attempts, locked_by, last_error, updated_at
    FROM inbox_messages
    WHERE event_id = 'evt_1a1a2e5515c09d46cfeec14fc08';

Actual result:

| Field | Actual value |
|---|---|
| Inbox ID | inb_1a1a2e5515f6c975145dacf4f62 |
| Event ID | evt_1a1a2e5515c09d46cfeec14fc08 |
| Status | COMPLETED |
| Attempt count | 0 |
| Maximum attempts | 5 |
| Worker | worker-aeede3c4 |
| Last error | NULL / empty |

Updated timestamp:

    2026-11-03 19:31:41.808159+00

The message reached COMPLETED without retries or processing errors.

## 9. Payment verification

SQL executed:

    SELECT provider_payment_id, status, amount_cents,
           captured_amount_cents, refunded_amount_cents, currency
    FROM payment_transactions
    WHERE provider_payment_id = 'pi_redis_down_20261104010139';

Actual result:

| Field | Actual value |
|---|---|
| Provider payment ID | pi_redis_down_20261104010139 |
| Status | CAPTURED |
| Amount | 39000 |
| Captured amount | 39000 |
| Refunded amount | 0 |
| Currency | USD |

The payment was successfully captured while Redis remained unavailable.

Docker independently confirmed that the Redis container was still stopped during this verification.

## 10. Redis recovery

Redis was restarted using:

    docker start ledgersync-redis

After restart:

    docker exec ledgersync-redis redis-cli PING

Actual response:

    PONG

Docker status:

    ledgersync-redis   Up 32 seconds (healthy)

## 11. Application recovery verification

Actuator endpoint:

    GET /actuator/health

Actual response summary:

    status: UP
    db.status: UP
    redis.status: UP
    livenessState.status: UP
    readinessState.status: UP

Redis version reported by Actuator:

    8.2.5

PostgreSQL and Redis connectivity were both healthy after recovery.

## 12. Expected versus actual

| Verification | Expected | Actual |
|---|---|---|
| Initial Redis connectivity | PONG | PONG |
| Redis outage | Container stopped | Confirmed |
| Webhook ingestion during outage | Accepted | ACCEPTED |
| Inbox processing | COMPLETED | COMPLETED |
| Retry attempts | 0 | 0 |
| Payment processing | CAPTURED | CAPTURED |
| Captured amount | 39000 | 39000 |
| Redis restart | Successful | Successful |
| Redis PING after restart | PONG | PONG |
| Application health after recovery | UP | UP |
| PostgreSQL health | UP | UP |

## 13. Limitations and engineering observations

1. This was a controlled local outage test, not a production-scale resilience or load test.

2. The test demonstrates successful PostgreSQL fallback for the tested single-worker scenario.

3. PostgreSQL SKIP LOCKED is the database-level concurrency protection. Redis lease acquisition must not be interpreted as an absolute guarantee against duplicate worker execution.

4. The worker currently calls processBatch() even when Redis coordination returns false. Redis coordination therefore does not act as a strict global worker exclusion mechanism.

5. The exact Spring Boot log message indicating Redis connection restoration was not captured. Recovery was independently verified using Actuator health and Redis PING.

6. No PostgreSQL reset, Redis flush, volume deletion or existing record deletion was performed.

7. The test does not establish behavior under simultaneous multi-instance failures or large-scale traffic.

## 14. Final result

**TEST 5: PASS**

Evidence establishes that:

- A fresh webhook was accepted while Redis was stopped.
- PostgreSQL inbox processing completed successfully.
- Payment processing reached CAPTURED.
- No retries or processing errors occurred.
- Redis was restarted successfully.
- Redis connectivity and application health recovered.

The tested PostgreSQL fallback path worked as intended.

---

Test date: 2026-11-04 (local test session)
Project: LedgerSync
Evidence type: Local integration / controlled infrastructure outage test

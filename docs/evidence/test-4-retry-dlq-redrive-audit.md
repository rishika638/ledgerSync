# Test 4 — Retry, Dead-Letter Queue, Redrive and Audit Logging

**Status: PASS**

## 1. Objective

Verify that LedgerSync handles repeatedly failing inbox messages through its retry mechanism, moves messages to the dead-letter queue after retry exhaustion, exposes dead-letter messages to authorized operators, supports manual redrive, and records the operator action in the audit log.

The test validates:

- Retry attempt tracking.
- Maximum retry enforcement.
- Dead-letter transition.
- Dead-letter queue retrieval.
- Operator authentication and authorization for queue management.
- Manual redrive of a dead-letter message.
- Audit persistence for the redrive operation.

## 2. Test environment

- Application: LedgerSync Spring Boot application
- Database: PostgreSQL
- Queue persistence: `inbox_messages`
- Worker: `InboxWorker`
- Queue management: `QueueManagementController`
- Redrive service: `DeadLetterRedriveService`
- Audit persistence: `audit_events`
- Authentication: API key using `X-API-Key`
- Authorized role: OPERATOR

Existing application and database state were preserved.

## 3. Implementation inspected

### InboxWorker.java

The inbox worker processes pending messages asynchronously.

Inspected behavior:

- Polls for pending messages on a scheduled interval.
- Claims messages in batches.
- Uses PostgreSQL `FOR UPDATE SKIP LOCKED` for database-level message claiming.
- Parses the stored webhook payload.
- Invokes payment processing.
- Marks successfully processed messages as `COMPLETED`.
- Increments the attempt count when processing fails.
- Returns a failed message to `PENDING` when retry attempts remain.
- Moves the message to `DEAD_LETTER` when the maximum attempt count is exhausted.
- Applies retry backoff with jitter.
- Uses a maximum-attempt limit for each message.

The implementation also includes abandoned-job recovery for messages left in `PROCESSING` beyond the configured recovery threshold.

### DeadLetterRedriveService.java

The redrive service handles operator-requested recovery of dead-letter messages.

Inspected behavior:

- Accepts messages only when their status is `DEAD_LETTER`.
- Changes the status back to `PENDING`.
- Resets the attempt count to zero.
- Makes the message eligible for processing again.
- Clears the previous worker lock information.
- Records the redrive operation through the audit service.

### QueueManagementController.java

The queue management controller exposes endpoints for inbox inspection and dead-letter operations.

Relevant endpoints:

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/v1/queue/inbox?status=...` | List inbox messages |
| GET | `/api/v1/queue/inbox/{id}` | Inspect an inbox message |
| GET | `/api/v1/queue/dead-letters` | List dead-letter messages |
| POST | `/api/v1/queue/redrive/{id}` | Redrive a specific message |
| POST | `/api/v1/queue/redrive/all?limit=50` | Redrive a batch of messages |

### SecurityConfig.java

Queue redrive operations are protected.

The security configuration requires an `OPERATOR` or `ADMIN` role for redrive operations.

The API key authentication filter reads the `X-API-Key` header and resolves the configured role.

The operator key is supplied through the application's configured environment or application configuration. Secret values are intentionally excluded from this evidence document.

## 4. Test message preparation

A dedicated poison message was inserted into PostgreSQL to test retry exhaustion and dead-letter behavior.

The payload was intentionally malformed JSON so that the worker would consistently fail while parsing it.

This was a controlled failure-injection test. It was not a valid provider payment event.

### Test identifiers

| Field | Value |
|---|---|
| Inbox ID | `inbox_dlq_e85c8e6b6b4f4844addf3c100c0677a0` |
| Parent internal event ID | `evt_dlq_e85c8e6b6b4f4844addf3c100c0677a0` |
| Provider event ID | `evt_dlq_test_e85c8e6b6b4f4844addf3c100c0677a0` |
| Provider | STRIPE |
| Event type | `test.poison_event` |
| Initial status | PENDING |
| Initial attempts | 0 |
| Maximum attempts | 2 |
| Payload | `{invalid-json` |

A parent webhook event was inserted to satisfy the foreign-key relationship between `inbox_messages.event_id` and `webhook_events.id`.

The setup was isolated to the dedicated test message.

## 5. Test 4A — Retry exhaustion and dead-letter transition

### Expected behavior

The worker should attempt to process the malformed payload.

Because the payload is invalid JSON, parsing should fail consistently.

After the configured maximum of two attempts is exhausted:

- The attempt count should reach 2.
- The message should transition to `DEAD_LETTER`.
- The last processing error should be recorded.
- Worker lock fields should be cleared.

### Actual PostgreSQL result

The database showed:

| Field | Actual value |
|---|---|
| Inbox ID | `inbox_dlq_e85c8e6b6b4f4844addf3c100c0677a0` |
| Status | DEAD_LETTER |
| Attempt count | 2 |
| Maximum attempts | 2 |
| Last error | JsonParseException |
| Lock fields | NULL |

The recorded error indicated that the JSON parser expected a double quote to start a field name.

The message reached the dead-letter state after exhausting its configured attempts.

<badge>PASS</badge>

## 6. Test 4B — Dead-letter queue retrieval

The operator-facing dead-letter endpoint was called:

    GET /api/v1/queue/dead-letters

Authentication was supplied through the `X-API-Key` header using the configured operator credential.

The credential was kept out of command output and evidence.

### Actual API response

- HTTP status: `200`
- `totalElements`: 1
- Returned message ID: `inbox_dlq_e85c8e6b6b4f4844addf3c100c0677a0`
- Status: `DEAD_LETTER`
- Attempts: `2/2`
- Error details: stored JSON parsing failure

The dedicated poison message was returned by the dead-letter listing endpoint.

<badge>PASS</badge>

## 7. Test 4C — Manual redrive

The operator initiated a redrive request:

    POST /api/v1/queue/redrive/inbox_dlq_e85c8e6b6b4f4844addf3c100c0677a0

### Expected behavior

The redrive service should:

- Accept the dead-letter message.
- Reset its attempt count.
- Change its status to `PENDING`.
- Clear previous lock information.
- Make it eligible for worker processing again.
- Record the operator action in the audit log.

### Actual API response

The endpoint returned:

- `redrivenCount`: 1
- Message ID: `inbox_dlq_e85c8e6b6b4f4844addf3c100c0677a0`
- Message: `Message successfully re-queued for processing`

### PostgreSQL observation after redrive

The subsequent database query showed:

| Field | Actual value |
|---|---|
| Status | PENDING |
| Attempt count | 1 |
| Maximum attempts | 2 |
| Lock fields | NULL |

The attempt count was observed as 1 rather than 0 because the redrive operation reset it to zero, after which the asynchronous worker picked up the message and attempted to process it again. The malformed JSON caused another processing failure and incremented the count.

The original poison payload remained invalid, so successful business processing was not expected.

This verifies that redrive re-queued the message and that the worker attempted processing again. It does not demonstrate successful recovery of a corrected provider event.

<badge>PASS</badge>

## 8. Test 4D — Audit log verification

The audit record created by the redrive operation was inspected in PostgreSQL.

### Actual audit record

| Field | Actual value |
|---|---|
| Audit ID | `aud_1a19eb0cec3c62f2b06e9369ae9` |
| Action | `REDRIVE_DEAD_LETTER` |
| Actor | `operator` |
| Resource type | `INBOX_MESSAGE` |
| Resource ID | `inbox_dlq_e85c8e6b6b4f4844addf3c100c0677a0` |
| Correlation ID | `791fd283-9d5a-4879-8b96-dd92185753b8` |

### Before state

    status=DEAD_LETTER,attempts=2

### After state

    status=PENDING,attempts=0

The audit record preserved the state transition performed by the redrive operation.

The before/after values represent the redrive service's transition itself. The worker's subsequent retry occurred after this operation and explains the later observed attempt count of 1.

<badge>PASS</badge>

## 9. Expected versus actual

| Verification | Expected | Actual |
|---|---|---|
| Poison message setup | Isolated test message | Created |
| Initial status | PENDING | PENDING |
| Maximum attempts | 2 | 2 |
| Invalid JSON processing | Failure | JsonParseException |
| Retry exhaustion | Attempts reach 2 | 2 |
| Final retry-exhaustion status | DEAD_LETTER | DEAD_LETTER |
| Error persistence | Error recorded | Recorded |
| Dead-letter API | HTTP 200 | HTTP 200 |
| Returned message count | 1 | 1 |
| Operator redrive | Accepted | Accepted |
| Redrive response count | 1 | 1 |
| Redrive state transition | DEAD_LETTER to PENDING | Verified |
| Redrive attempt reset | 0 | Verified in audit before-state/after-state |
| Worker reprocessing | Message attempted again | Observed |
| Audit action | REDRIVE_DEAD_LETTER | Persisted |
| Audit actor | operator | operator |
| Audit resource | Exact inbox message | Exact inbox message |

## 10. Engineering observations and limitations

1. The test verifies retry exhaustion using a deliberately malformed inbox payload.

2. The worker persisted the parsing failure and transitioned the message to `DEAD_LETTER` after two attempts.

3. The dead-letter listing endpoint returned the expected message to an authenticated operator.

4. The redrive endpoint accepted the dead-letter message and re-queued it.

5. The asynchronous worker picked up the redriven message and attempted processing again.

6. Because the payload remained malformed, the message failed again. This is expected for this poison-message test.

7. The test proves redrive and reprocessing behavior. It does not prove that the underlying business operation can recover successfully without correcting the original failure condition.

8. The audit record independently confirms the redrive action, actor, resource ID, correlation ID, and before/after state.

9. The redrive audit record shows the immediate service transition to `PENDING` with attempts reset to zero. The worker's later retry is a separate operation.

10. The test does not establish behavior under production-scale queue traffic or multi-instance failure conditions.

11. The test does not demonstrate successful redrive of a valid provider event after an external dependency or data problem has been repaired.

12. No existing PostgreSQL records or database volumes were intentionally reset as part of the test.

## 11. Final result

**TEST 4: PASS**

Evidence establishes that:

- A deliberately malformed inbox message was processed by the worker.
- The retry count reached the configured maximum of two.
- The message transitioned to `DEAD_LETTER`.
- The processing error was persisted.
- An authenticated operator retrieved the dead-letter message.
- The operator successfully initiated redrive.
- The message was re-queued and picked up by the worker again.
- The redrive operation reset the attempt count and changed the status to `PENDING`.
- The audit log persisted the redrive action with actor, resource, correlation ID, and before/after state.

The tested retry, dead-letter, redrive, and audit paths behaved as expected.

The poison message failed again because its malformed payload was intentionally left unchanged. Therefore, this test demonstrates redrive execution, not successful business recovery.

---

Test: 4 — Retry, Dead-Letter Queue, Redrive and Audit Logging  
Project: LedgerSync  
Evidence type: Local integration / controlled failure injection / database and API verification

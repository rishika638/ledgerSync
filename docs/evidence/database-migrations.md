# LedgerSync — Database Migration and Schema Verification

**Verification date:** November 3, 2026  
**Database:** PostgreSQL 16.15  
**Database name:** `ledgersync`  
**Migration tool:** Flyway  
**Migration versions:** V1 and V2  
**Verification result:** PASS

---

## 1. Overview

LedgerSync uses PostgreSQL as its primary relational database and Flyway for version-controlled database schema management.

The schema is managed through two SQL migrations:

- V1 — Initial schema
- V2 — Indexes and constraints

Hibernate is configured with:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

This configuration makes Hibernate validate the application entity mappings against the database schema rather than automatically modifying the schema.

Schema creation and evolution remain under Flyway's control.

---

## 2. Database Environment

| Component | Verified configuration |
|---|---|
| Database | PostgreSQL |
| PostgreSQL version | 16.15 |
| Docker image | postgres:16-alpine |
| Database name | ledgersync |
| Database user | postgres |
| Migration tool | Flyway |
| Hibernate DDL mode | validate |
| Persistent PostgreSQL volume | ledgersync_postgres_data |

The PostgreSQL version, database name and current database user were obtained from the database verification output.

The PostgreSQL database runs in the Docker container:

```text
ledgersync-postgres
```

Its persistent Docker volume is:

```text
ledgersync_postgres_data
```

---

## 3. Flyway Migration Files

### V1 — Initial Schema

File:

```text
src/main/resources/db/migration/V1__init_schema.sql
```

Creates the eight application tables.

| Table | Responsibility |
|---|---|
| webhook_events | Stores incoming provider webhook events |
| inbox_messages | Durable asynchronous message processing |
| outbox_messages | Stores outgoing messages for reliable delivery |
| payment_transactions | Stores payment transaction records |
| refunds | Stores refund records |
| audit_events | Stores audit event history |
| reconciliation_runs | Tracks reconciliation executions |
| reconciliation_discrepancies | Stores reconciliation mismatches |

The schema also includes the Flyway metadata table:

```text
flyway_schema_history
```

### V2 — Constraints and Indexes

File:

```text
src/main/resources/db/migration/V2__add_constraints_and_indexes.sql
```

Adds database-level constraints and indexes to enforce important financial invariants and support application queries.

#### Financial constraints

- Payment amounts must be non-negative.
- Captured amount cannot exceed the payment amount.
- Refunded amount cannot exceed captured amount.
- Refund amounts must be positive.

#### Status constraints

- Payment status is restricted to supported lifecycle states.
- Refund status is restricted to supported states.
- Webhook status is restricted to supported states.
- Inbox message status is restricted to supported processing states.

#### Uniqueness and referential integrity

- Unique provider and provider-payment identifier combination.
- Unique payment idempotency key.
- Unique provider and provider-event identifier combination.
- Unique refund identifier within a payment.
- Foreign-key relationships between inbox messages, webhook events, reconciliation discrepancies, reconciliation runs, refunds and payments.

---

## 4. PostgreSQL Migration History Verification

The PostgreSQL `flyway_schema_history` table was inspected.

Verification query:

```sql
SELECT installed_rank, version, description, type, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

Observed result:

| Installed rank | Version | Description | Type | Success |
|---:|---:|---|---|---|
| 1 | 1 | init schema | SQL | true |
| 2 | 2 | indexes and constraints | SQL | true |

**Result:** Both migrations are recorded as successfully applied.

No failed migration entry was shown in the inspected migration history.

---

## 5. Database Table Verification

The PostgreSQL schema was inspected to identify the existing tables.

Observed application tables:

1. audit_events
2. inbox_messages
3. outbox_messages
4. payment_transactions
5. reconciliation_discrepancies
6. reconciliation_runs
7. refunds
8. webhook_events

Additional metadata table:

9. flyway_schema_history

| Verification | Observed result |
|---|---:|
| Application tables | 8 |
| Flyway metadata tables | 1 |
| Total tables | 9 |

**Result:** All eight expected application tables and the Flyway metadata table were present.

---

## 6. Database Constraint Verification

The PostgreSQL constraint catalog was inspected.

Observed result:

**24 constraints across the inspected schema.**

### Constraint breakdown

| Constraint type | Count |
|---|---:|
| Primary keys | 9 |
| Foreign keys | 3 |
| Check constraints | 8 |
| Unique constraints | 4 |
| **Total** | **24** |

### 6.1 Primary keys

Primary keys were present on all eight application tables and the Flyway metadata table.

### 6.2 Foreign keys

| Table | Constraint | Relationship |
|---|---|---|
| inbox_messages | inbox_messages_event_id_fkey | References webhook_events(id) |
| reconciliation_discrepancies | reconciliation_discrepancies_run_id_fkey | References reconciliation_runs(id) |
| refunds | refunds_payment_id_fkey | References payment_transactions(id) |

The inspected foreign-key definitions included:

- Inbox-to-webhook relationship with `ON DELETE CASCADE`.
- Discrepancy-to-reconciliation-run relationship with `ON DELETE CASCADE`.
- Refund-to-payment relationship with `ON DELETE RESTRICT`.

### 6.3 Check constraints

| Table | Constraint | Enforced condition |
|---|---|---|
| inbox_messages | chk_inbox_status | Allowed inbox statuses |
| payment_transactions | chk_captured_lte_authorized | Captured amount cannot exceed payment amount |
| payment_transactions | chk_payment_amounts_non_negative | Payment, captured and refunded amounts cannot be negative |
| payment_transactions | chk_payment_status | Allowed payment statuses |
| payment_transactions | chk_refunded_lte_captured | Refunded amount cannot exceed captured amount |
| refunds | chk_refund_amount_positive | Refund amount must be positive |
| refunds | chk_refund_status | Allowed refund statuses |
| webhook_events | chk_webhook_status | Allowed webhook statuses |

### 6.4 Unique constraints

| Table | Constraint | Enforced uniqueness |
|---|---|---|
| payment_transactions | payment_transactions_idempotency_key_key | idempotency_key |
| payment_transactions | uk_payment_provider_payment_id | provider, provider_payment_id |
| refunds | uk_refund_provider_refund_id | payment_id, provider_refund_id |
| webhook_events | uk_webhook_provider_event | provider, provider_event_id |

**Result:** All 24 inspected constraints were present in PostgreSQL.

---

## 7. Database Index Verification

The PostgreSQL index catalog was inspected.

Observed result:

**23 indexes across the inspected schema.**

### 7.1 Audit indexes

| Index | Indexed columns |
|---|---|
| audit_events_pkey | id |
| idx_audit_correlation | correlation_id |
| idx_audit_resource | resource_type, resource_id, created_at DESC |

### 7.2 Flyway metadata indexes

| Index | Indexed columns |
|---|---|
| flyway_schema_history_pk | installed_rank |
| flyway_schema_history_s_idx | success |

### 7.3 Inbox processing indexes

| Index | Indexed columns / condition |
|---|---|
| idx_inbox_dead_letter | updated_at DESC, where status = DEAD_LETTER |
| idx_inbox_pending_poll | scheduled_at, created_at, where status = PENDING |
| idx_inbox_stuck_recovery | locked_at, where status = PROCESSING |
| inbox_messages_pkey | id |

These indexes support pending-message retrieval, dead-letter queries and abandoned-job recovery.

### 7.4 Outbox indexes

| Index | Indexed columns |
|---|---|
| outbox_messages_pkey | id |

### 7.5 Payment indexes

| Index | Indexed columns |
|---|---|
| idx_payment_customer | customer_id |
| idx_payment_status_created | status, created_at DESC |
| payment_transactions_idempotency_key_key | idempotency_key |
| payment_transactions_pkey | id |
| uk_payment_provider_payment_id | provider, provider_payment_id |

### 7.6 Reconciliation discrepancy indexes

| Index | Indexed columns |
|---|---|
| idx_discrepancy_run | run_id |
| idx_discrepancy_status | status, severity |
| reconciliation_discrepancies_pkey | id |

### 7.7 Reconciliation run indexes

| Index | Indexed columns |
|---|---|
| reconciliation_runs_pkey | id |

### 7.8 Refund indexes

| Index | Indexed columns |
|---|---|
| refunds_pkey | id |
| uk_refund_provider_refund_id | payment_id, provider_refund_id |

### 7.9 Webhook indexes

| Index | Indexed columns |
|---|---|
| uk_webhook_provider_event | provider, provider_event_id |
| webhook_events_pkey | id |

**Result:** All 23 indexes appeared in the inspected PostgreSQL index catalog.

Note: Primary-key and unique constraints are backed by indexes. Therefore, the index count includes indexes associated with those constraints.

---

## 8. Migration Verification Summary

| Verification | Observed result | Status |
|---|---|---|
| PostgreSQL connectivity | Database information returned | PASS |
| PostgreSQL version | 16.15 | PASS |
| Database name | ledgersync | PASS |
| Flyway V1 history | Success = true | PASS |
| Flyway V2 history | Success = true | PASS |
| Application tables | 8 | PASS |
| Total tables including Flyway | 9 | PASS |
| Database constraints | 24 | PASS |
| Database indexes | 23 | PASS |

---

## 9. Verification Commands

The following commands can be used to inspect the existing database without deleting its persistent data.

### Check Docker services

```powershell
docker compose ps
```

### Inspect PostgreSQL version and database

```powershell
docker compose exec postgres psql -U postgres -d ledgersync -c "SELECT version(), current_database(), current_user;"
```

### Inspect Flyway migration history

```powershell
docker compose exec postgres psql -U postgres -d ledgersync -c "SELECT installed_rank, version, description, type, success FROM flyway_schema_history ORDER BY installed_rank;"
```

### Inspect tables

```powershell
docker compose exec postgres psql -U postgres -d ledgersync -c "\dt"
```

### Count constraints

```powershell
docker compose exec postgres psql -U postgres -d ledgersync -c "SELECT COUNT(*) FROM pg_constraint;"
```

### Inspect constraint definitions

```powershell
docker compose exec postgres psql -U postgres -d ledgersync -c "SELECT conrelid::regclass AS table_name, conname, contype, pg_get_constraintdef(oid) AS definition FROM pg_constraint WHERE connamespace = 'public'::regnamespace ORDER BY table_name, conname;"
```

### Inspect indexes

```powershell
docker compose exec postgres psql -U postgres -d ledgersync -c "SELECT tablename, indexname, indexdef FROM pg_indexes WHERE schemaname = 'public' ORDER BY tablename, indexname;"
```

---

## 10. Persistence Configuration

PostgreSQL and Redis use separate persistent Docker volumes.

| Service | Persistent volume |
|---|---|
| PostgreSQL | ledgersync_postgres_data |
| Redis | ledgersync_redis_data |

Stopping and restarting the services does not intentionally delete their persistent volumes.

**Important:** Avoid the following command when preserving existing database data:

```powershell
docker compose down -v
```

This command can remove named volumes and destroy persisted database data.

---

## 11. Evidence Limitations

The November 3 database verification output establishes the state of the existing PostgreSQL schema and its Flyway history.

It confirms:

- PostgreSQL 16.15 was queried.
- Both Flyway migration versions were recorded as successful.
- Eight application tables were present.
- Twenty-four constraints were present.
- Twenty-three indexes were present.

However, this particular verification output is a database inspection transcript. It does not itself show a fresh Flyway migration execution, migration duration, or a new migration being applied.

The historical application startup verification established successful Flyway validation/application and application startup, but that should not be confused with the November 3 catalog inspection.

The automated 28-test suite used H2 in-memory mode, not PostgreSQL. Therefore, its passing result does not independently establish PostgreSQL-specific transaction or locking behavior.

Additional PostgreSQL runtime tests are documented separately.

---

## 12. Final Assessment

**Database migration and schema verification: PASS**

The inspected LedgerSync PostgreSQL database contains:

- 8 application tables.
- 1 Flyway metadata table.
- 2 successfully recorded Flyway migrations.
- 24 database constraints.
- 23 indexes.

The database schema reflects the expected initial schema and subsequent constraints/indexes migration.

This verification demonstrates successful schema migration history and database catalog state. It does not, by itself, establish production-scale performance, high availability, or complete PostgreSQL integration coverage.
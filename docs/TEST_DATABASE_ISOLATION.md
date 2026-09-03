# Test / Production Database Isolation

Status: Operational convention, established 2026-09-02 in direct response to a real incident. Not a Product Decision, not an ADR — this document records a testing-infrastructure convention, not a change to any domain, capability, or business rule. See [PIOS_REALITY_AUDIT.md](../PIOS_REALITY_AUDIT.md) Section 19 for the full incident record.

## What happened

Every backend module's PostgreSQL-backed integration tests connected to a hardcoded `internal object PostgreSQLTestDatabase` (one file per module, `src/test/kotlin/.../persistence/PostgreSQLTestDatabase.kt`), and that object's connection URL was the module's own **production** database name — `pios_driver_management`, `pios_dispatch`, `pios_identity`, `pios_order_management`, `pios_passenger_experience`, `pios_network_management` — on the same local Postgres instance production itself runs against. Nothing distinguished "a developer's own test run" from "the real pilot" at the connection-string level.

On 2026-09-02, a routine `./gradlew build` (backend-wide, launched for read-only verification during `PIOS_REALITY_AUDIT.md`) ran these integration test suites and wrote 76 real rows across four production databases before it was noticed and killed. 72 of those rows were not marked `is_test`, so the platform's own existing test/production data separation (`is_test` columns, `excludeTestData()` in the Owner Control Center) did not catch them — that mechanism protects the *application's* own read paths from *deliberately-marked* test data created through the real API; it was never designed to protect against a test suite writing directly to the database underneath it.

## The fix

Each module's `PostgreSQLTestDatabase.kt` now points at a **separate, isolated database**, one per module, named `pios_<module>_test`:

| Module | Production database (untouched by this fix) | Test database (new) |
| --- | --- | --- |
| `identity` | `pios_identity` | `pios_identity_test` |
| `driver-management` | `pios_driver_management` | `pios_driver_management_test` |
| `passenger-experience` | `pios_passenger_experience` | `pios_passenger_experience_test` |
| `order-management` | `pios_order_management` | `pios_order_management_test` |
| `dispatch` | `pios_dispatch` | `pios_dispatch_test` |
| `network-management` | `pios_network_management` | `pios_network_management_test` |

**The URL is hardcoded, exactly as before — deliberately not made configurable through an environment variable, system property, or `application-test.yml`.** A configuration knob would only reintroduce a way to point tests at production by misconfiguration (the exact failure mode this fix exists to remove). If a future need arises for tests to run against a different host (e.g., a CI runner), that is a separate, deliberate decision for whoever adds CI — not a default this convention should offer today.

Each `_test` database is schema-managed the same way its production counterpart already is: the module's own real Flyway migrations, run automatically by `PostgreSQLTestDatabase.kt` itself on first use. There is no manual schema step.

## Setting up a new clone / new machine

Before running any backend module's tests, create its own `_test` database (in addition to the production-named databases `README.md`'s own "Running Locally" section already documents):

```sql
CREATE DATABASE pios_identity_test;
CREATE DATABASE pios_driver_management_test;
CREATE DATABASE pios_passenger_experience_test;
CREATE DATABASE pios_order_management_test;
CREATE DATABASE pios_dispatch_test;
CREATE DATABASE pios_network_management_test;
```

A module's tests fail at the `PostgreSQLTestDatabase.dataSource` initializer (a clear connection error, not a silent fallback) if its own `_test` database does not exist yet — this is intentional; there is no default that would let a test run "succeed" by accident against something else.

## Convention for any future module

Any new backend module that needs a real-Postgres integration test must follow the same shape: its own `PostgreSQLTestDatabase.kt`, its own hardcoded `pios_<module>_test` URL, no configuration override. Copy an existing module's file as the starting point (e.g. `driver-management`'s) rather than inventing a new pattern.

## What this fix does not cover

- **RabbitMQ is a confirmed, separate, still-open risk — not merely theoretical.** While verifying this exact fix (2026-09-02), `order-management`'s own test suite (`./gradlew :order-management:test`) produced 9 real test failures, all in `AssignmentCompletedConsumerIntegrationTest`/`AssignmentAcceptedConsumerIntegrationTest`-shaped tests. Each of those tests' own KDoc says outright that it publishes "a real AssignmentCompleted-shaped message, published to the real `dispatch.events` exchange exactly as Dispatch's own publisher would" — the same shared RabbitMQ broker instance production's own live `order-management` service (still running throughout) is bound to. The tests' own assertions timed out waiting for their private test-harness listener to receive the message, consistent with production's own live consumer having raced for and consumed it first. **Verified this did not write bad data to production** (`pios_order_management.orders` row count and newest `created_at` were checked immediately before and after this specific test run and were unchanged — the newest row was already 50+ minutes old) — but the failure mode itself (a shared broker with no environment separation) is real, reproducible, and distinct from the database issue this document fixes. `dispatch`'s and `network-management`'s own test suites were deliberately **not** run against the shared broker while investigating this, specifically because `dispatch` is the actual publisher of this same event topology and its own tests were judged likely to reproduce the same or a wider form of this cross-talk (e.g. against its own consumed `DriverAvailabilityChanged` projection). This document is scoped to PostgreSQL only, per the task that produced it — RabbitMQ isolation needs its own separate task.
- **This does not, by itself, undo** the 76 rows already written to production on 2026-09-02. That remains a separate decision, recorded in `PIOS_REALITY_AUDIT.md` Section 19/21, not yet acted on.

# PIOS Current State

Read `AI_HANDOFF.md` first. **This file is a snapshot, not a living document — it reflects direct code inspection performed at the commit named below. If you are reading this any meaningful time after that commit, re-verify every claim against actual `backend/` code yourself before relying on it; do not trust this file as permanently current.**

**Verified at commit:** `f20c83f` (HEAD as of this Project Brain's creation). **Method:** direct `git log`/`grep`/file inspection of `backend/`, not copied from any prior Final Report. The last commit that changed any file under `backend/` was `e01451d` ("Implement production outbox relay trigger"); every commit after that (`1d60f71`, `f20c83f`) is documentation-only, confirmed via `git diff --stat e01451d HEAD -- backend/` returning no output.

---

## Implemented Modules

Four Kotlin/Spring Boot modules, each independently buildable (`backend/settings.gradle.kts`), no shared subproject:

| Module | Port | Database | Purpose |
| --- | --- | --- | --- |
| `order-management` | 8083 | `pios_order_management` | Order lifecycle (submit/complete/cancel) |
| `dispatch` | 8084 | `pios_dispatch` | Assignment decision (driver supplied externally) |
| `driver-management` | 8081 | `pios_driver_management` | Driver availability |
| `passenger-experience` | 8082 | **none** | Passenger representation, no persistence |

**Not scaffolded at all:** Payments, Administration, Analytics, Notifications — four of the eight ratified capability modules (MODULE_STRUCTURE.md) have zero code.

## Backend Stack

`[RATIFIED, CURRENT CODE]`: Kotlin on the JVM (Java 21 toolchain), Spring Boot 3.3.4, plain JDBC (`JdbcTemplate`, no ORM), Flyway migrations, RabbitMQ via Spring AMQP. No shared library module exists anywhere — every cross-module similarity (outbox pattern, publisher shape) is a deliberate, independent, module-local copy (confirmed: `PostgreSQLOutboxRepository.kt` differs only in table name across all three producer modules; `RabbitMQEventPublisher.kt` differs only in exchange name and doc comments).

## REST Flows — **CURRENT CODE**

Exactly one REST endpoint exists anywhere in this repository, verified by `grep -rl "@RestController"`:

- `POST /v1/orders` on Order Management (`com.pios.ordermanagement.api.OrderSubmissionController`) — accepts `{passengerReference}`, delegates to the pre-existing `OrderSubmissionRequestHandler`, returns `{orderId}` (201) or 400 for a blank reference.
- Passenger Experience has a real REST **client** (`RestClientOrderSubmissionClient`, Spring `RestClient`, 5000ms connect/read timeout, no automatic retry) calling the above — but **no production caller of that client exists yet**. It is wired and tested, but nothing in a running deployment currently invokes it; Passenger Experience gained no inbound endpoint of its own (an explicit, deliberate non-goal of Tranche 2).

No other endpoint, in any module, exists.

## RabbitMQ Flows — **CURRENT CODE**

Three producer exchanges, verified via `grep -rl "@RabbitListener"` and direct topology inspection:

| Exchange | Owner | Routing keys | Consumer |
| --- | --- | --- | --- |
| `order-management.events` | Order Management | `order.submitted`, `order.completed`, `order.cancelled` | **none exists anywhere** — no ratified consumer for any of these three events beyond the generic, unbuilt Notifications/Analytics contract |
| `driver-management.events` | Driver Management | `driver.availability.changed` | Dispatch (`DriverAvailabilityChangedListener`) — fully working, idempotent, retried, dead-lettered |
| `dispatch.events` | Dispatch | `order.assigned`, `assignment.accepted` | Order Management (`AssignmentAcceptedListener`, bound only to `assignment.accepted`) — fully working, idempotent, retried, dead-lettered; `order.assigned` has **no consumer** in this backend (its only documented consumer is the Driver actor externally, per API_SPECIFICATION.md Section 8, not yet built) |

Every consumer uses `RetryInterceptorBuilder.stateless().maxAttempts(3).backOffOptions(200L, 2.0, 2000L)` and a per-consumer dead-letter queue (`x-dead-letter-exchange`/`x-dead-letter-routing-key`).

## Outbox — **CURRENT CODE**

All three producer modules (`order-management`, `driver-management`, `dispatch`) have an identical-shape transactional outbox: `OutboxRecord`/`OutboxRepository`/`PostgreSQLOutboxRepository` (`SELECT ... WHERE published_at IS NULL ORDER BY id ASC`, **no row-claiming/locking**), written in the same PostgreSQL transaction as the triggering aggregate change (`TransactionRunner`).

## Scheduler — **CURRENT CODE**, verified via `grep -rl "@Scheduled"`

`OutboxRelayScheduler` (one per producer module, `com.pios.<module>.application`), `@Scheduled(fixedDelayString = "${pios.outbox.relay.fixed-delay-ms:2000}")`, activated via `@EnableScheduling` on each module's own `*Application.kt`. This closed the gap the Hardening Review found (`eaadf6a`): before `e01451d`, `OutboxRelay.relay()` existed and was fully tested but was invoked only by test code — no production trigger existed anywhere. **As of `e01451d`, all three producer modules automatically drain their own outbox without manual or test invocation**, proven by a real, unmocked, per-module `OutboxRelaySchedulerTest`.

**Known, deliberately unaddressed limitation:** no row-claiming exists, so if a module is ever run as more than one concurrent instance, both instances' schedulers could read and re-publish the same pending record. Delivery guarantee is precisely **at-least-once to the broker, effectively-once business effect only because every consumer is required to be idempotent** — never exactly-once. This is explicitly named, not silently assumed solved (`docs/IMPLEMENTATION_PLAN_OUTBOX_RELAY_PRODUCTION_TRIGGER.md` Part 4).

## Idempotency — **CURRENT CODE**

Both real consumers (`DriverAvailabilityChangedListener`, `AssignmentAcceptedListener`) use an `INSERT ... ON CONFLICT (event_id) DO NOTHING` ledger table (`markProcessed`/`isProcessed`), proven with dedicated redelivery and transaction-rollback tests against real PostgreSQL.

## Tests — **CURRENT CODE**

Verified test-file counts (`find src/test -name "*.kt"`) as of `f20c83f`: `dispatch` 30 files, `order-management` 29 files, `driver-management` 19 files, `passenger-experience` 7 files. Actual JUnit test-method counts, last confirmed by a full `./gradlew clean build` run at `e01451d` (no `backend/` change since): **185 tests total, 0 failures, 0 errors** (dispatch=63, order-management=65, driver-management=44, passenger-experience=13). All tests run against real, locally-running PostgreSQL and RabbitMQ — this codebase uses no mock framework (Mockito, MockK, or similar) anywhere.

**No single test proves the fully composed, real-transport, end-to-end chain** (REST-submitted order → real outbox → real automatic relay → real Dispatch assignment/acceptance → real automatic relay → real consumer) in one scenario — every individual segment is proven, the assembly is not (see `docs/PIOS_VERTICAL_SLICE_VERIFICATION_V1.md` Part 2–3 for the precise, still-current finding).

## Migrations — **CURRENT CODE**

| Module | Migrations |
| --- | --- |
| `order-management` | V1 (orders), V2 (outbox), V3 (assignment_accepted_idempotency) |
| `dispatch` | V1 (assignments), V2 (driver_availability), V3 (outbox) |
| `driver-management` | V1 (drivers), V2 (outbox) |
| `passenger-experience` | **none — no database exists for this module** |

## Domain Objects — **CURRENT CODE**

| Module | Domain classes (verified `ls`) |
| --- | --- |
| `order-management` | `Order`, `OrderId`, `OrderStatus`, `OrderSubmitted`, `OrderCompleted`, `OrderCancelled` |
| `dispatch` | `Assignment`, `AssignmentId`, `AssignmentStatus`, `AssignmentAccepted`, `OrderAssigned`, `OrderReference`, `DriverReference` |
| `driver-management` | `Driver`, `DriverId`, `Availability`, `DriverAvailabilityChanged` |
| `passenger-experience` | `OrderSubmissionIntent`, `OrderSubmissionRequested`, `PassengerReference` |

No Opportunity, Eligibility, Assignment Policy, team, network, invitation, or attribution class exists anywhere in `backend/` — confirmed by the domain-directory listings above being exhaustive.

## What Is Genuinely Working

- Order submission (real REST → real handler → real PostgreSQL), completion, cancellation.
- Dispatch assignment creation and acceptance (driver supplied externally, no automated trigger from an order).
- Driver availability declaration and its full event-driven pipeline into Dispatch.
- Full outbox → automatic scheduled relay → RabbitMQ → idempotent, retried, dead-lettered consumption, for the two flows with a real ratified consumer (`DriverAvailabilityChanged`, `AssignmentAccepted`).

## What Is Scaffolded Only / Partially Working

- Order Management's own three events (`OrderSubmitted`, `OrderCompleted`, `OrderCancelled`) are correctly published but have zero consumers — working as designed, not a gap, since none is ratified.
- `OrderAssignmentRecognitionHandler` (Order Management's `AssignmentAccepted` handler) validates and idempotently records the event but does **not** transition `Order`'s own status — no ratified Order state exists for "assigned"/"accepted" yet, by design, unchanged since Tranche 1.
- Passenger Experience's REST client is fully built and tested but has no production caller.

## What Is Absent

**Explicitly verified, not assumed:**

- **Frontend/PWA: does not exist.** `find . -iname "*frontend*" -o -iname "*pwa*"` returns nothing but the ADR-024 document itself. No `frontend/` directory, no `package.json`, no React/TypeScript file anywhere in this repository. ADR-024 selects the *technology* (TypeScript/React/React Native); nothing has been built with it.
- **Invitation/attribution: does not exist.** No domain class, table, event, or endpoint anywhere named "invitation," "attribution," "referral," "QR," or similar.
- **Team/network functionality: does not exist.** No domain class, table, event, or endpoint anywhere named "team," "network," or similar (beyond the pre-existing, unrelated `Network` word used only in "NETWORK scope" fulfillment-authority vocabulary, which is a manual pilot concept, not a code construct).
- **Opportunity, Eligibility, Assignment Policy, Fair Opportunity Policy: unimplemented**, deliberately, per every relevant Product Decision.
- **Payments, Administration, Analytics, Notifications: unscaffolded**, zero code.
- **Authentication/authorization: does not exist anywhere.** No security dependency, no login, no token handling in any module — API_SPECIFICATION.md Section 11 states a security boundary philosophy exists but defines no mechanism; none has been built.

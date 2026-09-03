# Task 14 — First Refusal Foundation: Implementation Report

**Date:** 2026-09-03. **Scope:** backend foundation only — the Passenger Experience → Dispatch event contract (ADR-062) and Dispatch's own First Refusal decision capability. No frontend, Coordinator, Network Management, Order Management, or Identity change. No caller-identity security fix (Task 13's own open finding, left open — Section 18).

**Status:** Complete. 401/401 Dispatch tests passing, 75/75 Passenger Experience tests passing. No STOP condition was triggered.

---

## 1. Executive Summary

Task 13's own runtime audit found the "hard architecture" for First Refusal already resolved (ADR-062, Accepted) and three genuinely missing pieces: the `PrimaryConnectionDesignated`/`PrimaryConnectionCleared` events (not published at all — Passenger Experience had **no event-publishing capability of any kind**, confirmed by re-reading `SetPrimaryConnectionApplicationService` before writing any code), Dispatch's own `PrimaryDriverRecord` projection, and the decision logic itself. This task built all three, plus the first-ever transactional outbox/RabbitMQ producer infrastructure for Passenger Experience (a materially larger addition than a first glance at ADR-062 suggests, since that ADR describes the *pattern* to reuse, not literal existing code in that specific module — documented in full in Section 19).

`ProposalApplicationService.handle` and every existing Proposal REST endpoint are untouched. First Refusal's own decision logic lives in a new, separate, additive service (`FirstRefusalApplicationService`) that a future routing task can invoke — not wired into any live endpoint by this task, per its own explicit instruction.

## 2. Actual Implementation Performed

**Passenger Experience** (its first-ever event-publishing capability):
- A complete transactional outbox + RabbitMQ producer stack, mirroring Dispatch's own already-proven shape file-for-file (`OutboxRecord`, `OutboxRepository`, `OutboxBacklog`, `NoOpOutboxRepository`, `EventPublisher`, `NoOpEventPublisher`, `OutboxRelay`, `OutboxRelayScheduler`, `PostgreSQLOutboxRepository`, `RabbitMQEventPublisher`, `RabbitMQProducerTopologyConfiguration`).
- Two new domain events: `PrimaryConnectionDesignated`, `PrimaryConnectionCleared`.
- `SetPrimaryConnectionApplicationService.handle` now publishes `PrimaryConnectionDesignated` in the same transaction as the `primary_connections` upsert.
- `RemoveConnectionApplicationService.handle` now publishes `PrimaryConnectionCleared` — **only** when the removed connection was, at the moment of removal, the passenger's own primary — in the same transaction as the delete.
- New migration `V3__outbox.sql`; new `spring-boot-starter-amqp` dependency; `spring.rabbitmq.*` config block; `@EnableScheduling` on `PassengerExperienceApplication`.

**Dispatch** (the projection + decision capability):
- A new `PassengerReference` domain type (module-local, mirrors `OrderReference`/`DriverReference`).
- `PrimaryDriverRecord` projection + `PrimaryDriverRepository` (interface, `PostgreSQLPrimaryDriverRepository`, `InMemoryPrimaryDriverRepository`) + `PrimaryDriverProjectionApplicationService`, mirroring `DriverAvailabilityRecord`/`DriverAvailabilityRepository`/`DriverAvailabilityProjectionApplicationService` exactly.
- `PrimaryConnectionEventListener` + `RabbitMQPassengerExperienceTopologyConfiguration` (new consumer queue + DLQ + two bindings, one per event), mirroring `DriverAvailabilityChangedListener`/`RabbitMQTopologyConfiguration`.
- New migration `V13__primary_driver.sql`.
- `FirstRefusalApplicationService.attempt(order, passengerReference, isTest)` — the decision logic (Section 5).

## 3. Primary Connection Event Design

`PrimaryConnectionDesignated(passengerReference, driverId, occurredAt)` fires from `SetPrimaryConnectionApplicationService.handle` on every successful designation (first-ever or a change — ADR-054's own `INSERT ... ON CONFLICT ... DO UPDATE` makes a "change" indistinguishable from a "first designation" at the persistence layer, so both simply republish this same event with the current `driverId`).

`PrimaryConnectionCleared(passengerReference, occurredAt)` fires from `RemoveConnectionApplicationService.handle` **only** when the connection being removed is, at the moment of removal, the passenger's current primary (checked via `primaryConnectionRepository.findByPassenger` *before* the delete, since the delete's own cascade clears the designation as a side effect with nothing left to inspect afterward). Removing a non-primary connection, or a connection when the passenger has no primary at all, publishes nothing — verified by dedicated tests.

Payload is exactly the two opaque identifiers ADR-062 authorizes: `passengerReference`, and (for the designated case) `driverId`. No `createdAt`, no name, no phone number, no other `Connection` metadata — verified by a dedicated test asserting the payload does not contain the connection's own `createdAt` timestamp. Routing keys: `primary.connection.designated`, `primary.connection.cleared`. Exchange: `passenger-experience.events` (new, this module's first).

## 4. Dispatch Projection Design

`PrimaryDriverRecord(passengerReference, primaryDriverId)` — the entire projection, mirroring `DriverAvailabilityRecord`'s own two-field minimalism. `primary_driver_records` (PK `passenger_reference`) + `primary_driver_processed_events` (PK `event_id`, shared idempotency ledger for both event types, since event ids are globally unique regardless of type). `PrimaryDriverProjectionApplicationService.handleDesignated`/`handleCleared` each: `markProcessed(eventId)` first, apply the effect only if new, inside one `transactionRunner` boundary. Consumed via a single new queue (`dispatch.from-passenger-experience`) bound to both routing keys — one listener (`PrimaryConnectionEventListener`) branching on `eventType`, since both events feed the identical projection logic (documented rationale for diverging from `RabbitMQOrderManagementTopologyConfiguration`'s own "one queue per producer *module*" precedent: here it's one relationship signal with two outcomes, not two unrelated event kinds needing different handlers — see that class's own KDoc).

## 5. Proposal Decision Logic

`FirstRefusalApplicationService.attempt(order, passengerReference, isTest)`:
1. Look up `PrimaryDriverRepository.findByPassenger(passengerReference)` → `NoPrimaryDriver` if absent.
2. Pre-check `DriverAvailabilityRepository` for the primary driver → `PrimaryDriverIneligible(driver)` if unavailable or unrecorded.
3. Otherwise, delegate straight to **existing, unmodified** `ProposalApplicationService.handle(ProposeDriverCommand(order, primaryDriver, isTest))` — reusing that method's own existing availability gate and one-open-proposal-per-order invariant rather than re-implementing either. A caught `IllegalStateException` (either gate) becomes `AlreadyAttempted`.

`ProposeDriverCommand`/`ProposalApplicationService.handle`/`ProposalController` are **completely unchanged** — confirmed by `git diff` showing zero lines touched in any of the three. This was a deliberate design choice, not an oversight: `ProposeDriverCommand` carries no passenger identity at all today (no real caller ever supplies one), so the decision logic could not live inside `handle` itself without either changing that command's own shape (touching every real caller) or duplicating its availability/invariant checks (a correctness risk under concurrency). A new, separate, additive service resolves this without touching anything existing — documented in full in `FirstRefusalApplicationService`'s own KDoc.

`ProposalStatus` vocabulary is completely unchanged (`OPEN`/`ACCEPTED`/`DECLINED`/`LAPSED`/`WITHDRAWN` — no First-Refusal-specific status was added, per this task's own explicit instruction). No marker/reason column was added to `Proposal` distinguishing a First-Refusal proposal from an ordinary one — not required by any of this task's own required tests, and deliberately deferred (Section 20) rather than invented.

## 6. Fallback Behavior

Confirmed against the actual current code (Task 13's own finding, re-verified): there is **no automated fallback** anywhere in this codebase today — a human (the Coordinator) manually creates the next Proposal via the same, unmodified `POST /v1/proposals`. `FirstRefusalApplicationService.attempt` therefore does not, and could not correctly, create any fallback Proposal itself: on `NoPrimaryDriver`/`PrimaryDriverIneligible`, it simply creates nothing, leaving the order's Root Invariant (`at most one OPEN proposal per order`) unviolated and the order exactly as free for the Coordinator's own existing flow as it is today — verified directly by `Test H`, which follows a `PrimaryDriverIneligible` outcome with a real call to the unmodified `ProposalApplicationService.handle` for a different driver and asserts it succeeds normally.

## 7. Idempotency Strategy

Three independent layers, each proven by a dedicated test:
- **Event publication** (Passenger Experience): each designation/clearing publishes its own event once, in the same transaction as the state change it describes — proven atomic by `SetPrimaryConnectionTransactionTest` (real PostgreSQL).
- **Event consumption** (Dispatch): `primary_driver_processed_events`'s own `PRIMARY KEY (event_id)` + `ON CONFLICT ... DO NOTHING` — the database-enforced half — proven against a real broker by `PrimaryConnectionIdempotencyIntegrationTest`, which redelivers an identical `eventId` claiming the *opposite* effect (a different driver, or a stale clear) and asserts the second delivery is silently ignored.
- **First Refusal attempts** (Dispatch): reuses `Proposal`'s own existing root invariant — no new mechanism — proven by `Test I`.

## 8. Transaction Boundaries

`SetPrimaryConnectionApplicationService.handle`/`RemoveConnectionApplicationService.handle`: `transactionRunner.run { ... }` wraps the `primary_connections`/`connections` write and the outbox write together — proven to roll back together on a simulated outbox failure by `SetPrimaryConnectionTransactionTest`'s own dedicated test. `PrimaryDriverProjectionApplicationService.handleDesignated`/`handleCleared`: `transactionRunner.run { ... }` wraps the idempotency-ledger insert and the projection upsert/clear together, mirroring `DriverAvailabilityProjectionApplicationService` exactly. `FirstRefusalApplicationService.attempt` opens no transaction of its own — it delegates entirely to `ProposalApplicationService.handle`'s own existing transaction boundary, unchanged.

## 9. Database Migration(s)

`backend/passenger-experience/.../V3__outbox.sql` (`passenger_experience_outbox`, structurally identical to `dispatch_outbox`/`order_management_outbox`). `backend/dispatch/.../V13__primary_driver.sql` (`primary_driver_records`, `primary_driver_processed_events`, structurally identical to `driver_availability`/`driver_availability_processed_events`). Both confirmed as the correct next version number by directly listing each module's existing migration files before creating them (Passenger Experience's highest was `V2`; Dispatch's highest was `V12`, from Task 11/12's own Trip work). Neither migration modifies an already-applied one.

## 10. Event/Outbox Changes

Two new events (`PrimaryConnectionDesignated`, `PrimaryConnectionCleared`), two new routing keys, one new exchange (`passenger-experience.events`), one new consumer queue + DLQ pair (`dispatch.from-passenger-experience`[`.dlq`]) in Dispatch. No existing event, exchange, queue, or binding was renamed, removed, or modified. `INTERFACE_CONTRACTS.md`/`EVENT_CATALOG.md` were **not** updated by this task — those documents' own TARGET-marked notes (Task 10B) already anticipated exactly this addition and explicitly reserved the actual update for "the implementation task" (their own wording); updating them now, before Task 13's own recommended sequencing (accept/decline security fix, then the routing task that actually wires this in) is complete, was judged premature and out of this task's own stated scope (build the *backend foundation*, not reconcile documentation for a capability nothing yet calls in production). Flagged here for the reviewing task, not silently skipped.

## 11. Files Created

**Passenger Experience:** `application/OutboxRecord.kt`, `OutboxBacklog.kt`, `OutboxRepository.kt`, `NoOpOutboxRepository.kt`, `EventPublisher.kt`, `NoOpEventPublisher.kt`, `OutboxRelay.kt`, `OutboxRelayScheduler.kt`; `domain/PrimaryConnectionDesignated.kt`, `PrimaryConnectionCleared.kt`; `persistence/PostgreSQLOutboxRepository.kt`, `RabbitMQEventPublisher.kt`, `RabbitMQProducerTopologyConfiguration.kt`; `db/migration/passengerexperience/V3__outbox.sql`; tests: `application/RecordingOutboxRepository.kt`, `SetPrimaryConnectionApplicationServiceTest.kt`, `RemoveConnectionApplicationServiceTest.kt`; `persistence/RabbitMQTestConnection.kt`, `SetPrimaryConnectionTransactionTest.kt`.

**Dispatch:** `domain/PassengerReference.kt`; `application/PrimaryDriverRecord.kt`, `PrimaryDriverRepository.kt`, `PrimaryDriverDesignatedCommand.kt`, `PrimaryDriverClearedCommand.kt`, `PrimaryDriverProjectionApplicationService.kt`, `FirstRefusalApplicationService.kt`; `persistence/PostgreSQLPrimaryDriverRepository.kt`, `InMemoryPrimaryDriverRepository.kt`, `PrimaryConnectionEventListener.kt`, `RabbitMQPassengerExperienceTopologyConfiguration.kt`; `db/migration/dispatch/V13__primary_driver.sql`; tests: `application/FirstRefusalApplicationServiceTest.kt`, `PrimaryDriverProjectionApplicationServiceTest.kt`; `persistence/PrimaryConnectionMessagePublisher.kt`, `PrimaryConnectionTestListenerHarness.kt`, `PrimaryConnectionConsumerIntegrationTest.kt`, `PrimaryConnectionIdempotencyIntegrationTest.kt`.

## 12. Files Modified

**Passenger Experience:** `build.gradle.kts` (+amqp dependency), `src/main/resources/application.yml` (+rabbitmq block), `PassengerExperienceApplication.kt` (+`@EnableScheduling`), `application/SetPrimaryConnectionApplicationService.kt`, `application/RemoveConnectionApplicationService.kt`, `src/test/kotlin/.../api/ConnectionControllerTest.kt` (constructor signature: `RemoveConnectionApplicationService` gained a required `primaryConnectionRepository` parameter).

**Dispatch:** none. Every Dispatch file this task touches is newly created; `ProposalApplicationService.kt`, `ProposalController.kt`, `ProposeDriverCommand.kt`, and every other pre-existing Dispatch file are untouched by this task (the `AssignmentController.kt`/`DispatchAssignmentApplicationService.kt` entries visible in `git status` are carried over, unmodified, from Task 12 — not touched here).

## 13. Files Deliberately Not Modified

`ProposalController.kt` and every existing Proposal REST endpoint (per this task's own instruction). `ConnectionController.kt` (no new REST endpoint was added for either event — per this task's own instruction). Any frontend file. `Coordinator.tsx`. Network Management, Order Management, Identity — zero files. `docs/INTERFACE_CONTRACTS.md`/`docs/EVENT_CATALOG.md` (Section 10). `Assignment.kt`/`Trip.kt` and Dispatch's own ride-progress code (Task 12's own convergence, untouched).

## 14. Tests Added

19 new Dispatch tests (`FirstRefusalApplicationServiceTest` ×8, `PrimaryDriverProjectionApplicationServiceTest` ×6, `PrimaryConnectionConsumerIntegrationTest` ×3, `PrimaryConnectionIdempotencyIntegrationTest` ×2) and 11 new Passenger Experience tests (`SetPrimaryConnectionApplicationServiceTest` ×4, `RemoveConnectionApplicationServiceTest` ×5, `SetPrimaryConnectionTransactionTest` ×4 — arithmetic note: exact per-file counts are approximate here; the authoritative count is the aggregated XML total in Section 16). Coverage against the required list:

| Required | Covered by |
|---|---|
| A. Designation publishes correct event | `SetPrimaryConnectionApplicationServiceTest` |
| B. Clearing publishes correct event | `RemoveConnectionApplicationServiceTest` |
| C. Dispatch consumes designation | `PrimaryConnectionConsumerIntegrationTest` (real broker) |
| D. Dispatch consumes clear/change | `PrimaryConnectionConsumerIntegrationTest` (real broker) |
| E. Projection is idempotent | `PrimaryConnectionIdempotencyIntegrationTest` (real broker) + `PrimaryDriverProjectionApplicationServiceTest` (in-memory) |
| F. Primary + eligible → selected | `FirstRefusalApplicationServiceTest` |
| G. No primary → unchanged | `FirstRefusalApplicationServiceTest` |
| H. Primary unavailable → fallback unchanged | `FirstRefusalApplicationServiceTest` |
| I. Duplicate creation → no duplicate state | `FirstRefusalApplicationServiceTest` |
| J. Existing Proposal tests remain green | Full Dispatch suite, Section 16 |

## 15. Test Commands

```
./gradlew.bat :passenger-experience:test --tests "com.pios.passengerexperience.application.SetPrimaryConnectionApplicationServiceTest" --tests "com.pios.passengerexperience.application.RemoveConnectionApplicationServiceTest" --tests "com.pios.passengerexperience.api.ConnectionControllerTest" --tests "com.pios.passengerexperience.application.CreateConnectionApplicationServiceTest"
./gradlew.bat :passenger-experience:test --tests "com.pios.passengerexperience.persistence.SetPrimaryConnectionTransactionTest"
./gradlew.bat :dispatch:test --tests "com.pios.dispatch.application.FirstRefusalApplicationServiceTest" --tests "com.pios.dispatch.application.PrimaryDriverProjectionApplicationServiceTest"
./gradlew.bat :dispatch:test --tests "com.pios.dispatch.persistence.PrimaryConnectionConsumerIntegrationTest" --tests "com.pios.dispatch.persistence.PrimaryConnectionIdempotencyIntegrationTest"
./gradlew.bat :dispatch:test
./gradlew.bat :passenger-experience:test
```

## 16. Test Results

All focused runs: **BUILD SUCCESSFUL**, zero failures, on first attempt for every file except `FirstRefusalApplicationServiceTest` (one initial failure, root-caused and fixed — Section 17).

**Complete Dispatch suite:** `./gradlew.bat :dispatch:test` → **BUILD SUCCESSFUL**. Aggregated from XML reports: `tests=401 skipped=0 failures=0 errors=0` (383 from Task 12's own baseline + 18 new from this task).

**Complete Passenger Experience suite:** `./gradlew.bat :passenger-experience:test` → **BUILD SUCCESSFUL**. Aggregated from XML reports: `tests=75 skipped=0 failures=0 errors=0`.

## 17. Production Safety Verification

- Verified before running any test: `psql -h 127.0.0.1 -U postgres -lqt` shows both `pios_dispatch_test`/`pios_dispatch` and `pios_passenger_experience_test`/`pios_passenger_experience` as separate, distinctly-named databases; every repository/test in this task uses only `PostgreSQLTestDatabase` (hardcoded to the `_test`-suffixed name, per `docs/TEST_DATABASE_ISOLATION.md`).
- RabbitMQ: every new test uses `RabbitMQTestConnection` (dispatch's own, already established; a new, identical one created for passenger-experience this task, its first-ever RabbitMQ-using test file) — hardcoded to the `pios-test` vhost and the `pios_test` credential, which the broker itself refuses to authenticate against any other vhost (confirmed by that credential's own established, unmodified provisioning from Task 2).
- **Own-test-suite residual-data issue found and cleaned, root-caused as pre-existing and unrelated to this task**: the first full-Dispatch-suite run failed 5 pre-existing tests (`ProposalControllerPostgreSQLIntegrationTest` ×3, `PostgreSQLAssignmentLifecycleTest` ×2) — the exact, already-diagnosed (Tasks 11/12) non-randomized-literal-identifier residual-data pattern, confirmed by inspecting the failing assertions (both files' own known fixed order/driver id literals). Cleaned via `DELETE` against `pios_dispatch_test` only (trips → assignments → proposals, respecting the FK order), then reran — clean. No code or test file was modified to achieve this; no production data was touched.
- **`FirstRefusalApplicationServiceTest`'s own one real bug, found and fixed**: `Test H`'s own fallback-Proposal assertion initially failed with `IllegalStateException` — root cause: the fallback driver in the test had no `DriverAvailabilityRecord` upserted, so `ProposalApplicationService.handle`'s own pre-existing availability gate correctly rejected it (exactly the gate this task deliberately reuses, Section 5, working as designed). Fixed by upserting availability for the fallback driver before the assertion — a test-construction fix, not a production-code change.
- Production PostgreSQL (`pios_dispatch`, `pios_passenger_experience`) and production RabbitMQ (vhost `/`): never connected to by any command in this task.

## 18. Security Risks Left Open

Per this task's own explicit instruction: Task 13's finding — `POST /v1/proposals/{id}/accept|decline|lapse` have **no caller-identity check of any kind**; any caller who knows a `proposalId` can accept or decline it regardless of which driver it names — remains **completely unaddressed by this task**. This is now marginally more consequential than before: a First-Refusal Proposal (once a future task actually wires `FirstRefusalApplicationService` into a live trigger) will be an ordinary `Proposal` row, indistinguishable at the API layer from any other, and therefore exactly as exposed to this gap as every Proposal already is. No new security surface was introduced by this task beyond that pre-existing, already-documented one. Also newly true, not previously applicable: Passenger Experience's own new RabbitMQ connection uses the same production default-vhost/`guest`-credential configuration every other module already does (`docs/PIOS_TAXI_ARCHITECTURE_RATIFICATION.md` Section 6 item 3, unaffected in substance — one more module now shares the same already-disclosed condition, not a new one).

## 19. Architectural Deviations, if Any

**Passenger Experience's outbox/RabbitMQ producer infrastructure did not already exist** — a materially larger addition than "reuse the existing outbox mechanism" (this task's own Phase 1 wording) suggested at first reading. Investigated directly before writing any code (Phase 0): `find backend/passenger-experience/src/main -iname "*outbox*"` returned nothing, and `build.gradle.kts` carried no `spring-boot-starter-amqp` dependency at all. ADR-054 itself explicitly recorded, at the time Circle of Trust was built, "no new event, no outbox record" as a deliberate non-goal — this module genuinely never needed event publication before ADR-062. This was not treated as a Phase 0 STOP condition (the instructions permit and require an implementation to "reuse existing project conventions," which this satisfies — the *convention*, proven three times over in Dispatch/Order Management/Driver Management, was reused verbatim; only its *presence in this specific module* was new) but is recorded here explicitly since it is a larger footprint than the task's own framing implied, per "if the implementation requires [something not obviously anticipated], report... before" the same discipline this task asks of a new configurable duration (Phase 4) is applied here to a new dependency instead.

No other deviation. Phase 4's own instruction not to invent a response-window duration was satisfied trivially and without any new configuration: since `FirstRefusalApplicationService` creates an ordinary `Proposal` (Section 5), it is automatically swept by the **existing, unmodified** `ProposalLapseScheduler`/`ProposalLapseApplicationService` (`pios.proposal.lapse.timeout-minutes`, default 5 minutes) — no new duration, configuration key, or business decision was introduced.

## 20. Remaining Work for the Next Task

- **The actual routing/orchestration**: nothing in this codebase calls `FirstRefusalApplicationService.attempt` yet. A future task must decide *when* (on `OrderSubmitted`? on the frontend's own existing sequential order→proposal calls, extended?) and *how many times* it may legitimately be invoked per order — this task's own service is stateless about its own history beyond the current attempt's own Root-Invariant-enforced exclusivity (Section 5's own documented scope boundary).
- **Whether a re-attempt after a First-Refusal Proposal's own decline/lapse should re-offer the same primary driver** (structurally possible today, since nothing prevents it) is unresolved — a product question, not touched here.
- **The Section 15 security fix** (caller-identity check on accept/decline/lapse) — explicitly out of this task's own scope, strongly recommended before or alongside the next implementation step.
- **`INTERFACE_CONTRACTS.md`/`EVENT_CATALOG.md` updates** for the two new events (Section 10) — deferred, not forgotten.
- **Whether a First-Refusal-distinguishing marker belongs on `Proposal` itself** (Section 5) — deferred, no test in this task required it.
- Whether `PrimaryConnectionCleared` should also fire from a future, not-yet-built explicit "unset primary without removing the connection" action, distinct from today's only trigger (removal) — named as open by ADR-062 itself, unresolved here.

---

## 21. Explicit Statement of Verified Safety

No frontend file, Coordinator, Network Management, Order Management, or Identity file was modified. No existing Proposal REST endpoint's signature, behavior, or authorization changed. No event was renamed. No production database or RabbitMQ vhost was connected to. Every test ran against the isolated `*_test` PostgreSQL databases and the isolated `pios-test` RabbitMQ vhost, confirmed before running.

**Per this task's own final instruction: STOP here.** Not proceeding to frontend integration, Coordinator integration, UI, or caller authentication. Not starting the next task.

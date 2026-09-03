# Task 12 — Trip Ride-Progress Convergence: Implementation Report

**Date:** 2026-09-03
**Task:** Make `Trip` (not `Assignment`) the authoritative owner of ride-progress (`ARRIVED`/`IN_PROGRESS`/`COMPLETED`) inside Dispatch, safely, additively, without touching the frontend, Order Management, or First Refusal.
**Status:** Complete. 383/383 Dispatch tests passing. No STOP condition was found that blocked implementation; one significant compatibility judgment call was made and is documented in full in Section 11.

---

## 1. Current Assignment Ride-Progress Implementation (before this task)

`Assignment` (`backend/dispatch/.../domain/Assignment.kt`) modeled both the match decision and the ride-in-progress facts in one aggregate: `AssignmentStatus` = `CREATED, ACCEPTED, ARRIVED, IN_PROGRESS, COMPLETED`. `arrive()`/`start()`/`complete()` validated transitions and stamped `arrivedAt`/`startedAt`/`completedAt` (ADR-043). `DispatchAssignmentApplicationService.arriveAssignment`/`startAssignment`/`completeAssignment` called these methods directly, persisted `Assignment` through `AssignmentRepository`, and wrote an outbox record (`AssignmentArrived`/`AssignmentStarted`/`AssignmentCompleted`) in the same transaction (ADR-032). `AssignmentController`'s `POST /v1/assignments/{id}/arrive|start|complete` and `GET /v1/assignments?orderId=` exposed this directly via `AssignmentResponse`.

## 2. Current Trip Implementation (Task 11, before this task)

`Trip` (`backend/dispatch/.../domain/Trip.kt`) already existed as a parallel, unwired aggregate: `TripStatus` = `CREATED, ARRIVED, IN_PROGRESS, COMPLETED`, with its own `arrive()/start()/complete()`, its own `TripArrived/TripStarted/TripCompleted` domain events, its own `trips` table (`V12__trips.sql`, real FK + `UNIQUE` on `assignment_id`), and its own `TripRepository` (in-memory, PostgreSQL, no-op). It was created transactionally alongside every new `Assignment` (`OrderAssigned` trigger, ADR-063 corrected), but its own ride-progress lifecycle was never invoked by any production code path — Task 11 deliberately stopped there.

## 3. Complete Dependency Map

| Fact | Source (before this task) |
|---|---|
| A. Where ARRIVING is created/changed/read | `Assignment.arrive()` (validate+mutate); `DispatchAssignmentApplicationService.arriveAssignment` (orchestrate); `AssignmentController.arrive` (REST); `AssignmentRepository`/`PostgreSQLAssignmentRepository` (persist); `AssignmentResponse.arrivedAt` (read) |
| B–D. ARRIVED/STARTED/COMPLETED | Same shape as A, for `start()`/`complete()` |
| E. Validates each transition | `Assignment` itself (`check()` preconditions) |
| F. Persists each state | `PostgreSQLAssignmentRepository` (`assignments` table, columns from `V8__assignment_transition_timestamps.sql`) |
| G. Publishes each event | `DispatchAssignmentApplicationService`, via `OutboxRepository` + the transactional outbox relay (ADR-032) |
| H. External consumers | `AssignmentCompleted` → Order Management's `AssignmentCompletedListener` (real, wired, routing key `assignment.completed`). `AssignmentArrived`/`AssignmentStarted` → no consumer (ADR-041 Decision item 4, re-confirmed by direct search of `backend/order-management`) |
| I. Frontend consumers | `DriverHome.tsx` (`respondToAssignment`: `POST /v1/assignments/{id}/{arrive|start|complete}`, reads `AssignmentInfo.status/arrivedAt/startedAt/completedAt`); `RideRequest.tsx` (`GET /v1/assignments?orderId=`, reads `.status`); `OwnerControlCenter/todayData.ts` and `pilotAnalytics.ts` (same GET, read `.status/.arrivedAt/.startedAt/.completedAt`) |
| J. API endpoints | `AssignmentController`: `GET /v1/assignments`, `POST /{id}/arrive`, `/start`, `/complete` — all under `/v1/assignments`, none under `/v1/trips` (no such controller exists) |

## 4. Consumers Discovered

- **Order Management's `AssignmentCompletedListener`** — real, production-wired, bound to routing key `assignment.completed` (`RabbitMQConsumerTopologyConfiguration.ASSIGNMENT_COMPLETED_ROUTING_KEY`). The one genuine cross-module consumer of ride-progress.
- **`DriverHome.tsx`** — calls the three transition endpoints directly and renders their response.
- **`RideRequest.tsx`, `OwnerControlCenter`'s `todayData.ts`/`pilotAnalytics.ts`** — read-only consumers of the same `GET /v1/assignments?orderId=` response shape.
- **Pilot's own PostgreSQL data.** The project is in an active evidence-gathering pilot (not hypothetical production, but real usage); `assignments` rows with `status` at `ARRIVED`/`IN_PROGRESS`/`COMPLETED` already exist from before this task.

## 5. API Dependencies

None require any change. `AssignmentController`'s three URLs and `AssignmentResponse`'s field set are unchanged — see Section 11.

## 6. Frontend Dependencies

Real (Section 4/I), but resolved additively — no frontend file was touched. See Section 11 for how.

## 7. Event Dependencies

`AssignmentCompleted` (real consumer, Section 4/H) is the binding constraint on any event rename. Resolved via the dual-publish window ADR-063's own Consequences section already anticipated: the legacy event is kept, byte-for-byte, alongside a new one. See Section 14.

## 8. Database Dependencies

`assignments` table (`V8` migration) carries `arrived_at`/`started_at`/`completed_at`/`status`; `trips` table (`V12` migration, Task 11) already carries the equivalent columns plus a FK to `assignments.id`. No new migration was needed — the existing `trips` schema already covers everything Trip now writes to.

## 9. Migration Strategy

Additive throughout. No column dropped, no column added, no existing migration modified. `Assignment`'s own ride-progress columns become a frozen historical remnant (still readable directly via SQL, no longer written to by any application code). No new migration file was created for this task (only application/domain code changed).

## 10. Changes Implemented

**Modified (2 main-source files):**
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/DispatchAssignmentApplicationService.kt` — `arriveAssignment`/`startAssignment`/`completeAssignment` now transition `Trip` (via a new private `tripFor(assignment)` self-healing lookup), not `Assignment`; each publishes both the legacy `Assignment*` outbox record (unchanged shape) and a new `Trip*` one, in the same transaction.
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/api/AssignmentController.kt` — gained a `tripRepository` constructor parameter (default `NoOpTripRepository`, matching the existing convention); `toResponse()` now projects from both `Assignment` and the connected `Trip` (falling back to `Assignment`'s own fields when no Trip exists or it is still at its initial state).

**Modified (4 test files, ride-progress-specific assertions retargeted from `AssignmentRepository`/`Assignment.status` to `TripRepository`/`Trip.status`, since that is what actually changed; every other assertion in each file is untouched):**
- `backend/dispatch/src/test/kotlin/com/pios/dispatch/api/AssignmentControllerTest.kt` — now wires a shared `InMemoryTripRepository` into both the service and the controller.
- `backend/dispatch/src/test/kotlin/com/pios/dispatch/application/DispatchAssignmentApplicationServiceTest.kt` — 3 ride-lifecycle assertions retargeted; 1 new test added proving `Assignment`'s own status never changes.
- `backend/dispatch/src/test/kotlin/com/pios/dispatch/persistence/AssignmentRepositoryLifecycleTest.kt` — 1 assertion retargeted.
- `backend/dispatch/src/test/kotlin/com/pios/dispatch/persistence/PostgreSQLAssignmentLifecycleTest.kt` — 3 assertions retargeted; 1 new test added (with a randomized order id, not a fixed literal — see Section 16) proving the same for real PostgreSQL.

**Created (1 new test file, 6 tests):**
- `backend/dispatch/src/test/kotlin/com/pios/dispatch/application/DispatchAssignmentApplicationServiceRideProgressConvergenceTest.kt` — self-heal (legacy Assignment with no Trip) ×3, dual-publish outbox verification ×3.

**Documentation updated:** `docs/ADR/ADR-063-Trip-as-a-New-Dispatch-Owned-Aggregate.md` (new "Implementation note (Task 12)" + "Files Changed" update — original Decision-section text preserved verbatim), `docs/PIOS_TAXI_ARCHITECTURE_CONTRACT.md` (Sections 9, 13), `docs/PIOS_TAXI_ARCHITECTURE_RATIFICATION.md` (Section 4), `docs/EVENT_CATALOG.md` (Section 6 table + two sync notes, Section 11 traceability table), `docs/INTERFACE_CONTRACTS.md` (Section 7 table, new Section 19 addendum, Section 18 marked superseded-in-part).

**Nothing else was modified.** No migration file, no frontend file, no Order Management file, no Passenger Experience file, no Network Management file, no Identity file.

## 11. Compatibility Strategy — including a deliberate, documented deviation from ADR-063's own wording

The target model (Assignment = match, Trip = execution) is real, but this task's own Phase 3 instruction — *"Do NOT delete existing Assignment fields or endpoints merely because Trip now exists. Only remove old behavior after proving that no active consumer depends on it"* — could not be satisfied for deletion: `DriverHome.tsx` is a live consumer of `AssignmentController`'s endpoints, and the pilot's own PostgreSQL already has `Assignment.status` rows at `ARRIVED`/`IN_PROGRESS`/`COMPLETED` from before this change.

ADR-063's own Decision section says `Assignment.arrive()/start()/complete()` "are removed from Assignment and relocated to Trip." **This was not done.** `Assignment.kt` is completely unmodified by this task — every method, field, and enum value it had before this task still exists, and `AssignmentTest.kt`'s own direct unit tests of `arrive()/start()/complete()` still pass, unmodified, because nothing about the aggregate itself changed. What changed is which code path is *called* in production: `DispatchAssignmentApplicationService` no longer calls `Assignment.arrive()/start()/complete()` at all — it calls the connected `Trip`'s identically-named methods instead. `Assignment`'s own ride-progress capability becomes a frozen, unreachable-from-production remnant, not a second live source of truth: nothing writes to it going forward, so there is no risk of it silently diverging from `Trip`.

This is a considered choice, not an oversight: Task 12's Phase 3 instruction is more specific than ADR-063's general Decision-section wording, and — given a live pilot with real, already-progressed ride data — the safer reading is the one that does not require a backfill migration or risk stranding historical rows. This is recorded in ADR-063 itself (new "Implementation note," preserving the original text per `CLAUDE.md`'s "Never Delete Documentation"), not silently done.

**How the REST contract stayed byte-for-byte unchanged despite this:** `AssignmentController.toResponse()` was changed to accept the connected `Trip` (nullable) and prefer its `status`/`statusChangedAt`/`arrivedAt`/`startedAt`/`completedAt` once it has moved past its own initial `CREATED` state — otherwise falling back to `Assignment`'s own `status`/`statusChangedAt` exactly as before (still only ever `CREATED` or `ACCEPTED`, the two states Trip does not itself model). Since `Trip`'s state machine and field names mirror `Assignment`'s former ride-progress ones exactly (both established in Task 11, deliberately, per ADR-063), the JSON `AssignmentResponse` a client receives is identical in shape and identical in value to what it would have received before this task — the frontend needs no knowledge that `Trip` exists.

**Self-healing for legacy data:** an `Assignment` saved before Task 11 (or through any path that bypassed Trip creation) has no connected `Trip`. `DispatchAssignmentApplicationService`'s new private `tripFor(assignment)` helper creates one on the spot, on first ride-progress action, mirroring exactly what `Trip.create()` would have done at Assignment-creation time — proven by `DispatchAssignmentApplicationServiceRideProgressConvergenceTest`'s own 3 self-heal tests. No backfill migration was required.

## 12. Assignment's Responsibility After Migration

Unchanged in code, changed in practice: `Assignment` still models `CREATED`/`ACCEPTED`/`ARRIVED`/`IN_PROGRESS`/`COMPLETED` and still has `arrive()/start()/complete()`, but no production code path calls the latter three anymore. In practice, going forward, `Assignment.status` will only ever be observed at `CREATED` or `ACCEPTED` for any Assignment created after this deployment — proven by two new tests (`DispatchAssignmentApplicationServiceTest`, `PostgreSQLAssignmentLifecycleTest`) that run the full ride lifecycle through the service and then assert `Assignment`'s own status stayed `CREATED`.

## 13. Trip's Responsibility After Migration

`Trip` is now the sole validator (its own `check()` preconditions), persister (`PostgreSQLTripRepository`/`InMemoryTripRepository`), and publisher (`TripArrived`/`TripStarted`/`TripCompleted`, dual-published alongside the legacy `Assignment*` events) of `ARRIVED`/`IN_PROGRESS`/`COMPLETED`, for both new Assignments (created with a Trip already attached, per Task 11) and legacy ones (self-healed, Section 11).

## 14. Events After Migration

Every ride-progress transition now publishes **two** outbox records, atomically, in the same transaction:

| Legacy (unchanged) | New | Order Management binding |
|---|---|---|
| `AssignmentArrived` / `assignment.arrived` | `TripArrived` / `trip.arrived` | Neither bound |
| `AssignmentStarted` / `assignment.started` | `TripStarted` / `trip.started` | Neither bound |
| `AssignmentCompleted` / `assignment.completed` | `TripCompleted` / `trip.completed` | **`assignment.completed` still bound** (`AssignmentCompletedListener`, unmodified) |

No event was renamed. No existing event's `eventType` or routing key changed. This is the dual-publish migration window ADR-063's own Consequences section already required before any implementation existed. Retiring the legacy half (and switching Order Management's own binding) is a distinct, not-yet-authorized future task, per this task's own Order Management Rule.

## 15. Tests

- 8 new self-heal/dual-publish/Assignment-untouched tests (Section 10).
- 7 existing tests retargeted (assertion target only — from `Assignment`'s own repository/status to `Trip`'s).
- All pre-existing tests not touching ride-progress (`AssignmentTest.kt`'s domain-level `arrive/start/complete` unit tests, every Proposal test, every Trip-creation test from Task 11, etc.) are completely unmodified.

## 16. Test Results

Focused Trip/Assignment ride-progress tests: all green (multiple runs during development — see Section 17 for the residual-data issue encountered along the way).

**Complete Dispatch suite, final run:** `./gradlew.bat :dispatch:test` → **BUILD SUCCESSFUL**. Aggregated from all XML reports: `tests=383 skipped=0 failures=0 errors=0` (375 from Task 11's own baseline + 8 new from this task).

**Pre-existing test-hygiene issue encountered again, investigated, and cleaned (not fixed at the source, consistent with Task 11's own precedent and this task's own "do not modify tests merely to make them pass" instruction):** `PostgreSQLAssignmentLifecycleTest` and `ProposalControllerPostgreSQLIntegrationTest` use fixed, non-randomized literal order/driver identifiers. Every successful run of these files leaves residual rows in the isolated `pios_dispatch_test` database that then collide with the *next* run — the same root cause Task 11 already diagnosed and worked around, not fixed. It recurred three times during this task's own development (each time confirmed via direct `psql` inspection before cleaning, each time cleaned via `DELETE` against only the isolated test database, `trips` rows removed before their parent `assignments` rows where the `V12` FK required it). **My own new test in `PostgreSQLAssignmentLifecycleTest`** was initially written with the same fixed-literal pattern and hit the identical problem on its second run; since it is a new test (not a pre-existing one this task must leave alone), it was corrected to use a randomized order id (`postgres-lifecycle-order-3-${UUID.randomUUID()}`) rather than perpetuating the anti-pattern — this is the one test-content change in this task that goes beyond a pure assertion-retarget, and it is called out here explicitly.

## 17. Production Safety Verification

- **Production PostgreSQL (`pios_dispatch`):** not queried, not touched. Every command in this task targeted `pios_dispatch_test` exclusively (`docs/TEST_DATABASE_ISOLATION.md`), confirmed via `psql -h 127.0.0.1 -U postgres -lqt` before any test run.
- **Production RabbitMQ:** not touched.
- **Production services:** not restarted.
- **Repository-wide build:** never run; only `:dispatch:test`, module-scoped, was used throughout.

## 18. Files Modified

`backend/dispatch/src/main/kotlin/com/pios/dispatch/application/DispatchAssignmentApplicationService.kt`, `backend/dispatch/src/main/kotlin/com/pios/dispatch/api/AssignmentController.kt`, `backend/dispatch/src/test/kotlin/com/pios/dispatch/api/AssignmentControllerTest.kt`, `backend/dispatch/src/test/kotlin/com/pios/dispatch/application/DispatchAssignmentApplicationServiceTest.kt`, `backend/dispatch/src/test/kotlin/com/pios/dispatch/persistence/AssignmentRepositoryLifecycleTest.kt`, `backend/dispatch/src/test/kotlin/com/pios/dispatch/persistence/PostgreSQLAssignmentLifecycleTest.kt`, `docs/ADR/ADR-063-Trip-as-a-New-Dispatch-Owned-Aggregate.md`, `docs/PIOS_TAXI_ARCHITECTURE_CONTRACT.md`, `docs/PIOS_TAXI_ARCHITECTURE_RATIFICATION.md`, `docs/EVENT_CATALOG.md`, `docs/INTERFACE_CONTRACTS.md`.

## 19. Files Created

`backend/dispatch/src/test/kotlin/com/pios/dispatch/application/DispatchAssignmentApplicationServiceRideProgressConvergenceTest.kt`, `docs/PIOS_TAXI_TASK_12_TRIP_CONVERGENCE_REPORT.md` (this file).

## 20. Files Deleted

None. No production code, test, or documentation was deleted. Only stale test *data* (specific rows in the isolated `pios_dispatch_test` database) was deleted, per Section 16.

## 21. Remaining Risks

- **The dual-publish window is open-ended.** No deprecation date is set for retiring `Assignment*` events or `Assignment`'s own dormant ride-progress code; this task did not set one (a product/scheduling decision, not a technical one).
- **`Assignment`'s ride-progress fields are now permanently dead code** unless a future task actually removes them — carrying real ongoing cognitive cost (a reader of `Assignment.kt` alone, without this report, would not know the methods are unreachable from production).
- **The pre-existing non-randomized-literal test-hygiene issue** (Section 16) will keep recurring on every alternating full-suite run of `PostgreSQLAssignmentLifecycleTest`'s two original tests and `ProposalControllerPostgreSQLIntegrationTest` until someone randomizes their identifiers — not fixed by this task, consistent with scope discipline, but now confirmed to affect this task's own area too.
- **Mid-Trip cancellation remains unmodeled** (ADR-063's own open question, unaffected by this task).
- **The separate Order Management event-consumption gap** (`OrderAssigned` has no consumer there; `AssignmentAccepted` is published by nothing) remains exactly as Task 11A left it — untouched, per this task's own Order Management Rule.

## 22. Explicit Statement of What Was NOT Changed

Not changed: any frontend file; Order Management (no new consumer, no binding switch, `AssignmentCompletedListener` untouched); Passenger Experience; Network Management; Identity; any database migration (no migration file was created or modified); `Assignment.kt`, `AssignmentStatus.kt`, `AssignmentArrived.kt`, `AssignmentStarted.kt`, `AssignmentCompleted.kt` (zero lines changed in any of these); First Refusal, Primary Driver, Circle of Trust routing, or any passenger-to-driver preference logic; RabbitMQ topology (no new exchange, queue, or binding — the two new `trip.*` routing keys are published to the same existing `dispatchEventsExchange`, with no queue bound to them, exactly like `AssignmentArrived`/`AssignmentStarted` already had none); production data, production database, or production broker.

---

**Per this task's own final instruction: STOP here.** Not continuing into First Refusal, Primary Driver, Network Management, or any frontend redesign. Waiting for architectural/engineering review.

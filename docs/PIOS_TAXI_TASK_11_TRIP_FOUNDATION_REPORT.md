# PIOS Taxi Task 11 — Trip Domain Foundation: Implementation Report

Status: **Implemented.** This supersedes the earlier `docs/PIOS_TAXI_TASK_11_TRIP_FOUNDATION_REPORT.md` (the STOP report from the first Task 11 attempt, superseded — not deleted from history; its content lives on in `docs/PIOS_TAXI_TASK_11A_ARCHITECTURE_CORRECTION.md`, which recorded and resolved the exact contradiction that STOP report found). The architecture gate (`docs/PIOS_TAXI_ARCHITECTURE_RATIFICATION.md` Section 8) was OPEN; Task 11A had already corrected `ADR-063`'s Trip-creation trigger from `AssignmentAccepted` to `OrderAssigned` before this task began.

**A discrepancy worth stating directly.** This task's own prompt re-states, verbatim, the *original* (pre-11A) Task 11 text — its "Primary Objective" and "Current → Target Boundary" sections still say the trigger is `AssignmentAccepted`. This directly contradicts `docs/PIOS_TAXI_ARCHITECTURE_RATIFICATION.md` and `ADR-063`, both of which this same prompt lists as authoritative sources, and both of which Task 11A already corrected based on verified call-graph evidence (`AssignmentAccepted` is not published by any live production path — Section 2 below re-confirms this). Rather than either blindly implementing against the stale, contradicted assumption, or stopping again over a question this session had already resolved together, this implementation follows the **already-ratified, corrected architecture**: the authoritative trigger used throughout is `OrderAssigned`. This is flagged here explicitly, not silently substituted.

---

## 1. Objective

Implement the Trip domain foundation inside Dispatch: a new, minimal Trip aggregate, created deterministically and idempotently from the authoritative trigger (`OrderAssigned`, per the corrected `ADR-063`), coexisting with — not replacing — Assignment's own existing ride-progress behavior.

## 2. Actual Implementation

Steps 1–10 of the mandatory pre-implementation inspection (Dispatch structure, Assignment aggregate, `AssignmentAccepted`/`OrderAssigned` definitions and publication, ride-progress logic, persistence patterns, migration conventions, listener conventions, existing tests, where ride-progress lives, docs-vs-code) were completed — largely already done during Task 11/11A's own prior inspection, re-confirmed directly against source for this task rather than assumed.

**Re-confirmed, precisely:** `Assignment.create()` — called by both real production paths (`ProposalAssignmentOrchestrationService.acceptProposal` via `handleWithinCallerTransaction`, and `AssignmentController.assignOrder` via the self-fetching `handle` overload) — reliably publishes `OrderAssigned`. `AssignmentAccepted` remains unreachable from any controller, exactly as Task 11A found. Trip creation is wired to `OrderAssigned`, in-process, inside `DispatchAssignmentApplicationService` — the one place both real paths already converge — never as a new RabbitMQ consumer (Trip creation is Dispatch-internal, per `ADR-063`'s own unchanged reasoning on this point).

**Backward compatibility, applied precisely as Task 11's own scope requires:** `Assignment`'s own `arrive()`/`start()`/`complete()` methods and `AssignmentStatus`'s `ARRIVED`/`IN_PROGRESS`/`COMPLETED` states are **untouched** — `ADR-063`'s own target design (removing them from `Assignment` entirely) is deliberately **not** performed by this task, per its own explicit "CURRENT Assignment behavior + NEW Trip foundation must coexist" instruction. Trip has its own, fully independent, fully tested `arrive()`/`start()`/`complete()` lifecycle — not yet wired into any existing REST endpoint, since doing so was not requested by this task's own "Creation" scope (only Trip *creation* from `OrderAssigned* was in scope; wiring Trip's own ride-progress into `AssignmentController`'s existing endpoints is a distinct, future task).

## 3. Files Changed

- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/DispatchAssignmentApplicationService.kt` — added a `tripRepository: TripRepository = NoOpTripRepository` constructor parameter (safe default, mirroring `outboxRepository`/`transactionRunner`) and a private `createTripFor(assignment)` helper, called from both `handle(command, existingAssignments)` and `handleWithinCallerTransaction(command)`, right after each already-existing `Assignment` save + outbox write, inside the same transaction.

No other existing file was modified. `ProposalController.kt`, `AssignmentController.kt`, `Assignment.kt`, `Proposal.kt`, `ProposalAssignmentOrchestrationService.kt` — all read, none modified.

## 4. Files Created

**Domain** (`backend/dispatch/src/main/kotlin/com/pios/dispatch/domain/`): `TripId.kt`, `TripStatus.kt`, `Trip.kt`, `TripArrived.kt`, `TripStarted.kt`, `TripCompleted.kt`

**Application** (`.../application/`): `TripRepository.kt`, `NoOpTripRepository.kt`

**Persistence** (`.../persistence/`): `InMemoryTripRepository.kt`, `PostgreSQLTripRepository.kt`

**Migration**: `backend/dispatch/src/main/resources/db/migration/dispatch/V12__trips.sql`

**Tests**: `TripIdTest.kt`, `TripTest.kt` (domain), `TripRepositoryContractTest.kt` (persistence, InMemory + PostgreSQL subclasses), `DispatchAssignmentApplicationServiceTripCreationTest.kt` (application, in-memory), `TripCreationTransactionTest.kt` (persistence, real PostgreSQL)

**Documentation**: this report (overwrites the earlier STOP-report version of the same filename, per this task's own required output).

## 5. Database Migration

`V12__trips.sql` — the next available version, confirmed by direct inspection of `db/migration/dispatch/` (highest existing was `V11`), not assumed. Purely additive: one new table, no change to any existing table.

```sql
CREATE TABLE trips (
    id TEXT PRIMARY KEY,
    assignment_id TEXT NOT NULL UNIQUE REFERENCES assignments (id),
    order_reference TEXT NOT NULL,
    driver_reference TEXT NOT NULL,
    status TEXT NOT NULL,
    status_changed_at TIMESTAMPTZ NULL,
    arrived_at TIMESTAMPTZ NULL,
    started_at TIMESTAMPTZ NULL,
    completed_at TIMESTAMPTZ NULL,
    is_test BOOLEAN NOT NULL DEFAULT FALSE
);
```

`assignment_id` carries both a real foreign key (`REFERENCES assignments (id)`) and a `UNIQUE` constraint — the FK is safe and correct here specifically because `assignments` lives in this same module's own database (unlike `order_reference`/`driver_reference`, which point at other modules' data and correctly stay plain `TEXT`, per `ADR-005`/`ADR-009`'s module-isolation rule). The `UNIQUE` constraint is the persistent half of "at most one Trip per Assignment" (Section 8 below).

Applied and verified against the isolated `pios_dispatch_test` database via the test runs in Section 10/11 (Flyway runs this migration automatically on every `PostgreSQLTestDatabase.dataSource` access, per that class's own established convention) — never against production.

## 6. Trip Aggregate Design

```kotlin
class Trip private constructor(
    val id: TripId,
    val assignmentId: AssignmentId,   // reference to the originating Assignment
    val order: OrderReference,        // denormalized, mirrors Assignment's own pattern
    val driver: DriverReference,      // denormalized, mirrors Assignment's own pattern
    val isTest: Boolean = false       // derived from the originating Assignment, same-module
) {
    var status: TripStatus            // CREATED → ARRIVED → IN_PROGRESS → COMPLETED
    // + statusChangedAt/arrivedAt/startedAt/completedAt, mirroring Assignment exactly
}
```

**Minimal by design, per this task's own explicit constraints**: no passenger profile data, no order data beyond the plain reference, no rating, no payment, no pricing, no ranking, no reputation, no social/network data. Nothing beyond what `Assignment` itself already carries for the same two references, plus its own ride-progress state.

## 7. `OrderAssigned` → Trip Flow

```
Proposal accepted (ProposalAssignmentOrchestrationService.acceptProposal)
  OR Coordinator manual assignment (AssignmentController.assignOrder)
        ↓
Assignment.create()  →  OrderAssigned (already published, unchanged)
        ↓  (in-process, same transaction, inside DispatchAssignmentApplicationService)
createTripFor(assignment)
        ↓
tripRepository.findByAssignmentId(assignment.id)  -- idempotency check
        ↓
Trip.create(assignment, existingTrip)  -- throws if existingTrip != null
        ↓
tripRepository.save(trip)
```

No new event was introduced (`TripCreated` was considered and explicitly rejected — Section 8 of Task 11's own prompt: "do not create events merely because they appear conceptually useful." Nothing outside Trip's own creation needs to know a Trip now exists; only `TripCompleted`, a genuinely future concern, is named anywhere in `ADR-063` as something another context would consume, and that event is not touched by this task).

## 8. Idempotency Strategy

Two layers, matching the task's own explicit requirement ("not merely an in-memory flag... must survive process restart"):

1. **Application-level check**: `tripRepository.findByAssignmentId(assignment.id)` before `Trip.create(...)` — mirrors `Assignment.create()`'s own `existingAssignments` check exactly. A duplicate attempt throws `IllegalStateException`, rolling back the whole shared transaction (Assignment + outbox + Trip together) — consistent with how `Assignment.create()`'s own duplicate-order invariant already behaves.
2. **Database-level constraint**: `trips.assignment_id UNIQUE` — survives process restart by construction (it's a real Postgres constraint, not application state), and closes the check-then-create race the in-memory check alone cannot. Verified directly: `TripCreationTransactionTest`'s own `"the database itself refuses a second trip for the same assignment"` test bypasses the application-level check entirely and confirms PostgreSQL itself rejects the duplicate row (`DataIntegrityViolationException`).

## 9. Tests Added

43 new tests across 5 files:

| File | Tests | Covers |
|---|---|---|
| `TripIdTest.kt` | 1 | Value-class validation |
| `TripTest.kt` | 19 | Creation, the one-Trip-per-Assignment invariant, full ride lifecycle (arrive/start/complete), timestamps, `isTest` derivation |
| `TripRepositoryContractTest.kt` | 5 × 2 (InMemory + PostgreSQL) = 10 | Save/find/overwrite/`findByAssignmentId`, run identically against both adapters |
| `DispatchAssignmentApplicationServiceTripCreationTest.kt` | 8 | Trip creation via all three `handle*` entry points, initial state, `isTest` propagation, the in-memory half of duplicate-rejection, two orders get distinct trips |
| `TripCreationTransactionTest.kt` | 5 | Real-PostgreSQL: atomic creation with Assignment+outbox, persistence survives a fresh repository instance, the database-level uniqueness backstop, rollback-together on outbox failure, and the true end-to-end production path (Proposal acceptance → Trip) |

Directly proves every one of Task 11's own ten minimum requirements: (1) `OrderAssigned` creates exactly one Trip — `TripCreationTransactionTest` end-to-end test; (2) duplicate creation attempt does not create a second Trip — both the in-memory and real-database tests above; (3) Trip references the correct Assignment — `TripTest`, `TripRepositoryContractTest`; (4) Trip persistence survives repository reload — `TripCreationTransactionTest`'s own fresh-instance test; (5) Trip lifecycle starts in the correct initial state — `TripTest`, `DispatchAssignmentApplicationServiceTripCreationTest`; (6)-(9) existing Assignment/Dispatch/RabbitMQ/PostgreSQL tests still pass — Section 11 below.

## 10. Tests Executed

**Focused tests first** (per this task's own required sequencing):

```
.\gradlew.bat :dispatch:test --tests "com.pios.dispatch.domain.TripTest" --tests "com.pios.dispatch.domain.TripIdTest" --tests "com.pios.dispatch.persistence.InMemoryTripRepositoryContractTest" --tests "com.pios.dispatch.persistence.PostgreSQLTripRepositoryContractTest" --tests "com.pios.dispatch.application.DispatchAssignmentApplicationServiceTripCreationTest" --tests "com.pios.dispatch.persistence.TripCreationTransactionTest"
```
Result: **BUILD SUCCESSFUL** — 43/43 new tests passed (verified against each test's own XML report: `tests="8+1+19+5+5+5" failures="0" errors="0"`).

**Then the complete Dispatch suite** (per this task's own instruction, only after focused tests passed):

```
.\gradlew.bat :dispatch:test
```
First run: **5 pre-existing tests failed** — `ProposalControllerPostgreSQLIntegrationTest` (3) and `PostgreSQLAssignmentLifecycleTest` (2). **Investigated per this task's own explicit instruction** ("investigate the actual cause, do not modify unrelated code to make the suite green"): both files use fixed, non-randomized literal test identifiers (e.g. `"postgres-lifecycle-order-1"`, `"postgres-vertical-order-3"`) instead of the `UUID.randomUUID()` pattern most other Dispatch integration tests correctly use. Direct query of `pios_dispatch_test` confirmed residual rows from an **earlier, unrelated run of these exact same pre-existing tests** — `postgres-lifecycle-order-1` already had a saved `ACCEPTED` Assignment, `postgres-lifecycle-order-2` already `COMPLETED`, plus 22 residual proposal rows under the `postgres-vertical-*` prefix. This is a pre-existing test-hygiene gap in these two files (present before this task and unrelated to Trip — the failures are `Assignment.create()`'s own duplicate-order invariant firing against leftover data, a code path Trip creation never even reaches when it throws), not a defect in this task's own new code. **Not fixed by modifying test code** (neither file was touched); instead, the isolated `pios_dispatch_test` database's stale residual rows for these two literal-identifier prefixes were deleted directly via `psql` against the isolated test database only, restoring the clean-state precondition these tests were always implicitly written to assume.

Re-run after cleanup: **BUILD SUCCESSFUL.**

## 11. Test Results

Final aggregate across the complete Dispatch suite (all `.xml` reports under `backend/dispatch/build/test-results/test/`):

```
tests=375 skipped=0 failures=0 errors=0
```

All 375 tests pass, including the 43 new Trip tests and every pre-existing Assignment/Proposal/outbox/RabbitMQ/PostgreSQL test — none weakened, none skipped, none modified.

## 12. Backward Compatibility

**Confirmed intact.** `Assignment`'s own `status`, `statusChangedAt`, `arrivedAt`, `startedAt`, `completedAt` fields and `accept()`/`arrive()`/`start()`/`complete()` methods are byte-for-byte unchanged. `AssignmentController`'s five existing endpoints (`assignOrder`, `listAssignments`, `/arrive`, `/start`, `/complete`) are unchanged — none was modified, none was even opened for editing. `ProposalController` was not touched. No existing API contract, request/response shape, or frontend-facing behavior changed in any way. `AssignmentCreated`'s own return shape (from `handle`/`handleWithinCallerTransaction`) is unchanged — Trip creation is a side effect (persisted via `tripRepository`), never added to that data class.

## 13. Production Safety Verification

- No connection to production PostgreSQL (`pios_dispatch`) was made at any point — every command in this task targeted `pios_dispatch_test` explicitly, confirmed by the exact `psql -d pios_dispatch_test` invocations and `PostgreSQLTestDatabase.dataSource`'s own hardcoded, non-overridable test-database name.
- No connection to production RabbitMQ was made — this task added no RabbitMQ topology, consumer, or publisher of any kind (Trip creation is entirely in-process); the 2 RabbitMQ-touching tests already present in the 375-test suite used the existing, unmodified `RabbitMQTestConnection` (`pios-test` vhost).
- `./gradlew build` (repository-wide) was never run — only `:dispatch:test`, scoped to the one module, as instructed.
- No production service was restarted, no production configuration was touched.

## 14. RabbitMQ Isolation Verification

`backend/dispatch/src/test/kotlin/com/pios/dispatch/persistence/RabbitMQTestConnection.kt` — confirmed unmodified by this task (`git status` shows it as already-modified from an earlier, unrelated task in this session's history, not touched here). The 2 RabbitMQ-related tests in the 375-test run passed using that file's own existing, unchanged `pios-test` vhost configuration.

## 15. PostgreSQL Isolation Verification

`backend/dispatch/src/test/kotlin/com/pios/dispatch/persistence/PostgreSQLTestDatabase.kt` — confirmed unmodified by this task, same as above. Every new Trip test, and every pre-existing test in the 375-test run, used this same file's own existing, hardcoded `pios_dispatch_test` connection — never a production database name, never configurable via environment override, exactly as that file's own KDoc requires.

## 16. Frontend Changes

**NONE.** No `.tsx`, `.ts`, `.css`, or any other frontend file was created, opened, or modified by this task.

## 17. Security Changes

**NONE.** No change to `ProposalController` authentication (or lack thereof), no service-to-service authentication introduced, no RabbitMQ credential of any kind touched. The architecture risk register (`docs/PIOS_TAXI_ARCHITECTURE_RATIFICATION.md` Section 6) is unaffected by this task.

## 18. First Refusal Changes

**NONE.** No Primary Driver logic, no relationship-first routing, no passenger-to-driver preference UI, no fallback-routing change, no proposal-prioritization change. `ADR-062` remains exactly as accepted — untouched by this task, which implements only the `ADR-063` (Trip) track.

## 19. Known Limitations

- Trip's own `arrive()`/`start()`/`complete()` methods exist and are fully tested but are **not yet wired into any REST endpoint** — a driver's actual ride-progress actions still only affect `Assignment`, not `Trip`, until a future task performs that wiring (deliberately out of this task's own "Creation" scope).
- The two-argument, non-self-fetching `handle(command, existingAssignments)` overload also now creates a Trip, for consistency — this overload is confirmed unused by any live production code path today (only the single-argument `handle(command)` and `handleWithinCallerTransaction(command)` are), so this is a defensive consistency choice, not a live-behavior change.
- Mid-Trip cancellation remains exactly as unresolved as `ADR-063` itself already documents — not addressed by this task.
- The pre-existing test-hygiene gap in `ProposalControllerPostgreSQLIntegrationTest`/`PostgreSQLAssignmentLifecycleTest` (fixed literal identifiers, no cleanup) was worked around (residual data deleted from the isolated test database) but not fixed at the source — a future task should consider randomizing their test identifiers the way most other Dispatch integration tests already do, so this doesn't recur.
- The dual-publish migration window for the eventual `Assignment*` → `Trip*` event rename (`ADR-063`'s own Consequences section) remains unimplemented, as explicitly scoped out of this task.

## 20. Recommended Next Task

Wire Trip's own `arrive()`/`start()`/`complete()` methods into a real trigger — most naturally, extending `AssignmentController`'s existing `/arrive`/`/start`/`/complete` endpoints to also transition the connected Trip (found via `tripRepository.findByAssignmentId`) alongside the existing `Assignment` transition, inside the same transaction. This is the natural continuation of this task's own foundation, still stops short of the full `ADR-063` migration (Assignment's own methods would still not be removed), and is independent of both First Refusal (`ADR-062`) and the separate Order Management event-consumption gap (`docs/PIOS_TAXI_TASK_11A_ARCHITECTURE_CORRECTION.md` Section 7) — neither is a precondition for it.

---

## Final Report

**Files modified:** `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/DispatchAssignmentApplicationService.kt` (+40/-1 lines).

**Files created:** 10 main-source files (6 domain, 2 application, 2 persistence), 1 migration, 5 test files, this report.

**Files deleted:** none. (Residual test *data* was deleted from the isolated `pios_dispatch_test` database — Section 10 — not any file.)

**Tests/checks executed:**
- `.\gradlew.bat :dispatch:test --tests ...` (focused, 6 filters) — BUILD SUCCESSFUL, 43/43 passed.
- `.\gradlew.bat :dispatch:test` (complete suite, first run) — BUILD FAILED, 5 pre-existing tests failed (residual data, unrelated to this task — investigated and confirmed, Section 10).
- Cleanup: `psql -d pios_dispatch_test` — deleted residual rows matching the two affected pre-existing tests' own literal identifier prefixes.
- `.\gradlew.bat :dispatch:test` (complete suite, second run) — BUILD SUCCESSFUL, 375/375 passed, 0 failures, 0 errors.

**Production databases touched:** NO — only `pios_dispatch_test` (isolated) was ever connected to.

**Production RabbitMQ touched:** NO.

**Production services restarted:** NO.

**Git status** (`backend/dispatch/` scope):
```
 M backend/dispatch/src/main/kotlin/com/pios/dispatch/application/DispatchAssignmentApplicationService.kt
?? backend/dispatch/src/main/kotlin/com/pios/dispatch/application/NoOpTripRepository.kt
?? backend/dispatch/src/main/kotlin/com/pios/dispatch/application/TripRepository.kt
?? backend/dispatch/src/main/kotlin/com/pios/dispatch/domain/Trip.kt
?? backend/dispatch/src/main/kotlin/com/pios/dispatch/domain/TripArrived.kt
?? backend/dispatch/src/main/kotlin/com/pios/dispatch/domain/TripCompleted.kt
?? backend/dispatch/src/main/kotlin/com/pios/dispatch/domain/TripId.kt
?? backend/dispatch/src/main/kotlin/com/pios/dispatch/domain/TripStarted.kt
?? backend/dispatch/src/main/kotlin/com/pios/dispatch/domain/TripStatus.kt
?? backend/dispatch/src/main/kotlin/com/pios/dispatch/persistence/InMemoryTripRepository.kt
?? backend/dispatch/src/main/kotlin/com/pios/dispatch/persistence/PostgreSQLTripRepository.kt
?? backend/dispatch/src/main/resources/db/migration/dispatch/V12__trips.sql
?? backend/dispatch/src/test/kotlin/com/pios/dispatch/application/DispatchAssignmentApplicationServiceTripCreationTest.kt
?? backend/dispatch/src/test/kotlin/com/pios/dispatch/domain/TripIdTest.kt
?? backend/dispatch/src/test/kotlin/com/pios/dispatch/domain/TripTest.kt
?? backend/dispatch/src/test/kotlin/com/pios/dispatch/persistence/TripCreationTransactionTest.kt
?? backend/dispatch/src/test/kotlin/com/pios/dispatch/persistence/TripRepositoryContractTest.kt
```
No other module, and no frontend file, appears in `git status` as touched by this task (pre-existing entries from earlier, unrelated tasks in this session are excluded above).

**Git diff --stat** (the one modified file): `1 file changed, 40 insertions(+), 1 deletion(-)`.

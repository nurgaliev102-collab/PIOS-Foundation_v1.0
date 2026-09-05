# PIOS Taxi — Task 23: Assignment API Security Remediation

Status: **Implemented.** Closes the unauthenticated Assignment `arrive`/`start`/`complete` write surface Task 22 audited, using exactly the mechanism Task 21 already established for `ProposalController`. No new authentication architecture, no new ADR, no production or RabbitMQ connection, no production service restarted, nothing committed or pushed.

## 1. Executive Summary

`AssignmentController` now requires a `Bearer` session token, verified against `Assignment.driver.driverId`, on `arrive`, `start`, and `complete` — the exact three endpoints Task 22 identified as fully open. The fix reuses `SessionTokenVerifier` unmodified, adds it as a new required constructor parameter (previously absent entirely, not merely unused), and touches no domain, application-service, migration, or RabbitMQ file. `listAssignments` and the deprecated `assignOrder` are untouched, per Task 23's own explicit scope (narrower than Task 22's own broader recommendation, which had also named `assignOrder` — this task deliberately implements only what its own Phase 1 asked for). 18 new backend tests (13 in-memory, 5 against real PostgreSQL) plus 1 new frontend test prove anonymous/wrong-driver/correct-driver behavior end to end, including that a rejected call never advances the real, persisted Trip. Full Dispatch suite: 453/453. Full frontend suite: 227/227.

## 2. Fresh Phase 0 Verification

Re-read directly from current source, not assumed from Task 22's report:

- **`AssignmentController.kt`** — confirmed constructor `(dispatchAssignmentApplicationService, assignmentRepository, tripRepository = NoOpTripRepository)`, no identity collaborator of any kind; confirmed exact endpoint set (`assignOrder`, `listAssignments`, `arrive`, `start`, `complete`); confirmed no `/accept` mapping exists.
- **`DispatchAssignmentApplicationService.kt`** — confirmed `arriveAssignment`/`startAssignment`/`completeAssignment` each self-fetch the `Assignment` by id, then resolve/self-heal the connected `Trip` via `tripFor`, then call `Trip.arrive()`/`start()`/`complete()`; confirmed no identity parameter on any of these methods.
- **`Assignment.kt`** — confirmed `driver: DriverReference` is a real, populated constructor field, exposed via the aggregate's own public property — the exact shape needed for the check.
- **`Trip.kt`** — confirmed `arrive()`/`start()`/`complete()` each check only `TripStatus`, no identity.
- **`SessionTokenVerifier.kt`** — confirmed `verify(authorizationHeader: String?): VerifiedToken?` returning `VerifiedToken(sub, drv)`, unmodified since Task 21; confirmed it is a plain `@Component` with no dependency on `AssignmentController`'s own module boundary, safe to inject into a second controller.
- **`OwnerCredentialGate.kt`** — read again to confirm it was *not* needed for this task's own narrower scope (Task 23's Phase 1 names only `SessionTokenVerifier`, for `arrive`/`start`/`complete`; `assignOrder` was not in scope — Section 6 below).
- **`ProposalController.kt`** (post-Task-21) — re-confirmed the exact 401→404→403 ordering `acceptProposal`/`declineProposal` already use, reused here verbatim for `arrive`/`start`/`complete`.
- **`docs/PIOS_TAXI_TASK_22_ASSIGNMENT_SECURITY_AUDIT.md`** — re-read in full; every claim it made was re-verified against current source above and found accurate. No contradiction was found — **Phase 0's own STOP trigger ("fresh verification contradicts Task 22") did not fire.**
- **Absence of internal callers** — re-confirmed by a repository-wide search: `arriveAssignment`, `startAssignment`, `completeAssignment` are each called from exactly one place, `AssignmentController` itself. No First-Refusal-style bypass exists for any of the three.
- **Frontend callers** — re-confirmed `DriverHome.tsx`'s `respondToAssignment` is the sole real caller of the three mutation endpoints; `RideRequest.tsx` and `todayData.ts` only ever read (`GET`).

No contradiction with Task 22 was found. Proceeding to implementation.

## 3. Exact Files Modified

**5 files, plus this report.**

1. `backend/dispatch/src/main/kotlin/com/pios/dispatch/api/AssignmentController.kt` — the only production/`main` source change.
2. `backend/dispatch/src/test/kotlin/com/pios/dispatch/api/AssignmentControllerTest.kt` — every existing `arrive`/`start`/`complete` call updated to supply the now-required token; 13 new tests.
3. `backend/dispatch/src/test/kotlin/com/pios/dispatch/api/AssignmentControllerPostgreSQLSecurityTest.kt` — **new file**, 5 tests against real PostgreSQL.
4. `frontend/src/pages/DriverHome/DriverHome.tsx` — `respondToAssignment` now sends `Authorization: Bearer ${identity.token}`.
5. `frontend/src/pages/DriverHome/DriverHome.test.tsx` — 1 new test asserting that header.

**Not touched:** `Assignment.kt`, `Trip.kt`, `DispatchAssignmentApplicationService.kt`, `ProposalController.kt`, `ProposalApplicationService.kt`, any First Refusal file, any Order Management or Passenger Experience file, any migration, any RabbitMQ topology file, `OwnerCredentialGate.kt` (read, not modified), `listAssignments`, `assignOrder` — confirmed by `git diff --stat` immediately before writing this report (Section 23).

## 4. Authentication Implementation

`AssignmentController`'s constructor gained one new, required parameter: `sessionTokenVerifier: SessionTokenVerifier` — the same class, unmodified, `ProposalController` already uses. `arrive`, `start`, and `complete` each gained a `@RequestHeader("Authorization", required = false) authorization: String? = null` parameter. No new class, no new configuration property, no new secret.

## 5. Authorization Implementation

Identical shape to `ProposalController.acceptProposal`/`declineProposal` (Task 21), applied to three methods instead of two:

```
val id = AssignmentId(assignmentId)                         // 400 if malformed (unchanged)
val verified = sessionTokenVerifier.verify(authorization)
    ?: return 401
val assignment = assignmentRepository.findById(id)
    ?: return 404
if (verified.drv != assignment.driver.driverId) return 403
// ... existing service call, unchanged
```

## 6. Anonymous-Caller Behavior

`arrive`/`start`/`complete` with no `Authorization` header (or a malformed one, e.g. wrong scheme): **401**, before any repository lookup — proven by `AssignmentControllerPostgreSQLSecurityTest`'s own `no Authorization header, backed by PostgreSQL, does not mutate the real Trip` test that the connected Trip's own status is untouched (still `CREATED`, the state `Assignment.create`'s own Trip-creation side effect already leaves it in — Section 10).

## 7. Wrong-Driver Behavior

A validly-signed token naming a *different* `drv` than `assignment.driver.driverId` (or a passenger-only token, `drv == null`): **403**, proven both in-memory (`AssignmentControllerTest`) and against real PostgreSQL (`AssignmentControllerPostgreSQLSecurityTest`, including the two-step and three-step sequences: wrong driver cannot advance an already-`ARRIVED` or already-`IN_PROGRESS` real Trip either).

## 8. Correct-Driver Behavior

A validly-signed token whose `drv` equals `assignment.driver.driverId`: proceeds exactly as before this task — 200, same response shape, same Trip transition. Proven by updating every pre-existing passing test to supply this token and confirming they still pass unmodified in substance (Section 13), plus a new, explicit real-PostgreSQL test carrying one Assignment through `arrive` → `start` → `complete` with the same token throughout.

## 9. Unknown-Assignment Behavior

**Unchanged: 404**, and — new, explicitly proven, not merely assumed — this is reached only *after* a validly-signed token is presented; a request with no token at all against an unknown id still returns 401 (Section 6), never leaking whether the id exists to a fully anonymous caller. A dedicated test (`an unknown assignment id with a valid token still returns 404, not 403`) confirms the token-then-lookup ordering explicitly, not just implicitly through existing 404 tests.

## 10. Assignment → Trip Mutation Preservation

No change to Task 12's own architecture: `AssignmentController` still calls `DispatchAssignmentApplicationService.arriveAssignment`/`startAssignment`/`completeAssignment` unchanged, which still transition the connected `Trip`, unchanged. The authentication check runs entirely inside the controller, strictly before that call — confirmed by the code itself (Section 5) and by `AssignmentControllerPostgreSQLSecurityTest`'s own assertions reading `tripRepository.findByAssignmentId(...)` directly after a rejected call.

**One thing this task's own verification surfaced, not a regression:** `DispatchAssignmentApplicationService.handle` (Assignment creation) already creates a connected `Trip` — in `TripStatus.CREATED` — the moment the Assignment itself is created, before any `arrive` call. The first version of this task's own new PostgreSQL test asserted "no Trip exists" after a rejected `arrive`, which failed — not because the remediation was wrong, but because that assertion was wrong about when Trip creation actually happens (Task 11's own already-existing, unrelated behavior). Corrected to assert the Trip's own *status* remains `CREATED` (i.e., the rejected call did not advance it), which is what the security guarantee actually is. Documented here in full, not silently fixed and hidden (Section 18).

Confirmed still true after this change: `arrive` still transitions Trip to `ARRIVED`; `start` still transitions Trip to `IN_PROGRESS`; `complete` still transitions Trip to `COMPLETED`; the Assignment REST response shape (`AssignmentResponse`) is byte-for-byte unchanged; the three URLs are unchanged; no event name, routing key, or outbox payload shape was touched.

## 11. Frontend Changes

Exactly one, in `DriverHome.tsx`'s `respondToAssignment`: added `headers: { Authorization: `Bearer ${identity.token}` }` to the existing `request(...)` call, and a defensive `!identity` early-return alongside the pre-existing `submitting` guard (mirrors Task 21's own identical addition to the neighboring `respondToProposal`, moments away in the same file). No UI, route, state model, response type, or API path was touched. `identity.token` was already held in this exact component's own scope before this change (used elsewhere, e.g. `loadProposals`) — nothing new was fetched or stored.

## 12. Tests Added/Changed

**`AssignmentControllerTest.kt`** (in-memory): every pre-existing `arrive`/`start`/`complete` call updated to pass a `driverToken(...)` matching the assignment's own driver (12 call sites across 5 pre-existing tests). 13 new tests: no-header rejected (arrive/start/complete), malformed-header rejected, correct-driver succeeds (arrive/start/complete), wrong-driver rejected — IDOR (arrive/start/complete), passenger-only-token rejected, unknown-id-with-valid-token still 404 (not 403), and pre-existing business validation (the 409 for calling `start` before `arrive`) confirmed unchanged by the new auth check.

**`AssignmentControllerPostgreSQLSecurityTest.kt`** (new file, real PostgreSQL): 5 tests — wrong-driver `arrive` does not mutate the real Trip; no-header `arrive` does not mutate the real Trip; the correct driver's own token still moves the real Trip through the full `arrive`→`start`→`complete` sequence; wrong driver cannot move an already-`ARRIVED` real Trip to `IN_PROGRESS`; wrong driver cannot move an already-`IN_PROGRESS` real Trip to `COMPLETED`. Every identifier is `UUID.randomUUID()`-based, per this task's own Phase 5 instruction — no fixed literal was introduced.

**`DriverHome.test.tsx`**: 1 new test asserting the `Authorization` header on `POST /v1/assignments/:id/arrive`.

**Proving `ProposalController`/`ProposalApplicationService` remain unaffected:** not re-tested here (out of this task's own scope) — confirmed instead by `git diff --stat` showing zero changes to either file (Section 3), and by the full Dispatch suite (Section 15) including `ProposalControllerTest`/`ProposalControllerPostgreSQLIntegrationTest` passing unmodified.

## 13. Focused Test Results

| Test class | Result |
|---|---|
| `AssignmentControllerTest` | **25/25 passed** (12 pre-existing, updated, + 13 new) |
| `AssignmentControllerPostgreSQLSecurityTest` (new) | **5/5 passed** |
| `DriverHome.test.tsx` (frontend, focused file) | **13/13 passed** (12 pre-existing + 1 new) |

## 14. Integration Test Results

Included in Section 13 above (`AssignmentControllerPostgreSQLSecurityTest`) — real, isolated `pios_dispatch_test` PostgreSQL, real `PostgreSQLAssignmentRepository`/`PostgreSQLTripRepository`, not mocks. All 5 passed after correcting the one test-assertion error described in Section 10.

## 15. Full Dispatch Test Results

**453/453 passed, 0 skipped, 0 failures, 0 errors.** (435 before this task, Task 21's own baseline, +18 new: 13 in `AssignmentControllerTest`, 5 in the new `AssignmentControllerPostgreSQLSecurityTest`.)

## 16. Frontend Test Results

`npx vitest run` (full suite): **227/227 passed**, 24 files (226 before this task, +1 new). `npx tsc -b`: **zero new errors** — the one pre-existing, unrelated error (`aiProvider.test.ts`, the same `pilotAnalytics`/`ai-advisor` gap every prior task in this chain has already found and confirmed pre-existing) is unchanged in cause and location.

## 17. Regression Analysis

No regression found anywhere in either full suite. The one failure encountered during this task's own work (Section 10's own Trip-existence assertion) was a bug in a test this task itself was writing, caught and fixed before being reported as passing — not a regression in production code, and not left silently corrected.

## 18. Test-Data Cleanup, If Any

**Yes, the same known, recurring, pre-existing collision every task in this chain has encountered.** The first full Dispatch run after this task's own changes hit the familiar failure: `ProposalControllerPostgreSQLIntegrationTest` (3 tests) and `PostgreSQLAssignmentLifecycleTest` (2 tests) — both files this task did not modify — failed due to residual rows from prior runs matching their own fixed, non-randomized literal identifiers (`postgres-vertical-*`, `postgres-lifecycle-*`). Per this task's own Phase 5 discipline: (1) determined the failure was stale isolated test data, not a code regression, by inspecting the failing tests' own names and confirming neither file was touched by this task (`git diff --stat`, Section 3); (2) confirmed both failing test classes are pre-existing, not new; (3) cleaned only the affected rows in the isolated `pios_dispatch_test` database via a targeted `DELETE` (respecting FK order: `trips` → `assignments` → `proposals`), never touching production, never modifying the test files themselves; (4) re-ran — clean, 453/453 (Section 15). This task's own two new test files use exclusively `UUID.randomUUID()`-based identifiers, so they cannot themselves contribute to this same collision pattern in a future run.

## 19. Security Impact

The three endpoints Task 22 identified as fully open (`arrive`, `start`, `complete`) now require proof of driver identity matching the Assignment's own `driver` field. This closes both the anonymous-caller and cross-driver-mutation exposure Task 22 documented, and — because these three transitions act on the connected Trip (Task 12) — closes the indirect Trip-mutation path Task 22's own Section 11 traced in detail: an unauthorized caller can no longer advance, or cause the self-healing creation of, a Trip it has no right to touch.

## 20. Residual Security Risks

Unchanged from Task 22's own Section 21, since this task's scope was deliberately narrower than that section's own full remediation proposal:

- **`assignOrder` (`POST /v1/assignments`) remains fully unauthenticated.** Task 22 recommended owner-credential gating for it; Task 23's own Phase 1 named only `arrive`/`start`/`complete`, so this task did not touch it. It has no real frontend caller today (re-confirmed, Section 2), so this is dormant, not active, risk — but it is still open, and a future task should close it deliberately rather than assume this one did.
- **`listAssignments` remains deliberately unauthenticated** — unchanged, per Task 22's own finding that this is a pre-existing, ratified decision, not a gap.
- **`AssignmentController`'s own sibling gap on `assignOrder`, and the still-unaddressed `ProposalController`-side residual (passenger ownership of `create`, Task 20/21's own disclosed gap)** both remain exactly as previously documented — neither is this task's to close.
- **`DispatchAssignmentApplicationService.acceptAssignment` remains unreachable dead code** — confirmed still true (Section 2), not addressed, not a security question since nothing can reach it over HTTP.

## 21. STOP-Condition Evaluation

All ten conditions checked explicitly; none fired.

1. **`SessionTokenVerifier` cannot reliably identify the driver?** False — confirmed working correctly by 18 new passing tests across both in-memory and real-PostgreSQL infrastructure.
2. **`Assignment.driver` does not contain a trustworthy driver identity?** False — it is the same aggregate-owned field, populated at creation from the same trusted `AssignOrderCommand`/Proposal-acceptance path `Proposal.driver` already relies on.
3. **Correct authorization semantics cannot be established?** False — driver-only, unambiguously, confirmed by the sole real caller being a driver-facing screen (Section 2).
4. **A frontend caller legitimately operates without a driver identity and the endpoint cannot distinguish it?** False — no such caller exists (Section 2); `RideRequest.tsx`/`todayData.ts` never call the mutating endpoints.
5. **Authentication would require changing the public API contract?** False — same URLs, same request/response shapes, only a header addition (Section 10).
6. **The fix requires changing Trip/Assignment architecture?** False — zero changes to either aggregate (Section 3).
7. **A new product decision is required?** False — this is access control, not product behavior.
8. **A new ADR appears necessary?** False — direct reuse of an already-Accepted mechanism (ADR-055), on a second controller, exactly as Task 20/22 already concluded.
9. **Production infrastructure would need to be contacted?** False — everything was proven against source, the isolated `pios_dispatch_test` database, and local builds/tests only.
10. **Another architectural/security boundary makes the minimal fix unsafe?** False — none was found; Section 10's own Trip-creation-timing discovery was a test-assertion correction, not a boundary that made the fix itself unsafe.

## 22. Production-Safety Verification

- **Production PostgreSQL touched: NO.** Every `psql` command in this task explicitly targeted `pios_dispatch_test`.
- **Production RabbitMQ touched: NO.** No RabbitMQ connection of any kind was made — none of this task's tests use RabbitMQ infrastructure at all (Assignment's own REST surface has no RabbitMQ involvement).
- **Production services restarted: NO.** No `sc start`/`sc stop`/`Restart-Service`, no `gradlew bootRun`, no direct `java -jar` invocation — only `gradlew compileKotlin`/`compileTestKotlin`/`test` and `npx tsc`/`npx vitest`.
- **Production deployment performed: NO.**
- **Commit made: NO.**
- **Push performed: NO.**

## 23. Git Status

```
Modified (this task): backend/dispatch/src/main/kotlin/com/pios/dispatch/api/AssignmentController.kt
                       backend/dispatch/src/test/kotlin/com/pios/dispatch/api/AssignmentControllerTest.kt
                       frontend/src/pages/DriverHome/DriverHome.tsx
                       frontend/src/pages/DriverHome/DriverHome.test.tsx
New (this task):       backend/dispatch/src/test/kotlin/com/pios/dispatch/api/AssignmentControllerPostgreSQLSecurityTest.kt
                       docs/PIOS_TAXI_TASK_23_ASSIGNMENT_SECURITY_REMEDIATION_REPORT.md
```

Everything else in the working tree is the same, already-disclosed baseline this entire task chain has carried since Task 17 (the unrelated design-system/onboarding/`ai-advisor`/tooling work, plus every prior task's own report) — confirmed unchanged by `git status --short` immediately before writing this report.

## 24. Recommended Next Task

**Task 24 (optional, low priority)** — close `assignOrder`'s own residual gap (owner-credential-only, mirroring `ProposalController.createProposal`'s owner branch), if and when a real caller for it is ever built; until then, this is not urgent (Section 20). No task should proceed to deploying any of Tasks 11–23 without first satisfying Task 19's own Deployment Gate (Part 3) — this remediation should travel with that same, already-planned, still-not-yet-authorized deployment, not trigger a separate one.

## Conclusion

**PASS — Assignment API security remediation complete.**

`arrive`, `start`, and `complete` now require and correctly enforce driver-identity verification, reusing `SessionTokenVerifier` exactly as `ProposalController` already does. 18 new backend tests (including 5 against real PostgreSQL, proving the real Trip is not mutated by a rejected call) and 1 new frontend test all pass; the full Dispatch suite (453/453) and full frontend suite (227/227) pass with zero regressions. Not deployed — awaiting the same authorization and sequencing Task 19's own Deployment Gate already established for the rest of this chain. Not proceeding to Task 24 automatically, per this task's own closing instruction.

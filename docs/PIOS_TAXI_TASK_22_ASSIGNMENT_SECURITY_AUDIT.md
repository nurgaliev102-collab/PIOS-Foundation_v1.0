# PIOS Taxi — Task 22: Assignment API Security Audit

Status: **Read-only audit. No code, test, migration, or configuration file was changed. No production or RabbitMQ connection was made. No service was restarted. Nothing was committed.**

## 1. Executive Summary

`AssignmentController` has **exactly the same class of vulnerability** Task 20 found and Task 21 fixed in `ProposalController` — and in one respect a narrower but still real one: it does not merely lack a caller-identity check on a few methods, it has **no authentication infrastructure at all**. `SessionTokenVerifier` and `OwnerCredentialGate` are not constructor-injected into `AssignmentController`; they are simply absent. Every one of `arrive`, `start`, and `complete` can be called by any anonymous HTTP client, for any `assignmentId`, regardless of which driver the Assignment actually belongs to — and because these three transitions now act on the connected **Trip** aggregate (Task 12's own convergence), the same unauthenticated call also mutates Trip, including self-healing-creating one if none exists yet. The deprecated `assignOrder` (`POST /v1/assignments`) is likewise fully open, though — verified directly, not assumed — it has zero real frontend caller today. `GET /v1/assignments?orderId=` is also unauthenticated, but this is a pre-existing, explicitly documented, deliberate decision (mirroring Proposal's own `?orderId=` precedent), not a newly-found gap, and this audit does not recommend changing it.

Assignment carries a `driver: DriverReference` field — the same shape `Proposal.driver` already has, which Task 21's remediation already keyed its own driver-identity check on. This means the fix is directly, mechanically analogous to Task 21's own: reuse `SessionTokenVerifier`, compare its `drv` claim against `assignment.driver.driverId`. No STOP condition (Section 22) fires. **Conclusion: FAIL — remediation required**, narrowly scoped, no new ADR needed, no architecture change needed.

## 2. Exact Assignment Endpoints Found

Read directly from `backend/dispatch/src/main/kotlin/com/pios/dispatch/api/AssignmentController.kt`, `@RequestMapping("/v1/assignments")`:

| Method | Path | Handler | Status |
|---|---|---|---|
| `POST` | `/v1/assignments` | `assignOrder` | `@Deprecated` (Sprint IMPLEMENTATION-004), still fully functional |
| `GET` | `/v1/assignments?orderId=` | `listAssignments` | Active |
| `POST` | `/v1/assignments/{assignmentId}/arrive` | `arrive` | Active |
| `POST` | `/v1/assignments/{assignmentId}/start` | `start` | Active |
| `POST` | `/v1/assignments/{assignmentId}/complete` | `complete` | Active |

**No `/accept` endpoint exists on this controller.** `DispatchAssignmentApplicationService.acceptAssignment` exists and is fully implemented, but no `@PostMapping` anywhere in this controller (or, confirmed by a repository-wide search of `backend/dispatch/src/main/kotlin`, anywhere else) ever calls it. It is dead code from the application layer's own point of view — exercised only by tests. This is a pre-existing, narrow finding this audit surfaces, not something Task 22 was asked to fix.

## 3. Complete Endpoint → Service → Domain Call Graph

```
POST /v1/assignments (assignOrder, deprecated)
  -> DispatchAssignmentApplicationService.handle(AssignOrderCommand)
       -> Assignment.create(order, driver, existingAssignments)   [domain: enforces one-active-assignment-per-order only]
       -> createTripFor(assignment) -> Trip.create(assignment)     [domain: enforces one-Trip-per-Assignment only]
     No identity of any kind is read, checked, or required anywhere in this path.

GET /v1/assignments?orderId= (listAssignments)
  -> assignmentRepository.findByOrder(OrderReference)  [direct repository read, no application service]
     No identity read or required -- deliberate, pre-existing (Section 6).

POST /v1/assignments/{id}/arrive
  -> DispatchAssignmentApplicationService.arriveAssignment(ArriveAssignmentCommand)
       -> assignmentRepository.findById(id) -- 404 if missing
       -> tripFor(assignment)  [self-healing: creates a Trip on the spot if none connected yet]
       -> Trip.arrive()        [domain: only checks TripStatus == CREATED]
     No identity of any kind is read, checked, or required anywhere in this path.

POST /v1/assignments/{id}/start   -- identical shape, Trip.start() [checks TripStatus == ARRIVED]
POST /v1/assignments/{id}/complete -- identical shape, Trip.complete() [checks TripStatus == IN_PROGRESS]

(No REST path reaches DispatchAssignmentApplicationService.acceptAssignment -- Section 2.)
```

Every domain-level `check()` in `Assignment.kt` and `Trip.kt` (read directly, this session) validates only the aggregate's own **current status** — none references, reads, or compares any caller identity, token, or credential. Confirmed by reading both classes in full: no such parameter exists on any method signature.

## 4. Authentication Findings

`AssignmentController`'s own constructor: `(dispatchAssignmentApplicationService, assignmentRepository, tripRepository)` — **no `SessionTokenVerifier`, no `OwnerCredentialGate`, no `Authorization` header parameter anywhere in the class.** This is a stronger absence than `ProposalController` had before Task 21: that controller already held both collaborators (just unused on four methods); `AssignmentController` holds neither. Confirmed by reading the full file (Section 3 of this report reproduces the relevant structure) and by the constructor signature `AssignmentControllerTest.kt` itself uses to build it (`AssignmentController(service, repository, tripRepository)` — three arguments, no fourth or fifth for identity).

## 5. Authorization Findings

None exist. No method on `AssignmentController`, `DispatchAssignmentApplicationService`, `Assignment`, or `Trip` compares a caller's identity against the Assignment's own `driver` field, or against any other notion of ownership. The only "authorization" in the whole path is implicit and purely structural: `Assignment`'s own private constructor (only `create` can make one) and `Trip`'s own private constructor (only `create`, called from inside `DispatchAssignmentApplicationService`, can make one) — this prevents a caller from fabricating an Assignment/Trip out of thin air, but says nothing about who may act on one that already exists.

## 6. Anonymous-Caller Matrix

| Endpoint | Classification | Basis |
|---|---|---|
| `assignOrder` | **ALLOWED** | No auth check of any kind; confirmed by full source read |
| `listAssignments` | **ALLOWED** | Deliberate, documented (Section 2's own `todayData.ts` KDoc: "unauthenticated, unaffected by ADR-060") |
| `arrive` | **ALLOWED** | No auth check of any kind |
| `start` | **ALLOWED** | No auth check of any kind |
| `complete` | **ALLOWED** | No auth check of any kind |

## 7. Wrong-Driver Matrix

| Endpoint | Classification | Basis |
|---|---|---|
| `arrive` | **ALLOWED** | Driver B can call `POST /v1/assignments/{driverA'sAssignmentId}/arrive` with no token at all, let alone a mismatched one — nothing compares caller to `assignment.driver` |
| `start` | **ALLOWED** | Identical reasoning |
| `complete` | **ALLOWED** | Identical reasoning |
| `assignOrder` | **N/A** | Not a "wrong driver" scenario — this endpoint *assigns* a driver, it does not act as one; anyone may call it naming any driver, which is this endpoint's own long-standing, undisputed shape (mirrors `ProposeDriverCommand`'s own "the order and driver references are supplied by the caller" precedent) |

## 8. Known-ID Attack Matrix

| Endpoint | Classification | Basis |
|---|---|---|
| `arrive`/`start`/`complete` | **ALLOWED** | Only `assignmentId` gates the call; a guessed or otherwise-obtained UUID is sufficient. `assignmentId`s are not sequential (`UUID.randomUUID()`, `Assignment.create`), so blind guessing is impractical — but any id an attacker legitimately observes (e.g., their own prior assignment, a leaked log line, a shared device) still grants full mutation rights over an Assignment/Trip that is not theirs |
| `listAssignments` | **ALLOWED, by `orderId` not `assignmentId`** | Already the deliberate, documented shape (Section 6) |

## 9. Legitimate Caller Matrix

Determined from source, not assumed:

| Endpoint | Legitimate actor(s), per actual current callers | Evidence |
|---|---|---|
| `arrive`/`start`/`complete` | **Driver only** | The sole real caller is `DriverHome.tsx`'s own `respondToAssignment` (Section 12) — a driver-facing screen. `RideRequest.tsx` (passenger) only ever reads (`GET`), confirmed by a repository-wide search for `/v1/assignments` across `frontend/src` — no passenger-facing write call exists anywhere. |
| `listAssignments` | **Driver (read own), Passenger (read own order), Owner (read all)** | Three real callers found: `DriverHome.tsx` (own accepted orders), `RideRequest.tsx` (own order's status), `OwnerControlCenter/todayData.ts` (all, for the owner console) — all read-only, already deliberately unauthenticated (Section 6) |
| `assignOrder` | **Coordinator/owner, conceptually — but zero real caller today** | A repository-wide search for a literal `'/v1/assignments'` POST call (excluding the `/{id}/...` sub-paths) across `frontend/src` returns nothing. `Coordinator.tsx`'s own manual-assignment flow (`handleAssign`) calls `POST /v1/proposals`, not `POST /v1/assignments` — confirmed by direct read of that file (also the subject of Task 21's own remediation). This endpoint is dormant, not merely deprecated in name. |

Per this task's own instruction not to assume every endpoint is driver-only: `listAssignments` genuinely is not (three legitimate actor types, already correctly left open); `assignOrder` genuinely is not driver-facing at all (a coordinator/owner action, by the shape of its own request body and its own KDoc, even though nothing calls it today).

## 10. Internal Caller Analysis

Checked directly (repository-wide search for each method name across `backend/dispatch/src/main/kotlin`), not inferred:

- **`arriveAssignment`, `startAssignment`, `completeAssignment`** — called from exactly one place each: `AssignmentController` itself. **No internal/bypass caller exists for any of the three ride-progress transitions.** This is a materially simpler situation than Proposal's `create` (which First Refusal calls internally, in-process, and must keep working unauthenticated at the application-service layer) — a controller-level identity check on `arrive`/`start`/`complete` has **zero risk** of breaking any internal execution path, because there is none to break.
- **`handle`/`handleWithinCallerTransaction` (Assignment creation)** — two callers: `AssignmentController.assignOrder` (dormant, Section 9) and `ProposalAssignmentOrchestrationService.acceptProposal` (real, internal, in-process). The latter is reached **only after** `ProposalController.acceptProposal` has already verified, since Task 21, that the caller's own token names the exact driver the Proposal was for — so Assignment *creation* via the real production path (accepting a Proposal) is **already transitively protected today**, a direct, positive consequence of Task 21's own work that this audit independently confirms rather than assumes.
- **`acceptAssignment`** — no caller anywhere in main source (Section 2).

## 11. Assignment → Trip Mutation Path

Answered directly from `Trip.kt`/`DispatchAssignmentApplicationService.kt` (both read in full this session):

1. **ARRIVED is performed by `Trip.arrive()`** — not `Assignment.arrive()`. `Assignment`'s own `arrive` method still exists (kept, unremoved, per Task 12's own explicit instruction) but is never called by `DispatchAssignmentApplicationService.arriveAssignment` any more.
2. **IN_PROGRESS is performed by `Trip.start()`**, same reasoning.
3. **COMPLETED is performed by `Trip.complete()`**, same reasoning.
4. **The REST endpoint supplies `assignmentId`** — never a `tripId`. No Trip identifier is ever part of this contract; Trip is entirely internal to Dispatch's own resolution.
5. **Assignment resolves its Trip via `tripFor(assignment)`** = `tripRepository.findByAssignmentId(assignment.id)`, **self-healing**: if no Trip exists yet for this Assignment (e.g. a pre-Task-11 Assignment, or simply one whose Trip was never separately created), one is created on the spot, inline, as part of servicing the request.
6. **Authorization is checked neither before nor after the Trip transition — it is checked nowhere in this path at all.** There is no "before" step to compare to "after"; the honest answer is that no authorization check exists anywhere between the HTTP request arriving and the Trip's own status changing.
7. **Yes — an unauthorized caller can mutate Trip indirectly through Assignment, unconditionally**, including causing a Trip to be created that did not previously exist, purely by knowing/obtaining a valid `assignmentId`. This is not a theoretical extension of the Assignment-level gap; it is the literal, direct effect of calling `arrive`/`start`/`complete` today. Auditing "Assignment as a database row" alone would have understated the real exposure — the actual mutable surface is Trip, reached exclusively through Assignment's own REST contract.

## 12. Frontend Caller Inventory

Every real caller of `arrive`/`start`/`complete`, found by a repository-wide search of `frontend/src` for `/v1/assignments`, read in full at each call site:

| File | Function | Endpoint(s) | Identity available | Auth mechanism currently used | Change needed after remediation |
|---|---|---|---|---|---|
| `frontend/src/pages/DriverHome/DriverHome.tsx` | `respondToAssignment(assignmentId, action)` | `POST /v1/assignments/{id}/arrive\|start\|complete` | **Yes** — `identity.token` (this driver's own already-held session token) is already in scope in this exact component, already used elsewhere in the same file (e.g. `loadProposals`, and, after Task 21, `respondToProposal`) | **None** — no `Authorization` header is sent on this call today | **Yes, minimal** — add `Authorization: Bearer ${identity.token}` to this one `request(...)` call, mirroring exactly what Task 21 already did to this same file's own `respondToProposal`, moments away in the same source file |
| `frontend/src/pages/RideRequest/RideRequest.tsx` | (inline, inside the status-polling effect) | `GET /v1/assignments?orderId=` | N/A — read stays unauthenticated (Section 6) | None, deliberately | No |
| `frontend/src/pages/OwnerControlCenter/todayData.ts` | `fetchAssignmentsForOrder` (or equivalent) | `GET /v1/assignments?orderId=` | N/A | None, deliberately | No |

`POST /v1/assignments` (`assignOrder`) has no frontend caller at all (Section 9) — no file, no change to consider.

## 13. Existing Test Coverage

Checked directly: `AssignmentControllerTest.kt`, `AssignmentRepositoryLifecycleTest.kt`, `PostgreSQLAssignmentLifecycleTest.kt` — a search for `Authorization`, `SessionTokenVerifier`, `OwnerCredentialGate`, `401`, `403`, `UNAUTHORIZED`, `FORBIDDEN` across all three returns **zero matches**. None of the six scenarios this task asked about are currently proven:

| Scenario | Currently proven? |
|---|---|
| Anonymous request rejected | **No** |
| Correct driver accepted | **No** (no test asserts any driver identity at all) |
| Wrong driver rejected | **No** |
| Unknown assignment rejected | **Yes** — `AssignmentControllerTest` already has 404 tests for `arrive`/`start`/`complete` on a never-created id (unrelated to identity, but confirms the existence check itself is solid) |
| Trip transition remains functional | Not applicable to this audit (nothing here would change Trip's own transition logic) |
| Internal application-service calls remain functional | Not applicable — no internal bypass exists to protect (Section 10) |

`AssignmentController`'s constructor, as built by every existing test (`AssignmentController(service, repository, tripRepository)`), has no fourth/fifth parameter for identity — meaning any future test proving the first three rows would first need the same constructor-signature change Task 21 made to `ProposalController`.

## 14. Comparison With ProposalController Remediation

| Aspect | Proposal (before Task 21) | Assignment (today) |
|---|---|---|
| `SessionTokenVerifier`/`OwnerCredentialGate` present in the controller at all | Yes, injected, but unused on 4 methods | **No — absent entirely** |
| Ownership field on the aggregate | `Proposal.driver: DriverReference` | `Assignment.driver: DriverReference` — same shape |
| Internal/bypass caller for the vulnerable methods | Yes (First Refusal → `ProposalApplicationService.handle`, in-process) | **No** (Section 10) — simpler to remediate, no risk of breaking an internal path |
| Endpoint with no real caller at all | None (every endpoint had at least one real caller) | `assignOrder` (Section 9) |
| Frontend already holds the needed token | Yes | Yes (Section 12) |

## 15. Reuse Analysis — `SessionTokenVerifier`

**Sufficient, directly, with no modification.** `VerifiedToken(sub, drv)` already carries exactly what `arrive`/`start`/`complete` need: `drv`, compared against `assignment.driver.driverId` — the identical shape and identical field name (`driverId`) `ProposalController`'s own `acceptProposal`/`declineProposal` already compare against `proposal.driver.driverId`. No new claim, no new token format, no change to `identity`'s own issuance (ADR-055) is implied.

## 16. Reuse Analysis — `OwnerCredentialGate`

**Required for exactly one endpoint: `assignOrder`.** Since it has no real caller today (Section 9) and, conceptually, its own request body (`orderId`, `driverId`, chosen by whoever calls it) matches Coordinator's own manual-assignment shape exactly (the same shape `ProposalController.createProposal`'s owner-credential branch already exists for, since Task 21), `OwnerCredentialGate` is the correct, already-precedented mechanism if this endpoint is required to accept any caller at all going forward. Not required for `arrive`/`start`/`complete` (driver-only, Section 9) or `listAssignments` (deliberately open, Section 6).

## 17. Whether Assignment Contains Sufficient Ownership Data

**Yes, fully — more directly than Proposal's own residual gap (Task 20/21).** `Assignment.driver: DriverReference` already exists, is already populated at creation, and is already exposed (`AssignmentResponse.driverId`, `AssignmentController`'s own `toResponse`). Unlike Proposal's still-open `create`-ownership question (no `passengerReference` field exists on `Proposal`), Assignment's own `arrive`/`start`/`complete` need only compare against a field that already exists — no schema change, no new column, no migration.

## 18. Whether A New ADR Is Required

**No.** This is the same conclusion Task 20 reached for Proposal, for the same reasons: `ADR-055` (session tokens) and `ADR-044` (owner credential) already ratify both mechanisms this fix would use; applying an already-Accepted pattern to a second controller is not a new architectural decision. No STOP condition in Section 22 concerns an ADR, because none applies.

## 19. Whether Frontend Changes Are Required

**Yes, exactly one, minimal, additive.** `DriverHome.tsx`'s `respondToAssignment` needs one header added to its existing `request(...)` call (Section 12) — the same shape of change Task 21 already made, in the same file, to the neighboring `respondToProposal` function. No wire contract, response shape, route, or component changes. `RideRequest.tsx` and `todayData.ts` need no change (both stay read-only, deliberately unauthenticated).

## 20. Exact Minimal Remediation Proposal (not implemented in this task)

1. Add `sessionTokenVerifier: SessionTokenVerifier` and `ownerCredentialGate: OwnerCredentialGate` to `AssignmentController`'s own constructor (both classes already exist, unmodified, in the same package — no new file).
2. `arrive`, `start`, `complete`: add an `Authorization` header parameter; before delegating to the existing service call, read the Assignment (`assignmentRepository.findById(id)` — already available, already used by this controller's own response-building code), verify the token, require `verified.drv == assignment.driver.driverId` — 401/404/403 in that order, mirroring `ProposalController.acceptProposal`'s own already-implemented sequence exactly (Task 21).
3. `assignOrder`: add the same header parameter; require `ownerCredentialGate.verify(authorization)` — 401 otherwise. (Whether to *also* accept a driver or passenger token here, the way Proposal's `create` does, is a narrow, low-stakes product question this audit flags but does not resolve, since the endpoint has no real caller either way — Section 21.)
4. `listAssignments`: **no change** — leave exactly as documented (Section 6).
5. Frontend: one header addition to `DriverHome.tsx`'s `respondToAssignment` (Section 19).

This mirrors Task 20/21's own shape closely enough that a Task 23 implementing it should be able to reuse most of the same test patterns (`driverToken`, `passengerToken`, `ownerAuth` helpers) `ProposalControllerTest.kt` already established, rather than inventing new ones.

## 21. Risks / Residual Risks

- **`assignOrder`'s own correct caller model is genuinely under-specified** (Section 16's parenthetical) — since it has zero real callers today, a Task 23 implementer has latitude to choose owner-only (simplest, matches its coordinator/manual-assignment origin) without urgency, but should not invent a broader policy than that without checking whether some future, not-yet-built caller was intended to use it differently.
- **`acceptAssignment` remains unreachable dead code** (Section 2) — not a security risk (nothing can reach it over HTTP), but worth a future cleanup task noting it, separate from this remediation.
- **This audit did not re-examine `AssignmentRepositoryLifecycleTest.kt`/`PostgreSQLAssignmentLifecycleTest.kt` line-by-line beyond confirming the absence of auth-related assertions** — a Task 23 implementer should still read them before modifying, per this project's own "verify fresh" discipline, not rely solely on this report.
- **Deploying this fix has the same prerequisite Task 19's own Deployment Gate already established for the whole Tasks 11–17 chain**: none of this code is live in production today — re-verified directly in this session, not assumed, via the same non-invasive, local-file-only method Task 18/19 used (no production connection): `backend/dispatch/build/libs/dispatch-0.1.0-SNAPSHOT.jar` is still last-built `2026-09-02 19:14:31`, unchanged and still older than `V12__trips.sql` (`2026-09-03 03:25:55`) and everything after it. This remediation, once implemented, should travel with that same, already-planned deployment.

## 22. STOP-Condition Evaluation

All eight conditions checked explicitly; none fired.

1. **Ratified product decision would need to change?** No — this closes an access-control gap; it does not touch who may request a ride, how First Refusal works, or any other product-level rule.
2. **Assignment/Trip architecture would need to change?** No — `driver: DriverReference` already exists on both aggregates; the fix reads a field that is already there.
3. **No existing authentication primitive can express the required authorization?** False — `SessionTokenVerifier`/`OwnerCredentialGate` express it directly (Sections 15–16), already proven for the structurally identical Proposal case.
4. **Legitimate caller model cannot be determined from source?** False — determined precisely for every endpoint (Section 9), including the two cases (`listAssignments`, `assignOrder`) that are *not* simply "driver-only."
5. **Authorization semantics genuinely ambiguous between driver/passenger/coordinator/owner?** No — `arrive`/`start`/`complete` are unambiguously driver-only (confirmed: the only real caller of any kind is a driver-facing screen); `listAssignments` is deliberately multi-actor and already correctly open; `assignOrder`'s own ambiguity is narrow and low-stakes (Section 21), not blocking.
6. **Remediation would require changing a public API contract rather than enforcing identity on the existing one?** No — same URLs, same request/response shapes, only a header addition, exactly Task 21's own precedent.
7. **Assignment authorization is intentionally delegated to another trusted boundary?** No such delegation was found anywhere in source, KDoc, or ADR — the absence appears to be an oversight (the same class of oversight Task 13/20 already found and named for Proposal), not a deliberate design choice recorded anywhere.
8. **A production-only dependency exists that cannot be safely evaluated from source/test infrastructure?** No — every finding in this report was established from source code, the isolated `pios_dispatch_test` database (read-only, via the existing test suite only — no direct query was needed or made this session), and prior tasks' own already-disclosed jar-timestamp evidence; nothing required a production connection.

## 23. Recommended Task 23 Scope

A narrowly-scoped implementation task, directly mirroring Task 21's own shape:

**In scope:** the five changes in Section 20; new tests proving anonymous/wrong-driver/correct-driver for `arrive`/`start`/`complete`, anonymous/owner-credential for `assignOrder`; full regression (focused tests → full Dispatch suite → frontend suite, isolated test infrastructure only, same residual-test-data-cleanup discipline every prior task in this chain has already established); a report at `docs/PIOS_TAXI_TASK_23_ASSIGNMENT_SECURITY_REMEDIATION_REPORT.md` in the same structure as Task 21's own.

**Explicitly out of scope, per this audit's own findings:** `listAssignments` (leave exactly as is); `acceptAssignment` (dead code, not a security question); resolving `assignOrder`'s own broader caller-model question beyond "owner-only, since nothing else calls it" (Section 21); any Trip-specific authorization concept distinct from Assignment's own (Trip is reached exclusively through Assignment's own contract — securing Assignment's own endpoints is sufficient, per Section 11's own call-graph trace); anything touching Proposal, First Refusal, Order Management, Passenger Experience, or Network Management.

## 24. Production-Safety Statement

- **Files inspected:** `AssignmentController.kt`, `DispatchAssignmentApplicationService.kt`, `Assignment.kt`, `Trip.kt`, `AssignmentControllerTest.kt`, `AssignmentRepositoryLifecycleTest.kt` (existence/grep only), `PostgreSQLAssignmentLifecycleTest.kt` (existence/grep only), `ProposalAssignmentOrchestrationService.kt`, `ProposalController.kt` (re-checked for the Task 21 comparison), `SessionTokenVerifier.kt`/`OwnerCredentialGate.kt` (re-confirmed, already read in full during Task 20/21), `DriverHome.tsx`, `RideRequest.tsx`, `Coordinator.tsx` (re-checked), `OwnerControlCenter/todayData.ts`, plus repository-wide searches (`grep`) across `backend/dispatch/src/main/kotlin` and `frontend/src` for every method/endpoint name discussed above.
- **Files changed: none, except this report.** Confirmed by `git diff --stat backend/dispatch` immediately before writing this report, matching exactly Task 21's own already-committed-to-working-tree changes and nothing more.
- **Production DB touched: NO.**
- **RabbitMQ touched: NO.**
- **Services restarted: NO.**
- **Commits made: NO.**

## Conclusion

**FAIL — remediation required.**

The Assignment API has the same class of unauthenticated-write vulnerability Task 20/21 found and fixed on Proposal, is simpler to remediate (no internal bypass to protect), already carries the field the fix needs, and reuses infrastructure already proven correct on the exact same problem shape one controller away. No architectural or product decision blocks proceeding. Recommended next step: Task 23, scoped exactly as Section 23 describes, implementation only — not this task.

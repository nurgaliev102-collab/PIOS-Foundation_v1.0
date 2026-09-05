# PIOS Taxi — Task 21: Proposal API Security Remediation

Status: **Implemented.** Closes the public, unauthenticated write surface on Dispatch's Proposal API that Tasks 19–20 found and audited, using only the two already-existing, already-precedented authentication mechanisms (`SessionTokenVerifier`, `OwnerCredentialGate`) already constructor-injected into `ProposalController`. No new authentication architecture, no new ADR, no production or RabbitMQ connection, no production service started, stopped, or restarted.

## Pre-Implementation Verification

- `git status --short` before any change matched exactly what Tasks 18–20 already documented: the same pre-existing, unrelated Category B modifications (design-system/onboarding, `ai-advisor`, `.claude`/`graphify` tooling), plus Task 17's own three files (`RideRequest.tsx`, `RideRequest.test.tsx`, `OrderSubmittedFirstRefusalConsumerIntegrationTest.kt`) already layered on top of the same pre-existing files. Nothing unexpected was present.
- Task 17's own actual changes were re-confirmed directly from source (not assumed): `RideRequest.tsx`'s `handleSubmit` sends `explicitDriverIntent: true`, and `OrderSubmittedFirstRefusalConsumerIntegrationTest.kt` carries its own Test 3. Neither needed any change for this task, and neither was touched.
- Confirmed, before writing any code, that no unrelated frontend/design-system/`ai-advisor` file would be included: every edit in this task targets a specific function inside a specific, already-identified file (Section "Exact Files Changed" below) — nothing was touched incidentally.

## Endpoints That Now Require Authentication

| Endpoint | Requirement |
|---|---|
| `POST /v1/proposals` (create) | A validly-signed `Bearer` session token (any `sub`, `drv` irrelevant) **or** a valid owner `Basic` credential. Anonymous requests: 401. |
| `POST /v1/proposals/{id}/accept` | A validly-signed `Bearer` session token whose `drv` equals the proposal's own `driver.driverId`. No/invalid token: 401. Proposal not found: 404. Token valid but wrong driver (or a passenger-only token, `drv == null`): 403. |
| `POST /v1/proposals/{id}/decline` | Identical to `accept`. |
| `POST /v1/proposals/{id}/lapse` | A valid owner `Basic` credential only — no driver or passenger token is accepted, since none has a legitimate reason to call this endpoint directly (Section below on the scheduler). |
| `GET /v1/proposals?driverId=` | Unchanged — already fixed by ADR-060 Decision 4, not part of this task. |
| `GET /v1/proposals?orderId=` | Unchanged — deliberately still unauthenticated (ADR-060 Decision 4), not part of this task. |

## Exact Files Changed

**9 files.** No file outside this list was modified.

1. `backend/dispatch/src/main/kotlin/com/pios/dispatch/api/ProposalController.kt` — the only production/`main` source change: `createProposal`, `acceptProposal`, `declineProposal`, `lapseProposal` each gained an `Authorization` header parameter and the check described above. Converted from expression-bodied (`fun x(): T = try {...}`) to block-bodied (`fun x(): T { return try {...} }`) methods where an early `return` was needed — Kotlin does not permit `return` inside an expression body; this was caught by the compiler on the first attempt and fixed immediately, not discovered later.
2. `backend/dispatch/src/test/kotlin/com/pios/dispatch/api/ProposalControllerTest.kt` — every existing call to `createProposal`/`acceptProposal`/`declineProposal`/`lapseProposal` updated to supply the now-required authorization (via three new small helpers: `driverToken`, `passengerToken`, `ownerAuth`, all built on the file's own pre-existing `issueToken`/`bearer`/`basicHeader` helpers — no new mock, no new token format). 22 new tests added (Section "Tests" below). `GatedFixture`'s own `SessionTokenVerifier` secret, previously a plain string that was not valid base64 (and so could never have verified any token, latent and unexercised until this task), was corrected to a properly base64-encoded value so its own three tests could supply a real, verifiable token.
3. `backend/dispatch/src/test/kotlin/com/pios/dispatch/api/ProposalControllerPostgreSQLIntegrationTest.kt` — the same authorization update against real PostgreSQL, plus a configured owner credential (previously left unconfigured, since no test here had ever needed it) and two new IDOR-against-real-Postgres tests.
4. `frontend/src/pages/RideRequest/RideRequest.tsx` — `attemptProposal`'s own `POST /v1/proposals` call now sends `Authorization: Bearer ${identity.token}` (this passenger's own already-held session token).
5. `frontend/src/pages/RideRequest/RideRequest.test.tsx` — one new test asserting that header.
6. `frontend/src/pages/DriverHome/DriverHome.tsx` — `respondToProposal` (accept/decline) now sends `Authorization: Bearer ${identity.token}` (this driver's own already-held session token); gained a defensive `!identity` early-return alongside the pre-existing `submitting` guard.
7. `frontend/src/pages/DriverHome/DriverHome.test.tsx` — extended the existing accept test's own assertion to also check the new header, and added one new decline test.
8. `frontend/src/pages/Coordinator/Coordinator.tsx` — `handleAssign`'s own `POST /v1/proposals` call now sends `Authorization: toBasicAuthorizationHeader(credential)` (the owner credential this screen already holds and already gates itself behind); gained a defensive `!credential` early-return.
9. `frontend/src/pages/Coordinator/Coordinator.test.tsx` — one new test, the first in this file to exercise the manual-assignment flow at all, asserting the header.

**Not changed, anywhere:** `AssignmentController.kt` (its own identical sibling gap is explicitly out of this task's scope, per Task 20's own H section); `Proposal.kt`/any domain or application-service file beyond the controller's own delegation calls; `FirstRefusalApplicationService.kt`; `OrderSubmitted*`; `Trip*`; any Passenger Experience file; `frontend/src/pages/PassengerLanding/**`; any `.css` file; any `ai-advisor`/`.claude`/`graphify` file. `git diff --stat` after all changes confirms exactly the 9 files above carry new content from this task — `RideRequest.tsx`/`RideRequest.test.tsx`/`RideRequest.module.css` show additional, larger diffs in the repository's own working tree, but those are the same, already-disclosed, pre-existing Category B/Task 17 content this task's edits are layered on top of, not new changes this task made (verified by re-reading each diff hunk, not assumed).

## Tests

**Backend — `ProposalControllerTest.kt`:** 22 new tests, organized under a dedicated "Task 21" section, directly proving every scenario this task's own instructions named: anonymous create rejected; authenticated passenger create succeeds, naming an arbitrary `driverId` deliberately unrestricted; owner-credential create succeeds (the Coordinator path); a malformed `Authorization` scheme rejected identically to a missing one; driver accepts/declines their own proposal, succeeds; driver accepts/declines a different driver's proposal, rejected (403), proposal state unchanged; no-header accept/decline rejected before any state change; a passenger-only token (`drv == null`) rejected on accept; owner-credential lapse succeeds; no-header lapse rejected, proposal state unchanged; the named driver's own token is explicitly **not** sufficient for lapse (proving the owner-only restriction, not merely assuming it).

**Backend — `ProposalControllerPostgreSQLIntegrationTest.kt`:** every existing test updated to supply valid authorization (proving the whole real-PostgreSQL chain still works end to end); 4 new tests: anonymous create rejected, accept-as-different-driver rejected (real Postgres), decline-as-different-driver rejected (real Postgres), no-header lapse rejected (real Postgres).

**Frontend:** `RideRequest.test.tsx` (+1), `DriverHome.test.tsx` (+1, plus one existing test's assertion extended), `Coordinator.test.tsx` (+1, the first test in that file to exercise the assign flow at all).

**Proving `ProposalLapseScheduler` continues to work:** not a new test — the class itself was not touched, has never called this HTTP endpoint (it calls `ProposalLapseApplicationService.lapseProposal` directly, in-process, confirmed by reading its own source again this session), and its own existing test suite (`ProposalLapseSchedulerTest`, `ProposalLapseApplicationServiceTest`) was re-run unmodified and passed. This is the honest proof — a new test exercising code this task didn't touch, to prove a claim the architecture already makes structurally true, would have been padding, not evidence.

**Proving First Refusal's direct application-service call continues to work:** identical reasoning. `FirstRefusalApplicationService.attempt` has no `Authorization` parameter in its own signature and never touches `ProposalController`; it calls `ProposalApplicationService.handle(ProposeDriverCommand(...))` directly. `FirstRefusalApplicationServiceTest` and both `OrderSubmittedFirstRefusalConsumerIntegrationTest`/`ConcurrencyTest` (real RabbitMQ/PostgreSQL, unmodified since Task 17) were re-run and passed unmodified — proof, not assertion.

## Focused Test Results

| Test class | Result |
|---|---|
| `ProposalControllerTest` | **63/63 passed** (41 pre-existing, updated for the new auth requirement, + 22 new) |
| `ProposalControllerPostgreSQLIntegrationTest` | **15/15 passed** (11 pre-existing, updated, + 4 new) |
| `FirstRefusalApplicationServiceTest` | **10/10 passed**, unmodified |
| `OrderSubmittedFirstRefusalConsumerIntegrationTest` | **7/7 passed**, unmodified |
| `OrderSubmittedFirstRefusalConcurrencyTest` | **1/1 passed**, unmodified |
| `ProposalLapseApplicationServiceTest` | **6/6 passed**, unmodified |
| `ProposalLapseSchedulerTest` | **1/1 passed**, unmodified |
| Frontend, 3 focused files | **53/53 passed** |

## Full-Suite Results

| Suite | Result |
|---|---|
| Dispatch (full) | **435/435 passed**, 0 skipped, 0 failures, 0 errors |
| Frontend (full, `npx vitest run`) | **226/226 passed**, 24 files (was 223/223 before this task; +3 new) |
| Frontend typecheck (`npx tsc -b`) | **1 pre-existing, unrelated error only** (`aiProvider.test.ts`, the same `pilotAnalytics`/`ai-advisor` gap Task 17 already found and confirmed pre-existing) — **zero errors** in any file this task touched |

**One transient failure encountered and resolved, disclosed honestly, identical in kind to every prior task in this chain:** the first full Dispatch run hit the known, recurring residual-test-data collision — `ProposalControllerPostgreSQLIntegrationTest` (3 tests) and `PostgreSQLAssignmentLifecycleTest` (2 tests) use fixed, non-randomized literal identifiers that collide with rows left by prior runs (this task's own new `postgres-vertical-order-*-idor`/`*-anon` tests added a few more such rows on their own first, failed attempt). Verified via `psql` against the isolated `pios_dispatch_test` database only, confirmed the same known shape, cleaned via targeted `DELETE` (respecting FK order: `trips` → `assignments` → `proposals`), never touching production. Re-ran: clean, 435/435.

Order Management and Passenger Experience were not touched by this task and were not re-run in full — nothing in either module's own source changed.

## Production and RabbitMQ Safety

- **No production PostgreSQL connection was made.** Every `psql` command in this task explicitly targeted `pios_dispatch_test` (`-d pios_dispatch_test`); none targeted `pios_dispatch` or any other production database.
- **No RabbitMQ connection of any kind was made directly** — the two RabbitMQ-backed test classes re-run (`OrderSubmittedFirstRefusalConsumerIntegrationTest`, `OrderSubmittedFirstRefusalConcurrencyTest`) use the project's own already-established, isolated `pios-test` vhost test infrastructure, unmodified by this task.
- **No production service was started, stopped, or restarted.** No `sc start`/`sc stop`/`Restart-Service`, no `gradlew bootRun`, no direct `java -jar` invocation of any kind was run at any point in this task — only `gradlew compileKotlin`/`compileTestKotlin`/`test` (which build and run tests against isolated resources, not the running production JVMs) and `npx tsc`/`npx vitest`.
- No migration was created or run. No frontend production build was produced or deployed.

## What Was Deliberately Left Out Of Scope

- **`AssignmentController`'s identical sibling gap** (`arrive`/`start`/`complete`, no authorization at all) — confirmed still present, confirmed unchanged, explicitly named by this task's own instructions as a separate task, not touched.
- **Passenger ownership of `Proposal` on `create`** — an authenticated passenger may still name any `orderId`, including one they do not own; this is Task 20's own named, out-of-scope architectural gap (`Proposal` carries no `passengerReference`), explicitly excluded from this task's own instructions ("passenger ownership of Proposal — это отдельное архитектурное решение и не входит в Task 21"), and not touched.
- **Arbitrary `driverId` choice on `create`** — deliberately, explicitly *not* restricted, per this task's own instruction ("не запрещать выбор произвольного driverId, поскольку это является существующим продуктовым поведением"). Confirmed by a dedicated new test (`create -- any authenticated passenger token succeeds, naming any driverId, deliberately unrestricted`) that this remains true after this change.
- **Circle of Trust** — no file under Passenger Experience or its own Circle-of-Trust UI was touched; the only frontend touchpoint (`RideRequest.tsx`) already held the token this change now also sends to Dispatch, so no new state, request, or UI element was introduced there.
- **First Refusal architecture / `FirstRefusalApplicationService`** — not touched; proven unaffected, not merely left alone (Section "Tests" above).
- **`OrderSubmitted`, `Trip`, `Assignment`, Passenger Experience** — none touched, confirmed by `git diff --stat`.
- **No new ADR** — none of this task's changes required one (Section "STOP Conditions" below).

## New Architectural Questions

**None emerged beyond what Task 20 already named.** The residual `create`-ownership gap is exactly as Task 20 described it, not newly discovered or newly complicated by implementation; no other architectural question surfaced while writing or testing this change.

## STOP Conditions — Checked, None Fired

- **Existing auth mechanism insufficient?** No — `SessionTokenVerifier`/`OwnerCredentialGate` were sufficient for every check this task required, proven by 26 new passing tests exercising exactly those checks against the real mechanisms (no mock authentication was invented).
- **Change requires a new ADR?** No — this is a direct, same-shape replication of an already-Accepted pattern (ADR-055, ADR-044, ADR-060 Decision 4), applied to four more methods on a controller that already held both collaborators.
- **Coordinator/First Refusal impossible to preserve without an architecture change?** No — both were preserved with a one-line frontend addition (Coordinator) and zero change at all (First Refusal, by construction — it never touches HTTP).
- **Production access required?** No — every check and every fix was achievable, and was achieved, against isolated test infrastructure and source code alone.
- **Proposal domain model needed to change?** No — `Proposal.kt` was not touched; the fix lives entirely in the controller's own transport boundary, reading `proposal.driver.driverId` (a field that already existed) rather than adding one.
- **Passenger ownership verification turned out to be necessary?** No — it remains exactly the disclosed, out-of-scope residual gap Task 20 already named; nothing in implementing this task's own scope required resolving it.

## Final Conclusion

The Proposal API's public, unauthenticated write surface — the single item Task 19's own Security Gate flagged as already-live, publicly-exploitable exposure independent of any deployment decision — is closed in source, proven by 26 new tests plus the full existing suite (435/435 Dispatch, 226/226 frontend) passing unmodified. The fix used nothing this codebase did not already have. This work has not been deployed — per Task 19's own Deployment Gate (Part 3), it should travel with Dispatch's own already-planned deployment, not trigger a separate one, and per this task's own instruction, no commit was made here either — it is left for review.

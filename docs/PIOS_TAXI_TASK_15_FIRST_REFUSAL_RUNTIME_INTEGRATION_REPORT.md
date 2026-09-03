# Task 15 — First Refusal Runtime Integration: STOP Report

**Date:** 2026-09-03. **Outcome: STOP triggered during Phase 0/1 (pre-implementation verification and insertion-point design). No implementation code was written.** This report documents the evidence and the decision required before Task 15 can proceed.

---

## 1. Executive Summary

Task 15 asks for the ratified First Refusal behavior — an order is submitted, and *if* the passenger has an eligible primary driver, the system automatically offers them the order first, before falling back to normal matching — to be connected to the real runtime. Phase 0 verification (re-tracing the actual call graph, not trusting Task 13/14's own prior reports) confirms that **every real Proposal-creation call site in this codebase already requires the caller to have explicitly chosen a specific driver before `POST /v1/proposals` is ever called.** There is no live call site representing "an order was just submitted; nobody has chosen a driver yet; decide automatically." Task 14's own `FirstRefusalApplicationService` is fully built, fully tested, and — confirmed fresh — still called from nowhere in production.

Connecting the two therefore cannot be done as a narrow, value-neutral backend change. It requires resolving a genuine product/architecture question that ADR-062 itself explicitly left open (its own "Explicitly not decided" list: *"The exact command/endpoint shape callers use to invoke 'propose, honoring First Refusal if applicable'"*) and that this task's own text does not resolve either. Three real candidate resolutions exist (Section 6); each crosses a boundary this task's own instructions treat as requiring authorization rather than improvisation. Per Task 15's own STOP-condition protocol: no further implementation changes were made; this report documents the exact evidence and the decision required.

## 2. Exact Pre-Implementation Call Graph

**Path 1 — Passenger direct-propose / Circle-of-Trust click (`frontend/src/pages/RideRequest/RideRequest.tsx`):**
1. Passenger submits the ride form → `POST /v1/orders` (Order Management), body includes `passengerReference` (`RideRequest.tsx:627`).
2. On success, `attemptProposal(orderId)` fires immediately (`RideRequest.tsx:638`) → `POST /v1/proposals` (Dispatch), body `{orderId, driverId: driverCode ?? ''}` (`RideRequest.tsx:653-661`) — **`driverCode` is fixed before this call**, either the invitation link's own driver (route param) or whichever circle member the passenger explicitly clicked via `handleChooseCircleMember` (`RideRequest.tsx:749-755`), which — for a member other than the current page's own driver — **navigates to that member's own `/i/:driverCode/request` page** (a fresh page load, `driverCode` now fixed to the chosen member, including the primary driver if that is who was clicked) rather than changing anything about this single-driver POST shape.
3. Controller: `ProposalController.createProposal` (`backend/dispatch/.../api/ProposalController.kt:93`), no auth check (confirmed, Task 13).
4. Application service: `ProposalApplicationService.handle(ProposeDriverCommand)` (`.../application/ProposalApplicationService.kt:125`) — confirmed unchanged since Task 14: `ProposeDriverCommand` carries exactly `{order: OrderReference, driver: DriverReference, isTest: Boolean}` — **no passenger identity field exists.**
5. Domain: `Proposal.propose(order, driver, existingProposals, isTest)` (`.../domain/Proposal.kt:230`).
6. `handle`'s own pre-existing gate: `driverAvailabilityRepository.findByDriverReference(command.driver)` must show `available == true`, else `IllegalStateException` (409) — unchanged, confirmed.

**Path 2 — Coordinator manual assignment (`frontend/src/pages/Coordinator/Coordinator.tsx`):**
1. Coordinator selects an order and a driver from two independently-loaded lists (`GET /v1/orders`, `GET /v1/drivers`) → `selectOrder`/`selectDriver` (`Coordinator.tsx:220-230`).
2. `handleAssign()` → `POST /v1/proposals`, body `{orderId: selectedOrderId, driverId: selectedDriverId}` (`Coordinator.tsx:246-249`) — **both already explicitly chosen by a human before this call.**
3. Same controller/application-service/domain path as Path 1, from step 3 onward.

**No other real path exists.** `OrderSubmissionCoordinator`/`PassengerOrderSubmissionApplicationService` (`backend/passenger-experience/.../application/`) — the code that would represent "order submitted, no driver chosen yet" from Passenger Experience's own side — remain fully built and tested (`PassengerOrderSubmissionEndToEndTest`, `OrderSubmissionContractVerificationTest`) but **confirmed, fresh, unreachable**: `backend/passenger-experience/src/main/kotlin/com/pios/passengerexperience/api/` contains only `ConnectionController`, `HealthController`, and their supporting response/request types — no order-submission controller of any kind. `FirstRefusalApplicationService` itself: confirmed, fresh (`grep -rn "FirstRefusalApplicationService" backend/ --include=*.kt`, excluding its own test files), is referenced only in its own file and `PrimaryDriverRepository`'s own KDoc — **called from no production code anywhere.**

**Where driver availability is checked:** `ProposalApplicationService.handle`'s own existing gate (Section above, step 6) — unchanged, and this is exactly the gate `FirstRefusalApplicationService.attempt` (Task 14) already reuses rather than duplicating.

**Where `Proposal.propose()`/`Proposal.create()` is invoked:** exactly one place, `ProposalApplicationService.handle` — confirmed, both real paths converge there. Task 13's own identification of this as the canonical insertion point is **re-confirmed, unchanged.**

**The actual gap, precisely stated:** it is not that the two paths fail to converge (they do, exactly as Task 13 found) — it is that **both already carry a fully-formed, human-or-link-chosen `driverId` by the time they reach that convergence point**, with no passenger identity attached and no "undecided" case in the command's own shape. `FirstRefusalApplicationService.attempt(order, passengerReference, isTest)` needs a `passengerReference` neither real caller supplies, and its entire purpose — choosing *which* driver to propose to — has nothing left to decide once a caller has already named one.

## 3. Files Inspected

`ProposeDriverCommand.kt`, `ProposeDriverRequest.kt`, `ProposalApplicationService.kt`, `ProposalController.kt`, `FirstRefusalApplicationService.kt`, `PrimaryDriverRecord.kt`, `PrimaryDriverRepository.kt`, `PrimaryConnectionEventListener.kt`, `ProposalLapseScheduler.kt`, `ProposalLapseApplicationService.kt`, `ProposalRepository.kt`, `DriverAvailabilityRecord.kt`/`DriverAvailabilityRepository.kt`, `Proposal.kt`, `frontend/src/pages/RideRequest/RideRequest.tsx` (full order-submission/proposal/circle-of-trust sections), `frontend/src/pages/Coordinator/Coordinator.tsx` (full assign section), `backend/passenger-experience/src/main/kotlin/com/pios/passengerexperience/application/OrderSubmissionCoordinator.kt`/`PassengerOrderSubmissionApplicationService.kt` (existence and wiring status), `backend/passenger-experience/src/main/kotlin/com/pios/passengerexperience/api/` (full directory listing, to confirm no order-submission controller exists), `docs/ADR/ADR-062-...md` (re-read in full for its own "Explicitly not decided" list).

## 4. Files Changed

None.

## 5. Files Created

Only this report: `docs/PIOS_TAXI_TASK_15_FIRST_REFUSAL_RUNTIME_INTEGRATION_REPORT.md`.

## 6. Exact Runtime Integration Point

**Confirmed, per Phase 1's own instruction to verify this again: `ProposalApplicationService.handle` remains the point both real Proposal-creation paths converge on.** This is not in dispute. What is in dispute is *what a caller must supply* for a decision to be made there at all, and *what happens to a caller's own already-explicit driver choice* — neither of which Task 13, Task 14, ADR-062, or Task 15's own text resolves. Three real candidates were identified; none was implemented:

**Option A — Silent override.** Add an optional `passengerReference` to `ProposeDriverRequest`/`ProposeDriverCommand`; when present and the passenger has an eligible primary driver *different* from the caller's own `driverId`, substitute the primary driver silently. **Rejected as unauthorized to implement unilaterally**: this is a new exclusivity/override business rule — precisely what Task 15's own IMPORTANT PRODUCT SEMANTICS section forbids ("the passenger retains participant choice") and what `CLAUDE.md` forbids inventing without ratification. It would also mean a passenger who deliberately clicks a *different*, non-primary circle member (a choice the UI already explicitly offers) gets silently redirected to someone else.

**Option B — New automatic trigger via a new consumed event.** Dispatch begins consuming Order Management's `OrderSubmitted` (currently consumed by nothing, per Task 13's own confirmed finding, re-checked here) and, on receipt, calls `FirstRefusalApplicationService.attempt` independently of whatever the frontend does next. **Rejected as unauthorized to implement unilaterally**: this is a new cross-module contract (Order Management → Dispatch) that neither ADR-062 nor ADR-063 authorizes — ADR-062's own contract is Passenger Experience → Dispatch only. It also creates an unresolved race: the frontend's own existing flow (Path 1) would still fire its own `POST /v1/proposals` moments later for the same order, and the Proposal Root Invariant (at most one `OPEN` proposal per order) means one of the two silently loses via a 409 with no defined, ratified rule for which one should — itself a new product decision.

**Option C — Additive frontend contract change.** Add an optional `passengerReference` to the existing `POST /v1/proposals` request body (no endpoint rename, no new endpoint), populated by `RideRequest.tsx`'s own existing `identity.identityId` in its existing `attemptProposal` call; `ProposalApplicationService.handle` (or a thin wrapper in front of it) uses it only to *offer* the primary driver — the actual UX for whether/how a passenger is shown "your primary driver gets first refusal" versus their current explicit-click flow is a **separate, unresolved UX question** this option does not itself answer. **Not implemented**: this is a frontend contract change, which Task 15 permits only when "the existing runtime absolutely cannot express the approved behavior without" one — a judgment call this report surfaces rather than makes, since Task 15's own Phase 3 diagram and "PRIORITY STAGE, not a replacement" framing suggest the intended UX is closer to automatic (Option B's shape) than to reusing today's already-existing manual "click your primary driver's card" affordance (which, notably, **already exists** in `RideRequest.tsx` today — Section 7 — meaning the passenger-choice half of the desired behavior is arguably already live, and the only genuinely missing piece may be narrower than a full automatic-trigger redesign).

## 7. Existing Passenger-Choice UX, Discovered During Verification

Not previously highlighted this precisely in Task 13's audit: `RideRequest.tsx` **already** presents the passenger's primary driver prominently with its own call-to-action (`RideRequest.tsx:865-870`, a "Вызвать" button on the primary driver's own card, disabled when Driver Management reports them unavailable) alongside the rest of their circle. Clicking it either proceeds in place (if already on that driver's own page) or navigates to that driver's `/i/:driverCode/request` page, which then creates an ordinary `POST /v1/proposals` for exactly that driver. **This is passenger-initiated, not system-automatic** — it satisfies "the passenger retains participant choice" but not the product decision's own stated trigger ("passenger creates an order → primary driver receives first opportunity," stated unconditionally, not "if the passenger clicks their primary driver's card"). Whether this existing UX already constitutes an acceptable implementation of "primary driver first" for Taxi V1, or whether a genuinely automatic system-decided trigger is still required, is itself part of the decision this report asks for (Section 6, Option C's own note).

## 8–14. First Refusal Decision Flow / Fallback Behavior / Idempotency / Race Safety / Database / RabbitMQ / API Changes

Not reached — no implementation was performed past Phase 0/1. `FirstRefusalApplicationService`'s own already-built decision flow, fallback semantics, and idempotency guarantees (Task 14) remain exactly as that task left them: fully implemented, fully tested against real PostgreSQL and RabbitMQ, and callable — but not called by any production path.

## 15. Frontend Changes

None made. Section 6/7 above identify where a frontend change *might* eventually be required, contingent on the Architect/Product Owner's own resolution of the insertion-point question — not performed here, per this task's own instruction not to redesign the frontend and to treat any frontend change as requiring explicit justification, which this report supplies as evidence rather than as authorization to proceed.

## 16. Security Boundary

**Inherited, unchanged, pre-existing** (Task 13's own finding, re-confirmed by direct reading of `ProposalController.kt` this task): `acceptProposal`/`declineProposal`/`lapseProposal` still have no caller-identity check of any kind. This task's own Phase 5 asked whether the new runtime would create a *new* exposure or only inherit the existing one — since no runtime wiring was implemented, **no new exposure exists; the pre-existing one is completely unaffected, in either direction.** Left unchanged, as instructed.

## 17. Tests Executed

None — no production code was modified, so no new or existing test run was required to validate a change. `docs/PIOS_TAXI_TASK_14_FIRST_REFUSAL_FOUNDATION_REPORT.md`'s own already-recorded results (401/401 Dispatch, 75/75 Passenger Experience, as of Task 14) remain the last verified state; this task did not re-run them, since nothing in the codebase changed since that verification.

## 18. Production Safety Verification

Trivially satisfied: no command in this task touched any database, any RabbitMQ vhost, or any running service — verification and reading only, consistent with Phase 0's own "do not modify anything during Phase 0" instruction, which this task never moved past.

## 19. STOP Conditions Checked

All 13 checked explicitly against the evidence gathered:

1. **TRIGGERED.** The actual Proposal-creation call graph (Section 2) does not match Task 15's own assumed flow (an order-submission-time automatic trigger) in a way that requires architectural redesign — see Section 6.
2. **TRIGGERED.** Implementing First Refusal at the real runtime requires resolving a decision ADR-062 itself explicitly left open (its own "Explicitly not decided" list) — see Section 6.
3. Not triggered — the existing Proposal invariant itself supports First Refusal fine (Task 14 already proved this); the problem is upstream of the invariant, in what reaches it.
4. Not reached — no fallback implementation was attempted.
5. Not applicable — no migration was attempted.
6. Not applicable — no test was run.
7. Not triggered — `PrimaryDriverRecord`'s own consistency (Task 14) is not in question; nothing here challenges it.
8. **Partially relevant**, folded into item 1/2 above — Option C (Section 6) would require a frontend change, but this alone was not treated as independently triggering, since Task 15's own text frames a frontend change as conditionally permissible, not as an automatic contradiction; the STOP is about *which* of Options A/B/C to authorize, not about C being forbidden outright.
9. Not triggered — nothing discovered suggests exclusivity is actually required; if anything, Option A (the only path that would *look* like exclusivity) is exactly what this report recommends against implementing unilaterally.
10–12. Not applicable — no command touched infrastructure.
13. Not triggered — no new authentication/authorization architecture is implicated by any of Options A/B/C; Section 16's own pre-existing gap is unaffected either way.

## 20. Known Remaining Risks

- **`FirstRefusalApplicationService` remains unreachable capability** until Section 6 is resolved — a real, if intentional, gap: Task 14's own investment has no runtime effect yet.
- **The pre-existing accept/decline/lapse authorization gap** (Section 16) becomes more consequential the moment any of Options A/B/C is implemented, since a First-Refusal Proposal will be an ordinary, equally-unprotected `Proposal` row — unchanged risk, but worth resolving before or alongside whichever option is chosen (Task 14's own report already recommended this).
- **Option B's own race** (Section 6) is a genuine, not-yet-designed-around correctness risk if that path is ever chosen without first deciding which of the two competing proposal-creation attempts should win.

## 21. Deviations from Architecture

None — no code was written, so no deviation is possible. The report itself does not propose a deviation; it surfaces an undecided point.

## 22. Final Implementation Status

**STOPPED at Phase 0/1, per Task 15's own explicit protocol.** No implementation, no test, no migration, no frontend change was made. Decision required from the Product Owner/Architect: which of Options A (rejected as unauthorized), B (rejected as unauthorized), or C (frontend-contract-change, not yet authorized) — or a fourth option not identified here — should govern how a real order actually reaches `FirstRefusalApplicationService.attempt`, and specifically whether "primary driver first" is meant to be system-automatic (requiring B or C) or is already adequately served by the passenger-initiated UX already live today (Section 7).

---

**Per this task's own STOP protocol: no further implementation changes were made.** Waiting for the Product Owner/Architect's decision before any further work on Task 15.

# Task 16 — First Refusal Runtime Integration: Implementation Report

**Date:** 2026-09-03. **Scope:** wire the already-built First Refusal foundation (Tasks 14/15C) into the real runtime — a Dispatch-side `OrderSubmitted` consumer invoking `FirstRefusalApplicationService`.

**Status: COMPLETE.** No STOP condition was triggered.

---

## 1. Objective

Make Dispatch automatically offer a passenger's primary driver first refusal on real order submission, when no explicit driver intent was declared — falling back to the existing, untouched matching/Proposal flow otherwise — without reinterpreting ADR-062/063 or any ratified product decision.

## 2. Pre-Implementation Call-Graph Verification

Re-verified fresh, per this task's own Phase 0 instruction, not trusted blindly from prior reports:

- **`OrderCancelledListener`/`OrderCancelledApplicationService`/`OrderCancelledRepository`** (the closest existing precedent — Order Management → Dispatch, a triggered action rather than a projection) were read in full and used as the direct structural template.
- **`RabbitMQOrderManagementTopologyConfiguration`** re-read fresh: confirmed it declares exactly one queue (`OrderCancelled`'s own) before this task; confirmed ADR-053 Part 4's own "dedicated queue per event type" rule, restated in that class's own KDoc.
- **`FirstRefusalApplicationService.attempt`'s actual current signature** re-confirmed by reading the file directly (post-Task-15C): `attempt(order: OrderReference, passengerReference: PassengerReference, isTest: Boolean = false, explicitDriverIntentDeclared: Boolean = false): FirstRefusalOutcome` — unchanged by this task.
- **`OrderSubmitted`'s actual current payload** re-confirmed by reading `OrderLifecycleApplicationService.outboxRecordFor(OrderSubmitted)` directly: `{orderId, passengerReference, explicitDriverIntent}` — **missing `isTest`**, a real gap this task's own Phase 0 found and closed (Section 4).
- **`OrderSubmissionCoordinator`/`PassengerOrderSubmissionApplicationService`**: confirmed, again, by listing `backend/passenger-experience/src/main/kotlin/com/pios/passengerexperience/api/` directly, that no controller wires either to any HTTP endpoint — this task does not depend on them and does not wire them.
- **`proposals_one_open_per_order`** (Task 15C's own migration) re-confirmed present and is the mechanism this task's own consumer relies on for its idempotency guarantee (Section 10).

No discrepancy was found between the actual runtime and the architecture this task assumes — no STOP condition fired at Phase 0.

## 3. Actual Order Submission Path

Unchanged, re-confirmed: `POST /v1/orders` → `OrderSubmissionController.submitOrder` → `OrderSubmissionRequestHandler.handle` → `OrderLifecycleApplicationService.submitOrder` → `Order.submit(...)`, persisting `Order` and its `OrderSubmitted` outbox record in one transaction. This task did not touch this call path's own structure — only the payload the last step publishes (Section 4).

## 4. Changes Made

**Order Management** (producer-side, closing a gap this task's own Phase 0 found in Task 15C's own prior work): `OrderSubmitted` now also carries `isTest` (`Order.isTest`'s own value) — without it, an automatically-created First-Refusal Proposal for a *test* order would have been indistinguishable from real production activity in Owner Control Center's own test/production separation, since every other Dispatch-side aggregate an order eventually produces (`Proposal.isTest`, `Assignment.isTest`) already carries this flag. Additive, default `false`, matching `Order.isTest`'s own existing default.

**Dispatch**: a new `OrderSubmittedFirstRefusalListener` consumes `OrderSubmitted` from a new, dedicated queue and calls `FirstRefusalApplicationService.attempt` directly — no intermediate application service, no new idempotency ledger (Section 10), no change to `FirstRefusalApplicationService` itself, `ProposeDriverCommand`, `ProposalApplicationService`, `Proposal.propose`, or any existing Proposal REST endpoint.

## 5. OrderSubmitted Consumer Design

`OrderSubmittedFirstRefusalListener` (`backend/dispatch/.../persistence/`) mirrors `OrderCancelledListener`'s own exact discipline: validates `eventType == "OrderSubmitted"` and `eventVersion == 1` before extracting anything (malformed/unexpected messages throw, routing to the dead-letter queue via the existing bounded-retry policy, never silently misinterpreted); extracts `payload.orderId`, `payload.passengerReference`, `payload.explicitDriverIntent`, `payload.isTest`; calls `firstRefusalApplicationService.attempt(...)` directly (justified in the listener's own KDoc: `attempt` is already the correct application-layer entry point, and introducing a wrapping service would only indirect through it for no reason — the same pattern `ProposalController` itself already uses at its own transport boundary). Never imports any Order Management type.

## 6. PrimaryDriverRecord Resolution

Unchanged from Task 14 — `FirstRefusalApplicationService.attempt` calls `primaryDriverRepository.findByPassenger(passengerReference)` internally; this task's own listener never touches `PrimaryDriverRepository` directly.

## 7. FirstRefusalApplicationService Integration

The listener is the **first and only production caller** of `FirstRefusalApplicationService.attempt` — confirmed by `grep` before this task (Section 2) that nothing called it in production; now exactly one call site does. `FirstRefusalApplicationService.kt` itself was not modified.

## 8. explicitDriverIntent Handling

The listener reads `payload.explicitDriverIntent` and passes it straight through as `attempt`'s own `explicitDriverIntentDeclared` parameter — no reinterpretation. When `true`, `attempt`'s own already-proven (Task 15C) short-circuit fires: no read of `PrimaryDriverRepository`, no call to `ProposalApplicationService` at all. This task's own integration test (`Test C`) proves the *observable* consequence (no proposal created); the *mechanism* (no collaborator touched at all) was already proven at the unit level in Task 15C and is not re-implemented or weakened here.

**Whether any real order-submission path currently sets this flag to `true`**: no — re-confirmed, unchanged since Task 15C. `RideRequest.tsx`'s existing "call your primary driver" / Circle-of-Trust card and `Coordinator.tsx`'s manual assignment both remain the passenger's/coordinator's own explicit act, routed through the pre-existing `POST /v1/proposals`, entirely unaffected by this task. This task does not silently reinterpret that existing UX as anything other than what it already is (Phase 5's own instruction) — it remains exactly "participant choice," not touched, not converted into a different product rule.

## 9. Fallback Behavior

Unchanged, verified by `Test B`: no `PrimaryDriverRecord` → `attempt` returns `NoPrimaryDriver`, no Proposal is created, the message is acknowledged normally, and the order remains exactly as available to the existing Coordinator/matching flow as before this task existed. Nothing in this task creates a fallback Proposal automatically — that responsibility remains exactly where Tasks 13/14 already found it: a human, via the unmodified Coordinator.

## 10. Idempotency

**Deliberately no new idempotency ledger** — a considered decision, not an oversight, documented in full in the listener's own KDoc and restated here: `DriverAvailabilityChangedListener`'s and `OrderCancelledListener`'s own ledgers exist because their effects (re-toggling availability; withdrawing a Proposal that may have since moved to a different state) would be *wrong* to reapply blindly. `FirstRefusalApplicationService.attempt`'s own effect — creating a Proposal — has no such hazard: it is already idempotent by construction (`Proposal.propose`'s own root invariant, backed at the database level by `proposals_one_open_per_order`, Task 15C). Adding a ledger here would be exactly the "ad-hoc deduplication mechanism" this task's own Phase 3 instructs against building unless required — and Test D proves it is not required for the realistic case (redelivery while the created Proposal is still `OPEN`).

**Disclosed, narrow, accepted residual gap**: a redelivery arriving only *after* the first-created Proposal has already resolved (accepted/declined/lapsed) — a window far longer than the existing bounded retry policy (3 attempts, backoff capped at 2000ms) or any real driver's own response time would realistically produce — would create a *second* proposal for the same primary driver, since the Root Invariant no longer blocks it. This is the same class of at-least-once-delivery tolerance ADR-031 already accepts platform-wide, not a new risk this task introduces, and not treated as blocking (Section 23).

## 11. Concurrency Protection

Proven with real concurrent threads (not RabbitMQ's own delivery timing) calling `OrderSubmittedFirstRefusalListener.onMessage` directly, twice, for the identical message (`OrderSubmittedFirstRefusalConcurrencyTest`, `Test E`): exactly one `OPEN` proposal results, for the primary driver, regardless of which invocation's own database transaction wins. This exercises the listener's own parsing/dispatch under concurrency, not only the already-proven service beneath it (Task 15C's own `ProposalConcurrencyTest`).

## 12. RabbitMQ Topology

`RabbitMQOrderManagementTopologyConfiguration` gained a second, dedicated queue/DLQ/binding pair for `OrderSubmitted` (`dispatch.from-order-management.order-submitted`[`.dlq`]) — not a second binding on the existing `OrderCancelled` queue, per ADR-053 Part 4's own explicit rule (a listener hard-requiring one `eventType` on a shared queue would dead-letter the other kind). The producer exchange (`order-management.events`) and the `OrderCancelled` queue/binding themselves are completely unchanged. Routing key: `order.submitted`, unchanged from Task 15C, unchanged from Order Management's own actual publisher.

## 13. PostgreSQL Test Isolation

`pios_dispatch_test`/`pios_order_management_test`/`pios_passenger_experience_test` — confirmed distinct from their production counterparts (`psql -h 127.0.0.1 -U postgres -lqt`) before any test ran. No test or command in this task connected to `pios_dispatch`, `pios_order_management`, or `pios_passenger_experience`.

## 14. Tests Added

10 new Dispatch tests: `OrderSubmittedFirstRefusalConsumerIntegrationTest.kt` (7: Test A ×2, Test B, Test C, Test D, Test F), `OrderSubmittedFirstRefusalConcurrencyTest.kt` (1: Test E) — real RabbitMQ (`pios-test` vhost) + real PostgreSQL throughout, no mocks. Plus new test infrastructure: `OrderSubmittedMessagePublisher.kt`, `OrderSubmittedFirstRefusalTestListenerHarness.kt` (test-only, mirror established precedent).

2 new Order Management tests (`OrderTest.kt`, `OrderSubmittedEnvelopeTest.kt`) proving `isTest` propagation (Section 4).

## 15. Test Commands

```
./gradlew.bat :dispatch:test --tests "com.pios.dispatch.persistence.OrderSubmittedFirstRefusalConsumerIntegrationTest" --tests "com.pios.dispatch.persistence.OrderSubmittedFirstRefusalConcurrencyTest"
./gradlew.bat :dispatch:test
./gradlew.bat :order-management:test
./gradlew.bat :passenger-experience:test
```

## 16. Test Results

Focused run: all green on first attempt. **Complete Dispatch suite**: `BUILD SUCCESSFUL`, `tests=416 skipped=0 failures=0 errors=0` (409 from Task 15C's own baseline + 7 new). **Complete Order Management suite**: `BUILD SUCCESSFUL`, `tests=167 skipped=0 failures=0 errors=0` (165 + 2 new). **Complete Passenger Experience suite**: `BUILD SUCCESSFUL`, `tests=75 skipped=0 failures=0 errors=0` (unchanged — confirms zero regression from this task's own Order Management changes).

**Pre-existing residual-data failures encountered and resolved, re-confirmed unrelated to this task**: the first full-Dispatch-suite run failed the same 5 pre-existing tests (`ProposalControllerPostgreSQLIntegrationTest` ×3, `PostgreSQLAssignmentLifecycleTest` ×2) already diagnosed in Tasks 11/12/14/15C — the identical non-randomized-literal-identifier pattern in those two files, unmodified by this task. Cleaned via `DELETE` against `pios_dispatch_test` only, then reran clean. No test or code file was modified to achieve this.

## 17. Existing Regression Tests

Full Dispatch (416/416), Order Management (167/167), and Passenger Experience (75/75) suites all re-run to completion and passing — Sections 16, above.

## 18. Production Safety Verification

`pios_dispatch_test`/`pios_order_management_test`/`pios_passenger_experience_test` confirmed distinct from production by direct `psql -lqt` listing before any test ran. Every residual-data `DELETE` targeted `pios_dispatch_test` explicitly by name. Every RabbitMQ-using test in this task uses `RabbitMQTestConnection` (the isolated `pios-test` vhost, `pios_test` credential — a credential the broker itself refuses to authenticate against any other vhost). No production PostgreSQL was written to. No production RabbitMQ vhost (`/`) was connected to. No production service was restarted. No repo-wide `./gradlew build` was run.

## 19. Files Changed

**Order Management**: `domain/Order.kt`, `domain/OrderSubmitted.kt`, `application/OrderLifecycleApplicationService.kt`; tests: `domain/OrderTest.kt`, `persistence/OrderSubmittedEnvelopeTest.kt`.

**Dispatch**: `persistence/RabbitMQOrderManagementTopologyConfiguration.kt`.

## 20. Files Created

**Dispatch**: `persistence/OrderSubmittedFirstRefusalListener.kt`; tests: `persistence/OrderSubmittedMessagePublisher.kt`, `persistence/OrderSubmittedFirstRefusalTestListenerHarness.kt`, `persistence/OrderSubmittedFirstRefusalConsumerIntegrationTest.kt`, `persistence/OrderSubmittedFirstRefusalConcurrencyTest.kt`.

This report: `docs/PIOS_TAXI_TASK_16_FIRST_REFUSAL_RUNTIME_INTEGRATION_REPORT.md`.

## 21. Files Intentionally Not Changed

Any frontend file — Phase 0 found no technical requirement forcing one (Section 8). `FirstRefusalApplicationService.kt`, `PrimaryDriverRepository.kt`, `ProposalApplicationService.kt`, `ProposalController.kt`, `Proposal.kt`, `ProposalStatus.kt` — unchanged; this task is additive wiring around already-built, already-tested logic. `Assignment.kt`, `Trip.kt`. `Coordinator.tsx`, `RideRequest.tsx` — the existing explicit-selection flows remain completely untouched, still funneling through the unmodified `POST /v1/proposals`. Network Management, Identity, Driver Management. `OrderCancelledListener`/`DriverAvailabilityChangedListener`/`PrimaryConnectionEventListener` and their own queues — unmodified; the new `OrderSubmitted` queue is additive, alongside them, not a replacement.

## 22. STOP Conditions Encountered/Not Encountered

None of the 14 listed conditions fired. Explicitly checked: (1) the call graph matched the assumed architecture, confirmed fresh; (2) `OrderSubmitted` reliably carries `passengerReference` (Task 15C, re-verified) — the one gap found (`isTest`, Section 4) was additive and closed within this task's own scope, not a contradiction requiring a stop; (3) `explicitDriverIntent` is guaranteed atomic at submission (Task 15C's own mechanism, unchanged); (4) `attempt` was invoked exactly as built, no invariant bypassed; (5) `PrimaryDriverRecord` resolution is unchanged and already proven reliable; (6)/(9)/(12) fallback is unchanged, no exclusivity was introduced, nothing protected/legacy was removed; (7) no new ADR was required — this task builds infrastructure ADR-062 already authorizes, using the exact pattern ADR-053 already established; (8)/(11) no production database/vhost was reached, and none of this task's own frontend-adjacent findings required an actual frontend change; (13) duplicate delivery was proven not to produce a second `OPEN` proposal (Test D) with all existing concurrency protections in place; (14) nothing protected or legacy was removed.

## 23. Known Limitations

- **The narrow "resolved-then-redelivered-much-later" idempotency gap** (Section 10) — disclosed, accepted, consistent with this codebase's own platform-wide at-least-once philosophy (ADR-031), not closed by a new ledger in this task.
- **The pre-existing accept/decline/lapse authorization gap** (Task 13) is unaffected in either direction by this task — a First-Refusal-created Proposal is an ordinary Proposal, exactly as exposed (or not) to that gap as any other. Not touched, per this task's own Phase 7.
- **No real order today sets `explicitDriverIntent: true`** — the mechanism is live and correctly wired, but every current real order submission still defaults to `false`, meaning First Refusal, now genuinely active for the first time, will attempt for *every* order with an eligible primary driver, including ones where the passenger is about to (or already has) explicitly chosen a different driver via the existing direct-propose flow. This is the exact race Task 15C's own Section 8 already disclosed honestly (non-deterministic which side wins, but never both, never corruption) — now live in production for the first time, not newly introduced by this task, but newly *consequential* since this task is what actually starts the automatic side firing for real.
- **RabbitMQ production credentials/topology sharing** (the pre-existing, disclosed condition — shared `guest`/`guest` on the default vhost) is unaffected; this task's own new exchange/queue additions are Dispatch-owned and follow the identical pattern every existing queue already uses.

## 24. Recommended Next Task

Decide — a genuine product/UX decision, not a technical one — whether and how the passenger-facing direct-propose flow (`RideRequest.tsx`) should set `explicitDriverIntent: true` when the passenger has already made an explicit choice (clicking a specific circle member, or arriving via a specific invitation link), closing the residual race named in Section 23 for the common case. This is a frontend change, explicitly out of this task's own scope, and was not made speculatively here per this task's own Phase 10 instruction.

---

## Final Verification

- `OrderSubmitted` contains `orderId` + `passengerReference`: **yes**, unchanged from Task 15C, re-verified.
- `Order.explicitDriverIntent` correctly set: **yes**, unchanged from Task 15C.
- Dispatch consumes `OrderSubmitted` from isolated RabbitMQ: **yes**, proven by `Test A`/`Test F` against the real `pios-test` vhost.
- `PrimaryDriverRecord` resolved correctly: **yes**, proven by `Test A`.
- `FirstRefusalApplicationService` invoked only when appropriate: **yes** — `Test C` proves no automatic proposal when explicit intent is declared.
- Explicit driver intent always wins (for the flag-declared case): **yes**, mechanism unchanged from Task 15C, exercised end-to-end by `Test C`.
- No primary driver produces no First Refusal proposal: **yes**, `Test B`.
- Duplicate events do not create duplicate `OPEN` proposals: **yes**, `Test D`.
- Concurrent processing produces exactly one `OPEN` proposal: **yes**, `Test E`, real threads, real PostgreSQL.
- Existing Proposal lifecycle intact: **yes**, 416/416 Dispatch tests, including every pre-existing Proposal test, unmodified and green.
- Existing five-minute lapse mechanism intact: **yes**, `ProposalLapseScheduler`/`ProposalLapseApplicationService` untouched.
- Existing explicit driver-selection flows intact: **yes**, `ProposalController`, `Coordinator.tsx`, `RideRequest.tsx` untouched.
- No frontend changes made: **confirmed true** — `git status` shows zero frontend files touched by this task.
- No production PostgreSQL touched: **confirmed true**.
- No production RabbitMQ vhost touched: **confirmed true**.
- No production service restarted: **confirmed true**.

**Task 16 is COMPLETE.** Exact test results: Dispatch 416/416, Order Management 167/167, Passenger Experience 75/75 — zero failures across all three. Files modified: 5 (2 Dispatch, 3 Order Management main + 2 Order Management test = listed in full, Section 19). Files created: 6 (1 Dispatch main, 4 Dispatch test, this report). Frontend changed: **no**. Production PostgreSQL touched: **no**. Production RabbitMQ touched: **no**. Production services restarted: **no**. No commit was created. Not proceeding to any further task automatically.

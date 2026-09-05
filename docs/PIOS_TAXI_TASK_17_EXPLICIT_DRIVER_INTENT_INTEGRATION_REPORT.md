# PIOS Taxi — Task 17: First Refusal Explicit Driver Intent Integration

Status: **Implementation complete.** Task 16 identified `explicitDriverIntent` as the remaining runtime gap. This task was not authorized to redesign First Refusal. Its sole purpose was to connect the already-existing `explicitDriverIntent` mechanism to the real explicit-driver order flow and prove that explicit passenger choice takes precedence over automatic First Refusal. No commit was made — the checkpoint at `b701c6c` stands unchanged; this work is left staged for review, per this task's own explicit instruction.

## 1. Objective

Close the residual runtime gap Task 16's own report disclosed: `FirstRefusalApplicationService` already runs automatically on every `OrderSubmitted`, but no real caller ever set `explicitDriverIntent: true`, so an order created through the one real explicit-driver flow this product has could, in principle, race an automatic First-Refusal proposal for the passenger's Primary Driver. This task wires the already-existing mechanism (Task 15C: `Order.explicitDriverIntent`; Task 16: threaded through `OrderSubmitted` and `FirstRefusalApplicationService.attempt`) into that real flow — nothing more.

## 2. Phase 0 — Fresh Inspection Findings

Re-read from source, not from prior reports. The central finding: **the entire backend chain was already fully wired, end to end, before this task started.** Nothing needed to change in any backend module.

Traced directly:

- **`frontend/src/pages/RideRequest/RideRequest.tsx`** — the only frontend code path that calls `POST /v1/orders` (confirmed by a repo-wide search for `/v1/orders`/`/v1/proposals`: the only other callers are `DriverHome.tsx`, `Coordinator.tsx`, and `OwnerControlCenter/todayData.ts`, and every one of them only ever *reads* orders (`GET`) or manually proposes an *already-existing* order to a dispatcher-chosen driver — none of them creates one). `RideRequest.tsx` is reachable only through a specific driver's own personal invitation link (`/i/:driverCode`, `/i/:driverCode/request`) — `driverCode` is a required URL parameter, not a passenger choice made on this screen. Its own `handleChooseCircleMember` (Circle of Trust step) confirms this structurally: choosing any driver in the circle other than the current one *navigates to that driver's own route* rather than letting this component hold an ambiguous, driver-less state.
- **`handleSubmit`** called `POST /v1/orders` with `{passengerReference, pickupAddress, destination, passengerName, requestedPickupAt?}` — no `explicitDriverIntent`, before this task. Immediately after, `void attemptProposal(response.orderId)` calls `POST /v1/proposals` (Dispatch) with `{orderId, driverId: driverCode}` directly — the real explicit-driver proposal, entirely independent of Order Management and of First Refusal.
- **`backend/order-management/.../api/SubmitOrderRequest.kt`** — already carries `explicitDriverIntent: Boolean = false` as a public, documented, optional field (added by Task 15C), sent by no real caller until this task.
- **`OrderSubmissionController.submitOrder`** already passes `request.explicitDriverIntent` straight through to `OrderSubmissionRequestHandler.handle(...)`.
- **`OrderSubmissionRequestHandler.handle`** already builds `SubmitOrderCommand(..., explicitDriverIntent = explicitDriverIntent)`.
- **`OrderLifecycleApplicationService.submitOrder`** already calls `Order.submit(..., command.explicitDriverIntent)` and, in the *same* `transactionRunner.run { }` block, saves both the `Order` row and the `OrderSubmitted` outbox record — the transactional-outbox pattern (ADR-029/031/032) already in place for every other event this service publishes.
- **`Order.submit`** already sets `explicitDriverIntent` on the aggregate and builds the `OrderSubmitted` event with the identical value, atomically, at the one point in the whole system that is causally guaranteed to precede anything `OrderSubmitted` itself can ever trigger (Task 15C's own reasoning, restated verbatim in `Order.kt`'s own KDoc — re-verified, not re-derived, here).
- **`OrderSubmitted.kt`** already carries `explicitDriverIntent` (Task 15C) and `isTest` (Task 16); the outbox envelope (`OrderLifecycleApplicationService.outboxRecordFor(event: OrderSubmitted)`) already serializes both into `payload`.
- **`OrderSubmittedFirstRefusalListener.onMessage`** (Task 16) already reads `payload.explicitDriverIntent` and passes it as `explicitDriverIntentDeclared` into `FirstRefusalApplicationService.attempt`.
- **`FirstRefusalApplicationService.attempt`** (Task 15C) already short-circuits to `FirstRefusalOutcome.ExplicitDriverIntentDeclared` the moment `explicitDriverIntentDeclared` is `true`, making zero calls to `PrimaryDriverRepository` or `ProposalApplicationService` — proven in Task 15C's own tests via a repository double that throws if ever invoked.
- **Existing Circle-of-Trust / direct-propose flow** (`attemptProposal`, `POST /v1/proposals` → `ProposalApplicationService.handle(ProposeDriverCommand(...))`) — untouched by this task, in code and in behavior; verified by full regression (Section 12).
- **An ordinary order with no explicit driver** — no such caller currently exists in the real frontend (see Section 5's honest disclosure). The mechanism itself is exercised by the existing Task 16 test suite, which synthesizes this case directly against the real listener/service, and remains valid and necessary for whatever future caller needs it.
- **`OrderSubmissionCoordinator`** (`backend/passenger-experience/.../application/OrderSubmissionCoordinator.kt`) — re-checked per this task's own explicit warning not to assume a `Coordinator`-named class is wired into production. Confirmed, by a repository-wide search, that no `@RestController` or any other class references it — it has no live HTTP entry point. It is not part of the real order-creation call graph and was not touched.

## 3. Actual Call Graph (as verified, Phase 0)

```
Passenger opens /i/{driverCode}                       (PassengerLanding.tsx)
        |
        v
"Заказать поездку" -> /i/{driverCode}/request          (RideRequest.tsx)
        |
        v
(optional) Circle of Trust step -- choosing a different
driver navigates to THAT driver's own /i/{otherCode}/request;
choosing the current one proceeds in place. Either way, by
the time the form renders, exactly one specific driverCode
is fixed for this submission.
        |
        v
handleSubmit()
   -> POST /v1/orders  { passengerReference, pickupAddress,
                          destination, passengerName,
                          requestedPickupAt?,
                          explicitDriverIntent: true }      <-- Task 17's only change
        |
        v
OrderSubmissionController.submitOrder
        |
        v
OrderSubmissionRequestHandler.handle(..., explicitDriverIntent)
        |
        v
OrderLifecycleApplicationService.submitOrder   (one transaction)
   Order.submit(..., explicitDriverIntent=true)
        |                                    \
        v                                     v
   orderRepository.save(order)      outboxRepository.save(OrderSubmitted{explicitDriverIntent:true})
        |
        v  (async, outbox relay, existing ADR-029/031/032 mechanism -- unchanged)
   RabbitMQ  order-management.events / order.submitted
        |
        v
OrderSubmittedFirstRefusalListener.onMessage           (Dispatch, Task 16)
        |
        v
FirstRefusalApplicationService.attempt(explicitDriverIntentDeclared=true)
        |
        v
   ExplicitDriverIntentDeclared  -- NO PrimaryDriverRepository read,
                                    NO ProposalApplicationService call

Meanwhile, back in RideRequest.tsx (independent, unchanged path):
   void attemptProposal(orderId)
        |
        v
   POST /v1/proposals  { orderId, driverId: driverCode }   (unchanged, Sprint 7B)
        |
        v
   ProposalApplicationService.handle(ProposeDriverCommand)
        |
        v
   Proposal (OPEN) for driverCode -- the passenger's own explicit choice
```

## 4. Existing Architecture Reused (nothing new built)

- `Order.explicitDriverIntent` (Task 15C) — reused as-is; not touched.
- `SubmitOrderRequest.explicitDriverIntent` (Task 15C) — reused as-is; already public, already wired to `Order.submit`; not touched.
- `OrderSubmitted.explicitDriverIntent` (Task 15C) — reused as-is; not touched.
- `FirstRefusalApplicationService.attempt(..., explicitDriverIntentDeclared)` (Task 15C) — reused as-is; not touched.
- `OrderSubmittedFirstRefusalListener` (Task 16) — reused as-is; not touched.
- The transactional outbox pattern (ADR-029/031/032) — reused as-is for the atomicity guarantee; not touched.
- `ProposalApplicationService.handle(ProposeDriverCommand)` / `POST /v1/proposals` (Sprint 7B / Sprint IMPLEMENTATION-005) — reused as-is for the explicit proposal itself; not touched.

No new boolean, aggregate, event, or endpoint was created, per this task's own Requirement №1.

## 5. Exact Files Changed

1. **`frontend/src/pages/RideRequest/RideRequest.tsx`** — two edits:
   - `handleSubmit`'s `POST /v1/orders` request body now includes `explicitDriverIntent: true`, with an inline comment explaining why this is unconditionally correct for every order this screen creates.
   - The component's own top-level KDoc gained one paragraph recording this change (matching this file's own established documentation convention).
2. **`frontend/src/pages/RideRequest/RideRequest.test.tsx`** — one new test added (`sends explicitDriverIntent: true on POST /v1/orders, since this screen always already knows the driver`), following the exact existing convention the file's own `requestedPickupAt` test already uses for asserting the submitted request body.
3. **`backend/dispatch/src/test/kotlin/com/pios/dispatch/persistence/OrderSubmittedFirstRefusalConsumerIntegrationTest.kt`** — one new import (`ProposeDriverCommand`) and one new test (`Task 17 Test 3`, Section 10 below) proving the full real scenario: a Primary Driver exists, a different driver is explicitly chosen, no First-Refusal proposal is created for the primary driver, and the explicit proposal itself still succeeds.
4. **`docs/PIOS_TAXI_TASK_17_EXPLICIT_DRIVER_INTENT_INTEGRATION_REPORT.md`** — this report (new).

**No backend production (`main`) source file was changed.** The entire backend mechanism already existed, fully wired, before this task began (Section 2).

## 6. Exact Files Not Changed

Everything else in the working tree is untouched by this task, specifically including (per Phase 6's own scope list): `Trip` (all of Tasks 11–12's files), `Assignment`, `Coordinator.tsx`/`Coordinator.test.tsx`, Network Management, Identity, every `RabbitMQ*TopologyConfiguration.kt`, every migration file, every production `application.yml`/WinSW config, any credential file, `ai-advisor/**`, `.claude/**`, `graphify-out/**`, and the separate, still-uncommitted frontend design-system/onboarding work (`frontend/src/components/**`, `frontend/src/styles/**`, `PassengerLanding.*`, `DriverHome.tsx`, `pilotAnalytics.*`, `BackendIdentityProvider.*`, `frontend/src/index.css`) — confirmed by `git status --short` before and after this task's own work (Section 13).

**Important disclosure about file state:** `RideRequest.tsx`, `RideRequest.test.tsx`, and `RideRequest.module.css` already carried unrelated, uncommitted modifications from a separate, earlier work stream (frontend design-system/onboarding — documented and deliberately excluded from the `b701c6c` checkpoint) *before* this task began. This task's own two edits to `RideRequest.tsx` and one edit to `RideRequest.test.tsx` are layered on top of that pre-existing state — additive, self-contained hunks (Section 5) — not a rewrite of it. `RideRequest.module.css` was not touched by this task at all. When these files are eventually committed, the reviewer should be aware the diff against `b701c6c` will show both work streams together in the same file; Section 5 above identifies exactly which parts are this task's own.

## 7. Explicit-Driver Flow — Before Task 17

```
RideRequest.tsx handleSubmit()
   -> POST /v1/orders { passengerReference, pickupAddress, destination,
                         passengerName, requestedPickupAt? }
      (explicitDriverIntent defaults to false -- not sent)
   -> attemptProposal(orderId) -> POST /v1/proposals { orderId, driverId }

Meanwhile, if this passenger also has a PrimaryDriverRecord (regardless of
whether it names the same driver or a different one), Dispatch's
OrderSubmittedFirstRefusalListener processes the same order's OrderSubmitted
with explicitDriverIntentDeclared=false and WILL attempt an automatic
First-Refusal proposal for the primary driver -- a genuine, undisclosed-
until-Task-16 race against attemptProposal's own explicit call, resolved
only by Proposal's "one OPEN per order" root invariant (Task 15C): whichever
of the two calls reaches the database first wins; the other receives
AlreadyAttempted / HTTP 409, with no data corruption, but also no
guarantee the passenger's own explicit choice is the one that survives.
```

## 8. Explicit-Driver Flow — After Task 17

```
RideRequest.tsx handleSubmit()
   -> POST /v1/orders { passengerReference, pickupAddress, destination,
                         passengerName, requestedPickupAt?,
                         explicitDriverIntent: true }
   -> attemptProposal(orderId) -> POST /v1/proposals { orderId, driverId }

Dispatch's OrderSubmittedFirstRefusalListener processes the same order's
OrderSubmitted with explicitDriverIntentDeclared=true and returns
ExplicitDriverIntentDeclared immediately -- no PrimaryDriverRepository
read, no attempt to create a competing Proposal, regardless of whether a
PrimaryDriverRecord exists for this passenger or which driver it names.
attemptProposal's own explicit proposal is now the only proposal-creation
attempt for this order from Dispatch's side of the race. (Proposal's "one
OPEN per order" invariant remains in force as the backstop it always was
-- Task 17 does not weaken it, only removes the one caller that used to
compete against the explicit choice.)
```

## 9. `explicitDriverIntent` Lifecycle (end to end, after Task 17)

1. Set by the passenger's own action: submitting the order form on a screen reachable only through one specific driver's own invitation link.
2. Recorded on `Order` at the single, causally-atomic point of submission (`Order.submit`), in the same transaction as the `OrderSubmitted` outbox record.
3. Carried, unchanged, on `OrderSubmitted.explicitDriverIntent` and its outbox JSON payload.
4. Relayed, unchanged, to RabbitMQ by the existing outbox relay (no code in this path was touched).
5. Read by `OrderSubmittedFirstRefusalListener` and passed as `explicitDriverIntentDeclared` into `FirstRefusalApplicationService.attempt`.
6. Consumed exactly once, to decide (never to record) — no new persistent state is created from this flag anywhere in Dispatch; the decision it drives (skip vs. attempt) is the entire effect.

## 10. `OrderSubmitted` Lifecycle

Unchanged by this task. The event's own shape (`orderId`, `origin`, `explicitDriverIntent`, `isTest`, `occurredAt`) is exactly what Task 15C/16 left it — Task 17 supplies a real, non-default value for a field that already existed on the wire; it does not add, remove, or rename anything on the contract. Per this task's own Hard Rule ("не изменяй OrderSubmitted payload повторно без STOP"), no such change was made or needed.

## 11. First Refusal Behavior

Unchanged in mechanism. `FirstRefusalApplicationService.attempt`'s own decision table (Task 15C) is exactly as before:

| `explicitDriverIntentDeclared` | Primary Driver exists & eligible | Outcome |
|---|---|---|
| `true` | (not checked) | `ExplicitDriverIntentDeclared` — no proposal attempt |
| `false` | yes | `Proposed` — automatic First-Refusal proposal created |
| `false` | no / ineligible | `NoPrimaryDriver` / `PrimaryDriverIneligible` — no proposal attempt |

What changed is only which real-world orders now reach this table with `true` versus the previous, always-`false` state.

## 12. Race Analysis

Two independent claims, both re-verified for this task, not assumed:

**Atomicity of the flag itself (unchanged from Task 15C, re-confirmed by source reading, Section 2):** `explicitDriverIntent` is set on `Order` and folded into `OrderSubmitted` inside the same `Order.submit()` call, and both the `Order` row and the outbox record are written inside the same database transaction (`OrderLifecycleApplicationService.submitOrder`'s `transactionRunner.run { }` block). No consumer of `OrderSubmitted` can ever observe an order for which this decision has not already been made — there is no window in which the flag could be read as `false` because it "hasn't been set yet."

**The race Task 17 actually closes (new, proven by Task 17 Test 3, Section 13):** before this task, `attemptProposal`'s own `POST /v1/proposals` (fired immediately after order creation, from the frontend) and Dispatch's asynchronous, RabbitMQ-driven `OrderSubmittedFirstRefusalListener` were two independent processes racing to create the *first* Proposal for the same order whenever a `PrimaryDriverRecord` also existed for that passenger. After this task, the listener no longer enters that race at all when `explicitDriverIntent` is `true` (which, for every order `RideRequest.tsx` creates, it now always is) — it returns `ExplicitDriverIntentDeclared` deterministically, before ever reading `PrimaryDriverRepository`, so there is no longer a race to analyze *for this flow*: one of the two former competitors has been removed, not merely made to lose more reliably. `Proposal`'s "one OPEN per order" database constraint (Task 15C) remains as the general-purpose backstop for every other, unrelated concurrent-Proposal scenario — untouched.

## 13. Tests Added

### Frontend — `RideRequest.test.tsx`
**`sends explicitDriverIntent: true on POST /v1/orders, since this screen always already knows the driver`** — submits the ordinary order form and asserts, by parsing the actual `fetch` body Object the component sent, that `body.explicitDriverIntent === true`. Mirrors the file's own existing `requestedPickupAt` assertion pattern exactly.

### Backend — `OrderSubmittedFirstRefusalConsumerIntegrationTest.kt`
**`Task 17 Test 3 -- explicit choice of a different driver suppresses First Refusal for the primary driver, and the explicit proposal itself still succeeds`** — against the real, isolated `pios_dispatch_test` PostgreSQL database and the real, isolated `pios-test` RabbitMQ vhost (not mocks): a `PrimaryDriverRecord` names Driver A; a real `OrderSubmitted` message (`explicitDriverIntent: true`) is published for the order, exactly as `OrderLifecycleApplicationService`'s own outbox envelope would produce it; the real explicit proposal for Driver B is created directly through `ProposalApplicationService.handle(ProposeDriverCommand(...))`, mirroring `RideRequest.tsx`'s own `attemptProposal`. Asserts exactly one Proposal exists for the order, that it is the explicit one, for Driver B, `OPEN` — never a competing proposal for Driver A.

This single test satisfies this task's own Phase 4 Test 1 (explicit driver → `explicitDriverIntent` true, carried through `OrderSubmitted` — proven by the publisher building the exact envelope shape the real outbox would), Test 3 (explicit choice suppresses First Refusal for a *different* Primary Driver, while the explicit proposal itself still succeeds), and Test 4 (the real event path: publish → RabbitMQ → listener → `FirstRefusalApplicationService`) in one real-infrastructure scenario. Task 16's own pre-existing `Test C` (`explicit driver intent declared creates no automatic proposal even with an eligible primary driver`) already independently covers the same suppression claim in isolation (no competing explicit proposal in that test) and was re-run, unmodified, as part of Section 14 below — together the two tests prove both halves: the listener alone suppresses correctly (Test C), and the full real two-path race resolves correctly in favor of the explicit choice (Task 17 Test 3). Test 2 (automatic First Refusal for an ordinary order) and Test 5 (duplicate delivery) are already covered, unmodified, by Task 16's own pre-existing Test A and Test D — re-run in Section 14, not duplicated here, since nothing about their own scenario changed. Test 6 (causal ordering of the flag *before* `OrderSubmitted` publication) is what Section 12's atomicity argument proves structurally (the same transaction/same call writes both) — re-verified by source reading in this task, not re-proven by a new timing-based test, since Task 15C's own `ProposalConcurrencyTest` already demonstrated this exact atomicity guarantee under real concurrent threads and nothing in Task 17 changes that code path.

## 14. Focused Test Results

| Test class | Result |
|---|---|
| `RideRequest.test.tsx` (frontend, focused file) | **33/33 passed** (32 pre-existing + 1 new) |
| `OrderSubmittedFirstRefusalConsumerIntegrationTest` (dispatch) | **7/7 passed** (6 pre-existing + 1 new, Task 17 Test 3) |
| `OrderSubmittedFirstRefusalConcurrencyTest` (dispatch) | **1/1 passed**, unmodified |
| `FirstRefusalApplicationServiceTest` (dispatch) | **10/10 passed**, unmodified |

Commands run: `npx vitest run src/pages/RideRequest/RideRequest.test.tsx`; `./gradlew.bat :dispatch:test --tests "...OrderSubmittedFirstRefusalConsumerIntegrationTest" --tests "...OrderSubmittedFirstRefusalConcurrencyTest" --tests "...FirstRefusalApplicationServiceTest"`.

## 15. Full-Suite Results

| Suite | Result | Command |
|---|---|---|
| Dispatch (full) | **417/417 passed**, 0 skipped, 0 failures, 0 errors | `./gradlew.bat :dispatch:test` |
| Order Management (full) | **167/167 passed**, 0 skipped, 0 failures, 0 errors | `./gradlew.bat :order-management:test --rerun` |
| Passenger Experience (full) | **75/75 passed**, 0 skipped, 0 failures, 0 errors | `./gradlew.bat :passenger-experience:test --rerun` |
| Frontend (full) | **223/223 passed** (24 test files) | `npx vitest run` |

**One transient failure encountered and resolved, disclosed honestly:** the first full Dispatch run hit the same known, pre-existing residual-test-data collision documented identically in Tasks 11, 12, 14, 15C, and 16 — `ProposalControllerPostgreSQLIntegrationTest` (3 tests) and `PostgreSQLAssignmentLifecycleTest` (2 tests) use fixed, non-randomized literal identifiers (`postgres-vertical-*`, `postgres-lifecycle-*`) that collide with residual rows left by prior runs. Verified via direct `psql` query against `pios_dispatch_test` that the residual rows were the same known shape as every prior occurrence, confirmed unrelated to Task 17's own changes (no Task 17 file touches Assignment, Trip, or the vertical-slice Proposal test), and cleaned via targeted `DELETE` statements against `pios_dispatch_test` only (never production, never the test files themselves), respecting FK order (`trips` → `assignments` → `proposals`). Re-ran afterward: 417/417, clean. This is the same disclosed, pre-existing limitation every prior task in this chain has recorded — not introduced or newly discovered by Task 17.

**Frontend typecheck (`npx tsc -b`):** **one pre-existing error**, entirely unrelated to Task 17 — `src/pages/OwnerControlCenter/aiProvider.test.ts(19,7): error TS2739`, caused by the separate, already-uncommitted `pilotAnalytics.ts`/`ai-advisor` work stream (Section 6) requiring `currentDay`/`history` fields a test fixture in that unrelated file does not supply. Confirmed pre-existing (identical error present with Task 17's own changes stashed out, via `git stash`); not caused, not fixed, by this task — fixing it would mean editing `pilotAnalytics.ts`/`aiProvider.test.ts`, both explicitly out of this task's scope (Phase 6: "unrelated ai-advisor").

**Frontend lint (`npx oxlint`):** exit code 1, **warnings only, zero errors**, both before and after this task's own changes (confirmed via `git stash`): 9 pre-existing `no-unsafe-optional-chaining`/`exhaustive-deps` warnings across `DriverHome.test.tsx`, `todayData.test.ts`, `Coordinator.test.tsx`, `PassengerLanding.tsx`, and three pre-existing instances already inside `RideRequest.test.tsx` itself, before Task 17 touched anything. Task 17's own new test reproduces the same, already-established `(x?.[1] as T).body as string` pattern already used three times in that same file (not a new style), adding one more instance of the same pre-existing warning category (10 total, up from 9) — a deliberate choice to match existing test convention exactly rather than invent a new one, consistent with "don't redesign," not a regression in kind.

## 16. Frontend Verification

- `npx vitest run` (full suite): 223/223 passed, 24 files — see Section 15.
- `npx tsc -b` (typecheck): one pre-existing, unrelated error (Section 15) — zero errors attributable to `RideRequest.tsx`/`RideRequest.test.tsx`.
- `npx oxlint` (lint): zero errors; warnings only, pre-existing in kind and count (+1 instance of an already-existing pattern) — see Section 15.
- No `npm run build` (`vite build`) was run to completion (it depends on the same `tsc -b` step, which already fails for the unrelated reason above) — a production frontend build was correctly out of scope for this task regardless (Production safety, Section 17).

## 17. PostgreSQL Isolation Verification

Every command in this task ran against `pios_dispatch_test` and `pios_order_management_test` exclusively — confirmed present and distinct from `pios_dispatch`/`pios_order_management` via `psql -h 127.0.0.1 -U postgres -lqt` before testing began. The one direct `psql` write performed (Section 15's cleanup) targeted `pios_dispatch_test` by explicit `-d pios_dispatch_test` connection, and only deleted rows matching the known, pre-existing residual-data literal-identifier pattern already documented in every prior task's own report.

## 18. RabbitMQ Isolation Verification

Task 17's own new test (`Task 17 Test 3`) uses the same `RabbitMQTestConnection` (isolated `pios-test` vhost, `pios_test` user) every other Dispatch integration test in this suite already uses — no new connection configuration was introduced. No production RabbitMQ vhost, exchange, or queue was read from or written to.

## 19. Production Safety

- **NO** production PostgreSQL database was connected to or modified (only `*_test` databases, Section 17).
- **NO** production RabbitMQ vhost was connected to or modified (only `pios-test`, Section 18).
- **NO** production service (`PIOS-Dispatch`, `PIOS-OrderManagement`, `PIOS-PassengerExperience`, or any other) was started, stopped, or restarted. `sc query PIOS-Dispatch` was run once, read-only, to confirm the production service's own running state was undisturbed by this task's work against the isolated test database of the same module.
- **NO** production credential, `.env`, or secret was read, written, or logged.
- **NO** frontend production build was produced or deployed.

## 20. Git Status

Before this task: working tree exactly as left by the `b701c6c` checkpoint plus the separate, pre-existing, uncommitted design-system/onboarding/ai-advisor/tooling work already disclosed in `docs/PIOS_TAXI_GIT_CHECKPOINT_REPORT.md` Section 6.

After this task: identical, except the three files in Section 5 gained this task's own additive edits (two of the three — `RideRequest.tsx`, `RideRequest.test.tsx` — were already modified by the pre-existing, unrelated stream; the third, `OrderSubmittedFirstRefusalConsumerIntegrationTest.kt`, was untouched before this task and is now modified only by this task), and this report was added as a new, untracked file. No file was staged and no commit was created, per this task's own explicit instruction — the checkpoint at `b701c6c` remains the current `HEAD`.

```
git status --short   (after this task's work, before any staging)
```
See Section 13 of `docs/PIOS_TAXI_GIT_CHECKPOINT_REPORT.md` for the complete, unchanged list of everything still deliberately unstaged; this task added no new category to that list, only new content inside three already-tracked files plus this one new report.

## 21. Remaining Risks

- **Redelivery-after-resolution gap (pre-existing, Task 16's own disclosure, unaffected by Task 17):** unchanged — still a narrow, accepted, disclosed residual gap, not touched by this task.
- **Proposal accept/decline/lapse caller-identity authorization gap (Task 13's own finding):** unchanged and unexpanded — confirmed by this task's own Phase 8 check that no file touched by Task 17 is `ProposalController.kt` or any authorization-related code.
- **`OrderSubmissionCoordinator` (Passenger Experience) remains dead code with no live HTTP entry point** — confirmed again by this task (Section 2), same as Task 16's own finding; still not removed, since removing unreferenced code was not authorized by this task and carries its own (small) risk of an undiscovered caller.
- **The "automatic First Refusal for an ordinary order" path (Case B) currently has no real frontend caller** — an honest, disclosed structural fact about the current product (Section 2), not a defect: every order the live frontend creates already goes through `RideRequest.tsx`'s own explicit-driver path. The mechanism remains fully tested and ready for whichever future flow needs it (e.g., a hypothetical general-queue order-creation screen), but that flow does not exist in this codebase today, and Task 17 was not authorized to invent one.
- **The pre-existing, unrelated `aiProvider.test.ts` typecheck failure and the pre-existing lint warnings** (Section 15) remain exactly as found — not this task's to fix.

## 22. Deviations from Task

None. Every Phase 0–10 instruction was followed as specified; no STOP condition fired (Section 23); no hard rule was violated (Section 24).

## 23. STOP Conditions — Checked, None Fired

All ten conditions from this task's own Phase 2 were checked explicitly during Phase 0/1 (Section 2) and none applied: the flag is provably atomic with `Order` creation (already true, Task 15C); no public API change was required (`explicitDriverIntent` already existed, unused, on the wire); the frontend flow unambiguously always knows the driver (`driverCode` is a required URL parameter); no Proposal semantics changed; no First Refusal aggregate/application-service semantics changed; this falls squarely within already-Accepted `ADR-062`, no new ADR needed; `explicitDriverIntent`'s only usage matches its already-documented meaning exactly; `Coordinator.tsx` was not touched (it never creates orders); `OrderSubmitted`'s contract was not changed again; and the flag-before-publication race is structurally impossible, not merely improbable (same transaction, Section 12).

## 24. Hard Rules — Compliance

No new ADR; no Proposal state-model change; no First Refusal architecture change; no `OrderSubmitted` payload change; no second intent/priority flag; First Refusal logic stayed in Dispatch, not moved to the frontend or `Coordinator`; First Refusal was not disabled as a workaround; no production DB/RabbitMQ touched; no production service restarted; no unrelated, unfinished work committed (nothing was committed at all, per instruction).

## 25. Final Conclusion

The explicit-driver-intent mechanism Tasks 15C/16 built is now connected to the one real flow in this product that needs it. The fix required zero backend code changes — the entire chain from the public REST contract through to `FirstRefusalApplicationService` was already correctly wired and already tested; only the frontend was silently never using the field it already had. `RideRequest.tsx` now sends `explicitDriverIntent: true` unconditionally, correctly, on every order it creates, and a new, real-infrastructure integration test proves the two previously-racing code paths (automatic First Refusal vs. the explicit proposal) no longer compete: the explicit choice is now the only proposal-creation attempt Dispatch's own First-Refusal path will ever make for an order created through this screen. Full regression is clean across all three backend modules and the frontend, with the sole two anomalies encountered (one known pre-existing residual-test-data collision, one known pre-existing unrelated typecheck error) both independently confirmed as not caused by, and not within scope for, this task.

---

# Task 17 — Final Report

**Status:** COMPLETE
**Files changed:** 3 (`frontend/src/pages/RideRequest/RideRequest.tsx`, `frontend/src/pages/RideRequest/RideRequest.test.tsx`, `backend/dispatch/src/test/kotlin/com/pios/dispatch/persistence/OrderSubmittedFirstRefusalConsumerIntegrationTest.kt`) + this report (new)
**Tests:** Dispatch 417/417, Order Management 167/167, Passenger Experience 75/75, Frontend 223/223 — all passed; 1 new frontend test, 1 new backend integration test
**Production touched:** NO
**Git status:** clean of any staging/commit — checkpoint `b701c6c` unchanged, this task's work left unstaged for review
**Remaining risks:** unchanged pre-existing items only (Section 21) — none introduced by this task

Not proceeding to Payment, Rating, Network Management, authentication hardening, frontend redesign, production deployment, or any other task automatically, per this task's own Final STOP instruction.

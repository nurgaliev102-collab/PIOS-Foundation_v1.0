# Sprint: Pilot Blockers

Status: **Stage 1 (Architect) output — investigation and design proposals only. No code changed by this document.** Opened by Product Owner instruction, 2026-08-06, superseding Platform Operations Center as PIOS's critical path (see `IMPLEMENTATION_PLAN_SPRINT_1_PLATFORM_OPERATIONS_MVP.md`'s own suspension note). Scope was originally the two items the Product Owner named, P0-1 and P0-2 below, both carried over from the pilot-readiness audit that preceded this sprint (three-drivers-working-a-full-day audit, 2026-08-06). Per `.claude/CLAUDE.md`'s Stage 1 → Stage 2 → Stage 3 workflow, neither item is implemented here; each ends with a recommendation for the Architect/Product Owner to approve before any Developer-stage work begins.

**P0-3 added 2026-08-06**, found by `docs/PILOT_FULL_DAY_WALKTHROUGH.md` — a full-day, three-driver simulation ordered before implementation of P0-1/P0-2 began, specifically to catch anything a file-by-file audit could miss. It did: an order whose proposal is never responded to has no reachable terminal state under today's code at all. Investigation/design for P0-3 was explicitly out of scope for that walkthrough ("identify blockers only"); it is recorded here as a finding, not yet designed.

Method: same Evidence Grading convention used throughout this repository — **[RATIFIED]** (observed directly in an approved document or committed code), **[DERIVED]** (a design choice reasoning from that material), **[OPEN]** (a real fork this document flags but does not resolve).

---

## P0-1 — Repeat ride impossible after first resolved order

### The defect, as verified

- `frontend/src/persistence/localCurrentOrder.ts` exposes `getCurrentOrderId(driverCode)` and `saveCurrentOrderId(driverCode, orderId)`. **No function clears or invalidates an entry** — confirmed by reading the file in full.
- `frontend/src/pages/RideRequest/RideRequest.tsx:165-170`, on every load, routes unconditionally:
  ```ts
  const existingOrderId = getCurrentOrderId(forDriverCode)
  if (existingOrderId) {
    setOrderId(existingOrderId)
    setStep('confirmed')
    return
  }
  ```
  No status check, no path back to `'form'`, anywhere in the component.

### Correction to the original audit framing

The screen does **not** show stale or false information once resumed. `RideRequest.tsx:188-250`'s own polling effect re-derives the order's real status from Dispatch on every mount (`GET /v1/proposals?orderId=`, then `GET /v1/assignments?orderId=`) and renders the true current label — `RIDE_STATUS_LABEL['DECLINED']`, `['LAPSED']`, or `['COMPLETED']` — correctly, within one poll cycle (`poll()` fires immediately on mount, `RideRequest.tsx:244`). The defect is narrower and purely structural: **there is no UI affordance anywhere on the `'confirmed'` step to leave it**, regardless of what the correctly-displayed status says. A passenger looking at an accurate "🏁 Поездка завершена" message has no button, link, or action that lets them order this driver again — ever, from this device, for this driver.

### Solution space investigated

1. **Do nothing.** Rejected — directly contradicts `PRODUCT_FOUNDATION.md` §10/§13's central commitment to a durable, repeating driver↔passenger relationship, not a one-off ride. A relationship that can transact exactly once is not durable.
2. **Time-based expiry** (clear the stored id after N hours). Rejected — doesn't reflect the actual fact of resolution. A still-open (`OPEN`) order could be cleared prematurely while genuinely pending; a resolved order could linger past its window for no reason. Invents a timing rule `DOMAIN_MODEL.md` does not establish, which `CLAUDE.md`'s "Never Invent Business Rules" forbids.
3. **Check order status before the initial redirect decision** (query Order/Proposal state before choosing `'form'` vs `'confirmed'` on mount). Rejected as the primary fix — it duplicates status logic the polling effect (`RideRequest.tsx:188-250`) already computes correctly a moment later, adds a second, earlier round-trip, and does not by itself solve the problem: even if the initial redirect skipped straight to `'form'` for a resolved order, nothing stops the *next* resolved order from hitting the exact same dead end again with no fix in the terminal-state rendering itself.
4. **Recommended — add an explicit "order again" affordance at the terminal states, and a function to clear the stored id.** Minimal, additive, and reuses status information the screen already computes correctly:
   - Add `clearCurrentOrderId(driverCode: string): void` to `localCurrentOrder.ts`, mirroring `saveCurrentOrderId`'s existing try/catch-and-swallow pattern (storage may be unavailable; same accepted limitation already documented there).
   - In `RideRequest.tsx`'s `'confirmed'` render branch, when `rideStatus` is `'DECLINED'`, `'LAPSED'`, or `'COMPLETED'` (the three states from which no further server-side progress is possible), render an additional action button ("Заказать ещё раз") that calls `clearCurrentOrderId(driverCode)` and resets local state (`setOrderId(null)`, `setProposalStatus(null)`, `setRideStatus('OPEN')`, `setStep('form')`).
   - `'OPEN'`, `'ACCEPTED'`, `'ARRIVED'`, `'IN_PROGRESS'` keep today's behavior exactly — no action offered while a ride is still pending or under way, which correctly prevents a passenger from placing a second concurrent order with the same driver.

### Why this is the minimal fix

No backend change, no new endpoint, no new poll, no change to what is persisted or its shape (still one order id per `driverCode`, exactly as today — no history, no list, nothing `DOMAIN_MODEL.md` or `PRODUCT_FOUNDATION.md` does not already ask for). It closes exactly the gap found — the missing way back to the form — without adding an order-history feature, a cancellation feature, or any other capability nobody asked for.

### Interaction with P0-2, noted not resolved here

A passenger who wants to stop waiting on an `'OPEN'` (still-pending) order and order differently has no action available under this fix either — that is a cancellation, not a resolved-order reset, and belongs entirely to P0-2 below.

### Recommendation

Approve for Stage 2 (Developer). Estimated as a small, single-PR frontend change: one new function in `localCurrentOrder.ts`, a conditional render branch and a handler in `RideRequest.tsx`. No architecture question is opened — this proposal introduces no new state, event, or cross-module contract.

### Stage 2 — implemented (2026-08-06)

No contradiction with the accepted design surfaced. Exactly the four changes above, frontend-only, no backend contract touched: `clearCurrentOrderId(driverCode)` added to `localCurrentOrder.ts`; `handleOrderAgain` and the "Заказать ещё раз" button added to `RideRequest.tsx`'s `'confirmed'` branch, gated on `rideStatus` being `DECLINED`/`LAPSED`/`COMPLETED`. 18 tests (13 component, 5 unit) pass, plus the full pre-existing frontend suite (40/40) unaffected. Full file list, engineering-decision rationale, and the acceptance walkthrough delivered separately. P0-1 implementation complete, pending Stage 3 (QA).

---

## P0-2 — Missing cancellation lifecycle

### Question 1 — Does any ratified document already exclude cancellation from MVP?

**Yes, in part — verified by direct citation, not inference.** The exclusion is specific, not blanket, and it is important not to overstate it:

- **DOMAIN_MODEL.md** treats Cancellation as a legitimate, named concept (§6, Value Objects: *"Cancellation — the indication that an order or assignment has been terminated before completion"*) and states the general invariant (§11): *"A cancellation may occur only before an order or assignment is completed."* Cancellation is not excluded from the domain model in principle.
- `Order.cancel()` **already exists**, fully implemented, at the domain and application layers (`Order.kt:112-118`, `CancelOrderCommand.kt`, `OrderLifecycleApplicationService.cancelOrder()`) — this was never excluded, it is simply unreachable through any controller, confirmed by the earlier audit's exhaustive controller grep.
- **`Assignment.kt:12-16`** (Dispatch) is explicit and current: *"Cancellation of an assignment (DOMAIN_MODEL.md Section 6, 'Cancellation') is a separate capability and is explicitly out of this task's scope"* — `AssignmentStatus` has no `CANCELLED` value, `Assignment` has no `cancel()` method, and no `AssignmentCancelled` event exists anywhere (`EVENT_CATALOG.md` confirms this — Section 6 lists only `CREATED`→`ACCEPTED`→`ARRIVED`→`IN_PROGRESS`→`COMPLETED`-related events).
- **ADR-041, Open Question 2, resolved by Product Owner amendment (2026-07-30) — the specific, binding exclusion:**
  > *"Cancellation after the ride has started? Not changed in Sprint 3... No `IN_PROGRESS → CANCELLED` path, implicit or explicit, is introduced... the underlying business question (who initiates, who pays, driver treatment, liability) is deferred, not answered, and remains **a prerequisite for ever exposing a cancel endpoint**."*

**Conclusion:** cancellation of an order that has no live Assignment yet was never ruled out by any ADR or Product Decision — it simply was never wired up. Cancellation of an order or assignment that already has a live, ride-progressing Assignment **is** explicitly excluded, by a named, ratified Product Owner decision, pending its own four open business questions. This asymmetry is the organizing fact for the design below.

### Question 2 — Impact analysis and design proposal (not implemented)

The current code's risk profile is not uniform across the order lifecycle, so the design splits into two tiers with very different cost and risk — proposing to build one now and explicitly not the other.

**Tier 1 — Cancel an order with no live Assignment (`Order.status == SUBMITTED`, no `Assignment` yet, at most one `Proposal`, per `DOMAIN_MODEL.md` §11's own invariants).**

- Nearly free: `Order.cancel()`, `CancelOrderCommand`, `OrderLifecycleApplicationService.cancelOrder()`, and the `OrderCancelled` event + outbox record already exist and only need a controller (mirroring the existing `OrderSubmissionController`/`OrderQueryController` pattern).
- **Real, not zero, consistency risk:** an `Order` can be `SUBMITTED` while it has one *open* `Proposal` outstanding (`Proposal` invariant: at most one open proposal per order, but zero-or-one, not "none"). If a passenger cancels while a `Proposal` is open, the `Proposal` is left dangling — a driver could still `accept()` it after the fact, which creates a new `Assignment` (`Proposal.accept` → `Assignment.create`) for an order the passenger already walked away from. This is the same shape of race ADR-041's Open Question 3 already resolved for the *opposite* end of the lifecycle (a late `AssignmentCompleted` against an already-`CANCELLED` Order) — but nothing today closes it at *this* end.
- `Proposal` has no transition for this: `accept`/`decline`/`lapse` are its only three, and `decline` is documented as specifically the driver's own refusing act (`PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md` §3) — reusing it for a passenger-initiated cancellation would misattribute who acted. **[OPEN]** A new `Proposal` transition (e.g. `withdraw()`, actor = Order Management/passenger, distinct from `decline`) is a small, real design fork this proposal flags rather than settles.
- **[DERIVED] Recommended shape:** an `OrderCancelled`-consuming handler inside Dispatch, mirroring the already-ratified pattern Order Management uses for the reverse direction (`OrderAssignmentRecognitionHandler` consuming `AssignmentAccepted`/`AssignmentCompleted`, per ADR-041). Not a direct synchronous cross-module call — Order Management and Dispatch talk only through events today (ADR-019, ADR-041), and a new synchronous REST dependency between them would itself be new, uncited architecture requiring its own ADR. The event-consumer shape requires no new ADR because it only reuses ADR-041's already-ratified integration pattern in the other direction.

**Tier 2 — Cancel or otherwise terminate an order with a live Assignment (`CREATED`/`ACCEPTED`/`ARRIVED`/`IN_PROGRESS`).**

This is exactly what ADR-041's Open Question 2 amendment declined to build, for stated reasons, and this proposal does not attempt to re-open or answer them. Structurally, building it would require, at minimum:

1. A new `AssignmentStatus.CANCELLED` value and `Assignment.cancel()` transition — does not exist today, by `Assignment.kt`'s own explicit statement.
2. A new `AssignmentCancelled` domain event — not in `EVENT_CATALOG.md` today in any form.
3. A decision on which side may initiate: `Order.cancel()` today has zero visibility into `Assignment` state (Order Management and Dispatch do not share status), so wiring a naive order-side cancel endpoint today would let a passenger silently cancel while a driver is already `ARRIVED` or `IN_PROGRESS`, with Dispatch never informed — the exact silent-divergence failure mode ADR-041 was written to prevent, just at the opposite transition.
4. A reverse notification path (Dispatch → Order Management) so a cancelled-mid-ride Order still reaches a coherent terminal status, mirroring the `AssignmentCompleted → Order.complete()` pattern ADR-041 already established for the successful case.
5. **Before any of 1-4:** the four business questions ADR-041's own amendment already named as unanswered — who may initiate a cancellation after ride start, who bears any cost, what happens to the driver (compensation, redispatch), and who bears liability. Per `.claude/CLAUDE.md` ("Never invent business rules... if a business rule is required and undefined, stop and ask"), none of these may be assumed by this document, an ADR, or a Developer. They require their own Product Decision first, exactly as ADR-041's amendment already concluded.

### Recommendation

- **Build Tier 1 for this pilot.** It closes the specific gap that blocks a full day of real driver/passenger usage — a passenger currently cannot even undo an order that no driver has touched yet — at low, well-understood risk, using patterns already ratified elsewhere in this codebase. Needs one small architectural decision before Stage 2: the `Proposal.withdraw()` fork flagged above.

**Update, 2026-08-06.** A Stage 2 implementation attempt against this section alone stopped exactly on that fork (see "Stage 2 implementation attempt — blocked" below). The fork is now resolved as a Draft ADR: [ADR-053: Proposal Resolution on Order Cancellation (P0-2 Tier 1)](ADR/ADR-053-Proposal-Resolution-on-Order-Cancellation.md) — a new terminal Proposal state (`WITHDRAWN`), Dispatch-owned, triggered by consuming Order Management's already-existing `OrderCancelled` event (mirroring ADR-041's own event-consumption pattern in reverse), never a direct write or synchronous call across the module boundary. Requires an `EVENT_CATALOG.md`/`INTERFACE_CONTRACTS.md` documentation sync (Architect work, per ADR-006) before Stage 2 may proceed.
- **Do not build Tier 2 for this pilot.** Its exclusion is not a new finding — it is ADR-041's own already-ratified position, restated here with the concrete engineering shape it would take if the underlying business questions were ever answered. Treat mid-ride cancellation (no-show passenger, driver breakdown, changed plans after the ride has started) as a known, explicitly-deferred operational gap for the pilot's own operating procedure to work around manually, not a code defect.

### Stage 2 implementation attempt — blocked (2026-08-06)

Product Owner instruction requested Stage 2 implementation of Tier 1 strictly per already-accepted Product Decisions and ADRs, with the same explicit fallback used for P0-3: stop and report instead of inventing a solution on contradiction. The connection of `Order.cancel()` itself is safe and fully authorized on its own (ADR-005/009/019, already-existing code). The stop was on the acceptance walkthrough's own steps 5 ("Proposal reaches its correct terminal state") and 7 ("no Assignment is created"): **no ratified document assigns Proposal a transition for passenger-initiated cancellation** — `accept`/`decline`/`lapse` are its only three, `decline` is the driver's own act (`PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md` §3), and reusing `lapse` would misattribute cause to elapsed time (ADR-051/052). Implementing only the Order-side endpoint would leave the Proposal dangling — a driver could still accept it, creating an Assignment for a cancelled order — so it could not honestly be reported as satisfying the walkthrough. **No code changed by this attempt.**

**Resolved as ADR-053** — see above.

### Stage 2 — implemented (2026-08-06)

P0-2 Tier 1 implemented per ratified ADR-053. `Order.cancel()` connected via a new `OrderCancellationController` (Order Management); Dispatch consumes `OrderCancelled` via a new dedicated queue/listener/idempotency ledger and withdraws the order's own `OPEN` Proposal (`ProposalStatus.WITHDRAWN`, new). `PostgreSQLProposalRepository.reconstruct()` updated per ADR-053's own Architectural Prerequisite. `EVENT_CATALOG.md`, `INTERFACE_CONTRACTS.md`, `DOMAIN_MODEL.md` synced. No contradiction with ADR-053 surfaced during implementation. Full file list, engineering decisions, and acceptance evidence in the Stage 2 completion report delivered separately. Tier 2 untouched, per ADR-041's own still-fully-intact exclusion.

---

## P0-3 — Abandoned proposal has no reachable terminal state

**Status: identified, not yet designed.** Found by `docs/PILOT_FULL_DAY_WALKTHROUGH.md`, Ride 3 (steps 12-13), simulating a driver who never responds to a proposal — a realistic full-shift event, not an edge case.

**The finding.** `Proposal.lapse()` (`Proposal.kt:147-154`) and `POST /v1/proposals/{id}/lapse` (`ProposalController.kt:137-151`) both exist and work. Nothing in the running system ever calls either — not `RideRequest.tsx`, not `DriverHome.tsx`, not `Coordinator.tsx` (whose own KDoc states it "does not let the coordinator ... act on any order beyond proposing it"), not any `@Scheduled` backend job. `DOMAIN_MODEL.md` §12 already named this precisely: *"Its precise resolution-recognition mechanism — in particular, what triggers a lapse — remains Domain Decision Required"* — and no subsequent document decided it. An order whose proposal nobody ever answers is therefore, under today's code, simultaneously unable to complete (no driver acts), unable to be cancelled (P0-2), and unable to lapse (no trigger exists) — no in-system action by anyone ever resolves it.

**Relationship to P0-2.** A designed fix for P0-2 Tier 1 (passenger-initiated cancellation of an unassigned order) would give the passenger a way *out* of this state, but would not by itself answer the separate question this finding raises: what should happen to an *open Proposal* on its own, independent of whether the passenger acts — i.e., what "waiting remained appropriate" (`Proposal.lapse()`'s own KDoc) actually means in wall-clock terms, and who or what triggers it. That is a distinct design question, not automatically resolved by P0-2's own scope.

**Not investigated further here, per this task's own instruction** ("only identify remaining pilot blockers" — no feature proposed, no redesign made). A solution design for P0-3, if requested, is future Stage 1 work.

### Lifecycle analysis (2026-08-06) — ownership, not mechanism

Ordered as a follow-up before any P0-3 solution work: which party is actually responsible for this gap, before anything is designed to close it. Four questions, answered strictly from existing ratified documentation and committed code — no mechanism proposed, no scheduler designed, no code written.

**1. Which aggregate owns the responsibility that an OPEN Proposal must never remain OPEN forever?**

None. `Proposal` itself explicitly disclaims it: `Proposal.lapse()`'s own KDoc (`Proposal.kt:136-142`) states *"The mechanism by which lapsing is recognized (a timeout, a policy decision) is not this aggregate's concern... this method only records the fact once it is already established elsewhere."* `DOMAIN_MODEL.md` §7 (Domain Services) names exactly three domain services — Dispatch (assignment decision), Notification Delivery, Analytics Aggregation — none described as detecting a stale Proposal. `PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md` explicitly organizes two of its own sections around exactly this kind of question (§6 "Who Owns the Opportunity Lifecycle?", §7 "Who Owns the Commitment Lifecycle?" — answered there, ratified, as Dispatch) — and contains no equivalent section for who owns resolving an unanswered Proposal/Opportunity. Its own §9 separately classifies **Expiration** as **[OPEN] — no ratified concept; not implemented**, distinct from Decline and Cancellation. No aggregate, entity, or domain service is assigned this responsibility anywhere in the documentation set.

**2. Is that responsibility currently assigned anywhere?**

No. `DOMAIN_MODEL.md` §4 (Proposal, Invariant) states this in its own words, currently: *"The precise mechanism by which a Proposal's outcome is recognized — in particular, what triggers a lapse — remains **Domain Decision Required**: Product Decision: Electronic Dispatcher MVP Blockers v1.0 ratifies only that a single, configurable, non-adaptive timeout parameter exists (Proposal Timeout Policy), never its value or triggering mechanism."* The one document that would presumably design that mechanism, `ELECTRONIC_DISPATCHER_DOMAIN_DESIGN_V1.md`, is itself unratified — its own status line: *"Proposed — Design Blueprint, not yet a Product Decision or ADR... Nothing in this document is ratified merely by being written here."*

**3. Missing Product Decision, missing Domain Decision, or implementation defect?**

**Missing Domain Decision — not a Product Decision gap, and not an implementation defect.** Two things are already settled and should not be re-opened:
- The *product policy content* is already ratified: `PRODUCT_DECISION_ELECTRONIC_DISPATCHER_MVP_BLOCKERS.md` PD-2 decided a timeout must exist, must be a single configurable operational parameter, and must not be adaptive, personalized, or algorithmically hidden. It deliberately left the concrete numeric value unratified on purpose (*"должно... корректироваться по данным реального пилота"* — meant to come from real pilot data, not to be guessed pre-pilot).
- What remains open is purely the *mechanism* — which party detects elapsed time and invokes `Proposal.lapse()` — and `DOMAIN_MODEL.md` uses its own formally defined term for exactly this: **"Domain Decision Required."** That term is not incidental prose; `DOMAIN_MODEL.md` line 198 defines its governing effect explicitly: *"Where this document is silent or marks a concept Domain Decision Required, no lower-priority document may resolve it; resolution requires a new ADR."*
- It is not an implementation defect: `Proposal.kt` behaves exactly as the documentation record specifies — it deliberately declines to invent an answer to a question the architecture has not yet settled, rather than silently guessing one. The code is consistent with the documented gap, not in violation of it.

**4. Every legal terminal state an OPEN Proposal may reach, per existing documentation.**

Exactly three, per `DOMAIN_MODEL.md` §4 (Proposal, Lifecycle), verbatim: *"it resolves, exactly once, to one of three outcomes — confirmed by the driver, refused by the driver, or lapsed for want of a timely response... it has no supported transition back to open once resolved."*

1. **ACCEPTED** — confirmed by the driver (`Proposal.accept()`).
2. **DECLINED** — refused by the driver (`Proposal.decline()`).
3. **LAPSED** — lapsed for want of a timely response (`Proposal.lapse()`) — the state whose *triggering mechanism* is the Domain Decision Required gap named above; the state itself is ratified, only how a Proposal ever reaches it is not.

No fourth terminal state is documented anywhere. A passenger-initiated withdrawal (`Proposal.withdraw()`, informally discussed as a possible future design fork in this same document's own P0-2 Tier 1 section above) is **not** a ratified or existing state — it was this document's own proposed design fork, not something the current architecture recognizes. `ProposalStatus.kt`'s own enum (`OPEN, ACCEPTED, DECLINED, LAPSED`) confirms no fourth value exists in code either.

### Ownership resolution (2026-08-06)

The lifecycle analysis above answers *what* states exist, not *who* is responsible for reaching LAPSED. That ownership question was investigated separately, architecture-only, and resolved in [ADR-051: Proposal Lifecycle Resolution Ownership (OPEN → LAPSED)](ADR/ADR-051-Proposal-Lifecycle-Resolution-Ownership.md) — **Accepted 2026-08-06**. Summary: Dispatch's own application layer owns both detecting elapsed time and invoking `Proposal.lapse()` — no other bounded context has a ratified contract to `Proposal`, and infrastructure (including `platform-ops`) is categorically excluded from domain ownership by ADR-046 Decision 1. ADR-051 assigns ownership only; it authorizes no mechanism and no implementation. A dedicated review (recorded in ADR-051 itself) subsequently confirmed this assignment against Domain Service precedent and left it unchanged.

### Stage 2 implementation attempt — blocked (2026-08-06)

Product Owner instruction requested Stage 2 implementation of P0-3 strictly per ADR-051, with an explicit fallback: if implementation reveals a contradiction with ADR-051, stop and produce an engineering report instead of continuing. That fallback triggered immediately, before any code was written. Two independent blockers, both in ADR-051's own text:

1. **ADR-051 is still Draft** (*"awaiting Product Owner / Architect ratification"*) — per this project's own established rule for exactly this situation (`IMPLEMENTATION_PLAN_SPRINT_1_PLATFORM_OPERATIONS_MVP.md` Part 0: *"Nothing... may be handed to a Developer while the documents this sprint depends on are still marked Draft"*; `PROJECT_CONSTITUTION.md`, quoted in ADR-036: *"An architectural change made without a corresponding ADR is considered undocumented and is not valid"*), implementation may not proceed on a Draft ADR alone.
2. **ADR-051 Part 3 explicitly withholds the mechanism decision implementation requires**: *"No mechanism. Whether detection is polling-based, event-driven, or evaluated at read-time is not decided here and is explicitly out of scope."* Its own Negative Consequences state directly: *"No implementation of automatic lapse-resolution may proceed on this ADR alone — a follow-up ADR... must still define the mechanism, which this document deliberately does not do."*

Deciding a trigger mechanism to make the code reachable would itself be an undecided architectural choice (forbidden by this task's own instruction not to change architecture or introduce new mechanisms); leaving the mechanism undecided and shipping only unreachable code (mirroring today's already-unreachable `cancelOrder`/`lapseProposal`) would not actually resolve P0-3's real symptom and could not honestly be reported as "implemented" (`.claude/CLAUDE.md`: "Never fabricate execution results"). No third option exists under ADR-051 as currently Draft. **No code changed by this attempt.** Unblocking requires: (a) ADR-051 ratified to Accepted, and (b) a separate, future architectural decision on the trigger mechanism — neither performed here, consistent with this task's own instruction not to redesign architecture.

**(b) is resolved in** [ADR-052: Proposal Lapse Resolution Mechanism](ADR/ADR-052-Proposal-Lapse-Resolution-Mechanism.md) — **Accepted 2026-08-06** — a scheduled, module-local, Dispatch-application-layer trigger, structurally mirroring the existing `OutboxRelayScheduler` pattern, recommended over event-driven, client-side-polling, read-time, and new-framework alternatives. Mechanism shape only — no code, no configuration value, no implementation task.

**Update (ratification), 2026-08-06.** Both blockers named above are cleared: ADR-051 and ADR-052 have each been ratified to **Accepted**. The historical description above (why the attempt was blocked at the time) is preserved unchanged as the record of what happened; it no longer describes the current state. Remaining, non-architectural prerequisites before a Stage 2 attempt can succeed are recorded in ADR-052 itself (the Proposal Enumeration prerequisite) and in the Consistency review immediately below.

### Consistency review (2026-08-06) — ADR-051 + ADR-052 together

A dedicated review checked both ADRs for contradictions, missing decisions, circular dependencies, undefined responsibilities, and remaining implementation blockers. Product Owner assessment: architecture ~95% ready, no further new ADR needed, remaining items are drafting/completeness, not concept gaps. Two corrections applied directly to ADR-052 as a result:

- **Terminology fixed.** ADR-052 previously described its own scheduled trigger as both "infrastructure" (Decision diagram) and "application-layer, not infrastructure" (Positive Consequences) for the same component. Resolved with a dedicated "Terminology, fixed here" section distinguishing *mechanism placement* (the timer — infrastructure) from *architectural responsibility* (the decision to check and invoke — application layer, per ADR-051 Part 2), consistent with `OutboxRelayScheduler`'s own KDoc.
- **`ProposalRepository` gap elevated to a named architectural prerequisite.** `ProposalRepository` exposes only `findById`/`findByOrder`/`findByDriver` — no way to enumerate `OPEN` Proposals, which the selected mechanism cannot function without. Previously undisclosed; now recorded in ADR-052 as a binding prerequisite (mirroring ADR-034's own disclosure of the analogous `DriverAvailabilityRepository` gap), not left as a detail an implementer discovers by surprise.

Confirmed, not corrected — findings the Product Owner classified as real but non-blocking or out of ADR-051/052's own scope: `Proposal.lapse()`'s concurrent-invocation exception handling (already honestly disclosed by ADR-052 as open; accepted as non-blocking for a single-instance pilot deployment); the Order's own status after LAPSED (assigned to P0-2's own territory, not a defect in ADR-051); P0-1's return-to-form affordance and the `DriverHome.tsx` display filter (both UX/engineering-implementation concerns, not architecture — ADRs govern how the system works, not what a user sees). Also confirmed: the wider ratification-chain gap (`APPLICATION_ARCHITECTURE.md`, ADR-034, ADR-035 all still Draft/Proposed) is a governance question for the Product Owner to resolve at ratification time, not an architectural defect in ADR-051/052 themselves.

Per Product Owner direction: no further architectural investigation of P0-3 is to be opened unless a real contradiction surfaces during implementation. **ADR-051 and ADR-052 have been ratified to Accepted, 2026-08-06**, per explicit Product Owner instruction. The wider ratification-chain gap noted above (`APPLICATION_ARCHITECTURE.md`, ADR-034, ADR-035) was explicitly out of scope of this ratification and remains Draft/Proposed — unchanged by this update.

### Stage 2 — implemented (2026-08-06)

No contradiction with ADR-051/ADR-052 surfaced during implementation. `ProposalRepository.findOpen()` (the Architectural Prerequisite ADR-052 names) added and implemented in both adapters; `ProposalLapseApplicationService` (detection + invocation, Dispatch application layer, per ADR-051 Part 2) and `ProposalLapseScheduler` (the periodic trigger, structurally mirroring `OutboxRelayScheduler`, per ADR-052's selected mechanism) added. New tests pass (6 unit + 1 real-Postgres integration proving the trigger fires automatically); the pre-existing `ProposalApplicationServiceTest` suite (20 tests) passes unchanged, confirming no regression. Full details, file list, and engineering-decision rationale in the Stage 2 completion report delivered separately. P0-3 implementation complete, pending Stage 3 (QA).

---

## Files Changed (this planning task)

`docs/SPRINT_PILOT_BLOCKERS.md` (P0-3 section added, 2026-08-06). `docs/PILOT_FULL_DAY_WALKTHROUGH.md` (new, same date — the walkthrough that found P0-3). `docs/IMPLEMENTATION_PLAN_SPRINT_1_PLATFORM_OPERATIONS_MVP.md` and `docs/ENGINEERING_EXECUTION_PLAN_SPRINT_1_PLATFORM_OPERATIONS_MVP.md` — suspension status notes added earlier, no other content changed or removed. No source code touched by this task.

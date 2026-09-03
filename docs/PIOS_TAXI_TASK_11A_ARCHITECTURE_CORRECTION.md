# PIOS Taxi Task 11A — Trip Creation Trigger Correction

Status: **Documentation-only architectural correction.** Corrects `ADR-063`'s Trip-creation trigger from `AssignmentAccepted` to `OrderAssigned`, based on runtime evidence gathered during Task 11's own mandatory pre-implementation inspection. No application code, test, migration, schema, RabbitMQ configuration, or frontend file was touched to produce this document or the correction it records.

---

## 1. Original Assumption

`ADR-063` (Accepted, after a first correction in Task 10B) named the authoritative Trip-creation trigger as the `AssignmentAccepted` event/outcome — chosen specifically to replace an earlier, even less reliable assumption (`Assignment.status == ACCEPTED`, the mutable field). The Task 10B correction reasoned: *"Trip creation must instead be wired to the production of the `AssignmentAccepted` event/outcome — the authoritative fact both `ADR-040` itself and Order Management's own existing consumer already treat as the true signal that an assignment is confirmed."* This assumed the event/outcome itself, if not the field, was reliably produced by the live Proposal-acceptance path.

## 2. Runtime Evidence

Gathered by direct source inspection during Task 11's own mandatory pre-implementation steps 1–3 (inspect Dispatch implementation, inspect Assignment aggregate/state machine, inspect `AssignmentAccepted` definition and publication):

1. `AssignmentAccepted` (`backend/dispatch/src/main/kotlin/com/pios/dispatch/domain/AssignmentAccepted.kt`) is constructed in exactly one place in the entire codebase: `Assignment.accept()` (`Assignment.kt:106-113`), as its return value.
2. `Assignment.accept()` is called in exactly one production method: `DispatchAssignmentApplicationService.acceptAssignment(...)` (`DispatchAssignmentApplicationService.kt:117-142`), which correctly persists the Assignment and writes a real, correctly-routed (`assignment.accepted`) outbox record.
3. **`acceptAssignment` is never called by any controller.** `AssignmentController.kt` exposes exactly five endpoints — `assignOrder` (POST, create), `listAssignments` (GET), `/{id}/arrive`, `/{id}/start`, `/{id}/complete` — and no `/accept` endpoint of any kind. An exhaustive repository-wide search (`grep -rn "acceptAssignment|AcceptAssignmentCommand" backend/dispatch/src/main`) found zero call sites beyond the method's own definition, its command class, and one KDoc mention in `ProposalApplicationService.kt` that only references it in prose.
4. **The only production path that creates an Assignment via Proposal acceptance — `ProposalAssignmentOrchestrationService.acceptProposal` — calls `DispatchAssignmentApplicationService.handleWithinCallerTransaction(AssignOrderCommand(...))`, which calls `Assignment.create()`, never `Assignment.accept()`.** `Assignment.create()` returns an `AssignmentCreated` outcome and publishes `OrderAssigned`.
5. **The only other production path that creates an Assignment — `AssignmentController.assignOrder` (Coordinator manual assignment) — uses the sibling `handle(AssignOrderCommand)` overload of the same application service, also `Assignment.create()`, also publishing `OrderAssigned`, never `.accept()`.**

**Conclusion: `AssignmentAccepted` is never published by the running system today, through any of its two real production paths.**

**Corroborating evidence, not required to reach the conclusion:** Order Management's own `AssignmentAcceptedListener` is fully built and correctly bound to routing key `assignment.accepted`, waiting for a message that never arrives. Independently, `OrderAssigned` — the event that does actually fire on every real Assignment creation — has no consumer at all in Order Management, despite `INTERFACE_CONTRACTS.md` Section 7 describing it as one. Section 7 below carries this forward as its own, separate finding.

## 3. Corrected Decision

**The authoritative Trip creation trigger is now `OrderAssigned`, not `AssignmentAccepted`.**

```
Proposal accepted / Coordinator assignment
        ↓
Assignment.create()
        ↓
OrderAssigned
        ↓
Trip.create()
```

This is ratified by amending `ADR-063` in place (Section 8 below) — its own Status and Decision sections now preserve all three successive attempts at this trigger definition (the original `Assignment.status`-based text, the Task 10B `AssignmentAccepted`-based correction, and this `OrderAssigned`-based correction), per `CLAUDE.md`'s "Never Delete Documentation." `ADR-063` remains **Accepted** — only its technical trigger changed, consistent with this task's own explicit instruction not to create a new ADR for this correction.

## 4. Why `OrderAssigned` Is Authoritative

- It is published, unconditionally and reliably, by `Assignment.create()` — the one domain-layer factory method both real production Assignment-creation paths call. There is no branch, precondition, or unreachable code path between "an Assignment is created" and "`OrderAssigned` is published," unlike `AssignmentAccepted`, which requires a call (`.accept()`) that never happens in either live path.
- It represents the correct conceptual moment for Trip's own creation: a driver has just been connected to an order. This is consistent with `ADR-063`'s own unchanged reasoning that Trip's creation should be entirely Dispatch-internal (an in-process reaction to an event Dispatch itself already publishes, never a new cross-module contract) — `OrderAssigned` satisfies this exactly as well as `AssignmentAccepted` would have, had it actually fired.
- **Trip creation does not mean the ride has started.** `OrderAssigned` fires before any ride-progress fact exists — before arrival, before the ride itself. `Trip` must therefore begin in its own initial, pre-`ARRIVED` lifecycle state (Section 6 below), distinct from any ride-progress state — a driver being connected to an order is not yet a driver having reached the passenger.

## 5. Why `AssignmentAccepted` Is Not Being Artificially Activated

Per this task's own explicit instruction, three tempting shortcuts were rejected:

- **Not wiring `AssignmentAccepted` into an existing production flow merely to satisfy `ADR-063`'s original text.** Doing so would mean changing `ProposalAssignmentOrchestrationService`'s or `AssignmentController.assignOrder`'s own behavior — real production code, real transactional guarantees (`ADR-036`'s own carefully-scoped shared transaction), well outside a documentation-only correction's scope, and outside Trip's own architectural need (Trip does not require `AssignmentAccepted` to exist; `OrderAssigned` already serves the purpose).
- **Not creating a fake acceptance step.** No synthetic call to `Assignment.accept()`, no new endpoint, no compatibility shim was added anywhere.
- **Not changing the meaning of `AssignmentAccepted`.** The event class, its shape, its intended meaning ("a proposed assignment has been confirmed"), and its outbox-publication code all remain exactly as they are — a real, correctly-modeled, currently-unreachable capability, not deleted, not repurposed, not redefined.

`AssignmentAccepted`'s own eventual activation (if ever decided) remains a distinct, separate question from Trip's own creation trigger — Section 7 below tracks it as its own, unresolved integration gap.

## 6. Current Order/Assignment/Trip Lifecycle (Corrected)

```
Order:      SUBMITTED → COMPLETED
                      → CANCELLED
                                                (Order Management, unaffected)

Assignment: CREATED → ACCEPTED                 (the match; ACCEPTED reachable only via
                                                 the still-unreachable /accept endpoint —
                                                 unaffected by this correction)

            └─ on creation (Assignment.create(), either real path) → OrderAssigned
                                                                          │
                                                                          ▼
Trip (target, not yet implemented):                        [initial state] → ARRIVED → IN_PROGRESS → COMPLETED
                                                (Dispatch-internal; TripArrived/Started/Completed —
                                                 a future rename of AssignmentArrived/Started/Completed,
                                                 not performed by this task)
```

Trip's own initial state (preceding `ARRIVED`) is named here only as a requirement, not decided — its exact label (e.g. `CREATED`, mirroring `AssignmentStatus`'s own convention) is left to the implementation task, consistent with `ADR-063`'s own discipline of not inventing implementation detail in an architecture document.

## 7. Separate Order Management Event Gap (Documented, Not Fixed)

**This is a distinct, pre-existing integration risk, explicitly not addressed by this task:**

1. Order Management contains a fully-built, correctly-wired `AssignmentAcceptedListener`, bound to routing key `assignment.accepted`.
2. `AssignmentAccepted` is not currently emitted by any live Assignment-creation path (Section 2 above) — so this listener has never received a message.
3. `OrderAssigned` is emitted, reliably, by both live Assignment-creation paths — but has no consumer of any kind in Order Management, despite `INTERFACE_CONTRACTS.md` Section 7 describing it as one.
4. **Therefore Order Management currently lacks a working event-driven signal for assignment completion, for any reason** — neither of the two candidate events actually closes this loop today.

**This is not fixed in this task.** No Order Management code was modified. No RabbitMQ topology was modified. No consumer was created. This is recorded as a finding for a future, separately-scoped task to address — its resolution is independent of Trip's own implementation, since Trip's new `OrderAssigned` consumption is entirely Dispatch-internal and does not require or depend on Order Management ever consuming anything.

## 8. Documents Updated

| Document | Change |
|---|---|
| `docs/ADR/ADR-063-Trip-as-a-New-Dispatch-Owned-Aggregate.md` | Trigger corrected a second time (Status section, Decision section's own trigger bullet, Context's event list, Consequences, Traceability, Files Changed) — all three successive trigger definitions preserved, none deleted. Status remains **Accepted**. |
| `docs/PIOS_TAXI_ARCHITECTURE_CONTRACT.md` | Sections 9 (Event Model table) and 13 (Order/Assignment/Trip) corrected to state `Assignment → OrderAssigned → Trip`; Section 8's idempotency-precedent reference corrected (no longer cites the non-production `AssignmentAccepted`); Section 20 gained the new Order Management gap as an open question; Section 21's gate item 2 updated; top-of-document status note added. |
| `docs/PIOS_TAXI_ARCHITECTURE_RATIFICATION.md` | Sections 1, 2, 4, 6, 8, 9 updated for internal consistency with the corrected trigger; Section 6 gained a new item 5 (the Order Management event gap, explicitly distinguished from the security-blocker items); top-of-document status note added. No new ADR was created, per this task's own instruction. |

`INTERFACE_CONTRACTS.md` and `EVENT_CATALOG.md` were **not** touched — both already describe only current, live runtime behavior for `AssignmentAccepted`/`OrderAssigned` (Task 10B's own TARGET-marked notes for `ADR-063` describe the *rename*, not the trigger, and remain accurate); their own TARGET notes will need the trigger detail added when Trip is actually implemented, not before, consistent with those documents' own discipline of never describing unbuilt behavior as current.

## 9. Implementation Implications

- The next implementation task (superseding this task's own STOP report, `docs/PIOS_TAXI_TASK_11_TRIP_FOUNDATION_REPORT.md`) should wire Trip's creation into **both** real `Assignment.create()` call sites — `ProposalAssignmentOrchestrationService.acceptProposal` and `AssignmentController`'s own assign-order application-service path — reacting to `OrderAssigned`, in-process, not via a new RabbitMQ consumer (Trip's creation remains Dispatch-internal, unchanged reasoning from `ADR-063`'s own original text).
- Idempotency (exactly one Trip per Assignment, surviving process restart) remains exactly as required by Task 11's own original scope — unaffected by which event triggers creation, since the uniqueness constraint belongs on the Assignment-reference, not on the triggering event's own identity.
- The Order Management event gap (Section 7) is **not** a precondition for implementing Trip — Trip's own correctness does not depend on Order Management consuming anything.
- `ADR-063`'s own dual-publish migration window for the eventual `Assignment*` → `Trip*` rename is unaffected by this correction — that migration concerns the ride-progress events (`Arrived`/`Started`/`Completed`), not the creation trigger (`OrderAssigned`, which is not renamed by `ADR-063` at all).

## 10. No Code Was Changed

**Explicit statement, per this task's own requirement:** no Kotlin file, no TypeScript file, no test file, no migration, no RabbitMQ topology or configuration, and no API was created, modified, or deleted by this task. Every change described above is to markdown documentation only — `ADR-063`, `docs/PIOS_TAXI_ARCHITECTURE_CONTRACT.md`, `docs/PIOS_TAXI_ARCHITECTURE_RATIFICATION.md`, and this document itself.

---

## Final Report

**Files modified:**
- `docs/ADR/ADR-063-Trip-as-a-New-Dispatch-Owned-Aggregate.md`
- `docs/PIOS_TAXI_ARCHITECTURE_CONTRACT.md`
- `docs/PIOS_TAXI_ARCHITECTURE_RATIFICATION.md`

**Files created:**
- `docs/PIOS_TAXI_TASK_11A_ARCHITECTURE_CORRECTION.md`

**Files deleted:** none.

**ADR changes:** `ADR-063`'s Decision (trigger) corrected a second time; **Status remains Accepted** (unchanged) — no new ADR was created, per this task's own explicit instruction.

**Architecture changes:** Trip's creation trigger changed from `AssignmentAccepted` to `OrderAssigned` at the documentation/architecture level only. No runtime behavior changed — nothing was implemented in either version.

**Tests/checks executed:** none — this was a call-graph/source-reading verification (carried over from Task 11's own inspection), not a test run.

**PostgreSQL touched:** NO
**RabbitMQ touched:** NO
**Production services restarted:** NO

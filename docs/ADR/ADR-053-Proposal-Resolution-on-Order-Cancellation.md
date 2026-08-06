# ADR-053: Proposal Resolution on Order Cancellation (P0-2 Tier 1)

## Status

Draft — awaiting Product Owner / Architect ratification. This ADR resolves the `[OPEN]` design fork `docs/SPRINT_PILOT_BLOCKERS.md`'s own P0-2 Tier 1 section named and left unsettled, and answers the contradiction the Stage 2 implementation attempt against that section stopped on (`SPRINT_PILOT_BLOCKERS.md`, P0-2, "Stage 2 implementation attempt — blocked"). No code, no timeout value, no endpoint shape beyond what is needed to state the decision, is authorized here beyond what each Part below explicitly says.

## Context

Connecting Order Management's already-existing `Order.cancel()` to a real endpoint (P0-2 Tier 1) is safe and fully authorized on its own — `Order` is Order Management's exclusive aggregate (ADR-005/009/019), the transition already exists, and exposing it introduces no new status, no domain redesign. The unresolved question is narrower and was named, not answered, when Tier 1 was first designed:

> *"`Proposal` has no transition for this: `accept`/`decline`/`lapse` are its only three, and `decline` is documented as specifically the driver's own refusing act (`PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md` §3) — reusing it for a passenger-initiated cancellation would misattribute who acted. **[OPEN]** A new `Proposal` transition (e.g. `withdraw()`, actor = Order Management/passenger, distinct from `decline`) is a small, real design fork this proposal flags rather than settles."*

Without an answer, cancelling an Order while its Proposal is still `OPEN` leaves that Proposal dangling: a driver can still `accept()` it after the fact (Dispatch has no visibility into Order status — module isolation, ADR-019), creating an Assignment for an order that no longer exists as an active request. This ADR closes that gap, the same way ADR-051/052 closed the structurally identical gap for `OPEN → LAPSED` — this document deliberately reuses that precedent's reasoning wherever it transfers.

## Problem

When an Order is cancelled while its Proposal is still `OPEN` (before driver acceptance), what happens to the Proposal — who decides, what state does it reach, what event records it, and does anything else in the system observe or depend on that fact?

## Decision

### Part 1: Bounded-Context Ownership

**Dispatch owns the transition, exclusively — unchanged from ADR-051 Part 1's own reasoning, applied to a new trigger.** `Proposal` remains Dispatch's own aggregate root (ADR-035); `DOMAIN_MODEL.md` §11 ("Only the domain that owns a piece of information... may change it") applies to this transition exactly as it does to `accept`/`decline`/`lapse`. Order Management may never write to `Proposal` directly, regardless of what triggers the need — ADR-005/009/019's exclusive-ownership rule applies here without exception, exactly as it already does everywhere else in this codebase.

**The triggering fact, however, originates in Order Management** — `OrderCancelled`, already a real domain event, already written to Order Management's own outbox by `OrderLifecycleApplicationService.cancelOrder()` (confirmed by direct inspection: `outboxRecordFor(event: OrderCancelled)` already exists and is already wired, unused only because no endpoint calls `cancelOrder` yet). Dispatch learns of it the same way it already learns of `DriverAvailabilityChanged` and would need to learn of any other cross-module fact: **by consuming an event Order Management publishes**, never by Order Management calling into Dispatch, and never by Dispatch polling Order Management. This is the identical shape ADR-041 already established for the reverse direction (Order Management consuming Dispatch's `AssignmentAccepted`/`AssignmentCompleted`) — this ADR is that same pattern, applied the other way.

### Part 2: New Terminal State, Not a Reused One

**`Proposal` gains a fourth terminal state: `WITHDRAWN`.** Not `CANCELLED` — deliberately a different word from Order's own `OrderStatus.CANCELLED`, even though the two are causally linked, for the same reason this codebase already keeps causally-linked facts in different aggregates conceptually distinct (ADR-041 Decision item 2: *"two facts owned by two different modules that happen to be causally linked, not one fact stored twice"*). `DOMAIN_MODEL.md` §6 already names **Cancellation** as a value object, but scopes it explicitly to *"an order or assignment"* — not Proposal. This ADR does not stretch that existing term over a new aggregate; it names a new one, consistent with `PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md` §9's own established discipline of keeping causally-adjacent concepts (there: Decline, Expiration, Cancellation, Failure) *"confirmed as conceptually distinct — none is a synonym for another."* `WITHDRAWN` joins that set as a fifth, distinct concept: the proposal is taken back because the request behind it no longer exists, which is neither a refusal (Decline), nor a timeout (Expiration/`LAPSED`), nor the order/assignment's own termination (Cancellation) — it is Proposal's own reflection of a fact that happened one level up.

Reusing an existing value was evaluated and rejected — see Alternatives, below.

### Part 3: The Actor Represented

**The actor is Order Management, on the order's own behalf — not the passenger directly, and not the driver.** Dispatch has no contract with Passenger Experience at all (`ADR-034` Part 1: *"no contract to Dispatch exists in INTERFACE_CONTRACTS.md Section 5"*) and never will merely because of this ADR — it does not learn "a passenger cancelled," it learns "the order this proposal is for has been cancelled," exactly as it never learns "a driver accepted," only that `AssignmentAccepted`/`ProposalAccepted` occurred. This mirrors `PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md`'s own established pattern of treating an event's firing as a recorded fact, not a channel for one domain to learn about another domain's own internal actors.

### Part 4: Events Emitted

**`ProposalWithdrawn`** — a new Dispatch-owned domain event, added to `EVENT_CATALOG.md` §6 alongside `OrderProposed`/`ProposalAccepted`/`ProposalDeclined`/`ProposalLapsed`, mirroring their exact shape and treatment: **Dispatch-internal only**, not published to Dispatch's own outbox, for the identical reason `ProposalApplicationService`'s own KDoc already gives for the other four — no other domain currently needs to consume it, and publishing it unasked would create an external contract nobody has ratified.

**`OrderCancelled` becomes a cross-domain business event under ADR-003**, for the first time — Dispatch now relies on it, which is exactly ADR-003's own threshold (`EVENT_CATALOG.md` §9: *"becomes a business event... wherever another domain relies on it"*). This is a real, new consequence of this ADR, not a documentation triviality: `EVENT_CATALOG.md` §9 and `INTERFACE_CONTRACTS.md` §5 both currently list only the Dispatch → Order Management direction (`OrderAssigned`/`AssignmentAccepted`/`AssignmentCompleted` one way; nothing the other way). This ADR **authorizes** the new Order Management → Dispatch relationship; it does not itself edit `EVENT_CATALOG.md`/`INTERFACE_CONTRACTS.md` — per `ADR-006` and the precedent `IMPLEMENTATION_PLAN_SPRINT_1_PLATFORM_OPERATIONS_MVP.md` Part 0.2 already established (*"the endpoints in scope must be recorded in API_SPECIFICATION.md and INTERFACE_CONTRACTS.md before implementation. That is Architect work"*), that documentation-sync is a required follow-up task, not optional cleanup, and must land before implementation, exactly as `ADR-041`'s own Consequences section required the identical sync for `AssignmentCompleted`.

**Consumer shape (illustrative, mirroring ADR-034 Part 3's own diagram convention — no interface, class, or file authorized here):**

```
Order Management                         Dispatch
  cancelOrder()                            OrderCancelledListener (new,
        |                                  own dedicated queue, mirroring
        v                                  AssignmentCompletedListener's
  OrderCancelled -----(outbox/RabbitMQ)--> own precedent: ADR-041 Decision
  (already wired,                          item 5 explicitly rejected
   unused today)                           sharing a queue across event
                                            types for this exact reason)
                                                  |
                                                  v
                                          Dispatch application layer
                                            (finds the order's own OPEN
                                             Proposal via findByOrder,
                                             same lookup ProposalApplicationService.handle
                                             already performs; invokes
                                             Proposal.withdraw())
                                                  |
                                                  v
                                          Proposal.withdraw() -> WITHDRAWN
```

A dedicated queue, not a shared binding on any existing one — the same reasoning `ADR-041` Decision item 5 already gave for `AssignmentCompleted` transfers without modification: a handler hard-requiring one `eventType` on a queue shared with another would dead-letter every message of the other kind.

**Not authorized by this ADR:** a manual `POST /v1/proposals/{id}/withdraw` endpoint. Tier 1's own scope is cancellation *of an order*; the only path into `WITHDRAWN` this ADR opens is the automatic one, triggered by `OrderCancelled`. A directly-callable withdraw endpoint would be a different capability, not requested, and is not decided here.

### Part 5: Queryability

**Unchanged — every existing read path continues to work exactly as today.** `GET /v1/proposals/{id}`, `?orderId=`, `?driverId=` all remain fully queryable for a `WITHDRAWN` Proposal, mirroring `DECLINED`/`LAPSED`'s own existing behavior precisely — no proposal is ever deleted or hidden on resolution, and this ADR introduces no exception to that.

### Part 6: Driver UI and Passenger UI

**Both observe the same fact through the same existing query surface — no new endpoint for either.** `DriverHome.tsx` already polls `GET /v1/proposals?driverId=`; `RideRequest.tsx` already polls `GET /v1/proposals?orderId=`. Both already receive whatever `status` a Proposal carries. `WITHDRAWN` is one more value flowing through paths that already exist — **not decided here**: the exact wording either screen renders for it (a presentation-layer concern, out of this ADR's scope, per the same "does not decide code" boundary ADR-051/052 already applied) and the fact that both screens' own TypeScript status unions (`ProposalListItem['status']` in each file) will need `'WITHDRAWN'` added to remain exhaustive — a real, disclosed follow-up implementation requirement, not resolved by this ADR, named here so it is not discovered by surprise (mirroring ADR-052's own "Architectural Prerequisite" disclosure discipline).

### Part 7: Compatibility with Existing Product Decisions

Checked directly against every Product Decision and ADR this touches or is adjacent to:

- **ADR-041 (cancellation after ride start remains excluded).** Untouched. This ADR concerns only a Proposal with no Assignment yet — structurally before anything ADR-041 governs. Tier 2 is not implemented, referenced as authorizing anything, or brought closer to being authorized by this document.
- **ADR-051 / ADR-052 (`OPEN → LAPSED`).** Untouched and structurally isolated: `findOpen()` (ADR-052's own prerequisite) filters `status = 'OPEN'`, so a `WITHDRAWN` Proposal is automatically excluded from the lapse sweep the same way `DECLINED`/`ACCEPTED`/`LAPSED` already are — no interaction, no race between the two mechanisms, confirmed by the query's own definition, not merely assumed.
- **`PRODUCT_DECISION_ELECTRONIC_DISPATCHER_MVP_BLOCKERS.md` PD-3 (Concurrent Proposal Policy — max one `OPEN` Proposal per order).** Unaffected — `WITHDRAWN` is a resolved status, identical in kind to `DECLINED`/`LAPSED` for this invariant's own purposes (only `OPEN` proposals count against it).
- **`PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md` §9.** Extended, not contradicted — `WITHDRAWN` joins Decline/Expiration/Cancellation/Failure as a fifth conceptually distinct outcome, per that document's own discipline of never treating two causally different things as one.
- **ADR-019 / ADR-005 / ADR-009 (exclusive ownership).** Preserved throughout: Dispatch alone ever writes `Proposal.status`; Order Management alone ever writes `Order.status`; the only thing crossing the module boundary is an already-established-pattern event, not a write.
- **ADR-026 (independent deployability).** Preserved — Order Management does not block on Dispatch's availability to cancel an order (the outbox write already commits independently, exactly as it does today); Dispatch's own consumption is asynchronous and can lag or be temporarily down without blocking Order Management's own cancellation.
- **A pre-existing, unrelated characteristic, named but not created by this ADR:** Dispatch has never verified an Order exists or checked its status before accepting a Proposal for it (`OrderReference.kt`: *"Dispatch does not verify that this order exists"*) — so nothing before this ADR, and nothing in it, stops a *new* Proposal from being created against an already-`CANCELLED` order by a caller that does not check first. This is an existing, disclosed module-isolation characteristic (Dispatch trusts its caller, per ADR-002/034's own established precedent), not a gap this ADR introduces or is responsible for closing.

### Architectural Prerequisite: PostgreSQL Reconstruction Update

**Identified by consistency review, 2026-08-06, recorded here as a binding implementation prerequisite — not a deferred detail.** `PostgreSQLProposalRepository.reconstruct()` (`backend/dispatch/src/main/kotlin/com/pios/dispatch/persistence/PostgreSQLProposalRepository.kt:150`) restores a persisted Proposal through an exhaustive-looking `when (status)` block that replays `accept`/`decline`/`lapse` onto the freshly-constructed (always-`OPEN`) instance. This `when` is used as a **statement, not an expression**, so Kotlin does **not** enforce exhaustiveness on it — adding `WITHDRAWN` to `ProposalStatus` will **compile successfully** without a corresponding branch. Left unmodified, a `WITHDRAWN` row read from `pios_dispatch` would silently reconstruct as a `Proposal` object still reporting `OPEN`, with no exception and no warning — a data-correctness defect, not a build failure someone would be forced to notice.

This is the identical category of prerequisite `ADR-052` already recorded for `ProposalRepository.findOpen()`: this ADR does not specify the branch's exact code (that is implementation work, out of scope here), but it records, as a precondition this ADR's own Decision depends on, that `reconstruct()`'s `when (status)` block must gain a `ProposalStatus.WITHDRAWN -> proposal.withdraw(respondedAt)` branch (mirroring the existing `LAPSED`/`DECLINED` branches' own shape) before this ADR may be considered implemented, regardless of whether the compiler would have caught its absence.

## Alternatives Considered

| Alternative | Verdict | Reason |
|---|---|---|
| **Reuse `DECLINED`** | Rejected | Misattributes the act to the driver, contradicting `PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md` §3/§9's own ratified actor distinction. |
| **Reuse `LAPSED`** | Rejected | Misattributes the cause to elapsed time, conflicting with the meaning ADR-051/052 just spent two ADRs establishing precisely. |
| **New state `WITHDRAWN`, via a Dispatch-owned event consumer (selected)** | **Selected** | Only option that assigns the correct actor, invents no new business rule, and reuses an already-ratified integration pattern (ADR-041) rather than a new one. |
| **New state, but Order Management writes it directly into Dispatch's own table/aggregate** | Rejected | Violates ADR-005/009/019 exclusive ownership outright — Order Management has no ratified authority to mutate a Dispatch-owned aggregate under any circumstance. |
| **Synchronous REST call, Order Management → Dispatch, at cancel time** | Rejected | No synchronous cross-module dependency exists anywhere in this architecture; would couple Order Management's own cancellation to Dispatch's live availability, contradicting ADR-026's independent-deployability guarantee, and would invert the event-only integration convention ADR-019/041 already established. |
| **Leave the Proposal untouched; rely solely on the `LAPSED` timeout (ADR-051/052) to eventually resolve it** | Rejected | Does not guarantee "no Assignment is created" — a driver can still accept during the window before timeout; conflates a passenger-initiated fact with a pure timeout under one state, exactly what `PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md` §9 already forbids. |
| **A new cross-module Domain Service spanning Order Management and Dispatch** | Rejected | No such cross-module Domain Service category exists anywhere in this architecture — `DOMAIN_MODEL.md` §7's three Domain Services are each single-domain; inventing one contradicts "Do not redesign the domain." |
| **Process Manager / Saga** | Rejected | Mirrors ADR-036's own already-established reasoning for the structurally adjacent Proposal→Assignment case: no compensating action exists or is needed (the transition is one-directional), a single consumer reacting to a single event is not a multi-step long-running coordination problem — a Saga would be new architecture for a problem this flow does not have. |
| **Extend `Assignment` instead of `Proposal`** | Not applicable | Tier 1 is explicitly pre-acceptance; no `Assignment` instance exists yet at the point this ADR concerns. |

## Consequences

### Positive

- Closes the `[OPEN]` fork `SPRINT_PILOT_BLOCKERS.md` named for P0-2 Tier 1, using the identical, now-twice-proven pattern (ownership: exclusive per-aggregate; cross-module fact: event consumption, never a direct write or call) this repository already applied for `OPEN → LAPSED`.
- Eliminates the dangling-Proposal race entirely, by construction: once `ProposalWithdrawn` fires, `Proposal.accept()`'s own precondition (`status == OPEN`) rejects any later acceptance attempt.
- No change to Order Management's own already-committed `cancelOrder()` behavior or transaction boundary — the `OrderCancelled` outbox write already exists and already commits atomically with the Order's own write (ADR-032), unmodified.

### Negative / Follow-up required before implementation

- `EVENT_CATALOG.md` §6 (add `ProposalWithdrawn`) and §9 (`OrderCancelled` becomes cross-domain) require updating — Architect work, per ADR-006, blocking implementation exactly as the identical sync blocked `AssignmentCompleted`'s own implementation under ADR-041.
- `INTERFACE_CONTRACTS.md` §5 requires a new Order Management → Dispatch contract entry — the fifth cross-module relationship, where `ADR-034` Part 2 counted four.
- **`DOMAIN_MODEL.md` §4 (Proposal, Lifecycle) requires updating.** Its current text states, verbatim, that a Proposal *"resolves, exactly once, to one of **three** outcomes."* This ADR makes that sentence inaccurate the moment it is ratified — Proposal now resolves to one of **four**. Identified by consistency review, 2026-08-06; added to the mandatory documentation-synchronization list alongside `EVENT_CATALOG.md`/`INTERFACE_CONTRACTS.md` above, same blocking status, same authority (ADR-006, Architect work before implementation).
- The `PostgreSQLProposalRepository.reconstruct()` update named in "Architectural Prerequisite: PostgreSQL Reconstruction Update," above — implementation work, but binding, not optional.
- Frontend: both `DriverHome.tsx` and `RideRequest.tsx` need `'WITHDRAWN'` added to their own local status types to remain exhaustive — disclosed here, not designed here.
- The pre-existing "Dispatch never checks Order status" characteristic (Part 7) remains open and is not closed by this ADR.

## Evolution Path

1. **Documentation sync** — `EVENT_CATALOG.md`, `INTERFACE_CONTRACTS.md`, `DOMAIN_MODEL.md` — required before Stage 2, not optional.
2. **Implementation task** — the new queue/listener/application-layer method, the `Order.cancel()` REST endpoint itself, the `PostgreSQLProposalRepository.reconstruct()` update (Architectural Prerequisite, above), and the frontend status-union additions. Not this ADR's work.
3. **Tier 2** remains untouched and unauthorized by anything here — any future cancellation-after-acceptance capability requires its own, separate Product Decision and ADR, exactly as ADR-041 already concluded.

Any change to Part 1 (ownership), Part 2 (new state, not reused), or Part 4 (event shape) requires a decision that explicitly supersedes this one, consistent with ADR-015.

## Related ADRs

- [ADR-005](ADR-005-Data-Ownership.md) / [ADR-009](ADR-009-Domain-Isolation.md) / [ADR-019](ADR-019-Conceptual-Data-Ownership.md) — the ownership rules Part 1 applies without modification.
- [ADR-035: Pre-Commitment Business Fact — Aggregate Boundary](ADR-035-Pre-Commitment-Business-Fact-Aggregate-Boundary.md) — `Proposal`'s own exclusive-ownership root, unchanged.
- [ADR-036: Proposal↔Assignment Shared Transaction](ADR-036-Proposal-Assignment-Shared-Transaction.md) — not engaged: this ADR's own transition never touches `Assignment`, since none exists yet at this stage.
- [ADR-041: Order Lifecycle Synchronization with Assignment Completion](ADR-041-Order-Lifecycle-Synchronization-with-Assignment-Completion.md) — the direct precedent for the event-consumption shape this ADR reuses in the reverse direction, and the still-fully-intact exclusion of Tier 2.
- [ADR-051: Proposal Lifecycle Resolution Ownership (OPEN → LAPSED)](ADR-051-Proposal-Lifecycle-Resolution-Ownership.md) / [ADR-052: Proposal Lapse Resolution Mechanism](ADR-052-Proposal-Lapse-Resolution-Mechanism.md) — the sibling resolution this ADR is structurally isolated from (Part 7), reusing the identical ownership reasoning for a different trigger.

## References

- `docs/SPRINT_PILOT_BLOCKERS.md`, P0-2 Tier 1 (the original `[OPEN]` fork) and the Stage 2 "implementation attempt — blocked" Engineering Report this ADR answers.
- `docs/PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md`, §3, §9.
- `docs/PRODUCT_DECISION_ELECTRONIC_DISPATCHER_MVP_BLOCKERS.md`, PD-3.
- `docs/DOMAIN_MODEL.md`, §6, §11.
- `docs/EVENT_CATALOG.md`, §5, §6, §9.
- `backend/order-management/src/main/kotlin/com/pios/ordermanagement/application/OrderLifecycleApplicationService.kt` — read, not modified; confirms `OrderCancelled`'s outbox wiring already exists.
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/domain/OrderReference.kt` — read, not modified; the pre-existing "Dispatch does not verify order exists" precedent cited in Part 7.
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/persistence/PostgreSQLProposalRepository.kt` — read, not modified; the `reconstruct()` prerequisite cited in Part 7.

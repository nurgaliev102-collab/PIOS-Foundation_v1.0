# ADR-051: Proposal Lifecycle Resolution Ownership (OPEN → LAPSED)

## Status

**Accepted**

**Decision Date:** 2026-08-06

**Reason:** Ratified by Product Owner following a dedicated Stage 1 review that checked Part 2's layer assignment against Domain Service precedent and found no basis to change it (see the review note immediately below, retained as the record of that check). This ADR assigns architectural ownership only. It defines no mechanism, no timeout value, no infrastructure, no API, and no code, and authorizes no implementation — mechanism is separately addressed by ADR-052.

**Reviewed 2026-08-06 — Part 2 confirmed, unchanged.** A dedicated review checked Part 2's Application Layer assignment against this repository's own Domain Service definitions (`DOMAIN_MODEL.md` §7), orchestration precedent (`ProposalAssignmentOrchestrationService.kt`'s own KDoc, ADR-036), and anti-speculative-abstraction principles (`PROJECT_CONSTITUTION.md` §4, `ENGINEERING_GUIDELINES.md`). Finding: no existing principle requires a "Proposal Lifecycle Domain Service" — `DOMAIN_MODEL.md` §7's three named Domain Services each hold genuine cross-aggregate decision criteria, and PD-2 (`PRODUCT_DECISION_ELECTRONIC_DISPATCHER_MVP_BLOCKERS.md`) already, deliberately, removed every such criterion from the timeout question, leaving nothing for a service to hold beyond what `ProposalAssignmentOrchestrationService`'s own established precedent already assigns to the Application Layer ("does not decide anything... a coordination detail of the application layer, not an architectural decision requiring one"). Part 1 and Part 2 stand as originally decided.

## Context

`DOMAIN_MODEL.md` §4 (Proposal, Invariant) already, explicitly marks this exact gap: *"The precise mechanism by which a Proposal's outcome is recognized — in particular, what triggers a lapse — remains **Domain Decision Required**: Product Decision: Electronic Dispatcher MVP Blockers v1.0 ratifies only that a single, configurable, non-adaptive timeout parameter exists (Proposal Timeout Policy), never its value or triggering mechanism."* `DOMAIN_MODEL.md` line 198 further states that resolving anything it marks `Domain Decision Required` "requires a new ADR" — this document is that ADR.

`Proposal.kt`'s own KDoc (`Proposal.lapse()`) independently confirms the aggregate itself declines this responsibility: *"The mechanism by which lapsing is recognized... is not this aggregate's concern... this method only records the fact once it is already established elsewhere."* Nothing in the codebase or documentation set "elsewhere" has, until now, said where.

This gap was surfaced operationally by a full-day pilot-readiness walkthrough (`docs/PILOT_FULL_DAY_WALKTHROUGH.md`, Ride 3) and recorded as finding P0-3 in `docs/SPRINT_PILOT_BLOCKERS.md`: an `Order` whose `Proposal` is never answered by a driver has, under today's code, no reachable terminal state — not completable, not cancellable (a separate, already-tracked gap), and not lapsable, because nothing ever invokes `Proposal.lapse()`.

## Problem

Which bounded context, and which layer within it, holds architectural ownership of (a) detecting that an `OPEN` Proposal's waiting period has elapsed, and (b) invoking the `OPEN → LAPSED` transition once detected?

## Decision

### Part 1: Bounded-Context Ownership

**Dispatch owns this responsibility, exclusively.** `Proposal` is Dispatch's own aggregate root (ADR-035 Part 1, Part 5: "owned exclusively by Dispatch"). `DOMAIN_MODEL.md` §11's invariant — "Only the domain that owns a piece of information... may change it" — applies to the `OPEN → LAPSED` transition exactly as it applies to every other transition this aggregate already performs (`accept()`, `decline()`). No other module (Order Management, Driver Management, Passenger Experience, Notifications, Analytics) has any ratified contract to `Proposal` at all (`INTERFACE_CONTRACTS.md` §3's exhaustive list of justified cross-module relationships contains no such entry), and none may be assigned this responsibility without first creating one — which this ADR does not do, and which nothing ratified justifies.

### Part 2: Layering Within Dispatch

- **Detection** ("has the waiting period elapsed") belongs to Dispatch's own **application layer**, never to the `Proposal` aggregate itself. This mirrors ADR-034 Part 6's own precedent exactly: that ADR rejected placing Assignment Policy's selection criteria inside the `Assignment` aggregate for the identical structural reason — a coordination fact is not a business invariant, and `APPLICATION_ARCHITECTURE.md` §2 ("Domain Decides Business Meaning... the application layer never decides these; it only requests and observes") already draws this line generally.
- **Invocation** of `Proposal.lapse()` belongs to the same layer, through the same path `ProposalApplicationService` already uses for `accept()`/`decline()` — never directly from infrastructure, never from another module, never bypassing the aggregate's own public transition method.
- **Infrastructure** (however elapsed time is eventually detected in practice) holds no authority of its own and must call into this application-layer responsibility, never into the aggregate directly and never independently of it — the same relationship `ADR-046` Decision 1 already establishes generally: infrastructure "owns no capability, models no domain, holds no data and answers no business question."

### Part 3: What This ADR Does Not Decide

Consistent with its own stated purpose (ownership only):

- **No mechanism.** Whether detection is polling-based, event-driven, or evaluated at read-time is not decided here and is explicitly out of scope.
- **No timeout value.** Already, separately, governed by `PRODUCT_DECISION_ELECTRONIC_DISPATCHER_MVP_BLOCKERS.md` PD-2 (a single, configurable, non-adaptive parameter; value left to real pilot data).
- **No infrastructure, scheduler, or job.** Any such component, once designed, is an *implementation* of the application-layer responsibility this ADR assigns — never a competing owner of it.
- **No API or endpoint.** Not addressed by this ADR.
- **No relationship to P0-2 (cancellation).** A passenger-initiated withdrawal of an unresolved Proposal, if ever designed, is a distinct capability from the time-driven LAPSED transition this ADR concerns; this ADR neither creates nor forecloses it.

## Alternatives Considered

- **The `Proposal` aggregate detecting its own elapsed time.** Rejected: an aggregate cannot observe wall-clock time passing between invocations; the aggregate's own KDoc already, correctly, disclaims this.
- **Order Management or Driver Management.** Rejected: neither has any ratified contract exposing `Proposal` or its age (`INTERFACE_CONTRACTS.md` §3); both would require inventing a new cross-module relationship this ADR does not authorize.
- **A new "Electronic Dispatcher" bounded context.** Rejected: `ELECTRONIC_DISPATCHER_DOMAIN_DESIGN_V1.md` is explicitly unratified ("Proposed — Design Blueprint, not yet a Product Decision or ADR") and does not itself claim to be a bounded context distinct from Dispatch; deferring this ADR to it would tie an answerable question to an unrelated, unresolved product decision.
- **`platform-ops` or any infrastructure-level scheduler as owner.** Rejected categorically by `ADR-046` Decision 1, which excludes infrastructure from holding any domain capability, ownership, or business-question authority.
- **Human Coordinator as standing resolution mechanism.** Rejected: `PRODUCT_DECISION_ELECTRONIC_DISPATCHER_MVP_BLOCKERS.md` PD-1 explicitly states the Coordinator "НЕ является штатным fallback-механизмом" (is not a standing fallback mechanism); `Coordinator.tsx`'s own KDoc confirms no such capability exists today.
- **Driver or Passenger client-side inaction treated as an implicit transition.** Rejected: `PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md` §9 keeps Decline (active refusal) and Expiration (passive non-response) explicitly, deliberately distinct — collapsing them would contradict already-ratified vocabulary.
- **Notifications or Analytics domain services.** Rejected: `DOMAIN_MODEL.md` §7 defines both as explicitly non-owning by nature ("without owning the underlying information"); neither is ever described as authorized to mutate another domain's aggregate.

## Consequences

### Positive Consequences

- Closes the ownership gap `DOMAIN_MODEL.md` §4 has named, unresolved, since before P0-3 surfaced it operationally.
- Gives a future mechanism-design task (explicitly out of scope here) an unambiguous layer to build against, mirroring the same "boundary decided now, mechanism decided later" split ADR-034 already established for Assignment Policy.
- Introduces no new cross-module contract, no new aggregate, and changes no existing ownership boundary — it extends Dispatch's already-exclusive ownership of `Proposal` (ADR-035) to the one transition that was previously unassigned.

### Negative Consequences

- No implementation of automatic lapse-resolution may proceed on this ADR alone — a follow-up ADR (or an accepted extension of this one) must still define the mechanism, which this document deliberately does not do.
- Until a mechanism is separately designed and authorized, P0-3's underlying symptom (an abandoned Proposal with no reachable terminal state) remains unresolved in the running system — this ADR fixes who is responsible, not the running behavior.

## Evolution Path

1. **Mechanism design** — how elapsed time is actually detected and how the transition is actually invoked, within the application-layer boundary this ADR assigns. Not decided here; a distinct future task.
2. **Any relationship to a future Electronic Dispatcher capability**, should that unratified design ever be adopted — this ADR's ownership assignment does not need to change for that to happen, since Electronic Dispatcher's own design already treats `Proposal` as an unmodified, Dispatch-owned artifact it operates through.

Any change to Part 1 (bounded-context ownership) or Part 2 (layering) requires a decision that explicitly supersedes this one, consistent with ADR-015.

## Related ADRs

- [ADR-002: Dispatch Engine](ADR-002-Dispatch-Engine.md) — the standing exclusive-authority decision this ADR extends to the resolution transition.
- [ADR-005: Data Ownership](ADR-005-Data-Ownership.md) / [ADR-009: Domain Isolation](ADR-009-Domain-Isolation.md) / [ADR-019: Conceptual Data Ownership](ADR-019-Conceptual-Data-Ownership.md) — the ownership rules this ADR applies without modification.
- [ADR-034: Assignment Policy and Dispatch Decision Architecture](ADR-034-Assignment-Policy-and-Dispatch-Decision-Architecture.md) — the direct architectural precedent (Part 6) for keeping a coordination-adjacent decision out of the aggregate and inside the application layer.
- [ADR-035: Pre-Commitment Business Fact — Aggregate Boundary](ADR-035-Pre-Commitment-Business-Fact-Aggregate-Boundary.md) — establishes `Proposal` as Dispatch's exclusive aggregate; this ADR's Evolution Path item 2 anticipated, and explicitly deferred, exactly this question.
- [ADR-046: Platform Operations Component Boundary](ADR-046-Platform-Operations-Component-Boundary.md) — Decision 1, the basis for excluding any infrastructure-level component from domain ownership.

## References

- `docs/DOMAIN_MODEL.md`, §4 (Proposal), §7 (Domain Services), §11 (Business Invariants), line 198 (Domain Decision Required governance rule).
- `docs/PRODUCT_DECISION_ELECTRONIC_DISPATCHER_MVP_BLOCKERS.md`, PD-1, PD-2.
- `docs/PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md`, §9.
- `docs/APPLICATION_ARCHITECTURE.md`, §2–3.
- `docs/SPRINT_PILOT_BLOCKERS.md`, P0-3.
- `docs/PILOT_FULL_DAY_WALKTHROUGH.md`, Ride 3.
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/domain/Proposal.kt` — read, not modified.

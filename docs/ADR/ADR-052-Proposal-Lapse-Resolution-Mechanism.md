# ADR-052: Proposal Lapse Resolution Mechanism

## Status

**Accepted**

**Decision Date:** 2026-08-06

**Reason:** Ratified by Product Owner together with ADR-051, following a consistency review of both documents that found no circular dependency, no unresolved contradiction, and two drafting gaps corrected directly in this ADR (see "Terminology, fixed here" and "Architectural Prerequisite: Proposal Enumeration" below). Downstream of [ADR-051](ADR-051-Proposal-Lifecycle-Resolution-Ownership.md), which this ADR treats as the accepted ownership decision and does not reopen. This ADR authorizes an architectural mechanism *shape* only — no code, no configuration value, no file, no class name, and no implementation task is decided here.

## Context

ADR-051 assigned ownership of the `OPEN → LAPSED` transition to Dispatch's own application layer, but its own Part 3 explicitly withheld the remaining question: *"No mechanism. Whether detection is polling-based, event-driven, or evaluated at read-time is not decided here and is explicitly out of scope."* Its Negative Consequences state directly that *"a follow-up ADR... must still define the mechanism, which this document deliberately does not do."* A Stage 2 implementation attempt against ADR-051 alone was subsequently stopped for exactly this reason (`docs/SPRINT_PILOT_BLOCKERS.md`, P0-3, "Stage 2 implementation attempt — blocked"). This ADR is that follow-up.

`PRODUCT_DECISION_ELECTRONIC_DISPATCHER_MVP_BLOCKERS.md` PD-2 already, separately, ratifies that a timeout value exists as a single, configurable, non-adaptive operational parameter — its numeric value and its property name are not decided by this ADR either.

## Problem

Given that Dispatch's own application layer owns detecting elapsed time and invoking `Proposal.lapse()` (ADR-051, not reopened here), which architectural mechanism should perform the detection and trigger the invocation?

## Research — mechanisms already present in this repository

Every mechanism actually found by direct inspection, not assumed:

1. **Scheduled polling, module-local (`@Scheduled`).** Present today, identically, in all three producer modules: `backend/dispatch/.../application/OutboxRelayScheduler.kt`, and its Order Management and Driver Management equivalents. Each is a thin `@Component` in the module's own `application` package, holding one `@Scheduled(fixedDelayString = ...)` method that calls an already-existing, already-tested capability (`OutboxRelay.relay()`) — no new logic of its own. This exact mechanism was itself the subject of a dedicated planning document, `docs/IMPLEMENTATION_PLAN_OUTBOX_RELAY_PRODUCTION_TRIGGER.md`, which evaluated and selected it over named alternatives (see below) for a structurally adjacent problem: nothing was periodically triggering an already-correct, already-idle capability.
2. **Event-driven consumption (`@RabbitListener`).** Present in `AssignmentCompletedListener.kt`, `AssignmentAcceptedListener.kt` (Order Management), and `DriverAvailabilityChangedListener.kt` (Dispatch) — each reacts to a business fact that has already occurred, published by another aggregate's own transition. No precedent anywhere in this repository for a consumer reacting to the *absence* of an event within a window; the pattern only exists for "something happened," never "nothing happened by a deadline."
3. **Client-side (frontend) polling.** Present in `RideRequest.tsx` (`STATUS_POLL_INTERVAL_MS = 3000`) and `DriverHome.tsx` (`PROPOSALS_POLL_INTERVAL_MS = 3000`) — both read-driven UI refresh loops, entirely outside any bounded context's own backend, and entirely dependent on a browser tab being open. Structurally cannot be the invoking mechanism: it is not part of Dispatch, and the exact P0-3 scenario this ADR responds to (`docs/PILOT_FULL_DAY_WALKTHROUGH.md`, Ride 3) is a driver who never opens the app again — the one condition under which this mechanism, by construction, never runs.
4. **Read-time (on-query) evaluation.** No committed precedent exists anywhere in this codebase — confirmed by direct search of every backend module's `api`/`application` packages. It appears in this documentation set only as one of three *unadopted* options ADR-051 Part 3 itself named without selecting.
5. **A dedicated scheduling/workflow framework, or a broker-native delay mechanism (e.g., Quartz, a delayed-message exchange, per-message TTL + dead-letter routing used as a timer).** None present anywhere in this repository. The structurally closest precedent, `IMPLEMENTATION_PLAN_OUTBOX_RELAY_PRODUCTION_TRIGGER.md` Part 2, already evaluated and explicitly rejected Quartz, Kafka, and "a workflow engine" for the adjacent outbox-trigger problem: *"Explicitly excluded by this task's own instruction and unjustified by any existing authority... a disproportionate new infrastructure dependency... contradicting No Premature Optimization (ENGINEERING_GUIDELINES.md)."* No approved document anywhere authorizes RabbitMQ's delayed-message capabilities for any purpose; introducing one here would be new, unauthorized infrastructure, not a reuse of anything ratified.

## Comparison

| Candidate | Repository precedent | Reliability for an *abandoned* Proposal (nobody ever acts again) | Fit with ADR-051's layer assignment | Governance evidence |
|---|---|---|---|---|
| **Scheduled polling, module-local** | Strong — three working, already-planned, already-running instances of the identical shape, in Dispatch itself and its sibling modules. | Guaranteed — runs independent of any external actor reading, querying, or opening a client. | Direct fit — `OutboxRelayScheduler`'s own package (`com.pios.dispatch.application`) is already Dispatch's application layer, by this codebase's own existing convention. | `docs/IMPLEMENTATION_PLAN_OUTBOX_RELAY_PRODUCTION_TRIGGER.md` (full evaluation, already vetted for this exact class of problem); `ENGINEERING_GUIDELINES.md` No Premature Optimization (favors reusing a proven pattern over inventing one). |
| **Event-driven (`@RabbitListener`)** | Strong for reacting to events, but zero precedent for reacting to an event's absence. | None — nothing publishes an event when a Proposal simply sits unanswered; there is no fact for a listener to consume. | Would require inventing a new triggering event from nothing, which is not "detecting elapsed time," it is the same unsolved problem relocated. | No governance document names or anticipates an "absence" event; ADR-003/EVENT_CATALOG.md model only occurrences. |
| **Client-side (frontend) polling** | Strong as a UI-refresh pattern, but never used, anywhere, as a backend-authoritative trigger. | None in the exact failure mode this ADR exists for — an unopened client. | Wrong bounded context entirely — not Dispatch, not backend, not application layer. | N/A — disqualified by definition, not by weighing evidence. |
| **Read-time (on-query) evaluation** | None committed; named once, as an unselected option, in ADR-051 Part 3. | Weak — only fires if and when something queries the specific Proposal again; the abandoned-Proposal scenario is exactly the case where nothing ever does. | Plausible fit for the layer (could live in the application layer's own query path) but does not, alone, satisfy PD-2's own implied guarantee that waiting has a bound. | No repository evidence for or against beyond ADR-051's own unadopted mention; not disqualified on ownership grounds, only on reliability grounds for this specific failure mode. |
| **New scheduling/workflow framework or broker-native delay mechanism** | None. | Would be reliable in principle, but is unbuilt, unauthorized infrastructure. | N/A — no existing authorization to introduce it. | Already evaluated and rejected for the structurally adjacent problem (`IMPLEMENTATION_PLAN_OUTBOX_RELAY_PRODUCTION_TRIGGER.md` Part 2); nothing distinguishes this case enough to revisit that rejection. |

## Decision

**A scheduled, module-local, Dispatch-application-layer trigger — structurally mirroring the existing `OutboxRelayScheduler` pattern — is the recommended mechanism shape**, justified entirely by repository evidence: it is the only candidate with (a) working, already-accepted-in-effect precedent in this exact codebase, in this exact module, for this exact class of problem (a periodic check triggering an idle, already-correct domain capability), and (b) a reliability property (runs independent of any external actor) that the abandoned-Proposal scenario this ADR exists for specifically requires, and that every other candidate lacks.

```
Dispatch infrastructure
  (a periodic trigger, shaped like OutboxRelayScheduler --
   package, dependency footprint, and @Scheduled precedent only;
   no class name, property name, or interval value decided here)
        |
        v
Application layer
  (an already-existing or newly-coordinated application-layer
   capability, following ProposalApplicationService's own
   established pattern for invoking a Proposal transition)
        |
        v
Proposal.lapse()
  (unchanged; already exists, already correct, already enforces
   its own OPEN-only precondition)
```

Illustrative only, exactly as ADR-034 Part 3's own diagram is illustrative — no interface, class, or file is created by this ADR.

### Terminology, fixed here — mechanism vs. responsibility

Two separate axes are in play, and an earlier version of this ADR conflated them; this section is the single, final resolution, superseding anything below it that reads otherwise.

1. **Architectural responsibility** — per ADR-051 Part 2, *deciding* whether elapsed time warrants a check, and invoking `Proposal.lapse()` once it does, belongs to Dispatch's own application layer. This is the "what."
2. **Mechanism placement** — the periodic timer that wakes that decision up is **infrastructure**, exactly as ADR-051 Part 3 already classifies it: *"No infrastructure, scheduler, or job... any such component... is an implementation of the application-layer responsibility this ADR assigns — never a competing owner of it."* This is the "when."

`OutboxRelayScheduler`'s own KDoc already draws this identical line for itself: *"This class adds no new relay logic of its own; it only calls the already-existing, already-tested `OutboxRelay.relay()` periodically."* That its Kotlin file happens to be filed under the module's `application` **package** is a directory convention this codebase uses for where trigger classes are kept — it is not evidence that the timer mechanism itself performs application-layer decision-making. Package location and architectural layer are different axes; this ADR does not treat one as proof of the other. The Decision diagram above reflects this: the trigger is labeled "Dispatch infrastructure" because the *timing mechanism* is infrastructure, calling into an *application-layer responsibility* — consistent with, and not a redecision of, ADR-051 Part 2.

### What this ADR does not decide

- **No class name, file, or package layout.** Left to a future implementation task, as `IMPLEMENTATION_PLAN_OUTBOX_RELAY_PRODUCTION_TRIGGER.md` Part 6 did for its own analogous case — that level of detail is explicitly not this ADR's job.
- **No interval, property name, or default value.** Governed by PD-2 (`PRODUCT_DECISION_ELECTRONIC_DISPATCHER_MVP_BLOCKERS.md`) for the *value*; the property-naming convention (`pios.<module>.<concern>`) already exists in this codebase as a pattern to reuse, not a name to assert here.
- **No decision on read-time evaluation as a future, complementary, defensive measure.** Nothing in this ADR forbids later adding an on-read check as a secondary correctness aid; it is rejected here only as the *sole* or *primary* mechanism, for the reliability reason stated above — not disqualified outright.
- **No implementation task, test plan, or file-level change list.** Deliberately absent, unlike the Outbox Relay Trigger planning document, per this task's own explicit instruction.

## Alternatives Considered

- **Event-driven consumption.** Rejected — no fact exists for a listener to react to; would require inventing a new mechanism to manufacture the very event this ADR is trying to avoid begging the question of.
- **Client-side/frontend polling as the authoritative trigger.** Rejected — wrong bounded context, and unreliable in exactly the failure mode (an unopened client) this ADR exists to close.
- **Read-time evaluation as the sole mechanism.** Rejected as primary — reliable only conditional on a future read that, for an abandoned Proposal, may never come; retained as a non-decided, optional future complement.
- **A new scheduling/workflow framework or broker-native delay mechanism.** Rejected — no precedent, no authorization, and the structurally adjacent case already rejected the same category of solution on record (`IMPLEMENTATION_PLAN_OUTBOX_RELAY_PRODUCTION_TRIGGER.md` Part 2), on grounds (No Premature Optimization, disproportionate new infrastructure) that transfer without modification.

## Consequences

### Positive Consequences

- Reuses a mechanism shape already proven, three times over, in this exact codebase — no new dependency, no new infrastructure, no new architectural vocabulary.
- Satisfies the one reliability property every other candidate lacks: resolution does not depend on any external actor acting again.
- Stays entirely within ADR-051's own layer assignment — the *responsibility* (deciding to check, deciding to invoke) sits in Dispatch's application layer exactly as ADR-051 Part 2 requires; the periodic *timer* is infrastructure that only calls into that responsibility, per the Terminology section above.

### Negative Consequences, named and not resolved here

- **Idempotency of `Proposal.lapse()` under concurrent invocation is not addressed by this ADR.** `Proposal.lapse()` throws `IllegalStateException` if the Proposal is not `OPEN` — unlike `OutboxRelay`'s own `markPublished`, which the Outbox Relay Trigger plan itself documented as *"safe to execute more than once... the second execution is a harmless no-op write."* If more than one instance of Dispatch ever runs concurrently (a deployment model no approved document currently requires — ADR-026: independent scaling "does not require exercising it for every module on day one"), two ticks could race to lapse the same Proposal, and one would observe an exception where the Outbox precedent observed a no-op. This is named here, honestly, as a gap a future implementation task must handle (for example, by treating that specific exception as an already-resolved outcome rather than a failure) — not solved by this ADR, mirroring exactly how the Outbox Relay Trigger plan itself disclosed its own unresolved multi-instance gap rather than silently assuming it away.
- **`ProposalRepository` cannot support this mechanism as it stands today.** See "Architectural Prerequisite: Proposal Enumeration" below — recorded here, not silently left for an implementer to discover.
- No implementation may proceed until the interval value is separately authorized (PD-2's own scope, not decided by this ADR) and until the Proposal Enumeration prerequisite above is closed.

### Architectural Prerequisite: Proposal Enumeration

The mechanism this ADR selects cannot function without one capability `ProposalRepository` does not currently provide, confirmed by direct inspection of `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/ProposalRepository.kt`: it exposes exactly `save`, `findById`, `findByOrder`, `findByDriver` — none of which can answer "which Proposals are currently `OPEN`" without already knowing a specific order or driver in advance. A periodic sweep, as this ADR's Decision selects, has no way to enumerate its own targets without such a capability.

This is recorded as a **binding architectural prerequisite of this ADR's own Decision**, not as a deferred implementation nicety — the same category `ADR-034` Part 6 already established precedent for, disclosing the structurally identical gap in `DriverAvailabilityRepository`: *"today only supports looking up one already-known driver reference... not 'every currently available driver,' which any real policy would need. This ADR records that gap; it does not close it."* This ADR does the same: it does not specify the query's name, signature, or shape — that is implementation work, out of this ADR's scope — but it records, as a precondition the selected mechanism depends on, that `ProposalRepository`'s contract must be extended with an enumeration capability before this ADR's mechanism can be built. A future implementation task closes this; this ADR only ensures it is not discovered by surprise during Stage 2.

## Evolution Path

1. **Close the Proposal Enumeration prerequisite** — extend `ProposalRepository`'s contract with a capability to enumerate `OPEN` Proposals. A precondition of this ADR's own Decision (see above), not optional future work; ordinary implementation scope, not requiring a new ADR, exactly as ADR-034's own analogous `DriverAvailabilityRepository` gap did not require one.
2. **Implementation task** — class/file naming, configuration property naming, and the exact application-layer call sequence. Not this ADR's work; a distinct future task, mirroring how `IMPLEMENTATION_PLAN_OUTBOX_RELAY_PRODUCTION_TRIGGER.md` handled the identical step for its own mechanism.
3. **Concurrent-invocation handling for `Proposal.lapse()`**, named as an open gap above — a future, narrow decision, not requiring a new ADR unless it changes ownership or mechanism shape.
4. **Whether read-time evaluation is ever added as a defensive complement** — left genuinely open, not decided either way here.

Any change to the mechanism shape decided here (the scheduled, module-local, application-layer trigger) requires a decision that explicitly supersedes this one, consistent with ADR-015.

## Related ADRs

- [ADR-051: Proposal Lifecycle Resolution Ownership (OPEN → LAPSED)](ADR-051-Proposal-Lifecycle-Resolution-Ownership.md) — the ownership decision this ADR is strictly downstream of and does not modify.
- [ADR-002](ADR-002-Dispatch-Engine.md), [ADR-005](ADR-005-Data-Ownership.md), [ADR-009](ADR-009-Domain-Isolation.md), [ADR-019](ADR-019-Conceptual-Data-Ownership.md) — unaffected; this ADR introduces no new ownership.
- [ADR-026: Deployment Strategy](ADR-026-Deployment-Strategy.md) — the independent-deployability/single-instance-per-module assumption this ADR's disclosed concurrency gap relies on, unchanged.
- [ADR-031](ADR-031-Event-Infrastructure-Foundation-Architecture.md) / [ADR-032](ADR-032-Reliable-Event-Publication-Strategy.md) — the reliability/idempotency discipline `OutboxRelayScheduler` itself already operates under, cited here only as precedent, not modified.
- [ADR-034: Assignment Policy and Dispatch Decision Architecture](ADR-034-Assignment-Policy-and-Dispatch-Decision-Architecture.md) — the direct precedent for authorizing a boundary/shape without deciding code (Part 3's own illustrative diagram convention, reused here).

## References

- `docs/IMPLEMENTATION_PLAN_OUTBOX_RELAY_PRODUCTION_TRIGGER.md` — the governing precedent for the selected mechanism shape, including its own rejection of Quartz/Kafka/workflow engines.
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/OutboxRelayScheduler.kt` — read, not modified; the structural analogy this ADR's Decision is grounded in.
- `backend/order-management/src/main/kotlin/com/pios/ordermanagement/persistence/{AssignmentCompletedListener,AssignmentAcceptedListener}.kt`, `backend/dispatch/src/main/kotlin/com/pios/dispatch/persistence/DriverAvailabilityChangedListener.kt` — read, not modified; the event-driven candidate's own precedent and limits.
- `frontend/src/pages/RideRequest/RideRequest.tsx`, `frontend/src/pages/DriverHome/DriverHome.tsx` — read, not modified; the client-side-polling candidate's own precedent and limits.
- `docs/SPRINT_PILOT_BLOCKERS.md`, P0-3 — the operational finding and blocked Stage 2 attempt this ADR resolves the way forward for.
- `docs/PRODUCT_DECISION_ELECTRONIC_DISPATCHER_MVP_BLOCKERS.md`, PD-2 — governs the timeout value, not decided here.

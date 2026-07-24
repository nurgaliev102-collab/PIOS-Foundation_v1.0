# ADR-036: Proposal↔Assignment Shared Transaction

## Status

Accepted

**Decision Date:** 2026-07-24

**Reason:** The proposed architectural decision has successfully addressed all review comments. The document now clearly distinguishes the DDD exception from the general rule, explicitly documents operational consequences, confirms compatibility with future product evolution, and justifies why this decision is intentionally scoped as a dedicated ADR rather than deferred to the future Database Design document. The architecture is approved for implementation.

## Problem

Does the Dispatch module's own persistence architecture permit a single database transaction to span the `Proposal` and `Assignment` aggregates' writes, for the specific purpose of making the Accept Proposal operation atomic?

This ADR answers that one question only. It does not select an implementation, does not modify code, and does not modify `PERSISTENCE_ARCHITECTURE.md` itself — it is the missing authorization that document's own Section 7 and Section 8 require before such a mechanism may be built.

## Context

`ProposalAssignmentOrchestrationService.acceptProposal` sequences two steps: `ProposalApplicationService.acceptProposal` (marks the Proposal `ACCEPTED`, its own transaction) and `DispatchAssignmentApplicationService.handle` (creates the Assignment plus its outbox record, a separate transaction). Both steps are already individually atomic and reliable — the transactional outbox pattern (ADR-032) makes the Assignment write and its own outbox record atomic with each other. What is not atomic is the pair of steps *together*: if the process fails between them, a `Proposal` can be left `ACCEPTED` with no corresponding `Assignment`. `Proposal` has no supported transition back from `ACCEPTED` (Domain Design — Pre-Commitment Aggregate: transitions are one-directional), so this failure window is not self-correcting.

This was identified, reproduced against real PostgreSQL, and disclosed as known technical debt during prior implementation work (`ProposalAssignmentOrchestrationService`'s own KDoc). A subsequent architecture review evaluated the closing options at a conceptual level; this ADR is the formal decision that review's own findings pointed to as still missing before implementation could proceed.

`PERSISTENCE_ARCHITECTURE.md` Section 7 states persistence responsibility, boundaries, and consistency principles "change only through a new ADR that explicitly supersedes the decision that established them." Section 8 lists "any specific consistency mechanism beyond the conceptual philosophy in Section 6" as "Intentionally undecided until Database Design." A transaction spanning two Aggregate Roots is exactly such a mechanism. No document currently authorizes it, and none forbids it — the silence itself is what blocks implementation under `PROJECT_CONSTITUTION.md` Section 7 ("An architectural change made without a corresponding ADR is considered undocumented and is not valid, regardless of whether it has been implemented").

**Why an ADR, and not the future `DATABASE_DESIGN.md`, resolves this.** Section 8's "Database Design" reference is to the *general* consistency-mechanism framework across every domain in PIOS — a broader, later exercise this ADR does not attempt and does not pre-empt. ADR-036 does not define a general transaction-management strategy for the system. It authorizes a single, narrow case within the Dispatch Bounded Context. The general database-design policy remains the subject of the future `DATABASE_DESIGN.md`; this ADR settles nothing beyond the one Proposal↔Assignment case it names.

## Constraints

- `Proposal` and `Assignment` remain two separate Aggregate Roots per ADR-035 — this ADR may not merge them, nest one inside the other, or change either aggregate's own invariant, state machine, or persistence responsibility.
- No new domain concept, entity, or persisted state may be introduced (Constitution §4, No Shortcuts / Single Source of Truth; ENGINEERING_GUIDELINES.md No Premature Optimization).
- Both aggregates remain owned exclusively by Dispatch and persisted in Dispatch's own database (`pios_dispatch`) — this ADR does not cross a module boundary, so ADR-005/ADR-009's cross-*domain* ownership rules are not implicated; only the intra-domain, cross-*aggregate* question is in scope.
- No implementation, migration, or code change is authorized by this ADR itself.

## Considered Options

### Option A — Single shared database transaction (recommended)

`ProposalAssignmentOrchestrationService` becomes the Unit of Work owner: it opens one transaction wrapping the Proposal read, the Proposal write, the Assignment read, the Assignment write, and the Assignment's own outbox write. Both aggregates' own repository `save()` calls participate in the same physical transaction; either all of it commits or none of it does.

### Option B — Saga / Process Manager

A new, persisted coordinator tracks the Proposal→Assignment transition across its own recorded state, with retry and compensation logic, independent of any single database transaction.

### Option C — Domain Event orchestration

`Proposal.accept()`'s own `ProposalAccepted` event is wired through the outbox/relay pattern already used for `OrderAssigned`/`AssignmentAccepted`; an event handler reacts to it by creating the Assignment, decoupling the two writes via eventual consistency instead of atomicity.

## Decision

**Option A is selected: a single shared database transaction, owned by `ProposalAssignmentOrchestrationService`, spanning the Proposal write and the Assignment write.**

This ADR authorizes the *mechanism*, not its code. It permits a future implementation task to make `ProposalAssignmentOrchestrationService` a `TransactionRunner` owner and to restructure `ProposalApplicationService`/`DispatchAssignmentApplicationService`'s relevant methods so they no longer independently open a transaction for this specific flow, consistent with the design already produced by prior architecture-review work.

## Why This Does Not Violate Aggregate Independence

Aggregate independence, as ADR-035 Part 2 establishes it, is about **invariant consistency boundaries** — which invariants must be checked together, synchronously, within one aggregate's own boundary. `Proposal`'s Root Invariant (at most one `OPEN` Proposal per order) and `Assignment`'s invariant (at most one active Assignment per order) remain independently enforced, each within its own aggregate's own `check()`/`require()` logic, exactly as today. A shared database transaction is an **infrastructure-level Unit of Work**, not a shared consistency boundary at the domain-modeling level: neither aggregate's invariant is evaluated against the other's state, and neither aggregate gains any awareness of the other's existence. The transaction only changes *when the durable commit happens*, not *what either aggregate is allowed to know or enforce*.

## Why This Does Not Contradict ADR-035

ADR-035 Part 5 ("Ownership Boundaries Unchanged") states explicitly that the aggregate-boundary decision changes no ownership already ratified, introduces no new cross-module contract, and changes no other module's boundary. This ADR changes none of those either — both aggregates stay exactly where ADR-035 placed them, each with its own repository, each still reachable and usable independently of the other (a Proposal can still be created, declined, or lapsed without ever touching Assignment; an Assignment can still be created directly via the deprecated manual path without ever touching Proposal). ADR-035 Part 4 itself explicitly named the *execution/orchestration mechanism* for the Proposal→Assignment sequence as "a distinct architectural question, separate from, not replaced by, this ADR's own aggregate-boundary decision" — this ADR is the answer to exactly that named, deferred question, not a revision of ADR-035's own Parts 1–3.

## Why This Is Consistent with DDD

**This is a deliberate exception, not a default rule.** Standard DDD guidance (Evans; Vernon's "effective aggregate design") treats "one transaction per aggregate" as the default — the presumption a designer should start from and depart from only for a specific, examined reason, not a routine or equally-weighted alternative. The domain model of PIOS deliberately uses an exception a number of DDD authors permit, for a synchronous atomic operation within one Bounded Context; this decision was made after considering the alternatives and must not be read as establishing a default. The narrow conditions this exception requires: (a) the aggregates are owned by the same bounded context, (b) the operation is a single, synchronous, user-initiated business transaction with no natural place for eventual consistency, and (c) introducing eventual consistency would itself create a new, harder-to-explain product behavior. All three hold here: Dispatch owns both aggregates; Accept Proposal is already implemented as one synchronous REST call with one synchronous response; and PIOS's own Transparency/No Hidden Algorithmic Decisions principles (Constitution §3) make an eventually-consistent "your proposal was accepted, but the assignment might not exist yet" response a worse fit for this specific product than for a generic distributed system. Any future cross-aggregate case must independently satisfy these same three conditions — it does not inherit an exemption merely because this ADR exists (see Consequences, Negative, below).

## Why Not Saga / Process Manager (Option B)

Rejected for the same reason Sprint IMPLEMENTATION-004 originally rejected a Process Manager for this exact sequencing problem: both aggregates are owned by the same module, in the same process, the transition is a single immediate step, and there is no waiting, no external system crossing a process boundary, and — critically — no compensating action for a Saga to eventually invoke, since `Proposal` has no supported reverse transition from `ACCEPTED`. Introducing a Saga's own persisted state here would be new architecture built to solve a problem (long-running, multi-service coordination) this flow does not have, at the cost of a second persisted concept with its own failure modes. A Saga becomes the right answer only if a future, separately-ratified domain decision introduces a real compensating action for Proposal — nothing today does.

## Why Not Domain Event Orchestration (Option C)

Rejected for two independent reasons already on record. First, `ProposalApplicationService`'s own KDoc discloses that wiring any Proposal event to the outbox today would create a real, externally-consumable published event the moment the service runs in a wired application — but `EVENT_CATALOG.md`/`INTERFACE_CONTRACTS.md` name no consumer for any Proposal event (confirmed directly: `INTERFACE_CONTRACTS.md` Sections 5 and 8 still ratify only `OrderAssigned`/`AssignmentAccepted` as Dispatch-outbound contracts). Adopting Option C would silently create a new external contract nobody has ratified, which is exactly the kind of architectural change this project's own discipline requires stopping on rather than inventing. Second, even an *internal-only*, non-outbox event handler was evaluated for this same orchestration problem during Sprint IMPLEMENTATION-004 and rejected as premature indirection for a single, mandatory, synchronous reaction with exactly one subscriber — nothing about the problem has changed since. Option C also, by its own nature, trades atomicity for eventual consistency, which reintroduces exactly the "Proposal accepted, Assignment not yet created" observable state this ADR exists to eliminate, rather than closing it.

## Operational Consequences

Sharing one transaction across two aggregates' writes creates real operational constraints that did not exist while each write had its own, independent transaction — named explicitly here so a future change to either side does not violate them unknowingly:

- **No network or external I/O inside the shared transaction.** Neither the Proposal step nor the Assignment step may perform a call to another service, another module, or any resource outside Dispatch's own `pios_dispatch` database while the shared transaction is open. Today neither does; this ADR makes that a binding constraint on any future change to either method, not merely an incidental current fact.
- **No long-held locks.** The shared transaction must remain as short as the current two separate transactions are individually — a future addition to either step that introduces a slow operation extends contention on *both* the `proposals` and `assignments`/`dispatch_outbox` tables, not just its own.
- **Any future external call related to either step must happen after commit**, using the already-established outbox/relay pattern (ADR-032) for anything that must reach another system reliably, never inline inside this transaction.
- **A failure on the Assignment side rolls back the Proposal side too.** This is the intended effect (it is what "atomic" means here), but it is also a genuine behavioral consequence worth stating plainly: a transient infrastructure failure while creating the Assignment (not only a business-rule rejection) will now also revert the Proposal's `ACCEPTED` write, where previously it would not have.

## Future Compatibility

**Electronic Dispatcher.** This ADR does not change the Accept Proposal mechanism an eventual Electronic Dispatcher would use. What changes under automatic dispatch is only *how and when a Proposal is created* (the candidate-selection step, upstream of everything this ADR concerns); the already-ratified Concurrent Proposal Policy (Product Decision: Electronic Dispatcher MVP Blockers v1.0 — strictly sequential, one `OPEN` Proposal per order) keeps the accept-time shape this ADR wraps identical under automatic dispatch to what it is today under manual dispatch.

**Fair Opportunity Policy.** A future Fair Opportunity Policy affects exclusively which candidate driver is selected before a Proposal is created — it has no bearing on, and is not affected by, the atomicity of Accept Proposal this ADR provides.

## Consequences

### Positive

- Eliminates the disclosed "Proposal `ACCEPTED` with no Assignment" failure window entirely, by construction, for the orchestrated accept path.
- No new domain concept, no new persisted state, no new external contract.
- Both aggregates' own code, invariants, and independent usability (via the deprecated manual Assignment path, or direct Proposal decline/lapse) remain completely unaffected.
- Closes the silence in `PERSISTENCE_ARCHITECTURE.md` Section 8 for this one specific, narrow case, without attempting to answer the general question of cross-aggregate transactions everywhere.

### Negative

- Widens the transaction's duration and lock footprint across two aggregates' worth of work for the Accept Proposal operation specifically — a real, if modest, cost under concurrent load.
- Establishes a precedent that a future, different cross-aggregate case might cite by analogy without necessarily sharing this case's own justifying properties (same bounded context, single synchronous operation, no compensating action available) — any future case must be evaluated on its own facts, not assumed to inherit this decision automatically.
- Does not address concurrent-invocation races absent a lock or database constraint (a separate, previously identified concern) — this ADR closes the cross-aggregate *atomicity* gap only, not every remaining concurrency question.

## Migration Impact

None to data. No schema change, no migration script, no change to either aggregate's persisted shape. The impact is confined to `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/{ProposalAssignmentOrchestrationService,ProposalApplicationService,DispatchAssignmentApplicationService}.kt` in a future implementation task, and to `PERSISTENCE_ARCHITECTURE.md`, which should be extended (not superseded) to note that intra-domain, cross-aggregate transactions are permitted on a case-by-case basis under the justification this ADR records, once this ADR itself reaches Accepted status.

## Risks

- **Scope risk.** A future implementation could be tempted to generalize this into "cross-aggregate transactions are now always fine" — this ADR authorizes exactly the Proposal↔Assignment case within Dispatch, argued on its own specific facts, not a general license.
- **Silent broadening risk.** Because `PERSISTENCE_ARCHITECTURE.md` itself is not modified by this ADR, a future reader of that document alone would not discover this exception exists — the recommended follow-up (a small, explicit extension to `PERSISTENCE_ARCHITECTURE.md` Section 6/8 once this ADR is Accepted) mitigates this, but until that follow-up happens, this ADR is the only record of the exception.
- **Non-resolution risk.** This ADR does not close the concurrent-invocation TOCTOU risk on the orchestrator's own initial read (identified separately, during Sprint 1's own verification work) — implementing this ADR is necessary but not sufficient to fully close that class of defect; a future implementation should confirm the read is restructured to occur inside the new shared transaction, not merely that a shared transaction now exists somewhere in the method.

## Related ADRs

- [ADR-002: Dispatch Engine](ADR-002-Dispatch-Engine.md) — unmodified; this ADR does not touch assignment-decision ownership.
- [ADR-032: Reliable Event Publication Strategy](ADR-032-Reliable-Event-Publication-Strategy.md) — the transactional-outbox pattern this ADR extends by one more write (the Assignment's own outbox record) participating in the same, now-larger transaction; ADR-032's own guarantee (aggregate write and outbox write are atomic) is preserved, not weakened.
- [ADR-035: Pre-Commitment Business Fact — Aggregate Boundary](ADR-035-Pre-Commitment-Business-Fact-Aggregate-Boundary.md) — Part 4 explicitly named the orchestration mechanism this ADR answers; Part 5's ownership guarantees are unaffected.

## References

- [PROJECT_CONSTITUTION.md](../PROJECT_CONSTITUTION.md) Section 6 (Architecture Governance), Section 7 (ADR Policy)
- [PERSISTENCE_ARCHITECTURE.md](../PERSISTENCE_ARCHITECTURE.md) Sections 6–8
- [project-brain/PIOS_MASTER_CONTEXT.md](../../project-brain/PIOS_MASTER_CONTEXT.md) §5.4
- [IMPLEMENTATION_STRATEGY.md](../IMPLEMENTATION_STRATEGY.md) §3, Phase 0
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/ProposalAssignmentOrchestrationService.kt` — read, not modified.

# ADR-035: Pre-Commitment Business Fact — Aggregate Boundary

## Status

Proposed

## Context

Product Decision: Opportunity Before Assignment v1.0 ratifies that a self-contained business fact exists between Dispatch's own selection of a candidate driver and that driver's own confirmation, distinct from Assignment — without deciding its architectural form. That Product Decision builds on `PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md`, which already found, examining `Assignment.create()` directly, that `Assignment`'s `CREATED` state "does not... represent a true, multi-candidate Opportunity" and that "Assignment creation should conceptually follow [this fact's] resolution... not precede or substitute for it" (Section 8), and which left "whether an Opportunity concept is ever formally designed as a domain aggregate/event" explicitly deferred (Unresolved Decisions, item 1).

ADR-034 already named the still-missing Assignment Policy seam and its input/output boundary, but its own Part 3 diagram and surrounding reasoning implicitly assumed Assignment Policy's chosen driver reference flows directly into `Assignment.create()` — consistent with the current, unmodified implementation, where `DispatchAssignmentApplicationService.handle()` does exactly that. The Product Decision this ADR is downstream of makes that assumption no longer safe to carry forward silently: a business fact now exists, architecturally, between the two.

This ADR answers only how that already-ratified business fact is modeled architecturally — not its name, states, invariants, events, commands, or storage.

## Problem

Given that a self-contained business fact exists between Dispatch's own candidate selection and a driver's own confirmation, what architectural construct should represent it: extending the existing `Assignment` aggregate, a child Entity within it, a Domain Event alone, a Process Manager, or a separate Aggregate Root?

## Decision

### Part 1: Architectural Form

The pre-commitment business fact is modeled as a **separate Aggregate Root**, owned exclusively by Dispatch, distinct from and referencing `Assignment` only by identity — never nested inside it, never represented solely as an extension of `AssignmentStatus`, and never represented solely as a free-standing event with no owning aggregate.

### Part 2: Aggregate Boundary Justification

Each of the following, independently, supports a separate root over an Entity nested inside `Assignment`:

- **Invariant independence.** The invariant this new fact will require ("no more than one open pre-commitment attempt per order at a time" — exact wording not decided here) shares no transactional consistency requirement with `Assignment`'s own already-ratified invariant ("an order cannot be connected to more than one active assignment at a time," DOMAIN_MODEL.md Section 11). Two invariants that never need to be checked together do not belong inside one consistency boundary.
- **Lifecycle precondition.** `Assignment`'s own ratified Purpose is "the outcome of Dispatch allocating a specific order to a specific driver" (DOMAIN_MODEL.md Section 4) — an already-settled result, not a pending one. Nesting the pre-commitment fact inside `Assignment` would require an `Assignment` row to exist before that outcome is true, contradicting its own definition. A separate root lets `Assignment.create()` — unmodified — fire only once the fact has resolved positively.
- **Persistence granularity.** `PERSISTENCE_ARCHITECTURE.md`'s own Domain-Owned Persistence principle is already applied identically to `Order`, `Driver`, and `Assignment`: one repository per aggregate root, addressed by identity. The pre-commitment fact needs its own, independently queryable history (for example, for future Fair Dispatch auditing) — a need only a repository at the root level, not an internal entity, can satisfy under this project's own already-established convention.
- **Actor separation.** `Assignment` is changed exclusively by Dispatch's own decision (ADR-002); the pre-commitment fact's own resolution is driven by the driver's own confirming act (PRODUCT_FOUNDATION.md Section 12, self-determined participation). Nesting these inside one aggregate would route a change initiated by a different actor through the same consistency boundary as Dispatch's own exclusive decision.
- **Cardinality mismatch.** A single order may, over time, generate more than one pre-commitment attempt (one declined or expired, replaced by another), while `Assignment`, by its own ratified definition, represents at most one outcome per order at a time. A child Entity cannot cleanly represent an independently growing collection whose count bears no relationship to its parent's own singular nature.
- **Existing precedent within the same module.** `Assignment` already answers an analogous question for `Order` and `Driver` by reference (`OrderReference`, `DriverReference`), never by nesting, precisely because those are independently consistent concepts owned elsewhere. The pre-commitment fact stands in the same relationship to `Assignment`, one module closer.

### Part 3: What Remains Undecided

Not decided by this ADR, consistent with the Product Decision it implements:

- The fact's name (this ADR does not call it "Opportunity" or anything else).
- Its identity, states, invariants (beyond naming that at least one will be required, per Part 2), commands, events, or repository contract.
- Decline, Expiration, and post-commitment Failure mechanisms (`PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md` Section 9 remains the authority — still `[OPEN]`).
- Multi-candidate ("many offered, one accepts") semantics — a future extension this boundary does not foreclose (Part 2, cardinality) but does not design.
- Whether `Assignment`'s own already-implemented `CREATED → ACCEPTED` transition still occurs after this fact resolves positively, runs in parallel, or is affected in any way — genuinely open, left to the future domain-design task this ADR's own Evolution Path names.

### Part 4: Relationship to Dispatch Engine / Assignment Policy

The conceptual sequence this ADR establishes, at the architecture level only, with no execution mechanism decided:

```
Policy decision → pre-commitment aggregate → driver resolution → (if positive) Assignment
```

Assignment Policy (ADR-034 Part 3) continues to decide *which driver is proposed*; its output now targets the pre-commitment aggregate, not `Assignment.create()` directly. No Process Manager, orchestration mechanism, or execution detail is designed here — this is a distinct architectural question, separate from, not replaced by, this ADR's own aggregate-boundary decision.

### Part 5: Ownership Boundaries Unchanged

This ADR changes no ownership boundary already ratified by ADR-002, ADR-005, ADR-009, or ADR-019. The new aggregate is owned exclusively by Dispatch, exactly as `Assignment` already is — no new cross-module contract, no change to `INTERFACE_CONTRACTS.md` Section 3's four already-justified relationships, no change to any other module's boundary.

## Alternatives Considered

- **Extend `Assignment`'s own state machine** (new `AssignmentStatus` values before/after `CREATED`). Rejected: requires revising `Assignment`'s own already-implemented, already-tested invariant text ("every Assignment that exists, regardless of status, is treated as active"), and stretches its own ratified Purpose past a settled outcome. See Part 2.
- **Entity nested inside `Assignment`.** Rejected per the Aggregate Boundary Justification, Part 2, in full.
- **Domain Event only, with no owning aggregate.** Rejected: an event with no owning aggregate cannot enforce any invariant this fact will require (`EVENT_CATALOG.md`'s own convention already ties every event to a `Related Aggregate`); necessary as a mechanism regardless of which aggregate is chosen, but not itself an answer to this ADR's own question.
- **Process Manager / Saga in place of a domain aggregate.** Rejected as a substitute (though not as a complementary, separate concern, Part 4): orchestrating a sequence of decisions is a different architectural question from where the business fact itself is recorded, and a Process Manager alone cannot, by itself, enforce a business invariant.

## Consequences

### Positive Consequences

- Preserves `Assignment`'s own already-implemented code, tests, invariant, and ratified Purpose entirely unchanged.
- Gives the pre-commitment fact its own consistency and query boundary, positioning future Fair Dispatch auditing and Decline/Expiration semantics (`PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md` Section 9) to be resolved without touching `Assignment` again.
- Introduces no new cross-module dependency; ownership stays exactly where ADR-002/005/009/019 already place it.

### Negative Consequences

- A second persisted aggregate within Dispatch, with its own future repository and invariant, is more design and implementation work than extending `Assignment` alone would have been.
- No implementation may proceed until a future, dedicated domain-design task resolves this aggregate's name, identity, states, invariants, commands, events, and repository contract — this ADR authorizes none of them.

## Evolution Path

Anticipated follow-up work, none resolved here:

1. **Domain design of the new aggregate** — name, identity, invariants, lifecycle, commands, events — a dedicated future task, per this ADR's own Part 3.
2. **Decline, Expiration, and post-commitment Failure mechanisms** — remain `PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md`'s own open items (Section 9, Unresolved Decisions #2), now positioned to attach to the new aggregate rather than to `Assignment`.
3. **Execution/orchestration mechanism** for the Part 4 sequence — a distinct architectural question, not decided here.

Any change to the aggregate-boundary decision itself (Part 1) requires a decision that explicitly supersedes this one, consistent with ADR-015.

## Related ADRs

- [ADR-002: Dispatch Engine](ADR-002-Dispatch-Engine.md) — unmodified; this ADR does not redecide assignment-decision ownership.
- [ADR-005: Data Ownership](ADR-005-Data-Ownership.md) / [ADR-009: Domain Isolation](ADR-009-Domain-Isolation.md) / [ADR-019: Conceptual Data Ownership](ADR-019-Conceptual-Data-Ownership.md) — unmodified; the new aggregate's ownership follows the same rules already governing `Assignment`.
- [ADR-015: Evolution Strategy](ADR-015-Evolution-Strategy.md) — governs this ADR's own future evolution, and the mechanism by which it clarifies ADR-034 (see ADR-034's own "Update" note).
- [ADR-034: Assignment Policy and Dispatch Decision Architecture](ADR-034-Assignment-Policy-and-Dispatch-Decision-Architecture.md) — Part 2's "Dispatch's own existing Assignment state" input and Part 3's diagram are clarified, not redecided in substance, by this ADR: Assignment Policy's output now targets the pre-commitment aggregate this ADR names, not `Assignment.create()` directly. Every other part of ADR-034 (Part 1 ownership, Part 4 future strategies, Part 5 product constraints, Part 6, Part 7) remains fully in force, unmodified.

## References

- [PROJECT_CONSTITUTION.md](../PROJECT_CONSTITUTION.md), Section 3 (Fair Dispatch), Section 5 (Documentation Hierarchy)
- [DOMAIN_MODEL.md](../DOMAIN_MODEL.md), Section 4 (Assignment), Section 11 (Business Invariants)
- [PRODUCT_DECISION_OPPORTUNITY_BEFORE_ASSIGNMENT.md](../PRODUCT_DECISION_OPPORTUNITY_BEFORE_ASSIGNMENT.md) — the Product Owner authority this ADR implements.
- [PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md](../PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md), Sections 8–10, Unresolved Decisions #1.
- [PERSISTENCE_ARCHITECTURE.md](../PERSISTENCE_ARCHITECTURE.md), Section 2 (Domain-Owned Persistence).
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/domain/Assignment.kt`, `application/AssignOrderCommand.kt`, `application/DispatchAssignmentApplicationService.kt` — read, not modified.

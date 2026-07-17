# ADR-002: Dispatch Engine

## Status

Proposed

## Context

PIOS Constitution Section 3 names Fair Dispatch as a product principle: the mechanism connecting riders and drivers must be designed and evaluated for fairness. Before that mechanism can be designed in detail, an architectural decision is needed on what kind of capability Dispatch is and who owns the decisions it makes, independent of how those decisions are actually computed.

## Problem

Where does the responsibility for making an assignment decision between a rider and a driver reside architecturally, and what is that responsibility's boundary?

## Decision

Dispatch exists as an independent architectural capability within PIOS. The decision of whether and how a rider request is assigned to a driver — the assignment decision — belongs exclusively to Dispatch. Dispatch owns assignment logic as part of its boundary; no other capability makes or overrides an assignment decision. The specific algorithm, fairness criteria, and business rules used to compute an assignment are not architectural concerns and are explicitly not defined by this ADR.

## Alternatives Considered

- **Distributing assignment logic across the capabilities that request or fulfill rides.** Rejected: it would scatter a single decision across multiple capabilities, making the fairness commitment in Constitution Section 3 impossible to evaluate or evolve as one coherent thing.
- **Treating assignment as a shared, cross-cutting concern owned by no single capability.** Rejected: an unowned decision cannot be held accountable to the Fair Dispatch principle, and it violates the ownership discipline consistent with ADR-001's Traceability requirement.

## Consequences

### Positive Consequences

- Establishes a single, accountable owner for one of the platform's most consequential decisions.
- Allows the fairness criteria behind assignment to be evaluated, audited, and evolved as a coherent whole.
- Gives later architectural decisions a clear capability to reference when describing how other parts of the system relate to assignment.

### Negative Consequences

- Any capability that needs an assignment decision must depend on Dispatch rather than implementing its own shortcut logic, which may feel restrictive during early implementation.
- Concentrating this responsibility in one capability makes Dispatch a critical point that later architecture must design for reliability and correctness.

## Future Impact

Every future document describing how riders are matched with drivers must attribute the assignment decision to Dispatch. The interaction mechanism between Dispatch and other capabilities, and the specific assignment logic itself, are left to later architectural and domain-level decisions and are not constrained further by this ADR beyond the ownership stated here.

## Related ADRs

Builds on ADR-001 (System Philosophy), specifically the requirement that architectural responsibilities be traceable to a single, accountable owner.

## References

- [PROJECT_CONSTITUTION.md](../PROJECT_CONSTITUTION.md), Section 3 (Product Principles — Fair Dispatch)
- [ADR-001: System Philosophy](ADR-001-System-Philosophy.md)

# ADR-009: Domain Isolation

## Status

Proposed

## Context

ADR-002 establishes Dispatch as an independent architectural capability, and ADR-005 establishes that each capability owns its own data exclusively. These are instances of a more general rule that has not yet been stated explicitly: how architectural capabilities, or domains, are bounded and kept independent of one another.

## Problem

What general rule governs the boundary between one architectural domain and another, so that independence established for Dispatch and for data ownership applies consistently across the whole system?

## Decision

PIOS is decomposed into bounded domains, each with an explicitly stated responsibility. A domain's boundary is defined by that responsibility, and everything within it — its internal reasoning, its owned data (ADR-005), and its internal logic — is accessible to other domains only through that domain's declared interaction style (ADR-004) or the events it publishes (ADR-003). No domain shares internal logic or internal representations with another domain outside those declared means. This ADR does not enumerate the specific domains of the PIOS system or define a domain model; it establishes the rule that governs how any domain, once identified, must relate to the others.

## Alternatives Considered

- **Allowing domains to share internal business logic through common internal libraries.** Rejected: it reintroduces the hidden coupling that ADR-001 requires the architecture to avoid, and it would make one domain's internal change silently affect another.
- **Defining domain boundaries informally, on a case-by-case basis, without a general rule.** Rejected: it would make the independence already committed to for Dispatch and for data ownership inconsistent with the rest of the system, contradicting the Consistency intent of ADR-004.

## Consequences

### Positive Consequences

- Generalizes the independence already established for Dispatch and data ownership into a single, consistent rule for every domain.
- Allows domains to be developed and changed independently, so long as their declared interaction style and published events remain stable.
- Gives future domain-level documentation a clear boundary rule to apply when domains are actually identified and named.

### Negative Consequences

- Requires every future domain to be designed with an explicit boundary and declared interaction style before it can be considered complete.
- May require rework if a domain's responsibility is later found to have been drawn incorrectly.

## Future Impact

Domain-level documentation, once produced, must identify each domain's responsibility and boundary consistent with this rule. No future document may describe two domains sharing internal logic or internal data access outside the means declared in ADR-003 and ADR-004.

## Related ADRs

Builds on ADR-001 (System Philosophy), ADR-002 (Dispatch Engine, as an instance of a bounded domain), and ADR-005 (Data Ownership, as an instance of the boundary this ADR generalizes).

## References

- [PROJECT_CONSTITUTION.md](../PROJECT_CONSTITUTION.md), Section 4 (Engineering Principles — No Hidden Assumptions)
- [ADR-001: System Philosophy](ADR-001-System-Philosophy.md)
- [ADR-002: Dispatch Engine](ADR-002-Dispatch-Engine.md)
- [ADR-003: Event-Driven Domain](ADR-003-Event-Driven-Domain.md)
- [ADR-004: API Style](ADR-004-API-Style.md)
- [ADR-005: Data Ownership](ADR-005-Data-Ownership.md)

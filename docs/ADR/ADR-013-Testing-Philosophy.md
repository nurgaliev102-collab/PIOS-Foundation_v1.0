# ADR-013: Testing Philosophy

## Status

Proposed

## Context

ADR-006 requires documentation to exist and be reviewed before implementation proceeds. ADR-009 establishes that domains are bounded and independent. Neither ADR defines how confidence is established that a domain's behavior matches its documentation, or that a domain's boundary is honored in practice. An architectural decision is needed on the philosophy that governs verification, independent of any specific tool.

## Problem

What philosophy governs how confidence is established that a domain's implementation matches its documentation and that domain boundaries are honored, without prescribing specific tools?

## Decision

Verification in PIOS follows two complementary practices:

- **Documentation validation.** A domain's documentation is checked for internal consistency and for consistency with the Constitution and applicable ADRs before it is considered part of the reviewed record, consistent with the Definition of Done in Constitution Section 11.
- **Integration philosophy.** Confidence that a domain honors its boundary (ADR-009) is established primarily at that boundary — its declared interaction style (ADR-004) and the events it publishes (ADR-003) — rather than by verifying another domain's internal behavior directly, since internal behavior is not visible across the boundary by design.

Verification weighted toward a domain's own internals and its declared boundary is preferred over verification that spans many domains at once, because the latter is harder to attribute to a single cause when it fails. This ADR does not define specific testing frameworks, tools, or coverage targets.

## Alternatives Considered

- **Relying primarily on verification that spans the whole system end to end.** Rejected: a failure detected this way is hard to attribute to a specific domain, and it is slower to run and maintain than verification scoped to a domain and its boundary.
- **Treating documentation as accurate by assumption, without validating it against the Constitution and ADRs.** Rejected: it would allow inconsistency to accumulate silently, contradicting the Single Source of Truth principle in ADR-001.

## Consequences

### Positive Consequences

- Localizes verification failures to a specific domain or boundary, making them easier to diagnose.
- Keeps documentation itself subject to the same scrutiny as implementation, consistent with Documentation Driven Development (ADR-006).
- Reinforces that a domain's boundary, not its internals, is what other domains and verification efforts may depend on.

### Negative Consequences

- Requires every domain to define what verifying its own boundary means before it can be considered complete.
- May leave certain cross-domain interaction failures undetected until verification specifically targeting an interaction is performed, which requires deliberate design.

## Future Impact

Any future testing strategy or quality assurance document for a specific domain must be consistent with this philosophy: verification weighted toward the domain's own behavior and its declared boundary, with documentation held to the same standard as implementation.

## Related ADRs

Builds on ADR-001 (System Philosophy), ADR-003 (Event-Driven Domain), ADR-004 (API Style), ADR-006 (Documentation Driven Development), and ADR-009 (Domain Isolation).

## References

- [PROJECT_CONSTITUTION.md](../PROJECT_CONSTITUTION.md), Section 11 (Definition of Done)
- [ADR-001: System Philosophy](ADR-001-System-Philosophy.md)
- [ADR-003: Event-Driven Domain](ADR-003-Event-Driven-Domain.md)
- [ADR-004: API Style](ADR-004-API-Style.md)
- [ADR-006: Documentation Driven Development](ADR-006-Documentation-Driven-Development.md)
- [ADR-009: Domain Isolation](ADR-009-Domain-Isolation.md)

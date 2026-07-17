# ADR-012: Observability Principles

## Status

Proposed

## Context

PIOS domains communicate through interactions (ADR-004) and events (ADR-003) across boundaries that are, by design, independent of one another (ADR-009). A rider or driver interaction with the platform may involve several domains. When something behaves unexpectedly, the platform must be diagnosable, consistent with the Reliability principle in Constitution Section 3 and the Traceability principle in ADR-001.

## Problem

What must be true of every domain, from an observability standpoint, for the platform's behavior to remain diagnosable as domains interact across boundaries?

## Decision

Every domain provides observability into its own behavior as an intrinsic part of that domain, not as work added afterward. Observability includes a record of what the domain did (logs), a measure of how the domain is behaving over time (metrics), and a way to follow a single interaction as it crosses domain boundaries (tracing). A single rider or driver interaction that touches multiple domains must be followable across those domains, not only within one. Monitoring of this observability output is treated as an ongoing responsibility, not a one-time setup step. This ADR does not define specific tools, formats, or infrastructure for logging, metrics, or tracing.

## Alternatives Considered

- **Adding observability only after a domain reaches production or after an incident occurs.** Rejected: it leaves the platform undiagnosable exactly when reliability matters most, contradicting the Reliability principle in Constitution Section 3.
- **Treating observability as optional, left to each domain's discretion.** Rejected: since an interaction can cross multiple domains (ADR-009), an interaction is only as traceable as its least observable domain, so discretion at the domain level would undermine traceability for the whole platform.

## Consequences

### Positive Consequences

- Ensures that a rider or driver interaction spanning multiple domains can be understood after the fact.
- Makes observability a shared, predictable property of every domain rather than something that varies by domain.
- Supports the Reliability and Traceability principles already established in the Constitution and in ADR-001.

### Negative Consequences

- Adds design and implementation work to every domain from the point it is first built.
- Requires a consistent way to follow an interaction across domain boundaries to be defined before multiple domains begin interacting in production.

## Future Impact

Any future architecture or implementation document for a domain must describe how that domain satisfies these observability principles. The specific tooling and cross-domain tracing mechanism are left to later architectural decisions.

## Related ADRs

Builds on ADR-001 (System Philosophy), ADR-003 (Event-Driven Domain), ADR-004 (API Style), and ADR-009 (Domain Isolation), addressing the diagnosability of interactions that cross the boundaries those ADRs establish.

## References

- [PROJECT_CONSTITUTION.md](../PROJECT_CONSTITUTION.md), Section 3 (Product Principles — Reliability), Section 14 (Quality Principles — Traceability)
- [ADR-001: System Philosophy](ADR-001-System-Philosophy.md)
- [ADR-003: Event-Driven Domain](ADR-003-Event-Driven-Domain.md)
- [ADR-004: API Style](ADR-004-API-Style.md)
- [ADR-009: Domain Isolation](ADR-009-Domain-Isolation.md)

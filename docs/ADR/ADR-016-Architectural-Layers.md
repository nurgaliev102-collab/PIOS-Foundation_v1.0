# ADR-016: Architectural Layers

## Status

Proposed

## Context

SYSTEM_ARCHITECTURE.md Section 5 (High-Level Architecture) marks as "Architecture Decision Required" whether PIOS follows a specific overall architectural style. ADR-001 already establishes that PIOS is composed of loosely coupled, independently evolvable components with explicit boundaries, decomposed along proven domain boundaries (ADR-009) rather than technical layers. SYSTEM_ARCHITECTURE.md Section 5 already states, without formally ratifying it, that PIOS is organized as a set of independently owned architectural capabilities "rather than as a stack of technical layers." This ADR resolves that open marker.

## Decision

PIOS follows a Capability-Based architecture: the system as a whole is organized around independently owned architectural capabilities (ADR-009), each with an explicit boundary and responsibility, rather than around technical layers shared uniformly across the whole system. This decision concerns only the system's overall organizational style. It does not decide, and explicitly leaves open, how any single capability structures its own internals — whether in a layered, hexagonal, clean, vertical-slice, or other pattern is a separate question, out of scope here, and not required at this level of decision.

## Consequences

Removes the "Architecture Decision Required" marker in SYSTEM_ARCHITECTURE.md Section 5 by formally ratifying the capability-based organization already implied by ADR-001 and ADR-009. Gives every future architectural or domain document a confirmed system-wide organizing principle. Does not constrain the internal design of any individual capability, which remains open if and when that decision is needed.

## Alternatives Considered

Layered architecture, applying a single set of technical layers across the whole system — rejected, since it directly contradicts the boundary-first, capability-owned organization already established in ADR-001 and ADR-009, and was already explicitly ruled out in SYSTEM_ARCHITECTURE.md Section 5. Hexagonal, Clean, or Vertical Slice architecture as the system-wide style — not decided either way, since none of these describe a system-wide, multi-capability organization in the same sense as Layered or Capability-Based; they describe internal structuring of a single unit, which is out of scope for this ADR.

## References

- [PROJECT_CONSTITUTION.md](../PROJECT_CONSTITUTION.md), Section 4
- [ADR-001: System Philosophy](ADR-001-System-Philosophy.md)
- [ADR-009: Domain Isolation](ADR-009-Domain-Isolation.md)
- [SYSTEM_ARCHITECTURE.md](../SYSTEM_ARCHITECTURE.md), Section 5

# ADR-006: Documentation Driven Development

## Status

Proposed

## Context

Constitution Section 4 establishes Documentation First as an engineering principle, and ADR-001 restates it as part of the system's foundational philosophy. Neither document defines what, concretely, must be true before implementation work is allowed to begin. Without an operational rule, "documentation first" remains an intention rather than an enforceable practice.

## Problem

What concrete condition must be satisfied before implementation of a given capability is allowed to begin?

## Decision

Implementation of any capability does not begin until documentation describing that capability exists at every layer of the hierarchy defined in Constitution Section 5 that is applicable to it, and that documentation has been reviewed under the process defined in Constitution Section 12. A change that introduces implementation without a corresponding, already-existing document is not accepted. Documentation gaps discovered during implementation are treated as documentation defects to be resolved before implementation continues, not as license to proceed undocumented.

## Alternatives Considered

- **Writing documentation alongside or after implementation.** Rejected: it allows undocumented assumptions to enter the system before they are reviewed, which Constitution Section 4 explicitly forbids.
- **Requiring documentation only for capabilities judged "significant" by the implementer.** Rejected: it introduces a subjective judgment call that undermines the Single Source of Truth and Traceability principles established in ADR-001.

## Consequences

### Positive Consequences

- Makes Documentation First an enforceable condition rather than an aspiration.
- Ensures every implemented capability can be traced back to a reviewed document, satisfying the Traceability principle in ADR-001.
- Surfaces missing or ambiguous documentation before it becomes embedded in code.

### Negative Consequences

- Slows the start of any new capability until its documentation exists and is reviewed.
- Requires ongoing maintenance discipline so documentation does not silently fall out of date relative to implementation.

## Future Impact

Every future workflow or process document, including how AI assistants and human developers collaborate, must be consistent with this rule: no implementation without prior, reviewed documentation.

## Related ADRs

Builds on ADR-001 (System Philosophy), specifically the Documentation First and Traceability elements of the system philosophy it establishes.

## References

- [PROJECT_CONSTITUTION.md](../PROJECT_CONSTITUTION.md), Section 4 (Engineering Principles — Documentation First), Section 5 (Documentation Hierarchy), Section 12 (Change Management)
- [ADR-001: System Philosophy](ADR-001-System-Philosophy.md)

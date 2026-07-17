# ADR-008: Repository Strategy

## Status

Proposed

## Context

Constitution Section 8 states, at a principle level, that the repository's structure must reflect the documentation hierarchy and that folders have a single, stated purpose. As documentation and, later, implementation accumulate, an explicit decision is needed on how the repository is organized to keep that principle enforceable in practice.

## Problem

What philosophy governs how folders and documentation are organized within the repository as the project grows?

## Decision

The repository is organized so that its top-level structure mirrors the documentation hierarchy defined in Constitution Section 5: the Constitution at the root, ADRs in their own location, and each subsequent layer of documentation in its own clearly named location. Every folder has exactly one stated purpose, declared in that folder's own index document, and content is placed according to that stated purpose rather than convenience. Documentation is never split across folders in a way that obscures which single document is authoritative for a given topic, preserving the Single Source of Truth principle from ADR-001.

## Alternatives Considered

- **Organizing folders by contributor or team rather than by documentation layer.** Rejected: it would decouple repository structure from the documentation hierarchy the Constitution requires it to reflect, making the authoritative source for a given topic harder to locate.
- **Allowing ad hoc folders to be created without a stated purpose.** Rejected: it would erode the single-purpose-per-folder rule and make navigation dependent on tribal knowledge rather than the documented structure itself.

## Consequences

### Positive Consequences

- Keeps the repository navigable by anyone who understands the documentation hierarchy alone.
- Reinforces Single Source of Truth by giving every topic exactly one authoritative location.
- Makes it straightforward to verify, at any time, that the repository structure still reflects Constitution Section 5.

### Negative Consequences

- Requires a deliberate decision before adding any new top-level folder, rather than creating one ad hoc.
- May require reorganization if the documentation hierarchy itself is amended in the future.

## Future Impact

Any future decision to add a new category of documentation must include a corresponding update to the repository structure and its stated purpose, consistent with this ADR and Constitution Section 8.

## Related ADRs

Builds on ADR-001 (System Philosophy) and ADR-006 (Documentation Driven Development), applying their Single Source of Truth and documentation-first requirements to the physical organization of the repository.

## References

- [PROJECT_CONSTITUTION.md](../PROJECT_CONSTITUTION.md), Section 5 (Documentation Hierarchy), Section 8 (Repository Rules)
- [ADR-001: System Philosophy](ADR-001-System-Philosophy.md)
- [ADR-006: Documentation Driven Development](ADR-006-Documentation-Driven-Development.md)

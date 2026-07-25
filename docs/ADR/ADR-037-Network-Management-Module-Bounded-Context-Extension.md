# ADR-037: Network Management Module — Bounded Context Extension

## Status

Proposed

## Context

`PRODUCT_DECISION_PERSONAL_NETWORK_MVP_TRANSITION.md` authorized a minimal software Personal Network MVP (Sprint 7), and left module placement for its `Person`/`Connection` concepts as an explicit open question, to be resolved either by extending an existing module or by a new one — noting that a new module requires an ADR extending or superseding ADR-017/ADR-018, per `MODULE_STRUCTURE.md` Section 8's own Extension Strategy ("A new module is added only when a new bounded context is ratified through a decision that extends or supersedes ADR-017 and ADR-018").

The Sprint 7A technical specification ("PIOS Network Foundation") that followed chose a new module, `network-management`, owning four capabilities not held by any of the eight modules `MODULE_STRUCTURE.md` Section 3 already ratifies: representing a person independent of any single role, a person's roles (profiles), a directed connection between two people, and an invitation mechanism that produces a connection. This ADR is the missing authorization `MODULE_STRUCTURE.md` Section 8 requires before that module may be scaffolded.

**Why not extend Passenger Experience instead.** `MODULE_STRUCTURE.md` already assigns "Personal Client relationships" to Passenger Experience, and an earlier planning pass (`IMPLEMENTATION_PLAN_SPRINT_7_PERSONAL_NETWORK_MVP.md`) recommended exactly that placement for a narrower, reference-only `Connection`. Sprint 7A's own specification instead models `Person` as the identity a `Driver` and a `Passenger` will eventually both point to (`PersonProfile.type`: `DRIVER` / `PASSENGER` / `NETWORK_MEMBER`) — a concept that, by definition, cannot be owned by either existing module alone without one of them owning a piece of the other's actor representation, which `MODULE_STRUCTURE.md` Section 2 ("Domain Alignment," "no module spans more than one domain") forbids. A new, narrowly-scoped module is the smaller change once `Person` itself — not only `Connection` — is in scope.

## Problem

Does PIOS's already-ratified module structure (ADR-017, ADR-018, `MODULE_STRUCTURE.md`) permit adding a ninth module, `network-management`, owning Person, Profile, Connection, and Invitation, without altering any of the eight already-ratified modules' own ownership?

## Decision

`network-management` is added as a new module, independently deployable per ADR-023's own per-module service style, owning exactly four capabilities: Create/Retrieve Person, Create/Retrieve Profile, Create/Retrieve Connection, Create/Accept Invitation. It persists these in its own database (`pios_network_management`), consistent with ADR-005/ADR-009 — no shared schema with any existing module.

**Boundary, stated explicitly:**

- `network-management` does not own, read, or write `Driver`, `Order`, `Proposal`, or `Assignment` data. It references a driver or passenger only by a plain string id, the same "reference, not ownership" pattern Dispatch's own `DriverReference`/`OrderReference` already establish for cross-aggregate references within a single module — here used across modules, at the API boundary only, never through shared storage.
- No existing module depends on `network-management`, and `network-management` depends on no existing module. Sprint 7A introduces zero integration in either direction (Product Decision's own scenario is realized by a later, separate sprint).
- `Person` is not a merger of `Driver` and `Passenger`; it is a new, independent identity concept. Whether or how `Driver`/`Passenger` records ever come to reference a `Person` id is explicitly not decided here.

## Constraints

- No change to any of the eight already-ratified modules' owned capabilities, boundaries, or persisted schema (`MODULE_STRUCTURE.md` Section 3).
- No new cross-module dependency, synchronous or event-based (`MODULE_STRUCTURE.md` Section 5) — Sprint 7A is additive only.
- No Assignment Policy, ranking, scoring, or dispatch-affecting behavior — unchanged from `PRODUCT_DECISION_PERSONAL_NETWORK_MVP_TRANSITION.md`'s own narrow authorization.

## Consequences

- A ninth entry is added to `MODULE_STRUCTURE.md` Section 3 and to the module list `settings.gradle.kts` declares — a mechanical, additive change, not a rewrite of either document.
- Future work connecting `network-management`'s `Connection` concept to actual order routing (the deferred half of the founder's original Sprint 7 sketch) will need its own ADR when it introduces the first real cross-module dependency — not authorized here.
- If a future decision instead wants `Driver`/`Passenger` to be reconstituted as `Person` roles directly (rather than referenced by id from a separate module), that is a materially larger change to ADR-009/ADR-017/ADR-018 themselves, requiring its own ADR; this decision does not anticipate or simplify that path.

## Traceability

| Source | Relationship |
| --- | --- |
| `PRODUCT_DECISION_PERSONAL_NETWORK_MVP_TRANSITION.md` | Authorizes the Person/Connection scope this ADR places into a module |
| `MODULE_STRUCTURE.md` Section 8 (Extension Strategy) | Requires this ADR before a new module may be added |
| `ADR-017` (Bounded Context Strategy), `ADR-018` (Core Architectural Components) | Extended, not superseded — an additional bounded context, no change to the existing eight |
| `ADR-023` (Backend Technology Decision) | This module follows the same Kotlin/Spring Boot/independently-deployable-service style |
| `ADR-005`, `ADR-009` (Data Ownership, Domain Isolation) | Unchanged; this ADR's own database-ownership rule is a direct application, not an exception |

## Files Changed

`docs/ADR/ADR-037-Network-Management-Module-Bounded-Context-Extension.md` (new). `docs/README.md`'s ADR table receives one new row. `docs/MODULE_STRUCTURE.md` Section 3 should receive a corresponding "Network Management Module" entry as a follow-up documentation update once this ADR is reviewed — not performed automatically by this ADR itself, consistent with how ADR-035 preceded rather than silently rewrote `MODULE_STRUCTURE.md`'s own Dispatch entry.

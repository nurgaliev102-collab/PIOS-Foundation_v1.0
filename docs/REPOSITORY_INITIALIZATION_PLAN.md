# PIOS Repository Initialization Plan

Status: Draft — Derived from Approved Foundation. This document defines the controlled transition from architecture documentation to the first real software repository: what is created first, what is intentionally delayed, and how architecture becomes implementation. It creates no repository, no folder, no file, and no source code; it is a plan for that creation, not the creation itself.

This document is derived exclusively from [PROJECT_CONSTITUTION.md](PROJECT_CONSTITUTION.md), the ADR Foundation (through ADR-026), [SYSTEM_ARCHITECTURE.md](SYSTEM_ARCHITECTURE.md), [APPLICATION_ARCHITECTURE.md](APPLICATION_ARCHITECTURE.md), [DATABASE_DESIGN.md](DATABASE_DESIGN.md), [MODULE_STRUCTURE.md](MODULE_STRUCTURE.md), [INTERFACE_CONTRACTS.md](INTERFACE_CONTRACTS.md), [ENGINEERING_GUIDELINES.md](ENGINEERING_GUIDELINES.md), [PROJECT_SCAFFOLDING_PLAN.md](PROJECT_SCAFFOLDING_PLAN.md), and [INITIAL_IMPLEMENTATION_PLAN.md](INITIAL_IMPLEMENTATION_PLAN.md), in that order of authority. It narrows what these documents already establish to the specific moment the repository is first created; it introduces no module, technology, or phase they do not.

---

## 1. Purpose

PROJECT_SCAFFOLDING_PLAN.md defines the full conceptual repository structure, and INITIAL_IMPLEMENTATION_PLAN.md defines what is built first and in what order. This document narrows both one step further: not "eventually there will be eight module areas," but exactly what exists the moment the repository is first created, and what is deliberately absent from that moment. It is the point at which planning ends and the first real repository begins, consistent with the Minimal Initial Complexity principle already established in PROJECT_SCAFFOLDING_PLAN.md Section 2.

## 2. Initialization Principles

- **Architecture Preservation.** Nothing created at initialization contradicts MODULE_STRUCTURE.md, INTERFACE_CONTRACTS.md, or any ratified ADR; the repository's first state is already consistent with the full architecture, not a placeholder to be reconciled with it later.
- **Minimal Starting Scope.** Only what INITIAL_IMPLEMENTATION_PLAN.md's Phases 1 through 4 require exists at initialization; nothing is created because it will "eventually" be needed.
- **Incremental Growth.** Each subsequent module or area is added only when its own phase begins (INITIAL_IMPLEMENTATION_PLAN.md Section 4), never all at once.
- **Traceability.** Every area created at initialization traces to a specific decision that already justified it — PROJECT_SCAFFOLDING_PLAN.md or MODULE_STRUCTURE.md — nothing is added without that traceability.
- **Avoid Premature Complexity.** Consistent with No Premature Optimization (ENGINEERING_GUIDELINES.md Section 3), extended here to repository structure itself: an area for a deferred module (Section 5) is not created ahead of need.

## 3. Initial Repository Scope

No actual file is created by this document; the following describes what the first repository state contains, conceptually.

- **Documentation.** The existing `docs/` area, already real since the Foundation phase (ADR-008), is unchanged; the repository's code areas reference it, never duplicate it.
- **Backend foundation.** The `backend/` area itself (PROJECT_SCAFFOLDING_PLAN.md Section 3) exists, but only the sub-areas for the first-priority modules (Section 4) are populated; the remaining four modules' areas are named in the plan but not yet created.
- **Frontend foundation.** The `frontend/` area exists conceptually; it is not populated at initialization, since INITIAL_IMPLEMENTATION_PLAN.md's Phase 5 (User Experiences) follows Phases 1 through 4, not the initial state.
- **Configuration foundation.** Only the repository-level configuration needed to make Phase 1, Platform Foundation, provable — consistent with the First Implementation Milestone already defined in PROJECT_SCAFFOLDING_PLAN.md Section 10.
- **Development foundation.** Whatever satisfies the Environment Preparation Principles already established in PROJECT_SCAFFOLDING_PLAN.md Section 8 — the ability to build and run a module's area in isolation — without naming a tool.

## 4. Initial Module Scope

Aligned exactly with INITIAL_IMPLEMENTATION_PLAN.md Sections 4 and 5. No code is created for any module below.

- **Driver Management.** First, because INTERFACE_CONTRACTS.md Section 3 shows it has no inbound dependency on any other module (INITIAL_IMPLEMENTATION_PLAN.md, Phase 2).
- **Passenger Experience, minimal context only.** Created only to the extent Order Management's Submit Order depends on it (INTERFACE_CONTRACTS.md, Contract: Passenger Experience → Order Management), consistent with the same minimal scope INITIAL_IMPLEMENTATION_PLAN.md Phase 3 already describes. Passenger Experience's fuller responsibility — including Personal Client relationships (UC-002) — is not part of this initial scope, since UC-002 is not part of the first vertical slice.
- **Order Management.** Its own responsibility — accepting and tracking a submitted order — is created at initialization (INITIAL_IMPLEMENTATION_PLAN.md, Phase 3); the order's progression through assignment depends on Dispatch, created alongside it.
- **Dispatch.** Depends on Driver Management and Order Management; its creation, integrated with the other three, is what makes the first vertical slice (INITIAL_IMPLEMENTATION_PLAN.md Section 6) realizable (Phase 4).

## 5. Deferred Modules

- **Administration.** Self-contained, with no dependency on or from any other module (INTERFACE_CONTRACTS.md Section 4); it supports UC-010, which is not part of the first vertical slice. Deferred to Phase 5, per INITIAL_IMPLEMENTATION_PLAN.md Section 4.
- **Analytics.** A generic consumer of other modules' events with no specific event-to-consumer mapping established (EVENT_CATALOG.md Section 9); nothing exists yet at initialization for it to consume. Deferred to Phase 6.
- **Notifications.** The same reasoning as Analytics applies; no specific provider is named for it in any approved document. Deferred to Phase 6.
- **Payments.** No currently grounded driving use case exists at all (USE_CASE_CATALOG.md, Areas Not Catalogued); even its Record Payment command has no attributed actor goal. Deferred to Phase 6, the lowest initialization priority.

## 6. Repository Organization Principles

- **Documentation location.** Remains exactly where PROJECT_SCAFFOLDING_PLAN.md Section 6 and ADR-008 already placed it; this plan changes nothing about it.
- **Backend area.** Exists as the conceptual container PROJECT_SCAFFOLDING_PLAN.md Section 3 defines; only the four modules in Section 4 above are populated within it at initialization.
- **Frontend area.** Exists as the same conceptual container; unpopulated until Phase 5.
- **Module isolation.** No initialized module's area contains another module's code, consistent with MODULE_STRUCTURE.md Section 2 (High Cohesion, Explicit Ownership); this holds identically for the four modules created at initialization and the four deferred.
- **Configuration separation.** Repository-level configuration remains distinct from any module's own configuration, consistent with PROJECT_SCAFFOLDING_PLAN.md Section 3.

No real filesystem creation is performed by describing these principles.

## 7. First Development Environment

No tool is named here.

- **Developer preparation.** An engineer, human or AI, can build and run each of the four first-priority modules' areas in isolation, consistent with ADR-023 (Kotlin, Spring Boot) and ADR-025 (PostgreSQL for these domains), extending the Environment Preparation Principles in PROJECT_SCAFFOLDING_PLAN.md Section 8 to this specific initial scope.
- **Validation.** The repository's initial state satisfies the First Implementation Milestone already defined in PROJECT_SCAFFOLDING_PLAN.md Section 10 — proving the scaffolding itself works, before any business capability is claimed to.
- **Consistency.** Every engineer's environment produces the same result from the same repository state, extending the Consistency principle in ENGINEERING_GUIDELINES.md Section 3 to environment setup itself, without naming a mechanism.

## 8. First Commit Definition

The first commit represents the Initial Repository Scope (Section 3) existing and provably satisfying the First Implementation Milestone in PROJECT_SCAFFOLDING_PLAN.md Section 10 — an architecture-aligned foundation, nothing more. It contains no implementation of any use case and claims no business capability; its sole claim is that the scaffolding itself is correct and usable, consistent with MODULE_STRUCTURE.md and INTERFACE_CONTRACTS.md. No code implementation is represented by it.

## 9. Transition to Implementation

- **Repository initialization.** This document's own scope (Sections 3 through 8) is realized as an actual repository — an act this document plans for but does not itself perform.
- **Backend foundation.** Driver Management and Order Management, with its minimal Passenger Experience context, begin (INITIAL_IMPLEMENTATION_PLAN.md Phases 2–3), per the Initial Module Scope in Section 4.
- **First vertical slice.** Dispatch (Phase 4) completes, and the flow already defined in INITIAL_IMPLEMENTATION_PLAN.md Section 6 becomes exercisable end to end.
- **MVP development.** User Experiences (Phase 5) begin, giving real actors access to the already-working vertical slice; MVP readiness is then evaluated against the Validation Criteria already defined in INITIAL_IMPLEMENTATION_PLAN.md Section 8.

## 10. Traceability

| Section | Scaffolding Plan | Implementation Plan | Module Structure | Interface Contracts | Engineering Guidelines | ADR-023 | ADR-024 | ADR-025 | ADR-026 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1. Purpose | Section 2 | — | — | — | — | — | — | — | — |
| 2. Initialization Principles | Section 2 | — | — | — | Section 3 | — | — | — | — |
| 3. Initial Repository Scope | Sections 3, 8, 10 | Section 4 | — | — | — | — | — | — | — |
| 4. Initial Module Scope | — | Sections 4–5 | Section 3 | Section 5 | — | — | — | — | — |
| 5. Deferred Modules | — | Sections 4–5 | — | Sections 4–5 | — | — | — | — | — |
| 6. Repository Organization Principles | Sections 3, 6 | — | Section 2 | — | — | — | — | — | — |
| 7. First Development Environment | Section 8 | — | — | — | Section 3 | ✓ | — | ✓ | — |
| 8. First Commit Definition | Section 10 | — | — | ✓ | — | — | — | — | — |
| 9. Transition to Implementation | — | Sections 4, 6, 8 | — | — | — | — | ✓ | — | ✓ |

Where this document is silent, no lower-priority document may fill that silence by invention; resolution requires the relevant higher-priority document to be extended first, per the authority order established in PROJECT_CONSTITUTION.md Section 5.

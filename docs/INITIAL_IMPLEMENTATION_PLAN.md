# PIOS Initial Implementation Plan

Status: Draft — Derived from Approved Foundation. This document converts the architecture already approved into the first controlled implementation roadmap: what is built first, why in this order, what is intentionally postponed, and what defines MVP readiness. It creates no code, no repository file, and no feature.

This document is derived exclusively from [PROJECT_CONSTITUTION.md](PROJECT_CONSTITUTION.md), the ADR Foundation (through ADR-026), [PRODUCT_FOUNDATION.md](PRODUCT_FOUNDATION.md), [DOMAIN_MODEL.md](DOMAIN_MODEL.md), [EVENT_CATALOG.md](EVENT_CATALOG.md), [USE_CASE_CATALOG.md](USE_CASE_CATALOG.md), [APPLICATION_ARCHITECTURE.md](APPLICATION_ARCHITECTURE.md), [MODULE_STRUCTURE.md](MODULE_STRUCTURE.md), [INTERFACE_CONTRACTS.md](INTERFACE_CONTRACTS.md), [ENGINEERING_GUIDELINES.md](ENGINEERING_GUIDELINES.md), and [PROJECT_SCAFFOLDING_PLAN.md](PROJECT_SCAFFOLDING_PLAN.md), in that order of authority. It sequences what these documents already establish; it introduces no capability, module, or event they do not.

---

## 1. Purpose

The architecture, technology, and scaffolding decisions are now complete; what remains is deciding what gets built first. This plan makes that transition, converting a fully specified but not-yet-sequenced architecture into a controlled roadmap, consistent with Documentation Driven Development (ADR-006) and the Small, Controlled Changes principle in ENGINEERING_GUIDELINES.md Section 2. It decides sequencing only — never how anything is built, which remains governed by the architecture documents already approved.

## 2. Implementation Principles

- **Architecture Before Features.** No feature is implemented ahead of the architecture that governs it (ADR-001, ADR-006); this plan sequences what is built, never how.
- **Core Value Before Expansion.** The platform's central value — connecting a driver to a passenger through a fair, transparent assignment (PRODUCT_FOUNDATION.md Section 4) — is built before any capability that extends beyond it.
- **Small, Validated Increments.** Each phase produces a working, verifiable increment before the next begins, consistent with ENGINEERING_GUIDELINES.md Section 2.
- **Maintain Module Boundaries.** No phase implements a module ahead of the boundary already established for it (MODULE_STRUCTURE.md); no phase blurs two modules together to move faster.
- **Avoid Premature Complexity.** A capability is implemented only once a demonstrated need for it exists, consistent with No Premature Optimization (ENGINEERING_GUIDELINES.md Section 3); this governs sequencing as much as individual technical decisions.

## 3. MVP Definition

The MVP is the smallest working realization of PIOS's Core Problem (PRODUCT_FOUNDATION.md Section 4): connecting a driver to a passenger fairly and transparently. It focuses on driver availability, order creation, the dispatch capability, assignment, and an order's full lifecycle — because these correspond exactly to the only interaction contracts already grounded between real, interdependent modules (INTERFACE_CONTRACTS.md Section 5): Driver Management providing availability to Dispatch, and Dispatch providing assignment outcomes to Order Management. No other module pairing in the entire documentation set has an equivalent, specific, already-established dependency. Building anything else first would mean building capability the architecture has not yet shown to be load-bearing for the platform's central value.

Concretely, the MVP realizes: UC-004 (Declare Availability), UC-001 (Request Transportation), UC-007 (Determine Assignment), UC-005 (Receive Assignment), UC-006 (Accept Assignment), and UC-008 (Maintain Fair Allocation) — producing DriverAvailabilityChanged, OrderSubmitted, OrderAssigned, AssignmentAccepted, and OrderCompleted or OrderCancelled (EVENT_CATALOG.md).

## 4. Implementation Sequence

No task or code is defined for any phase below.

**Phase 1 — Platform Foundation.** *Purpose:* the technical foundation. The repository areas and environment preparation already defined in PROJECT_SCAFFOLDING_PLAN.md Sections 3 and 8 exist and are provably usable, satisfying the First Implementation Milestone already defined in PROJECT_SCAFFOLDING_PLAN.md Section 10. No business capability exists yet.

**Phase 2 — Driver Management.** *Purpose:* the driver availability capability (UC-003, UC-004). Selected first among the business capabilities because INTERFACE_CONTRACTS.md Section 3 shows Driver Management has no inbound dependency on any other module; it can be built and verified in isolation.

**Phase 3 — Order Management.** *Purpose:* the order lifecycle (UC-001, UC-002). Order Management depends on Passenger Experience's originating context to accept a submission (INTERFACE_CONTRACTS.md, Contract: Passenger Experience → Order Management); this phase therefore also stands up the minimal Passenger Experience representation that contract requires, without which Order Management cannot be exercised. Order Management's own responsibility — accepting and tracking a submission — is complete at the end of this phase; the order's progression through assignment depends on Phase 4, and the two phases' outputs are integrated to complete the first vertical slice (Section 6).

**Phase 4 — Dispatch.** *Purpose:* the assignment capability (UC-005 through UC-008). Depends on Phase 2 (driver availability) and Phase 3 (a submitted order to assign); its completion, integrated with Phases 2–3, is what completes the first vertical slice.

**Phase 5 — User Experiences.** *Purpose:* passenger and operational interaction. The Passenger and Driver frontend applications (ADR-024) are built to let real actors invoke the capabilities Phases 2–4 already provide. Administration — self-contained, with no dependency on or from any other module (INTERFACE_CONTRACTS.md Section 4) — is stood up in this phase as well, backend and Administrator interface together, supporting UC-010.

**Phase 6 — Expansion Domains.** Analytics, Notifications, and Payments. *Purpose:* the three domains USE_CASE_CATALOG.md's "Areas Not Catalogued" and EVENT_CATALOG.md Section 9's generic, unspecified consumer relationship already identify as lacking a currently grounded driving use case or a specific event-to-consumer mapping. Placed last because each depends on the events Phases 2–4 already produce, and none has an independent grounding that would justify earlier priority.

## 5. Module Implementation Priority

1. **Driver Management.** No inbound module dependency (INTERFACE_CONTRACTS.md Section 3); Dispatch depends on it.
2. **Passenger Experience.** Provides the originating context Order Management depends on; otherwise self-contained.
3. **Order Management.** Depends on Passenger Experience for context and, for full lifecycle completion, on Dispatch; central to the MVP's core value.
4. **Dispatch.** Depends on Driver Management and Order Management; completes the MVP's core assignment capability.
5. **Administration.** Self-contained — no dependency on or from any other module; ranked after the MVP's four core modules because it supports UC-010 rather than the core vertical slice (Section 6).
6. **Notifications.** A generic consumer of other modules' events (EVENT_CATALOG.md Section 9); requires at least one of Phases 2–4 to exist to have anything to consume.
7. **Analytics.** A generic consumer of other modules' events, for the same reason as Notifications. No approved document distinguishes a priority between Notifications and Analytics; both are ranked adjacently for that reason, not because either is architecturally more urgent than the other.
8. **Payments.** No currently grounded driving use case at all (USE_CASE_CATALOG.md, Areas Not Catalogued); even its Record Payment command has no attributed actor goal. Lowest priority.

## 6. First Vertical Slice

No implementation detail is described; each step corresponds only to a command or event already established.

1. A driver becomes available — Declare Availability, producing DriverAvailabilityChanged (UC-004).
2. A passenger creates an order — Submit Order, using Passenger Experience's originating context, producing OrderSubmitted (UC-001).
3. Dispatch assigns a driver — the Dispatch domain service acts on the submitted order and the driver's availability, producing OrderAssigned (UC-007).
4. The driver accepts — Accept Assignment, producing AssignmentAccepted (UC-006), which Order Management relies on to know the order will proceed.
5. The order completes — OrderCompleted (DOMAIN_MODEL.md Section 12), reaching a state that, per DOMAIN_MODEL.md Section 11, cannot return to active.

## 7. MVP Exclusions

Each exclusion below rests on a boundary already established elsewhere, not a boundary invented for this plan.

- **Advanced analytics.** Analytics is deferred to Phase 6; no use case currently grounds it (USE_CASE_CATALOG.md).
- **Complex payment processing.** Payments is deferred to Phase 6; its Record Payment command has no attributed use case, and external settlement integration is explicitly outside PIOS (ADR-020; SYSTEM_ARCHITECTURE.md Section 11).
- **Partner ecosystem.** No Partner use case or domain exists anywhere in the documentation set; SYSTEM_ARCHITECTURE.md Section 4 marks specific future extension points as "Architecture Decision Required." No phase in this plan implements it.
- **Fleet features.** Fleet remains a Domain Decision Required gap throughout DOMAIN_MODEL.md, the data model documents, and DATABASE_DESIGN.md. No phase implements it.
- **Assignment algorithm sophistication.** Dispatch's specific assignment criteria are explicitly excluded from every level of this documentation (ADR-002); the MVP implements Dispatch's exclusive ownership of the decision, not any particular algorithmic sophistication.
- **Notification delivery and external channels.** Deferred to Phase 6; no specific event-to-consumer mapping exists yet for Notifications (EVENT_CATALOG.md Section 9).
- **Corporate Customer and Administrator experiences beyond their base use case.** UC-009 and UC-010 exist but are not part of the first vertical slice (Section 6); they belong to Phase 5, not Phases 1–4.

## 8. Validation Criteria

- **Business flow works.** The first vertical slice (Section 6) can be exercised end to end, producing the events already catalogued, in the order already established in DOMAIN_MODEL.md Section 12.
- **Architecture boundaries preserved.** No phase's implementation crosses a module boundary without going through the interface contracts already established (INTERFACE_CONTRACTS.md), verified per the Code Review Principles in ENGINEERING_GUIDELINES.md Section 11.
- **Events consistent.** Every event produced during the vertical slice matches, in name and meaning, exactly what EVENT_CATALOG.md already defines; no ad hoc event is introduced.
- **Documentation updated.** Any gap or ambiguity the implementation reveals is corrected in the governing documentation as part of the same change, consistent with ADR-006 and ENGINEERING_GUIDELINES.md Section 4.

## 9. Evolution Path

The transition from MVP to full platform happens the same way the architecture itself evolves: each subsequent phase and expansion domain (Section 4, Phase 6) is added incrementally, without requiring the MVP's already-working vertical slice to be redesigned, consistent with the supersede-don't-edit discipline in ADR-015. Because ADR-026 already commits every module to independent deployability as a release-process property, adding Analytics, Notifications, or Payments as later phases never requires redeploying the MVP's core modules — the full platform is reached by addition, not by replacement.

## 10. Traceability

| Section | Product Foundation | Use Cases | Application Architecture | Module Structure | Interface Contracts | Scaffolding Plan | Engineering Guidelines |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1. Purpose | — | — | Section 1 | — | — | Section 1 | Section 4 |
| 2. Implementation Principles | Section 4 | — | Section 2 | Section 2 | — | Section 2 | Sections 2–3 |
| 3. MVP Definition | Section 4 | UC-001, UC-004 through UC-008 | Section 4 | — | Section 5 | — | — |
| 4. Implementation Sequence | — | UC-001 through UC-010 | Sections 4–5 | Section 3 | Sections 3, 5 | Sections 3, 8, 10 | — |
| 5. Module Implementation Priority | — | — | — | Section 3 | Sections 3–4 | — | — |
| 6. First Vertical Slice | — | UC-001, UC-004, UC-006, UC-007 | — | — | Section 5 | — | — |
| 7. MVP Exclusions | Section 8 | Areas Not Catalogued | Section 5 | — | — | — | — |
| 8. Validation Criteria | — | — | — | — | Section 5 | — | Sections 4, 11 |
| 9. Evolution Path | — | — | — | — | — | Section 9 | — |

Where this document is silent, no lower-priority document may fill that silence by invention; resolution requires the relevant higher-priority document to be extended first, per the authority order established in PROJECT_CONSTITUTION.md Section 5.

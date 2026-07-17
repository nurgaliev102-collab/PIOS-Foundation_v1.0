# PIOS Application Architecture

Status: Draft — Derived from Approved Foundation. This document describes how application responsibilities are organized between user intentions and domain capabilities: how the goals already catalogued in USE_CASE_CATALOG.md are executed conceptually, and how application capabilities coordinate the domain behavior already established in DOMAIN_MODEL.md. It is the bridge between business intentions and system implementation. It is not an API specification, a database design, a frontend or backend architecture, a framework architecture, a microservice design, or a deployment design.

This document is derived exclusively from [PROJECT_CONSTITUTION.md](PROJECT_CONSTITUTION.md), the ADR Foundation, [PRODUCT_FOUNDATION.md](PRODUCT_FOUNDATION.md), [SYSTEM_ARCHITECTURE.md](SYSTEM_ARCHITECTURE.md), [DOMAIN_MODEL.md](DOMAIN_MODEL.md), [EVENT_CATALOG.md](EVENT_CATALOG.md), and [USE_CASE_CATALOG.md](USE_CASE_CATALOG.md), in that order of authority. It introduces no capability, domain, aggregate, or event beyond what these documents already establish.

This document does not decide, and remains consistent with ADR-016's explicit non-decision on, how any single domain structures its own internals. It addresses only the coordination responsibility that connects actor intentions (USE_CASE_CATALOG.md) to the domain capabilities already established (DOMAIN_MODEL.md), through the interaction and event mechanisms already established in SYSTEM_ARCHITECTURE.md Section 8. It does not ratify "application layer" as a new architectural component alongside the eight ratified in ADR-018; it describes a coordination responsibility that already follows from the Communication Model, not a ninth domain.

---

## 1. Purpose

Application Architecture exists so that the goals catalogued in USE_CASE_CATALOG.md can be understood in terms of how they are conceptually fulfilled — which domain capabilities they invoke, in what sequence of commands, queries, and events — without describing any interface, protocol, or data structure. It is the layer of description between an actor's goal and a domain's business meaning: it explains how intentions reach domains, never what those domains decide once they are reached.

## 2. Application Architecture Principles

- **Use-Case Driven Design.** Every application responsibility described here exists to fulfill a goal already catalogued in USE_CASE_CATALOG.md; none is introduced to serve a goal that document does not already recognize.
- **Domain Isolation.** Application responsibilities never bypass a domain's boundary. Every domain interaction occurs only through that domain's declared interaction (ADR-004) or the events it publishes (ADR-003), consistent with ADR-009.
- **Application Coordinates.** The application's role is limited to sequencing and coordinating requests to domain capabilities in service of a use case; it does not perform domain decision-making itself.
- **Domain Decides Business Meaning.** Business invariants and decisions — such as which driver an order is assigned to (ADR-002) — belong exclusively to the domain that owns them (ADR-005, ADR-009, DOMAIN_MODEL.md Section 11). The application layer never decides these; it only requests and observes.
- **No Business Logic Duplication.** Consistent with Single Source of Truth (ADR-001), a business rule or invariant is expressed once, within its owning domain, and is never re-implemented, re-checked, or restated at the application level.

## 3. Application Layer Responsibilities

**Belongs to the application layer:** translating an actor's goal, as catalogued in USE_CASE_CATALOG.md, into the appropriate sequence of domain commands and queries (DOMAIN_MODEL.md Sections 9–10); coordinating a use case that spans more than one domain, such as UC-001 (Order Management, Dispatch, Driver Management); and reacting to domain events (EVENT_CATALOG.md) where a use case's completion depends on a fact recorded by a domain other than the one the actor directly addressed.

**Does not belong to the application layer:** any business invariant or decision, which belongs to the owning domain (ADR-005, ADR-009); the specific criteria Dispatch uses to determine an assignment, which ADR-002 excludes from every level of this documentation; any technology, protocol, or persistence detail, which belongs to future API and database documents; and any business concept, domain, aggregate, or event not already established in DOMAIN_MODEL.md or EVENT_CATALOG.md.

## 4. Use Case Orchestration

Described conceptually, without workflow steps or sequence diagrams:

- **Passenger goals.** UC-001 (Request Transportation) is supported by coordinating a request toward Order Management, whose outcome depends on Dispatch's assignment decision becoming known through OrderAssigned; UC-002 (Maintain Platform Relationship) is supported by coordinating the passenger's ongoing representation within Passenger Experience.
- **Driver goals.** UC-003 (Participate in the Platform) and UC-004 (Declare Availability) are supported by coordinating a driver's standing and declared readiness within Driver Management; UC-005 (Receive Assignment) and UC-006 (Accept Assignment) are supported by coordinating the driver's side of Dispatch's assignment outcome, without any application-level involvement in how that outcome was determined.
- **Dispatch capabilities.** UC-007 (Determine Assignment) and UC-008 (Maintain Fair Allocation) are supported by invoking the Dispatch domain service (DOMAIN_MODEL.md Section 7) on behalf of a submitted order, and making its outcome available to the domains that depend on it, without the application layer containing any assignment criteria itself.
- **Administrator goals.** UC-010 (Oversee Platform Operation) is supported by coordinating an administrator's access to the governance and standing information Administration already owns (ADR-018).
- **Corporate goals.** UC-009 (Originate Corporate Demand) is supported the same way as a passenger's request, distinguished only by Order Origin identifying the order as a Corporate Order (DOMAIN_MODEL.md Section 6).

## 5. Application Capabilities

Each capability below is the application-level coordination responsibility for one domain already ratified in ADR-018. It restates none of that domain's own responsibility (DOMAIN_MODEL.md Section 3); it describes only how actor intentions reach it.

### Order Management

- **Purpose.** Translate a passenger's or corporate customer's request for transportation, and any cancellation, into Order Management's own lifecycle (DOMAIN_MODEL.md Sections 4, 12).
- **Responsibility.** Sequence the Submit Order and Cancel Order commands (DOMAIN_MODEL.md Section 9) in service of UC-001 and UC-009, and make the resulting OrderSubmitted, OrderCompleted, and OrderCancelled events (EVENT_CATALOG.md Section 5) available to the use case's originating actor.
- **Boundary.** Does not decide an order's status or completion itself — that decision belongs exclusively to Order Management the domain (ADR-005, ADR-009); the application capability only coordinates the request and observes the outcome.

### Dispatch

- **Purpose.** Connect the Order Management use cases (UC-001, UC-009) and the driver use cases (UC-005, UC-006) to Dispatch's assignment decision.
- **Responsibility.** Invoke the Dispatch domain service and sequence the Accept Assignment command (DOMAIN_MODEL.md Sections 7, 9) in service of UC-005 through UC-008, and make the resulting OrderAssigned and AssignmentAccepted events (EVENT_CATALOG.md Section 6) available to the domains and actors that depend on them.
- **Boundary.** Does not determine which driver an order is assigned to — that decision belongs exclusively to Dispatch the domain (ADR-002); the application capability only requests and observes the outcome.

### Driver Management

- **Purpose.** Translate a driver's declaration of readiness into Driver Management, in service of UC-003 and UC-004.
- **Responsibility.** Sequence the Declare Availability command (DOMAIN_MODEL.md Section 9) and make the resulting DriverAvailabilityChanged event (EVENT_CATALOG.md Section 7) available to Dispatch and any other domain that depends on it.
- **Boundary.** Does not decide a driver's availability state — that declaration belongs exclusively to the driver, recorded by Driver Management the domain (ADR-005, ADR-009); the application capability only coordinates the declaration.

### Passenger Experience

- **Purpose.** Represent a passenger's or corporate customer's ongoing relationship with the platform, in service of UC-002, and as the originating context for UC-001 and UC-009.
- **Responsibility.** Coordinate the representation of the passenger or corporate customer, including any Personal Client relationship (DOMAIN_MODEL.md Section 5), for the domains and use cases that depend on it.
- **Boundary.** Does not own the resulting order once submitted — that belongs to Order Management (ADR-019); the application capability only coordinates the passenger's or corporate customer's own representation.

### Administration

- **Purpose.** Support UC-010, the platform administrator's oversight and governance goal.
- **Responsibility.** Coordinate the administrator's access to the platform governance and participant standing information Administration already owns (ADR-018).
- **Boundary.** Does not decide participant standing itself — that belongs to Administration the domain (ADR-005, ADR-009); no use case beyond UC-010 currently justifies further application-level responsibility here.

### Analytics

- **Purpose.** No use case in USE_CASE_CATALOG.md currently attributes a goal to Analytics; its application-level responsibility is limited to what DOMAIN_MODEL.md Section 7 already establishes.
- **Responsibility.** Coordinate Analytics's consumption of events and information obtained from other domains (DOMAIN_MODEL.md Section 7; EVENT_CATALOG.md Section 9), without owning that information.
- **Boundary.** Does not decide what an insight means to another domain — that remains internal to Analytics and to the domain being observed (ADR-005, ADR-019); no application-level orchestration beyond event consumption is currently justified.

### Notifications

- **Purpose.** No use case in USE_CASE_CATALOG.md currently attributes a goal to Notifications; its application-level responsibility is limited to what DOMAIN_MODEL.md Section 7 already establishes.
- **Responsibility.** Coordinate Notifications's consumption of events from other domains (DOMAIN_MODEL.md Section 7; EVENT_CATALOG.md Section 9) in order to inform participants, without owning the underlying information that triggers a notification.
- **Boundary.** Does not decide the content or delivery mechanism of a notification beyond what Notifications the domain determines (ADR-005, ADR-009, ADR-020 for any external delivery channel); no application-level orchestration beyond event consumption is currently justified.

### Payments (Justification Noted)

No application-level orchestration responsibility is currently justified for Payments beyond the Record Payment command already defined in DOMAIN_MODEL.md Section 9. USE_CASE_CATALOG.md's "Areas Not Catalogued" explicitly notes that no approved document yet attributes this command to a specific actor's goal. This document does not invent one; a Purpose, Responsibility, and Boundary for Payments awaits that use case being established.

## 6. Commands

The following are the application-level intentions already established in DOMAIN_MODEL.md Section 9; no new command is introduced. Each represents an intention only — no payload, schema, or mechanism is defined:

- **Submit Order** — toward Order Management, in service of UC-001, UC-009.
- **Assign Order** — toward Dispatch, in service of UC-007.
- **Accept Assignment** — toward Dispatch, in service of UC-006.
- **Cancel Order** — toward Order Management, in service of UC-001, UC-009.
- **Declare Availability** — toward Driver Management, in service of UC-004.
- **Record Payment** — toward Payments; no use case currently attributes this to a specific actor's goal, per Section 5.

## 7. Queries

The following are the application-level information requests already established in DOMAIN_MODEL.md Section 10; no new query is introduced, no API, database, or read model is defined:

- **Retrieve Order Status** — from Order Management.
- **Retrieve Assignment** — from Dispatch.
- **Retrieve Driver Availability** — from Driver Management.
- **Retrieve Payment Record** — from Payments.
- **Retrieve Insight** — from Analytics.

## 8. Domain Interaction

The application layer invokes domain capabilities only through the mechanisms already established in SYSTEM_ARCHITECTURE.md Section 8: synchronous interaction for a command or query requiring an immediate response (ADR-004), and reaction to events for facts already recorded by another domain (ADR-003). The domain being invoked owns every invariant that governs the outcome (DOMAIN_MODEL.md Section 11; ADR-005, ADR-009); the application layer contains no business rule of its own and cannot decide an outcome a domain has not itself decided.

## 9. Event Interaction

The application layer reacts to the domain events already catalogued in EVENT_CATALOG.md to coordinate a use case that depends on a fact recorded by a domain other than the one an actor directly addressed — for example, reacting to OrderAssigned to make the outcome known to the driver side of UC-005. No application-level event distinct from the domain events already catalogued is currently justified; the application layer coordinates and reacts to domain events but does not introduce a new event category, consistent with events belonging to domain ownership (EVENT_CATALOG.md Section 2). No event infrastructure — queue, topic, or broker — is described here.

## 10. Transaction Boundaries

Conceptually, a boundary of consistency aligns with a single domain's aggregate boundary (DOMAIN_MODEL.md Section 4), since only the domain that owns an aggregate may change it (ADR-005). A use case that spans more than one domain, such as UC-001, does not achieve consistency across those domains within one boundary; it achieves it only through the event mechanism already established in ADR-003, which introduces eventual consistency by design. The application layer does not extend a single domain's consistency boundary across domains, and does not define any technical transaction mechanism.

## 11. Error Handling Philosophy

An outcome that would violate a domain's own invariant (DOMAIN_MODEL.md Section 11) is rejected by the domain that owns that invariant, never by the application layer, consistent with Domain Decides Business Meaning in Section 2. The application layer's role is limited to relaying that rejection back to the use case that requested it, without reinterpreting, overriding, or supplementing the reason for it, since only the owning domain has authority over its own meaning (ADR-005). No specific mechanism, code, or signal is defined here.

## 12. Authorization Responsibility

Authorization for a given action is decided at the boundary of the domain whose information or decision is affected, consistent with the Security by Design and Least Privilege principles in ADR-011. The application layer, being a coordination-only responsibility (Section 2), has no independent authorization authority of its own: it never grants, overrides, or supplements a domain's own authorization decision. No authentication mechanism, protocol, or identity source is defined here, consistent with SYSTEM_ARCHITECTURE.md Section 14.

## 13. Traceability

| Section | Constitution | ADR | System Architecture | Domain Model | Event Catalog | Use Case Catalog |
| --- | --- | --- | --- | --- | --- | --- |
| 1. Purpose | Section 4 | ADR-001 | Section 1 | Section 1 | Section 1 | Section 1 |
| 2. Application Architecture Principles | Section 4 | ADR-001, ADR-002, ADR-003, ADR-004, ADR-005, ADR-009 | Section 2 | Section 11 | — | — |
| 3. Application Layer Responsibilities | — | ADR-002, ADR-005, ADR-009 | Section 5 | Sections 9–10 | — | — |
| 4. Use Case Orchestration | — | ADR-002, ADR-003, ADR-004 | Sections 8–9 | Sections 4, 7 | Sections 5–7 | UC-001 through UC-010 |
| 5. Application Capabilities | — | ADR-005, ADR-009, ADR-018, ADR-019, ADR-020 | Section 6 | Sections 3–4, 7 | Sections 5–7 | UC-001 through UC-010 |
| 6. Commands | — | ADR-004 | Section 8 | Section 9 | — | UC-001, UC-004, UC-006, UC-007, UC-009 |
| 7. Queries | — | ADR-004 | Section 8 | Section 10 | — | — |
| 8. Domain Interaction | — | ADR-003, ADR-004, ADR-005, ADR-009 | Section 8 | Section 11 | — | — |
| 9. Event Interaction | — | ADR-003 | Sections 8–9 | Sections 4, 8 | Sections 2, 9 | — |
| 10. Transaction Boundaries | — | ADR-003, ADR-005 | Section 8 | Sections 4, 11 | — | — |
| 11. Error Handling Philosophy | — | ADR-005 | — | Section 11 | — | — |
| 12. Authorization Responsibility | Section 3 (Trust) | ADR-011 | Section 14 | — | — | — |

Where this document is silent — including on Analytics, Notifications, and Payments having no attributed use case — no lower-priority document may fill that silence by invention; resolution requires USE_CASE_CATALOG.md to be extended first, per the authority order established in PROJECT_CONSTITUTION.md Section 5.

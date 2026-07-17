# PIOS Use Case Catalog

Status: Draft — Derived from Approved Foundation. This document describes who interacts with PIOS, what goals they achieve, and what a successful outcome means for each of them. It is a business behavior document, not a UI specification, an API specification, a database design, a workflow engine, a technical sequence diagram, or an implementation document.

This document is derived exclusively from [PROJECT_CONSTITUTION.md](PROJECT_CONSTITUTION.md), the ADR Foundation, [PRODUCT_FOUNDATION.md](PRODUCT_FOUNDATION.md), [SYSTEM_ARCHITECTURE.md](SYSTEM_ARCHITECTURE.md), [DOMAIN_MODEL.md](DOMAIN_MODEL.md), and [EVENT_CATALOG.md](EVENT_CATALOG.md), in that order of authority. Every use case here maps to an existing domain, and every event it references already exists in EVENT_CATALOG.md; no new domain, aggregate, or event is introduced.

---

## 1. Purpose

A use case connects an actor's goal to the business capabilities PIOS already commits to elsewhere in this documentation set. It exists so that the domains, aggregates, and events already established (DOMAIN_MODEL.md, EVENT_CATALOG.md) can be understood in terms of who they serve and why, before any API, database, or interface is designed against them. Where no approved document currently supports a goal an actor might plausibly have, this catalog says so explicitly rather than inventing the missing capability.

## 2. Actors

Each actor below is defined only as PRODUCT_FOUNDATION.md Sections 6–7 and SYSTEM_ARCHITECTURE.md Section 3 already define it. No permission, role hierarchy, or access matrix is described.

- **Passenger.** *Purpose:* originates or receives individual transportation demand. *Relationship with platform:* represented and served by Passenger Experience (ADR-018); may hold a recognized, recurring relationship with a driver as a Personal Client.
- **Driver.** *Purpose:* provides transportation and controls their own availability and participation. *Relationship with platform:* represented by Driver Management (ADR-018); the platform's primary stakeholder (PROJECT_CONSTITUTION.md Section 3).
- **Dispatcher / Operational Role.** *Purpose:* represents the historical work-allocation role. *Relationship with platform:* this responsibility is carried by the platform's own Dispatch capability, not by this actor directly; where it persists, it operates at the fleet level rather than as the allocator of record (PRODUCT_FOUNDATION.md Section 7).
- **Fleet.** *Purpose:* coordinates and represents multiple drivers and vehicles as a single organizational participant. *Relationship with platform:* recognized as an ecosystem participant (PRODUCT_FOUNDATION.md Section 6), but no domain currently owns fleet-related information (DOMAIN_MODEL.md Section 5).
- **Corporate Customer.** *Purpose:* originates aggregated transportation demand on behalf of its own members or clients. *Relationship with platform:* represented by Passenger Experience alongside individual passengers (ADR-018).
- **Platform Administrator.** *Purpose:* oversees the platform's operation and governs participants' standing within it. *Relationship with platform:* supported by the Administration domain (ADR-018).
- **Partner.** *Purpose:* extends the platform's ecosystem reach without being a party to individual transportation demand. *Relationship with platform:* recognized as an ecosystem participant (PRODUCT_FOUNDATION.md Section 6); no concrete interaction is defined by any approved document — SYSTEM_ARCHITECTURE.md Section 4 marks specific future extension points as "Architecture Decision Required."
- **External Service.** *Purpose:* a system outside PIOS that the platform depends on or interacts with. *Relationship with platform:* governed as an integration boundary under ADR-020, not as a goal-driven actor; see Section 4 for why it has no discrete use case.

## 3. Use Case Principles

- **Goal-Oriented.** Every use case exists because an actor has a goal that a PIOS capability already serves; a use case is never written to describe a mechanism in search of a purpose.
- **Actor-Driven.** Every use case has exactly one primary actor, drawn only from Section 2.
- **Technology-Independent.** A use case describes what is achieved, never how — no interface, protocol, or data structure is implied.
- **Business Outcome-Focused.** A use case is judged complete by the business fact it results in, expressed as the domain events already catalogued in EVENT_CATALOG.md, not by any technical completion signal.

## 4. Use Case Catalog

### UC-001: Request Transportation

- **Primary Actor:** Passenger
- **Goal:** Obtain transportation for a journey.
- **Description:** A passenger submits a request for transportation. PRODUCT_FOUNDATION.md Section 10 defines an Order as a request for transportation from any source; Passenger Experience represents the passenger's interaction with the platform throughout (ADR-018). The passenger's own confirmation that a proposed order will proceed is part of Acceptance, which DOMAIN_MODEL.md Section 6 defines as available to a passenger as well as a driver.
- **Preconditions:** The passenger is represented within Passenger Experience.
- **Main Success Outcome:** The order is submitted, connected to a driver through an accepted assignment, and completed.
- **Alternative Outcomes:** The order is cancelled before completion.
- **Related Domain:** Order Management.
- **Related Aggregate:** Order.
- **Related Events:** OrderSubmitted, OrderCompleted, OrderCancelled.

### UC-002: Maintain Platform Relationship

- **Primary Actor:** Passenger
- **Goal:** Maintain an ongoing relationship with the platform, including any recurring Personal Client relationship with a driver.
- **Description:** Passenger Experience represents the passenger and their interaction with the platform on an ongoing basis (ADR-018). A passenger may hold a recognized, recurring relationship with a driver as a Personal Client (PRODUCT_FOUNDATION.md Section 10; DOMAIN_MODEL.md Section 5).
- **Preconditions:** The passenger is represented within Passenger Experience.
- **Main Success Outcome:** The passenger's representation, and any Personal Client relationship they hold, remains current.
- **Alternative Outcomes:** Not applicable — no discrete lifecycle transition is catalogued for this ongoing responsibility.
- **Related Domain:** Passenger Experience.
- **Related Aggregate:** None — Personal Client Relationship is a conceptual entity, not a ratified aggregate (DOMAIN_MODEL.md Section 5).
- **Related Events:** None currently catalogued.

### UC-003: Participate in the Platform

- **Primary Actor:** Driver
- **Goal:** Be represented within the platform with standing and participation.
- **Description:** Driver Management represents drivers within the platform, including their standing, availability, and participation (ADR-018).
- **Preconditions:** None specified beyond the driver's existence as a represented entity within Driver Management.
- **Main Success Outcome:** The driver's standing and participation are maintained within Driver Management.
- **Alternative Outcomes:** Not applicable — availability specifically is addressed separately in UC-004.
- **Related Domain:** Driver Management.
- **Related Aggregate:** Driver.
- **Related Events:** None currently catalogued for standing or participation specifically.

### UC-004: Declare Availability

- **Primary Actor:** Driver
- **Goal:** Declare readiness to receive an assignment.
- **Description:** A driver declares their own availability, per the Availability value object and the Declare Availability command (DOMAIN_MODEL.md Sections 6 and 9).
- **Preconditions:** The driver is represented within Driver Management.
- **Main Success Outcome:** The driver's availability state changes.
- **Alternative Outcomes:** A driver may declare either available or unavailable; the invariant that a driver has exactly one current availability state at any time applies either way (DOMAIN_MODEL.md Section 11).
- **Related Domain:** Driver Management.
- **Related Aggregate:** Driver.
- **Related Events:** DriverAvailabilityChanged.

### UC-005: Receive Assignment

- **Primary Actor:** Driver
- **Goal:** Be connected to a submitted order.
- **Description:** Dispatch allocates a submitted order to the driver, exclusively as Dispatch's own decision (ADR-002).
- **Preconditions:** An order has been submitted; the driver's current availability state is available.
- **Main Success Outcome:** The driver is connected to the order.
- **Alternative Outcomes:** Not applicable at this use case's level — the specific criteria Dispatch uses are explicitly excluded from every level of this documentation (ADR-002).
- **Related Domain:** Dispatch.
- **Related Aggregate:** Assignment.
- **Related Events:** OrderAssigned.

### UC-006: Accept Assignment

- **Primary Actor:** Driver
- **Goal:** Confirm that a proposed assignment will proceed.
- **Description:** The driver confirms a proposed assignment, per the Acceptance value object and the Accept Assignment command (DOMAIN_MODEL.md Sections 6 and 9).
- **Preconditions:** An assignment has been made.
- **Main Success Outcome:** The assignment is confirmed.
- **Alternative Outcomes:** The order or assignment is cancelled instead of accepted.
- **Related Domain:** Dispatch.
- **Related Aggregate:** Assignment.
- **Related Events:** AssignmentAccepted, OrderCancelled.

### UC-007: Determine Assignment

- **Primary Actor:** Dispatch (platform capability)
- **Goal:** Connect a submitted order to a driver.
- **Description:** The Dispatch domain service determines, within its own boundary, which driver an order is assigned to (DOMAIN_MODEL.md Section 7); this is Dispatch's exclusive responsibility (ADR-002).
- **Preconditions:** An order has been submitted; at least one driver's availability state is available.
- **Main Success Outcome:** An assignment is created.
- **Alternative Outcomes:** Not applicable — the specific criteria for determining an assignment are explicitly excluded from this and every other level of documentation (ADR-002).
- **Related Domain:** Dispatch.
- **Related Aggregate:** Assignment.
- **Related Events:** OrderAssigned.

### UC-008: Maintain Fair Allocation

- **Primary Actor:** Dispatch (platform capability)
- **Goal:** Keep the assignment decision fair, consistent with the Fair Dispatch product principle.
- **Description:** Because Dispatch owns the assignment decision exclusively (ADR-002), the platform's fairness commitment (PROJECT_CONSTITUTION.md Section 3; PRODUCT_FOUNDATION.md Section 5) can be evaluated and evolved as one coherent capability rather than scattered across multiple capabilities.
- **Preconditions:** Dispatch is the sole owner of the assignment decision.
- **Main Success Outcome:** Assignment decisions continue to be made exclusively and accountably by Dispatch.
- **Alternative Outcomes:** Not applicable — the specific fairness criteria are explicitly excluded from this and every other level of documentation (ADR-002).
- **Related Domain:** Dispatch.
- **Related Aggregate:** Assignment.
- **Related Events:** OrderAssigned, as the observable outcome of this capability.

### UC-009: Originate Corporate Demand

- **Primary Actor:** Corporate Customer
- **Goal:** Originate transportation demand on behalf of the corporate customer's own members or clients.
- **Description:** A corporate customer originates an order categorized as a Corporate Order (PRODUCT_FOUNDATION.md Section 10; DOMAIN_MODEL.md Section 6, Order Origin).
- **Preconditions:** The corporate customer is represented within Passenger Experience.
- **Main Success Outcome:** The order is submitted with Corporate Order as its origin, connected to a driver through an accepted assignment, and completed.
- **Alternative Outcomes:** The order is cancelled before completion.
- **Related Domain:** Order Management (Passenger Experience represents the corporate customer; Order Management owns the resulting order).
- **Related Aggregate:** Order.
- **Related Events:** OrderSubmitted, OrderCompleted, OrderCancelled.

### UC-010: Oversee Platform Operation

- **Primary Actor:** Platform Administrator
- **Goal:** Oversee and govern the platform ecosystem.
- **Description:** Administration supports platform administrators in overseeing and governing the ecosystem (ADR-018).
- **Preconditions:** The administrator is represented within Administration.
- **Main Success Outcome:** Platform governance and participant standing are maintained within Administration.
- **Alternative Outcomes:** Not applicable — no discrete lifecycle transition is currently catalogued for this responsibility.
- **Related Domain:** Administration.
- **Related Aggregate:** None — Administration has no ratified aggregate in DOMAIN_MODEL.md.
- **Related Events:** None currently catalogued.

### Areas Not Catalogued

- **Fleet.** DOMAIN_MODEL.md Section 5 explicitly marks Fleet as **Domain Decision Required**: no domain currently owns fleet-related information. A Fleet use case would require an aggregate or domain ownership that does not yet exist, so none is catalogued here.
- **Partner.** No domain, aggregate, or event supports a concrete Partner interaction. SYSTEM_ARCHITECTURE.md Section 4 marks specific future extension points as "Architecture Decision Required." No use case is catalogued until that decision exists.
- **External Service.** External Services are integration boundaries governed by ADR-020, not goal-driven participants; PIOS depends on them rather than serving them. No use case is catalogued for this actor, consistent with its purpose in Section 2.
- **Login, registration, password reset, payment workflow, map workflow, notification workflow.** None of these is supported by DOMAIN_MODEL.md or EVENT_CATALOG.md as a discrete business goal; none is catalogued here. Payments is a ratified domain (ADR-018) and Payment Record a catalogued entity (DOMAIN_MODEL.md Section 5), but no Payments-specific command, event, or actor goal is currently documented beyond the Record Payment command already listed in DOMAIN_MODEL.md Section 9, which no approved document yet attributes to a specific actor's goal.

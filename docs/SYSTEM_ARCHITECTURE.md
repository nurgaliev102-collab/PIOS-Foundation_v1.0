# PIOS System Architecture

Status: Draft — Derived from Approved Foundation. This document describes how the PIOS system is organized: its major architectural components, their responsibilities, and how they relate to one another. It is implementation-independent by design and contains no technology, framework, database, API, or deployment decision.

This document is derived exclusively from [PROJECT_CONSTITUTION.md](PROJECT_CONSTITUTION.md), the ADR Foundation, and [PRODUCT_FOUNDATION.md](PRODUCT_FOUNDATION.md), in that order of authority. Where a section would require an architectural decision that no existing document makes, it is marked **Architecture Decision Required** rather than resolved by invention, consistent with the Documentation Driven Development discipline in [ADR-006](ADR/ADR-006-Documentation-Driven-Development.md).

---

## 1. Architecture Vision

PIOS is architected as a modular, domain-oriented system of loosely coupled, independently evolvable components with explicit boundaries and contracts, in service of the platform's purpose: connecting independent drivers with transportation demand from multiple sources through a mechanism that remains fair, transparent, and trustworthy as the platform grows into a broader ecosystem.

## 2. Architectural Principles

- **Architecture First and Documentation First.** Structural decisions are made and recorded before implementation, and no decision governs the system until it is written down.
- **Single Source of Truth.** Each architectural fact has exactly one authoritative document; this document references rather than restates them.
- **Boundary-First Composition.** A component's boundary and contract are defined before its internals, and no component assumes another's internal implementation.
- **Exclusive Ownership.** Responsibility for a decision or a piece of data belongs to exactly one component, never shared.
- **Consistency.** Components that expose the same kind of interaction are shaped the same way.
- **Security by Design, Least Privilege, and Defense in Depth.** Every boundary enforces its own protection; no boundary is trusted by default.
- **Evolution Over Replacement.** The architecture is expected to change through governed, incremental decisions, not through undocumented drift or wholesale rewrites.

## 3. System Context

PIOS interacts with the following external actors, each carrying the responsibility described below and no more; no interaction sequence or workflow is implied.

- **Driver.** Provides transportation and controls their own availability and participation in the platform.
- **Passenger.** Originates or receives individual transportation demand.
- **Dispatcher.** Represents the historical work-allocation role; within PIOS this responsibility is carried by the platform's own Dispatch capability rather than by this actor directly, though an operational participant may still act in this role at the fleet level.
- **Fleet.** Coordinates and represents multiple drivers as a single organizational actor.
- **Corporate Customer.** Originates aggregated transportation demand on behalf of others.
- **Administrator.** Oversees the platform's operation and governs participants' standing within it.
- **Partner.** Extends the platform's ecosystem reach without being a party to individual transportation demand.
- **External Services.** Systems outside PIOS that the platform depends on or interacts with, without being part of the platform itself.

## 4. System Boundaries

**Inside.** Connecting independent drivers with transportation demand from any source; the mechanisms that keep that connection fair, transparent, and explainable; support for durable relationships between drivers and recurring passengers or customers; and the supporting capabilities of administration, analytics, and notification needed to operate the platform.

**Outside.** Ownership, maintenance, or insurance of vehicles; licensing or certification of drivers, which remains a regulatory responsibility; the underlying financial and banking infrastructure that settles payments; physical road and traffic infrastructure; and the physical act of transportation itself, performed by the driver.

**Future Extension Points.** The architecture is intended to accommodate ecosystem growth beyond the initial taxi use case. The general mechanism for that growth is the governed evolution process defined in ADR-015 (new capabilities and interfaces introduced and versioned without breaking existing ones) operating within the domain boundary rule of ADR-009. **Architecture Decision Required:** no document yet identifies specific future extension points beyond this general mechanism; that is left to future domain-level and architecture-evolution decisions.

## 5. High-Level Architecture

PIOS is organized as a set of independently owned architectural capabilities, each with an explicit boundary, rather than as a stack of technical layers. A capability communicates with others only through its declared interaction style or the events it publishes, owns its own data exclusively, and is releasable independently of the others.

**Architecture Decision Required:** no ADR specifies an internal layering scheme (such as presentation, application, domain, integration, or infrastructure layers) for any capability. This document does not assume one. The capability-oriented organization described above is what is established; any further internal structure is left to future architectural decisions.

## 6. Core Architectural Components

The following components are architecturally established at this stage:

- **Dispatch.** An independent architectural capability that owns the assignment decision connecting a transportation request to a driver exclusively. No other component makes or overrides that decision. The specific assignment logic is explicitly not an architectural concern.

The following are candidate components, drawn from the product-level capability groupings in Product Foundation, that have not yet been ratified as architectural components by an ADR: Order Management, Driver Management, Passenger Experience, Payments, Administration, Analytics, and Notifications. **Architecture Decision Required:** formal definition of these as architectural components, with explicit boundaries and responsibilities, is pending; see Section 7.

## 7. Bounded Context Overview

**Architecture Decision Required.** No ADR enumerates the bounded contexts (domains) of the PIOS system. ADR-009 establishes the rule that will govern any bounded context once identified — an explicit responsibility, a boundary crossed only through declared interaction or published events, and no shared internal logic or data with another context — but it deliberately does not name the contexts themselves. The candidate components in Section 6 may inform that future decision but do not themselves constitute it.

## 8. Communication Model

- **Synchronous communication** is used when an immediate answer is required. It follows the resource-oriented, versioned, consistent interaction style established for direct interaction between components.
- **Asynchronous communication** is used to make a fact that has already occurred known to other components without direct coupling.
- **Events** represent something significant that has already happened. Two kinds are recognized: domain events, of primarily internal significance to the component where they occur, and business events, which other components may rely on.
- **Commands and Queries.** No document distinguishes a separate command mechanism from the synchronous interaction style; both a request for information and a request that a component act within its own boundary use the same synchronous, resource-oriented interaction style, differentiated only by intent, not by mechanism. Events are explicitly not used to issue commands to other components.

## 9. Order Flow Overview

At a conceptual level, and without defining any algorithm or sequence: an order is submitted representing transportation demand; the Dispatch capability determines its assignment to a driver, as established in Section 6; the assignment is subject to acceptance; a ride follows from an accepted assignment; and an order or assignment may be cancelled before it is completed. Each of these transitions is a change of state and, where it has significance beyond the component in which it occurs, is made known to other components as a business event rather than through direct coupling. The specific criteria used to determine an assignment, and the detailed lifecycle of an order's status, are explicitly not architectural concerns.

## 10. Data Ownership

Each architectural component owns the data relevant to its own responsibility exclusively; no component's data is jointly owned, and a component that needs information it does not own obtains it only through another component's declared interaction style or the events that component publishes, never through direct access. **Architecture Decision Required:** the definitive mapping of specific data to a specific owning component depends on the bounded context decision flagged in Section 7 and is not made here.

## 11. Integration Boundaries

The platform depends on external systems for concerns that are explicitly outside its boundary, per Section 4:

- **Payment.** The platform's own record of payment-related information is an internal responsibility; settlement of that payment through banking or financial infrastructure is external. **Architecture Decision Required:** the specific integration mechanism with that external infrastructure is not defined.
- **Location and Routing.** Physical road, traffic, and routing infrastructure is explicitly outside the platform. **Architecture Decision Required:** whether and how the platform consumes externally provided location or routing information is not defined by any existing document.
- **Notifications.** Informing participants is an internal platform responsibility. **Architecture Decision Required:** whether that responsibility depends on an external delivery channel, and how, is not defined.
- **Identity.** Driver licensing and certification remain the responsibility of external regulatory authorities. **Architecture Decision Required:** whether or how the platform integrates with an external authority or identity source for this purpose is not defined; ADR-011 requires every boundary to enforce authentication and authorization but does not name an identity source.

## 12. Scalability Principles

The architecture supports growth in participants, demand, and geography, consistent with the Scalability principle in Product Foundation Section 11, by keeping components independently owned (ADR-009) and independently deployable (ADR-014), so that a component under greater load can be addressed without requiring every other component to change. No infrastructure mechanism for achieving this is defined here.

## 13. Reliability Principles

Reliability is supported architecturally through mandatory reversibility of every release (ADR-014), so that a change that misbehaves can be undone, and through observability built into every component from the outset (ADR-012), so that misbehavior can be diagnosed. The independence of components (ADR-009) is intended to keep a problem within the component where it originates rather than assuming it will remain contained by default.

## 14. Security Principles

Every boundary between components, whether a synchronous interaction or a published event, enforces its own authentication and authorization; no component trusts a request solely because it appears to originate from within the system. Access follows least privilege, and no single boundary is treated as sufficient protection on its own, consistent with ADR-011. No authentication mechanism, protocol, or identity technology is defined here.

## 15. Observability Principles

Every component provides its own logs, metrics, and tracing as an intrinsic part of its design, not as work added afterward, and a single interaction that crosses component boundaries can be followed across them, consistent with ADR-012. No specific tooling is defined here.

## 16. Evolution Strategy

The architecture evolves only through a new ADR that explicitly supersedes an existing decision, with the superseded decision retained rather than deleted, consistent with Constitution Section 7 and ADR-015. Interfaces and events evolve incrementally: a new version is introduced alongside the one it replaces, and a deprecated version remains available for a stated migration window before removal, consistent with ADR-010 and ADR-015. This is the same mechanism referenced as the platform's general growth path in Section 4.

## 17. Traceability

| Section | Constitution | ADR | Product Foundation |
| --- | --- | --- | --- |
| 1. Architecture Vision | Sections 1–2 | ADR-001 | Sections 1–2 |
| 2. Architectural Principles | Section 4 | ADR-001, ADR-004, ADR-005, ADR-009, ADR-011 | — |
| 3. System Context | — | — | Sections 6–7 |
| 4. System Boundaries | Section 3 (Extensibility) | ADR-009, ADR-015 | Section 8 |
| 5. High-Level Architecture | — | ADR-001, ADR-009 | Section 9 |
| 6. Core Architectural Components | — | ADR-002 | Section 9 |
| 7. Bounded Context Overview | — | ADR-009 | Section 9 |
| 8. Communication Model | — | ADR-003, ADR-004 | — |
| 9. Order Flow Overview | — | ADR-002, ADR-003 | Section 10 |
| 10. Data Ownership | — | ADR-005, ADR-009 | — |
| 11. Integration Boundaries | — | ADR-011 | Sections 8, 14 |
| 12. Scalability Principles | Section 14 (Scalability) | ADR-009, ADR-014 | Section 11 |
| 13. Reliability Principles | Section 3 (Reliability) | ADR-012, ADR-014 | Section 11 |
| 14. Security Principles | Section 3 (Trust) | ADR-011 | — |
| 15. Observability Principles | Section 14 (Traceability) | ADR-012 | — |
| 16. Evolution Strategy | Section 7 (ADR Policy) | ADR-010, ADR-015 | Section 2 (Vision) |

Where this document is silent or marks a section **Architecture Decision Required**, no lower-priority document may resolve it; resolution requires a new ADR, per the authority order established in Constitution Section 5.

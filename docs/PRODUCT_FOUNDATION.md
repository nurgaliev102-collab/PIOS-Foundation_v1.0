# PIOS Product Foundation

Status: Foundational. This document describes the product domain of PIOS: what the platform is, who participates in it, why it exists, and where its architectural boundaries originate. It is not a product requirements document, not a domain model, and not a specification of business rules. It derives from and must remain consistent with [PROJECT_CONSTITUTION.md](PROJECT_CONSTITUTION.md) and the accepted [ADR Foundation](README.md#adr).

---

## 1. Purpose

PIOS exists to give independent drivers and the people and organizations who need transportation a shared platform they can each rely on. The problem it addresses is not the mechanics of moving a person from one place to another — drivers already do that — but the absence, in much of the market, of a trustworthy, transparent mechanism that connects independent supply with demand from multiple sources, without concentrating unaccountable control over that connection in a single intermediary.

## 2. Vision

PIOS is conceived as more than a single-purpose taxi-hailing application. Its long-term vision is to become an extensible ecosystem that independent drivers, passengers, fleets, and organizations can build durable relationships on, capable of growing to support forms of demand and participation not yet enumerated, without abandoning the trust its early participants were given.

## 3. Market Context

The transportation-for-hire market in which PIOS operates is served today by several overlapping models:

- **Independent drivers**, who own or control their own vehicle and their own working time, and who seek demand without necessarily surrendering control over how that demand is allocated to them.
- **The dispatcher model**, in which a person or small operation manually allocates incoming requests to available drivers, typically within a local or regional scope.
- **The platform model**, in which a software platform aggregates demand and allocates it to drivers at scale, typically across a broad geography.
- **Hybrid models**, in which fleets, dispatch operations, and platform-mediated demand coexist, and a driver's work may originate from more than one of these sources at once.

Common pain points across these models include: opacity in how work is allocated to drivers; drivers having little visibility into, or influence over, the criteria used to assign them work; fragmentation of tools and relationships across multiple uncoordinated systems; and a structural tendency for the party that controls dispatch to accumulate disproportionate leverage over the driver's livelihood. The broader industry has been trending toward platformization of demand aggregation, increasing driver reliance on software-mediated work allocation, and growing interest — from drivers in particular — in preserving durable relationships with recurring clients rather than treating every interaction as anonymous.

## 4. Core Problem

The core problem PIOS addresses is the absence of a platform that allocates work to independent drivers in a way that is fair, explainable, and transparent to the people it affects, while still being capable of aggregating demand from multiple, heterogeneous sources and supporting durable relationships between drivers and the people they serve. This is a problem of trust and structural accountability in how work is allocated, not a problem of routing, payment processing, or any other specific mechanism. This document does not propose how the problem is solved; it defines the problem and the domain within which a solution is built.

## 5. Product Philosophy

- **Transparency.** Participants can understand, in terms meaningful to them, how the platform behaves toward them.
- **Fairness.** The mechanisms that allocate work and opportunity are designed and evaluated for fairness, not merely for throughput or efficiency.
- **Driver-First.** Independent drivers are treated as the platform's primary stakeholders, not as undifferentiated capacity to be consumed by demand.
- **Platform Neutrality.** The platform does not favor one demand source, participant, or relationship over another except where a documented, explainable reason justifies it.
- **Trust.** Every product decision is weighed by whether it strengthens or weakens the confidence participants place in the platform.

## 6. Ecosystem

PIOS involves the following categories of participant:

- **Driver.** An independent individual who provides transportation using a vehicle they own or control, and who is the platform's primary stakeholder.
- **Passenger.** A person who requests or receives transportation through the platform.
- **Dispatcher.** A role, historically performed by a person, concerned with allocating work to drivers; within PIOS this role is superseded by the platform's own dispatch capability, described in Section 9, though operational participants may still interact with that capability on behalf of a fleet.
- **Fleet.** An organization that coordinates multiple drivers and vehicles, and that participates in the ecosystem as a distinct stakeholder from any single driver within it.
- **Corporate Customer.** An organization that originates transportation demand on behalf of its own members or clients, rather than as an individual passenger.
- **Platform Administrator.** A participant responsible for the ongoing operation, oversight, and governance of the platform itself.
- **Partner.** An external organization that extends or integrates with the platform's ecosystem without being one of its core participants.
- **External Service.** A capability or system outside PIOS that the platform relies on or interacts with, without being part of the platform itself.

## 7. Roles

Each participant category carries a high-level responsibility within the ecosystem:

- **Driver** — provides transportation and controls their own availability and participation.
- **Passenger** — originates or receives individual transportation demand.
- **Dispatcher** — represents the historical allocation role that the platform's dispatch capability now performs; where it persists as a human role, it operates at the fleet level, not as the allocator of record.
- **Fleet** — coordinates and represents multiple drivers as a single organizational participant.
- **Corporate Customer** — originates aggregated transportation demand on behalf of others.
- **Platform Administrator** — oversees the platform's operation and governs its participants' standing within it.
- **Partner** — extends the ecosystem's reach without being a party to individual transportation demand.
- **External Service** — supplies a capability the platform depends on from outside its own boundary.

This section describes responsibility, not process; no workflow, sequence, or interaction between roles is defined here.

## 8. Product Boundaries

**Within PIOS:** connecting independent drivers with transportation demand regardless of its source; maintaining the mechanisms that make that connection fair, transparent, and explainable; supporting durable relationships between drivers and recurring passengers or customers; and the supporting capabilities — administration, analytics, and notification — needed to operate the platform responsibly.

**Outside PIOS:** the ownership, maintenance, or insurance of vehicles; the licensing or certification of drivers, which remains the responsibility of the relevant regulatory authority; the underlying financial and banking infrastructure that settles payments, as distinct from the platform's own handling of payment-related information; physical road and traffic infrastructure; and the act of transportation itself, which is performed by the driver, not the platform.

## 9. Product Capabilities

PIOS is organized around the following high-level capability groups, described here at the level of what each is responsible for, not how it is implemented:

- **Order Management.** Receiving and tracking transportation demand from any source through its lifecycle.
- **Dispatch.** Allocating transportation demand to drivers as a platform-owned capability, consistent with ADR-002.
- **Driver Management.** Representing drivers within the platform and their standing, availability, and participation.
- **Passenger Experience.** Representing passengers and corporate customers and their interaction with the platform.
- **Payments.** Handling the platform's own record of payment-related information associated with transportation demand.
- **Administration.** Supporting platform administrators in overseeing and governing the ecosystem.
- **Analytics.** Providing insight into how the platform and its ecosystem are behaving and performing.
- **Notifications.** Informing participants of information relevant to them.

## 10. Product Terminology

- **Driver.** An independent individual who provides transportation through the platform.
- **Passenger.** A person who requests or receives transportation through the platform.
- **Ride.** A single instance of transportation provided by a driver to a passenger.
- **Order.** A request for transportation, from any source, before or as it becomes a ride.
- **Dispatch.** The platform capability responsible for allocating orders to drivers.
- **Assignment.** The outcome of dispatch by which a specific order is allocated to a specific driver.
- **Availability.** A driver's own declaration of their readiness to receive an assignment.
- **Acceptance.** A driver's or passenger's confirmation that a proposed assignment or order will proceed.
- **Cancellation.** The termination of an order or assignment before it is completed.
- **Personal Client.** A passenger or corporate customer with whom a driver has a recognized, recurring relationship.
- **Platform Order.** An order that originates from platform-aggregated demand rather than from a driver's own personal client relationship.
- **Corporate Order.** An order originated by a corporate customer on behalf of its own members or clients.
- **Status.** The current state of an order, assignment, or ride within its lifecycle.

These are canonical, platform-wide definitions. They do not constitute a domain model, and no attribute, relationship, or data structure is implied by their inclusion here.

## 11. Product Principles

- **Explainability.** Platform behavior that affects a participant must be explainable to that participant in terms they can understand.
- **Fairness.** Allocation of work and opportunity must be designed and evaluated for fairness.
- **Transparency.** Participants are not left to guess how the platform treats them.
- **Scalability.** The product is conceived to grow in participants, demand sources, and geography without its foundational concepts breaking down.
- **Reliability.** Participants must be able to depend on the platform behaving predictably.
- **Extensibility.** The product's concepts are defined broadly enough to accommodate ecosystem growth beyond the initial taxi use case.

## 12. Constraints

- **Business constraints.** Drivers participate as independent stakeholders, not as employees of the platform; the product must be conceivable without assuming an employment relationship.
- **Operational constraints.** A driver's availability and participation are self-determined; the platform cannot assume centralized control or ownership of drivers, vehicles, or fleets.
- **Legal constraints.** Transportation-for-hire is subject to regulatory and licensing regimes that vary by jurisdiction; the product must be conceivable without assuming a single, universal regulatory environment.
- **Platform constraints.** No single order source or participant category may be structurally privileged over another without a documented, explainable reason, consistent with Platform Neutrality in Section 5.

## 13. Assumptions

- Independent drivers, rather than an employed workforce, will remain the platform's primary supply model.
- Transportation demand will originate from more than one kind of source, including but not limited to platform-aggregated orders, personal client relationships, and corporate customers.
- Durable relationships between drivers and recurring passengers or corporate customers are valuable enough to warrant explicit product support.
- The specific jurisdictions, markets, and scale at which PIOS will initially operate are not yet determined and are not assumed by this document.

These assumptions are explicit and are subject to validation by the Product Owner before domain-level documentation proceeds on their basis.

## 14. Out Of Scope

PIOS does not attempt to solve, and this document does not describe:

- The manufacture, leasing, ownership, or insurance of vehicles.
- The licensing, certification, or regulatory qualification of drivers.
- Ownership of payment settlement or banking infrastructure.
- Underwriting of insurance or financial risk.
- Physical road, traffic, or routing infrastructure.
- The physical act of transportation, which remains the responsibility of the driver.
- Any employment relationship between the platform and a driver.

## 15. Traceability

This document derives from and extends, without contradicting, the following authoritative sources:

- [PROJECT_CONSTITUTION.md](PROJECT_CONSTITUTION.md) Sections 1–2 (Vision, Mission) ground the Purpose and Vision described in Sections 1–2 of this document.
- [PROJECT_CONSTITUTION.md](PROJECT_CONSTITUTION.md) Section 3 (Product Principles — Fair Dispatch, Driver Ownership, Transparency, Extensibility, Platform Thinking, Trust) grounds the Product Philosophy and Product Principles in Sections 5 and 11 of this document.
- [ADR-001: System Philosophy](ADR/ADR-001-System-Philosophy.md) establishes the Long-Term Maintainability and Evolution principles reflected in the Vision and Product Principles above.
- [ADR-002: Dispatch Engine](ADR/ADR-002-Dispatch-Engine.md) establishes that assignment is a platform-owned architectural capability rather than an individual's responsibility, which grounds the description of Dispatch in Sections 6, 7, and 9 of this document.
- [ADR-009: Domain Isolation](ADR/ADR-009-Domain-Isolation.md) anticipates that the participants and capabilities described here will later be organized into bounded domains; this document intentionally stops short of that boundary definition, which belongs to Domain-level documentation.

Where a future document elaborates on the product domain described here, it must remain consistent with this document, the ADR Foundation, and the Constitution, per the Documentation Hierarchy in [PROJECT_CONSTITUTION.md](PROJECT_CONSTITUTION.md) Section 5.

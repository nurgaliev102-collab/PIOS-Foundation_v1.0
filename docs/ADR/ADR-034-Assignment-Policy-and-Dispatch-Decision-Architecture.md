# ADR-034: Assignment Policy and Dispatch Decision Architecture

## Status

Proposed

## Context

ADR-002 already established that the assignment decision — whether and how a submitted order is connected to a driver — belongs exclusively to Dispatch, and explicitly excluded the specific algorithm, fairness criteria, and business rules from every level of this documentation, a prohibition APPLICATION_ARCHITECTURE.md Section 3, MODULE_STRUCTURE.md Section 3, USE_CASE_CATALOG.md (UC-007, UC-008), and `Assignment.kt`'s own KDoc each independently reaffirm. Dispatch Consumer Foundation v1.0 (commit `b794d8f`) has since given Dispatch a real, populated, locally-owned availability projection, built from consuming Driver Management's DriverAvailabilityChanged over the RabbitMQ foundation ADR-028 through ADR-032 established. Inspecting the actual committed code shows the assignment-selection seam has no architectural presence at all today: `Assignment.create(order, driver, existingAssignments)` and `AssignOrderCommand(order, driver)` both simply accept an already-chosen `driver` from their caller — `Assignment.kt`'s own KDoc states this directly: *"The specific criteria for selecting [driver] are explicitly not an architectural or domain concern (ADR-002); this factory only records a decision already made elsewhere, given to it as [driver]."* No component, port, or placeholder for making that choice exists anywhere in this codebase.

Now that real availability data exists for a future decision to actually use, this ADR defines the architectural boundary and abstraction for that still-missing decision — what information it may and must never use, and how it plugs into the already-built infrastructure without disturbing it — without deciding the algorithm itself, consistent with ADR-002's standing prohibition.

## Problem

What architectural boundary governs the eventual assignment decision's inputs, and what abstraction lets that decision be introduced — and later evolved — without touching Dispatch's already-implemented infrastructure (outbox, RabbitMQ consumer, transactional idempotency, availability projection) or reopening ADR-002's prohibition on defining the algorithm?

## Decision

### Part 1: Dispatch Responsibility

**Dispatch owns:**

- The assignment decision itself, exclusively (ADR-002).
- The Assignment aggregate and its lifecycle — creation and acceptance (DOMAIN_MODEL.md Sections 4, 12; `Assignment.kt`).
- Its own local, derived availability projection (Dispatch Consumer Foundation v1.0) — a read-only reflection of Driver Management's DriverAvailabilityChanged, never a source of truth for a driver's actual availability.
- Publication of OrderAssigned and AssignmentAccepted (EVENT_CATALOG.md Section 6).
- Its own consumer-owned RabbitMQ topology — queue and dead-letter queue — for whatever it consumes (ADR-031).

**Dispatch does NOT own:**

- The Driver aggregate, or any driver information beyond availability (Driver Management; MODULE_STRUCTURE.md Section 3, PERSISTENCE_ARCHITECTURE.md Section 3 — availability is the only field the Driver Management → Dispatch contract, INTERFACE_CONTRACTS.md Section 5, exposes).
- The Passenger or Corporate Customer aggregate or representation (Passenger Experience — no contract to Dispatch exists in INTERFACE_CONTRACTS.md Section 5).
- The Order aggregate or its lifecycle/status (Order Management) — Dispatch reacts to and reports on it, never decides it (APPLICATION_ARCHITECTURE.md Section 5, "Dispatch," Boundary).
- Payment or pricing information (Payments; ADR-018, ADR-020 — Payments interacts with no other module per INTERFACE_CONTRACTS.md Section 3).
- Notification delivery or content (Notifications; ADR-033).
- Analytics or Insight (Analytics — Analytics consumes from other domains, never the reverse; INTERFACE_CONTRACTS.md Section 4).
- Driver standing, participation, or any ranking/reputation concept (Driver Management owns these per MODULE_STRUCTURE.md Section 3, but exposes none of them to Dispatch).

### Part 2: Assignment Decision Boundary

**Current ratified inputs** — already established by an existing contract or by what is actually implemented:

- **Order identity** (`OrderReference`) — supplied to the Assign Order command (DOMAIN_MODEL.md Section 9; `AssignOrderCommand`).
- **Driver availability** — exclusively through Dispatch's own availability projection, itself sourced only from DriverAvailabilityChanged (Dispatch Consumer Foundation v1.0; INTERFACE_CONTRACTS.md Section 5).
- **Dispatch's own existing Assignment state** — already used by `Assignment.create`'s own invariant (no order may have more than one active assignment; DOMAIN_MODEL.md Section 11).

**Future candidate inputs** — plausible, but not ratified by any approved document; noted, not decided:

- **Personal Client Relationship** — PRODUCT_FOUNDATION.md Section 5 names "supporting durable relationships between drivers and recurring passengers or customers" as a product-philosophy element, and DOMAIN_MODEL.md Section 5 catalogs Personal Client Relationship as an entity. No document attributes this relationship a role in the assignment decision specifically.
- **Any Driver Management-owned information beyond availability** (standing, participation) — would require both a new INTERFACE_CONTRACTS.md contract and a Driver Management-side decision to expose it, neither of which exists.

**Structurally unsupported** — not yet even a candidate, since no domain model exists to draw it from:

- **Driver location or positioning.** EVENT_CATALOG.md Section 7 is explicit: *"No location, tracking, or positioning data is described or implied; DOMAIN_MODEL.md defines Availability only as a driver's own declaration of readiness, not a location signal."* This would require new domain modeling — a separate, future decision — before it could even be considered a candidate.

**Forbidden inputs** — excluded by already-ratified ownership and interaction boundaries (ADR-005, ADR-009, ADR-019; INTERFACE_CONTRACTS.md Section 3's exhaustive list of the four currently-justified cross-module relationships):

- Payment or pricing information (Payments).
- Passenger or Corporate Customer profile data (Passenger Experience — Dispatch has no contract with it at all).
- Driver profile, standing, participation, or ranking beyond availability (Driver Management — the contract is availability-only).
- Notification history (Notifications).
- Analytics or Insight (Analytics).
- Any external system's data (ADR-020 governs external boundaries generically; none is established for assignment).

### Part 3: Assignment Policy Abstraction

Today, the "which driver" decision has no architectural presence: `AssignOrderCommand`'s own KDoc states the driver reference is simply "supplied by the caller." This ADR names that missing seam **Assignment Policy** — an application-layer port Dispatch's own application service will call, shaped the same way this codebase's other already-proven ports are (`EventPublisher`, `OutboxRepository`/`DriverAvailabilityRepository`, `TransactionRunner`): a narrow, technology-free interface that infrastructure never reaches through.

```
Dispatch infrastructure
  (RabbitMQ consumer/topology, outbox relay, JDBC repositories)
        |
        v   (already-established application ports: EventPublisher,
        |    DriverAvailabilityRepository, AssignmentRepository, TransactionRunner)
Application layer
  (DispatchAssignmentApplicationService)
        |
        v   (NEW port this ADR defines: Assignment Policy)
Assignment decision
  (a concrete Assignment Policy implementation -- not specified here)
```

Illustrative only, not a code decision (no interface is created by this ADR): an Assignment Policy would receive the submitted order's reference and the set of drivers Dispatch's own projection currently records as available, and return either a chosen driver reference or an explicit "no eligible driver" outcome — never a Driver Management domain type, never an infrastructure type. Because the policy sits entirely behind this port, it is replaceable without any change to RabbitMQ topology, the outbox, the consumer, the availability projection, or any persistence adapter — the same replaceability guarantee ADR-032's `TransactionRunner`/`EventPublisher` pattern already provides for infrastructure swaps, applied here to the business-decision seam specifically. No concrete algorithm is chosen, evaluated, or implied by this ADR.

### Part 4: Future Evolution

Candidate future strategies are named only to describe the extension point's shape — none is approved, previewed, or ranked:

- Simple FIFO (submission order).
- Fair queue (some notion of rotation across drivers).
- Reciprocal balancing (possibly informed by Personal Client Relationship, if that input is ever separately ratified — Part 2).
- Reputation-based selection.
- Optimization-based selection.
- Machine-learning-based selection.

Each would be a distinct Assignment Policy implementation, selected via ordinary dependency injection at the application layer's own boundary. None requires a change to Part 1's ownership boundary or to any of Dispatch's existing infrastructure. A strategy that needs an input not already listed as "current ratified" in Part 2 requires that input to be separately ratified first — the same pattern ADR-033 and Product Decision Notifications v1.0 already established as the required route for authorizing a new information dependency, not invented here.

### Part 5: Product Constraints

**Already ratified:** Dispatch exclusively owns the assignment decision (ADR-002); an order has at most one active assignment at a time (DOMAIN_MODEL.md Section 11); fairness is a standing product principle the decision must someday be evaluated against (PROJECT_CONSTITUTION.md Section 3, "Fair Dispatch"; PRODUCT_FOUNDATION.md Section 5) — but its concrete criteria are nowhere decided.

**Explicitly NOT decided by any approved document:** which specific inputs beyond order identity, availability, and Dispatch's own assignment state a policy may draw on; whether Personal Client Relationship factors into selection; any fairness metric or tie-breaking rule; behavior when no driver is eligible; timeout or expiry of an unassigned order; reassignment policy. Where documentation is silent, this ADR leaves the rule undecided rather than inventing one.

### Part 6: Interaction with Existing Architecture

- **ADR-002.** Unmodified. This ADR is downstream of it — it elaborates the boundary and names the missing seam ADR-002 already implied, without redeciding ownership or defining the algorithm ADR-002 continues to exclude.
- **ADR-003.** Assignment Policy introduces no new event dependency: it consumes Dispatch's own already-projected state (itself built from DriverAvailabilityChanged through ADR-003's own event mechanism) and the order reference already supplied to the Assign Order command.
- **ADR-028 through ADR-032.** Entirely unaffected. The transport, broker, envelope, topology, and reliability principles governing Dispatch's existing DriverAvailabilityChanged consumption — and its still-unbuilt OrderAssigned publication — remain exactly as implemented; Assignment Policy sits above and after all of it, never inside it.
- **ADR-033.** Mirrors its own precedent directly: Part 3 is deliberately silent on a concrete policy for the same reason ADR-033 left Notifications' event consumption open — no ratified product decision yet justifies a specific choice, and inventing one now would repeat exactly what ADR-033 already declined to do for a different capability.
- **Driver Management publisher / Dispatch consumer / availability projection (already implemented).** None is modified by this ADR. Assignment Policy would be a new *consumer* of the projection's own data — surfacing one genuine, currently-unresolved implementation gap: `DriverAvailabilityRepository` today only supports looking up one already-known driver reference (`findByDriverReference`), not "every currently available driver," which any real policy would need. This ADR records that gap; it does not close it, since doing so is a code change outside this task's scope.

### Part 7: Decision Ownership

- **Business decision** (not made here): which specific driver receives a given order — belongs to a future Assignment Policy implementation, informed by product requirements not yet ratified.
- **Infrastructure concern** (already resolved, untouched by this ADR): how a message is transported, retried, made durable, or kept idempotent — ADR-028 through ADR-032 and the existing implementation already answer this, entirely invisible to Assignment Policy.
- **Belongs to Assignment Policy** (decided here): the shape of the boundary itself — what it receives, what it returns, and that it must be swappable independent of infrastructure.
- **Belongs to future optimization** (decided nowhere, including here): everything about *how* a policy actually chooses — scoring, ranking, fairness balancing, ML. This ADR approves none of it.

## Alternatives Considered

- **Defining a concrete first algorithm now (for example, simple FIFO) "just to unblock implementation."** Rejected: ADR-002 already excludes this from every level of documentation; no product decision has ratified even FIFO specifically, and doing so would repeat exactly the invention ADR-033 and Product Decision Notifications v1.0 already declined for a different capability.
- **Leaving the seam entirely implicit, as it is today** (the caller of `AssignOrderCommand` simply supplies a driver). Rejected: an implicit seam is not inspectable, not independently testable, and not swappable without touching the application service itself — exactly the coupling ADR-032's own port pattern (`TransactionRunner`, `EventPublisher`) was already adopted to avoid elsewhere in this codebase.
- **Placing the future policy call inside the `Assignment` aggregate itself.** Rejected: `Assignment.create`'s own KDoc already establishes it "only records a decision already made elsewhere" — moving selection logic into the aggregate would invert APPLICATION_ARCHITECTURE.md Section 2's Domain Decides Business Meaning boundary, since selection criteria are explicitly not a domain modeling concern (ADR-002), while `Assignment`'s own invariant (one active assignment per order) genuinely is.

## Consequences

### Positive Consequences

- Gives a name and an architectural boundary to a decision that today has no presence in the codebase at all — the caller simply picks.
- Gives a future implementation task an unambiguous port to build against, once a product decision authorizes a specific policy.
- Explicitly protects ADR-002's standing prohibition from being silently eroded by "just this once" implementation pressure now that real availability data exists to make that pressure concrete.
- Surfaces a genuine, previously-invisible implementation gap (no "list all available drivers" query exists yet) for a future task to address.

### Negative Consequences

- No implementation of assignment selection can proceed until a policy is both architecturally defined (this ADR) and product-authorized (a still-open decision, Part 5).
- `AssignOrderCommand` continues to require an externally-supplied driver until a policy exists to remove that requirement — unchanged by this ADR.

## Evolution Path

Anticipated follow-up decisions, none resolved here:

1. **A specific first Assignment Policy and its criteria** — a Product Owner decision (PROJECT_CONSTITUTION.md Section 7), not an architectural one, following the same evidence-based pattern ADR-033 and Product Decision Notifications v1.0 already established.
2. **A "list currently available drivers" query** on Dispatch's own availability projection — an implementation-level extension of `DriverAvailabilityRepository`, needed by any real policy, not decided here.
3. **No-eligible-driver, timeout, and reassignment behavior** — left open pending the same product decision as (1).

Any change to the ownership boundary (Part 1), the input boundary (Part 2), or the abstraction shape (Part 3) itself requires a decision that explicitly supersedes this one, consistent with ADR-015.

**Update:** Part 2's "Dispatch's own existing Assignment state" input and Part 3's diagram assumed Assignment Policy's output flows directly into `Assignment.create()`. [ADR-035: Pre-Commitment Business Fact — Aggregate Boundary](ADR-035-Pre-Commitment-Business-Fact-Aggregate-Boundary.md) clarifies this: a ratified pre-commitment business fact now sits between Assignment Policy's decision and `Assignment`'s own creation, per Product Decision: Opportunity Before Assignment v1.0. Part 1 (ownership), Part 4 (future strategies), Part 5 (product constraints), Part 6, and Part 7 remain unchanged and in full force.

## Related ADRs

- [ADR-002: Dispatch Engine](ADR-002-Dispatch-Engine.md) — the standing ownership and algorithm-exclusion decision this ADR elaborates without reopening.
- [ADR-003: Event-Driven Domain](ADR-003-Event-Driven-Domain.md) — the event mechanism Dispatch's availability projection is already built on.
- [ADR-005: Data Ownership](ADR-005-Data-Ownership.md) / [ADR-009: Domain Isolation](ADR-009-Domain-Isolation.md) / [ADR-019: Conceptual Data Ownership](ADR-019-Conceptual-Data-Ownership.md) — the ownership rules Part 1 and Part 2's forbidden-inputs list are derived from.
- [ADR-015: Evolution Strategy](ADR-015-Evolution-Strategy.md) — governs any future change to this decision.
- [ADR-028](ADR-028-Production-Integration-Transport-Decision.md) through [ADR-032](ADR-032-Reliable-Event-Publication-Strategy.md) — the transport, broker, schema, topology, and reliability foundation Assignment Policy sits above, unmodified.
- [ADR-033: Notifications Module and Event Consumption Boundary](ADR-033-Notifications-Module-and-Event-Consumption-Boundary.md) — the direct precedent for separating "the abstraction may exist" from "a specific instance is authorized."

## References

- [PROJECT_CONSTITUTION.md](../PROJECT_CONSTITUTION.md), Section 3 (Fair Dispatch), Section 7 (Product Owner authority)
- [PRODUCT_FOUNDATION.md](../PRODUCT_FOUNDATION.md), Section 5 (Product Philosophy)
- [DOMAIN_MODEL.md](../DOMAIN_MODEL.md), Sections 4, 5, 7, 9, 11, 12
- [EVENT_CATALOG.md](../EVENT_CATALOG.md), Sections 6, 7, 9, 10
- [USE_CASE_CATALOG.md](../USE_CASE_CATALOG.md), UC-005 through UC-008
- [APPLICATION_ARCHITECTURE.md](../APPLICATION_ARCHITECTURE.md), Sections 2–3, 5
- [MODULE_STRUCTURE.md](../MODULE_STRUCTURE.md), Section 3 ("Dispatch Module")
- [INTERFACE_CONTRACTS.md](../INTERFACE_CONTRACTS.md), Sections 3, 5
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/domain/Assignment.kt`, `application/AssignOrderCommand.kt` (commit `b794d8f` and prior)

# ADR-028: Production Integration Transport Decision

## Status

Proposed

## Context

ADR-003 established events as PIOS's own mechanism for communicating that something has already happened — a capability publishes a fact; it never uses an event to issue a command — but deliberately selected no delivery mechanism or technology for carrying that event between capabilities. ADR-004 established that direct, request-driven interaction (anything needing an immediate answer) must be resource-oriented, versioned, and consistent, again without selecting a protocol or technology. ADR-020 extended both philosophies to PIOS's relationship with systems outside its own boundary. ADR-026 then committed every one of PIOS's eight ratified modules to independent release from day one — each buildable, versionable, and deployable without requiring any other module to be rebuilt or redeployed alongside it.

MVP Integration Layer work since (ADR-027, "MVP Integration Mechanism") produced three concrete, ratified interaction contracts (INTERFACE_CONTRACTS.md Section 5: Passenger Experience → Order Management, Driver Management → Dispatch, Dispatch → Order Management) and proved each is correctly shaped at the application layer — but ADR-027 explicitly confined itself to proving contract *correctness*, not runtime *delivery*, and named selecting the concrete transport mechanism as a distinct, deliberately deferred decision. With domain, application, and persistence layers now implemented for the MVP's four core modules, and PostgreSQL selected per-domain (ADR-025), transport selection is the next concrete step before any module can actually communicate with another as an independently running, independently deployed service.

## Problem

Given PIOS's own event/command distinction (ADR-003, ADR-004) and its binding commitment to full independent per-module deployability (ADR-026), what mechanism should actually carry a fact or a request between two independently deployed PIOS modules in production — REST, a message broker, or some combination of the two?

## Decision

**Hybrid, by category of interaction — not by module.** This is not a new architectural split invented here; it resolves the mechanism for a distinction ADR-003 and ADR-004 already drew:

- **Events (ADR-003) are delivered through a message broker.** Every interaction already catalogued as an event — OrderSubmitted, OrderAssigned, AssignmentAccepted, OrderCancelled, OrderCompleted, DriverAvailabilityChanged (EVENT_CATALOG.md) — is published by its owning module and delivered asynchronously to every current and future consumer through a broker. This is the production mechanism ADR-027 deferred; it supersedes ADR-027's in-process/test-scoped mechanism as the actual runtime transport once implemented.
- **Direct, request-driven interaction (ADR-004) continues through REST.** Anything genuinely needing an immediate answer — today, this is exclusively each module's own externally-facing API (a Passenger, Driver, or Administrator client calling their own module) — follows the resource-oriented, versioned, consistent principles ADR-004 already established. No cross-module synchronous command or query is established anywhere in INTERFACE_CONTRACTS.md Section 5 today; if one is ratified in the future, it follows this same REST style, since ADR-004 governs "direct, request-driven interaction between a capability and its callers, whether external clients or other capabilities" without distinction.

No specific message broker product (e.g., Kafka, RabbitMQ, AWS SNS/SQS, Google Pub/Sub, NATS) is selected here. Consistent with how ADR-021 established the database *evaluation framework* before ADR-025 selected PostgreSQL specifically, this ADR establishes only the transport *category* per interaction type; the specific broker product is a distinct, dedicated decision left to a future ADR.

## Evaluation

Adapting ADR-021's evaluation-framework approach to transport rather than storage:

| Criterion | REST (pure) | Message Broker (pure) | Hybrid (selected) |
| --- | --- | --- | --- |
| **Decoupling / Independent Evolution** (ADR-001, ADR-003) | Poor for events: producer must call every consumer by address, so adding a consumer requires changing the producer — directly contradicting ADR-003's own stated benefit, "allowing new consumers of an event to be added without changing the capability that publishes it." | Strong: publish-and-forget; consumers subscribe independently. | Strong where it matters (events); REST's coupling is acceptable for direct interaction, where a caller-callee relationship is the whole point. |
| **Generic, unspecified multi-consumer pattern** (Notifications, Analytics — INTERFACE_CONTRACTS.md Section 4, "generic consumption... with no specific provider named") | Cannot be satisfied without the producer enumerating every current and future consumer, which no approved document requires or permits it to know in advance. | Satisfied directly: Notifications and Analytics each subscribe to whatever events they need without any producer change. | Satisfied, via the broker side of the split. |
| **Immediate-answer needs** (ADR-004) | Fits directly. | Poor fit: request/reply over a broker adds latency and complexity a direct call doesn't need, for a case ADR-004 already describes as needing an immediate answer. | Fits directly, via the REST side of the split. |
| **Independent Deployability** (ADR-026) | Risk: a naive point-to-point call can reintroduce the "temporal coupling" ADR-003 already rejected as an alternative — the caller and callee must both be up at the moment of the call. | No such coupling: a subscriber's downtime does not block the publisher. | Preserved for events (the dominant, currently-established inter-module traffic); the same caller/callee availability consideration REST always carries applies only where REST is actually used. |
| **Consistency Model** (ADR-003 Section on Consequences; PERSISTENCE_ARCHITECTURE.md Section 6) | Can be made synchronous, but PIOS already accepts eventual consistency across domains (ADR-003's own "Negative Consequences": "introduces eventual consistency... a capability reacting to an event does so after the fact"). | Matches the eventual-consistency model PIOS already accepts. | Matches, since events (the eventually-consistent case) go through the broker; REST is reserved for cases where an immediate, consistent answer is the actual requirement. |
| **Operational Maturity / Team Capability** (mirroring ADR-021's criteria) | Very high — REST is already the assumed style for every module's own API (ADR-004), no new operational capability required. | New operational capability required: running and operating a broker is a genuine addition to the platform's operational surface. | Same broker-operation cost as pure Message Broker, without removing REST's existing, already-assumed use for direct interaction — no net new REST capability required. |
| **Security** (ADR-011) | Each module already terminates its own boundary; well-understood. | Requires securing the broker itself as a shared piece of infrastructure, at the edge of every module that publishes or subscribes. | Same broker-security consideration as pure Message Broker; unchanged for REST's already-assumed use. |
| **Evolution / Versioning** (ADR-010, ADR-015) | Mature, well-understood versioning practice for request/response interfaces. | Requires its own versioning discipline for event schemas — a genuine, currently-undecided extension of ADR-010, whichever transport is chosen for events. | Same event-schema-versioning need as pure Message Broker; REST's versioning need is already governed by ADR-004/ADR-010. |

## Alternatives Considered

- **Pure REST for everything, including events** (via polling, webhooks, or similar). Rejected: cannot satisfy the generic, unspecified multi-consumer pattern already established for Notifications and Analytics without the producer enumerating every consumer, which directly contradicts the decoupling benefit ADR-003 already claims for events; reintroduces exactly the "temporal coupling... undermining independent evolution" ADR-003 explicitly rejected as an alternative when it first considered "direct, synchronous calls between capabilities for every interaction."
- **Pure Message Broker for everything, including direct request-driven interaction.** Rejected: ADR-004 already establishes that some interactions require an immediate answer; forcing those through asynchronous messaging (request/reply-over-broker patterns) adds latency, complexity, and failure modes a direct call does not have, for no benefit over REST in exactly the case ADR-004 was written to cover.
- **Hybrid, split by module rather than by interaction category** (e.g., "Dispatch uses REST, Driver Management uses messaging"). Considered and rejected: nothing about a specific module's own nature requires a different transport category from another's; the distinction that actually matters — event versus direct, request-driven interaction — is the one ADR-003 and ADR-004 already drew, and every module participates in both categories (its own event(s), and its own external-facing API).
- **Deferring this decision further, continuing to rely on ADR-027's mechanism indefinitely.** Rejected: ADR-027 was explicit that its mechanism does not deliver events between independently running instances of these services; continuing to defer would leave the MVP unable to actually operate as more than a single co-located test process, contradicting ADR-026's independent-deployment commitment in practice even though not in documentation.

## Consequences

### Positive Consequences

- Resolves ADR-027's explicitly deferred question without inventing a new architectural split — it applies the event/command distinction ADR-003 and ADR-004 already established.
- Directly satisfies the generic, unspecified multi-consumer pattern already required for Notifications and Analytics (INTERFACE_CONTRACTS.md Section 4), which no REST-only design can satisfy without contradicting ADR-003's own decoupling rationale.
- Preserves ADR-026's independent-deployability commitment for the dominant, currently-established form of inter-module traffic (all three ratified contracts in INTERFACE_CONTRACTS.md Section 5 are event-shaped).
- Keeps REST exactly where it already sits (ADR-004, each module's own external-facing API), avoiding any disruption to work already built on it.

### Negative Consequences

- Introduces a second technology category (a message broker) that the platform must learn to operate, beyond the REST capability already assumed — genuine new operational surface, consistent with the tradeoff ADR-025 already accepted when introducing PostgreSQL.
- The specific broker product remains undecided; production delivery of any event cannot actually begin until a follow-up ADR selects one and an implementation task wires it in. Until then, ADR-027's provisional mechanism remains the only implemented one.
- Event schema versioning (how OrderAssigned's shape, for example, evolves without breaking existing consumers) is not decided by this ADR and becomes a genuine open question a future decision must resolve, consistent with ADR-010's existing versioning-strategy scope.

## Evolution Path

This ADR decides *transport category per interaction type*, not a specific product. Two concrete follow-up decisions are anticipated and explicitly left open:

1. **Message broker product selection** — a dedicated ADR evaluating candidate technologies (e.g., Kafka, RabbitMQ, cloud-managed pub/sub options) against criteria comparable to ADR-021's database framework, adapted for messaging (delivery guarantees, ordering, operational maturity, team capability, cost).
2. **Event schema versioning strategy** — a dedicated extension of ADR-010 addressing how a published event's shape evolves without breaking existing consumers.

Until both exist and an implementation task wires the selected broker in, ADR-027's mechanism remains PIOS's only actually-implemented integration transport. Any future decision changing the category split established here (for example, introducing cross-module synchronous commands) requires a decision that explicitly supersedes this one, consistent with ADR-015.

**Update:** Follow-up decision 1 (message broker product selection) is now [ADR-029: Message Broker Selection for PIOS Event Infrastructure](ADR-029-Message-Broker-Selection.md), which selects RabbitMQ. Follow-up decision 2 (event schema versioning strategy) is now [ADR-030: Event Schema and Versioning Strategy](ADR-030-Event-Schema-Versioning-Strategy.md).

## Related ADRs

- [ADR-001: System Philosophy](ADR-001-System-Philosophy.md) — independent evolution of capabilities, which the event/broker split preserves.
- [ADR-002: Dispatch Engine](ADR-002-Dispatch-Engine.md) — the original example of a cross-capability business event.
- [ADR-003: Event-Driven Domain](ADR-003-Event-Driven-Domain.md) — establishes events and rejects direct synchronous calls as the general mechanism; this ADR selects the delivery technology category for the events ADR-003 already defined.
- [ADR-004: API Style](ADR-004-API-Style.md) — establishes the principles this ADR confirms continue to govern direct, request-driven interaction.
- [ADR-005: Data Ownership](ADR-005-Data-Ownership.md) / [ADR-009: Domain Isolation](ADR-009-Domain-Isolation.md) — no module accesses another's internals regardless of transport; this ADR changes only how a fact or request physically crosses the boundary.
- [ADR-010: Versioning Strategy](ADR-010-Versioning-Strategy.md) — event schema versioning, left open here, extends this ADR's existing scope.
- [ADR-011: Security Principles](ADR-011-Security-Principles.md) — governs securing both the broker and REST boundaries.
- [ADR-015: Evolution Strategy](ADR-015-Evolution-Strategy.md) — governs how the two follow-up decisions in Evolution Path must be recorded.
- [ADR-019: Conceptual Data Ownership](ADR-019-Conceptual-Data-Ownership.md) — unaffected; transport carries facts, never ownership.
- [ADR-020: Integration Philosophy](ADR-020-Integration-Philosophy.md) — the nearest existing precedent, applied to PIOS's own internal modules rather than external dependencies.
- [ADR-021: Database Technology Strategy](ADR-021-Database-Technology-Strategy.md) — the evaluation-framework-then-product-selection pattern this ADR follows for messaging.
- [ADR-025: Database Technology Decision](ADR-025-Database-Technology-Decision.md) — the concrete-selection precedent this ADR's own follow-up (broker product selection) will mirror.
- [ADR-026: Deployment Strategy](ADR-026-Deployment-Strategy.md) — the independent-deployability guarantee this decision preserves for events.
- [ADR-027: MVP Integration Mechanism](ADR-027-MVP-Integration-Mechanism.md) — the provisional mechanism this ADR's eventual implementation supersedes; ADR-027 remains in effect until then.

## References

- [PROJECT_CONSTITUTION.md](../PROJECT_CONSTITUTION.md), Section 4
- [INTERFACE_CONTRACTS.md](../INTERFACE_CONTRACTS.md), Sections 4–5, 7
- [EVENT_CATALOG.md](../EVENT_CATALOG.md)
- [APPLICATION_ARCHITECTURE.md](../APPLICATION_ARCHITECTURE.md), Section 10
- [SYSTEM_ARCHITECTURE.md](../SYSTEM_ARCHITECTURE.md), Section 12
- [ENGINEERING_GUIDELINES.md](../ENGINEERING_GUIDELINES.md), Section 3 (No Premature Optimization — motivates deferring the specific broker product to its own decision)

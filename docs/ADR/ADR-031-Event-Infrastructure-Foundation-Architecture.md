# ADR-031: Event Infrastructure Foundation Architecture

## Status

Proposed

## Context

ADR-029 selected RabbitMQ as PIOS's message broker; ADR-030 established who owns an event's schema and how it may version, without describing how RabbitMQ itself is structured to carry it. EVENT_CATALOG.md Section 10 already establishes that a domain creates only the events describing facts it owns; today that is exactly three domains — Order Management (OrderSubmitted, OrderCancelled, OrderCompleted), Dispatch (OrderAssigned, AssignmentAccepted), and Driver Management (DriverAvailabilityChanged). Passenger Experience currently owns no event (EVENT_CATALOG.md Section 8 records this absence explicitly, rather than inventing one), so it is not a publisher in this topology. INTERFACE_CONTRACTS.md Section 4 establishes that Notifications and Analytics each consume events generically, with no specific provider-event pairing named — either may need to receive any current or future event without the publishing domain knowing about them in advance.

None of the approved documents so far describe RabbitMQ's actual internal shape: how many exchanges exist and who owns them, how queues are declared and by whom, where the boundary between a publishing module and a consuming module actually sits, or what happens when a consumer fails to process a message. This ADR establishes that architecture, as a foundation for a future implementation task — it defines no code, no configuration file, and no concrete broker deployment.

## Problem

Given RabbitMQ as the selected broker (ADR-029) and the ownership and versioning policy already established (ADR-030, EVENT_CATALOG.md Section 10), what topology — exchanges, queues, the publisher/consumer boundary, and failure handling — should PIOS's event infrastructure follow, consistent with the exclusive-ownership principle already governing every other part of this architecture (ADR-005, ADR-009, ADR-019)?

## Decision

### RabbitMQ Topology Principles

- **Exclusive ownership extends to messaging infrastructure, exactly as it already governs persistence.** The same principle ADR-021/ADR-025 apply to "one database instance per domain, never shared" applies here: a domain's exchange is administered only by that domain, and a domain's queues are administered only by the module that declared them. No platform-wide, shared, or centrally-provisioned exchange or queue exists.
- **A publishing module knows only its own exchange.** It has no knowledge of which queues are bound to it, how many consumers exist, or who they are — the same decoupling ADR-003 already claims as an event's benefit, realized concretely in the broker topology.
- **A consuming module knows only the exchanges it binds to, never another module's internals beyond that binding.** This is the messaging-topology expression of INTERFACE_CONTRACTS.md Section 8's existing rule that no module accesses another's internals directly.
- **Every exchange and queue is durable** (survives a broker restart), consistent with the reliable, at-least-once delivery ADR-029 already selected RabbitMQ to provide.
- **Topology declaration is each module's own responsibility.** A module declares its own exchange (if it owns an event) and its own queues (for whatever it consumes) as part of its own startup, exactly as each module already owns its own database schema (ADR-025) and is independently deployable (ADR-026) — no shared provisioning step creates a cross-module deployment dependency.

### Exchange Strategy

- **One topic exchange per event-owning domain**, named after the domain — for example, `order-management.events`, `dispatch.events`, `driver-management.events` — not one exchange per event and not one shared, platform-wide exchange.
  - *One per domain, not per event*, because a domain that owns several events (Order Management owns three) should expose them through a single, coherent publishing surface a generic consumer can subscribe to entirely, rather than forcing every consumer to discover and bind to several separate exchanges for one domain's facts.
  - *Not shared platform-wide*, because a shared exchange would be a single piece of infrastructure jointly depended on by every domain's publishing path, contradicting the exclusive-ownership rule (ADR-005, ADR-009) this ADR extends to messaging.
- **Topic exchange, not fanout or direct**, because a topic exchange's routing key lets a specific consumer bind to exactly the event it needs (for example, Dispatch binding only to `driver.availability.changed`) while still letting a generic consumer (Notifications, Analytics) bind with a wildcard to receive every event a domain publishes, without that domain enumerating its consumers — satisfying both the specific and the generic consumption patterns already established (INTERFACE_CONTRACTS.md Sections 4–5) with a single exchange type and routing scheme.
- **Routing key equals the event's own business name**, in a consistent, documented, dot-separated form (for example, `order.submitted`, `order.completed`, `order.cancelled` on Order Management's exchange; `order.assigned`, `assignment.accepted` on Dispatch's exchange; `driver.availability.changed` on Driver Management's exchange) — matching EVENT_CATALOG.md's own past-tense, business-language naming principle, never a technology or queue name.
- **Only the owning domain publishes to its own exchange or declares/modifies it.** No other module is ever granted publish or administrative rights to another domain's exchange.

### Queue Ownership

- **A queue belongs to the consuming module that declares it, never to the publisher.** The publishing domain's exchange has no awareness of any queue bound to it; queue lifecycle (declaration, binding, deletion) is entirely the consuming module's own responsibility and administrative boundary — the same exclusive-ownership principle applied to the consumer side of the topology.
- **One queue per (consumer, source-exchange) pair**, not one shared queue serving multiple consuming modules and not one queue spanning multiple producers. For example: Dispatch declares its own queue bound to Driver Management's exchange for `driver.availability.changed`; Order Management declares its own queue bound to Dispatch's exchange for `order.assigned` and `assignment.accepted`; Notifications and Analytics each declare their own queue per producer exchange they need, bound with a wildcard routing pattern.
  - This keeps one consumer's backlog, slowness, or failure fully isolated from every other consumer of the same event — no module ever competes with another over the same queue, and no single queue's health depends on more than one consuming module's own processing rate.
- **Naming convention:** a consuming module's queue is named `<consuming-domain>.from-<producer-domain>` (for example, `dispatch.from-driver-management`, `order-management.from-dispatch`, `notifications.from-order-management`) — traceable at a glance to both ends of the relationship it serves, without naming any technology beyond the pattern itself.

### Publisher/Consumer Boundaries

- **A module publishes only to the exchange it owns**, and only for the events EVENT_CATALOG.md Section 10 already establishes it owns. A module never publishes to another domain's exchange and never publishes an event on another domain's behalf, consistent with EVENT_CATALOG.md's existing "no domain creates an event describing a fact it does not own" rule.
- **A module consumes by declaring and binding its own queue(s)** to whichever other domain's exchange(s) it needs, using the routing key(s) matching the specific events it depends on (INTERFACE_CONTRACTS.md Section 5's named contracts) or a wildcard pattern (Notifications' and Analytics' generic consumption, INTERFACE_CONTRACTS.md Section 4).
- **The application-layer translation already established for the MVP (ADR-027) carries forward conceptually**: the publishing module's own application layer is responsible for turning its domain event into the message it puts on its own exchange; the consuming module's own application layer is responsible for turning a received message into whatever local representation it needs. Exactly how that translation is implemented in production (serialization format, concrete message envelope) is left to the schema-representation decision ADR-030 already deferred, and to a future implementation task — this ADR fixes only where the publisher's and consumer's respective responsibilities begin and end.
- **No module is ever granted administrative access to another module's exchange or queue** beyond a consumer's own binding operation on a producer's exchange — never redeclaration, reconfiguration, or deletion of infrastructure another module owns.

### Retry and Failure Handling Principles

- **Every consumer queue has its own dead-letter queue (DLQ), owned by the same consuming module** — never a shared or platform-wide dead-letter destination. A message that cannot be processed after a bounded number of attempts is routed to that queue's own DLQ, never silently dropped, consistent with the Transparency principle (PROJECT_CONSTITUTION.md Section 3) and the No Hidden Assumptions principle (Section 4).
- **Retries are bounded, with backoff, never infinite.** The specific count and backoff timing are implementation decisions left to a future task, not fixed here; the principle is that a message is retried a limited number of times before it is dead-lettered, so a single poison message can never block that queue's other, healthy messages indefinitely.
- **Publishers use RabbitMQ's publisher-confirm mechanism.** A publishing module treats the broker's confirmation as the point at which it may consider an event successfully hand off; how a module reacts to a missing confirmation (retry the publish, alert, or otherwise) is left to that module's own implementation, not fixed here.
- **Consumers must be idempotent.** Because RabbitMQ, like any at-least-once broker (ADR-029), can redeliver a message a consumer already processed (for example, after a crash between processing and acknowledging), every consumer's own processing must tolerate receiving the same event more than once without incorrect effect. This is a direct, required consequence of choosing at-least-once delivery, not an optional recommendation.
- **One consumer's failure never blocks another's, and never blocks the publisher.** This follows directly from the one-queue-per-consumer principle already established (Queue Ownership, above): a publisher only ever interacts with its own exchange, and a struggling consumer's own queue and DLQ absorb that consumer's own failures.
- **This ADR does not resolve the dual-write problem** — the case where a domain's own persistence write (PostgreSQL, per-domain) and its event publish are not part of one atomic operation, so one could succeed while the other fails. That is a distinct, genuine architectural question (commonly addressed by patterns such as a transactional outbox) left open for a future decision; it is called out explicitly here so it is not mistaken for something this ADR already answers.

## Alternatives Considered

- **A single, shared, platform-wide exchange for all events.** Rejected: every domain publishing to one shared exchange would make that exchange a piece of jointly-depended-on infrastructure, directly contradicting the exclusive-ownership rule ADR-005 and ADR-009 already establish and that ADR-025 already extended to per-domain database instances.
- **One exchange per event, rather than per domain.** Considered: technically workable, but would require a generic consumer (Notifications, Analytics) to discover and bind to a growing, unbounded number of exchanges as new events are added, rather than one exchange per domain with a wildcard binding — more moving parts for no benefit over the selected approach.
- **Direct/fanout exchanges instead of topic exchanges.** Rejected: a direct exchange cannot support wildcard subscription for a generic consumer without one binding per event; a fanout exchange cannot support a specific consumer subscribing to only the one event it needs without filtering messages itself, pushing routing logic into every consumer instead of the broker. Topic exchanges support both patterns PIOS actually needs (INTERFACE_CONTRACTS.md Sections 4–5) without either drawback.
- **Shared queues consumed by more than one module (competing consumers across module boundaries).** Rejected: this would couple two independently deployed modules' consumption rate and failure modes together through one physical queue, contradicting the isolation ADR-026 already requires between independently deployable modules.
- **A shared, platform-wide dead-letter queue for all failures.** Rejected: this would recreate exactly the shared-infrastructure problem already rejected for exchanges and queues, and would obscure which module's consumption actually failed, contradicting Transparency (PROJECT_CONSTITUTION.md Section 3).
- **Resolving the dual-write problem within this ADR (e.g., mandating a transactional outbox pattern now).** Rejected for this ADR specifically: it is a distinct architectural question deserving its own dedicated evaluation, and no approved document yet establishes a demonstrated need urgent enough to decide it ahead of the topology this ADR is scoped to define, consistent with No Premature Optimization (ENGINEERING_GUIDELINES.md Section 3).

## Consequences

### Positive Consequences

- Extends PIOS's already-established exclusive-ownership principle (ADR-005, ADR-009, ADR-019, ADR-021, ADR-025) consistently into messaging infrastructure, rather than introducing a new, differently-shaped rule for this one layer.
- Gives every current and future consumer (specific or generic) a single, predictable pattern for finding and binding to the events it needs, without any producer needing to know who its consumers are.
- Isolates every consumer's own failure, backlog, or slowness from every other consumer and from the publisher, through the one-queue-per-consumer and one-DLQ-per-queue principles.
- Leaves implementation-level specifics (retry counts, backoff timing, exact message envelope) properly open for a future implementation task, while still fixing the architectural shape those specifics must fit within.

### Negative Consequences

- More queues and exchanges to operate than a single shared topology would require — each consumer's own queue (and DLQ) is a distinct piece of infrastructure to declare, monitor, and maintain.
- Every consumer must independently implement idempotent processing; this is a genuine, non-optional development cost following directly from RabbitMQ's at-least-once delivery model.
- The dual-write problem (persistence write and event publish not being atomic) remains open; until a future decision resolves it, a module's own implementation must account for the possibility of one succeeding without the other.

## Evolution Path

This ADR fixes the topology's shape — exchange-per-domain, queue-per-consumer, the publisher/consumer boundary, and the failure-handling principles — not its concrete configuration. Anticipated follow-up decisions:

1. **Dual-write resolution** — a dedicated decision (for example, evaluating a transactional outbox pattern) addressing how a domain's persistence write and event publish are kept consistent with each other.
2. **Concrete retry/backoff parameters and message envelope format** — implementation-level detail, dependent on the schema-representation decision ADR-030 already deferred.
3. **New event-owning domains** — if a future ADR grants Passenger Experience, Administration, Payments, or any other domain a catalogued event it does not currently own, that domain gains its own exchange following exactly this same architecture, without requiring this ADR to be revisited.

Any change to the topology principles themselves (for example, moving away from exclusive per-domain exchange ownership) requires a decision that explicitly supersedes this one, consistent with ADR-015.

## Related ADRs

- [ADR-001: System Philosophy](ADR-001-System-Philosophy.md) — Long-Term Maintainability and Traceability, both served by a consistent, documented topology.
- [ADR-003: Event-Driven Domain](ADR-003-Event-Driven-Domain.md) — the decoupling benefit this topology realizes concretely.
- [ADR-005: Data Ownership](ADR-005-Data-Ownership.md) / [ADR-009: Domain Isolation](ADR-009-Domain-Isolation.md) — the exclusive-ownership rule this ADR extends to exchanges and queues.
- [ADR-015: Evolution Strategy](ADR-015-Evolution-Strategy.md) — governs any future change to this topology.
- [ADR-019: Conceptual Data Ownership](ADR-019-Conceptual-Data-Ownership.md) — the ownership mapping that determines which domain owns which exchange.
- [ADR-021: Database Technology Strategy](ADR-021-Database-Technology-Strategy.md) / [ADR-025: Database Technology Decision](ADR-025-Database-Technology-Decision.md) — the per-domain, exclusive-resource precedent this ADR mirrors for messaging infrastructure.
- [ADR-026: Deployment Strategy](ADR-026-Deployment-Strategy.md) — the independent-deployability guarantee the one-queue-per-consumer principle preserves.
- [ADR-027: MVP Integration Mechanism](ADR-027-MVP-Integration-Mechanism.md) — the application-layer translation pattern this ADR carries forward conceptually.
- [ADR-028: Production Integration Transport Decision](ADR-028-Production-Integration-Transport-Decision.md) — selected the message-broker category this topology implements.
- [ADR-029: Message Broker Selection for PIOS Event Infrastructure](ADR-029-Message-Broker-Selection.md) — selected RabbitMQ, the broker this topology is defined for.
- [ADR-030: Event Schema and Versioning Strategy](ADR-030-Event-Schema-Versioning-Strategy.md) — the ownership and versioning policy this topology's exchange strategy is built to carry.

## References

- [PROJECT_CONSTITUTION.md](../PROJECT_CONSTITUTION.md), Sections 3–4
- [EVENT_CATALOG.md](../EVENT_CATALOG.md), Sections 8, 10
- [INTERFACE_CONTRACTS.md](../INTERFACE_CONTRACTS.md), Sections 4–5, 8
- [PERSISTENCE_ARCHITECTURE.md](../PERSISTENCE_ARCHITECTURE.md), Section 6
- [ENGINEERING_GUIDELINES.md](../ENGINEERING_GUIDELINES.md), Section 3

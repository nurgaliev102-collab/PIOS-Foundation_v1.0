# ADR-029: Message Broker Selection for PIOS Event Infrastructure

## Status

Proposed

## Context

ADR-028 (Production Integration Transport Decision) selected transport by interaction category rather than by module: a message broker for every catalogued event (EVENT_CATALOG.md — OrderSubmitted, OrderAssigned, AssignmentAccepted, OrderCancelled, OrderCompleted, DriverAvailabilityChanged), and REST for direct, request-driven interaction (ADR-004). ADR-028 explicitly deferred the specific broker product to its own dedicated decision, mirroring how ADR-021 established the database evaluation framework before ADR-025 selected PostgreSQL specifically. This ADR is that decision.

ADR-023 already selected Kotlin with Spring Boot for backend implementation. ADR-026 committed every module to independent deployability. INTERFACE_CONTRACTS.md Section 4 establishes that Notifications and Analytics each consume events generically, with no specific provider-event pairing named — any current or future event may need a new consumer without the publishing module changing. No approved document establishes a need for high-frequency telemetry, complex stream processing, or long-term event replay at scale; the catalogued events are discrete, moderate-volume business facts (an order submitted, an assignment made, a driver's availability changed), and ENGINEERING_GUIDELINES.md Section 3 already commits PIOS to avoiding complexity not justified by a demonstrated need.

## Problem

Which message broker technology should carry PIOS's catalogued events between independently deployed modules, evaluated against PIOS's actual, currently-established needs rather than hypothetical future scale?

## Decision

**RabbitMQ** is selected as PIOS's message broker for event delivery, satisfying ADR-028's "message broker" category for all six catalogued events.

**Why it satisfies architecture.**

- **Reliable, at-least-once delivery.** RabbitMQ's acknowledgement model (consumer ack/nack, dead-letter queues, publisher confirms) directly supports the Ownership Consistency and Lifecycle Consistency principles already established in PERSISTENCE_ARCHITECTURE.md Section 6 — a catalogued fact like DriverAvailabilityChanged or OrderAssigned must not be silently lost between an independently deployed producer and consumer.
- **Generic, unspecified multi-consumer pattern.** RabbitMQ's topic and fanout exchanges let a new consumer (Notifications, Analytics, or any future one) subscribe to an existing event without the publishing module's own code or configuration changing — directly satisfying INTERFACE_CONTRACTS.md Section 4's requirement and preserving the decoupling benefit ADR-003 already claims for events.
- **JVM and Spring ecosystem fit.** Spring AMQP is a mature, first-class Spring Boot integration — the same reasoning ADR-025 already applied when selecting PostgreSQL partly for its "demonstrated fit with the JVM and Kotlin backend already selected in ADR-023" applies here as well.
- **Operational simplicity matched to demonstrated need.** RabbitMQ requires materially less operational machinery to run reliably (no partition planning, no consumer-group rebalancing, no distributed log storage tuning) than Kafka, while still providing durability and delivery guarantees Core NATS alone does not — the right balance for an MVP-stage platform under ENGINEERING_GUIDELINES.md Section 3's No Premature Optimization and Operational Simplicity principles, without under-provisioning reliability.
- **Sufficient throughput.** RabbitMQ comfortably handles the moderate volume of discrete business events PIOS's own catalogue describes; nothing in any approved document establishes a throughput requirement RabbitMQ cannot meet.

## Evaluation Against Adapted Criteria

Adapting ADR-021's evaluation-framework approach (originally for storage) to messaging:

| Criterion | RabbitMQ | Kafka | NATS (Core + JetStream) | Redis Streams |
| --- | --- | --- | --- | --- |
| **Delivery Guarantee** | At-least-once via ack/nack, publisher confirms, dead-letter queues — mature and battle-tested. | At-least-once via consumer offsets; strong, but tuned for log-based replay rather than per-message acknowledgement semantics. | Core NATS: at-most-once (fire-and-forget) — unsuitable alone for business facts. JetStream adds at-least-once, but is a materially newer addition than RabbitMQ's or Kafka's delivery guarantees. | At-least-once via consumer groups, but Redis's persistence (RDB/AOF) is a data-store durability model retrofitted for streaming, not a purpose-built message-durability design. |
| **Generic multi-consumer pattern** (INTERFACE_CONTRACTS.md Section 4) | Native fit: topic/fanout exchanges, declarative, new consumers added without publisher change. | Native fit: topics, consumer groups; more configuration (partitions, group coordination) for the same outcome. | Native fit via subject wildcards (JetStream); smaller ecosystem for it in practice. | Possible via consumer groups, but each stream and group is more manually managed; less idiomatic for "unknown future consumer." |
| **Operational Complexity** | Low–moderate: single-purpose broker, well-documented clustering, no external coordination service. | Moderate–high: partition strategy, consumer-group rebalancing, broker cluster sizing; even simplified by KRaft mode, still more moving parts than RabbitMQ. | Low: minimal footprint, very simple to run — but JetStream (the reliable mode PIOS actually needs) adds back meaningful operational surface, and less operational precedent exists for it. | Moderate: introduces Redis into PIOS as a wholly new dependency — PIOS has not adopted Redis for any other purpose (caching or otherwise), so this does not reduce net operational surface versus RabbitMQ. |
| **JVM / Spring Ecosystem Fit** (mirroring ADR-025's Community Maturity / Team Capability criteria) | Excellent: Spring AMQP is mature, first-class, and widely used for exactly this "reliable business event" scenario. | Excellent: Spring Kafka is equally mature, but brings Kafka's own operational complexity along with it. | Weak: no first-party "Spring NATS" starter; integration requires more direct use of the Java client. | Good: Spring Data Redis supports Streams, but Streams is not Redis's primary purpose, and tooling is less purpose-built than Spring AMQP or Spring Kafka. |
| **Throughput / Scale Fit for PIOS's Demonstrated Need** | More than sufficient for discrete, moderate-volume business events (order, assignment, availability facts) — not high-frequency telemetry. | Excess capacity for PIOS's current catalogue; its strengths (very high throughput, long retention, replay) address a need no approved document currently establishes. | Sufficient throughput; JetStream's newer, smaller-scale production track record is the more relevant limiting factor than raw capacity. | Sufficient throughput for current volumes; less proven at this specific role than the other three. |
| **Replay / Historical Reprocessing** | Supported via RabbitMQ Streams (a first-class RabbitMQ feature since 3.9) if a future need for it is demonstrated; not required today. | Native strength — the best fit if a documented future need for long-term event replay (e.g., for Analytics) materializes. | JetStream supports replay; less production track record than Kafka's for this specific strength. | Streams retain a configurable history, but retention and replay tooling are less mature than Kafka's purpose-built log model. |
| **Team Capability** | Widely known, extensive documentation and community precedent for exactly this microservices-event-bus role. | Requires deeper operational expertise (partitioning, consumer-group tuning) than currently demonstrated as necessary. | Smaller community; less precedent for reliable business-event delivery specifically (as opposed to Core NATS's original telemetry/low-latency use case). | Smaller community for this specific role; more commonly adopted as a cache than an event backbone. |

## Alternatives Considered

- **Kafka.** Rejected as the default, not because it is technically deficient, but because its distinguishing strengths — very high throughput, long-term log retention, and replay for stream processing — address a need no approved document currently establishes for PIOS's six catalogued events. Its operational complexity (partition planning, consumer-group coordination, broker cluster sizing) is a real cost the platform would carry starting today for capability it does not yet need, contradicting ENGINEERING_GUIDELINES.md Section 3's No Premature Optimization principle. Kafka remains the natural candidate if a documented future need for long-term event replay (most plausibly for Analytics's own, still-pending dedicated evaluation per ADR-025) materializes; see Evolution Path.
- **NATS.** Rejected: Core NATS's at-most-once delivery does not meet the reliability PIOS's own consistency principles require for business facts (PERSISTENCE_ARCHITECTURE.md Section 6); JetStream closes that gap but is a materially newer addition with less production track record than RabbitMQ's or Kafka's delivery guarantees, and NATS's JVM/Spring ecosystem tooling is comparatively immature — a weaker fit against the same Team Capability and Community Maturity reasoning ADR-025 already applied when selecting PostgreSQL.
- **Redis Streams.** Rejected: introducing Redis solely for Streams does not reduce operational surface, since PIOS has not adopted Redis for any other purpose (caching or otherwise) — it is a net-new dependency, not a reuse of one already justified. Redis's persistence model is a data store's durability retrofitted for streaming, not a purpose-built message-durability design, and it is less proven specifically as a reliable inter-service event backbone than RabbitMQ or Kafka.
- **Deferring broker selection further, continuing to rely on ADR-027's mechanism indefinitely.** Rejected: ADR-028 already established that ADR-027's mechanism does not deliver events between independently running service instances; continuing to defer would leave that gap open indefinitely, with no path to actually operating the MVP as more than a single co-located test process.

## Consequences

### Positive Consequences

- Resolves ADR-028's explicitly deferred question with a broker matched to PIOS's actual, currently-demonstrated needs rather than anticipated future scale.
- Gives every catalogued event a concrete, reliable delivery mechanism, with the generic multi-consumer pattern Notifications and Analytics already require satisfied natively.
- Keeps operational complexity proportionate to demonstrated need, consistent with ENGINEERING_GUIDELINES.md Section 3, while still meeting the reliability PERSISTENCE_ARCHITECTURE.md Section 6 already requires.
- Leaves a clear, non-foreclosed evolution path to Kafka specifically, if and when a documented need for long-term event replay materializes (most plausibly from Analytics's own future evaluation).

### Negative Consequences

- Introduces a genuinely new operational dependency (running and operating RabbitMQ) that did not exist before this decision — the same category of cost ADR-025 already accepted when introducing PostgreSQL.
- If PIOS's event volume or replay needs grow substantially beyond what is currently demonstrated, a future migration to or addition of Kafka becomes a real, non-trivial undertaking rather than something designed in from the start — an accepted tradeoff of not over-engineering ahead of need.
- Event schema versioning (how a catalogued event's shape evolves without breaking existing consumers) remains open, as already noted in ADR-028; this ADR does not resolve it.

## Evolution Path

This ADR selects RabbitMQ for PIOS's current, demonstrated event-delivery needs; it does not foreclose a different technology for a genuinely different future need:

- If Analytics's own dedicated future technology evaluation (anticipated but not finalized per ADR-025) determines it needs long-term historical event replay for derived-insight computation beyond what RabbitMQ Streams provides, introducing Kafka for that specific purpose is a legitimate future decision — to be recorded as its own ADR explicitly superseding or extending this one, consistent with ADR-015.
- A change to this selection for any other reason (operational experience, a demonstrated throughput ceiling, a team-capability shift) likewise requires a decision that explicitly supersedes this one, never a silent substitution.
- Event schema versioning strategy, independent of which broker carries the events, is now [ADR-030: Event Schema and Versioning Strategy](ADR-030-Event-Schema-Versioning-Strategy.md).
- How RabbitMQ itself is structured — exchanges, queues, publisher/consumer boundaries, and failure handling — is now [ADR-031: Event Infrastructure Foundation Architecture](ADR-031-Event-Infrastructure-Foundation-Architecture.md).

## Related ADRs

- [ADR-001: System Philosophy](ADR-001-System-Philosophy.md) — Long-Term Maintainability and No Premature Optimization, both directly weighed in this selection.
- [ADR-003: Event-Driven Domain](ADR-003-Event-Driven-Domain.md) — the events this broker carries.
- [ADR-004: API Style](ADR-004-API-Style.md) — the direct-interaction category this ADR does not affect.
- [ADR-015: Evolution Strategy](ADR-015-Evolution-Strategy.md) — governs any future change to this selection.
- [ADR-020: Integration Philosophy](ADR-020-Integration-Philosophy.md) — the event philosophy this broker operationalizes for external-facing occurrences too.
- [ADR-021: Database Technology Strategy](ADR-021-Database-Technology-Strategy.md) — the evaluation-framework pattern this ADR follows, adapted for messaging.
- [ADR-023: Backend Technology Decision](ADR-023-Backend-Technology-Decision.md) — Kotlin/Spring Boot, whose ecosystem fit is a direct evaluation criterion here.
- [ADR-025: Database Technology Decision](ADR-025-Database-Technology-Decision.md) — the concrete-selection precedent this ADR mirrors, including its own deferred Analytics/Notifications question this ADR's Evolution Path connects to.
- [ADR-026: Deployment Strategy](ADR-026-Deployment-Strategy.md) — the independent-deployability guarantee a reliable broker preserves.
- [ADR-027: MVP Integration Mechanism](ADR-027-MVP-Integration-Mechanism.md) — the provisional mechanism this decision's eventual implementation supersedes.
- [ADR-028: Production Integration Transport Decision](ADR-028-Production-Integration-Transport-Decision.md) — selects the "message broker" category this ADR fills with a specific product.

## References

- [PROJECT_CONSTITUTION.md](../PROJECT_CONSTITUTION.md), Section 4
- [EVENT_CATALOG.md](../EVENT_CATALOG.md)
- [INTERFACE_CONTRACTS.md](../INTERFACE_CONTRACTS.md), Section 4
- [PERSISTENCE_ARCHITECTURE.md](../PERSISTENCE_ARCHITECTURE.md), Section 6
- [ENGINEERING_GUIDELINES.md](../ENGINEERING_GUIDELINES.md), Section 3

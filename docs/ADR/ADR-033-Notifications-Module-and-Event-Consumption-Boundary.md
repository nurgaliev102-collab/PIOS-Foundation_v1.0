# ADR-033: Notifications Module and Event Consumption Boundary

## Status

Proposed

## Context

Notifications is one of PIOS's eight ratified capability-based architectural components (ADR-016, ADR-017, ADR-018: "Notifications — owns informing participants of information relevant to them"), consistently documented ever since across DOMAIN_MODEL.md, MODULE_STRUCTURE.md, INTERFACE_CONTRACTS.md, CONCEPTUAL_DATA_MODEL.md, LOGICAL_DATA_MODEL.md, DATABASE_DESIGN.md, PERSISTENCE_ARCHITECTURE.md, and PRODUCT_FOUNDATION.md. Unlike Order Management, Dispatch, Driver Management, and Passenger Experience — the four modules already scaffolded as independently deployable Gradle/Spring Boot services, three of which now also publish domain events through the RabbitMQ foundation ADR-028 through ADR-032 established — Notifications has never been scaffolded. No Gradle module, package, database, or commit anywhere in this repository's history creates one.

A recently attempted implementation task ("RabbitMQ Consumer Foundation v1.0") requested scaffolding Notifications, wiring a RabbitMQ consumer bound to `order-management.events`, and consuming `OrderSubmitted` specifically. Before any code was written, that task was stopped: it rested on two assumptions neither of which any approved document actually establishes — that Notifications is ready to be scaffolded as an implementation module today, and that `OrderSubmitted` specifically is a fact Notifications is authorized to consume. This ADR resolves both questions, kept explicitly separate, using only what the repository's already-approved documents establish — inventing neither.

## Problem

Two distinct questions, which must not be conflated:

**A. Module existence and readiness.** Is Notifications entitled to become an actual, independently deployable software module, and if so, what — if anything — still blocks scaffolding it today?

**B. Event consumption authorization.** Which concrete event(s), if any, is Notifications currently authorized to consume? Does creating the module imply authorization to consume `OrderSubmitted`, or any other specific event?

## Decision

### A. Module Existence

**Confirmed, not newly decided.** Notifications is already a ratified bounded context (ADR-017, ADR-018) with its own exclusively owned logical entity — Notification, "a record of what has been communicated to a participant" (CONCEPTUAL_DATA_MODEL.md Section 4; LOGICAL_DATA_MODEL.md Section 4) — its own persistence responsibility (PERSISTENCE_ARCHITECTURE.md Section 4: "Persists only the Notification logical entity"), and its own anticipated physical structure (DATABASE_DESIGN.md Section 5: "one physical record per communication sent, typically retained as an append-only historical record"). ADR-026 already commits all eight ratified modules, Notifications included, to independent deployment "from the point implementation begins." This ADR does not reopen or re-decide any of that — it confirms it explicitly for Notifications, in one place, since no single prior document stated it in the specific terms an implementation task needs.

**Confirmed entitlement is not the same as scaffolding readiness.** ADR-025 already, explicitly, identifies a still-open prerequisite specific to Notifications (and Analytics): *"Analytics and Notifications are explicitly not assigned PostgreSQL by this decision... that remains a dedicated, future evaluation for those two domains specifically."* ADR-025's own Negative Consequences already state: *"implementation of those two domains cannot fully begin until their own dedicated evaluation is complete."* This ADR does not override that — it reaffirms ADR-025's own gate as still binding, and notes it applies independently of, and in addition to, the event-consumption question in Part B.

### B. Event Consumption Authorization

**No concrete event-consumption relationship is authorized by this ADR. Specifically: consumption of `OrderSubmitted` by Notifications is NOT authorized here.**

Evidence that no such relationship is currently ratified:

- EVENT_CATALOG.md Section 9: *"the Domain Model does not name which specific events each of them [Notifications or Analytics] consumes, and this document does not invent that specificity."*
- INTERFACE_CONTRACTS.md Section 3: only a generic relationship exists — *"Notifications and Analytics each consume events from other modules generically, without a specific provider named for either."* Section 5's "Any Module → Notifications and Analytics (Generic)" contract states explicitly: *"No specific provider-event pairing is established by any approved document, and none is invented here."* Section 7's event table lists Notifications only as a generic possible consumer for every one of the six catalogued events, never as a committed consumer of any one of them.
- APPLICATION_ARCHITECTURE.md, Notifications entry: *"No use case in USE_CASE_CATALOG.md currently attributes a goal to Notifications... no application-level orchestration beyond event consumption is currently justified."* USE_CASE_CATALOG.md itself contains no use case naming any Notifications trigger, goal, or participant-facing communication.
- ADR-031's Queue Ownership section names `notifications.from-order-management` once, but only as an illustrative example of its naming *convention* ("for example: ... `notifications.from-order-management`") — it does not authorize that queue to exist or bind to any specific routing key, and ADR-031 itself never claims to resolve which events Notifications consumes.

Given no ratified product or business requirement identifies a specific participant-facing notification PIOS must deliver, or which event would trigger it, deciding now that Notifications consumes `OrderSubmitted` would be exactly the invention PROJECT_CONSTITUTION.md Section 8 forbids ("Never invent business rules, architecture, or product decisions that have not been documented or explicitly instructed"). The relationship is therefore left explicitly undecided; the missing decision is named in full in Unresolved Decisions, below, rather than filled by inference.

### Responsibility Boundary

Applies once Notifications is eventually implemented; establishes scope, not code:

- **Owns:** the Notification entity only — a record of what was communicated to a participant, append-only (DATABASE_DESIGN.md Section 5; LOGICAL_DATA_MODEL.md Section 4).
- **Never owns:** the underlying information that triggers a notification — the order, assignment, driver, or any other domain's own fact — stated identically across DOMAIN_MODEL.md Sections 5 and 7, LOGICAL_DATA_MODEL.md Sections 6 and 9, PERSISTENCE_ARCHITECTURE.md Section 4, and MODULE_STRUCTURE.md Section 3.
- **Decision content is real, but minimal.** DOMAIN_MODEL.md Section 7 attributes Notifications a genuine domain responsibility beyond passive transport: the Notification Delivery domain service *"determines... what is communicated to a participant in response to information obtained from other domains, without owning the underlying information that triggers it."* Notifications is therefore not a dumb pass-through adapter — some decision logic mapping a consumed fact to a communicated message is part of its ratified responsibility.
- **Explicitly NOT ratified, and NOT decided here:** notification preferences, message templates, or multi-channel delivery orchestration (channel selection, cross-channel retry, participant opt-in/opt-out). No approved document defines any of these; DOMAIN_MODEL.md Section 7's one-sentence domain-service description does not stretch to cover them, and inventing them now would exceed it. Actual delivery to an external channel (email, SMS, push) is, if and when it exists, an External Systems integration governed by ADR-020 (Integration Philosophy) — a boundary Notifications sits behind, not a technology this ADR selects.
- Consistent with No Premature Optimization (ENGINEERING_GUIDELINES.md Section 3): Notifications' ratified scope today is exactly "consume an authorized event, decide minimally what it means to inform a participant, and record that a communication occurred" — nothing broader is authorized.

### Module Scaffolding Requirements

Apply only once both Part A's database-technology prerequisite and Part B's event-consumption authorization are separately resolved; recorded now so a future implementation task has an unambiguous target:

- **Gradle module boundary.** A new `notifications` module declared in `backend/settings.gradle.kts`, structured with the same `domain` / `application` / `persistence` layering already used by `order-management`, `dispatch`, and `driver-management`; no shared module is introduced (MODULE_STRUCTURE.md Section 2, Dependency Direction).
- **Dependency rules.** Notifications depends on no other module's internals or physical storage (MODULE_STRUCTURE.md Section 5); any cross-module test-only reference follows the existing `testImplementation(project(...))` convention (INTERFACE_CONTRACTS.md Section 12) — never a production dependency.
- **Database ownership, if persistence is actually required.** Persistence is already justified in principle (DATABASE_DESIGN.md Section 5; PERSISTENCE_ARCHITECTURE.md Section 4) — Notifications will own a single database instance holding only the Notification entity, exclusively. The specific technology is not selected here: ADR-025 already scoped that as a dedicated, separate future evaluation for Notifications and Analytics, and this ADR does not perform it.
- **RabbitMQ consumer-owned topology, once Part B is resolved (per ADR-031, unmodified).** Notifications declares and owns its own queue(s), one per (Notifications, producer-exchange) pair, named `notifications.from-<producer-domain>`, bound only with the routing key(s) matching whatever specific event(s) a future decision actually authorizes — never a broad wildcard binding adopted by default, since a wildcard would consume every event a domain publishes regardless of whether Notifications has been authorized to act on any of them. Each queue has its own dead-letter queue, bounded retries with backoff, and Notifications' own consumer processing must be idempotent, consistent with ADR-031's Retry and Failure Handling Principles, unchanged here.

## Alternatives Considered

- **Authorize `OrderSubmitted` consumption now, since it is the only event with an implemented producer.** Rejected: an event already being implemented is evidence of publishing readiness, not evidence of a product requirement to notify anyone about it. Deciding otherwise would let architecture follow implementation convenience rather than the reverse, contradicting Architecture First (PROJECT_CONSTITUTION.md Section 4).
- **Treat the existing generic "Notifications may consume any event" contract (INTERFACE_CONTRACTS.md Section 5) as sufficient authorization for a specific implementation.** Rejected: that same contract explicitly states no specific provider-event pairing is established, and INTERFACE_CONTRACTS.md's own closing line requires a higher-priority document to resolve that silence before a lower one may act on it. A generic entitlement is not a specific authorization.
- **Decide nothing and leave the gap undocumented until the next implementation attempt.** Rejected: silence would leave a future task to rediscover the same two gaps from scratch. Recording the confirmed entitlement, the responsibility boundary, and the specific missing decisions now serves Traceability (ADR-001) even though implementation remains blocked.
- **Have this ADR itself decide the specific event-consumption relationship, as a pragmatic shortcut.** Rejected: an ADR is the correct authority to resolve EVENT_CATALOG.md's and INTERFACE_CONTRACTS.md's documented silence procedurally (PROJECT_CONSTITUTION.md Section 5 places ADRs above both), but it is not the correct authority to invent the underlying product requirement that silence is waiting on; that requires evidence this ADR does not have.

## Consequences

### Positive Consequences

- Replaces an unverified implementation assumption with an explicit, evidence-based answer, closing the exact gap the stopped RabbitMQ Consumer Foundation task exposed.
- Gives Notifications a concrete, minimal responsibility boundary consistent with every existing document, so a future implementation task has an unambiguous scope once its prerequisites clear.
- Surfaces ADR-025's already-existing database-technology gate alongside the event-consumption gap in one place, so both are visible together rather than discovered separately at different points in the future.

### Negative Consequences

- No implementation of Notifications can begin as a direct result of this ADR; two distinct prerequisite decisions remain open, each requiring separate follow-up work.
- The RabbitMQ Consumer Foundation task that prompted this ADR remains blocked — on Part B outright, and on Part A's database-technology gate for any persistence-backed component of it (for example, idempotent consumer storage).

## Unresolved Decisions

1. **Notifications' (and Analytics') database technology.** Already flagged as a dedicated future evaluation by ADR-025 itself; unchanged by this ADR.
2. **Which specific event(s) Notifications is authorized to consume, and why.** Requires a product/business decision naming a concrete participant-facing notification need, extending USE_CASE_CATALOG.md and/or PRODUCT_FOUNDATION.md first — consistent with those documents' own stated rule that only a higher-priority document may resolve their silence. Only after that exists should EVENT_CATALOG.md Section 9 and INTERFACE_CONTRACTS.md Sections 5 and 7 be extended to name a specific contract.
3. **Notification content and delivery scope.** Whether Notifications' responsibility ever extends beyond "record that a communication occurred" into preferences, templates, or multi-channel delivery orchestration is not decided here and would require its own future product decision and ADR if the need arises.

## Recommended Next Implementation Task

Not a RabbitMQ consumer implementation. Two candidate next steps, independent of each other and of this ADR:

- A product/business-level decision (Product Owner authority, PROJECT_CONSTITUTION.md Section 7) naming the specific participant notification(s) PIOS requires and the event(s) that trigger them — after which EVENT_CATALOG.md and INTERFACE_CONTRACTS.md would be extended, and a follow-up ADR could then authorize a specific consumption contract.
- A dedicated "Notifications and Analytics Database Technology Decision" ADR, mirroring ADR-025's own methodology, resolving the persistence-technology gate ADR-025 already identified.

Only once at least the event-consumption decision exists — and, for any persistence-backed component such as idempotent consumer storage, the database-technology decision as well — should a "Notifications Module Scaffolding" or "RabbitMQ Consumer Foundation" implementation task be reissued.

## Related ADRs

- [ADR-001: System Philosophy](ADR-001-System-Philosophy.md) — Traceability, served by recording this boundary explicitly rather than leaving it silent.
- [ADR-003: Event-Driven Domain](ADR-003-Event-Driven-Domain.md) — the event mechanism Notifications would consume through, once authorized.
- [ADR-004: API Style](ADR-004-API-Style.md) — confirms Notifications' interaction, like every module's, is scoped to declared events and interactions only, never direct access.
- [ADR-005: Data Ownership](ADR-005-Data-Ownership.md) / [ADR-009: Domain Isolation](ADR-009-Domain-Isolation.md) — the exclusive-ownership rule bounding what Notifications may and may not own.
- [ADR-015: Evolution Strategy](ADR-015-Evolution-Strategy.md) — governs any future change to this decision.
- [ADR-016: Architectural Layers](ADR-016-Architectural-Layers.md) / [ADR-017: Bounded Context Strategy](ADR-017-Bounded-Context-Strategy.md) / [ADR-018: Core Architectural Components](ADR-018-Core-Architectural-Components.md) — the existing ratification of Notifications as a bounded context, confirmed rather than reopened here.
- [ADR-020: Integration Philosophy](ADR-020-Integration-Philosophy.md) — governs any future external delivery-channel integration Notifications would sit behind.
- [ADR-025: Database Technology Decision](ADR-025-Database-Technology-Decision.md) — the still-open database-technology gate this ADR reaffirms rather than resolves.
- [ADR-026: Deployment Strategy](ADR-026-Deployment-Strategy.md) — the independent-deployability commitment already covering Notifications.
- [ADR-028: Production Integration Transport Decision](ADR-028-Production-Integration-Transport-Decision.md) / [ADR-029: Message Broker Selection](ADR-029-Message-Broker-Selection.md) / [ADR-030: Event Schema and Versioning Strategy](ADR-030-Event-Schema-Versioning-Strategy.md) / [ADR-031: Event Infrastructure Foundation Architecture](ADR-031-Event-Infrastructure-Foundation-Architecture.md) / [ADR-032: Reliable Event Publication Strategy](ADR-032-Reliable-Event-Publication-Strategy.md) — the transport, broker, schema, topology, and reliability foundation Notifications would consume through once B is resolved; none of their principles are altered by this ADR.

## References

- [PROJECT_CONSTITUTION.md](../PROJECT_CONSTITUTION.md), Sections 4, 5, 7, 8
- [MODULE_STRUCTURE.md](../MODULE_STRUCTURE.md), Sections 2–3, 5
- [EVENT_CATALOG.md](../EVENT_CATALOG.md), Sections 8–10
- [INTERFACE_CONTRACTS.md](../INTERFACE_CONTRACTS.md), Sections 3–5, 7
- [APPLICATION_ARCHITECTURE.md](../APPLICATION_ARCHITECTURE.md), Notifications section
- [USE_CASE_CATALOG.md](../USE_CASE_CATALOG.md)
- [DOMAIN_MODEL.md](../DOMAIN_MODEL.md), Sections 3, 5, 7–8
- [CONCEPTUAL_DATA_MODEL.md](../CONCEPTUAL_DATA_MODEL.md), Section 4
- [LOGICAL_DATA_MODEL.md](../LOGICAL_DATA_MODEL.md), Sections 4, 6, 9
- [DATABASE_DESIGN.md](../DATABASE_DESIGN.md), Section 5
- [PERSISTENCE_ARCHITECTURE.md](../PERSISTENCE_ARCHITECTURE.md), Section 4
- [PRODUCT_FOUNDATION.md](../PRODUCT_FOUNDATION.md), Sections 8–9
- [ENGINEERING_GUIDELINES.md](../ENGINEERING_GUIDELINES.md), Section 3

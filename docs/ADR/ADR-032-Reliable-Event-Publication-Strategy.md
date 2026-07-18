# ADR-032: Reliable Event Publication Strategy

## Status

Proposed

## Context

ADR-025 selected PostgreSQL, one database instance per domain, exclusively owned. ADR-029 selected RabbitMQ, with at-least-once delivery as an explicit design goal. ADR-031 established RabbitMQ's topology — one exchange per event-owning domain, one queue per consumer — and explicitly flagged, without resolving, "the dual-write problem: the case where a domain's own persistence write and its event publish are not part of one atomic operation, so one could succeed while the other fails." This ADR is that resolution.

This decision must also be reconciled with what already exists: Persistence Foundation v1.0 through PostgreSQL Persistence Dispatch v1.0 already implemented state-based PostgreSQL repositories for the Order, Driver, and Assignment aggregates (`PostgreSQLOrderRepository`, `PostgreSQLDriverRepository`, `PostgreSQLAssignmentRepository`), each persisting an aggregate's *current* state via plain JDBC. Application Persistence Wiring v1.0 wired each application service to call `save()` after a domain operation; no service yet actually publishes to RabbitMQ — that remains future implementation work this ADR governs the design of, not a change to what already exists.

## Problem

When a domain operation both changes an aggregate's persisted state (its own PostgreSQL database, ADR-025) and must produce the corresponding event on that domain's own exchange (ADR-029, ADR-031), and the database and the broker are two independent systems with no shared transaction, what strategy guarantees the state change and the event never diverge — without invalidating the state-based persistence already implemented for Order, Driver, and Assignment?

## Decision

**Transactional Outbox**, implemented as an **in-process polling relay** within each domain's own service — not change-data-capture (CDC) tooling, and not a change to the already-implemented state-based persistence model.

Concretely, as a conceptual shape (no schema, code, or configuration is defined here):

- **The same PostgreSQL transaction that saves an aggregate's new state also inserts an outbox record**, in a table owned by that domain's own database (no shared or cross-domain table, consistent with ADR-025's exclusive-ownership rule). The outbox record conceptually carries: which exchange and routing key it belongs to (per ADR-031's topology), the event's own versioned payload (per ADR-030's schema policy), and an ordering position consistent with the order operations committed in that domain.
- **Because the aggregate write and the outbox insert share one atomic transaction, they always succeed or fail together.** There is no window in which the state changes without a corresponding outbox record, or vice versa.
- **A relay, running within the same domain's own service, polls its own outbox table** for unpublished records, in commit order, and publishes each to the domain's own exchange (ADR-031), using RabbitMQ's publisher-confirm mechanism (already required by ADR-031). A record is marked published only once the broker confirms receipt.
- **If the relay or the broker fails between publishing and marking a record published, the same record is republished on the next poll.** This produces at-least-once delivery to the broker — exactly the guarantee ADR-029 already selected RabbitMQ for, and exactly what ADR-031 already requires every consumer to tolerate through idempotent processing.
- **Per-aggregate event ordering is preserved** by publishing outbox records in the order they were committed; the relay does not reorder or batch records out of sequence.
- **No aggregate's persistence model changes.** `Order`, `Driver`, and `Assignment` continue to be persisted as current state, exactly as already implemented; the outbox is an additional table and an additional, small relay responsibility layered alongside that existing work, not a replacement for it.

## Evaluation

| Criterion | Transactional Outbox (selected) | Direct Publish After Transaction | Event Sourcing |
| --- | --- | --- | --- |
| **Consistency** | Strong: the state change and the intent-to-publish are one atomic PostgreSQL transaction; the event is guaranteed to eventually reach the broker if the state change committed. | Weak: publishing after commit risks losing the event if the publish step fails after the commit succeeds; publishing before commit risks a "phantom event" for a change that then rolls back, violating ADR-003's "events represent completed facts" principle directly. | Strong in principle — the event log is the state — but does not, by itself, guarantee reliable delivery to RabbitMQ; a relay from the event log to the broker is still required, facing the same relay-reliability question the outbox already answers. |
| **Failure Handling** | Built-in: an unpublished or unconfirmed record is simply retried on the relay's next poll; no event is ever silently lost. | None built-in: requires bespoke, per-call-site compensation or reconciliation logic to approximate reliability, which is easy to get wrong and does not eliminate the failure window, only narrow it. | Requires the same relay-and-retry mechanism as the outbox to actually deliver reliably; event sourcing alone does not solve broker delivery any more completely than the outbox does. |
| **Complexity** | Moderate: one additional table per domain and one lightweight, in-process polling relay — proportionate to the specific problem being solved. | Apparently low, but achieving real reliability requires reintroducing outbox-like retry and reconciliation logic anyway, done ad hoc per call site rather than once, centrally. | High: requires re-architecting every already-implemented aggregate from state-based persistence to an append-only, replay-based event log — a wholesale redo of already-completed, committed work (Persistence Foundation v1.0 through PostgreSQL Persistence Dispatch v1.0), disproportionate to the specific problem of reliable publication. |
| **Compatibility with PostgreSQL-per-domain** | Excellent: the outbox table is one more table in the same already-existing, exclusively-owned per-domain database (ADR-025); no new infrastructure, no cross-domain access. | Compatible, but does not use PostgreSQL's transactional guarantee to solve the actual problem — the database and broker remain uncoordinated. | Possible (PostgreSQL can serve as an event store), but requires discarding the state-based repositories already implemented and tested for Order, Driver, and Assignment, and replacing them with an entirely different persistence model no approved document establishes a need for beyond solving this specific publication problem. |

## Alternatives Considered

- **Direct publish after the database transaction commits.** Rejected: the dual-write failure window is real and unmitigated — a committed state change whose publish subsequently fails leaves other domains permanently unaware of a fact that already happened, silently violating the consistency principles PERSISTENCE_ARCHITECTURE.md Section 6 already establishes.
- **Publish before the database transaction commits.** Rejected: risks publishing an event for a change that is then rolled back, producing a "phantom" fact that never actually occurred — a direct violation of ADR-003's principle that "an event represents something that has already happened."
- **Event Sourcing as the persistence model for every aggregate.** Rejected: solves a broader problem (an aggregate's full history as the source of truth, enabling replay, temporal queries, and audit) that no approved document currently establishes a need for, at the cost of discarding already-implemented, working, committed persistence for Order, Driver, and Assignment. Disproportionate to the specific reliability problem this ADR addresses, and contrary to No Premature Optimization (ENGINEERING_GUIDELINES.md Section 3).
- **Change-Data-Capture (CDC) tooling (e.g., Debezium) reading the outbox table via the PostgreSQL write-ahead log, instead of an in-process polling relay.** Considered and not selected now: CDC would typically add its own infrastructure (commonly Kafka Connect or an equivalent runtime) beyond RabbitMQ already selected in ADR-029, a genuinely heavier operational addition than an in-process poller, for a throughput and latency need no approved document currently establishes. Noted in Evolution Path as a legitimate future upgrade if polling proves insufficient.
- **A single, shared outbox table or relay service spanning multiple domains.** Rejected: would recreate exactly the shared-infrastructure problem ADR-025 and ADR-031 already reject for databases, exchanges, and queues.

## Consequences

### Positive Consequences

- Directly closes the dual-write gap ADR-031 explicitly left open, using a well-established, proportionate pattern rather than a bespoke or partial mitigation.
- Requires no change to the state-based persistence already implemented and committed for Order, Driver, and Assignment — the outbox is additive, not a replacement.
- Stays entirely within the already-established architecture: one more table in each domain's own already-exclusively-owned PostgreSQL database (ADR-025), publishing through the already-selected broker (ADR-029) and topology (ADR-031).
- Preserves per-aggregate event ordering, consistent with the causal expectations already implicit in DOMAIN_MODEL.md Section 12's lifecycle descriptions.

### Negative Consequences

- Adds a new table and a new, small ongoing responsibility (the relay) to every event-owning domain's own service — genuine, if modest, additional complexity.
- Introduces polling latency between a state change committing and its event actually reaching the broker — bounded and tunable, but not instantaneous; no approved document currently requires sub-polling-interval delivery, so this is an accepted tradeoff, not a violation of anything already established.
- Requires an eventual housekeeping strategy for published outbox records (archival or deletion) so the table does not grow unbounded — left as an implementation detail, not decided here.
- Does not, by itself, guarantee end-to-end exactly-once processing — it guarantees at-least-once delivery to the broker, which is why ADR-031 already requires every consumer to be idempotent; this ADR does not weaken or replace that requirement.

## Evolution Path

This ADR fixes the pattern (transactional outbox, in-process polling relay) and the guarantee it provides (at-least-once, order-preserving, transactionally consistent with the domain's own state change) — not implementation specifics. Anticipated follow-up work:

1. **Outbox table shape and relay implementation details** (polling interval, batch size, exact schema) — left to a future implementation task, consistent with this ADR's own "no implementation" scope.
2. **Housekeeping strategy** for published outbox records (archival, deletion, or retention window) — a genuine open question, not decided here.
3. **CDC as a future upgrade.** If polling latency or database load from frequent polling becomes a demonstrated problem, replacing the polling relay with CDC-based tooling reading the same outbox table is a legitimate future evolution — the outbox table's role as the durable, transactionally-consistent record of intent-to-publish does not change; only how it is relayed to the broker would.

Any change to the pattern itself (for example, adopting event sourcing after a genuine, documented need for its broader benefits emerges) requires a decision that explicitly supersedes this one, consistent with ADR-015.

## Related ADRs

- [ADR-001: System Philosophy](ADR-001-System-Philosophy.md) — No Premature Optimization, directly weighed against Event Sourcing's disproportionate cost.
- [ADR-003: Event-Driven Domain](ADR-003-Event-Driven-Domain.md) — "an event represents something that has already happened," which this ADR's ordering (never publish before commit) directly upholds.
- [ADR-005: Data Ownership](ADR-005-Data-Ownership.md) / [ADR-009: Domain Isolation](ADR-009-Domain-Isolation.md) — the exclusive-ownership rule the outbox table stays within (no shared table).
- [ADR-015: Evolution Strategy](ADR-015-Evolution-Strategy.md) — governs any future change to this pattern, including a possible future move to CDC or event sourcing.
- [ADR-021: Database Technology Strategy](ADR-021-Database-Technology-Strategy.md) / [ADR-025: Database Technology Decision](ADR-025-Database-Technology-Decision.md) — the per-domain PostgreSQL instance the outbox table lives inside.
- [ADR-026: Deployment Strategy](ADR-026-Deployment-Strategy.md) — the independent-deployability guarantee an in-process, per-domain relay preserves (no shared relay service).
- [ADR-028: Production Integration Transport Decision](ADR-028-Production-Integration-Transport-Decision.md) — the message-broker category this reliability guarantee serves.
- [ADR-029: Message Broker Selection for PIOS Event Infrastructure](ADR-029-Message-Broker-Selection.md) — RabbitMQ's publisher-confirm mechanism, which the relay relies on.
- [ADR-030: Event Schema and Versioning Strategy](ADR-030-Event-Schema-Versioning-Strategy.md) — the schema an outbox record's payload must conform to.
- [ADR-031: Event Infrastructure Foundation Architecture](ADR-031-Event-Infrastructure-Foundation-Architecture.md) — the topology this ADR publishes into, and the source of the dual-write problem this ADR resolves.

## References

- [PROJECT_CONSTITUTION.md](../PROJECT_CONSTITUTION.md), Section 4
- [PERSISTENCE_ARCHITECTURE.md](../PERSISTENCE_ARCHITECTURE.md), Section 6
- [DOMAIN_MODEL.md](../DOMAIN_MODEL.md), Section 12
- [ENGINEERING_GUIDELINES.md](../ENGINEERING_GUIDELINES.md), Section 3
- Persistence Foundation v1.0, Application Persistence Wiring v1.0, Persistence Quality Foundation v1.0, PostgreSQL Persistence Order/Driver Management v1.0, PostgreSQL Persistence Dispatch v1.0 (prior implementation work this ADR is reconciled against)

# ADR-030: Event Schema and Versioning Strategy

## Status

Proposed

## Context

EVENT_CATALOG.md Section 10 already establishes event ownership at the business level: the domain that owns the fact an event describes creates that event and is the sole authority on its meaning (ADR-005, ADR-009, ADR-019). ADR-010 already establishes the *general* versioning principle shared by every interface and event: a change that alters what a consumer can rely on must be a distinct, identifiable version from one that does not, without defining a specific numbering scheme, format, or tooling. INTERFACE_CONTRACTS.md Section 10 already states the general evolution pattern — a new version is introduced alongside the one it replaces, with a stated migration window, before the old version is retired.

None of this resolves event-*schema*-specific questions, and both EVENT_CATALOG.md ("no payload, field, or schema is defined for any event, consistent with ADR-003") and INTERFACE_CONTRACTS.md ("no event schema is defined here") deliberately leave every event's actual payload undefined. This ADR does not change that — it establishes the *policy* an event's eventual schema must follow, not the schema of any specific event.

ADR-028 selected a message broker as the transport category for every catalogued event; ADR-029 selected RabbitMQ as the specific broker and explicitly left event schema versioning as a distinct, still-open decision. This ADR is that decision.

## Problem

Given that PIOS's six catalogued events (EVENT_CATALOG.md) have no defined payload yet, and that ADR-010 established only the general principle that a breaking change is a distinct version from a non-breaking one, what specific strategy governs: who owns an event's schema, what counts as a breaking versus non-breaking schema change, what compatibility PIOS requires between versions, how a version is identified, and what a consumer must do to remain compatible across versions?

## Decision

### Event Ownership

Schema ownership follows the same exclusive-ownership rule EVENT_CATALOG.md Section 10 already establishes for event meaning: **the domain that owns an event also exclusively owns its schema and the sole authority to version it.** Concretely:

- Only Order Management may add, change, or version a field on OrderSubmitted, OrderCancelled, or OrderCompleted; only Dispatch may do so for OrderAssigned or AssignmentAccepted; only Driver Management may do so for DriverAvailabilityChanged.
- A consuming domain that needs additional information not currently in an event's schema asks the owning domain to extend it (or, if the need is for an immediate answer rather than a fact, uses a direct request per ADR-004) — it never defines its own copy of, or private extension to, another domain's event schema. This is the schema-level expression of the Forbidden Ownership principle already established in PERSISTENCE_ARCHITECTURE.md Section 5.
- Consuming an event, at any version, never grants the consumer authority over its meaning or its schema, consistent with EVENT_CATALOG.md Section 10's existing "Who consumes information" rule.

### Schema Evolution

An event schema evolves through exactly two kinds of change, distinguished by whether an existing, correctly-written consumer keeps working without modification:

- **Non-breaking (additive) change** — does not require a new version:
  - Adding a new optional field.
  - Adding a new event entirely (a new fact the owning domain now also publishes).
  - Widening what a field's value may be, in a way a tolerant-reader consumer (see Consumer Compatibility) already accommodates.
- **Breaking change** — always requires a new version (see Versioning Approach):
  - Removing or renaming a field.
  - Changing a field's type, unit, or meaning.
  - Changing a field from optional to required.
  - Narrowing what a field's value may be, in a way an existing consumer's logic could no longer handle.
  - Changing what triggers the event, or what business fact it represents.

This mirrors ADR-010's already-established principle — "a change which alters what a consumer can rely on is distinguishable from a change that does not" — applied specifically to event payload structure.

### Backward Compatibility

PIOS requires **consumer-side backward compatibility during a stated migration window**, not indefinite compatibility:

- When a breaking change is made, the owning domain publishes the new version *alongside* the version it replaces — both are available to consumers — for a stated migration window, before the old version is retired, exactly as INTERFACE_CONTRACTS.md Section 10 already requires generally for any contract.
- A consumer still depending on the old version continues to receive it, unaffected, until that window ends; a consumer that has migrated receives the new version. Neither producer nor consumer is required to support both versions forever.
- Cross-domain consistency during a migration window is never assumed to be simultaneous — a consumer may legitimately observe a mix of old- and new-version events from the same owning domain while migration is in progress, consistent with the eventual-consistency philosophy PERSISTENCE_ARCHITECTURE.md Section 6 already establishes for cross-domain consistency generally.
- Forward compatibility (an old consumer surviving a *new* version it has never seen) is not assumed or required; that is precisely what distinguishes a breaking change and is why the migration window, not indefinite mutual compatibility, is the mechanism PIOS relies on.

### Versioning Approach

- **Version is a property of the event itself, not of the transport.** An event's version identifies its own schema, independent of which broker, exchange, or routing key carries it (ADR-029); conflating the two would make an event's compatibility guarantees depend on transport configuration, which no approved document supports.
- **Non-breaking changes do not increment the version.** They are absorbed silently, since a tolerant-reader consumer already accommodates them; incrementing a version for every additive change would contradict ADR-010's own distinction between changes that alter consumer expectations and changes that do not.
- **A breaking change produces a new, explicitly numbered version of the same named event** (for example, a hypothetical second version of OrderAssigned) — the event's business name and meaning per EVENT_CATALOG.md's naming principles do not change; only its schema version does. The two versions coexist only for the stated migration window (Backward Compatibility, above).
- The specific mechanical representation of a version (a field in the event's own envelope, a distinct schema identifier, or another concrete technique) and any schema description or serialization technology (JSON Schema, Avro, Protobuf, or another) are **not selected here** — see Evolution Path.

### Consumer Compatibility

- Every consumer of a PIOS event follows the **tolerant reader** pattern: it reads only the fields it actually needs, ignores fields it does not recognize, and does not assume the schema it receives is exhaustive or closed — consistent with the No Hidden Assumptions principle already established in PROJECT_CONSTITUTION.md Section 4.
- A consumer that depends on a field introduced in a specific version must know, and be able to state, which version it depends on; it is not entitled to assume every producer instance has migrated to that version simultaneously (Backward Compatibility, above).
- A consumer never infers meaning beyond what the owning domain's schema and EVENT_CATALOG.md documentation state; if an existing event's documented meaning does not answer a consumer's question, that is a gap to raise with the owning domain, never a gap to fill by inventing an interpretation, consistent with the Never Invent Business Rules discipline already governing this project.

## Alternatives Considered

- **No formal versioning; change event schemas in place ("latest only").** Rejected: this is the exact alternative ADR-010 already rejected generically ("gives consumers no way to know whether a change they are about to depend on is safe"); applying it to event schemas specifically would silently break every existing consumer of a changed event, with no way for them to know in advance.
- **Indefinite backward *and* forward compatibility for every event, forever.** Rejected: requiring every future version to remain fully compatible with every past and hypothetical future consumer would either freeze event schemas permanently or require reader logic of unbounded complexity; it is not required by anything already approved and contradicts No Premature Optimization (ENGINEERING_GUIDELINES.md Section 3) by solving a problem — perpetual mutual compatibility — no approved document establishes a need for.
- **A shared, cross-domain event schema registry with joint change approval.** Rejected: this would require more than one domain to agree before a domain could evolve its own event, directly contradicting the exclusive-ownership rule already established in ADR-005, ADR-009, and EVENT_CATALOG.md Section 10 ("no domain creates an event describing a fact it does not own... no consuming domain may redefine, reinterpret, or override that meaning").
- **Deciding a specific schema format and tooling (Avro with a schema registry, Protobuf, JSON Schema) as part of this ADR.** Rejected for this ADR specifically: EVENT_CATALOG.md and INTERFACE_CONTRACTS.md have consistently and deliberately left every event's concrete payload undefined until now; selecting a serialization technology without first having any schema to serialize would be deciding an implementation detail ahead of the policy it should serve, and is better evaluated once a first real event payload is actually being defined. See Evolution Path.

## Consequences

### Positive Consequences

- Gives every event owner and every event consumer a shared, precise definition of "breaking" versus "non-breaking," resolving the ambiguity ADR-010 deliberately left general.
- Preserves exclusive event ownership (ADR-005, ADR-009, EVENT_CATALOG.md Section 10) by keeping schema evolution authority exactly where meaning authority already sits.
- Keeps the common case (additive, non-breaking evolution) cheap — no new version, no migration window — while still protecting consumers from breaking changes through a bounded, stated migration window rather than indefinite compatibility.
- Leaves the concrete schema format and any tooling decision to be made once genuinely needed, consistent with No Premature Optimization.

### Negative Consequences

- Requires every event-owning domain to consciously classify each schema change as breaking or non-breaking, and to run a migration window for the latter — genuine, ongoing discipline rather than a one-time decision.
- Requires every consumer to implement tolerant-reader deserialization rather than assuming a fixed, exhaustive schema — a real constraint on how consumers are built.
- Defers the concrete schema representation and format decision, meaning no event's actual payload can yet be implemented against a chosen serialization technology; that remains a genuine open step before implementation.

## Evolution Path

This ADR decides schema-evolution *policy* — ownership, the breaking/non-breaking distinction, compatibility scope, and the version-is-a-property-of-the-event principle — not a concrete schema representation. Two follow-up decisions are anticipated:

1. **Schema description and serialization technology** — a dedicated decision (or an implementation-level ENGINEERING_GUIDELINES extension) selecting how an event's schema is concretely described and encoded (for example, JSON Schema, Avro, or Protobuf) and whether a schema registry or similar tooling is used, made once a first real event payload is being defined.
2. **Per-event payload definition** — each of the six catalogued events' actual fields remain undefined until a dedicated task defines them, consistent with EVENT_CATALOG.md's and INTERFACE_CONTRACTS.md's existing, deliberate silence; any such definition must follow the ownership, evolution, and compatibility policy this ADR establishes.

Any future change to the policy itself — the breaking/non-breaking classification, the compatibility scope, or the ownership rule — requires a decision that explicitly supersedes this one, consistent with ADR-015.

## Related ADRs

- [ADR-001: System Philosophy](ADR-001-System-Philosophy.md) — Single Source of Truth and Traceability, both directly served by explicit schema ownership and versioning.
- [ADR-003: Event-Driven Domain](ADR-003-Event-Driven-Domain.md) — the events this policy governs the evolution of.
- [ADR-004: API Style](ADR-004-API-Style.md) — the direct-interaction category this ADR does not affect.
- [ADR-005: Data Ownership](ADR-005-Data-Ownership.md) / [ADR-009: Domain Isolation](ADR-009-Domain-Isolation.md) — the exclusive-ownership rule this ADR extends to schema authority specifically.
- [ADR-010: Versioning Strategy](ADR-010-Versioning-Strategy.md) — the general versioning principle this ADR makes concrete for event schemas.
- [ADR-015: Evolution Strategy](ADR-015-Evolution-Strategy.md) — governs any future change to this policy.
- [ADR-019: Conceptual Data Ownership](ADR-019-Conceptual-Data-Ownership.md) — the ownership mapping event creation already follows (EVENT_CATALOG.md Section 10).
- [ADR-028: Production Integration Transport Decision](ADR-028-Production-Integration-Transport-Decision.md) — selected the message-broker category this policy's versions will eventually be carried over.
- [ADR-029: Message Broker Selection for PIOS Event Infrastructure](ADR-029-Message-Broker-Selection.md) — selected RabbitMQ and explicitly deferred this decision.

## References

- [PROJECT_CONSTITUTION.md](../PROJECT_CONSTITUTION.md), Section 4 (No Hidden Assumptions), Section 7 (ADR Policy)
- [EVENT_CATALOG.md](../EVENT_CATALOG.md), Sections 2–3, 10
- [INTERFACE_CONTRACTS.md](../INTERFACE_CONTRACTS.md), Section 10 (Evolution Strategy)
- [PERSISTENCE_ARCHITECTURE.md](../PERSISTENCE_ARCHITECTURE.md), Sections 5–6
- [ENGINEERING_GUIDELINES.md](../ENGINEERING_GUIDELINES.md), Section 3 (No Premature Optimization)

# ADR-067: PIOS Core Bounded Context — Slice 01 (Participant History Projection)

## Status

**Ratified — 2026-09-10.**

Ratified by the Product Owner on 2026-09-10, consistent with `ADR-007` (architectural acceptance is a human act, transcribed here — not self-declared) and `ADR-015`, after the full read-only Taxi code audit (`docs/PIOS_CURRENT_STATE_AUDIT_2026-09-10.md`) and the constraints fixed in `docs/PIOS_CORE_SLICE_01_CONSTRAINTS.md`, and following the architectural review this ADR passed. No architectural content of this ADR was changed at ratification. Implementation of Slice 01 (`backend/core`, `pios_core`, migrations, `@RabbitListener`s, endpoints, deployment unit) is a **separate task**, to be carried out strictly within the Boundary, Participant Reference, Input Events, Write Boundary, Event Boundary, Failure Isolation, Data Model Scope, Idempotency, and QA / Production Gate sections below; production deployment of `pios-core` remains gated on the QA / safety criteria stated in "QA / Production Gate", and every restriction in "What This ADR Does Not Authorize" that is not conditioned solely on "until this ADR is ratified" still stands.

**Decision Date:** 2026-09-10.

**Product authority.** This ADR is the architectural record of the direction the product owner set on 2026-09-10, after commissioning a full read-only Taxi code audit (`docs/PIOS_CURRENT_STATE_AUDIT_2026-09-10.md`) and explicitly fixing the constraints in `docs/PIOS_CORE_SLICE_01_CONSTRAINTS.md`: build the future PIOS Platform Core **beside** the working Taxi product, not by rewriting it; begin with a read-only projection; do not activate `network-management`; do not create a second Relationship graph; do not touch any existing Taxi domain, event contract, or frontend. This document invents no business rule of its own (`CLAUDE.md`, "Never Invent Business Rules"); it records boundaries, not product behaviour.

**Why this needs an ADR and not an implementation note.** Per `.claude/CLAUDE.md` ("Decision Authority" / "Development Workflow", Stage 1): "domain model changes", "bounded context boundaries may change", "a new ADR may be required", and "responsibilities between modules are affected" are each explicit triggers for an architect-level decision *before* any code. Per `docs/MODULE_STRUCTURE.md` §8 ("Extension Strategy"): *"A new module is added only when a new bounded context is ratified through a decision that extends or supersedes ADR-017 and ADR-018, consistent with ADR-015."* Creating `backend/core` and `pios_core` is exactly that. This ADR is that decision, in proposed form.

**Scope discipline.** This ADR extends `ADR-017` (Bounded Context Strategy) and `ADR-018` (Core Architectural Components) by adding one new bounded context. It **amends no existing ADR's own Decision text**. It creates **no new cross-module contract** — `docs/INTERFACE_CONTRACTS.md` is unchanged, because Core in Slice 01 has no outbound contract at all. It does not touch `network-management`'s code, schema, or Sprint 7A tests.

---

## Context

### Where Taxi is today (from the code, not from documents)

`docs/PIOS_CURRENT_STATE_AUDIT_2026-09-10.md` established, by direct reading of source, migrations, `application.yml`, and the WinSW/systemd units:

- **6 backend bounded contexts** (`identity`, `driver-management`, `order-management`, `dispatch`, `network-management`, `passenger-experience`), each an independently deployable Spring Boot / Kotlin service with its own PostgreSQL database, no shared tables, no cross-database foreign keys (`ADR-005`/`ADR-009`/`ADR-019`), plus a standalone `ai-advisor`. Cross-module coupling is event-driven (RabbitMQ + transactional outbox) with one exception (a "dead/unused" synchronous REST path from `passenger-experience` to `order-management`).
- **The Taxi transactional core works end-to-end in production** (`piosapp.ru` and a second Tailscale-funnel environment): registration → Circle of Trust → order → First Refusal → Proposal (price required) → passenger confirmation → Assignment + Trip → ride progress → `Order` COMPLETED → driver milestones.
- **There is no single "Person".** The concept of "a human" exists in five uncoordinated representations (`identity.Identity`, `driver-management.Driver`, a bare `PassengerReference` string, `network-management.Person` — dead, and an owner Basic credential). The de-facto cross-system key is `identityId` (also the session token `sub`, also `Order.origin`, also `PassengerReference`, also `Proposal.passengerReference`).
- **`network-management` is a frozen scaffold.** It is not deployed anywhere (no WinSW unit, no systemd unit), no module reads it, it has no authentication and no events, and `ADR-064` already formally superseded it for new product work. `docs/PIOS_TAXI_V1_TARGET_ARCHITECTURE.md` §14: *"Taxi V1 has zero runtime dependency on Network Management."*
- **There is no unified "history of a participant".** History is a byproduct of `dispatch` tables (`assignments`, `trips`) plus counter aggregates in `driver-management` (`driver_milestones`, `driver_client_rides`). Nothing aggregates "everything that happened for one person, over time".

### Why PIOS Core appears beside Taxi (not as Taxi 2.0)

`docs/PIOS_PRODUCT_VISION.md` (§10–§12, §16) states the long-term intent: PIOS is infrastructure that lets an independent provider build their own client and commercial network; taxi is the *first* vertical, not the destination. §12 explicitly: *"the model needs room to scale beyond taxi"* and the existing bounded contexts *"must not be broken for the sake of a quick UI fix."*

The audit's semantic diff (`docs/PIOS_CURRENT_STATE_AUDIT_2026-09-10.md` §21–§23) concluded, against the actual code:

1. The correct place for a future PIOS Core is a **new bounded context**, not `network-management` (a scaffold without auth/events/health, and only one of several possible relationship models), not a shared library (forbidden by `backend/build.gradle.kts`), not an extension of `identity` (`ADR-038` deliberately separated the *authentication* identity from the *relationship* identity).
2. The two biggest risks are (a) accidentally creating a **second Relationship-graph authority**, and (b) prematurely declaring a **canonical Person ID** before a Core Data Contract decision exists.
3. The safest first step is a **read-only projection** built purely from events Taxi already publishes — zero write path into Taxi, zero new event, zero contract change — which lets Core exist, run, and accumulate real data without any risk to the working product, and gives later slices (Relationship, Trust) a foundation of facts rather than assumptions.

### Why Slice 01 is a History projection specifically

- It is the smallest coherent thing Core can *be*: a service that owns one read-model.
- **It takes no new decision about the final Core product model.** Slice 01 does not decide what PIOS Core ultimately becomes — Person, Relationship, Trust, Capability, Need, Opportunity, Network (see "Explicit Non-Decisions"). It is the *technical* implementation of one minimal History projection over the platform model that is **already ratified and already running in production**: "record what already happened, per participant" is bookkeeping over the existing, published event stream, not a new business rule and not a commitment to any future Core shape.
- Every input it needs is **already published in production today** (verified — see "Input Events" below).
- It closes a real, audited gap: `docs/PIOS_CURRENT_STATE_AUDIT_2026-09-10.md` §8.2 / §21 (the "History" row) — no unified participant history exists anywhere.
- It is trivially **outside the Taxi critical path**: if the projection lags or the service is down, no order, proposal, assignment, trip, First Refusal check, login, or screen is affected.

---

## Decision

**A new bounded context, `core`, is authorized (in proposed form), implemented as a new independently deployable Spring Boot / Kotlin module `backend/core` with its own PostgreSQL database `pios_core`, following the same hexagonal `domain / application / api / persistence` structure, plain-JDBC + Flyway persistence, and per-module migration path (`classpath:db/migration/core`) as the six existing modules.**

This decision **extends `ADR-017` and `ADR-018`** by adding one bounded context to the ratified set, as `docs/MODULE_STRUCTURE.md` §8 requires for any new module. It supersedes nothing.

**Slice 01's entire responsibility is a single read-only projection: for each participant, the ordered history of platform events concerning them, derived exclusively by consuming events Taxi modules already publish.** Core in Slice 01 has no write path into any other module, publishes no event, exposes one read endpoint plus a health endpoint, and is not on the Taxi critical path.

`ai-advisor`'s precedent (`ADR-056`) is the closest existing shape: a peripheral, owner-gated component whose failure never degrades the transactional core. Slice 01 Core is stricter still — it also never *reads* a Taxi module synchronously and never *sends* anything anywhere.

---

## Boundary

### What Core owns (Slice 01)

- The `pios_core` database and every table in it.
- One read-model: "participant → chronological list of platform events about them", derived from consumed events.
- The `participantReference` naming and lookup **within Slice 01 only** (see "Participant Reference").
- Its own idempotency ledger for events it consumes.

### What remains the authority of existing Taxi modules (unchanged)

| Concept | Authority (unchanged) |
| --- | --- |
| Authentication identity, session tokens (`sub`/`drv`), password credential | `identity` |
| `Driver`, availability, vehicle, milestones, earnings | `driver-management` |
| `Order`, `OrderStatus`, all order fields | `order-management` |
| `Proposal`, `Assignment`, `Trip`, their state machines, `stated_price`, First Refusal | `dispatch` |
| `Connection` (driver↔passenger via link), `primary_connections` (Circle of Trust) | `passenger-experience` |
| Owner Control Center AI advisor | `ai-advisor` |
| Abstract Person / Profile / Connection / Invitation scaffold | `network-management` (frozen, superseded — `ADR-064`) |

Core **derives from** these via events; it is the authority for **none** of them. Any future slice that would make Core the authority for a concept currently owned by a Taxi module requires its own superseding ADR.

---

## Participant Reference

- Slice 01 keys its read-model on **`identityId`** — the value already flowing through the system as the session token `sub` claim, `Order.origin`, `Proposal.passengerReference`, and `passenger-experience.Connection.passenger_reference`. This is the only cross-system participant key that exists in the working product today.
- In Core's schema and API this key is named **`participant_reference` / `participantReference`**, deliberately **not** `person_id` / `personId`.
- **This ADR does not declare `identityId` the canonical universal Person ID of PIOS.** The canonical Person ID — and whether it is `identityId`, a new Core-minted id, or something else, and how driver/passenger/owner representations reconcile to it — is a **Core Data Contract** decision, explicitly deferred (see "Explicit Non-Decisions"). Slice 01 uses `identityId` as a working reference precisely because doing so commits to nothing: if a later decision introduces a different canonical id, the Slice 01 read-model is a disposable, rebuildable projection (see "Consequences") and carries no authority that would need migrating.
- Core in Slice 01 does **not** mint identifiers, does **not** create participant records proactively, and does **not** attempt to bridge `identityId` to `network-management.PersonId` or to any other id space.

---

## Input Events

### Current Authorized Inputs

Core in Slice 01 is authorized to consume **only** the following events. Each is **already published in production today** on an **existing routing key** (verified against `docs/PIOS_CURRENT_STATE_AUDIT_2026-09-10.md` §17 and the publishing modules' outbox code), **and** each yields a **durable, participant-meaningful historical fact** — not a transient operational state. Core binds to the existing routing keys unchanged and requires `eventVersion: 1`, exactly as every existing consumer does.

| Event | Publisher | Routing key (existing) | Historical fact recorded | Participant reference |
| --- | --- | --- | --- | --- |
| `OrderSubmitted` | order-management | `order.submitted` | participant requested a ride | `payload.passengerReference` |
| `OrderCompleted` | order-management | `order.completed` | a ride requested by this participant completed | correlate on `payload.orderId` → prior `OrderSubmitted` |
| `OrderCancelled` | order-management | `order.cancelled` | a ride requested by this participant was cancelled | correlate on `payload.orderId` |
| `AssignmentCompleted` | dispatch | `assignment.completed` (legacy) | a driver completed a ride; via `orderId`, the passenger's ride completed | `payload.driverId` (+ `payload.orderId`) |
| `PrimaryConnectionDesignated` | passenger-experience | `primary.connection.designated` | this passenger designated a primary driver | `payload.passengerReference`, `payload.driverId` |

Binding notes:

- **`AssignmentCompleted` on the legacy `assignment.*` key, not `trip.*`.** `ADR-063` created a dual-publish window: `dispatch` emits both `AssignmentArrived/Started/Completed` (`assignment.*`, consumed by `order-management` and `driver-management`) and `TripArrived/Started/Completed` (`trip.*`, consumed by nobody). Slice 01 binds the legacy, battle-tested key. When the `ADR-063` migration window closes and `assignment.*` is retired, Core's binding is updated in a **future slice with its own note** — not now, and not as a reason to touch anything in `dispatch` today.
- `Order` submission has **no idempotency key** (audit §18) — a double-submitted order appears twice in Core's history. Recorded as an accepted limitation (Consequences), not worked around.

### Reserved Future Inputs

The following are **NOT consumed by Slice 01**, and this ADR grants **no permission to add a listener for any of them now**. Each requires either a future slice's own note (for events already published) or a separate contract decision with its own ADR (for events not published today).

| Event | Status today | Why deferred from Slice 01 |
| --- | --- | --- |
| `DriverAvailabilityChanged` (`driver.availability.changed`) | published | **Purely a Taxi operational state, not a durable PIOS-participant fact.** A driver toggles AVAILABLE/OFFLINE many times a shift; "was online at 14:03" carries no participant-history meaning and would flood the projection with churn. **Excluded from Slice 01.** Any future shift/working-time analytics is its own decision, not an extension of History. |
| `PrimaryConnectionCleared` (`primary.connection.cleared`) | published | The symmetric counterpart to `PrimaryConnectionDesignated` and a real participant fact. Left out only to keep the first cut minimal; a future slice may add it (already published, no contract change) with its own note. |
| `OrderAssigned` (`order.assigned`) | published, no consumer | Participant-meaningful ("a driver was connected to this person's order"), but overlaps `AssignmentCompleted` for a minimal history. Future slice, its own note. |
| `AssignmentArrived` / `AssignmentStarted` (`assignment.arrived` / `assignment.started`) | published | Ride-progress detail; not needed for a minimal "what happened" history. Future slice. |
| `TripArrived` / `TripStarted` / `TripCompleted` (`trip.*`) | published, no consumer | The `ADR-063` successors to `Assignment*`. Slice 01 stays on the legacy keys; a future slice switches when the migration window closes. |
| `OrderProposed`, `ProposalAccepted`, `ProposalDeclined`, `ProposalLapsed` | **NOT published** | `dispatch`'s `ProposalApplicationService` deliberately does not write these to its outbox (internal-only — its own KDoc). Core must not cause them to be published. Proposal-lifecycle history requires a **separate decision touching `dispatch`, with its own ADR** — not authorized here. |
| Any `identity` or `network-management` event | **do not exist** | Neither module publishes anything. |

**Rule.** If a public event does not carry a value Core needs, Core does **not** obtain that value by any other means (see "Write Boundary" and "What This ADR Does Not Authorize"). Core records the event without the missing value and waits for a future event contract that provides it.

---

## Write Boundary

- Core may write **only** to `pios_core`.
- Core makes **no** `INSERT` / `UPDATE` / `DELETE` against any other module's database. It holds no other module's datasource URL and no other module's `base-url`.
- **Core never reads any other module's database — not for lookup, not for history enrichment, not via a read replica, a cross-schema `SELECT`, a shared connection, or a synchronous query API.** `pios_core` is the only database Core connects to. This is stated explicitly for the enrichment case: if a consumed event's payload lacks a value Core would like on a history record (a passenger name, a pickup address, an order's full detail, a driver's profile, a `Trip` status), Core records the event **without** that value and does **not** fetch it from `order-management` / `identity` / `driver-management` / `dispatch` / `passenger-experience` by any route. The missing value becomes available only when a future event contract carries it — never by Core reaching into a Taxi datastore or Taxi API.
- Core makes **no** mutating HTTP call (or call of any kind) to any Taxi module. It imports no `com.pios.<other-module>` type. It copies **no** personal data from any module by any route (`ADR-005` / `ADR-059` S1 — reference, not ownership).
- The only data flowing *out* of a Taxi module *toward* Core is the RabbitMQ event stream those modules already publish for their own reasons. Core adds no new subscription that causes a publisher to behave differently.

---

## Event Boundary

- Core in Slice 01 **publishes nothing** — not to its own outbox, not to any exchange, not to any queue.
- Core has no `EventPublisher`, no `OutboxRepository` with a real write, no `RabbitTemplate.convertAndSend`. Its only RabbitMQ surface is `@RabbitListener`.
- Consequently there is **no new entry in `EVENT_CATALOG.md` or `INTERFACE_CONTRACTS.md`** for Core, and no consumer anywhere depends on Core.

---

## Failure Isolation

Slice 01 Core is a pure downstream consumer. The following must hold and must be verified in QA:

- If `pios-core` is **stopped, crashed, uninstalled, or never deployed**, every Taxi flow behaves exactly as it does today: order submission, Proposal creation / price / confirm / decline, Assignment + Trip creation, ride progress, `Order` completion, First Refusal, Circle of Trust designation, login/registration, and every frontend screen.
- If Core's consumer **lags** (its queue backs up) or **fails to process** a message (retries exhausted → its own dead-letter queue), no publisher blocks, no other consumer is affected, and no Taxi state is left inconsistent — Core's dead-lettering is entirely internal, using the **project's existing Spring AMQP retry/DLQ standard** (not a Core-specific mechanism), exactly as `dispatch`'s own consumer dead-lettering does (`ADR-031`).
- Core is on its **own port** (candidate `8087`, not colliding with `8081`–`8086` or `8091`), its **own database**, its **own systemd/WinSW unit**. It is never a dependency (`Wants=`/`After=`) of any Taxi service; other Taxi services are never a hard dependency of it beyond RabbitMQ and its own PostgreSQL.
- Core exposes `GET /v1/health/core` (owner Basic, mirroring existing `HealthController`s) so its own liveness is observable without any Taxi service consulting it.

---

## Data Model Scope

Slice 01's `pios_core` schema is the **minimum** projection and nothing more:

1. **A participant record** — keyed on `participant_reference`, recording first-seen time. Created lazily, on first event mentioning that reference. No name, no phone, no address, no profile — Core copies **no** personal data from any module (`ADR-005`/`ADR-059` S1 discipline: reference, not ownership).
2. **A history-event record** — one row per consumed, accepted event: `participant_reference`, a `kind` from a closed Core-local vocabulary, `occurred_at` (from the event envelope), and a bare correlation reference (e.g. `order_reference` / `driver_reference` string) sufficient to group events, never a copy of the referenced aggregate.
3. **A processed-event ledger** — `processed_events(event_id PK, processed_at)`, mirroring `dispatch`'s own `driver_availability_processed_events` / `order_management_processed_events` pattern exactly.

Explicitly **out of the Slice 01 data model**: any mutable relationship entity, trust level, capability, need, opportunity, transaction/payment record, contact record, or stored "insight". Any denormalized copy of an `Order`, `Driver`, `Proposal`, `Assignment`, `Trip`, or `Connection` beyond the bare correlation reference named above.

---

## Idempotency

- RabbitMQ is at-least-once (`ADR-029`/`ADR-031`); the same `eventId` may be delivered more than once.
- Core's consumption is **effectively-once** via its own `processed_events(event_id PK)` ledger: the ledger insert and the read-model write happen in **one `pios_core` transaction**; a redelivered `eventId` finds the ledger row already present and applies no second effect. This is the identical mechanism, and the identical guarantee, `dispatch` already proves for `DriverAvailabilityChanged` / `OrderCancelled` / `PrimaryConnection*`.
- Envelope validation (`eventType`, `eventVersion == 1`, required `payload` fields) happens before any extraction; a malformed or unsupported message throws.
- **Retry and dead-lettering use the project's existing standard, not a Core-built mechanism:** Spring AMQP's `RetryInterceptorBuilder` bounded retry (mirroring `dispatch`'s own `RabbitMQListenerContainerConfiguration` — 3 attempts, exponential backoff capped at 2000 ms) plus a `RejectAndDontRequeueRecoverer`-style dead-letter route (mirroring `dispatch`'s own `LoggingRejectAndDontRequeueRecoverer`), per `ADR-031`. Core introduces **no** custom retry loop, **no** home-grown backoff, **no** bespoke dead-letter handling. A message is never silently dropped or misinterpreted.
- No new deduplication mechanism beyond the `processed_events` ledger is introduced.

---

## Network Management

`network-management` remains **frozen and superseded** exactly as `ADR-064` Part 2 established:

- Not activated: no WinSW unit, no systemd unit, `pios_network_management` not created in any production environment, `networkmanagement/V*` migrations not applied anywhere new.
- Core does **not** read from, write to, import from, subscribe to, or otherwise depend on `network-management` — this ADR reaffirms `ADR-064`'s "no new feature may depend on it" for the PIOS Core case specifically.
- Its existing code, schema definition, and Sprint 7A tests are left untouched (`CLAUDE.md`, "Never Delete Documentation").
- Whether Core eventually absorbs `network-management`'s `Person` / `Profile` / `Connection` / `Invitation` **ideas** (with revised semantics) is a later, separate decision — not made here, and not made by activating the module.

---

## Explicit Non-Decisions

This ADR decides only the Slice 01 boundary above. It **does not decide**, and must not be read as implying, any of:

- **Canonical Person ID / Core Data Contract** — whether PIOS has one universal person identifier, what it is, and how `identityId` / `DriverId` / owner / future ids reconcile. Slice 01's use of `identityId` as `participantReference` is a working convenience, not this decision.
- **Relationship** — any mutable model of a link between two participants. `passenger-experience.Connection` remains the only relationship record; it is untouched.
- **Trust** — any general trust model. `primary_connections` (Circle of Trust) remains the only trust-like fact; it is untouched.
- **Capability / Need / Opportunity** — none of these become a Core entity. `Order` remains the (taxi-specific) demand record; `Proposal` remains the (taxi-specific) opportunity record; both stay in their current modules.
- **Network** — no participant graph is built. `network-management` stays frozen.
- **Payment / Transaction** — no money, payout, invoice, commission, or settlement concept. Payments remains an unbuilt, ratified-only capability (`ADR-018`).
- **AI / LLM** — Core connects no model and no provider. `ai-advisor` is untouched.
- **Migration or canonicalisation of existing Taxi `Connection` / `primary_connections`** — these are not moved, mirrored bidirectionally, or made subordinate to Core. Any future synchronisation direction is a separate decision.
- **Frontend surface for Core** — no screen, no route, no `apiClient` change. The one read endpoint exists for later consumption; nothing calls it yet.
- **The eventual disposition of `network-management`** (retire vs. repurpose) — still open, as `ADR-064` Evolution Path already recorded.

---

## QA / Production Gate

Consistent with `docs/PIOS_CORE_SLICE_01_CONSTRAINTS.md` C-7 and C-8:

1. **Isolated implementation and QA first.** Slice 01 is implemented and verified entirely in isolation — `backend/core` only, its own `pios_core_test` database for integration tests (hard-coded name, no env/property override, mirroring every module's `PostgreSQLTestDatabase`), local run, no deployment.
2. **Production deployment of `pios-core` is not authorized by this ADR.** It is authorized only after a QA / safety gate explicitly confirms **all** of:
   - **DB isolation** — `pios_core` name is distinct from every production database name; all Core tests run only against `pios_core_test`; there is no code path by which a Core test or build writes to any production-named database. (This criterion exists because the audit, §16 / `PIOS_REALITY_AUDIT.md` §19, records a real prior incident where a test wrote to production because test and production databases share one PostgreSQL instance and differ only by name.)
   - **No write path** — verified (code review + test) that Core issues no write to any database but `pios_core` and no mutating call to any Taxi module or contract.
   - **Failure isolation** — verified (explicit test / drill) that stopping `pios-core` leaves every Taxi flow unaffected, and that Core lag / dead-lettering does not block any publisher or other consumer.
   - **Idempotency** — verified (integration test) that redelivering any consumed `eventId` produces no duplicate read-model effect.
3. Until the gate passes, `pios-core` runs only on a developer machine against `pios_core_test` and `pios_core` local databases, never on `piosapp.ru`'s VPS or the HOME-PC production environment.
4. **No `./gradlew` invocation on any machine hosting a production PostgreSQL instance** during Slice 01 work — the test/production databases share one instance and are separated only by name (audit §16).

---

## Alternatives Rejected

- **Activate / extend `network-management` to host Core.** Rejected. It is a frozen scaffold with a self-generated identity space (`PersonId` unrelated to real accounts — `ADR-064` Context), no authentication on any endpoint, no events, no health check, and `ADR-054` + `ADR-064` already rejected it once each for adjacent features. Reviving it as the Core authority would create exactly the second Relationship-graph the audit (§22) and the product owner both warned against.
- **Extend `identity` to become Core.** Rejected. `ADR-038` deliberately separated the *authentication* identity (`identity`) from the *relationship* identity, with a documented self-critique of why merging them is wrong. `identity` is narrow (register / login / associate driver) and must stay so.
- **A shared library / shared module of Core domain code linked into Taxi modules.** Rejected. `backend/build.gradle.kts` states plainly: the root build file declares *"plugin versions … only — never shared business code or domain logic"*; `docs/MODULE_STRUCTURE.md` §4 forbids shared capabilities carrying domain logic. It would also couple deploy cycles, violating `ADR-026`.
- **A package inside an existing module (e.g. `order-management` or `dispatch`).** Rejected. There is no monolith to add a package to; every capability is already a separate service, and placing a cross-cutting "participant history" inside one vertical's module makes that module the accidental owner of a platform-wide concern.
- **Change existing Taxi modules to emit new "history" events or a participant-scoped event.** Rejected — direct violation of constraints C-3 / C-10 and of the whole "beside, not rewrite" premise. Slice 01 must work from what is already published.
- **Have Core read Taxi databases directly (a read replica, a cross-schema `SELECT`, or a synchronous query API).** Rejected. Violates `ADR-005` / `ADR-009` (a module never reads another's tables), reintroduces cross-module coupling the whole architecture avoids, and — for a projection that only needs "what happened" — the event stream already carries exactly that, immutably and in order.
- **Do nothing until a full Core Data Contract (canonical Person ID, Relationship, Trust) is decided.** Rejected. Slice 01 takes **no** new product decision itself — it only projects the already-ratified, already-running platform model (see Context, "Why Slice 01 is a History projection"). That larger Core Data Contract decision, when it is taken separately, benefits from real data about what participants actually do; Slice 01 produces that data at zero risk to Taxi. Blocking a risk-free read-only projection on a product decision it does not make is the "over-document, never ship" trap `docs/PIOS_CONSOLIDATION_IMPLEMENTATION_READINESS_REVIEW.md` §14 explicitly names.
- **Enrich history by reading a Taxi database or Taxi API when an event payload is missing a value.** Rejected — see "Write Boundary". It would reintroduce exactly the cross-module coupling (`ADR-005` / `ADR-009`) the architecture avoids. Core records what the event carries and waits for a contract that carries more.
- **Build a Core-specific retry / backoff / dead-letter mechanism.** Rejected — Core uses the project's existing Spring AMQP standard (`ADR-031`) unchanged; a bespoke one would be new infrastructure to maintain for no benefit.

---

## Consequences

### Positive

- **Zero risk to the working product.** No write path into Taxi, no new event, no contract change, no frontend change, not on the critical path, fully isolatable. The worst failure mode is "Core's history is stale or missing" — invisible to every Taxi user.
- **PIOS Core exists as a real, running bounded context** — a concrete foundation for later slices (Relationship, Trust, Capability) to build on, instead of designing them against the codebase from documents.
- **The read-model is disposable and rebuildable.** Because Core only projects from an immutable event stream and is the authority for nothing, its schema can be dropped and rebuilt from event history (or from a future replay mechanism) if a later decision changes the canonical participant id or the projection shape. Nothing depends on Core, so nothing breaks when Core changes.
- **Precedent-consistent.** Same module pattern as the six existing contexts; same outbox-less consumer + idempotency-ledger + dead-letter pattern `dispatch` already proves; same "peripheral, owner-gated, failure-isolated" posture as `ai-advisor` (`ADR-056`), only stricter.
- **`network-management`'s disposition question is not reopened** — this ADR reaffirms its frozen status rather than adding a new "should we use it?" thread.

### Negative

- **A seventh backend service to operate.** Another database (`pios_core`), another migration history, another systemd/WinSW unit, another queue and dead-letter queue, another health endpoint to watch. `docs/PIOS_PATH_TO_PUBLIC_LAUNCH.md` B4 already notes the single-machine deployment is a single point of failure; Core adds resource load there (mitigated: it is not on the critical path, so it can be the first thing shed under pressure).
- **Slice 01's history is incomplete by construction.** It consumes only the **five** Current Authorized Inputs, on the legacy routing keys. It does **not** see Proposal lifecycle (never published), does **not** see driver availability changes (excluded as a transient operational state, not a participant fact), and inherits `Order` submission's lack of idempotency (audit §18) — a double-submitted order appears twice. It also cannot see anything that happened *before* Core is deployed (no backfill; the projection is complete only from deployment forward, the same disclosed limitation `ADR-043` accepted for its own timestamps).
- **`identityId`-as-`participantReference` is a soft commitment.** Even though the read-model is disposable, once a Core read endpoint is consumed by anything, changing the participant key is a coordinated change. Slice 01 mitigates this by having nothing consume the endpoint yet, and by the neutral `participantReference` naming — but the risk that "temporary" hardens into "load-bearing" is real and is named here.
- **The `ADR-063` dual-publish window is a latent maintenance item.** When `assignment.*` is retired in favour of `trip.*`, Core's binding must move with it — a future slice's obligation, easy to forget.
- **QA/safety-gate discipline depends on people, not infrastructure.** Test/production database isolation is still "the hard-coded name in the test file", not a separate instance or container (audit §16). Core adds one more place that discipline must hold.

---

## What This ADR Does Not Authorize

- Creating `backend/core`, `pios_core`, any migration, any `@RabbitListener`, any endpoint, any WinSW/systemd unit — until this ADR is ratified.
- Any write, of any kind, to any database other than `pios_core`, or any mutating call to any Taxi module.
- **Any read of any database other than `pios_core`** — including a read replica, a cross-schema query, a shared connection, or a synchronous query API — for lookup or history enrichment. If a public event lacks a value, Core does not acquire it by any other means until a contract provides it.
- **Any custom / Core-specific retry, backoff, or dead-letter mechanism.** Core uses the project's existing Spring AMQP standard (`ADR-031`) only.
- **Adding a listener for any Reserved Future Input** (`DriverAvailabilityChanged`, `PrimaryConnectionCleared`, `OrderAssigned`, `AssignmentArrived`/`Started`, `Trip*`, any Proposal event). Each needs its own future slice note or, where the event is not published, its own separate ADR touching the publishing module.
- Publishing any event from Core, or adding any row to `EVENT_CATALOG.md` / `INTERFACE_CONTRACTS.md`.
- Any change to `Order`, `Proposal`, `Assignment`, `Trip`, `Connection` (either module), `primary_connections`, `Identity`, `Driver`, `dispatch`, `order-management`, `driver-management`, `passenger-experience`, `identity`, `ai-advisor`, or any of their migrations or event contracts.
- Any change to `frontend/`.
- Activating, reading, writing, or depending on `network-management`; creating any new Person/identity concept; declaring a canonical Person ID.
- Deploying `pios-core` to any production environment before the QA/safety gate above passes.
- Running `./gradlew` on a machine hosting a production PostgreSQL instance.
- Deciding Relationship, Trust, Capability, Need, Opportunity, Network, Payment, AI integration, or migration of existing Taxi `Connection` / Circle of Trust.

---

## Related ADRs

- **[ADR-017: Bounded Context Strategy](ADR-017-Bounded-Context-Strategy.md)** / **[ADR-018: Core Architectural Components](ADR-018-Core-Architectural-Components.md)** — extended by this ADR (one new bounded context added), per `MODULE_STRUCTURE.md` §8. Neither is superseded.
- **[ADR-005: Data Ownership](ADR-005-Data-Ownership.md)** / **[ADR-009: Domain Isolation](ADR-009-Domain-Isolation.md)** / **[ADR-019: Conceptual Data Ownership](ADR-019-Conceptual-Data-Ownership.md)** — applied unmodified: Core owns `pios_core` only, reads no other module's tables, copies no other module's data.
- **[ADR-026: Deployment Strategy](ADR-026-Deployment-Strategy.md)** — independently deployable module, own unit, own port.
- **[ADR-029](ADR-029-Message-Broker-Selection.md) / [ADR-031](ADR-031-Event-Infrastructure-Foundation-Architecture.md) / [ADR-032](ADR-032-Reliable-Event-Publication-Strategy.md)** — the consumer + idempotency-ledger + dead-letter pattern Core reuses; Core adds a consumer only, no publisher.
- **[ADR-037: Network Management Module Bounded Context Extension](ADR-037-Network-Management-Module-Bounded-Context-Extension.md)** — the frozen scaffold; its Decision text is not amended.
- **[ADR-064: Referral Visibility — Network Management Formally Superseded](ADR-064-Referral-Visibility-No-Network-Management-Activation.md)** — the direct precedent for "do not activate `network-management`, do not create a second identity/relationship system"; reaffirmed here for the PIOS Core case.
- **[ADR-056: Control Center AI Advisor](ADR-056-Control-Center-AI-Advisor.md)** — the closest existing shape: a peripheral, owner-gated, failure-isolated component. Slice 01 Core is stricter (no synchronous reads, no outbound anything).
- **[ADR-038: Identity Module Bounded Context Foundation](ADR-038-Identity-Module-Bounded-Context-Foundation.md)** — why authentication identity and relationship identity are kept separate; the basis for rejecting "extend `identity`".
- **[ADR-063: Trip as a New Dispatch-Owned Aggregate](ADR-063-Trip-as-a-New-Dispatch-Owned-Aggregate.md)** — the dual-publish window Core's `assignment.*` binding must eventually track.
- **[ADR-055: Session Authentication and Password Credential](ADR-055-Session-Authentication-and-Password-Credential.md)** — the session token whose `sub` claim is the `identityId` Core uses as `participantReference`.
- **[ADR-015: Architecture Decision Record Process](ADR-015-Architecture-Decision-Record-Process.md)** — supersession discipline for any future change to this ADR's Boundary or Participant Reference sections.

## References

- `docs/PIOS_CORE_SLICE_01_CONSTRAINTS.md` — the pre-implementation charter this ADR converts into an architectural decision; every constraint C-1…C-10 is reflected above.
- `docs/PIOS_CURRENT_STATE_AUDIT_2026-09-10.md` — the read-only Taxi code audit this ADR is conditioned on: §16 (database boundaries + isolation incident), §17 (event / outbox map, verifying the Input Events list), §20 (production safety), §21–§25 (semantic diff, recommended Core location, Slice 01 boundary).
- `docs/PIOS_PRODUCT_VISION.md` §10–§12, §16 — the "beside Taxi, room to scale beyond taxi" intent.
- `docs/MODULE_STRUCTURE.md` §8 — the rule that a new module requires a decision extending `ADR-017`/`ADR-018`.
- `docs/PIOS_REALITY_AUDIT.md` §19 — the test-writes-to-production incident underpinning the QA/safety gate's DB-isolation criterion.
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/persistence/OrderSubmittedFirstRefusalListener.kt`, `.../persistence/DriverAvailabilityChangedListener.kt`, `.../persistence/RabbitMQListenerContainerConfiguration.kt`, `.../persistence/LoggingRejectAndDontRequeueRecoverer.kt`, `.../application/ProposalApplicationService.kt` (KDoc on why Proposal events are unpublished) — read, not modified; the listener/retry/DLQ pattern Core's consumer mirrors, and the boundary it respects. (Core mirrors the *structure* of `DriverAvailabilityChangedListener`; it does **not** consume `DriverAvailabilityChanged` itself — that event is a Reserved Future Input, excluded as a transient operational state.)
- The product owner's direction of 2026-09-10 (in the task commissioning this ADR).

## Files Changed

**This edit round (final corrections, 2026-09-10):** `docs/ADR/ADR-067-PIOS-Core-Bounded-Context-Slice-01.md` only. Changes: (1) reframed "no product decision" as "takes no new decision about the final Core product model; technically implements a minimal History projection from the already-ratified model"; (2) split Input Events into **Current Authorized Inputs** (5 events) and **Reserved Future Inputs** (no listener permitted now); (3) **excluded `DriverAvailabilityChanged`** from Slice 01 — it is a transient Taxi operational state, not a durable participant fact; (4) fixed that retry/DLQ uses the project's existing Spring AMQP standard, not a Core-built mechanism; (5) added a hard prohibition on reading any Taxi/Identity/Driver/Dispatch/Passenger database or API for history enrichment — if a public event lacks a value, Core does not obtain it until a contract provides it.

**Ratification (2026-09-10):** `docs/ADR/ADR-067-PIOS-Core-Bounded-Context-Slice-01.md` (Status section only — `Proposed — awaiting ratification` → `Ratified`, no architectural content changed) and `docs/README.md` (ADR index row + scope note added, per the narrow-scope precedent the index's own notes already set). No code, module, database, migration, deployment file, event contract, or frontend file created or modified.

No code, module, database, migration, deployment file, event contract, or frontend file is created or modified by this ADR. `docs/README.md`'s ADR index is known-incomplete (its own note at the table foot: ADR-043 through ADR-066 are unindexed, "the files themselves … remain authoritative") — a row for this ADR should be added by the same task that ratifies it, not by this proposal.

# ADR-062: Primary Driver / First Refusal — Passenger Experience → Dispatch Contract

## Status

**Accepted.**

**Decision Date:** 2026-09-03.

**Basis.** Reviewed for internal consistency against `ADR-034` Part 2, `MODULE_STRUCTURE.md` Section 5, `ADR-005`/`ADR-009`/`ADR-019`, `ADR-054`, and `ADR-055` in `docs/PIOS_TAXI_IMPLEMENTATION_READINESS_REVIEW.md` Section 1 — no material defect found. Accepted per explicit Human Architect / Product Owner direction (Task 10B, "Architecture Ratification & Document Reconciliation"), consistent with `ADR-007`'s own rule that architectural acceptance is a human act, not a self-declared one. Per `ADR-007`, this status recorded here transcribes that acceptance; it does not itself constitute it.

**What acceptance authorizes and does not authorize, restated for clarity.** Accepted as an **architectural contract** — the ownership model, the event-fed-projection mechanism, the minimum data shape, and the constraints below are now ratified. **Not authorized by this acceptance**: any code, migration, endpoint, or configuration change (none exists yet); the response-window value; the exact command/endpoint shape a caller uses to trigger First-Refusal-aware proposal creation (both remain explicitly OPEN, per this ADR's own "Explicitly not decided" list below); any change to `POST /v1/proposals`'s own current lack of authentication (a separate, pre-existing condition, tracked in `docs/PIOS_TAXI_ARCHITECTURE_RATIFICATION.md` Section 6, not resolved by this ADR).

## Context

`docs/PIOS_TAXI_PRODUCT_DECISIONS.md` (Product Owner-authority decision, `PROJECT_CONSTITUTION.md` Section 7) ratifies First Refusal: when a passenger with a Primary Driver submits an order, that driver receives first opportunity to accept before the order proceeds to normal matching, on decline, timeout, or ineligibility. Primary Driver is Circle of Trust's already-implemented `Connection.isPrimary` fact (Passenger Experience, `ADR-054`) — passenger-controlled, never ownership, never exclusivity.

This creates an architectural problem `docs/PIOS_TAXI_PRODUCT_DECISIONS.md` Section 19 already named and explicitly declined to resolve itself, deferring it to this ADR: `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 2 currently states, as a **[RATIFIED absence]**, *"Dispatch. No ratified role at all. INTERFACE_CONTRACTS.md Section 5 lists exactly four justified module-to-module contracts; none gives Dispatch any access to Personal Client Relationship information."* (That count is now five, per `ADR-053`'s own addition of the Order Management → Dispatch `OrderCancelled` contract — the statement's substance, "Dispatch has no access to this information," is unaffected by the count changing.) First Refusal cannot function without Dispatch gaining exactly the access this statement says it does not have.

**Current implementation, traced directly from source (not inferred from documentation):**

- Order submission is passenger-driven and direct: the frontend (`RideRequest.tsx`) calls Order Management's `POST /v1/orders` directly, with `passengerReference` set to the passenger's own Identity id. Passenger Experience's own order-submission code path (`OrderSubmissionCoordinator`, `PassengerOrderSubmissionApplicationService`, `RestClientOrderSubmissionClient` — "Tranche 2: Passenger Experience REST Transport") exists in source but has **no controller wiring it to any HTTP entry point** — it is unreachable today.
- The one already-shipped precedent for "propose to a specific known driver without Coordinator involvement" is Sprint 7B (Personal Network Flow MVP): immediately after order submission, the same frontend calls Dispatch's `POST /v1/proposals` directly with `{orderId, driverId: driverCode}`, where `driverCode` is the URL's own inviting-driver id — not Circle of Trust's `isPrimary`, a different fact entirely, tied to the invitation link the passenger arrived through rather than to any stored relationship. `POST /v1/proposals` (`ProposalController.createProposal`) has **no authentication check at all** — neither `sessionTokenVerifier` nor `ownerCredentialGate` is invoked on this endpoint, unlike its sibling accept/decline endpoints.
- Circle of Trust's own read API, `GET /v1/connections?passengerReference=`, is **session-authenticated for the passenger's own browser only** (`ADR-055`; requires the query parameter to equal the caller's own token `sub` claim, 403 otherwise) — it has no service-to-service authentication path today, and was not designed for a backend caller.
- The Coordinator (`/coordinator`, owner-credential-gated) is the only mechanism today by which an order not auto-proposed to a specific driver ever reaches one — a human, not an automated fallback, and visible only through the Coordinator's own manual REST-polling console (`todayData.ts`).
- Dispatch already has a proven precedent for "a small, derived, local fact projected from another module's own event, never a synchronous cross-module call": `DriverAvailabilityRecord` (`com.pios.dispatch.application`), fed exclusively by `DriverAvailabilityChanged` (Driver Management → Dispatch, `INTERFACE_CONTRACTS.md` Section 5), carrying nothing beyond `driverReference`/`available`.

## Problem

Does PIOS's already-ratified module-isolation architecture (`ADR-005`, `ADR-009`, `ADR-019`, `MODULE_STRUCTURE.md`) permit a new Passenger Experience → Dispatch contract carrying Primary Driver information, and if so, what is its shape — command/REST, event-fed projection, or client-orchestrated?

## Decision

**A new contract is authorized: Passenger Experience → Dispatch, event-fed, following the same shape and justification precedent as `ADR-053`'s own Order Management → Dispatch addition** (the "sixth" justified relationship in `INTERFACE_CONTRACTS.md` Section 5, alongside the five it already lists).

**Ownership (Part 2 of the originating task, answered explicitly):**
- **Source of truth**: Passenger Experience's own `Connection`/`isPrimary` (`ADR-054`), unchanged. Passenger Experience alone may create, modify, or delete this fact.
- **Consumed routing signal**: Dispatch receives and stores a **local, derived, eventually-consistent projection** — never a copy treated as authoritative, never write access, mirroring `DriverAvailabilityRecord` exactly:

```kotlin
// Illustrative shape only — not implementation, not created by this ADR.
data class PrimaryDriverRecord(
    val passengerReference: PassengerReference,
    val primaryDriverId: DriverReference
)
```

- **Who may modify it**: Passenger Experience only, via the existing `setPrimaryConnection`/`removeConnection` endpoints (`ADR-054`, `ADR-055`) — unchanged, no new write path.
- **Who may read it (within Dispatch)**: only the component that decides whether to trigger First Refusal for a new order — never exposed further (no new query endpoint for any external caller to read Dispatch's own copy).
- **Staleness handling**: the projection is eventually consistent, exactly as `DriverAvailabilityRecord` already is — a brief window between a passenger changing their primary driver and Dispatch's own projection catching up is accepted, the same tradeoff this codebase already accepts for availability. First Refusal is a courtesy first opportunity, never a guarantee (`docs/PIOS_TAXI_PRODUCT_DECISIONS.md` Section 3) — a stale read producing a first-refusal offer to a passenger's *previous* primary driver, occasionally, does not violate any ratified invariant, since no invariant claims the projection is instantaneous.
- **If the information is unavailable** (no `Connection` exists, no `isPrimary` set, or Dispatch's own projection has no entry for this passenger): treated identically to "no Primary Driver" — the order proceeds directly to normal matching (Coordinator-visible), exactly as it does today for every passenger who has no Circle of Trust relationship at all. No error state, no blocking.

**Contract mechanism (Part 3 of the originating task, four options evaluated):**

| | A — Dispatch synchronously calls Passenger Experience's REST API | B — Order event carries relationship data | C — Passenger Experience publishes a new event, Dispatch projects it (chosen) | D (named here as "E" in the originating analysis) — client-orchestrated (frontend queries PE, then calls Dispatch) |
|---|---|---|---|---|
| Coupling | High — Dispatch becomes synchronously dependent on Passenger Experience's own availability at request time | High — couples Order Management to Passenger Experience *and* to Dispatch in one event, two new dependencies for one contract | Low — Dispatch depends only on an event stream, never on Passenger Experience being reachable at request time | None at the backend level — no new module-to-module contract at all |
| Consistency | Strong (always current) but at the cost of a hard runtime dependency | Only as current as Order Management's own read at submission time | Eventually consistent (Section above) | Eventually consistent, and additionally depends on the client's own good behavior |
| Latency | Adds a synchronous hop to every order path with a Primary Driver | No added hop at read time, but couples Order submission's own latency to a new dependency | No added hop — the projection is already local when needed | No backend hop, but the client itself performs two sequential calls |
| Failure behavior | Dispatch's own First Refusal check fails whenever Passenger Experience is down — directly threatens Order Management/Dispatch's own independent-deployability guarantee (`ADR-026`) | An event carrying stale/wrong data at submission time can never be corrected later without a new event | Degrades to "no Primary Driver" cleanly if the projection hasn't caught up yet — never blocks | Degrades the same way, but with no server-side record of whether the check was even attempted |
| Ownership clarity | Muddied — Dispatch would be making a live decision using another module's live state, blurring "who decided" | Muddied — Order Management would be relaying a fact it doesn't own, without ever having asked to own it | Clean — Passenger Experience remains sole owner; Dispatch's projection is explicitly, structurally a derived cache, exactly like `DriverAvailabilityRecord` | Clean in principle, but enforcement of "First Refusal actually happened" lives nowhere on the server |
| Scalability | Every order creation now costs a network round-trip to a second service | One-time cost, but on the hot path of every order submission regardless of whether a Primary Driver exists | No per-order cost beyond an in-process projection lookup | No backend cost, but no backend guarantee either |
| Future vertical applicability | Poor — a future vertical's own "relationship-aware routing" would need the same synchronous coupling repeated | Poor — ties a generic Order concept to a Taxi-specific relationship field | Good — the event-fed-projection pattern is exactly `DriverAvailabilityChanged`'s own already-proven, reusable shape | Fair, but only if every future client reimplements the same orchestration correctly |
| Operational complexity | New service-to-service auth path required (today's `GET /v1/connections` is passenger-session-only, `ADR-055`) | New Order Management-side dependency to resolve at submission time | Reuses existing outbox/RabbitMQ/DLQ machinery already proven for `DriverAvailabilityChanged` (`ADR-029`, `ADR-031`) | Lowest to build (the endpoint it would call, `POST /v1/proposals`, already has zero authentication — Context, above) — but this ease is itself the weakness: no server-side enforcement point exists for a rule the product decision treats as a first-class Matching Rule |

**Chosen: Option C.** Passenger Experience publishes a new domain event — **`PrimaryConnectionDesignated`** (fired by `setPrimaryConnection`) and its natural counterpart **`PrimaryConnectionCleared`** (fired by `removeConnection`, or by a future explicit "unset primary" action if one is ever added) — through the same transactional-outbox mechanism already proven by every other event-publishing module in this codebase (`ADR-029`, `ADR-031`, `ADR-032`). Dispatch consumes both, maintaining `PrimaryDriverRecord` as a local projection, following `DriverAvailabilityRecord`'s own exact precedent: a small, derived, non-authoritative cache, with its own dead-letter queue, following the same per-consumer DLQ pattern already proven for Dispatch's other two consumer queues (`RabbitMQTopologyConfiguration`/`RabbitMQOrderManagementTopologyConfiguration`).

**Trigger, kept unchanged from the existing, deliberately-not-`OrderSubmitted`-driven pattern (Task 7 Section 9, reaffirmed):** the projection answers "does this passenger have a Primary Driver" passively; it does not itself trigger anything. The active trigger for attempting First Refusal remains an explicit act — today, that act is the frontend's own order-submission flow (the same client that already calls Order Management then Dispatch sequentially, Sprint 7B's own proven shape) or the Coordinator's own manual action. This ADR does not change who or what triggers a Proposal to be created; it changes only what Dispatch's own `ProposalApplicationService`-adjacent logic may consult, in-process, once triggered, to decide whether the first Proposal should target the passenger's Primary Driver before falling through to Coordinator-visible normal matching.

**Explicitly not decided or authorized by this ADR:**
- The exact command/endpoint shape callers use to invoke "propose, honoring First Refusal if applicable" — this is implementation detail, out of this ADR's own architectural scope, and out of this task's scope entirely (no code is written here).
- The response-window duration for "does not respond" (`docs/PIOS_TAXI_PRODUCT_DECISIONS.md` Section 4 — explicitly OPEN, not invented here).
- Any change to `POST /v1/proposals`'s own current lack of authentication — noted as a pre-existing condition (Context, above), not addressed or fixed by this ADR, and worth its own separate security review before First Refusal adds a second reason for that endpoint to be called.

## Constraints

- No shared database, no direct table access across modules (`ADR-005`, `ADR-009`) — the projection is Dispatch's own table, in Dispatch's own database, fed only by events.
- Passenger Experience gains no new write path, no new authority beyond what `ADR-054` already grants it.
- Dispatch gains no authority over `Connection`/`isPrimary` — it may read its own derived copy, never write back, never treat its copy as more authoritative than Passenger Experience's own.
- No ranking, scoring, or weighting is introduced — `PrimaryDriverRecord` answers a binary question (does a primary driver exist, and who) consistent with `docs/PIOS_TAXI_PRODUCT_DECISIONS.md` Section 10's matching-rules/ranking-algorithm boundary.
- This does **not** authorize any Network Management integration — Network Management remains exactly as deferred as `ADR-037`'s own Consequences section already left it; this contract concerns Circle of Trust (Passenger Experience) only.

## Consequences

- `INTERFACE_CONTRACTS.md` Section 5 will need a new "Contract: Passenger Experience → Dispatch" entry, and Section 7's event table will need two new rows (`PrimaryConnectionDesignated`, `PrimaryConnectionCleared`), the same kind of additive update `ADR-053` already made for `OrderCancelled` — **not performed by this ADR**, reserved for the implementation task that actually builds this, consistent with how `ADR-053` itself preceded rather than silently rewrote that document.
- `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 2's own statement — *"Dispatch. No ratified role at all"* — is **superseded by this ADR, for this specific, narrow purpose only** (consuming a Primary Driver signal for First Refusal). It is not superseded generally: Dispatch still has no ratified role in Personal Client Relationship information for any purpose beyond what this ADR authorizes (no ranking use, no visibility beyond the projection described above). A future documentation-reconciliation pass should update that document to record this narrow exception explicitly, rather than leave the two documents silently disagreeing — **not performed by this ADR**.
- `ADR-034` Part 2's classification of Personal Client Relationship as a "future candidate input, never ratified" to Assignment Policy is **partially superseded**: it is now a ratified input for the specific, binary First Refusal mechanism this ADR authorizes. It remains exactly as unratified as before for any future ranking/scoring use — this distinction should be made explicit in `ADR-034` itself when that document is next revisited, not assumed by silence.
- Dispatch's own database gains one new table (the `PrimaryDriverRecord` projection) and one new consumer queue/DLQ pair, following existing, proven patterns — no new infrastructure technology, no new topology mechanism.
- Implementation of this contract is a precondition for implementing First Refusal itself (`docs/PIOS_TAXI_PRODUCT_DECISIONS.md` Section 4) — this ADR does not implement either.

## Traceability

| Source | Relationship |
| --- | --- |
| `docs/PIOS_TAXI_PRODUCT_DECISIONS.md` Sections 3–4 | Authorizes the product direction (Primary Driver, First Refusal) this ADR converts into an architectural contract |
| `docs/PIOS_PRODUCT_DECISION_GATE.md` Section 4 (Decision 1) | Originating analysis of routing options; this ADR resolves the *contract* question that document's own Section 19 left open |
| `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 2 | Partially superseded — see Consequences |
| `ADR-034` Part 2 | Partially superseded — see Consequences |
| `ADR-053` | Direct precedent for how a new module-to-module event contract is added additively, referenced not duplicated |
| `ADR-054` | Source of truth for `Connection`/`isPrimary`, unchanged by this ADR |
| `ADR-055` | Governs the existing session-authentication model this ADR does not alter |
| `ADR-005`, `ADR-009`, `ADR-019` | Data ownership/domain isolation rules this ADR applies, not exceptions to |
| `ADR-029`, `ADR-031`, `ADR-032` | Outbox/event-infrastructure precedent this ADR's chosen mechanism reuses |
| `ADR-037` | Confirms Network Management is untouched by this ADR |

## Files Changed

`docs/ADR/ADR-062-Primary-Driver-First-Refusal-Passenger-Experience-Dispatch-Contract.md` (new, this Status section amended on acceptance). The follow-up updates named above — `docs/README.md`'s ADR table, `INTERFACE_CONTRACTS.md` Section 5/7, and `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 2 — were performed alongside this acceptance, in Task 10B, consistent with `ADR-053`'s own precedent of the accepting task performing its own documentation-reconciliation pass rather than deferring it indefinitely. `ADR-034` Part 2 received the same treatment.

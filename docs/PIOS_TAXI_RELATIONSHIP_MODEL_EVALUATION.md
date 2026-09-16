# PIOS Taxi — Relationship Model Evaluation

**Type:** architecture evaluation and decision document (architect role). **Not an implementation authorization.**
**Date:** 2026-09-16. **Branch:** `pios-product-main`.
**Scope:** how far the code implements `Client → Relationship → Request → Commitment → Execution → Handoff → Settlement` versus today's `Client → Order → Driver`, and what to build next.

**Basis of this document, stated honestly (read this first).** Everything below is derived from direct reads of the current source and of ratified documents under `docs/`, cited by file and line. It is **not** derived from any user observation, pilot session, or evidence-log entry. Under this project's own Sprint gate (`docs/PIOS_PRODUCT_EVIDENCE.md:93-97` — *«Какое доказательство из журнала делает этот Sprint необходимым? Если ответа нет — не начинаем»*), an architectural analysis is **not** an admissible Sprint basis. The evidence journal contains exactly one real entry, `E-001` (`docs/PIOS_PRODUCT_EVIDENCE.md:84-87`), a registration-validation defect unrelated to any stage below; the three `E-000` entries above it are explicitly labelled *«только демонстрация формата»* (`:76`). Therefore **any Sprint arising from this document must first register a hypothesis in `docs/PIOS_PRODUCT_HYPOTHESES.md` under the second admissible basis (`PIOS_PRODUCT_EVIDENCE.md:115-119`, «Дополнение 2026-08-01»), by the Product Owner, before implementation starts.** This document deliberately registers none — see Part 8.

---

## Part 1 — Summary table

| Model stage | Current code reality | Gap severity | Primary citations |
|---|---|---|---|
| **Client** | No `Client`/`Passenger` aggregate exists anywhere. A client is an opaque string carried as `Order.origin` / `Proposal.passengerReference` / `Connection.passengerReference`. `identity` owns an authentication anchor with four fields and no passenger-role concept; ADR-075 added a guest state so the string exists before any account does. | **Partial** — the *identifier* is first-class and stable; the *client as a domain concept* is absent. | `backend/identity/src/main/kotlin/com/pios/identity/domain/Identity.kt:33-48`; `backend/order-management/src/main/kotlin/com/pios/ordermanagement/domain/Order.kt:163`; `docs/ADR/ADR-075-...md:45-59`, `:61-63`, `:98-105` |
| **Relationship** | A join row, not an entity. `Connection` carries `id, driverId, passengerReference, createdAt` and nothing else — no status, no lifecycle, no terms. Primary designation lives in a *separate* table. Driver-side CRM depth (ride count, last ride, repeat flag) is computed **in the browser** from proposals/assignments/orders, not stored or queryable. | **Partial** — queryable per-side, but has no lifecycle of its own and no server-side depth. | `backend/passenger-experience/src/main/kotlin/com/pios/passengerexperience/domain/Connection.kt:15-20`; `backend/passenger-experience/src/main/resources/db/migration/passengerexperience/V1__create_connections.sql:10-16`; `docs/ADR/ADR-054-...md:132` ("No lifecycle beyond creation, designation and removal"); `frontend/src/pages/DriverHome/DriverHome.tsx:557-584` |
| **Request** | Conflated with Order. `Order` *is* the request: it carries `explicitDriverIntent`, `requestedDriverId`, `requestedPickupAt`, `notes`, `passengerCount`, and is `SUBMITTED` from creation. There is no pre-commitment "ask" object distinct from the order record. Dispatch separately holds a *routing obligation* (`dispatch_requests`) which is the closest thing to a Request-as-process, but it is Dispatch-internal plumbing, not a client-facing concept. | **None (by design) / mislabeled** — the concept exists, it is simply called `Order`. | `Order.kt:160-174`, `:234-282`; `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/DispatchRequestApplicationService.kt:43-59`, `:97-128` |
| **Commitment** | Genuinely modeled, and the strongest stage. Offer (`Proposal`, `OPEN`) is deliberately not commitment; the committing moment is two-sided — driver states a price (`proposePrice`, → `PRICE_PROPOSED`), passenger confirms (`confirmPrice`, → `ACCEPTED`), and only then is an `Assignment` created. | **None** | `backend/dispatch/src/main/kotlin/com/pios/dispatch/domain/Proposal.kt:13-15`, `:194-208`, `:227-234`, `:335-355`; `backend/dispatch/src/main/kotlin/com/pios/dispatch/domain/Assignment.kt:176-193` |
| **Execution** | Modeled as `Trip` (ADR-063), created in-process alongside `Assignment`, and since Task 12 it is the *sole validator* of arrive/start/complete. `Assignment`'s identical ride-progress fields survive as a deliberately frozen read-only remnant. The HTTP surface is still `/v1/assignments/{id}/arrive|start|complete`. | **None** (with one known duplication debt: two parallel status enums, dual-published events) | `backend/dispatch/src/main/kotlin/com/pios/dispatch/domain/Trip.kt:61-67`, `:95-125`; `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/DispatchAssignmentApplicationService.kt:74-96`, `:208-211`; `backend/dispatch/src/main/kotlin/com/pios/dispatch/api/AssignmentController.kt:251,279,307` |
| **Handoff / доверенная замена** | **Does not exist in any form.** Full-codebase search for handoff / substitution / reassignment / backup-driver / sub-fleet concepts returns zero domain matches in `*.kt` and zero in `*.tsx`. The only hits are unrelated English words ("delegates to", "transfer encoding"). Additionally it is *currently prohibited* by a ratified decision. | **Missing — and blocked by ADR-054 Part 5** | Verified by repo-wide search; `docs/ADR/ADR-054-...md:126` and its 2026-09-15 supersession note `:128` ("still no Team/Fleet/crew/delegation mechanism of any kind") |
| **Settlement** | No settlement, payment, cash-acknowledgment, or earnings-ledger concept. The only money fact in the system is a **free-text string the driver typed** (`Proposal.statedPrice`), forwarded verbatim on `AssignmentCompleted` and summed by Driver Management under a digits-only rule, shown only to that driver. `billing` is a subscription container with no tariff, no provider, and gates nothing; it is deliberately undeployed. | **Missing** | `Proposal.kt:92-113`; `docs/ADR/ADR-065-...md:58-62`, `:74-94`; `backend/driver-management/.../domain/DriverMilestones.kt:34-49`; `backend/billing/.../domain/Subscription.kt:53-60`; `docs/ADR/ADR-074-...md` (container only) |

### Cross-cutting evaluations requested

| Topic | Current code reality | Gap severity | Citations |
|---|---|---|---|
| **Network (Fallback Tier 2)** | Tier 1 (Trusted) **is** implemented — `TrustedDriverRepository` projection exists and is queried first. Tier 2 is skipped unconditionally, by design and in code comments. No driver↔driver edge exists other than ADR-073's single-hop `invitedByDriverId`. | **Missing, correctly deferred** | `backend/dispatch/.../application/FallbackDispatchApplicationService.kt:41-58`, `:159-162`; `docs/ADR/ADR-068-...md:129-134`, `:152-168` |
| **Trust** | Only signal is `driver_milestones.completedRidesCount` — driver-facing only. Nothing numeric reaches the passenger, per an explicit, still-standing ban. | **Partial by ratified intent, not a defect** | `backend/driver-management/.../domain/DriverMilestones.kt:34-42`; `docs/DRIVER_IDENTITY_DESIGN_DECISION.md:33-37` |
| **Recurring orders** | Nothing. Repeat = the passenger manually submits another order with `explicitDriverIntent`/`requestedDriverId`. `requestedPickupAt` is a single future instant on a single order and is explicitly *not* a calendar reservation. No schedule, no series, no conflict check. | **Missing** | `Order.kt:79-90`, `:168`; `docs/PIOS_TAXI_COMMERCIAL_EXECUTION.md:59` ("an offer for a future time, **not** a driver calendar reservation") |

---

## Part 2 — Stage-by-stage findings

### 1. Client — implicit, not first-class

`Identity` (`identity/domain/Identity.kt:33-38`) holds exactly `id`, `phone`, `driverId`, `createdAt`. It is an authentication anchor only; its own KDoc (`:6-11`) states it is "independent of any role (Driver, Passenger)". There is no `Passenger`, `Client`, or `Customer` aggregate in any module.

What a client *is*, operationally, is an opaque string that appears under four different names:
- `Order.origin` — an `OrderOrigin` value object whose only invariant is non-blankness, and which `Order.kt:71-72` is careful to say is "the passenger's own participant reference, unrelated to any location";
- `Proposal.passengerReference` (`Proposal.kt:50-63`);
- `Connection.passengerReference` (`Connection.kt:18`);
- `primary_connections`' own passenger column (ADR-054).

ADR-075 is the reason this works at all: a guest `Identity` is minted at order time with `phone = null` and no credential (`ADR-075:45-59`), and upgrade is **in place** so `IdentityId` never changes (`:61-63`). That is a genuinely good property for a relationship model — every relationship fact keeps pointing at the same string when a guest later registers.

**Assessment:** Partial. The identifier is stable and cross-module-safe. What is missing is any place that answers "what do we know about this client" — the answer today is scattered across three modules and one browser-side computation. Building a `Client` aggregate would be a new bounded-context concern and is *not* recommended now (see Part 5); the cheaper closure is at the Relationship stage.

**Named honest limitation, not a gap to fix silently:** `ADR-075:98-105` states plainly that phone ownership is never verified, there is no identity recovery, and there is no sybil resistance. Any "client" concept built on top inherits all three.

### 2. Relationship — a join row with no lifecycle

`Connection` is four fields (`Connection.kt:15-20`) over a table with one unique constraint (`V1__create_connections.sql:10-16`). Its own KDoc says so explicitly: *"Deliberately minimal: no status, no type, nothing beyond the pairing and when it was recorded."*

`ADR-054:132` ratified that this is intentional: **"No lifecycle beyond creation, designation and removal. No confirmation by the driver, no suspension, no expiry from inactivity."** Primary designation is a *separate* table (`V2__create_primary_connections.sql`), not a field on the relationship — by ADR-054 Part 4's design, so setting a primary can never create a connection as a side effect.

The REST surface (`passenger-experience/api/ConnectionController.kt:82-206`) is five endpoints, all passenger-session-gated, with `POST` idempotent (201 first time, 200 after). The *driver* can list their connections (`:119-136`) but the response is deliberately thin — `ADR-054` Part 4: "the driver-facing response gains nothing".

The "Мой бизнес" CRM depth the tracker calls **Реализовано** is real but is entirely a **frontend derivation**: `clientRideStatsByReference` (`frontend/src/pages/DriverHome/DriverHome.tsx:557-584`) builds ride count / last-ride / repeat-flag per passenger from already-loaded proposals, assignments and order details, with a documented "No new request, no new backend endpoint" (`:536`). The only server-side relationship depth is the aggregate counter `repeatClientsCount` on `DriverMilestones` (`DriverMilestones.kt:39`), which is a count, not a per-client record queryable by the driver.

**Assessment:** Partial, and this is the highest-leverage gap in the whole model. A "Relationship" in the target model has a lifecycle (formed → active → dormant → ended) and carries facts (how many rides, when last, on what terms). Today it has none of those server-side, and adding them touches `ADR-054:132` directly.

**Note on `network-management`:** a second, entirely separate `Connection` exists (`network-management/domain/Connection.kt:16-26`) — directed `Person → Person` with a `ConnectionType`. It is not the passenger↔driver relationship, is used by nothing, and `ADR-064` formally supersedes it for new work. It must not be pressed into service as "the Relationship aggregate".

### 3. Request — conflated with Order, and that is probably correct

There is no Request object distinct from Order. `Order` is created already `SUBMITTED` (`Order.kt:258`) and carries the entire ask: `destination`, `pickupAddress`, `requestedPickupAt`, `passengerCount`, `notes`, `explicitDriverIntent`, `requestedDriverId` (`:160-174`). ADR-076 moved the named driver onto the order itself, and `submit` enforces the one real invariant between them — `requestedDriverId` may not be set without `explicitDriverIntent` (`:253-255`).

The closest thing to a separate Request-as-process is Dispatch's `dispatch_requests` routing obligation (ADR-077): a durable per-order record with a 2-minute window and a 5-second retry (`DispatchRequestApplicationService.kt:148-152`), which terminates in `DispatchExhausted` → `OrderStatus.UNFULFILLED` (`:84-88`, `Order.kt:208-214`). That is a routing lifecycle, not a client-facing request lifecycle.

**Assessment:** No real gap. Splitting "Request" out of "Order" would be renaming, not capability. I recommend explicitly recording that `Order` **is** the Request stage, rather than building a second object — otherwise a future sprint will build one and create two sources of truth for the same fact.

### 4. Commitment — the strongest stage; no gap

This is already modeled better than the target sketch implies. There are three distinct moments, each with its own state:

1. **Offered, not committed** — `Proposal` in `OPEN`. Its KDoc is explicit that this is deliberately not an Assignment: *"Assignment represents an already-settled outcome, created only once this fact has resolved positively — never a pending one"* (`Proposal.kt:13-15`).
2. **Driver commits with terms** — `proposePrice` (`:194-208`) moves to `PRICE_PROPOSED` and requires a non-blank price. Its KDoc records the rule's origin as a Product Owner instruction of 2026-09-05: a driver must name a price before a ride is confirmed.
3. **Passenger accepts the commitment** — `confirmPrice` (`:227-234`) → `ACCEPTED`, and only then does `Assignment.create` run (`Assignment.kt:176-193`).

Refusals are modeled as separate facts: `decline`, `lapse`, `withdraw` (ADR-053), `declinePriceProposal` (`:255-262`) — the last of which deliberately closes the request rather than rerouting, citing *"PIOS does not substitute a random driver for the one a client came to"* (`:241`). That sentence is directly relevant to Handoff and is quoted again in Part 4.

**Assessment:** None. Do not touch this. The one real gap adjacent to it is recovery after decline/lapse, which ADR-077 explicitly did **not** cover (`ADR-068:177`: *"once any proposal exists the routing row leaves `PENDING` forever and a later decline or lapse triggers nothing"*), and which that ADR says requires its own decision.

### 5. Execution — modeled, with known duplication debt

`Trip` (ADR-063) is created in-process the moment an `Assignment` is created (`DispatchAssignmentApplicationService.kt:208-211`), and since Task 12 it is the sole validator of arrive/start/complete (`:76-80`). `Assignment`'s own `arrive/start/complete`, its three timestamp fields, and three of its five `AssignmentStatus` values are kept as **"a frozen, read-only remnant of pre-Trip history, not a second live source of truth"** (`:93-94`), because deleting them would strand real pilot rows.

Both event families are published on every transition — `Assignment*` for Order Management's already-wired consumer and `Trip*` alongside (`:105-112`). `TripCreated` carries no event at all, by design (`Trip.kt:154-162`).

**Assessment:** None as a capability. Two debts worth naming: (a) two status enums for one fact; (b) the HTTP surface is still assignment-shaped (`AssignmentController.kt:251,279,307`). Neither is urgent — but (b) matters for Handoff, because those three endpoints authorize on `verified.drv != assignment.driver.driverId` (`:262`, `:290`, `:318`), which is precisely the check a substitution has to change.

### 6. Handoff / доверенная замена — entirely unbuilt, and currently prohibited

**Verified, not assumed.** A repository-wide search for `handoff|hand-off|substitut|замен|reassign|transfer|delegat|backup.?driver|subfleet|sub-fleet` across all `*.kt` returns no domain concept — every hit is an unrelated English usage ("delegates to Spring's TransactionOperations", "chunked transfer", "not a mock or hand-rolled substitute"). The same search across `*.tsx` returns nothing at all. There is no `Handoff`, no substitute driver field, no second driver anywhere on `Assignment`, `Trip`, or `Proposal`.

Two structural facts make this more than "not built yet":

- **It is ratified as out of scope.** `ADR-054:126`: *"**No Team, Fleet, crew, or delegation mechanism of any kind.** ... No fallback-to-another-driver behavior, no group, no shared client list."* When ADR-068 partially superseded that sentence on 2026-09-15, the amendment was written narrowly and re-affirmed the rest: *"Nothing else in this sentence or this Part is superseded: still no Team/Fleet/crew/delegation mechanism of any kind"* (`ADR-054:128`). A handoff **is** a delegation mechanism. It cannot be built as ordinary engineering.
- **The underlying product question is formally open.** `docs/PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md:107` lists *"Can it be delegated — for example, to a Fleet…?"* as an unanswered Part 8 question, and `:148` states resolution of any Part 8 question requires a Product Owner decision. `ADR-068:166` separately rejected candidate "N-c — explicit driver-declared peers" precisely because vouching/delegation was excluded outright.

Full design proposal in Part 4.

### 7. Settlement — no such concept

Search for `payment|payout|settlement|cash|наличн|оплат|invoice` across `*.kt` returns **zero** domain matches; every "ledger" hit is an event-idempotency ledger (e.g. `order_management_processed_events`), not money.

What exists instead:
- `Proposal.statedPrice` — *"A plain, opaque, unvalidated string: PIOS does not compute, parse, compare, or assign any meaning to it"* (`Proposal.kt:93-96`). `ADR-065:58-62` documents that the UI field is `<Input type="text">` with no numeric coercion, so a driver may legitimately have typed «договоримся».
- Under ADR-065 that string travels verbatim on `AssignmentCompleted` to Driver Management, which sums it **only if it is entirely digits** and counts the rest as `unpricedRidesCount` (`ADR-065:89-94`; `DriverMilestones.kt:40-41`). ADR-065 itself flags the parsing rule as *"a proposal, not a ratification"* requiring product-owner confirmation (`:94`), and states there is **no historical backfill** (`:104-111`).
- `billing` holds a `Subscription` state machine (`Subscription.kt:53-60`) with `FREE` represented as the absence of a record. It consumes no event, publishes no event, gates nothing, has no tariff and no provider, and is deliberately not deployed (`PIOS_AI_HANDOFF.md:57`, `:94`).

**Assessment:** Missing. There is no record that money changed hands, no per-ride value the passenger also saw and agreed to in a durable form outside Dispatch's proposal row, and no driver-facing earnings statement that is anything more than a best-effort sum of free text from ship-date forward. Note also the asymmetry: a passenger-side record of what they paid does not exist at all — `ADR-065:71` explicitly keeps the amount out of `order-management`, `passenger-experience`, and every API except the driver's own milestones endpoint.

---

## Part 3 — Prioritized recommendation

Ranked by (a) commercial value per the existing blueprint/audit/tracker, (b) how local and reversible the change is, (c) ADR requirement per `.claude/CLAUDE.md` Stage 1.

| # | Work item | Commercial value (basis) | Reversibility / blast radius | ADR needed? |
|---|---|---|---|---|
| **1** | **Recovery after proposal decline/lapse** | Highest. `PIOS_TAXI_COMMERCIAL_EXECUTION.md:63` names it first among "Still blocking commercial V1". A declined offer today silently strands the order until the coordinator notices — worse than the no-proposal case ADR-077 already fixed. | Local: one module (`dispatch`), reuses the existing `dispatch_requests` row and scheduler. Reversible by config. | **Yes.** `ADR-068:177` states in terms that *"Extending recovery to decline/lapse would reverse this Part and requires its own ADR."* Non-negotiable. |
| **2** | **Relationship depth server-side** — move per-client ride count / last ride / repeat flag from the browser into a driver-owned read model, and give `Connection` a real (minimal) lifecycle state | High. "Мой бизнес" is the product's core promise to the driver, and today its CRM depth vanishes if the driver's proposal list is paginated or trimmed. Directly serves the Relationship stage. | Medium: additive read model in `driver-management` fed by events it already consumes; no new cross-module call. The *lifecycle state* half is less local. | **Yes for the lifecycle half** — `ADR-054:132` ratified "no lifecycle beyond creation, designation and removal". **No for the read-model half** if it is strictly a derived projection over events already consumed (precedent: ADR-065's own "derived read model, not a second source of truth", `:87`). |
| **3** | **Handoff / доверенная замена** (Part 4) | Product-owner-named focus area. Commercially it is the difference between "my driver" and "my driver's business" — the first feature that makes a driver's client relationship survive their own unavailability. | Large: new aggregate, new events, new consent UI on two sides, changes the authorization check on three live endpoints. | **Yes**, and it must *partially supersede* `ADR-054:126`. Also requires Product Owner sign-off before any ADR is written (Part 5). |
| **4** | **Settlement evidence v1** — a durable, two-sided record that a ride had a value and that it was settled | High but gated. `PIOS_TAXI_COMMERCIAL_EXECUTION.md:63` lists "settlement/payment evidence" as blocking commercial V1. | Medium-large, and it crosses the `ADR-042`/`ADR-065` prohibition surface. | **Yes.** And it cannot start: the *mechanics* are undecided business rules (Part 6, OQ-5…OQ-8). |
| **5** | **Recurring / scheduled commitments** | Medium. Named in the tracker's blocking list as "genuine future/recurring commitments and conflict checks" (`:63`). Real for commuter clients, but unproven. | Large: needs a series concept, a driver calendar, and conflict detection — none of which exist. `requestedPickupAt` today is explicitly *not* a reservation. | **Yes**, a new aggregate and a new invariant ("a driver cannot hold two conflicting commitments") that has never been ratified. |
| **6** | **Fallback Tier 2 (Network) data model** | Low right now. | N/A | **No new ADR to decide *not* to build it** — `ADR-068:152-168` already fully specifies the decision as the Product Owner's, with three named candidates and none chosen. |
| **7** | **Client aggregate** | Low. | Large. | **Yes** (new bounded context). **Not recommended** — see below. |

**On item 6 (Network / Tier 2):** a data model is **not** needed now. ADR-068 shipped Tier 2 as a deliberately empty tier and the code honours that (`FallbackDispatchApplicationService.kt:50-52`). Of the three candidates, N-a is buildable today but is an unratified privacy surface, N-b now has a partial data source it did not have when ADR-068 was written (ADR-073's `invitedByDriverId`), and N-c was rejected as vouching. The correct action is to put **Q2 back in front of the Product Owner**, noting that N-b became cheaper since ADR-073 — not to pick one.

**On item 7 (Client aggregate):** recommend explicitly **not** building it. It would be a new bounded context whose only content today would be a copy of identifiers three modules already hold — exactly the "copy of another module's own data" the reference-not-ownership rule forbids (`ADR-005`/`ADR-019`; `Identity.kt:20-31`). Everything a `Client` aggregate would buy is bought more cheaply by item 2.

**On Trust:** no work is recommended. `DRIVER_IDENTITY_DESIGN_DECISION.md:33-37` bans any numeric rating, ride count, "X rides together" tally, or connection count from the passenger-facing surface, and a passenger-facing trust signal was already proposed and **declined by the Product Owner on this exact basis** (`PIOS_AI_HANDOFF.md:65`). Do not re-propose it. If trust needs strengthening, the honest route is the non-numeric facts §3 already permits — name, real-time availability, and the passenger's own primary designation — and nothing else.

---

## Part 4 — Handoff / доверенная замена: concrete minimal design

This is a **proposal for Product Owner decision**, not a decision. It is written to the level of detail a developer could implement from, so that the trade-offs are visible before anyone commits.

### 4.1 The prohibition that has to move first

Nothing below can be built until `ADR-054:126` is narrowly superseded. That sentence bans delegation mechanisms outright, and its own 2026-09-15 amendment (`:128`) deliberately re-affirmed the ban while superseding only fallback candidate filtering. The precedent for how to do this correctly already exists in that same amendment: a *narrow*, in-place, dated supersession pointer per `ADR-015`, not a rewrite.

### 4.2 The three principles the design must not break

1. **"The client relationship stays with whoever created it."** Therefore a handoff must **never** create a `Connection` between the passenger and the substitute driver, and must never move the originating driver's `Connection`. The substitute drives one ride; they do not inherit a client.
2. **"The relationship should not be silently reassigned"** (`PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md:95`, a stated `[PRINCIPLE]`). Therefore passenger consent is *constitutive*, not a notification.
3. **"PIOS does not substitute a random driver for the one a client came to"** (`Proposal.kt:241`). Therefore the substitute must be **named by the originating driver**, never selected by PIOS. A handoff is not a re-dispatch.

### 4.3 Which aggregate owns it

**Recommendation: a new Dispatch-owned aggregate, `Handoff`, with its own table `handoffs` in `pios_dispatch`.** Reasons, each with the evidence that forces it:

- **Not a field on `Assignment`.** Assignment's mutable state is now a deliberately frozen remnant (`DispatchAssignmentApplicationService.kt:93-94`); adding live, multi-party state to it runs against ADR-063's stated direction of travel.
- **Not a field on `Trip`.** A handoff is a change to *who is committed*, resolved before or at the start of execution. Trip is the execution record.
- **Not a second `Proposal`.** Two blockers. Semantically, `Proposal` is pre-commitment by ADR-035's own definition (`Proposal.kt:13-15`) and a handoff is post-commitment. Mechanically, accepting a second proposal on the same order would hit `Assignment.create`'s guard — `check(existingAssignments.none { it.order == order })` (`Assignment.kt:182`) — and throw *"Order … already has an active assignment"*. Note that `Proposal.propose`'s own guard would **not** catch it, because it counts only `OPEN` proposals (`:342`); the failure would surface late, at Assignment creation, which is exactly the kind of silent-until-runtime trap this codebase already warns about elsewhere.
- **Dispatch, not `passenger-experience`.** The fact being changed is "who is committed to this order", which Dispatch owns. Passenger Experience owns the *relationship*, which by principle 1 above is precisely the thing a handoff must leave untouched.

### 4.4 Shape

```
Handoff
  id                 HandoffId
  assignmentId       AssignmentId        -- the commitment being handed off
  order              OrderReference      -- denormalized from Assignment, same-module derivation
  fromDriver         DriverReference     -- must equal Assignment.driver at creation
  toDriver           DriverReference     -- named by fromDriver; a plain string, never verified against driver-management
  passenger          PassengerReference  -- from the Assignment's own ACCEPTED Proposal
  status             HandoffStatus
  createdAt / resolvedAt
  isTest             derived from Assignment.isTest, never independently supplied
                     (the exact convention Assignment.kt:34-43 and Trip.kt:56-59 already state)
```

`HandoffStatus`: `PROPOSED → SUBSTITUTE_ACCEPTED → CONSENTED` (terminal-positive), with terminal-negative `SUBSTITUTE_DECLINED`, `PASSENGER_REFUSED`, `WITHDRAWN`, `LAPSED`.

Two consents, in this order, both required:

1. `PROPOSED` — the originating driver names a substitute. Nothing about the ride changes.
2. `SUBSTITUTE_ACCEPTED` — the named driver accepts. **Still nothing changes for the passenger**; asking the passenger to consent to a driver who has not themselves agreed would produce a consent to something that may evaporate.
3. `CONSENTED` — the passenger accepts. *This* is the moment the executing driver changes.

The invariant worth stating on the aggregate: **at most one non-terminal `Handoff` per `assignmentId`**, enforced in the aggregate factory the way `Proposal.propose` enforces its own (`Proposal.kt:342`) *and* by a partial unique index, the way `trips.assignment_id`'s `UNIQUE` backs `Trip.create`'s in-memory check (`Trip.kt:49-54`) — in-memory checks alone lose the check-then-create race, and that lesson is already written down in this codebase.

### 4.5 What changes at `CONSENTED`, and what deliberately does not

**Recommended: the `Assignment` and `Trip` are retained; `Trip` gains an `executingDriver`.**

- `Trip.executingDriver` defaults to `assignment.driver` and is only ever changed by a `CONSENTED` handoff. `Assignment.driver` — the record of *who committed* — is never rewritten. This keeps "who took the job" and "who drove" as two separate, both-true facts, which is what a handoff actually is.
- The alternative (cancel the Assignment/Trip, create new ones for the substitute) is strictly larger and currently impossible without new terminal states: `AssignmentStatus` has no cancelled value (`AssignmentStatus.kt:18-24`, and its own KDoc `:6-8` says assignment cancellation "is not represented here"), and neither does `TripStatus` (`TripStatus.kt:25-30`). Adding them is a bigger, separate decision.
- **The three ride-progress endpoints must change their authorization check.** `AssignmentController.arrive/start/complete` currently compare `verified.drv != assignment.driver.driverId` (`:262`, `:290`, `:318`). Post-handoff they must compare against the trip's executing driver. This is the single most important concrete constraint in this design: miss it and a consented handoff produces a substitute who cannot mark arrival.
- **No `Connection` is created, moved, or touched.** Principle 1.
- **No `PrimaryConnection` change.** Principle 1.

### 4.6 Events

Minimum set, following the module's existing outbox conventions (`ADR-029`/`ADR-031`/`ADR-032`) and its own restraint about not inventing events (`Trip.kt:154-162`):

| Event | Emitted at | Consumers |
|---|---|---|
| `HandoffProposed` | `PROPOSED` | None server-side initially — the substitute driver's app learns of it by poll, exactly the ADR-071 pattern, so `PRODUCT_DECISION_NOTIFICATIONS.md` §6's prohibition on event-consuming notifications is not triggered. |
| `HandoffConsented` | `CONSENTED` | This is the one that must exist as a real domain event, because it changes who is executing. |
| `HandoffResolvedNegatively` (or three narrow events) | each terminal-negative state | None initially. |

**The consumer question that must not be guessed:** Driver Management increments `driver_milestones.completed_rides_count` from `AssignmentCompleted`'s `driverId`. If the executing driver changes, whose count increments, and whose `totalStatedEarnings` grows? That is a **business rule, not an architecture choice** — see OQ-3. Do not implement `HandoffConsented` until it is answered, because the answer determines whether `AssignmentCompleted` must carry an additional `executingDriverId` field (additive, `eventVersion` stays 1, exactly the ADR-065 precedent at `:76`).

### 4.7 What passenger consent looks like

- **A blocking step, not a toast.** The ride does not change hands until the passenger acts.
- **Shown facts about the substitute are limited to what `DRIVER_IDENTITY_DESIGN_DECISION.md:25-30` permits**: name, and availability if genuinely known. **No ride count, no rating, no "N rides with your driver", no connection count** (`:33-37`). Crucially, "my driver vouches for them" is *itself* a trust claim — and vouching is precisely what `ADR-068:166` rejected. Whether PIOS may render even the sentence «Артур передаёт заказ Регине» as an implicit endorsement is a product question (OQ-2), not one to settle in code.
- **Refusal must be safe and must not silently strand the passenger.** If the passenger refuses, the honest default is that the original commitment stands unchanged — the original driver is still committed. Whether the passenger may instead cancel without penalty at that point touches cancellation policy, which is undecided (OQ-4).
- **Timeout.** A handoff offer that no one answers must lapse, reusing `ProposalLapseApplicationService`'s existing sweep shape rather than a new mechanism. The *duration* is a configuration parameter, not a ratified business rule — the same status `ADR-051`/`ADR-052` already gave the proposal lapse timeout (`ADR-068:185`).

### 4.8 What this design explicitly does not authorize

No PIOS-selected substitute; no fleet, crew, team, or shared client list; no transfer of any `Connection` or primary designation; no handoff of an order that has no `Assignment` yet (that is a decline, and belongs to recommendation #1); no chained handoff (substitute hands off again) in v1; no commission, fee, or split of any kind (OQ-5); no cross-module read of `driver-management` to validate the named substitute (reference-not-ownership, `ADR-005`/`ADR-019`).

---

## Part 5 — Requires Product Owner sign-off vs. safe to implement autonomously

### Requires explicit Product Owner sign-off before any implementation (fundamental business-model or ratified-decision change)

1. **Handoff / доверенная замена, in its entirety.** It is a delegation mechanism, banned by `ADR-054:126` and re-affirmed at `:128`, and it resolves `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md:107`'s open Part 8 delegation question — which `:148` reserves to the Product Owner. It also touches the core commercial principle "the client relationship stays with whoever created it": Part 4 is designed to *preserve* that principle, but the owner should confirm that reading rather than have it assumed.
2. **Any `Connection` lifecycle beyond create/designate/remove** (recommendation #2's second half) — `ADR-054:132` ratified the absence of one.
3. **Settlement mechanics of any kind** — amounts, who owes whom, commission, when a ride counts as paid. `ADR-002` puts pricing and commission outside architectural scope entirely, and `CLAUDE.md`'s "Never Invent Business Rules" applies directly.
4. **Activating `billing` to actually gate anything** — `PIOS_AI_HANDOFF.md:74` already records this as explicitly out of scope for autonomous action.
5. **Fallback Tier 2 definition** — `ADR-068:201` (Q2) reserves the choice among N-a/N-b/N-c to the Product Owner.
6. **Recurring/scheduled commitments** — introduces a driver-calendar concept and a conflict invariant that no ratified document defines.
7. **Any passenger-facing trust signal** — already proposed once and declined (`PIOS_AI_HANDOFF.md:65`). Do not re-litigate without the owner raising it.

### Safe to implement autonomously once a Sprint basis exists (ordinary engineering, no ratified decision reversed)

- The **server-side per-client ride read model** (recommendation #2, first half) — strictly a derived projection over events Driver Management already consumes, with the precedent stated in `ADR-065:87`. No new cross-module call, no new invariant, rebuildable and disposable.
- **Fixing `DriverController.createDriver`'s stale class KDoc** (`PIOS_AI_HANDOFF.md:70`, KNOWN RISKS #1) — a documented known-wrong doc comment; correcting a doc to match code is not an architecture change.
- **Converging the HTTP surface toward Trip**, or not — either way, no new decision; ADR-063 already ratified the direction and Task 12 already did the domain half.

### Requires an ADR but not necessarily a business decision

- **Recommendation #1 (decline/lapse recovery)** — the ADR is mandatory (`ADR-068:177` says so in terms), but the substance is a routing/reliability decision, not a product one, provided it reuses ADR-077's existing window and does not change the driver-selection rule.

---

## Part 6 — Open questions (not answered here, by rule)

These are business rules. `CLAUDE.md` ("Never Invent Business Rules") and `ADR-002` forbid filling them in from this seat.

- **OQ-1.** May a driver hand off a commitment at all, and if so, at which points — after Assignment but before arrival only, or also mid-ride?
- **OQ-2.** Is "my driver named this substitute" a trust claim PIOS is willing to render to the passenger? If yes, it is a vouching signal, which `ADR-068:166` rejected in a different context; if no, what exactly does the consent screen say?
- **OQ-3.** After a consented handoff, whose `completedRidesCount` and whose `totalStatedEarnings` increment — the driver who committed, the driver who drove, or both? (Determines whether `AssignmentCompleted` needs an additive `executingDriverId`.)
- **OQ-4.** If the passenger refuses a handoff, what happens to the commitment? The original driver remains committed by default in Part 4's design; is that the intended product behaviour?
- **OQ-5.** Does any fee, split, or commission attach to a handoff? The core principle says no commission on a driver's own client — but the substitute's relationship to that client is undefined, which is exactly the ambiguity to resolve.
- **OQ-6.** May a substitute hand off again (chained handoff)? Part 4 proposes "no in v1" as a scoping choice, not a product answer.
- **OQ-7.** Does a handoff cap exist per driver, per day, or per client? (Inventing one would be inventing a business rule; so would assuming none.)
- **OQ-8.** Settlement: what event constitutes "settled"? Cash acknowledged by the driver, by the passenger, by both, or neither? Is a settlement record required for the ride to count as complete?
- **OQ-9.** ADR-065's digits-only price-parsing rule was shipped while its own text says it *"requires product-owner confirmation"* (`ADR-065:94`) and that no historical backfill exists (`:104-111`). Was it ever confirmed? Any settlement work inherits this question.
- **OQ-10.** Tier 2 (Network): does ADR-073's `invitedByDriverId` change the answer to `ADR-068`'s Q2, now that candidate N-b has a partial data source it lacked in September?

---

## Part 7 — ADR numbering note

The highest ADR present is **ADR-077**. **ADR-072 is absent** from `docs/ADR/` — the sequence jumps 071 → 073. Per `MODULE_STRUCTURE.md` §8's "next free number continues the existing sequence", the next new ADR should be **ADR-078**; ADR-072's gap should be confirmed as intentional (or its file located) rather than silently reused, since reusing a number that was allocated elsewhere would break every future citation.

Proposed ADR titles, if the work above proceeds — **named, not written, and none authorized by this document**:

- **ADR-078 — Dispatch Recovery After Proposal Decline or Lapse** (recommendation #1; explicitly reverses `ADR-068` Part 4's preserved "no retry on decline/lapse", per `ADR-068:177`).
- **ADR-079 — Driver-Initiated Handoff with Passenger Consent** (recommendation #3; must carry a narrow, dated in-place supersession pointer into `ADR-054:126` per `ADR-015`, matching the ADR-068 amendment's own form).
- **ADR-080 — Connection Lifecycle** (recommendation #2's second half; supersedes `ADR-054:132`) — only if the Product Owner wants relationship state.

---

## Part 8 — Sprint-basis flag (do not skip)

This document is an architectural analysis. Under `docs/PIOS_PRODUCT_EVIDENCE.md:93-97` that is **not** an admissible basis for starting a Sprint, and `:99` requires every product change to cite at least one `E-NNN`. The journal holds exactly one real entry (`E-001`, `:84-87`), which bears on registration validation and on nothing in this document.

The second admissible basis — a hypothesis registered **before** the Sprint, with «Как проверим» and «Критерий успеха» — is available (`:115-119`, «Дополнение 2026-08-01»), and has been used seven times already (H7–H14). **I have not registered one, deliberately.** Registering a hypothesis is a Product Owner act; the last time hypotheses were registered retroactively by an AI session it was labelled as retroactive precisely because that was a gap in the project's own discipline (`PIOS_TAXI_COMMERCIAL_EXECUTION.md:73`).

Concretely: before implementation of recommendation #1, #2, or #3 begins, the Product Owner must register the corresponding hypothesis and name it as that Sprint's basis. For Handoff specifically, the hypothesis is not "drivers want this" — it is something falsifiable, e.g. whether a passenger offered a named substitute by their own driver accepts rather than cancelling. Let the Product Owner write it.

---

## References read for this document (none modified)

- `backend/identity/src/main/kotlin/com/pios/identity/domain/Identity.kt`
- `backend/order-management/src/main/kotlin/com/pios/ordermanagement/domain/Order.kt`
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/domain/Proposal.kt`, `Assignment.kt`, `AssignmentStatus.kt`, `Trip.kt`, `TripStatus.kt`
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/DispatchAssignmentApplicationService.kt`, `FallbackDispatchApplicationService.kt`, `DispatchRequestApplicationService.kt`
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/api/AssignmentController.kt`
- `backend/passenger-experience/src/main/kotlin/com/pios/passengerexperience/domain/Connection.kt`, `api/ConnectionController.kt`
- `backend/passenger-experience/src/main/resources/db/migration/passengerexperience/V1__create_connections.sql`
- `backend/network-management/src/main/kotlin/com/pios/networkmanagement/domain/Connection.kt`
- `backend/driver-management/src/main/kotlin/com/pios/drivermanagement/domain/DriverMilestones.kt`
- `backend/billing/src/main/kotlin/com/pios/billing/domain/Subscription.kt`
- `frontend/src/pages/DriverHome/DriverHome.tsx` (CRM derivation, lines 517-596)
- `docs/ADR/ADR-054`, `ADR-063`, `ADR-065`, `ADR-068`, `ADR-074`, `ADR-075`, `ADR-076`, `ADR-077`
- `docs/DRIVER_IDENTITY_DESIGN_DECISION.md`, `docs/PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md`
- `docs/PIOS_PRODUCT_EVIDENCE.md`, `docs/PIOS_PRODUCT_HYPOTHESES.md`
- `docs/PIOS_AI_HANDOFF.md`, `docs/PIOS_TAXI_COMMERCIAL_EXECUTION.md`

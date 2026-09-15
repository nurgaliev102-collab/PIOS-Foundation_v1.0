# ADR-073: Driver-to-Driver Referral — a Single-Hop Origin Fact Owned by Driver Management

## Status

**Accepted, 2026-09-15, by the Product Owner, in-session.**

**Author:** Architect role.

**Number collision, resolved.** `ADR-071` (In-App Poll-Derived Notification Surface, the parallel "engagement layer" review) landed first and claimed `071`. `072` was declined outright (the passenger-facing trust-signal piece — see that review's own report). `073` is therefore clear and stays as originally chosen.

### Preconditions — resolved at ratification

1. **Evidence basis: H11 registered.** The highest hypothesis at the time this ADR was proposed was H9; `ADR-071`'s own Sprint claimed H10 in the interim (notifications, same day). This ADR's hypothesis is registered as **H11** in `docs/PIOS_PRODUCT_HYPOTHESES.md`, immediately below, by the Product Owner, before implementation — same `ADR-054`/H7, `ADR-068`/H8, `ADR-070`/H9, `ADR-071`/H10 precedent.
2. **One basis per Sprint.** `PIOS_PRODUCT_EVIDENCE.md` line 133: *«Одно основание на Sprint. Смешивать "частично есть доказательство, частично гипотеза" нельзя»*. This ADR and `ADR-074` (subscription/billing foundation) are therefore **two Sprints on two separate bases**, not one bundled scope, even though they are designed together for consistency.
3. **Two standing ADRs explicitly decline to authorize this.** `ADR-064`'s Evolution Path item 2 (line 88): *"**Driver-to-driver referral** — no data path exists today; not decided here."* `ADR-064`'s "What This ADR Does Not Authorize" (line 96) and `ADR-070` Part 5 (line 173) each forbid *"any driver-to-driver referral mechanism."* This ADR is the decision that takes up that deferred question; it may not be treated as implicitly permitted by either.

This ADR invents no business rule (`ADR-002`; `CLAUDE.md`, "Never Invent Business Rules"). The anti-MLM constraint it enforces is **already ratified** (Context below), not introduced here.

---

## Context

### The gap, confirmed against source rather than remembered

`ADR-064` line 29, written 2026-09-06, states: *"There is no driver-to-driver referral record anywhere in the codebase, and no passenger-to-passenger one either... **The entire "who invited whom" fact PIOS captures anywhere, today, is the single-hop, driver→passenger `Connection`**."* Re-verified for this ADR, 2026-09-15, by direct reading:

| Claim | Evidence |
|---|---|
| Identity carries no referral concept | `backend/identity/src/main/kotlin/com/pios/identity/api/RegisterIdentityRequest.kt` line 3 — the whole type is `data class RegisterIdentityRequest(val phone: String, val password: String)` |
| Driver creation carries no referral concept | `backend/driver-management/src/main/kotlin/com/pios/drivermanagement/api/CreateDriverRequest.kt` line 20 — `data class CreateDriverRequest(val driverId: String, val displayName: String? = null, val isTest: Boolean = false)` |
| The `drivers` table has no such column | `db/migration/drivermanagement/V1__initial_schema.sql` (`id`, `availability`) through `V11__backfill_driver_availability_changed_outbox.sql`; the added columns are `display_name`, `created_at`, `is_test`, the vehicle block, `accepts_long_distance_trips`. **Next free migration number is `V12`.** |
| The only invite link in the product is passenger-facing | `frontend/src/identity/InvitationProvider.ts` lines 25–27 issues `${window.location.origin}/i/${driverId}`; `frontend/src/app/routes.tsx` lines 24–31 routes `/i/:driverCode` → `PassengerLanding` and `/i/:driverCode/request` → `RideRequest` |
| A driver registers with no notion of a referrer | `frontend/src/pages/DriverHome/DriverHome.tsx` lines 1134–1172 (`handleNameSubmit`): generates a client-side UUID, posts `{driverId, displayName}` to `POST /v1/drivers` (line 1157), then calls `identityProvider.attachDriver(newDriverId)` |

`docs/PIOS_TAXI_COMMERCIAL_PRODUCT_BLUEPRINT.md` Section 10 classifies "Driver-to-driver referral" as **MISSING**, and Section 13 sequences it under LATER, *"pending their own Product Decisions"* (line 237). This ADR supplies the architectural half of that decision; the product half is the Product Owner's ratification below.

### The anti-MLM constraint is already ratified — this ADR enforces it, it does not invent it

The Product Owner's instruction for this Sprint («не превращай в MLM») is not a new rule. It is already the ratified position of this product, in three places:

- `docs/PIOS_PRODUCT_DECISIONS_NETWORK_RULES.md` **Принцип 3** (lines 42–44): *«Оплата следует за трудом. Кто фактически выполнил поездку — получает оплату. **Пригласивший человека не получает оплату автоматически только за факт приглашения.**»*
- `docs/PRODUCT_DECISION_PERSONAL_NETWORK_VALUE_DISTRIBUTION.md` §2.5: recognition/priority and payment are *"cleanly separated"* — *"Исполнитель поездки получает оплату"*, regardless of who originated the connection.
- `docs/PRODUCT_DECISION_ENTREPRENEUR_MODEL.md` §2.5 names this as *"the sharpest single difference from both an aggregator **and** from a referral/MLM structure, and it is what makes 'entrepreneur' the accurate word rather than 'affiliate': the model pays for performed work, and recognition of a connection confers advantage at the moment of first offer only, **never an annuity and never ownership**."*

What **is** ratified as permitted: `PIOS_PRODUCT_DECISIONS_NETWORK_RULES.md` **Принцип 1** (origin of a connection is preserved permanently — *«PIOS навсегда сохраняет факт происхождения связи»*) and **Принцип 2** (*«Каждый участник может развивать собственную сеть»*, `Артур → Регина → Мама`). So a network that *grows* is ratified; a payout that *flows upward* is ratified against. This ADR records exactly the first and structurally withholds the second.

### Why an ADR, and why the `Order.destination` additive precedent does not cover this

`Driver.kt`'s own KDoc (lines 18–21) names that precedent for `displayName`: *"the same 'plain nullable field, no new Value Object' precedent `Order.destination` already established (Sprint 3B)."* Three existing fields (`displayName`, `createdAt`, `isTest`) were added under it without an ADR.

It does not stretch to cover this one, for the same reason `ADR-070` Alternatives gave when it rejected the identical shortcut: that precedent covers *"an optional attribute with no competing claimant, no new invariant, and no new cross-module relationship."* `invitedByDriverId` is a **new class of relationship** (Driver↔Driver) that exists nowhere in the data model, that two standing ADRs explicitly deferred, and that carries an invariant (single-hop, never monetized) which must be recorded somewhere enforceable. Per `.claude/CLAUDE.md` ("Decision Authority"), "domain model changes" and "a new ADR may be required" are explicit Stage-1 triggers.

---

## Problem

1. How does Driver A's invitation reach Driver B, given that the only invite link in the product is passenger-facing and resolves to a passenger screen?
2. What fact is recorded when B registers through A's link, such that a multi-level payout structure is not merely discouraged but structurally absent?
3. What, if anything, does A see about it?
4. Which module owns the fact?

---

## Decision

### Part 1 — Placement: `driver-management`, on the `Driver` aggregate

The recorded fact is *"this Driver was registered through that Driver's link"* — a property of the Driver entity at the moment of its own creation. `driver-management` already owns Driver creation (`CreateDriverApplicationService.handle`, lines 49–56) and already stores three set-once-at-creation optional facts on the same aggregate. This is the module.

The alternatives were checked against their own owning documents, not dismissed by preference:

- **`passenger-experience.connections`** — `UNIQUE (driver_id, passenger_reference)`, and `ADR-054` makes that table mean *"this passenger is this driver's client."* Placing a driver in the passenger column would corrupt the single relationship the whole product is built on, and `ADR-064` Part 1 already exercised that table's ownership for a *different* fact. **Rejected.**
- **`network-management`** — forbidden outright: `ADR-064` Part 2 supersedes it for all new work and prohibits *"any read from, write to, or new dependency on `network-management`."* Note that `PRODUCT_DECISION_PERSONAL_NETWORK_VALUE_DISTRIBUTION.md` §2.6 (2026-07-29) once pointed *toward* that module for exactly this kind of general graph; `ADR-064` (2026-09-06) closed that door afterward and is the later authority. **Rejected.**
- **`identity`** — `ADR-038` keeps Identity as the authentication anchor, deliberately independent of the Driver/Passenger role. A referral is a fact about the *driver role*, not about authentication. **Rejected.**
- **`core`** — `ADR-067`'s Boundary section (line 84): Core *"is the authority for **none**"* of the Taxi concepts, and *"any future slice that would make Core the authority for a concept currently owned by a Taxi module requires its own superseding ADR."* Core may later *project* this fact from events; it may not own it. **Rejected as owner.**

### Part 2 — The fact: exactly one edge, nullable, immutable at creation

`Driver` gains `invitedByDriverId: String?`, appended last so every existing positional call site keeps compiling — the identical treatment `createdAt` received (`Driver.kt` KDoc lines 22–28). `CreateDriverRequest` gains the matching optional field, defaulting to `null`, so **every existing caller of `POST /v1/drivers` is unaffected**.

Storage: `V12__add_driver_invited_by.sql`, one nullable `TEXT` column, **no foreign key**. A self-referencing FK inside the module's own database would be permissible under `ADR-005`/`ADR-009` (those forbid *cross-database* FKs), and is nonetheless declined: every other identifier in this codebase is stored as a plain string, and an FK would couple a driver's registration write path to row-ordering and cleanup concerns for a fact that is inert by design.

Validation, in the application service:

- An `invitedByDriverId` that identifies no existing Driver records **`null`** — it does not fail the registration. A driver's own registration must never fail because someone else's link was stale or mistyped.
- Self-reference (`invitedByDriverId == driverId`) records **`null`**.
- The field is set once, at creation, and never changes — the same immutability every other optional field on this aggregate already has.

### Part 3 — Reach: a driver-facing route, distinct from the passenger link

A new route `/d/:inviterDriverCode` renders the **existing** `DriverHome` component, with the inviter code read from the route and threaded into `handleNameSubmit`'s existing `POST /v1/drivers` body. Reuse rather than a second parallel component, following `ADR-070` Part 1's own precedent for `/request` (`routes.tsx` lines 32–41).

**It must be a distinct path from `/i/:driverCode`.** That URL already means *"a passenger is being introduced to this driver by name"* — `PassengerLanding` resolves it, and `invitationSource.ts` refuses to resolve it at all for a driver with no `displayName` (quoted in `PRODUCT_DECISION_ENTREPRENEUR_MODEL.md` §2.2). One URL cannot mean two different things depending on who opens it without making the passenger flow's own behaviour conditional on a guess about the visitor's role.

**Not changed:** `/i/:driverCode`, `/i/:driverCode/request`, `/request`, `PassengerLanding`, `RideRequest`, `InvitationProvider.linkFor`, and the behaviour of `POST /v1/drivers` for any caller that omits the new field.

### Part 4 — What A sees: a plain count, private to A

An additive field on the **existing** `GET /v1/drivers/{id}/milestones`. That endpoint is already `Bearer`-gated to the driver named in the path (401/403, `DriverController.getMilestones`, lines 223–249), and its own KDoc (lines 92–99) already classifies what it returns as *"private business data about that driver, not a public fact the way `displayName` ... is."* No new endpoint.

- **Derived, not denormalized.** Computed by a direct count over `drivers` (`WHERE invited_by_driver_id = :id`), not persisted as a counter in `driver_milestones`. That table is maintained by event listeners and would become a second source of truth that can drift; a count over a column that never changes after creation cannot.
- **A plain count and nothing else.** No score, no rank, no percentile, no trend, no earnings implication. This follows the product-owner ruling already recorded in `PIOS_TAXI_COMMERCIAL_PRODUCT_BLUEPRINT.md` Section 3 (quoting `DriverTrustIndicator.tsx`: *"never a rating, count, rank, or any fabricated trust score"*) — honest facts, no fabricated signal.
- **Never passenger-facing.** It must not appear on `DriverResponse`, `GET /v1/drivers`, or `GET /v1/drivers/{driverId}` — all three are deliberately unauthenticated. The moment a passenger can see it, it becomes a de facto reputation signal, which is an **open** Product Decision (`PIOS_TAXI_COMMERCIAL_PRODUCT_BLUEPRINT.md` Section 19, Decision 2), not one this ADR may settle.

### Part 5 — The anti-MLM invariant, and the honest limit of what storage can guarantee

**Binding, and the reason this ADR exists:**

1. **Exactly one edge is stored.** No `depth`, no `level`, no `path`, no closure table, no ancestor/descendant column.
2. **No recursive read, ever.** No recursive CTE, no repeated-lookup loop in application code, no endpoint, no screen, and no report may traverse more than one hop from any driver.
3. **Nothing of value may attach to this fact** — no payout, fee, commission, credit, balance, discount, subscription benefit, or tier grant — without a new Product Decision that explicitly amends `PIOS_PRODUCT_DECISIONS_NETWORK_RULES.md` Принцип 3. An ADR may not make that amendment; only a Product Decision may.
4. **It is never an input to Dispatch.** Not to First Refusal, not to any Fallback tier, not to any ordering or tie-break. `ADR-034` Part 2's forbidden-input boundary is unchanged, and `PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` §8 (line 61) forbids any commercial fact purchasing priority *"independent of which commercial model is eventually ratified."*

**The limit, stated rather than overclaimed.** Any parent pointer is transitively walkable in principle: given `B.invitedBy = A` and `C.invitedBy = B`, a chain is reconstructible by anyone with database access. The guarantee here is **not** "the schema cannot express a chain" — it is "no query, endpoint, screen, or payout in this product ever traverses more than one hop, and nothing of value is attached to the fact at any depth." Recording this distinction is deliberate: a future reader must not believe the storage enforces something it does not, and must not conclude that because the data *could* support a chain, building one is a mere implementation choice. It is not — it is a Product Decision amendment.

### Part 6 — The security fact, recorded rather than glossed

`POST /v1/drivers` is deliberately unauthenticated. `DriverController`'s own KDoc (lines 60–74) explains why and records that Task 24's audit classified it *"LOW/informational, not a remediation target: it is a pre-authentication 'first contact' action creating a brand-new resource named by a caller-generated, unguessable UUID."*

Consequence, undisguised: **the referral claim is self-asserted by the registering client and is unverifiable today.** Anyone can post any `invitedByDriverId`. PIOS still has no authorization layer (`ADR-054` Part 6, carried forward unresolved through `ADR-068`, `ADR-069`, `ADR-070`), and this ADR does not change that and must not be described as doing so.

The blast radius is one wrong number on one private dashboard — **precisely because** Part 5 attaches no money and no priority to the fact. This is load-bearing, not incidental: the anti-MLM constraint is also what makes the unverified claim tolerable. Attaching anything of value to this fact later reopens the *security* question, not only the product one, and any such proposal must cite this paragraph.

---

## Alternatives Considered

- **Treat it as additive under the `Order.destination` precedent, no ADR.** Rejected — see Context; it establishes a new relationship class and a new invariant, and two standing ADRs explicitly deferred it.
- **Reuse `/i/:driverCode` and branch on whether the visitor is a driver.** Rejected — makes one URL's meaning conditional on a guess about the visitor's role, and risks a registering driver being drawn into the passenger `Connection` flow.
- **Record the referral as a `Connection` row in `passenger-experience`.** Rejected — Part 1.
- **A dedicated `driver_referrals` table in `driver-management`** (`inviter_driver_id`, `invited_driver_id`, `created_at`). Rejected as disproportionate *for this shape*: the fact is 1:1 with a Driver's own creation and immutable, so a column on the aggregate that already owns creation is the smaller, more truthful model. **Reopen this if, and only if, the fact ever needs its own lifecycle** (revocation, multiple inviters, timestamps distinct from `created_at`) — at which point it is a different decision requiring its own ADR.
- **Record the inviter but expose nothing at all.** Rejected — the Product Owner asked for A to see the outcome of their own effort, and an invisible fact produces no observable behaviour, which would fail the evidence gate's own requirement (line 132) that a hypothesis-based Sprint leave behind *"поведение, пригодное к наблюдению"*.
- **Any multi-level structure, chain visualization, or upward payout.** Rejected — ratified against; see Context.

---

## Consequences

**Positive**

- One nullable column, one optional request field, one route, one additive response field. No new table, no new module, no new event, no new cross-module contract; `docs/INTERFACE_CONTRACTS.md` is unchanged.
- The anti-MLM position becomes structural (nothing to traverse, nothing attached) rather than a comment someone can later argue around.
- Closes `ADR-064` Evolution Path item 2 for the driver→driver case specifically, leaving the passenger→passenger case (item 1) untouched and still open.
- Every constraint listed in the Sprint brief is preserved: `ADR-068`/`ADR-069`/`ADR-070`, Circle of Trust, Primary Driver, Assignment, Trip lifecycle, test/real isolation, and the current behaviour of driver registration for existing callers.

**Negative**

- The claim is unverifiable (Part 6). Accepted knowingly, on the stated terms.
- `driver-management` gains its first Driver↔Driver relationship. It is intra-module (both ends are this module's own entity), so no reference-not-ownership question arises — but the module's domain language now contains a second kind of edge, and future work must not assume "Driver has no relationships."
- A count that mixes real and test drivers could inflate a real driver's dashboard. **Recommendation to the developer, not a business rule:** exclude `is_test = true` rows from the count when the inviter is a real driver, consistent with `ADR-069`'s spirit (dispatch already segregates), and note that `driver_milestones` does *not* segregate today. If this is judged a behaviour change rather than hygiene, it needs the Product Owner, not the developer.

---

## What This ADR Does Not Authorize

Any implementation before ratification and before H10 is registered; any multi-hop, recursive, or chain-shaped read of the referral fact; any payout, fee, commission, credit, discount, subscription benefit, or tier grant attached to a referral at any depth; any referral input to Dispatch, First Refusal, any Fallback tier, or any ordering or tie-break; any passenger-facing exposure of the referral fact or its count; any change to `DriverResponse`, `GET /v1/drivers`, or `GET /v1/drivers/{driverId}`; any change to `POST /v1/identities/register` or the Identity module; any read from, write to, or dependency on `network-management` (`ADR-064` Part 2); any change to `connections`, `primary_connections`, Circle of Trust, or Primary Driver; any change to `Proposal`, `Assignment`, `Trip`, or any Dispatch selection query; any change to `/i/:driverCode`, `/i/:driverCode/request`, `/request`, `PassengerLanding`, or `RideRequest`; any authorization layer claim (`ADR-054` Part 6 stands unchanged); any reputation, rating, or trust signal; any pricing, commission, or subscription mechanism (`ADR-002`; see `ADR-074`).

## Blocking Questions for the Product Owner

- **Q1 — is driver-to-driver referral ratified product direction at all?** `ADR-064` and `ADR-070` both deferred it explicitly. Confirmation required, not inferred from the Sprint brief alone.
- **Q2 (Evidence Analyst / Product Owner) — H10 must be registered in `docs/PIOS_PRODUCT_HYPOTHESES.md` before the Sprint**, with «Как проверим» and «Критерий успеха», per `PIOS_PRODUCT_EVIDENCE.md` lines 119–133. The Sprint document must name `H10` literally.
- **Q3 — is the plain count the right visible outcome**, or should A see nothing at all until there is evidence a count changes behaviour? Part 4 recommends the count; the alternative is recorded above.
- **Q4 — does the count exclude test drivers?** Consequences, above. A hygiene recommendation is given; the ruling is not the developer's to make silently.

**Non-blocking, for follow-up:** `docs/README.md`'s ADR table and any `docs/MODULE_STRUCTURE.md` update are **not performed by this ADR**, following `ADR-070` Q7's own precedent.

## Related ADRs

- [ADR-064: Referral Visibility — Network Management Formally Superseded](ADR-064-Referral-Visibility-No-Network-Management-Activation.md) — Evolution Path item 2, which this ADR takes up; its Part 2 prohibition applied unchanged.
- [ADR-070: Channel 1 Discovery Matching](ADR-070-Channel-1-Discovery-Matching-and-Post-Ride-Relationship-Formation.md) — Part 5's own "no driver-to-driver referral mechanism"; its `/request` route-reuse precedent applied in Part 3.
- [ADR-054: Circle of Trust](ADR-054-Circle-of-Trust-Passenger-Experience-Owned-Primary-Driver-Relationship.md) — sole owner of `Connection`; untouched, and the reason Part 1 rejects that table.
- [ADR-067: PIOS Core Bounded Context — Slice 01](ADR-067-PIOS-Core-Bounded-Context-Slice-01.md) — Core is authority for nothing; may project this fact later, may not own it.
- [ADR-038](ADR-038-Identity-Module-Bounded-Context-Foundation.md) / [ADR-039](ADR-039-Identity-Driver-Association.md) — Identity as authentication anchor, independent of role.
- [ADR-005](ADR-005-Data-Ownership.md) / [ADR-009](ADR-009-Domain-Isolation.md) / [ADR-019](ADR-019-Conceptual-Data-Ownership.md) — ownership rules; this edge is intra-module and raises no cross-module reference question.
- [ADR-034](ADR-034-Assignment-Policy-and-Dispatch-Decision-Architecture.md) — forbidden assignment inputs, unchanged.
- [ADR-069](ADR-069-Test-Real-Segregation-In-Fallback-Driver-Selection.md) — the `isTest` sourcing pattern referenced in Consequences.
- [ADR-002](ADR-002-Dispatch-Engine.md) — pricing/commission/matching rules out of architectural scope.
- [ADR-074: Subscription / Billing Foundation](ADR-074-Subscription-Billing-Bounded-Context-Foundation.md) — designed alongside this ADR; Part 5 clause 3 is the seam that keeps a future tier-gating question answerable without pre-empting it.

## References

Read for this ADR on 2026-09-15, **none modified**:

- `backend/driver-management/src/main/kotlin/com/pios/drivermanagement/domain/Driver.kt` (KDoc 18–58; constructor 60–72)
- `backend/driver-management/src/main/kotlin/com/pios/drivermanagement/api/CreateDriverRequest.kt` (line 20)
- `backend/driver-management/src/main/kotlin/com/pios/drivermanagement/application/CreateDriverApplicationService.kt` (lines 44–57)
- `backend/driver-management/src/main/kotlin/com/pios/drivermanagement/api/DriverController.kt` (KDoc 60–99; `createDriver` 133–144; `getMilestones` 223–249)
- `backend/driver-management/src/main/resources/db/migration/drivermanagement/` (V1–V11 — next free is V12)
- `backend/identity/src/main/kotlin/com/pios/identity/api/RegisterIdentityRequest.kt` (line 3)
- `frontend/src/app/routes.tsx` (lines 19–50), `frontend/src/identity/InvitationProvider.ts` (lines 25–27)
- `frontend/src/pages/DriverHome/DriverHome.tsx` (`handleNameSubmit` 1134–1172; QR card 2017–2024)
- `docs/PIOS_PRODUCT_DECISIONS_NETWORK_RULES.md` (Принципы 1–3, Правило 5, Section 6)
- `docs/PRODUCT_DECISION_PERSONAL_NETWORK_VALUE_DISTRIBUTION.md` (§2.1–2.6), `docs/PRODUCT_DECISION_ENTREPRENEUR_MODEL.md` (§2.5, §2.6, §6, §7)
- `docs/PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` (§8, line 61)
- `docs/PIOS_PRODUCT_EVIDENCE.md` (E-001, lines 84–87; gate lines 95, 119–133), `docs/PIOS_PRODUCT_HYPOTHESES.md` (H9 highest, line 191)
- `docs/PIOS_TAXI_COMMERCIAL_PRODUCT_BLUEPRINT.md` (Sections 3, 7, 10, 13, 19), `docs/PIOS_TAXI_MONETIZATION_VALUE_MAP.md`
- `docs/ADR/ADR-064`, `ADR-067`, `ADR-070`, `ADR-017`, `ADR-018`; `docs/MODULE_STRUCTURE.md` §8

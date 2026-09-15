# ADR-070: Channel 1 — Discovery Matching for a Passenger With No Driver Relationship, and How a Completed Ride Becomes a Relationship

## Status

**Accepted, 2026-09-15, by the Product Owner, in-session.** Part 1 authorized as designed. Part 2: **Option B only** — Option A remains blocked exactly as written (not ratified, requires its own future Product Decision amendment first).

**Proposed Date:** 2026-09-15. **Ratified Date:** 2026-09-15. **Author:** Architect role. Ratification below resolves the blocking questions:

- **Q1 — stranger-matching as a front door: confirmed as ratified product direction.** The Product Owner's own task brief for this Sprint ("PIOS TAXI — CUSTOMER ACQUISITION & MATCHING," 2026-09-15) is itself the ratification input: it specifies, in the Product Owner's own words, exactly the journey Part 1 makes reachable ("НЕЗНАКОМЫЙ ПАССАЖИР → создаёт запрос → PIOS находит подходящего независимого водителя"). This is treated as explicit confirmation, not inferred from silence. The staleness-cost change Part 6 (Negative) names is accepted knowingly, not inherited silently.
- **Q2 — Option A or B: Option B.** For exactly the reasons Part 2 already gives it as the recommendation — it needs no amendment to `PRODUCT_DECISION_CIRCLE_OF_TRUST.md`, no new cross-module contract, no new consumer infrastructure in a module that has none, and it keeps the Circle of Trust's "passenger's own explicit act only" rule genuinely intact rather than routed around. This is also the more conservative reading of this Sprint's own instruction to preserve existing philosophy and avoid a large refactor. **Option A stays exactly as documented — fully designed, not authorized, blocked on its own precondition** — for a future Sprint if the Product Owner later chooses to amend that Product Decision explicitly.
- **Q3 — moot**, Option A not chosen.
- **Q4 — primary designation on a discovery-matched save: reuses `handleSaveDriver`'s existing rule verbatim** (primary only if the passenger has none yet) — this is not a new decision, it is declining to special-case discovery-formed connections differently from link-formed ones, which is itself the smaller, more consistent change.
- **Q5 — H9 registered** in `PIOS_PRODUCT_HYPOTHESES.md`, before this Sprint, by the Product Owner (via this ADR's ratification), following the exact `ADR-054`/H7 and `ADR-068`/H8 precedent.
- **Q6 — yes, required, and is this Sprint's own deliverable**: `docs/PIOS_TAXI_CUSTOMER_ACQUISITION_DESIGN.md` is produced alongside this ratification, informed by this ADR's Part 1/2/4.
- **Q7–Q9** — assigned to the developer/architect follow-up exactly as Part 7 already scoped them (non-blocking).

**This ADR contains one decision the Architect role may not make alone.** Its Part 2 asks whether completing a ride may create a Circle-of-Trust `Connection` without the passenger's own explicit act. That is not an open question — it is **already ratified in the negative** by `PRODUCT_DECISION_CIRCLE_OF_TRUST.md` (Product Owner authority, 2026-08-08), three times over:

- Decision 3 (line 13): the relationship is created *"**By the passenger's own explicit action only**… It is **never inferred from completing a ride with a driver**, and never created or changed by the driver's own action."*
- Business rule 2 (line 27): *"Completing a ride with a non-primary trusted driver never changes who is primary, and **never on its own creates a new circle-of-trust membership** beyond what the passenger already explicitly holds."*
- Business rule 3 (line 28): *"A driver cannot make themselves anyone's primary, and **cannot add themselves to a passenger's circle of trust**."*

`ADR-054`'s own "What This ADR Does Not Authorize" (line 183) carries the same prohibition into the architecture: *"any derivation of circle membership or primacy from ride history, completion, or frequency; any driver-initiated write to a passenger's circle."* Its Alternatives section (line 148) calls this *"the rejection that matters most."*

**Therefore Part 2 presents two options and recommends one.** Option B (passenger-confirmed) is compliant with every ratified document as they stand today and is the Architect role's recommendation. Option A (automatic, Dispatch-published) is fully designed here so the Product Owner can evaluate it on its merits — but it **cannot be implemented until `PRODUCT_DECISION_CIRCLE_OF_TRUST.md` is itself amended or superseded by a new Product Decision**, and that amendment is a Product Owner act, not an architectural one. Implementing Option A without it would be exactly the silent re-decision of a standing Product Decision that `CLAUDE.md` and `MODULE_STRUCTURE.md` §8 forbid.

**Three further preconditions are unmet as of this date:**

1. **No evidence supports this Sprint.** `docs/PIOS_PRODUCT_EVIDENCE.md`'s journal still contains exactly one real entry (`E-001`, registration phone-format validation), and nothing in it bears on discovery matching or relationship formation. Under that log's own gate rule, the honest basis here is the *second* admissible Sprint basis (a hypothesis registered **before** the Sprint) — the same path `ADR-054`/H7 and `ADR-068`/H8 used. **H9 does not exist yet**; the highest registered hypothesis is H8 (`PIOS_PRODUCT_HYPOTHESES.md` line 180).
2. **`docs/PIOS_TAXI_CUSTOMER_ACQUISITION_DESIGN.md` does not exist** (verified: no such file). This ADR is the architectural half of a product design that has not been written.
3. **Part 2's Option A additionally requires the `PRODUCT_DECISION_CIRCLE_OF_TRUST.md` amendment described above.**

This ADR invents no business rule (`ADR-002`; `CLAUDE.md`, "Never Invent Business Rules"). Where the product intent is unresolved, it is recorded as a blocking question in Part 7 rather than filled with an assumption.

---

## Context

### The gap this addresses

`docs/PIOS_TAXI_PRODUCT_COMPLETION_AUDIT.md` (2026-09-15) Section 7 names this as its single largest finding: *"a driver's client base can only ever grow by that driver's own personal, offline effort… PIOS today is a **retention and resilience tool for relationships a driver already has**, not an **acquisition tool for relationships they don't**."* Its own remediation block states the required change is *"a net-new architectural capability… which is itself a major, ADR-scale decision."* Section 13 lists it as critical blocker #1; Section 16 sequences it as P1, item 3, explicitly as "Channel 1 architecture decision (Stage 1 Architect review, likely a new ADR)". This document is that review.

### What the backend discovery chain actually does today (read from source, not remembered)

The chain the task brief describes **exists and is correctly wired**, and every link was verified directly:

| Link | Evidence |
|---|---|
| Order submission requires no driver at all | `backend/order-management/src/main/kotlin/com/pios/ordermanagement/api/SubmitOrderRequest.kt` lines 68–78 — the whole request body is `passengerReference` plus eight optional fields. **There is no `driverId` on this contract.** Confirmed. |
| `OrderSubmitted` reaches Dispatch with the four facts routing needs | `backend/dispatch/.../persistence/OrderSubmittedFirstRefusalListener.kt` lines 129–139 (`orderId`, `passengerReference`, `explicitDriverIntent`, `isTest`) |
| No primary → `NoPrimaryDriver` | `FirstRefusalApplicationService.kt` line 140–141 |
| `NoPrimaryDriver` triggers Fallback with no exclusions | `OrderSubmittedFirstRefusalListener.kt` lines 154–159 |
| Tier 1 (trusted) tried first, then Tier 3 (open marketplace) | `FallbackDispatchApplicationService.kt` lines 145–148 — `trustedDriverRepository?.findLongestIdleTrustedAvailable(...) ?: driverAvailabilityRepository.findLongestIdleAvailable(...)` |
| Tier 1 is live, not inert — the `ADR-068` Q6 backfill shipped | `backend/passenger-experience/src/main/resources/db/migration/passengerexperience/V4__backfill_connection_established_outbox.sql` exists |
| Test/real segregation applies to both tiers | `ADR-069`, applied at `FallbackDispatchApplicationService.kt` lines 145–147 (`isTest` is a mandatory argument to both queries) |
| A real Proposal to a real driver results | `FallbackDispatchApplicationService.kt` lines 150–159 |

**So the brief's point 1 is correct about the backend.** It is **not** correct that this flow is reachable in production. It is not reachable, for three independent reasons, each sufficient on its own.

### Why the discovery path cannot actually fire today — three independent blocks

**Block 1 — no driverless entry point exists.** `RideRequest.tsx` is the only screen in the product that calls `POST /v1/orders`, and it is rendered at `/i/:driverCode/request` (its own KDoc, line 284). Its KDoc line 288 records that opening it without an identity *"redirects back to `/i/:driverCode`"*. Every path into it is keyed by a specific driver's code. A passenger holding no driver's link has no screen from which to submit an order at all — matching the audit's Section 6 step 3 finding.

**Block 2 — the only submitter hard-codes `explicitDriverIntent: true`, which switches the entire fallback chain off.** `frontend/src/pages/RideRequest/RideRequest.tsx` line 1013:

```tsx
explicitDriverIntent: true,
```

It is unconditional — not derived from state, not behind a branch. `FirstRefusalApplicationService.attempt` returns `ExplicitDriverIntentDeclared` immediately for that flag (lines 136–138), and `OrderSubmittedFirstRefusalListener` line 154 invokes Fallback Dispatch **only** for `NoPrimaryDriver` or `PrimaryDriverIneligible`. `ExplicitDriverIntentDeclared` is one of the three outcomes that skip it, deliberately, per that listener's own KDoc lines 92–96. **No order created by any real caller today can ever reach Fallback Dispatch.** Dispatch's own test suite already states this in as many words — `RepeatClientLoopIntegrationTest.kt` lines 52–56 describe `explicitDriverIntent = false` as *"the one condition under which First Refusal is meant to act at all; contrast `RideRequest.tsx`'s own always-explicit submissions, which deliberately never reach this path."*

**Block 3 — even with the flag cleared, the screen races itself.** `RideRequest.tsx` line 1020 calls `attemptProposal(response.orderId)` immediately after submission, creating a Proposal to `driverCode` directly. Fallback Dispatch would collide with it on `proposals_one_open_per_order` (V14) and collapse to `FallbackDispatchOutcome.AlreadyAttempted` (`FallbackDispatchApplicationService.kt` lines 160–172).

**Block 4, downstream — the existing post-ride "save this driver" action is keyed to the URL driver, not the actual driver.** `RideRequest.tsx` line 1968 renders "Добавить в мои водители" only when `rideStatus === 'COMPLETED'` and `!circle.some(m => m.driverId === driverCode)`, and `handleSaveDriver` (line 1303) posts `driverId: driverCode` — again the route param. In a discovery-matched ride there is no `driverCode`, so even if a stranger were matched and the ride completed, this button could not form the correct relationship.

### The relationship-formation surface that already exists, and already complies

This matters more than it first appears, because it changes what Part 2 actually has to build.

`RideRequest.tsx`'s `handleSaveDriver` (lines 1303–1330) is **already a passenger-explicit, post-completion "add this driver to my circle" action**, and it already:

- calls `POST /v1/connections` (`ConnectionController`, idempotent 201/200 by explicit requirement, `ADR-054` Part 4);
- sets primary **only if the passenger has no primary at all yet** (line 1315–1322), explicitly so that *"an existing primary is never silently replaced (Rule 4, `PRODUCT_DECISION_CIRCLE_OF_TRUST.md`)"* — its own KDoc, lines 1294–1297;
- causes `CreateConnectionApplicationService.handle` to publish `ConnectionEstablished` through the transactional outbox (lines 61–65), which feeds `ADR-068`'s Tier 1 projection — so a driver saved this way immediately becomes a trusted-tier fallback candidate.

That is the mechanism `PRODUCT_DECISION_CIRCLE_OF_TRUST.md` Decision 3 requires, already built, already live. **The thing genuinely missing for Channel 1 is not a way to create the relationship — it is a way for the passenger's post-ride screen to know *which driver actually completed the ride* when the passenger never chose one.** That is a read-model question inside a screen, not a new cross-module write.

### Why a Dispatch → Passenger Experience event is a bigger step than it looks

The brief is right that this direction is unprecedented. Verified:

- **Passenger Experience has no inbound event infrastructure whatsoever.** Its entire `src/main/kotlin` tree contains `OutboxRepository`, `OutboxRelay`, `RabbitMQEventPublisher` and `RabbitMQProducerTopologyConfiguration` — **producer-side only**. There is no `*Listener.kt`, no listener-container configuration, no consumer topology, no dead-letter queue, and no `markProcessed(eventId)` idempotency ledger anywhere in the module. Its four migrations are `V1__create_connections.sql`, `V2__create_primary_connections.sql`, `V3__outbox.sql`, `V4__backfill_connection_established_outbox.sql` — no processed-events table. Every other consumer in PIOS (`OrderSubmittedFirstRefusalListener`, `PrimaryConnectionEventListener`, `OrderCancelledListener`, `DriverAvailabilityChangedListener`) sits in a module that already had this machinery. Passenger Experience would be building it from zero.
- **`INTERFACE_CONTRACTS.md` §5's contracts all flow the other way or between other pairs.** The Passenger Experience ↔ Dispatch relationship it names (line 118–124, added by `ADR-062`) is **Passenger Experience → Dispatch**, one-directional. A Dispatch → Passenger Experience contract would be the seventh justified relationship and the first inbound one for that module.
- **`TripCompleted` does not carry the passenger, and Dispatch cannot always supply one.** `Trip.kt` line 124 constructs `TripCompleted(orderId = order, driverId = driver)`; the published payload is exactly `{orderId, driverId}` (`DispatchAssignmentApplicationService.kt` lines 358–367). Neither `Trip` nor `Assignment` holds a `passengerReference` at all — a repository-wide search of `backend/dispatch/.../domain` finds it on **`Proposal` only**, and there it is **nullable**: `val passengerReference: PassengerReference? = null` (`Proposal.kt` line 71, added by `ADR-066`). The only way Dispatch could name the passenger at completion is the same `ACCEPTED`-Proposal lookup `AssignmentCompleted`'s `statedPrice` already performs (`DispatchAssignmentApplicationService.kt` lines 307–309) — and that lookup yields nothing for an Assignment created by `AssignmentController.assignOrder` (Coordinator manual assignment), which has no Proposal at all. **Any automatic mechanism is therefore best-effort by construction, not a guarantee.**
- **`TripCompleted` currently has zero consumers** — `INTERFACE_CONTRACTS.md` line 160: *"None yet — Order Management's consumer was deliberately left bound to `AssignmentCompleted`."* That makes its payload comparatively free to extend, which Part 3 uses.

### Documentation staleness found while verifying, reported not fixed

`INTERFACE_CONTRACTS.md` lines 162–163 still mark `PrimaryConnectionDesignated`/`PrimaryConnectionCleared` as **"TARGET — ratified by `ADR-062`, not yet implemented"**, although `ADR-068` Context (line 57) records them as implemented and this session's own work depends on them being live. This is `ADR-068`'s own non-blocking Q8, still open. Named here so it is not lost; **not corrected by this ADR.**

---

## Problem

1. Can a passenger with no driver relationship submit an order and be matched to a real available driver by the existing dispatch machinery? (Answered above: architecturally yes, operationally no — four blocks.)
2. What must change to make that reachable, and what must **not** change so that an existing relationship is never damaged by it?
3. When such a ride completes, how does it become a Passenger↔Driver relationship the driver sees as a new client — and specifically, **may that be automatic?**

---

## Decision

### Part 1 — Channel 1 order entry: a driverless submission path, using the existing chain unchanged

**No new backend capability is required for matching.** The chain is built, tested, and (per the audit's Section 3) live-verified as recently as today. What is authorized here is making it *reachable*, under four constraints:

1. **A passenger-side entry point that is not keyed to a `driverCode`.** A route and screen that submits `POST /v1/orders` with `passengerReference` and the existing optional fields, and **omits `explicitDriverIntent`** (it defaults to `false` — `SubmitOrderRequest.kt` line 75) so First Refusal and Fallback Dispatch run as designed.
2. **It must not call `attemptProposal`.** Block 3 above. A driverless order's driver is chosen by Dispatch, never by the client. This is the structural difference between the two entry points and must be visible as such in the code, not left as an omission a future edit could undo.
3. **`RideRequest.tsx` is not modified in its submission behavior.** Its `explicitDriverIntent: true` (line 1013) and its `attemptProposal` call (line 1020) stay exactly as they are. See Part 4(b).
4. **No change to `SubmitOrderRequest`, `OrderSubmitted`, `FirstRefusalApplicationService`, `FallbackDispatchApplicationService`, `ProposalApplicationService`, or any Dispatch query.** Nothing in the matching chain is touched. This is an entry-point addition, not a dispatch change.

**Recorded honestly:** this makes `ADR-068`'s Tier 3 ("OPEN MARKETPLACE") the *primary* path for a stranger passenger, where today it is only a fallback-of-a-fallback. `ADR-068` ratified Tier 3 as a tier; it did not contemplate it becoming a front door. That posture change is real and is a Product Owner question — Part 7, Q1.

### Part 2 — Relationship formation after the ride: the decision that needs a human

#### Option B — passenger-confirmed (RECOMMENDED; compliant with every ratified document as they stand)

After a discovery-matched ride reaches `COMPLETED`, the passenger's own screen offers the **same explicit action that already exists** — "Добавить в мои водители" — now naming the driver who actually completed the ride rather than a URL parameter.

- **Mechanism:** the existing `POST /v1/connections`, called by the passenger's own client with the passenger's own Bearer token, exactly as `handleSaveDriver` already does (`RideRequest.tsx` lines 1309–1314). No new endpoint, no new event, no new consumer, no new table, no new queue, **no new cross-module contract in either direction**.
- **The only genuinely new piece:** the passenger's client must learn which driver completed the ride. It already reads `/v1/assignments?orderId=...` for ride status (`RideRequest.tsx` KDoc line 335) and `/v1/proposals?orderId=...` for acceptance — the matched driver's identity is available on the passenger's own already-authorized read path. **The exact endpoint and response shape are the developer's to finalize within this constraint**, subject to `ADR-060`/`ADR-066` authorization rules and `ADR-059` (Ride Contact Disclosure) for anything beyond a plain driver identifier.
- **Duplicate protection:** already guaranteed twice and needs nothing new — `CreateConnectionApplicationService.handle` returns the existing row with `created = false` and **publishes no event** on that branch (lines 50–53, 61–66), and `UNIQUE (driver_id, passenger_reference)` (`V1__create_connections.sql` line 15) is the race-proof backstop. A pair that already has a Connection (from a previous discovery ride, or because the passenger independently opened that driver's link) is a no-op.
- **Primary designation:** follows `handleSaveDriver`'s existing rule verbatim — set primary **only** when the passenger has no primary at all; never replace an existing one (`PRODUCT_DECISION_CIRCLE_OF_TRUST.md` business rule 1).
- **Why this is recommended:** it needs no Product Decision amendment, no new bounded-context contract, no new event infrastructure in a module that has none, and it works identically whether or not Dispatch can name the passenger (Context's nullable-`passengerReference` gap simply does not arise — the passenger is the one asking). It also keeps the four facts `PRODUCT_DECISION_CIRCLE_OF_TRUST.md` line 30 insists must never collapse — *"a specific ride's chosen driver, the driver who actually completed a ride, the passenger's primary driver, and… team membership"* — genuinely distinct, because the passenger performs the act that converts the second into circle membership.

#### Option A — automatic on Trip completion (fully designed; BLOCKED on a Product Decision amendment)

If the Product Owner amends `PRODUCT_DECISION_CIRCLE_OF_TRUST.md` Decision 3 and business rules 2–3 to permit it, the correct architectural mechanism is Part 3. **It must not be implemented before that amendment exists as its own dated Product Decision document.**

### Part 3 — The exact mechanism for Option A, if and only if it is ratified

**Chosen shape: extend the existing `TripCompleted` event with a nullable `passengerReference`, and give Passenger Experience its first consumer.** This mirrors `ADR-062`/`ADR-068`'s event-fed pattern, run in the opposite direction.

1. **Dispatch publishes.** `TripCompleted`'s outbox payload (`DispatchAssignmentApplicationService.kt` lines 358–367) gains a third field, `passengerReference`, resolved by the **same** `ACCEPTED`-Proposal lookup that already runs three lines earlier for `statedPrice` (lines 307–309). It carries **exactly three opaque identifiers** — `orderId`, `driverId`, `passengerReference` — plus `occurredAt`. No name, phone, price, rating, vehicle, or address. Reference-not-ownership (`ADR-005`, `ADR-019`) applies unchanged, in the new direction.
   - **Additive and safe:** `TripCompleted` has no consumer today (`INTERFACE_CONTRACTS.md` line 160), so adding a field breaks nothing. Per `ADR-030`, an additive optional field does not require a version bump; the developer confirms that against `ADR-030`'s own text rather than assuming it.
   - **`passengerReference` is nullable and will genuinely be null** for Coordinator-assigned Assignments, which have no Proposal. The consumer treats null as "nothing to do." **This is a best-effort mechanism, and must never be described to anyone as a guarantee.**
   - **`AssignmentCompleted` is untouched** — the legacy half of `ADR-063`'s dual-publish window stays byte-for-byte as it is, so Order Management's `AssignmentCompletedListener` is unaffected.
2. **Passenger Experience consumes.** A new `TripCompletedListener` bound to routing key `trip.completed`, on its own queue with its own dead-letter queue, following `PrimaryConnectionEventListener`'s structure exactly — but note this module has **none** of that machinery today (Context), so listener-container configuration, consumer topology, and a `processed_events` idempotency ledger (new migration `V5`) are all net-new work, not reuse.
3. **What the consumer does — and only this.** It calls the **existing** `CreateConnectionApplicationService.handle` with `(driverId, passengerReference)`. Nothing else.
   - **Duplicate guard: none is needed, and none may be added.** The existing service already returns the existing row with `created = false` and publishes nothing on that branch (lines 50–53), and `UNIQUE (driver_id, passenger_reference)` is the storage-level backstop. Adding a second guard in the consumer would duplicate an invariant `ADR-054` Part 2 deliberately placed in the schema.
   - **It must never set, clear, or read a primary designation.** `PRODUCT_DECISION_CIRCLE_OF_TRUST.md` business rules 1 and 3 remain in force regardless of any amendment to Decision 3 unless the amendment says otherwise in terms. `handleSaveDriver`'s "set primary if none exists" convenience is a *passenger-initiated* act and **must not** be replicated in an automatic consumer.
   - **It must never write anything Dispatch owns**, and Passenger Experience remains the sole owner of `connections`.
4. **Event-loop safety, checked explicitly.** `CreateConnectionApplicationService` publishes `ConnectionEstablished` on the creation branch, which Dispatch consumes into its Tier 1 projection (`ADR-068` Part 1). The resulting cycle is `TripCompleted → Connection → ConnectionEstablished → TrustedDriverRecord` and **terminates**: Dispatch's projection consumer publishes nothing, and a redelivered `TripCompleted` hits the `created = false` branch, which publishes nothing. No feedback loop exists. This was verified, not assumed.
5. **Not authorized even under Option A:** any driver-side write to a passenger's circle; any automatic primary designation; any new `Connection` field, status, or lifecycle; any Passenger Experience read of Dispatch state; any synchronous call in either direction.

### Part 4 — The two product-safety rules, confirmed structurally

**(a) A driver can never "steal" a passenger's existing primary or trusted driver. Confirmed — and the protection is stronger than Tier 1 alone.**

Two independent gates, both already in code:

- **Gate 1, before any tier is consulted:** Fallback Dispatch is only invoked at all for `NoPrimaryDriver` or `PrimaryDriverIneligible` (`OrderSubmittedFirstRefusalListener.kt` line 154). A passenger **with** an available primary driver never enters the fallback chain in the first place — First Refusal proposes to their primary and returns `Proposed` (`FirstRefusalApplicationService.kt` lines 147–156), an outcome that skips fallback entirely.
- **Gate 2, inside the chain:** `FallbackDispatchApplicationService.kt` lines 145–148 evaluate Tier 1 (trusted) before Tier 3 (open marketplace) using Kotlin's `?:` — Tier 3 is reached **only** when Tier 1 returns null. A stranger is therefore reachable only after the primary was unavailable/absent *and* no trusted driver was available. `ADR-068` Part 2 property 2's `excludeDrivers` semantics (applied identically to every tier) ensure the driver who just declined or lapsed is not re-selected, without weakening either gate.

So a stranger cannot displace a relationship; a stranger can only fill a hole the passenger's own network left open at that moment. **Caveat, stated rather than glossed:** these gates depend on Dispatch's `PrimaryDriverRecord` and `TrustedDriverRecord` projections being current. `ADR-068` Part 1 accepts staleness on the stated terms — a lagging projection degrades to "this passenger has no trusted drivers", which under Part 1 of this ADR now means "goes to a stranger" rather than "gets nothing". That is a real, new consequence of opening Channel 1 and is recorded in Consequences.

**(b) A repeat ride to "my driver" can never be rerouted to a stranger. Confirmed, and untouched by this ADR.**

`RideRequest.tsx`'s explicit-driver path is preserved in full: `explicitDriverIntent: true` (line 1013) → `ExplicitDriverIntentDeclared` (`FirstRefusalApplicationService.kt` lines 136–138), which that service's own KDoc (lines 90–98) guarantees is *"a complete no-op with respect to every collaborator this service owns"* — no read of `PrimaryDriverRepository`, no call to `ProposalApplicationService`. `OrderSubmittedFirstRefusalListener` line 154 then excludes that outcome from Fallback Dispatch by construction. Combined with `attemptProposal`'s direct Proposal to the chosen driver (line 1020), an order created on the `/i/:driverCode/request` route can reach **no** driver other than the one the passenger named. Part 1 constraint 3 forbids changing any of this.

### Part 5 — What this ADR does not authorize

Any implementation before ratification; **any automatic creation of a `Connection` from ride completion, under any mechanism, until `PRODUCT_DECISION_CIRCLE_OF_TRUST.md` is amended by its own Product Decision**; any automatic primary designation, ever; any driver-initiated write to a passenger's circle; any change to `RideRequest.tsx`'s submission behavior; any change to `SubmitOrderRequest`, `OrderSubmitted`, or any Dispatch selection query; any Tier 2 definition (`ADR-068` Part 3 remains in force); any ranking, scoring, or weighting of drivers; any read from, write to, or dependency on `network-management` (`ADR-064` Part 2); any driver-to-driver referral mechanism; any rating, reputation, or reviews; any "browse available drivers" directory or driver-profile surface for passengers; any geographic, distance, or ETA-based matching input (`ADR-034` Part 2's forbidden inputs are unchanged); any pricing, commission, or subscription mechanism (`ADR-002`; the audit's Section 11 decision is a separate, unstarted Product Decision); any change to `POST /v1/connections`'s request shape or its 201/200 idempotency semantics; any change to `GET /v1/connections?driverId=`'s response shape; any new authorization model (`ADR-054` Part 6's limitation stands unchanged and must not be described as solved).

### Part 6 — Consequences

**Positive**

- Channel 1 becomes reachable with **zero changes to the dispatch decision chain** — the matching machinery is already built, already segregated (`ADR-069`), already trust-ordered (`ADR-068`). The audit's Section 7 acceptance criterion becomes achievable.
- Under Option B: no new module, service, queue, event, table, cross-module contract, or event-consumer infrastructure anywhere. The relationship-formation mechanism already exists and already complies.
- The explicit-driver path and the primary/trusted protections are preserved structurally, not by convention (Part 4).
- `ADR-054` Part 5's "completing a ride never changes who is primary" stays guaranteed under both options.

**Negative**

- **Opening Tier 3 as a front door changes what a stale projection costs.** Today a lagging `TrustedDriverRecord` means a passenger gets nothing; after Part 1 it means they get a stranger. `ADR-068` Part 1 accepted staleness against the old cost, not this one. This should be re-confirmed, not inherited silently — Part 7, Q2.
- PIOS still has no authorization layer (`ADR-054` Part 6, `ADR-042` R8). A stranger-matching front door is a materially larger unauthenticated surface than a link-gated one. Nothing here changes that, and nothing here may be described as an enforced rule.
- A discovery-matched passenger meets a driver with no reputation signal of any kind — the audit's Section 3 records this as deliberate (`DriverTrustIndicator.tsx`: *"never a rating, count, rank, or any fabricated trust score"*). Channel 1 is where that deliberate absence is felt hardest. This ADR does not invent a reputation mechanism; it flags that Channel 1 makes the gap load-bearing.
- Under Option A only: Passenger Experience gains its first inbound consumer, first DLQ, first idempotency ledger, and first dependency on another module's events — a permanent operational surface, for a mechanism that is nullable-best-effort by construction.

### Part 7 — Blocking questions for the Product Owner

- **Q1 (Product Owner) — is stranger-matching as a front door ratified product direction?** `ADR-068` ratified Tier 3 as a *fallback* tier. Part 1 makes it a stranger passenger's primary path. `PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` §2 scopes fair-opportunity mechanics to NETWORK-scoped requests; a driverless order is a genuinely new request scope neither DIRECT nor previously described. Confirmation required, not inferred.
- **Q2 (Product Owner) — Option A or Option B?** If A, `PRODUCT_DECISION_CIRCLE_OF_TRUST.md` Decision 3 and business rules 2–3 must be amended by a dated Product Decision **before** any implementation. If B (recommended), no amendment is needed and Part 3 stays unbuilt.
- **Q3 (Product Owner) — if Option A: what happens for the orders it cannot cover?** Coordinator-assigned Assignments have no Proposal and therefore no `passengerReference` (Context). Silent no-op, or a passenger-confirmed fallback (i.e. Option B anyway)?
- **Q4 (Product Owner) — does a discovery-matched driver become *primary* if the passenger has none?** `handleSaveDriver` currently does this for the link-based flow (line 1315–1322). Extending that to a stranger the passenger met once is a different product statement, and business rule 1 makes it the passenger's call. This ADR does not assume it either way.
- **Q5 (Evidence Analyst) — which `E-NNN` entry justifies this Sprint?** As of 2026-09-15 the answer appears to be "none". If the honest basis is a hypothesis, **H9 must be registered in `PIOS_PRODUCT_HYPOTHESES.md` with «Как проверим» and «Критерий успеха» before the Sprint starts**, not after — the third application of the `PIOS_PRODUCT_EVIDENCE.md` «Дополнение 2026-08-01» basis after `ADR-054`/H7 and `ADR-068`/H8.
- **Q6 (Product Owner) — is `docs/PIOS_TAXI_CUSTOMER_ACQUISITION_DESIGN.md` required before implementation?** It does not exist. This ADR is the architectural half of a product design with no product half.

**Non-blocking, for implementation design:**

- **Q7 (Architect).** `INTERFACE_CONTRACTS.md` §5/§7 and `docs/README.md`'s ADR table need entries for whatever is ratified — plus the stale `ADR-062` "TARGET" rows noted in Context (`ADR-068`'s own Q8, still open). **Not performed by this ADR.**
- **Q8 (Developer).** How the passenger's client learns the completing driver's identifier (Option B), within `ADR-060`/`ADR-066` authorization and `ADR-059` disclosure limits.
- **Q9 (Developer).** What a passenger sees while a driverless order is unmatched, and what happens on `NoAvailableDriver` — today an order with no proposal simply sits. Channel 1 makes this state common rather than exceptional. A UX answer is needed; **it must not invent a retry, queue, or timeout rule** — `ADR-068` Part 4's "no retry on decline/lapse" and `ADR-052`'s lapse mechanism are unchanged.

---

## Alternatives Considered

- **Dispatch synchronously calls `POST /v1/connections` on trip completion.** Rejected. Puts a hard runtime dependency on Passenger Experience inside Dispatch's ride-completion write path, against `ADR-026`/`ADR-027`; `ConnectionController` gates its endpoints on the caller's own session token `sub` and there is no service-to-service auth path. `ADR-062` and `ADR-068` Part 1 both rejected the mirror-image option for the same reasons.
- **A new dedicated `RideRelationshipFormed` event instead of extending `TripCompleted`.** Rejected for Option A: `TripCompleted` already carries exactly this fact at exactly this moment and has no consumer to disturb; a second event expressing the same occurrence would need its own catalogue entry, routing key, and dual meaning, for no gain. (`ADR-068` rejected widening `PrimaryConnectionDesignated` for the opposite reason — that event is about a *different* fact. This one is not.)
- **Putting `passengerReference` on `Trip` or `Assignment`.** Rejected as out of proportion: it would add a field to two aggregates and their tables to avoid one lookup that `DispatchAssignmentApplicationService` already performs three lines away (lines 307–309) for `statedPrice`. It would also not fix the Coordinator-assignment case, which has no passenger fact anywhere in Dispatch.
- **Frontend-orchestrated automatic creation (the passenger's client silently POSTs the Connection on completion, with no button).** Rejected: it is Option A's product semantics with none of Option A's auditability, it is unenforceable server-side, and it would be a *de facto* amendment of `PRODUCT_DECISION_CIRCLE_OF_TRUST.md` Decision 3 made in a component file. `ADR-062` Option D rejected client-orchestrated routing on the same principle.
- **Deriving the relationship from ride history in a query rather than storing it.** Rejected outright — `ADR-054` Alternatives (line 148) rejects history-derived circle facts as *"the rejection that matters most"*, citing `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 6. Not reopened here.
- **Treating this as additive under the `Order.destination` precedent (Sprint 3B), with no ADR.** Rejected. That precedent covers an optional attribute with no competing claimant, no new invariant, and no new cross-module relationship (`ADR-054` Context lines 19–24). Part 1 changes which path a whole class of orders takes; Part 2 touches a ratified Product Decision and, under Option A, creates the first inbound cross-module contract for a module that has no consumer infrastructure. Neither is additive.
- **Using `network-management`'s Person↔Person graph for discovery.** Rejected without analysis: `ADR-064` Part 2 formally supersedes that module for new work and forbids *"any read from, write to, or new dependency on `network-management`."*

## Related ADRs

- [ADR-068: Relationship-Ordered Fallback Dispatch](ADR-068-Relationship-Ordered-Fallback-Dispatch.md) — the Trusted → Open chain this ADR makes reachable; Part 2's tier order is the structural anti-stealing guarantee in Part 4(a).
- [ADR-069: Test/Real Segregation in Fallback Driver Selection](ADR-069-Test-Real-Segregation-In-Fallback-Driver-Selection.md) — the second eligibility gate, applied unchanged to Channel 1 orders.
- [ADR-062: Primary Driver / First Refusal — Passenger Experience → Dispatch Contract](ADR-062-Primary-Driver-First-Refusal-Passenger-Experience-Dispatch-Contract.md) — the only existing contract between this module pair, and the mechanism-selection precedent Part 3 reuses in reverse.
- [ADR-054: Circle of Trust](ADR-054-Circle-of-Trust-Passenger-Experience-Owned-Primary-Driver-Relationship.md) — sole owner of `Connection`; its Part 5 and "does not authorize" list are the direct obstacle to Option A.
- [ADR-063: Trip as a New Dispatch-Owned Aggregate](ADR-063-Trip-as-a-New-Dispatch-Owned-Aggregate.md) — `Trip`/`TripCompleted` and the dual-publish window Part 3 extends without disturbing.
- [ADR-064: Referral Visibility — Network Management Formally Superseded](ADR-064-Referral-Visibility-No-Network-Management-Activation.md) — the prohibition applied in Part 5.
- [ADR-034: Assignment Policy and Dispatch Decision Architecture](ADR-034-Assignment-Policy-and-Dispatch-Decision-Architecture.md) — forbidden assignment inputs, unchanged.
- [ADR-066](ADR-066-Proposal-Participant-Authorization.md) / [ADR-060](ADR-060-Order-Query-Authorization.md) / [ADR-059](ADR-059-Ride-Contact-Disclosure.md) — the authorization and disclosure limits governing Q8.
- [ADR-005](ADR-005-Data-Ownership.md) / [ADR-009](ADR-009-Domain-Isolation.md) / [ADR-019](ADR-019-Conceptual-Data-Ownership.md) — reference-not-ownership, applied in the new direction without exception.
- [ADR-030](ADR-030-Event-Schema-Versioning-Strategy.md) / [ADR-031](ADR-031-Event-Infrastructure-Foundation-Architecture.md) / [ADR-032](ADR-032-Reliable-Event-Publication-Strategy.md) — versioning and outbox/consumer machinery Part 3 would reuse.
- [ADR-002](ADR-002-Dispatch-Engine.md) — pricing, commission, matching and regulatory rules out of architectural scope; applied in Part 5 and Part 7.

## References

Read for this ADR, **none modified**:

- `docs/PIOS_TAXI_PRODUCT_COMPLETION_AUDIT.md` — Sections 3, 6, 7, 13, 16
- `docs/PRODUCT_DECISION_CIRCLE_OF_TRUST.md` — Decision 3 (line 13), business rules (lines 26–30)
- `docs/PIOS_PRODUCT_HYPOTHESES.md` (H8 is the highest registered); `docs/PIOS_PRODUCT_EVIDENCE.md`
- `docs/INTERFACE_CONTRACTS.md` — §4, §5 (lines 118–124), §7 (lines 155–163), §17, §19
- `backend/order-management/src/main/kotlin/com/pios/ordermanagement/api/SubmitOrderRequest.kt`
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/persistence/OrderSubmittedFirstRefusalListener.kt`
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/FirstRefusalApplicationService.kt`
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/FallbackDispatchApplicationService.kt`
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/DispatchAssignmentApplicationService.kt` (lines 296–367)
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/domain/Trip.kt`, `domain/TripCompleted.kt`, `domain/Proposal.kt` (line 71)
- `backend/dispatch/src/test/kotlin/com/pios/dispatch/persistence/RepeatClientLoopIntegrationTest.kt` (lines 23–64)
- `backend/passenger-experience/src/main/kotlin/**` (full module listing — producer-only, no consumer)
- `backend/passenger-experience/src/main/kotlin/com/pios/passengerexperience/application/CreateConnectionApplicationService.kt`
- `backend/passenger-experience/src/main/resources/db/migration/passengerexperience/V1__create_connections.sql`, `V4__backfill_connection_established_outbox.sql`
- `frontend/src/pages/RideRequest/RideRequest.tsx` (KDoc lines 282–387; lines 979–1029, 1283–1330, 1961–1978)

# ADR-068: Relationship-Ordered Fallback Dispatch — Trusted → Network → Open Marketplace

## Status

**Accepted, narrowed scope: Trusted → Open only. Tier 2 ("Network") is explicitly deferred, not implemented, not defined.**

**Proposed Date:** 2026-09-15. **Ratified Date:** 2026-09-15, by the Product Owner, in-session.

Ratification resolves the five blocking questions as follows — recorded here, not silently assumed:

- **Q1 — matching rule, not ranking.** Accepted as reasoned in Part 2, property 1: a binary tier filter with the existing, unmodified `updated_at ASC` tie-break inside each tier introduces no cross-driver score or weight. `PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` §6(c) ("priority within an already-eligible pool") is answered narrowly and only for this fallback-candidate-filtering case: **yes, trust status may gate which pool tier is tried, never score or rank within a tier.** The general question — any influence on the *primary* assignment decision — remains open.
- **Q2 — no Tier 2 for now.** Ships as the named, structurally-empty tier Part 3 specifies. None of N-a/N-b/N-c is chosen. This ADR authorizes **Trusted → Open**, not the three-tier concept; the Tier 2 name stays reserved for a future, separately-ratified decision.
- **Q3 — no cascading retry.** Confirmed as already stated in Part 4: one `attempt`, one tier evaluation, no descent on a later decline/lapse of the selected driver's own Proposal.
- **Q4 — longest-idle tie-break, not history-derived.** Confirmed as already stated in Part 2; `ADR-054`'s rejection of history-derived circle facts is not reopened.
- **Q5 — evidence basis.** No `E-NNN` evidence exists. Per `PIOS_PRODUCT_EVIDENCE.md`'s second admissible Sprint basis (2026-08-01 addendum, the same path `ADR-054`/H7 used), a hypothesis has been registered **before** this Sprint: `PIOS_PRODUCT_HYPOTHESES.md` H8. This Sprint produces evidence; it does not yet rest on any.
- **Q9 — `ADR-054` supersession recorded.** `ADR-054` Part 5, Part 6 note, "Evolution Path" item 2, and "What This ADR Does Not Authorize" have each been amended in place (2026-09-15) with a narrow, explicit pointer to this ADR, per `ADR-015`. No silent disagreement between the two documents remains.

Q6 (backfill), Q7 (staleness), and Q8 (`INTERFACE_CONTRACTS.md`/`docs/README.md` index updates) remain **non-blocking implementation-design tasks**, owned by the developer executing this ADR, exactly as originally scoped below.

This document is the architect-role response to a product-owner-supplied concept ("PIOS Taxi — единая концепция", §10, "Fallback philosophy"), which is **not itself present in this repository** — no file under `docs/` contains that text, and it is therefore cited here as an external, oral/attached product statement, not as a ratified repository document. Per `ADR-007`, acceptance of this ADR is a human act; nothing below may be built on the strength of this document alone.

**Three preconditions are unmet as of this date, and each is individually blocking** (detailed in "Blocking Prerequisites"):

1. **No evidence supports this change.** `docs/PIOS_PRODUCT_EVIDENCE.md`'s journal contains exactly one real entry — `E-001` (05.09.2026, registration phone-format validation failure). The three `E-000` entries above it are explicitly labelled *«только демонстрация формата»* (line 76). That log's own gate rule (line 93–97) is *«Перед началом любого нового Sprint… Какое доказательство из журнала делает этот Sprint необходимым? Если ответа нет — не начинаем.»* Nothing in the log bears on fallback driver selection.
2. **Tier 2 ("Network") has no data source anywhere in PIOS**, and defining one is a product decision, not an architectural one (Part 3).
3. **The product question this ADR's ordering answers is explicitly recorded as OPEN** in `docs/PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` §6(c) (Part 6).

This ADR invents no business rule (`CLAUDE.md`, "Never Invent Business Rules"; `ADR-002`). Where the source concept is philosophical rather than operational, the gap is recorded as an open question below rather than filled with an assumption.

---

## Context

### What Fallback Dispatch actually does today (read from source, not remembered)

`backend/dispatch/src/main/kotlin/com/pios/dispatch/application/FallbackDispatchApplicationService.kt` lines 95–96:

```kotlin
val driver = driverAvailabilityRepository.findLongestIdleAvailable(excludeDrivers)
    ?: return FallbackDispatchOutcome.NoAvailableDriver
```

`backend/dispatch/src/main/kotlin/com/pios/dispatch/persistence/PostgreSQLDriverAvailabilityRepository.kt` lines 70–80 is the whole selection rule:

```sql
SELECT driver_reference FROM driver_availability
WHERE available = true [AND driver_reference NOT IN (?, …)]
ORDER BY updated_at ASC LIMIT 1
```

A flat, global, platform-wide query over Dispatch's own availability projection. It has no passenger parameter at all — `passengerReference` is accepted by `attempt()` and used only to populate `ProposeDriverCommand` (line 104), never to constrain selection. The service's own KDoc states this is deliberate for its current scope (lines 35–42): *"deliberately not geographic, not rated, not weighted: exactly the 'not complex matching' boundary FR-003A's own scope draws."* The live-production E2E observation reported alongside this task — fallback selects whichever real driver has been idle longest, with no relationship to the passenger's circle — is consistent with this code and requires no further verification.

**`FR-003A` exists only in code KDoc.** A repository-wide search of `docs/` returns zero occurrences of the identifier. Fallback Dispatch's selection rule was introduced under a product-owner instruction that was never written into a ratified document and is governed by no existing ADR: no ADR in `docs/ADR/` mentions Fallback Dispatch as a mechanism (`ADR-051` line 53 mentions "fallback" only to record that the human Coordinator is *not* one). This is itself a documentation gap this ADR surfaces rather than inherits.

### What Dispatch knows about relationships today — and what it does not

`ADR-062` (Accepted, 2026-09-03) created the one Passenger Experience → Dispatch contract that exists. It is implemented: `passenger-experience` publishes `PrimaryConnectionDesignated`/`PrimaryConnectionCleared`, `dispatch` consumes them (`PrimaryConnectionEventListener`, `RabbitMQPassengerExperienceTopologyConfiguration`, `V13__primary_driver.sql`) into `PrimaryDriverRecord`.

`PrimaryDriverRecord.kt` lines 20–23 carry **exactly two fields**: `passengerReference` and `primaryDriverId`. That record answers one binary question — *does this passenger have a primary driver, and who* — and `ADR-062` Constraints (line 79) fixes that scope: *"No ranking, scoring, or weighting is introduced — `PrimaryDriverRecord` answers a binary question."*

**Therefore: Dispatch today knows a passenger's primary driver and nothing else about their circle.** A passenger may have many `connections` rows (`ADR-054` Context: *"A many-to-many circle is already the schema's shape"*), but only the single primary designation is projected across the boundary. Tier 1 of the requested ordering — *trusted drivers other than the one who just declined* — is therefore **not answerable from any data Dispatch currently holds.**

### How fallback is triggered today (three call sites, all preserved by this ADR)

| Trigger | Call site | `excludeDrivers` passed |
|---|---|---|
| Order submitted, First Refusal produced no Proposal (`NoPrimaryDriver` / `PrimaryDriverIneligible`) | `OrderSubmittedFirstRefusalListener.onMessage` lines 154–159 | empty |
| Primary driver's own Proposal **declined** | `ProposalController.attemptFallbackAfterDecline` lines 429–438 | `setOf(proposal.driver)` |
| Primary driver's own Proposal **lapsed** (timeout sweep) | `ProposalLapseApplicationService.attemptFallbackAfterLapse` lines 147–163 | `setOf(proposal.driver)` |

Both decline and lapse paths identify "was this the primary driver's proposal" by reading `primaryDriverRepository.findByPassenger` fresh and comparing against `Proposal.driver` — so a driver Fallback Dispatch itself selected never re-triggers fallback when *their* proposal resolves. This is the "no retry on decline/lapse" boundary `FallbackDispatchApplicationService`'s KDoc lines 44–53 draws, and it is load-bearing for everything below.

### The ratified statements a tiered fallback runs into

- `ADR-054` Part 5 (line 126): **"No fallback-to-another-driver behavior, no group, no shared client list."** Its "What This ADR Does Not Authorize" (line 181) repeats this and adds *"any Assignment Policy, ranking, scoring, priority, or queue reordering."* A relationship-ordered fallback is precisely the behavior that section declined to authorize.
- `ADR-034` Part 2 (line 51): `ADR-062`'s promotion of Personal Client Relationship *"is a **narrow promotion out of this Future-candidate tier, not a general one**, and it must not be read as authorizing Personal Client Relationship for the assignment decision itself… or as any weighted/ranking input."*
- `ADR-034` Part 2 (line 61), forbidden inputs: passenger/driver **profile** data of any kind. Unchanged by this ADR.
- `docs/PIOS_TAXI_PRODUCT_DECISIONS.md` §10 (lines 117, 121): **MATCHING RULES** (explicit, explainable, binary/filter-based) are *"explicitly and permanently distinguished from **RANKING ALGORITHM** (any mechanism that scores or orders eligible drivers against each other). The latter is **not** to be introduced implicitly."*
- `docs/PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` §6(c): whether Personal Client Relationship affects *priority within an already-eligible pool* is **[OPEN]**, *"left exactly as open as ADR-034 Part 2… This document does not conflate (a), (b), and (c), and resolves none of them."*
- `ADR-064` Context (line 29): *"There is no driver-to-driver referral record anywhere in the codebase… **The entire 'who invited whom' fact PIOS captures anywhere, today, is the single-hop, driver→passenger `Connection`.**"* Evolution Path item 2: *"Driver-to-driver referral — no data path exists today; not decided here."* "What This ADR Does Not Authorize": *"any read from, write to, or new dependency on `network-management`."*

---

## Problem

1. Does implementing Trusted → Network → Open ordering require Dispatch to cross a bounded-context boundary it does not currently cross?
2. If so, what is the cleanest shape that preserves the ratified architecture?
3. What exactly can be specified now, and what cannot be specified without a product decision?

**Answer to (1): yes, for Tier 1; and Tier 2 crosses a boundary that does not exist at all.** Tier 3 requires nothing new. Tier 1 requires Dispatch to know a passenger's *whole* circle, which `ADR-062`'s contract deliberately does not carry.

---

## Decision

### Part 1 — Tier 1 contract: extend the existing Passenger Experience → Dispatch event contract, do not add a synchronous call

**Chosen mechanism: a new pair of Passenger Experience domain events, projected locally by Dispatch — the exact shape `ADR-062` already chose and `DriverAvailabilityRecord`/`PrimaryDriverRecord` already prove.**

- Passenger Experience publishes `ConnectionEstablished` (fired by the existing `CreateConnectionApplicationService` write path) and `ConnectionRemoved` (fired by the existing `RemoveConnectionApplicationService`), through the same transactional outbox already used for `PrimaryConnectionDesignated`/`PrimaryConnectionCleared` (`ADR-029`, `ADR-031`, `ADR-032`).
- Each event carries **exactly two opaque identifiers** — `passengerReference`, `driverId` — plus `occurredAt`. No name, phone, rating, `createdAt` of the underlying row, or any other `Connection` metadata. This is the same limit `PrimaryConnectionDesignated.kt` lines 18–22 already states for itself, and it keeps `ADR-034` line 61's forbidden-profile-data boundary intact by construction.
- Dispatch maintains a local, derived, non-authoritative projection (working name `TrustedDriverRecord`, a `(passenger_reference, driver_reference)` set, one new table in `pios_dispatch`), with its own per-consumer DLQ and its own `markProcessed(eventId)` idempotency ledger, following `PrimaryDriverRepository`'s own structure exactly.
- **Passenger Experience remains sole owner.** Dispatch never writes back, never treats its copy as authoritative, and exposes no query endpoint over it. Eventual consistency is accepted on the identical terms `ADR-062` already accepted for `PrimaryDriverRecord`: fallback is a courtesy ordering, never a guarantee, and a stale projection degrades to "this passenger has no trusted drivers", which is the current behavior.
- **`network-management` is not involved, read, or depended upon** — `ADR-064` Part 2's superseded status is honored without exception.

**Rejected alternatives for this contract** (same evaluation axes as `ADR-062`'s own table, which reached the same conclusion for the same module pair):

| Option | Why rejected |
|---|---|
| Dispatch synchronously calls `GET /v1/connections?passengerReference=` | `ConnectionController` gates that endpoint to the caller's own session token `sub` claim (`ADR-064` Context, line 24); there is no service-to-service auth path, and building one puts a hard runtime dependency on Passenger Experience inside Dispatch's order path, directly against `ADR-026`/`ADR-027`. `ADR-062` rejected this exact option already. |
| Carry the circle on `OrderSubmitted` | Couples Order Management to a fact it does not own; a stale payload can never be corrected. `ADR-062` Option B, rejected there for the same reason. |
| Widen `PrimaryConnectionDesignated`'s payload | The event is about one driver; carrying a set would change a live, versioned event's shape (`ADR-030`) to express a different fact. |
| Client-orchestrated (frontend supplies the circle) | Puts a routing rule on the client with no server-side enforcement point; `ADR-062` Option D, rejected. |

**Named implementation constraint, not a detail:** every existing `connections` row predates these events. Without a one-time, explicitly-designed backfill (replay from Passenger Experience, or an operator-run projection seed), Tier 1 is empty for **every existing passenger** and the behavior is indistinguishable from today's. The backfill mechanism is **not designed here** and must be part of the implementation handoff.

### Part 2 — The selection algorithm, stated exactly

`FallbackDispatchApplicationService.attempt(order, passengerReference, isTest, excludeDrivers)` evaluates tiers **strictly in order**, stopping at the first tier that yields a driver. Exactly **one** Proposal is created per `attempt` call — no broadcast, no parallel offers, no internal retry loop.

```
T1  TRUSTED
    candidates := { d : (passengerReference, d) ∈ TrustedDriverRecord }
                  ∩ { d : driver_availability.available = true }
                  \ excludeDrivers
    pick       := the candidate with the oldest driver_availability.updated_at
    if pick ≠ ∅ → propose(pick); return

T2  NETWORK
    NOT DEFINED. No candidate set is computable from any data PIOS holds today.
    Until a product decision defines it (Part 3), this tier yields ∅ unconditionally
    and the algorithm falls through. It must be implemented as an explicitly empty,
    named tier — never as a guessed definition.

T3  OPEN MARKETPLACE
    pick := driverAvailabilityRepository.findLongestIdleAvailable(excludeDrivers)
            — the existing query, byte-for-byte unchanged
    if pick ≠ ∅ → propose(pick); return

    → FallbackDispatchOutcome.NoAvailableDriver   (unchanged)
```

Four properties of this shape are deliberate:

1. **Tier membership is a binary filter, and the tie-break inside every tier is the already-shipped `ORDER BY updated_at ASC` rule.** No new comparison between drivers is introduced, no score, no weight. This is what keeps the change on the MATCHING RULES side of `docs/PIOS_TAXI_PRODUCT_DECISIONS.md` §10's line — *offered for product-owner confirmation, not asserted as settled*, because §10's own text forbids introducing a ranking mechanism "implicitly" and a three-tier preference order is close enough to that line that silence would be the wrong answer. See Part 6, Q1.
2. **`excludeDrivers` applies identically to every tier, and this is load-bearing.** The primary driver is by definition a member of the passenger's own circle (`ADR-054` Part 2's composite foreign key makes the primary a `connections` row for that same passenger). Without the exclusion reaching T1, a primary driver who just declined or lapsed would be the *first* candidate re-selected. Existing `excludeDrivers` semantics are preserved and extended to the new tier — no new exclusion concept is introduced.
3. **Availability remains the sole eligibility gate at selection time**, sourced only from Dispatch's own `driver_availability` projection (`ADR-034` Part 2, "current ratified inputs"). The trusted projection narrows the candidate set; it never overrides availability, and it carries no eligibility meaning of its own.

   > **Amendment pointer (2026-09-15, added same day; narrowing is PENDING RATIFICATION).** `ADR-069` proposes a second eligibility gate at selection time — test/real segregation — in response to a confirmed live production defect: `pios_dispatch.driver_availability` has no `is_test` column, so both this ADR's Tier 1 query and the pre-existing Tier 3 query can offer a real passenger's order to an `is_test = true` driver. If `ADR-069` is ratified, the sentence above reads "availability, **within the order's own data domain**, remains the sole eligibility gate", and `ADR-069` Part 5 makes the same predicate on this ADR's Tier 1 query a **merge precondition** for this ADR's implementation, not a follow-up. Recorded here per `ADR-015` so no silent disagreement exists between the two documents. Nothing else in this ADR is changed by that pointer.
4. **`ProposalApplicationService.handle` remains the sole authority on availability**, exactly as `FirstRefusalApplicationService`'s KDoc lines 38–57 already document for its own pre-check: the tier queries are pre-filters that only determine *which* driver is offered; the atomic check inside `handle` is still what actually enforces it. No new race is introduced, and the existing accepted race is not re-litigated.

### Part 3 — Tier 2 ("Network") is NOT DEFINED by this ADR, and must not be invented by the implementation

The source concept describes Tier 2 as *"drivers connected to the passenger's trusted drivers (a 'friend of a trusted driver') or otherwise in the passenger's extended network."* Against the actual data model, that sentence has **no referent**:

- The only relationship graph PIOS records is **bipartite passenger↔driver** (`connections`). A driver's "connections" are their passengers, never other drivers.
- There is **no driver↔driver edge anywhere** — `ADR-064` line 29 states this after a full audit, and its Evolution Path item 2 leaves "driver-to-driver referral" explicitly undecided.
- `network-management`'s `Person↔Person` graph is a self-generated identity space with no bridge to real accounts, formally superseded for new work by `ADR-064` Part 2, and may not be used.

Three candidate definitions are recorded **for product-owner selection only. None is chosen, ranked, or previewed by this ADR** (`ADR-034` Part 4's own discipline, reused):

| Candidate | Buildable from data that exists today? | What it would mean |
|---|---|---|
| **N-a — Two-hop co-passenger** : drivers trusted by passengers who also trust one of *my* trusted drivers | Yes, from `connections` alone | "A driver trusted by someone who trusts my driver." A genuine social signal, but also a privacy surface nobody has ratified, and combinatorially unbounded in a dense city. |
| **N-b — Driver-to-driver referral** : drivers who joined through another driver's link | **No** — no such record exists (`ADR-064` line 29); requires a new capture mechanism first | Closest to the concept's literal wording. Blocked on a product decision `ADR-064` Evolution Path item 2 already named. |
| **N-c — Explicit driver-declared peers** : a driver names drivers they vouch for | **No** — no such concept, table, or screen exists | Introduces vouching/trust-delegation, which `ADR-054` Part 5 excluded outright ("No Team, Fleet, crew, or delegation mechanism of any kind"). |

**Until one is ratified, T2 must ship as a named, structurally-empty tier.** Choosing N-a because it happens to be queryable would be exactly the "inventing a business rule" `CLAUDE.md` forbids and `ADR-064` already refused once for the adjacent referral-chain feature.

### Part 4 — What stays unchanged (FR-003A semantics, preserved in full)

Every one of the following is explicitly **not** modified by this ADR, and the implementation must preserve each:

- **`FallbackDispatchOutcome`'s three cases** (`Proposed` / `NoAvailableDriver` / `AlreadyAttempted`) and their meanings. "No driver in any tier" is `NoAvailableDriver`, the same non-exceptional outcome it is today — not an error, not a new state, not a new event.
- **Concurrency safety.** `Proposal.propose`'s in-memory one-open-proposal-per-order invariant and `proposals_one_open_per_order` (V14) remain the only deduplication mechanisms. Both `IllegalStateException` and `DataIntegrityViolationException` continue to collapse to `AlreadyAttempted` (`FallbackDispatchApplicationService.kt` lines 108–120). **No new idempotency ledger, no new lock, no new advisory lock.**
- **Lapse semantics.** `ProposalLapseApplicationService`'s timeout sweep, its `timeoutMinutes` configuration parameter (not a ratified business rule — `ADR-051`/`ADR-052`), its treatment of a concurrently-resolved Proposal as already-resolved rather than a failure, and its primary-driver identification by fresh `findByPassenger` comparison — all unchanged.
- **"No retry on decline/lapse"** for a driver Fallback Dispatch itself selected. This ADR does **not** authorize cascading down the tiers on each successive decline. A single `attempt` evaluates the tiers once; if the resulting Proposal is later declined or lapses, the order returns to unmatched exactly as today, Coordinator-visible. Turning the tiers into a retry ladder is a separate decision (Part 6, Q3).
- **The three trigger call sites** and the `excludeDrivers` values they pass.
- **First Refusal** (`FirstRefusalApplicationService`) — unchanged in every respect, including `ExplicitDriverIntentDeclared`'s complete-no-op guarantee.
- **`PrimaryDriverRecord`, `ADR-062`'s contract, and `ADR-054`'s `primary_connections`** — unchanged; the new projection sits beside `PrimaryDriverRecord`, it does not replace or subsume it.
- **Order Management, Driver Management, Identity, `network-management`, `core`** — untouched.

### Part 5 — What this ADR does not authorize

Any implementation before ratification; any Tier 2 definition (Part 3); any read from or dependency on `network-management`; any driver-to-driver referral or vouching mechanism; any new `Connection` lifecycle, cap, or expiry; any score, weight, or ranking across drivers; any profile, rating, reputation, phone, or name crossing the Passenger Experience → Dispatch boundary; any synchronous cross-module call on a write path; any change to `POST /v1/proposals`'s existing shape or its still-unresolved authentication gap; any Assignment Policy implementation (`ADR-034` Part 3's port still does not exist); any broadcast/parallel offering of one order to multiple drivers; any cascading retry across tiers; any change to the lapse timeout value; any change to Passenger Experience's existing endpoints or response shapes.

### Part 6 — Blocking Prerequisites and Open Questions

**Blocking — must be answered by the Product Owner (or by ChatGPT-the-architect where marked) before a developer starts:**

- **Q1 (Product Owner).** Is a three-tier relationship-ordered fallback a *matching rule* or a *ranking algorithm* under `PIOS_TAXI_PRODUCT_DECISIONS.md` §10? This ADR's reading is "matching rule" (Part 2, property 1), but §10 forbids introducing ranking "implicitly", and `PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` §6(c) records *priority within an already-eligible pool* as **[OPEN]**. This is exactly question (c). Confirmation is required, not inferred.
- **Q2 (Product Owner).** Which Tier 2 definition, if any — N-a, N-b, N-c, or "no Tier 2 for now" (Part 3)? N-b and N-c additionally require a new capture mechanism that does not exist.
- **Q3 (Product Owner).** On decline/lapse of a Tier-1 driver's Proposal, does dispatch descend to the next tier, or does the order return to unmatched as it does today? Today's behavior is "no retry" (`FallbackDispatchApplicationService` KDoc lines 44–53). A tiered ladder implies retries; the concept does not say so.
- **Q4 (Product Owner).** Within Tier 1, is longest-idle the right tie-break, or should it be something relationship-derived (most recent completed ride together, most frequent)? **Any history-derived ordering is rejected by default** — `ADR-054`'s Alternatives (line 146) rejects deriving circle facts from ride history outright, citing `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 6. Reopening that requires superseding `ADR-054`.
- **Q5 (Evidence Analyst).** Which `E-NNN` entry justifies this work? As of 2026-09-15 the answer appears to be "none" (Status, above). If the honest basis is a registered hypothesis rather than evidence, that is the *second* admissible Sprint basis recorded in `PIOS_PRODUCT_EVIDENCE.md`'s «Дополнение 2026-08-01» and used by `ADR-054` — but it must be registered in `PIOS_PRODUCT_HYPOTHESES.md` with «Как проверим» and «Критерий успеха» **before** the Sprint, not after.

**Non-blocking, to be resolved during implementation design:**

- **Q6.** The Tier-1 backfill mechanism for pre-existing `connections` rows (Part 1, named constraint). Without it the feature is inert for every current passenger.
- **Q7.** Behavior when the trusted projection is stale or its consumer is dead-lettered — this ADR's position is "degrade to the next tier, never block", matching `ADR-062`'s own staleness stance; confirm no monitoring gap results.
- **Q8 (Architect).** `docs/INTERFACE_CONTRACTS.md` §5 and §7 will need a new event pair and an amended Passenger Experience → Dispatch contract entry, following `ADR-053`/`ADR-062`'s additive precedent. **Not performed by this ADR.** `docs/README.md`'s ADR table likewise.
- **Q9 (Architect).** `ADR-054` Part 5 (line 126) and its "What This ADR Does Not Authorize" (line 181) explicitly exclude fallback-to-another-driver behavior. If this ADR is ratified it **partially supersedes** those statements, narrowly, for fallback candidate-set filtering only — that supersession must be recorded in `ADR-054` itself per `ADR-015`, not left as a silent disagreement between two documents.

---

## Consequences

### Positive

- Fallback stops being blind to the one relationship PIOS's product thesis is built on, using the event-fed-projection pattern this codebase has already proven three times (`DriverAvailabilityRecord`, `PrimaryDriverRecord`, `core`'s Slice 01 projection).
- No new module, no new service, no new synchronous cross-module call, no shared schema, no cross-module foreign key. The reference-not-ownership rule (`ADR-005`, `ADR-019`) is applied unchanged: two opaque strings, nothing else.
- Tier 3 is the current behavior verbatim, so the change is strictly additive at the bottom of the ladder — a passenger with no circle gets exactly today's outcome.
- Degradation is safe by construction: an empty or lagging projection falls through to today's query.

### Negative

- Dispatch gains a second relationship projection, a second Passenger Experience consumer queue/DLQ pair, and a second table fed by another module's events — more eventual-consistency surface to operate and monitor.
- `ADR-054` Part 5's explicit "no fallback-to-another-driver behavior" must be formally superseded (Q9). Recorded here rather than glossed.
- Tier 2 ships empty, so the delivered feature is "Trusted → Open", not the three-tier concept. Naming the tier without defining it is honest but leaves a visible stub.
- The backfill (Q6) is real work with no product-visible output, and skipping it silently produces a feature that appears to work and changes nothing.
- PIOS still has no authorization layer (`ADR-054` Part 6, `ADR-042` R8); nothing here changes that, and nothing here should be described as an enforced rule.

---

## Related ADRs

- [ADR-062: Primary Driver / First Refusal — Passenger Experience → Dispatch Contract](ADR-062-Primary-Driver-First-Refusal-Passenger-Experience-Dispatch-Contract.md) — the contract this ADR extends, and the mechanism-selection precedent it reuses wholesale.
- [ADR-054: Circle of Trust](ADR-054-Circle-of-Trust-Passenger-Experience-Owned-Primary-Driver-Relationship.md) — source of truth for the circle; Part 5's fallback exclusion is what this ADR would partially supersede (Q9).
- [ADR-064: Referral Visibility — Network Management Formally Superseded](ADR-064-Referral-Visibility-No-Network-Management-Activation.md) — the audit establishing that no driver-to-driver edge exists (Part 3), and the prohibition on using `network-management`.
- [ADR-034: Assignment Policy and Dispatch Decision Architecture](ADR-034-Assignment-Policy-and-Dispatch-Decision-Architecture.md) — Part 2's ratified/forbidden input lists, applied unmodified; Part 3's Assignment Policy port, still not created.
- [ADR-051](ADR-051-Proposal-Lifecycle-Resolution-Ownership.md) / [ADR-052](ADR-052-Proposal-Lapse-Resolution-Mechanism.md) — lapse ownership and mechanism, preserved unchanged (Part 4).
- [ADR-005](ADR-005-Data-Ownership.md) / [ADR-009](ADR-009-Domain-Isolation.md) / [ADR-019](ADR-019-Conceptual-Data-Ownership.md) — reference-not-ownership, applied, not excepted.
- [ADR-029](ADR-029-Message-Broker-Selection.md) / [ADR-031](ADR-031-Event-Infrastructure-Foundation-Architecture.md) / [ADR-032](ADR-032-Reliable-Event-Publication-Strategy.md) / [ADR-030](ADR-030-Event-Schema-Versioning-Strategy.md) — the outbox/consumer/versioning machinery reused with no new technology.
- [ADR-002](ADR-002-Dispatch-Engine.md) — pricing/commission/matching/regulatory rules are out of architectural scope; Part 3 and Part 6 apply it.

## References

Read for this ADR, none modified:

- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/FallbackDispatchApplicationService.kt`
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/DriverAvailabilityRepository.kt`
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/persistence/PostgreSQLDriverAvailabilityRepository.kt`
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/FirstRefusalApplicationService.kt`
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/ProposalLapseApplicationService.kt`
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/PrimaryDriverRecord.kt`, `PrimaryDriverRepository.kt`
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/persistence/OrderSubmittedFirstRefusalListener.kt`
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/api/ProposalController.kt` (lines 123–128, 392–438)
- `backend/passenger-experience/src/main/kotlin/com/pios/passengerexperience/application/ConnectionRepository.kt`
- `backend/passenger-experience/src/main/kotlin/com/pios/passengerexperience/domain/PrimaryConnectionDesignated.kt`
- `docs/PIOS_TAXI_PRODUCT_DECISIONS.md` §10, §17, §19
- `docs/PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` §2, §6, §11
- `docs/PIOS_PRODUCT_EVIDENCE.md` (journal, lines 59–97)
- "PIOS Taxi — единая концепция" §10 — **external, product-owner-supplied; not present in this repository.**

# ADR-074: Subscription / Billing Foundation — a New Bounded Context That Gates Nothing

## Status

**Accepted, 2026-09-15, by the Product Owner, in-session — foundation/container only, per the precondition below, which stays binding after ratification, not just before it.**

**Author:** Architect role.

**Number collision, resolved.** `ADR-071`/`072` are claimed/declined by the parallel review; `073` is `ADR-073` (referral); `074` stays as originally chosen.

### Preconditions

1. **The Product Decision this ADR depends on for a *paid tier* still does not exist, and ratifying this ADR does not create it.** `docs/PIOS_TAXI_COMMERCIAL_PRODUCT_BLUEPRINT.md` Section 19, Decision 1: *"What does a paying driver actually get — the single most important open decision."* This ADR is accepted **only as the container** — the state machine and module boundary — precisely so it can exist without that decision, per its own Part-1-not-Part-2 framing below. **The paid tier itself remains blocked on Decision 1.** No price, tier, period, or feature gate may be built from this ADR alone.
2. **Evidence basis: H12 registered.** H10 (`ADR-071`) and H11 (`ADR-073`) were claimed the same day; this ADR's hypothesis is registered as **H12** in `docs/PIOS_PRODUCT_HYPOTHESES.md`, immediately below, honoring the architect's own Q4 concern (a state machine that gates nothing and charges nobody produces limited observable behavior) by scoping H12's own success criterion to what this Sprint can actually observe — not to willingness-to-pay, which stays H4's own, separate, still-open question.
3. **One basis per Sprint** — satisfied: `ADR-073` and this ADR are ratified together but hold separate hypotheses (H11, H12).
4. **`ADR-070` Part 5's non-authorization of "any pricing, commission, or subscription mechanism"** is satisfied by scope: this ADR authorizes the state-machine container only, sets no price/commission/fee of any kind, and gates nothing on the ride path — consistent with, not an exception to, that prohibition.

**This ADR sets no price, tier, billing period, trial length, currency, discount, or fee, and none may be inferred from it** (`ADR-002`; `CLAUDE.md`, "Never Invent Business Rules"; `PRODUCT_DECISION_ENTREPRENEUR_MODEL.md` §6, which is [HYPOTHESIS] in full).

---

## Context

### What exists today: nothing

`docs/PIOS_TAXI_COMMERCIAL_PRODUCT_BLUEPRINT.md` Section 10 classifies *"Monetization / billing / subscription"* as **MISSING**, *"confirmed zero code, repo-wide."* Re-verified for this ADR by repository search of `backend/`: the single match for `subscription|billing` is an unrelated AMQP term in `order-management`'s `RabbitMQConsumerTopologyConfiguration.kt`. There is no subscription state, no plan, no period, no payment record, and no provider integration anywhere.

### What is already ratified about money, and binds this design

| Constraint | Source |
|---|---|
| Payment for a ride happens **directly between passenger and driver, outside PIOS**; no PIOS payment custody | `PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md` lines 33, 46, as quoted in `PRODUCT_DECISION_ENTREPRENEUR_MODEL.md` §2.4 |
| Payment to PIOS **may never purchase priority**, *"independent of which commercial model is eventually ratified"* | `PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` §8, line 61 (a constraint reused and extended three times across the decision chain) |
| *«Оплата следует за трудом»* — the executing driver is paid for the ride | `PIOS_PRODUCT_DECISIONS_NETWORK_RULES.md` Принцип 3 |
| Whatever PIOS eventually charges for is *"a **tool**, never a **queue position**"* | `PRODUCT_DECISION_ENTREPRENEUR_MODEL.md` §5 |
| The commercial model itself is **genuinely undecided**, not decided-against | `PROJECT_CONSTITUTION.md` Sections 19, 26, as cited in `PRODUCT_DECISION_ENTREPRENEUR_MODEL.md` §7.1 |

The blueprint's own Section 5 (line 81) reaches the same place from the product side: the baseline *"a driver can always earn through PIOS"* guarantee — registration, going online, receiving and completing a ride, keeping 100% of the stated price — *"must almost certainly stay free"*, because charging for the right to work *"would contradict «оплата следует за труду» and would functionally become the commission-based aggregator model this product explicitly rejects."*

### A boundary finding the Sprint brief did not anticipate: `Payments` already exists as a ratified context

`ADR-017` (line 13) divides PIOS into **eight** bounded contexts, including **Payments**. `ADR-018` (line 19) scopes it: *"owns the platform's own record of payment-related information **associated with transportation demand**."* `docs/MODULE_STRUCTURE.md` lines 76–81 carries the same scope (*"Payment information associated with orders"*, capabilities *Record Payment* / *Retrieve Payment Record*, boundary *"Never owns external settlement information"*). It has never been scaffolded.

**A driver→PIOS subscription is not that.** Payments is money for a **ride** (passenger→driver, and ratified as flowing *outside* PIOS). A subscription is money for **PIOS itself** (driver→platform). Different parties, different direction, different relationship to custody. Quietly filing subscription under `Payments` would redefine a ratified context's responsibility without saying so — which is exactly the silent re-decision `MODULE_STRUCTURE.md` §8 and `CLAUDE.md` forbid. The choice must be made explicitly, either way, and this ADR makes it explicitly.

### The precedent for adding a context

`MODULE_STRUCTURE.md` §8: *"A new module is added only when a new bounded context is ratified through a decision that extends or supersedes `ADR-017` and `ADR-018`, consistent with `ADR-015`."* `ADR-067` performed exactly that act five days ago for `core` (`backend/settings.gradle.kts` lines 13–19). This ADR follows its shape.

---

## Problem

1. Where does a per-driver subscription state live, without putting commercial state inside the module the ride path depends on?
2. What is the minimal state machine, given that no price, tier, or benefit is decided?
3. What abstraction lets a real payment provider attach later without a rewrite — without building a speculative integration now?
4. Where could a *future* feature gate hook in, and how is it guaranteed that **nothing is gated today**?

---

## Decision

### Part 1 — A new bounded context, `billing`, distinct from the ratified `Payments` context

**Authorized in proposed form:** a new bounded context `billing`, implemented as an independently deployable Spring Boot / Kotlin module `backend/billing` with its own PostgreSQL database `pios_billing`, the same hexagonal `domain / application / api / persistence` structure, plain-JDBC + Flyway, and per-module migration path (`classpath:db/migration/billing`) as every existing module. **This extends `ADR-017` and `ADR-018` by adding one bounded context. It supersedes nothing.**

**The demarcation, recorded in one line so it is not re-litigated:** *`Payments` is the record of money for a ride (still unscaffolded; custody still forbidden). `billing` is the record of money for PIOS itself.*

Why not the alternatives — each checked against its own owning document:

- **Extend `driver-management`.** The honest case for it is proportionality: one table and one enum is a small thing to justify a service. The case against is structural and decisive. `driver-management` is on the ride critical path and is the source of `DriverAvailabilityChanged`, which `dispatch` consumes into the availability projection that feeds driver selection. Commercial state living in that module makes a future accidental gate cheap to write and hard to notice — precisely the outcome `PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` §8 forbids *"independent of which commercial model is eventually ratified."* Isolation makes "billing cannot influence dispatch" true by construction rather than by review. **Rejected — but this is the alternative most worth the Product Owner overruling if proportionality is judged to outweigh the structural guarantee (Blocking Question Q2).**
- **`core`.** `ADR-067`'s Boundary (line 84): Core is authority for none of these concepts and *"any future slice that would make Core the authority for a concept currently owned by a Taxi module requires its own superseding ADR."* Slice 01 is explicitly read-only, publishes nothing, and writes nowhere. A subscription is authoritative write state. **Rejected.**
- **`identity`.** `ADR-038` keeps Identity the authentication anchor. A commercial relationship is not an authentication fact. **Rejected.**
- **`network-management`.** Forbidden by `ADR-064` Part 2. **Rejected.**
- **The ratified `Payments` context.** Rejected on the demarcation above — but this is a legitimate alternative if the Product Owner prefers to widen `Payments`' ratified responsibility, which would require this ADR to say so in terms rather than being read as compatible with it.

**Carrying cost, named so it is not repeated by accident.** PIOS already carries one unused module indefinitely (`network-management`, `ADR-064` Consequences: *"a known, accepted carrying cost rather than a resolved one"*). To avoid a second: **this ADR authorizes the placement, not the scaffolding.** `backend/billing` is created by the Sprint that actually implements Part 2, not in advance. Until then this document is a placement decision and nothing exists on disk.

### Part 2 — The minimal state machine

States: **`FREE`**, **`TRIAL`**, **`ACTIVE`**, **`EXPIRED`**.

**The load-bearing property: absence of a record *is* `FREE`.** No row is created for any existing driver; no backfill; no migration touching `driver-management`. "Everything is free today" is therefore true by construction, not by policy, and remains true for every driver who never interacts with this module.

Permitted transitions, and nothing else:

| From | To | Meaning |
|---|---|---|
| `FREE` (no record) | `TRIAL` | a trial period was started |
| `FREE` (no record) | `ACTIVE` | a paid period was recorded |
| `TRIAL` | `ACTIVE` | a paid period was recorded |
| `TRIAL` | `EXPIRED` | the period ended with no new period |
| `ACTIVE` | `EXPIRED` | the period ended with no new period |
| `EXPIRED` | `ACTIVE` | a new paid period was recorded |

Aggregate state: `driverId` (a plain string reference to `driver-management`'s Driver — never a foreign key, never a copy of that module's data, never a synchronous call on a write path: `ADR-005`/`ADR-019`, the rule first established for `network-management` in `ADR-037`), `status`, `currentPeriodEnd: Instant?`, `isTest`, and creation/update timestamps. Exactly one current subscription record per driver.

`EXPIRED` is a **recorded transition**, driven by an explicit sweep over `currentPeriodEnd` (the `OutboxRelayScheduler` pattern already present in every module), not a status inferred at read time by a rule this ADR would have to invent.

**Deliberately not designed, because they are business rules:** grace periods, dunning, retries, proration, refunds, cancellation-vs-expiry semantics, whether a trial is offered at all, trial length, period length, currency, price. Each requires a Product Decision. None may be added by a developer or by an ADR.

**`isTest`** is supplied by the caller that creates the record, exactly as `Driver.isTest` and `Order.isTest` are supplied by their own callers (`ADR-069`'s sourcing pattern: *"this aggregate's own field, sourced by the owning module, never inferred or fetched by any consumer"*). It is **not** fetched from `driver-management` — that would be a copy of another module's data, which the reference-not-ownership rule forbids.

### Part 3 — The billing abstraction: a seam, not a provider SDK

**No payment provider is integrated, and no provider-shaped port is declared.** `.claude/CLAUDE.md` explicitly lists *"speculative refactoring"* and *"unnecessary abstractions"* among what to avoid; an empty `PaymentProvider` interface with no implementation is a permanent fiction that predicts a provider's shape before one is chosen.

What is decided instead is **where a provider attaches**:

1. **The application service is the seam.** A subscription period is recorded by a single command (`RecordSubscriptionPeriodCommand` → application service → aggregate transition). Every future inbound path lands here.
2. **Today's only inbound adapter is owner-gated.** `POST /v1/subscriptions/{driverId}/periods`, protected by the `OwnerCredentialGate` pattern already present in `driver-management`. A subscription period is recorded by an authorized human operator, not by a provider callback.
3. **Tomorrow's provider is one more inbound adapter** — a webhook controller in `api/` that validates the provider's signature and calls the *same* application service. It adds an adapter; it does not change the aggregate, the state machine, or any other module. That is the "no rewrite" guarantee, and it is a real one precisely because the domain never learns the provider's vocabulary.
4. **What a provider integration may never do, whenever it arrives:** take custody of ride payments (`PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md` lines 33/46); write to any other module; read any other module synchronously; publish an event any Taxi module consumes.

### Part 4 — The future gate: exactly one seam, everything else forbidden

**Nothing is gated by this ADR. Nothing may be gated without a further ADR *and* the Product Decision in Section 19/Decision 1 of the blueprint.**

**The one authorized future seam:** a read-only `GET /v1/subscriptions/{driverId}` returning `{status, currentPeriodEnd}`, session-token-gated to that driver (the `drv`-claim check already replicated in five modules per `ADR-055` Decision 1), consumed **only by the frontend, only to decide what to display.**

**Permanently forbidden without a superseding ADR, and the reason for each:**

- **Any subscription input to Dispatch** — First Refusal, any Fallback tier (`ADR-068`), discovery matching (`ADR-070`), ordering, tie-break, or eligibility. `PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` §8; `ADR-034` Part 2's forbidden inputs.
- **Any gate on the baseline earn-a-living path** — registration, driver creation, declaring availability / going online, receiving a Proposal, accepting or declining, stating a price, Assignment, arrival, trip start, trip completion, or keeping 100% of the stated price. `PIOS_PRODUCT_DECISIONS_NETWORK_RULES.md` Принцип 3; blueprint Section 5.
- **Any commission, per-ride fee, or revenue share.** `ADR-002`; `PRODUCT_DECISION_ENTREPRENEUR_MODEL.md` §2.4, §6.
- **Any subscription fact on a passenger-facing surface.** It would function as a trust or quality signal, which is an open Product Decision (blueprint Section 19, Decision 2).

**Why this is safe today, structurally rather than by promise:** `billing` consumes no event, publishes no event any module consumes, and exposes no endpoint any backend module calls. `docs/INTERFACE_CONTRACTS.md` is therefore **unchanged** — the same property `ADR-067` claimed for Core Slice 01. Combined with "no record ≡ `FREE`", **the subscription cannot gate anything today even by accident.**

### Part 5 — What remains an open Product Decision, named rather than answered

1. **What a paying driver actually gets** — blueprint Section 19, Decision 1. **Still open.** The strongest evidence-backed *candidate* (blueprint Section 12: Channel 1 discovery reach plus analytics depth) is a recommendation on the record, **not a ratified answer**, and this ADR does not adopt it. Note that any candidate touching discovery *reach* collides with `PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` §8 and needs that collision resolved explicitly, not assumed away.
2. **What happens when a subscription lapses** — blueprint Section 19, Decision 5, which that document itself says *"cannot be answered before Decision 1 exists."* **Still open.** This ADR records the `EXPIRED` state; it does not say what `EXPIRED` costs anyone.
3. **Price, currency, period length, trial length, whether a trial exists.** **Still open.**
4. **Whether driver-invites-driver (`ADR-073`) is ever tier-gated.** **Still open** — and deliberately kept answerable: `ADR-073` Part 5 clause 3 forbids attaching any subscription benefit to a referral without a Product Decision amending Принцип 3. The two designs share one mental model (a recorded fact that confers no entitlement) precisely so this question can be answered later without unwinding either.

---

## Alternatives Considered

- **Do nothing until Decision 1 exists.** Genuinely defensible, and closest to the blueprint's own Phase 1/Phase 2 sequencing. Rejected only because the *container* question (which context owns commercial state) is answerable without the *contents* question, and answering it early prevents subscription state from being added ad hoc to `driver-management` under deadline later. If the Product Owner disagrees, deferring costs nothing today — **nothing in this ADR is urgent.**
- **A `subscriptions` table inside `driver-management`.** Part 1. The proportionality argument is real and is the likeliest reason to overrule this ADR.
- **Widen the ratified `Payments` context.** Part 1. Legitimate, but must be stated in terms, not drifted into.
- **Model subscription in `core`.** Rejected — `ADR-067` Boundary.
- **Declare a `PaymentProvider` port now, implemented by a `NoOpPaymentProvider`.** Rejected — Part 3; speculative abstraction, and it would encode a guess about a provider's shape as architecture.
- **Store a `hasActiveSubscription: Boolean` on `Driver`.** Rejected twice over: it is commercial state in the ride-path module, and a boolean cannot express `TRIAL` vs `EXPIRED` vs never-subscribed, which is exactly the distinction any future product answer will need.
- **Emit a `SubscriptionActivated` event now, for future consumers.** Rejected — `CreateDriverApplicationService`'s own KDoc (lines 26–31) already rejected this pattern for `DriverRegistered`: *"introducing an unconsumed event is exactly the premature abstraction this project's engineering discipline rejects."*

---

## Consequences

**Positive**

- The first commercial capability in PIOS lands in a context that **cannot** influence dispatch, pricing, or the ride path, because it is not wired to them at all.
- `docs/INTERFACE_CONTRACTS.md` unchanged; no existing module, schema, event, endpoint, or screen is touched. `ADR-068`/`ADR-069`/`ADR-070`, Circle of Trust, Primary Driver, Assignment, Trip, and test/real isolation are all outside this ADR's reach.
- "Free today" is structural (absence ≡ `FREE`), so the baseline earn-a-living guarantee cannot regress by oversight.
- A real provider attaches as one adapter, with no domain or cross-module change.

**Negative**

- An eighth backend deployment unit, for a capability with no revenue attached and no ratified purpose. Mitigated by deferring scaffolding to the implementing Sprint (Part 1) — but if Decision 1 is never made, this is a placement decision that never becomes code, and that outcome should be considered acceptable rather than a failure.
- PIOS has no authorization layer (`ADR-054` Part 6, carried forward unresolved). An owner-gated write endpoint uses the `OwnerCredentialGate` pattern, which is a credential check, not an authorization model. Nothing here may be described as solving that.
- Recording a subscription period is a *money-adjacent* fact, a category PIOS has deliberately avoided. Even with no custody and no provider, this is the first time the platform records "this driver owes/paid PIOS." That is a genuine change in what PIOS is, and it should be ratified consciously, not slipped in as infrastructure.

---

## What This ADR Does Not Authorize

Any implementation before ratification and before H11 is registered; any price, tier, fee, commission, currency, billing period, trial, discount, or refund rule; any real payment provider integration or webhook; any payment custody; any gate on registration, driver creation, availability, proposals, price statement, assignment, arrival, trip start, trip completion, or the driver keeping 100% of the stated price; any subscription input to Dispatch, First Refusal, any Fallback tier, discovery matching, ordering, or eligibility; any passenger-facing exposure of subscription state; any change to `driver-management`, `identity`, `order-management`, `dispatch`, `passenger-experience`, or `core`; any change to `docs/INTERFACE_CONTRACTS.md`; any event published or consumed by `billing`; any read from, write to, or dependency on `network-management` (`ADR-064` Part 2); any widening of the ratified `Payments` context's responsibility (`ADR-018`); any claim that Section 19 Decision 1 or Decision 5 is answered; any scaffolding of `backend/billing` before the implementing Sprint.

## Blocking Questions for the Product Owner

- **Q1 — is a billing-adjacent capability ratified direction at all, now?** Section 21 of the blueprint sequences Decision 1 *before* anything downstream. This ADR argues the container is separable from the contents; that argument needs confirming, not assuming.
- **Q2 — new `billing` context, or a table in `driver-management`?** Part 1 recommends the former on structural grounds and records the proportionality argument for the latter. This is the single decision most worth overruling if you weigh it differently.
- **Q3 — or widen the ratified `Payments` context instead?** Requires this ADR to be rewritten to extend `ADR-018`'s Payments responsibility in terms.
- **Q4 (Evidence Analyst / Product Owner) — H11 must be registered in `docs/PIOS_PRODUCT_HYPOTHESES.md` before the Sprint**, per `PIOS_PRODUCT_EVIDENCE.md` lines 119–133. Note the gate's own requirement (line 132) that a hypothesis-based Sprint leave behind observable behaviour — **a subscription state that gates nothing and charges nobody may produce no observable behaviour at all**, which is a real argument that this work does not qualify as a hypothesis-based Sprint under that rule, and may need to wait for Decision 1 rather than lead it.
- **Q5 — is Section 19 Decision 1 being worked on separately?** Part 5 item 1 is this ADR's hardest dependency; it is not this ADR's to resolve.

**Non-blocking, for follow-up:** `docs/README.md`'s ADR table, a `docs/MODULE_STRUCTURE.md` Section 3 entry for Billing, and any `SYSTEM_ARCHITECTURE.md` context-count update are **not performed by this ADR**, and are conditioned on ratification — `ADR-070` Q7's precedent.

## Related ADRs

- [ADR-017: Bounded Context Strategy](ADR-017-Bounded-Context-Strategy.md) / [ADR-018: Core Architectural Components](ADR-018-Core-Architectural-Components.md) — the ratified eight contexts, including `Payments`; extended by Part 1, superseded by nothing.
- [ADR-067: PIOS Core Bounded Context — Slice 01](ADR-067-PIOS-Core-Bounded-Context-Slice-01.md) — the precedent for adding a context; its Boundary section is why `core` cannot own this.
- [ADR-056: Control Center AI Advisor](ADR-056-Control-Center-AI-Advisor.md) — the peripheral-component precedent whose failure never degrades the transactional core.
- [ADR-070: Channel 1 Discovery Matching](ADR-070-Channel-1-Discovery-Matching-and-Post-Ride-Relationship-Formation.md) — Part 5's own exclusion of any subscription mechanism; untouched by this ADR.
- [ADR-068](ADR-068-Relationship-Ordered-Fallback-Dispatch.md) / [ADR-069](ADR-069-Test-Real-Segregation-In-Fallback-Driver-Selection.md) — untouched; `ADR-069`'s `isTest` sourcing pattern reused in Part 2.
- [ADR-064](ADR-064-Referral-Visibility-No-Network-Management-Activation.md) — `network-management` prohibition; its carrying-cost lesson applied in Part 1.
- [ADR-005](ADR-005-Data-Ownership.md) / [ADR-019](ADR-019-Conceptual-Data-Ownership.md) / [ADR-037](ADR-037-Network-Management-Module-Bounded-Context-Extension.md) — reference-not-ownership, applied to `driverId` in Part 2.
- [ADR-055](ADR-055-Session-Authentication-and-Password-Credential.md) — the replicated session-token verifier Part 4's read endpoint would use.
- [ADR-002](ADR-002-Dispatch-Engine.md) — pricing, commission and regulatory rules out of architectural scope; the reason Part 5 names rather than answers.
- [ADR-073: Driver-to-Driver Referral](ADR-073-Driver-To-Driver-Referral-Single-Hop-Origin-Fact.md) — designed alongside this ADR; shares the "a recorded fact confers no entitlement" model that keeps Part 5 item 4 answerable later.

## References

Read for this ADR on 2026-09-15, **none modified**:

- `docs/PIOS_TAXI_COMMERCIAL_PRODUCT_BLUEPRINT.md` (Sections 5, 10, 12, 13, 15–17, 19, 20, 21)
- `docs/PIOS_TAXI_MONETIZATION_VALUE_MAP.md` (full)
- `docs/PRODUCT_DECISION_ENTREPRENEUR_MODEL.md` (§2.4, §2.5, §5, §6, §7.1, §7.2, Unresolved Decisions 1–2)
- `docs/PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` (§8, line 61; Explicit Preservations, line 88)
- `docs/PRODUCT_DECISION_PERSONAL_NETWORK_VALUE_DISTRIBUTION.md` (§2.3, §2.5)
- `docs/PIOS_PRODUCT_DECISIONS_NETWORK_RULES.md` (Принцип 3, Section 7)
- `docs/PIOS_PRODUCT_EVIDENCE.md` (E-001; gate lines 95, 119–133), `docs/PIOS_PRODUCT_HYPOTHESES.md` (H4 line 130; H9 highest, line 191)
- `docs/MODULE_STRUCTURE.md` (Payments Module, lines 76–81; §8 Extension Strategy, line 116)
- `docs/ADR/ADR-017` (line 13), `ADR-018` (line 19), `ADR-064`, `ADR-067` (Boundary, line 84), `ADR-070` (Part 5, line 173)
- `backend/settings.gradle.kts` (lines 1–19), `backend/driver-management/src/main/kotlin/com/pios/drivermanagement/api/OwnerCredentialGate.kt` (existence), `application/CreateDriverApplicationService.kt` (KDoc lines 26–31, the unconsumed-event precedent)
- Repository-wide search of `backend/` for `subscription|billing|Subscription|Billing|подписк` — one unrelated AMQP match, confirming zero commercial code

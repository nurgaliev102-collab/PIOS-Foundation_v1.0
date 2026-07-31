# ADR-042: Stated Ride Price — Minimal MVP Model and Ownership Boundary

## Status

Proposed — **one Product Owner input outstanding.**

This ADR follows [ADR-041](ADR-041-Order-Lifecycle-Synchronization-with-Assignment-Completion.md)'s own precedent of being written so that its structural decisions (items 2–7 below) stay correct regardless of how the outstanding business question is answered, while stating plainly that Decision item 1's *placement* does depend on it. The question is filed under "Open Questions (Product Decision Required — ADR-002)" and is **Open Question 1: who states the amount, and at what moment.** It is not answered here and must not be answered by an implementation task.

Amends no existing ADR. Adds a cross-reference note to ADR-034 confirming that ADR-034 Part 1 and Part 2 are **not** narrowed by this decision.

---

**Amendment 2026-07-31 — Product Owner decision recorded; placement revised.**

Open Question 1 ("who states the amount, and at what moment") has been
answered by the Product Owner, and answered with the **third** branch: the
driver states the amount when responding to a Proposal. That is the one
branch this ADR, as originally written, said it did **not** authorize
(Decision item 2, "Conditionality, stated plainly"; Open Question 1, third
bullet).

Consequently:

- Decision item **2** (placement on `Order`, `order-management`) is
  **superseded** by "Decision (Revised, 2026-07-31)" below. Its original
  text is preserved verbatim and carries an inline superseded marker
  (`CLAUDE.md`: "Never Delete Documentation").
- Decision item **3** ("Dispatch neither stores, receives, nor reads the
  price") is **superseded in its first clause only**. Dispatch now stores
  the value; it still neither reads it for any decision nor publishes it.
  Original text preserved with an inline marker.
- Decision items **1, 4, 5, 6, 7** stand **unchanged and in full force**.
  They were written to be placement-independent and they are: PIOS still
  calculates nothing (1), price still may never influence selection (4),
  Payments' ownership is still untouched (5), no event payload changes (6),
  and backward compatibility is still mandatory (7).
- `ADR-034` receives a second append-only Update block correcting the
  first one, which asserted that Dispatch would store nothing of this
  value. See ADR-034, "Update 2 (ADR-042 revised placement)".

Status of this ADR after the amendment: **Accepted for implementation**,
with Open Questions 2, 3 and 5 still open (none of them blocking — see
"Open Questions" below, which now carries their resolution state
explicitly).

---

**Amendment 2026-07-31 (round 2) — second Product Owner ruling; Open
Question 5 resolved; the downstream lifecycle of the stated amount
answered.**

The Product Owner has ruled on driver-side price input (in scope) and on
passenger visibility (not in scope), excluded all negotiation logic, and
asked the question this ADR had not actually answered: *what happens after
the driver states the price?* The full ruling, the resolution of Open
Question 5, the new Open Question 7 and its resolution, the traced answer
(R7), the passenger-visibility analysis (R8), and the revised Developer
Scope / Definition of Done / Risks are all in
**"Amendment 2026-07-31 (round 2)"** at the end of this document. Nothing
above is deleted; **Open Question 2 (currency) is *not* resolved by that
ruling** and remains open — see the note appended to it below.

Status of this ADR after the round-2 amendment: **Accepted for
implementation**, with Open Question 2 still open (non-blocking) and Open
Question 8 recorded as a limitation rather than a blocker.

---

## Context

Sprint 4's stated goal is to bring PIOS closer to a first commercial MVP without expanding the domain. The Product Owner's constraints for this sprint are explicit: no two-sided price negotiation, no new `Negotiation` aggregate, no Payments module, no change to bounded-context boundaries without genuine necessity, and a minimal solution compatible with future evolution.

### What exists in the repository today

**No monetary concept exists anywhere in PIOS code.** A case-insensitive search for `price|fare|cost|amount` across `backend/` returns exactly one match — the English word "costs" in a comment in `network-management/.../application/CreatePersonProfileApplicationService.kt` line 14 — and no monetary field, column, or payload. Concretely:

- `backend/order-management/src/main/kotlin/com/pios/ordermanagement/domain/Order.kt` lines 67–74: the aggregate carries `id`, `status`, `origin`, `destination`, `passengerName`, `createdAt`. Nothing else.
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/domain/Assignment.kt` lines 34–38 and `domain/Proposal.kt` lines 29–33: both carry `id`, `order`, `driver` and a status. Nothing else.
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/DispatchAssignmentApplicationService.kt` lines 183–235: every Dispatch outbox payload is exactly `{orderId, driverId}`.
- `backend/order-management/src/main/resources/db/migration/ordermanagement/` contains `V1`–`V7`; the last two (`V6__add_optional_order_destination.sql`, `V7__add_passenger_name_and_created_at.sql`) are the additive-optional-field precedent this ADR reuses. The next free version is `V8`.

**A precedent for additive, optional, unvalidated order context already exists and is documented in the code itself.** `Order.kt` lines 38–45, on `destination`: *"a plain, unvalidated `String?`, deliberately not wrapped in a value object the way [origin] is: [origin] enforces a ratified domain invariant ... while nothing in ratified documentation says anything about the shape or presence of a destination yet, so this class asserts nothing about it beyond carrying whatever the passenger typed, or nothing at all. Set once, at submission, and never changes for the rest of the order's lifecycle — neither [complete] nor [cancel] touches it."* `passengerName` (lines 55–65) follows the same shape. `api/SubmitOrderRequest.kt` line 28 keeps both optional with defaults, for the reason lines 10–15 record: an earlier mandatory `destination` broke the contract's one real caller, and under ADR-026's independent-deployability guarantee a caller cannot be assumed to upgrade in lockstep.

**The read path for a driver already exists, with no cross-module contract.** `frontend/src/pages/DriverHome/DriverHome.tsx` line 270 already calls `GET /v1/orders` on Order Management directly (`ORDER_MANAGEMENT_BASE_URL`), alongside its Dispatch calls — the direct-module-call precedent ADR-040's own Consequences ratified. Anything placed on `Order` and exposed through `api/OrderResponse.kt` (lines 30–37) is therefore visible to the driver's own screen without any new module-to-module dependency, and without Dispatch ever seeing it.

### The conflicting evidence that makes this an ADR rather than an additive field

A monetary amount is not analogous to `destination` in one decisive respect: `destination` had no other claimant in ratified documentation, and a price does.

- **`DOMAIN_MODEL.md` line 25** (Section 3, Core Domains): *"**Payments.** The platform's own record of payment-related information associated with transportation demand."* **Line 68** (Section 5, Entities): *"**Payment Record** (Payments) — the platform's own record of payment-related information associated with an order."* **Line 169** (Section 13, Relationships): *"A Payment Record relates to the Order it is associated with."*
- **`DOMAIN_MODEL.md` line 177** (Section 14, Boundaries): *"any pricing, commission, or regulatory rule, none of which is established by any approved document"* is explicitly excluded from the domain model.
- **`ADR-034` line 34** (Part 1, "Dispatch does NOT own"): *"Payment or pricing information (Payments; ADR-018, ADR-020 — Payments interacts with no other module per INTERFACE_CONTRACTS.md Section 3)."*
- **`ADR-034` line 58** (Part 2, "Forbidden inputs"): *"Payment or pricing information (Payments)."*
- **`MODULE_STRUCTURE.md` lines 76–81** ratifies a Payments module owning "Record Payment; Retrieve Payment Record" — and `PRODUCT_BASELINE_V2.md` line 164 records that Payments, along with Administration/Analytics/Notifications, is **not scaffolded**: *"Ratified as bounded contexts; none scaffolded ... Leave deferred; no urgency created by this baseline."*
- **`PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` Section 8** (line 61): *"payment to PIOS should not secretly purchase higher priority in fair shared-network opportunity allocation ... This constraint holds independent of which commercial model is eventually ratified."*
- **`PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md` line 33**: *"The order completes, and payment for the ride occurs directly between the requester and the fulfilling driver, outside PIOS."* Line 46: *"Direct payment between requester and fulfilling driver; no PIOS payment custody."* Line 52 excludes *"dynamic pricing; PIOS payment custody or a commission engine."*

So three distinct things are already ratified and must each be respected: (a) Payments owns payment-related information about an order, and may not be scaffolded this sprint; (b) no pricing rule may be invented at any level of documentation; (c) money may never influence dispatch selection. A price field is therefore not a purely additive change of the kind `destination` was, and cannot be introduced under that precedent alone.

### The two placements this sprint's constraints leave open

The Product Owner's constraints (no Negotiation aggregate, no Payments module) eliminate every option except two:

- **(i) On Dispatch's `Proposal`.** Requires narrowing `ADR-034` line 34's ownership exclusion by an explicit Update section, because a price stored on `Proposal` *is* pricing information owned by Dispatch.
- **(iii) On Order Management's `Order`.** Requires no amendment to any existing ADR: ADR-034 line 34 and line 58 remain literally true and are in fact reinforced, since Dispatch would still hold no price at all.

This ADR selects (iii), and Decision item 2 records why (iii) strictly dominates (i) under this sprint's own constraints — subject to Open Question 1.

## Problem

Where does a single, one-sided, non-negotiated ride price live for a first commercial MVP, given that Payments may not be scaffolded, no new aggregate may be created, no pricing rule may be invented, and money may never enter the dispatch decision?

## Decision

### 1. PIOS records a *stated* price. It calculates nothing.

The MVP records an amount that a human stated, outside PIOS. It does not compute, derive, validate, suggest, estimate, or settle it. No tariff, base fare, distance rate, time rate, surge factor, discount, commission, split, rounding rule, or currency conversion is introduced, implied, or reserved by this ADR. This is a direct application of ADR-002 and `DOMAIN_MODEL.md` line 177 — pricing rules are outside architectural scope, and this ADR does not become the place one is smuggled in.

Recording a stated amount is also not payment custody and creates no settlement path: payment continues to occur directly between requester and driver, outside PIOS, exactly as `PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md` lines 33 and 46 already ratify.

### 2. The stated price is an attribute of `Order`, in `order-management`. — SUPERSEDED 2026-07-31

> **Superseded by "Decision (Revised, 2026-07-31)", item R2.** The entire
> text of this item below is preserved unmodified for the record and is no
> longer in force. It was explicitly conditional on Open Question 1 being
> answered "the requester, at order submission"; the Product Owner answered
> "the driver, when responding to a Proposal" instead. The item's own
> "Conditionality, stated plainly" paragraph correctly predicted this
> outcome and correctly refused to authorize it — that refusal is now
> discharged by the Product Decision and by the revised Decision below, not
> overridden silently.

It is added as an **optional, immutable, submission-time field on the `Order` aggregate**, following `destination`'s own precedent (`Order.kt` lines 38–45) exactly: set once at submission, never changed by `complete()` or `cancel()`, absent (`null`) for every order that predates the change and for every caller that does not send one.

It is **not** placed on `Proposal`, **not** on `Assignment`, **not** in a new aggregate, and **not** in a Payments module.

Why `Order` dominates `Proposal` under this sprint's own constraints:

- **It changes no ratified boundary.** Constraint 4 of this sprint forbids changing bounded-context boundaries without genuine necessity. Placement (i) would require narrowing `ADR-034` line 34 — by definition a boundary change. Placement (iii) requires narrowing nothing; ADR-034 Parts 1 and 2 remain word-for-word true.
- **It makes the "no pay-to-win" constraint structural rather than procedural.** Under (iii), Dispatch's database has no price column, its projection carries no price, and no contract delivers one — so `PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` Section 8 and `ADR-034` line 58 are enforced by construction. Under (i), a price sitting on `Proposal` would be one field access away from any future Assignment Policy, and the prohibition would rest on discipline alone.
- **The read path already exists.** `DriverHome.tsx` line 270 already fetches `GET /v1/orders` directly. A driver sees the price with no new cross-module contract, no server-to-server call on any write path (ADR-027), and no change to Dispatch whatsoever.
- **It keeps the eventual Payments migration clean.** An amount recorded as originating order context is not a Payment Record and pre-empts nothing; a future ADR scaffolding Payments can supersede or derive from it. Under (i), Dispatch would first acquire a pricing responsibility and then have to give it back.

**Conditionality, stated plainly.** This item assumes the amount is known at or before order submission (stated by the requester, or recorded by the coordinator on their behalf). If **Open Question 1** is answered "the driver names the amount when responding to a Proposal," this item does **not** hold: that answer makes the price an attribute of the driver's response, which is Dispatch-owned, and would require narrowing `ADR-034` line 34 through a separate decision. **That branch is not authorized by this ADR** and may not be implemented under it.

### 3. Dispatch neither stores, receives, nor reads the price. — PARTIALLY SUPERSEDED 2026-07-31

> **Superseded in its first clause only, by "Decision (Revised,
> 2026-07-31)", items R3 and R4.** Dispatch **does** now store the value,
> on `Proposal`, because the Product Owner ruled that the driver states it
> when responding to a Proposal and a driver's response to a Proposal is,
> by ADR-035 Part 1, a Dispatch-owned fact. Everything else in this item
> survives intact and is restated as binding in R4: `Assignment` receives
> no price, Dispatch's availability projection receives no price, **no
> Dispatch outbox payload carries the price**, and no Assignment Policy
> (ADR-034 Part 3) may receive it as an input. `ADR-034` **Part 2** (line
> 58, forbidden inputs) therefore stands entirely unamended; `ADR-034`
> **Part 1** (line 34, ownership) is clarified rather than relaxed — see
> ADR-034's own "Update 2 (ADR-042 revised placement)" and R3 below for the
> full reasoning. A Dispatch migration and a Dispatch API change *are* now
> authorized, by the revised Decision, not by this item.

`ADR-034` Part 1 (line 34) and Part 2's forbidden-inputs list (line 58) stand **unamended and reinforced**. `Proposal`, `Assignment`, Dispatch's availability projection, Dispatch's outbox payloads, and any future Assignment Policy (ADR-034 Part 3) receive no price input. No Dispatch migration, no Dispatch API change, no Dispatch payload change is authorized by this ADR.

### 4. Price may never influence candidate selection, ordering, or priority.

A binding restatement, not a new rule: `PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` Section 8 already established that payment may never purchase priority, *"independent of which commercial model is eventually ratified."* This ADR extends that constraint explicitly to the field it introduces. Until now the constraint was abstract — no amount existed in the system. From this ADR forward a real amount exists, so the constraint acquires a concrete referent and must be enforced, not merely stated. Any future proposal to let price affect selection requires its own ADR and its own Product Decision; it may not be introduced as a refinement of this one.

### 5. Payments' ownership is unchanged, and Payments stays unscaffolded.

A stated ride price recorded as originating order context is **not** a Payment Record. Payments continues to own "the platform's own record of payment-related information associated with an order" (`DOMAIN_MODEL.md` lines 25, 68) and continues to be ratified-but-unscaffolded (`MODULE_STRUCTURE.md` lines 76–81; `PRODUCT_BASELINE_V2.md` line 164). No cross-module contract is created, and `INTERFACE_CONTRACTS.md` Section 3's set of justified module relationships is unchanged.

The distinction this ADR draws, so it can be checked later: a **Payment Record** asserts that money moved and is the platform's own account of it; a **stated price** asserts only what a human said the ride would cost, carried alongside the request the way `destination` is. If a future capability needs the former — settlement, reconciliation, dispute, commission, refund — that is Payments' scope and requires scaffolding Payments under its own ADR, including ADR-025's still-open database-technology gate.

### 6. No event contract changes.

`OrderSubmitted`, `OrderCompleted` and `OrderCancelled` payloads remain v1 `{orderId}`. This mirrors ADR-041's own Q4 ruling (*"`OrderCompleted`'s payload is not extended. It stays v1 `{orderId}`"*) and keeps this sprint clear of any event-versioning exercise under ADR-030. The price is exposed only through Order Management's own read model — `api/OrderResponse.kt` — which is a REST response, not a published contract to another module.

Consequently no consumer is affected: Order Management's two inbound subscriptions and Dispatch's five outbound events are untouched.

### 7. Backward compatibility is mandatory, not best-effort.

The field is optional on the wire and nullable in storage. `api/SubmitOrderRequest.kt` line 28's existing shape — every field after `passengerReference` optional with a default — is preserved, so `passenger-experience`'s `RestClientOrderSubmissionClient`, which sends only `passengerReference`, keeps working unmodified. This is not a stylistic preference: `SubmitOrderRequest.kt` lines 10–15 record that a mandatory field on this exact contract has already broken this exact caller once, and ADR-026 guarantees callers are deployed independently.

## Open Questions (Product Decision Required — ADR-002)

These are business rules. They are recorded unanswered rather than assumed. Question 1 blocks Decision item 2's placement; questions 2–4 do not block the decision but each changes exactly one identified place in the implementation.

1. **Who states the amount, and at what moment?** Three candidate answers, with materially different architectural consequences:
   - *The requester, at order submission* → Decision item 2 stands as written. Field on `Order`, no ADR amended.
   - *The coordinator, before or during dispatch* → Decision item 2 still stands (the coordinator records it against the Order), but Developer scope gains a way to set it after submission, which conflicts with the "set once, immutable" shape item 2 inherits from `destination`. This variant needs its own explicit ruling before implementation.
   - *The driver, when accepting or responding to a Proposal* → Decision item 2 does **not** apply. This makes the price part of the driver's own response, which Dispatch owns, and requires narrowing `ADR-034` line 34 by a separate ADR. **Not authorized here.**

   This ADR does not guess. Note that the Product Owner's own Sprint 4 constraints answered the two adjacent questions — no second-party confirmation and no counter-offer — but left this one open.

   **Resolved — Product Owner decision, 2026-07-31: the driver, when responding to a Proposal.** The third branch. The first branch ("the requester, at order submission") and the second ("the coordinator, before or during dispatch") are both closed and do not apply. Decision item 2 is superseded; see "Product Decision (Product Owner, 2026-07-31)" and "Decision (Revised, 2026-07-31)". The branch's own warning — that this "requires narrowing `ADR-034` line 34 by a separate ADR" — is discharged, and discharged more narrowly than predicted: what line 34 needs is a clarification of its referent, not a narrowing of its substance, recorded as an append-only Update block on ADR-034 rather than as a new ADR. R3 below sets out why, in full, and why the original prediction overstated the change.

2. **Is a currency recorded, and which one?** **Still open — not answered by the 2026-07-31 ruling.** The ruling names who states the amount and when; it says nothing about currency, units, formatting or rounding, and this ADR does not infer one. The revised Decision adopts branch (a) — a plain unvalidated string that asserts nothing — precisely *because* the question is unanswered: a bare string makes no claim about currency, so answering the question later requires no reversal of anything decided here. Branch (b) remains available to a future ADR once a currency and a formatting rule are ratified. No approved document names a currency anywhere. Recording a bare number asserts an implicit single currency, which is itself an unratified business assumption. Two honest options: (a) carry the amount as a plain unvalidated string exactly as `destination` is carried, asserting nothing (`Order.kt` lines 38–45's own reasoning applies verbatim); or (b) introduce a typed amount plus an explicit currency, which requires the Product Owner to ratify a currency and a formatting/rounding rule first. **Recommendation: (a)**, because it invents nothing and matches the existing precedent exactly; (b) is a strictly larger change that a later ADR can make once a rule exists to encode.

   **Still open after the round-2 ruling of 2026-07-31 — recorded
   explicitly because the amendment request asked for this question to be
   marked resolved and it must not be.** The round-2 ruling states that
   driver price input is in Sprint 4 scope, that the passenger does not see
   the amount, that negotiation is excluded, and that the value is stored as
   a stated fact. It says nothing about currency, units, precision,
   rounding, or formatting. Marking this question resolved on the strength
   of that ruling would be inventing a business rule the Product Owner did
   not state, which `CLAUDE.md` ("Never Invent Business Rules") and ADR-002
   both forbid. R5's plain-unvalidated-string representation continues to
   hold **because** the question is unanswered, and continues to require no
   reversal when it is eventually answered.

3. **Is the stated price binding on anything inside PIOS?** **Answered in substance by the 2026-07-31 ruling: no.** The Product Owner stated that the price "does not influence driver selection and is not payment information," and that PIOS "does not calculate it, does not recommend it, and does not participate in calculations." R4 below turns that into concrete, checkable prohibitions. The original text stands as written: The MVP answer must be "no — it is display-only context carried with the order." If anything in PIOS is meant to depend on it (gating completion, driver acceptance, dispute handling, reconciliation), that is a new business process and a new capability, requiring its own Product Decision and ADR — the same gate ADR-041's Product Decision section already placed in front of payment, ratings, disputes and passenger confirmation.

4. **Must a driver see the price before accepting a Proposal?** **Dissolved by the 2026-07-31 ruling, not answered.** The question presupposed that someone else had already stated the price before the driver responded. Under the ruling there is no price to see before acceptance: the driver *creates* it at the moment of acceptance. The question is therefore void rather than resolved, and it is replaced by Open Question 5 below (who, if anyone, sees it afterwards). Original text preserved: If yes, this is already satisfied with no additional work: `DriverHome.tsx` line 270 already reads `GET /v1/orders`, and Decision item 2 puts the price in that response. If the requirement is instead that the price must arrive *inside the Proposal payload*, that is the ADR-034 conflict again and falls under Open Question 1's third branch.

5. **Is the stated price shown to the passenger, and if so when?** **Open — added 2026-07-31, not answered by the ruling and not assumed here.** The ruling establishes who states the amount; it says nothing about who may read it. This matters and is deliberately left unanswered rather than guessed, because the two plausible answers have different consequences: if the passenger never sees it, the field is a driver-side record only; if the passenger sees it before or at acceptance, the amount starts to look like an offer the passenger responds to, which edges toward the bilateral negotiation Sprint 4 explicitly forbids. Note the incidental technical fact, recorded so nobody later mistakes reachability for a decision: `frontend/src/pages/RideRequest/RideRequest.tsx` line 159 already polls `GET /v1/proposals?orderId=...`, so under R2's placement the value would be *technically reachable* by the passenger screen the moment it is added to `ProposalResponse`. **Reachable is not the same as displayed.** The revised Developer scope therefore authorizes no passenger-side UI change whatsoever; rendering it on the passenger screen requires this question to be answered first.

   **Resolved — Product Owner decision, 2026-07-31 (round 2): no. The
   passenger does not see the amount and does not interact with it in
   Sprint 4.** *"Нет, пассажир в Sprint 4 стоимость не видит и не
   взаимодействует с ней."* The concern this question was raised to guard
   against — an amount the passenger responds to sliding into bilateral
   negotiation — is additionally closed by the same ruling's own sentence
   excluding all price-agreement, price-change and negotiation logic from
   the sprint. No passenger-side UI change is authorized, and
   `RideRequest.tsx` requires none: it reads only `.status` from each item
   of `GET /v1/proposals?orderId=` (lines 159–166), so an added response
   field is structurally ignored by it. What this ruling can and cannot
   mean, given that PIOS has no authorization layer at all, is analysed in
   **R8** below rather than assumed here.

6. **May a driver state an amount when *declining* a Proposal?** **Not open — derived as "no" from an existing constraint, not invented.** A price attached to a refusal is a counter-offer, and counter-offers plus bilateral price negotiation are excluded by this sprint's own standing constraints (restated by the Product Owner on 2026-07-31: "без Negotiation"). R2 therefore admits the amount on `accept` only. This is recorded as a derivation with its citation rather than as an architectural choice, so that a future reader can check the reasoning instead of having to trust it.

## Product Decision (Product Owner, 2026-07-31)

Authority: Product Owner. Scope: Open Question 1 above. This is a business
ruling, not an architectural one — it was made by the Product Owner, not by
the architecture role, in keeping with ADR-002 (pricing, commission,
matching and regulatory rules are outside architectural scope). It answers
who states the amount and when; it does **not** decide currency, display,
or anything else, and nothing beyond its own words is inferred from it.

The Product Owner's own formulation, recorded verbatim:

```
Product Decision изменён. Стоимость поездки определяет водитель при ответе
на Proposal. PIOS не рассчитывает стоимость, не рекомендует её и не
участвует в расчётах. Стоимость не влияет на выбор водителя и не является
платёжной информацией.

Просьба пересмотреть ADR-042 с учётом нового Product Decision и предложить
минимальное архитектурное решение, соответствующее нашим ограничениям: без
Negotiation, без Payments и без излишнего усложнения.
```

Working translation, for readers of this repository who do not read Russian
(the Russian text above is authoritative; this rendering is not):

> The Product Decision is changed. The driver determines the ride price when
> responding to a Proposal. PIOS does not calculate the price, does not
> recommend it, and does not participate in calculations. The price does not
> influence driver selection and is not payment information.
>
> Please revise ADR-042 in light of the new Product Decision and propose a
> minimal architectural solution consistent with our constraints: no
> Negotiation, no Payments, and no unnecessary complexity.

Four assertions are load-bearing and each is carried into the revised
Decision as a checkable constraint rather than a sentiment:

1. **Producer and moment:** the driver, at Proposal response. → R2.
2. **PIOS calculates, recommends, participates in nothing.** → R4, which
   states this as a list of specific prohibited behaviours, not as a
   principle.
3. **It does not influence driver selection.** → R4 and ADR-034 Part 2,
   which is *not* amended.
4. **It is not payment information.** → R3, which is the whole reason
   ADR-034 Part 1 is clarified rather than narrowed, and R6, which keeps
   Payments' ownership untouched.

Standing Sprint 4 constraints restated by the ruling and binding on the
revised Decision: no Negotiation (no bilateral price setting, no
counter-offer, no `Negotiation` aggregate), no Payments module, no
bounded-context boundary change without extreme necessity, minimal and
forward-compatible.

## Decision (Revised, 2026-07-31)

This section supersedes Decision items 2 and 3 above. Decision items 1, 4,
5, 6 and 7 above are **not** superseded and are restated here only where
the new placement makes them concrete.

### R1. Aggregate test re-applied: still no new aggregate.

ADR-035 Part 2's six-criteria test is re-run against the *new* question —
"is a driver-stated amount its own aggregate root?" — not the old one. The
answer is no, on every criterion, and the Product Owner's independent
exclusion of a `Negotiation` aggregate is therefore not merely obeyed but
independently corroborated:

1. **Invariant independence.** The amount has *no invariant at all*. R4
   forbids every rule that could constitute one — no minimum, no maximum,
   no comparison, no currency check, no arithmetic. A construct with zero
   invariants cannot justify a consistency boundary, since a consistency
   boundary exists to hold exactly one thing: something to be consistent
   about.
2. **Lifecycle precondition.** The amount has no lifecycle. It comes into
   existence at one moment and never changes (R5). Contrast the fact
   ADR-035 *did* promote to a root, which had a real `OPEN →
   ACCEPTED/DECLINED/LAPSED` progression (`ProposalStatus.kt`,
   `Proposal.kt` lines 42–78).
3. **Persistence granularity.** Nothing needs to query amounts
   independently of the Proposal they were stated on. Note the inversion:
   the only query need that *would* justify a root-level repository —
   retrieving amounts across proposals to compare or aggregate them — is
   precisely what R4 forbids. This criterion cannot be satisfied without
   violating the ruling that motivates the change.
4. **Actor separation.** This is the one criterion that superficially
   argues *for* separation: the amount is authored by the driver, not by
   Dispatch. But that separation is **already discharged by an existing
   aggregate**. `Proposal` exists, by ADR-035 Part 2's own actor-separation
   reasoning, specifically to hold facts driven by "the driver's own
   confirming act." The amount introduces no third actor, so it needs no
   third aggregate — it belongs to the one that already models this actor.
5. **Cardinality mismatch.** At most one amount per accepted Proposal, set
   once: a 1:0..1 scalar relationship. The exact opposite of the
   "independently growing collection whose count bears no relationship to
   its parent's own singular nature" that ADR-035 Part 2 required.
6. **Existing precedent within the same module.** The precedent points at
   an attribute, not a root: `Assignment.statusChangedAt`
   (`Assignment.kt` lines 42–49) is a nullable scalar added to an existing
   aggregate under ADR-040, set by transition methods and modelled as
   nothing of its own. That is this change's shape precedent exactly.

**Conclusion:** zero of six support a new aggregate; criterion 4 is
satisfied by `Proposal` already existing. The amount is an **attribute of
an existing aggregate**. No `Negotiation`, no `Price`, no `Quote`, no
`Offer` aggregate is created, and none is reserved.

### R2. The stated price is an attribute of `Proposal`, in `dispatch`.

It is added as an **optional, immutable, acceptance-time attribute of the
`Proposal` aggregate**: supplied by the driver on the accept action,
recorded once at that moment, never modified afterwards, and absent
(`null`) for every proposal that predates the change and every accept call
that does not supply one.

It is **not** placed on `Assignment`, **not** on `Order`, **not** in a new
aggregate, **not** in a Payments module, and **not** duplicated anywhere.

**Why `Proposal` and not `Assignment`** — the live alternative, since both
are Dispatch-owned and both are written inside the same ADR-036 shared
transaction, so mechanically either would work:

- **Provenance matches the aggregate.** The ruling ties the amount to the
  driver's *response*, and `Proposal.accept()` (`Proposal.kt` lines 42–48)
  is that response, in code. The amount is an attribute of the act, not of
  the act's consequence. `Assignment` is the consequence.
- **ADR-035 Part 2's actor-separation criterion points here.** It states
  that "`Assignment` is changed exclusively by Dispatch's own decision
  (ADR-002); the pre-commitment fact's own resolution is driven by the
  driver's own confirming act." A driver-authored value stored on
  `Assignment` would put driver-authored data inside the aggregate ADR-035
  characterises as Dispatch-decision-only. *Disclosed counterweight, so
  this is not overstated:* ADR-040 already lets a driver drive
  `Assignment`'s `arrive`/`start`/`complete` transitions, so that actor
  line is not pristine today. The distinction that survives is ADR-041's,
  not ADR-035's alone — see the next point.
- **ADR-041 item 4 drew a boundary that this respects.** Ride-progress
  state lives on `Assignment`, exclusively and by name (`ARRIVED`,
  `IN_PROGRESS`, `COMPLETED`). A stated amount is not ride progress.
  Loading `Assignment` with non-ride-progress attributes erodes the exact
  boundary ADR-041 spent a sprint establishing, for no gain.
- **It keeps the amount on the far side of the selection seam — the
  structural no-pay-to-win argument, preserved.** Placing it on
  `Assignment` would require widening `AssignOrderCommand` and
  `Assignment.create()` — the factory whose own KDoc states it "only
  records a decision already made elsewhere, given to it as [driver]"
  (`Assignment.kt` lines 126–129) — and would route a driver-supplied
  monetary value through the very creation path a future Assignment Policy
  (ADR-034 Part 3) also feeds. On `Proposal`, the amount is written by
  `accept()`, which by definition runs *after* selection has already
  happened. See R3's temporal argument.
- **Both existing read paths already reach it, with no new endpoint.**
  `frontend/src/pages/DriverHome/DriverHome.tsx` line 304 already polls
  `GET /v1/proposals?driverId=...`; `frontend/src/pages/RideRequest/RideRequest.tsx`
  line 159 already polls `GET /v1/proposals?orderId=...`. Placing it on
  `Assignment` would additionally require threading it through
  `/v1/assignments`. (Reachability is not authorization to display — Open
  Question 5.)
- **One fact, one home.** The amount is *not* copied onto `Assignment`
  when the Assignment is created. ADR-041 item 2's discipline — "two
  different facts owned by two different modules ... not one fact stored
  twice" — is applied here *within* a module: one fact, stored once, on the
  aggregate that received it.

**Why not `Order`, the previously chosen placement.** Under the new ruling
the amount does not exist at submission time, so `Order` cannot receive it
at submission. The only way to put it on `Order` would be for Dispatch to
call Order Management when the driver accepts — a server-to-server call on
a write path, forbidden by ADR-027 and by the reference-not-ownership rule
(ADR-005, ADR-019), and an inversion of ownership besides, since Dispatch
would be writing into an aggregate whose lifecycle ADR-034 Part 1 says it
does not own. The alternative — a new Dispatch → Order Management event
carrying the amount — would create a new cross-module contract and a new
event version for a value no other module needs. Both are strictly larger
changes than R2 and neither is justified.

### R3. The ADR-034 Part 1 question, resolved.

This is the exact conflict flagged as the top risk in the original ADR, and
it is answered here in full rather than by assertion.

**The clause.** `ADR-034` line 34, Part 1, "Dispatch does NOT own":
*"Payment or pricing information (Payments; ADR-018, ADR-020 — Payments
interacts with no other module per INTERFACE_CONTRACTS.md Section 3)."*

**What the original ADR-042 said about it, and where that was wrong.**
Context, "The two placements this sprint's constraints leave open," asserted
that placement on `Proposal` "requires narrowing `ADR-034` line 34 ...
because a price stored on `Proposal` *is* pricing information owned by
Dispatch." That sentence conflated **stored by** with **owned by**. The
correction is recorded here explicitly rather than quietly dropped, because
the earlier claim is what blocked this placement last round and a future
reader is entitled to see it withdrawn on the record.

**Ownership, as this repository defines it.** ADR-005, ADR-009 and ADR-019
do not define ownership as physical custody of bytes. A module owns
information when it decides that information's meaning, enforces its
invariants, and is the authority other modules must consult about it. The
repository already relies on this distinction working: `OrderReference.kt`
lines 3–10 state that Dispatch "does not own or import Order Management's
Order type; it holds only the identity value it needs ... Dispatch does not
verify that this order exists or is submitted — that remains Order
Management's own responsibility." Dispatch stores order identifiers in
`proposals.order_reference` and `assignments.order_reference` while ADR-034
Part 1 simultaneously and correctly says Dispatch does not own the Order.
Storage has never implied ownership here.

**Apply that test to the stated amount.** Under R2 + R4, Dispatch:

- **decides nothing about it** — no computation, derivation, default,
  suggestion, or estimate (R4.1);
- **enforces no invariant on it** — no range, no currency, no format, no
  relationship to anything (R4.2);
- **interprets it never** — it is not parsed as a number, compared,
  ordered, summed, or converted (R4.3);
- **is the authority for no other module about it** — no cross-module
  contract carries it, no event carries it, no module asks Dispatch for it
  (R4.5, R4.6).

Dispatch is therefore the **custodian of a record of an utterance**, not
the owner of a pricing capability. It holds what a driver said, verbatim
and uninterpreted, as an attribute of that driver's own recorded response.

**Is the amount "payment or pricing information" in the clause's own
sense?** No, and the clause's own parenthetical is what settles it. Every
entry in ADR-034 Part 1's list names the module that *does* own the thing
excluded — the Driver aggregate (Driver Management), the Order aggregate
(Order Management), Notification delivery (Notifications). Line 34's
parenthetical names Payments. So the clause's referent is
**Payments-owned** information, which `DOMAIN_MODEL.md` line 68 defines
precisely: *"Payment Record (Payments) — the platform's own record of
payment-related information associated with an order."* A driver-stated
amount is not that. No money has moved, PIOS holds no custody
(`PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md` lines 33, 46: payment occurs
"directly between the requester and the fulfilling driver, outside PIOS"),
and the Product Owner has now stated the point directly: *"не является
платёжной информацией"* — it is not payment information. Decision item 5
above already drew this line before it was needed, and it is unchanged: a
Payment Record asserts that money moved; a stated price asserts only what a
person said.

**Is it "pricing information" in the sense of pricing *semantics*?** Also
no — and this is the stronger half of the answer. What ADR-002 and
`DOMAIN_MODEL.md` line 177 exclude from PIOS is pricing *rules*: tariffs,
computation, commission, regulation. R4 forbids every one of them. Dispatch
acquires no pricing rule, no pricing behaviour, and no pricing capability.
It acquires a nullable text column whose content it is forbidden to
understand.

**Part 2 (forbidden inputs) is not touched, and is now structurally
stronger than before.** ADR-034 line 58 forbids pricing information as an
*input to the assignment decision*. Under R2 that prohibition is enforced
by **temporal ordering**, not by discipline: the amount does not exist
until the driver responds to a Proposal, and a Proposal is only ever made
to a driver who was *already selected*. A value that comes into existence
after the decision cannot be an input to it. Under the previously-chosen
placement (passenger states it at submission) the amount existed *before*
selection and the prohibition rested on nothing but an Assignment Policy's
future good manners. **On this specific axis, the new placement is a
fairness improvement over the old one, not a concession.**

**So: does ADR-034 need amending?** In substance, no — no ownership moves
and no forbidden input is admitted. In form, **yes**, for two reasons that
this project's own discipline makes non-optional. First, ADR-034 already
carries an "Update (ADR-042 — no narrowing)" block asserting that ADR-042
"places the stated price on Order Management's own `Order` aggregate
specifically so that Dispatch stores, receives and reads nothing of it."
That sentence is now **false**, and false ratified text is worse than no
text. Second, a reader in 2027 finding a `stated_price` column in
`pios_dispatch` while reading a clause that says "Dispatch does not own
pricing information" is owed the resolution in the document, not in a
reviewer's head (ADR-015). ADR-034 therefore receives a second, append-only
Update block — a **clarification of the clause's referent plus three
binding constraints**, not a narrowing of its substance. Both blocks are
preserved; the first is marked as corrected, not deleted.

### R4. What "PIOS does not calculate, recommend, or participate" means concretely.

Binding prohibitions. Each is checkable in review, and each maps to a
sentence of the ruling rather than to a preference:

1. **No production of a value.** PIOS never computes, derives, estimates,
   suggests, defaults, pre-fills, or recommends an amount. No tariff, base
   fare, distance rate, time rate, surge factor, discount, commission,
   split, or rounding rule is introduced, implied, or reserved. There is
   no server-side default: absent input means `null`, not zero.
2. **No validation beyond basic shape.** The only checks permitted are
   structural: if present, the value must be non-blank and within a stated
   maximum length. **Explicitly forbidden:** parsing it as a number, range
   or min/max checks, currency validation, decimal-place or format
   normalisation, and rejecting a value for being "too high" or "too low."
   PIOS has no basis for any of those and inventing one would be a pricing
   rule (ADR-002; `DOMAIN_MODEL.md` line 177).
3. **No comparison, ordering, or aggregation.** Amounts are never compared
   across proposals, orders, drivers, or time; never sorted or ranked;
   never summed, averaged, or otherwise aggregated. **This is the single
   most important prohibition in this ADR**, because comparison across
   proposals is exactly what would turn a set of stated amounts into an
   auction — and an auction is the `Negotiation` capability the Product
   Owner has twice excluded. See Risks.
4. **No currency semantics.** No currency is assumed, recorded, inferred,
   displayed as a symbol by the backend, or converted. The value is opaque
   text (R5). Open Question 2 remains open and this ADR does not close it
   by implication.
5. **No influence on selection, ordering, priority, or eligibility.** The
   amount is excluded from any Assignment Policy (ADR-034 Part 3), from any
   candidate filtering or ranking, and from any future Fair Opportunity
   Policy implementation. This restates `ADR-034` Part 2 line 58 and
   `PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` Section 8's standing
   constraint — *"payment to PIOS should not secretly purchase higher
   priority in fair shared-network opportunity allocation ... This
   constraint holds independent of which commercial model is eventually
   ratified"* — and, per R3, is now additionally enforced by the fact that
   the amount does not exist until after selection has occurred.
   `PROJECT_CONSTITUTION.md` Section 3's Fair Dispatch principle is
   unaffected and unweakened.
6. **No propagation.** The amount appears in **no** event payload, **no**
   outbox record, **no** cross-module contract, and **no** other module's
   database. Decision item 6 above stands: every event stays at v1. It does
   not travel to `Assignment`, to `Order`, or to `order-management`.
7. **No PIOS behaviour depends on it.** It gates nothing, blocks nothing,
   and is a precondition for nothing. A ride completes exactly as ADR-041
   ratified, with or without an amount recorded. Making anything conditional
   on it is a new business process requiring its own Product Decision and
   ADR (Open Question 3; ADR-041's own standing gate).
8. **No settlement.** Recording an amount creates no payment custody, no
   reconciliation, no dispute path, and no commission. Payment continues to
   occur directly between requester and driver, outside PIOS.

### R5. Shape, immutability and mechanics.

- **Type: a plain, unvalidated, nullable string.** Open Question 2, branch
  (a). This is a representation choice, not a business rule: a bare string
  asserts nothing about currency, units, precision or format, which is
  exactly the property required while Open Question 2 is unanswered. It
  reuses `Order.destination`'s own documented reasoning (`Order.kt` lines
  38–45) verbatim in spirit — carry whatever the human typed, or nothing at
  all. A typed money value with an explicit currency remains available to a
  future ADR once a currency and a formatting rule exist to encode.
- **Immutability.** Set exactly once, inside `Proposal.accept()`, at the
  moment of acceptance. No setter, no update path, no endpoint that
  modifies it afterwards. `decline()` and `lapse()` never set it
  (Open Question 6). This mirrors `destination`'s "set once at submission,
  never changes" shape, relocated to the moment the ruling names.
- **Not a constructor parameter.** It must be a private-set property on
  `Proposal`, following `Assignment.statusChangedAt`'s exact shape
  (`Assignment.kt` lines 42–49), **not** an added constructor argument.
  This is load-bearing, not stylistic:
  `PostgreSQLProposalRepository.reconstruct` (lines 110–125) restores a
  persisted Proposal by reflectively invoking
  `Proposal::class.java.getDeclaredConstructor(String, String, String)` and
  then replaying `accept()`/`decline()`/`lapse()`. Widening the constructor
  breaks that reflective lookup at runtime, silently, in a path no unit
  test that avoids the database will catch.
- **Reconstruction must replay the persisted value.** `reconstruct`'s
  `ProposalStatus.ACCEPTED` branch must pass the persisted amount into
  `accept(...)`, so a round-tripped Proposal carries what was stored rather
  than losing it or acquiring the current request's value. This is the same
  problem ADR-040 item 3 already solved for `Assignment` by making the
  transition timestamp an overridable parameter — see
  `PostgreSQLAssignmentRepository`'s own KDoc and `Assignment.accept(at)`
  (`Assignment.kt` lines 56–64). Reuse that pattern; do not invent another.
- **Backward compatibility is mandatory, not best-effort** (Decision item 7
  above, unchanged, restated for the new surface). `POST
  /v1/proposals/{proposalId}/accept` currently takes **no request body**
  (`ProposalController.kt` lines 102–114). Any body added must be optional,
  so the existing caller — `DriverHome.tsx` line 431, which posts with no
  body — keeps working unmodified. A missing body, a present body with the
  field absent, and a present body with the field null are all valid and
  all mean "no amount stated." Under ADR-026 callers deploy independently;
  `SubmitOrderRequest.kt` lines 10–15 record that this exact class of
  mistake has already broken this exact kind of caller once.
- **Migration.** One additive, nullable column on Dispatch's own
  `proposals` table. Rows written before this change satisfy it without a
  default, exactly as `V5__assignment_ride_lifecycle.sql`'s own comment
  already reasons for `status_changed_at`.

### R6. Payments, Negotiation, and boundaries: all unchanged.

- **Payments** continues to own payment-related information about an order
  (`DOMAIN_MODEL.md` lines 25, 68, 169) and continues ratified-but-
  unscaffolded (`MODULE_STRUCTURE.md` lines 76–81; `PRODUCT_BASELINE_V2.md`
  line 164). Decision item 5 above stands verbatim, including its
  Payment-Record-versus-stated-price distinction; only the field's location
  changed, not its nature. When Payments is eventually scaffolded, nothing
  recorded under this ADR is a Payment Record, and a future ADR may
  supersede, mirror or derive from it.
- **No `Negotiation` aggregate**, no counter-offer, no bilateral price
  setting, no second-party confirmation of an amount (R1, Open Question 6).
- **No bounded-context boundary changes.** All six contexts keep their
  current ownership. `INTERFACE_CONTRACTS.md` Section 3's four justified
  cross-module relationships are unchanged; no new contract is created.
  `network-management` and `identity` are untouched and remain isolated.
- **No new module, no new aggregate, no new event, no new event version,
  no new cross-module call.**

## Alternatives Considered

- **A new `Negotiation` or `Price` aggregate in Dispatch.** Rejected — explicitly forbidden by this sprint's constraint 2, and unjustified besides: ADR-035 Part 2's six-part test for a separate aggregate root (independent invariant, lifecycle precondition, persistence granularity, actor separation, cardinality mismatch, existing precedent) is satisfied by none of them for a single stated amount with no invariant, no lifecycle and no second actor.
- **Scaffolding the Payments module.** Rejected — forbidden by this sprint's constraint 3, and independently blocked: `MODULE_STRUCTURE.md` line 74 records ADR-025's database-technology gate as the same kind of open prerequisite it is for Notifications, and `PRODUCT_BASELINE_V2.md` line 164 leaves all four unscaffolded modules deliberately deferred.
- **A field on `Proposal`, with an Update section narrowing ADR-034 Part 1.** Rejected in favour of Decision item 2, for the four reasons given there — chiefly that it changes a ratified ownership boundary to obtain a capability that placement on `Order` provides without changing one, and that it weakens the no-pay-to-win guarantee from structural to procedural. Recorded as the required path *only* under Open Question 1's third branch, and then as a separate ADR, not as an amendment slipped into this one.
- **Treating the price as a purely additive field needing no ADR** (the `destination`/Sprint 3B precedent applied directly). Rejected: `destination` had no competing claimant, whereas `DOMAIN_MODEL.md` lines 25, 68 and 169 already attribute payment-related order information to Payments. Placing it elsewhere is an ownership judgement, and ownership judgements are exactly what an ADR exists to record.
- **Deriving the price from anything PIOS knows** (distance, time, destination). Rejected outright: that is a pricing rule, excluded by ADR-002 and `DOMAIN_MODEL.md` line 177, and PIOS holds no location, distance or duration concept at all — `EVENT_CATALOG.md` Section 7 states *"No location, tracking, or positioning data is described or implied."*

## Consequences

### Positive

- PIOS can display what a ride costs for the first time, with no new aggregate, no new module, no new event, no new cross-module contract, and no change to Dispatch.
- `ADR-034`, `ADR-035`, `ADR-036`, `ADR-037`, `ADR-040` and `ADR-041` are all left unmodified; ADR-034's two pricing clauses are strengthened rather than weakened.
- The "payment never buys priority" constraint moves from abstract principle to structurally enforced fact, at the exact moment a real amount first exists.
- Backward compatibility is preserved by construction; every existing caller, order row, event payload and consumer is unaffected.
- The eventual Payments module inherits a clean boundary: nothing recorded under this ADR is a Payment Record, so nothing has to be un-decided later.

### Negative

- Order Management carries a monetary-looking attribute while `DOMAIN_MODEL.md` line 25 attributes payment-related order information to Payments. The distinction drawn in Decision item 5 is a real judgement, not a self-evident fact, and a future reader may reasonably challenge it — which is why it is recorded explicitly here rather than left implicit in a field name.
- If Open Question 2 is answered with the recommended plain unvalidated string, PIOS holds an amount it cannot sum, compare or validate. That is deliberate — it asserts nothing — but it is a real limitation, and converting it later to a typed amount is a migration plus a contract change, not a free move.
- The price is not visible to Dispatch by design, so any future feature that genuinely needs Dispatch to know it (not to *select* by it) will require a new contract and a new decision.
- One more optional, nullable column joins `destination`, `passengerName` and `createdAt` on `Order`. The accumulation of optional pilot-context fields on this aggregate is becoming a pattern worth a deliberate review at some point; this ADR notes it rather than addressing it.

**Amendment 2026-07-31 — consequences of the revised placement.** The
bullets above were written for the superseded `Order` placement. Those that
no longer hold are corrected here rather than edited in place.

### Corrected

- "no change to Dispatch" and "`ADR-034` ... left unmodified" are **no
  longer accurate**. Dispatch gains one nullable column, one optional
  request field, one response field, and one migration. ADR-034 gains a
  second append-only Update block clarifying Part 1's referent (R3). Its
  Part 2 forbidden-inputs list is genuinely unmodified.
- "`ADR-035` ... unmodified" **still holds**, and its Part 2 test was
  re-run rather than assumed (R1). ADR-036, ADR-037, ADR-040 and ADR-041
  remain unmodified.
- "Order Management carries a monetary-looking attribute" — **no longer
  applies.** `order-management` is entirely untouched by the revised
  decision, and the accumulation-of-optional-fields concern noted for
  `Order` does not materialise. It transfers, in smaller form, to
  `Proposal`, which gains its first non-structural attribute.

### Still true, and in some cases stronger

- No new aggregate, no new module, no new event, no new event version, no
  new cross-module contract, no boundary change.
- Backward compatibility by construction: every existing caller, row,
  payload and consumer is unaffected. The accept endpoint's body is
  optional; the existing bodyless caller keeps working.
- The eventual Payments module inherits a clean boundary.
- **The no-pay-to-win guarantee is now structural in a stronger sense than
  before.** Under the superseded placement, the amount existed before
  selection and was kept away from the decision by ownership. Under this
  placement it does not exist until after selection has already happened,
  so it *cannot* be an input to the decision that produced the Proposal it
  is attached to (R3).

### New

- Dispatch's database now contains a monetary-looking column for the first
  time. R3 records exactly why this is not an ownership change, and ADR-034's
  Update 2 records the same reasoning where a reader of ADR-034 will find
  it. This judgement is stated explicitly so it can be challenged, not left
  implicit in a column name.
- A stated amount is now attached to a `Proposal`, and ADR-035 Part 3 left
  multi-candidate ("many offered, one accepts") semantics deliberately open.
  Several priced proposals per order is structurally the shape of an
  auction. R4.3 forbids the comparison that would make it one; this is the
  principal residual risk and is named as such rather than buried.
- Which amount "applies" to a ride is currently unambiguous only because at
  most one Proposal per order is ever `ACCEPTED` (`Assignment.create`'s own
  one-assignment-per-order invariant, `Assignment.kt` lines 136–138). If
  multi-candidate semantics ever arrive, that answer stops being
  self-evident — one more reason multi-candidate needs its own ADR.

## Evolution Path

None of the following is resolved here:

1. **A real pricing capability** — estimation, tariffs, commission, splits. Requires a Product Decision first; the commercial model itself remains open (`PROJECT_CONSTITUTION.md` Section 19; `PRODUCT_BASELINE_V2.md` line 112).
2. **Scaffolding Payments**, and deciding whether the field this ADR places on `Order` is then superseded, mirrored or derived. Requires its own ADR, including ADR-025's open database-technology gate.
3. **A typed money representation with an explicit currency** (Open Question 2, branch b).
4. **Any dependency of PIOS behaviour on the stated amount** (Open Question 3).

Any change to Decision items 2, 3, 4 or 5 requires a decision that explicitly supersedes this one, consistent with ADR-015.

## What This ADR Does Not Authorize

Payment custody, settlement, commission, refunds, disputes, price estimation, dynamic pricing, price negotiation or counter-offers, a `Negotiation` aggregate, a Payments module, any price-derived dispatch behaviour, and any change to Dispatch. Each is excluded by an already-ratified document cited in Context, by this sprint's own constraints, or both. None may be introduced as an implementation detail of this ADR.

> **Amended 2026-07-31.** Every item above still stands **except the last**.
> "Any change to Dispatch" is replaced by: *any change to Dispatch beyond
> the one nullable `Proposal` attribute, its migration, its optional accept-
> request field and its response field, exactly as specified in R2 and R5.*
> In particular the following remain unauthorised and may not be introduced
> as implementation details: any change to `Assignment`, `AssignmentStatus`,
> `Assignment.create`, `AssignOrderCommand`, the availability projection,
> any outbox record or event payload, any Assignment Policy, any endpoint
> other than the existing accept endpoint, and any change to
> `order-management`, `passenger-experience`, `driver-management`,
> `network-management` or `identity`. Additionally unauthorised, and named
> because the new placement makes them newly tempting: comparing or ranking
> stated amounts across proposals (R4.3), and displaying the amount on the
> passenger's screen (Open Question 5, unanswered).

## Related ADRs

- [ADR-002: Dispatch Engine](ADR-002-Dispatch-Engine.md) — the standing exclusion of pricing rules from architectural scope; unmodified and directly applied by Decision item 1.
- [ADR-005](ADR-005-Data-Ownership.md) / [ADR-009](ADR-009-Domain-Isolation.md) / [ADR-019](ADR-019-Conceptual-Data-Ownership.md) — the ownership rules Decision items 3 and 5 apply; unmodified.
- [ADR-026: Deployment Strategy](ADR-026-Deployment-Strategy.md) — the independent-deployability guarantee Decision item 7 protects.
- [ADR-030: Event Schema Versioning Strategy](ADR-030-Event-Schema-Versioning-Strategy.md) — not exercised; Decision item 6 keeps every payload at v1.
- [ADR-034: Assignment Policy and Dispatch Decision Architecture](ADR-034-Assignment-Policy-and-Dispatch-Decision-Architecture.md) — Part 1 (line 34) and Part 2 (line 58) are **not** narrowed; Decision items 3 and 4 reinforce them. A cross-reference note is added to ADR-034 recording this.
  > **Amended 2026-07-31.** Part 2 (line 58) is still not narrowed, and is now enforced by temporal ordering rather than by ownership alone. Part 1 (line 34) has its **referent clarified**, not its substance narrowed: Dispatch stores a driver-stated amount as an opaque attribute of a Proposal without owning any pricing capability or any Payments-owned information. Full reasoning in R3; recorded on ADR-034 itself as "Update 2 (ADR-042 revised placement)", which also corrects the now-false first Update block.
- [ADR-035: Pre-Commitment Business Fact — Aggregate Boundary](ADR-035-Pre-Commitment-Business-Fact-Aggregate-Boundary.md) — its Part 2 aggregate-boundary test is applied in Alternatives to reject a new aggregate; unmodified.
  > **Amended 2026-07-31.** ADR-035 remains unmodified, and is now load-bearing in two further ways: its Part 2 six-criteria test is re-run against the revised question in R1 (result: still no new aggregate, zero of six criteria met), and its actor-separation criterion is one of the reasons R2 places the attribute on `Proposal` rather than on `Assignment`. Part 3's still-open multi-candidate semantics are the source of this ADR's principal residual risk.
- [ADR-036: Proposal↔Assignment Shared Transaction](ADR-036-Proposal-Assignment-Shared-Transaction.md) — untouched; nothing enters the shared transaction, and its Operational Consequences (no external I/O, no long-held locks) are unaffected.
- [ADR-037: Network Management Module — Bounded Context Extension](ADR-037-Network-Management-Module-Bounded-Context-Extension.md) — untouched; `network-management` remains isolated in both directions.
- [ADR-040: Assignment Ride Lifecycle](ADR-040-Assignment-Ride-Lifecycle.md) / [ADR-041: Order Lifecycle Synchronization](ADR-041-Order-Lifecycle-Synchronization-with-Assignment-Completion.md) — untouched; `OrderStatus` remains `SUBMITTED / COMPLETED / CANCELLED` and ride progress remains on `Assignment`. ADR-041's own gate ("anything that would make driver completion insufficient ... is now formally gated behind a new ADR") is respected: Decision item 3's Open Question keeps the price display-only and non-blocking.

## References

- [DOMAIN_MODEL.md](../DOMAIN_MODEL.md) lines 25, 68, 169, 177 (Sections 3, 5, 13, 14)
- [MODULE_STRUCTURE.md](../MODULE_STRUCTURE.md) lines 74, 76–81 (Section 3: Notifications, Payments), Section 8 (Extension Strategy)
- [INTERFACE_CONTRACTS.md](../INTERFACE_CONTRACTS.md) Sections 3, 4, 5
- [EVENT_CATALOG.md](../EVENT_CATALOG.md) Sections 5, 6, 7, 9
- [PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md](../PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md) Section 8, Explicit Preservations ("No pay-to-win priority")
- [PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md](../PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md) lines 33, 46, 52
- [PRODUCT_BASELINE_V2.md](../PRODUCT_BASELINE_V2.md) lines 112, 118, 130, 164
- [PROJECT_CONSTITUTION.md](../PROJECT_CONSTITUTION.md) Section 7 (ADR Policy), Section 19 (commercial model open)
- `backend/order-management/src/main/kotlin/com/pios/ordermanagement/domain/Order.kt` lines 38–45, 55–65, 67–74, 85–105 — read, not modified.
- `backend/order-management/src/main/kotlin/com/pios/ordermanagement/api/SubmitOrderRequest.kt` lines 10–15, 28; `api/OrderResponse.kt` lines 30–37 — read, not modified.
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/domain/Assignment.kt` lines 34–38; `domain/Proposal.kt` lines 29–33; `application/DispatchAssignmentApplicationService.kt` lines 183–235 — read, not modified.
- `backend/order-management/src/main/resources/db/migration/ordermanagement/V6__add_optional_order_destination.sql`, `V7__add_passenger_name_and_created_at.sql` — read, not modified.
- `frontend/src/pages/DriverHome/DriverHome.tsx` line 270 — read, not modified.

**Amendment 2026-07-31 — additional evidence read for the revised Decision.** All files below were read at the time of the revision and are cited above by line; none was modified by this ADR, which is a documentation artefact only. The "read, not modified" annotations above describe the act of writing this ADR, not the implementation it authorises — under the revised Decision the `dispatch` files marked with (*) are the ones the Developer task is expected to change.

- `backend/dispatch/src/main/kotlin/com/pios/dispatch/domain/Proposal.kt` lines 5–28 (KDoc, ownership and invariant), 29–35 (constructor and status), 42–48 (`accept`), 56–62 (`decline`), 72–78 (`lapse`), 80–108 (`propose`) (*)
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/domain/Assignment.kt` lines 42–49 (`statusChangedAt`, the shape precedent), 56–71 (`accept(at)`, the reconstruction-parameter precedent), 119–147 (`create`) — read, and deliberately **not** to be modified
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/ProposalApplicationService.kt` lines 30–61 (why no outbox write exists for Proposal events), 99–120 (`acceptProposal` / `acceptProposalWithinCallerTransaction`) (*)
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/ProposalAssignmentOrchestrationService.kt` lines 66–99 (ADR-036 shared transaction), 121–132 (`acceptProposal`) (*)
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/api/ProposalController.kt` lines 102–114 (`acceptProposal`, currently bodyless), 187–188 (`toResponse`) (*)
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/api/ProposalResponse.kt` lines 9–14 (*)
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/persistence/PostgreSQLProposalRepository.kt` lines 25–46 (reconstruction KDoc), 52–64 (`save`), 110–125 (`reconstruct`, the reflective constructor lookup) (*)
- `backend/dispatch/src/main/resources/db/migration/dispatch/V4__proposals.sql` (the `proposals` table), `V5__assignment_ride_lifecycle.sql` (the additive-nullable-column precedent) — next free version is `V6` (*)
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/domain/OrderReference.kt` lines 3–10 — the storage-is-not-ownership evidence R3 relies on
- `frontend/src/pages/DriverHome/DriverHome.tsx` lines 304, 425–433 (bodyless accept POST); `frontend/src/pages/RideRequest/RideRequest.tsx` lines 159, 173–178 — read, not modified by this ADR

---

# Amendment 2026-07-31 (round 2)

Append-only. Nothing above is deleted or edited in substance; the only
in-place additions are the two Resolved/Still-open markers on Open
Questions 5 and 2, and the status pointer at the top.

## Product Decision (Product Owner, 2026-07-31, round 2)

Authority: Product Owner. Scope: driver-side price-input UI in Sprint 4;
passenger visibility of the amount; exclusion of all negotiation logic; the
status of the stored value. Business ruling, not an architectural one
(ADR-002). Recorded verbatim:

```
Да, ввод стоимости водителем входит в Sprint 4.
Нет, пассажир в Sprint 4 стоимость не видит и не взаимодействует с ней.
Любая логика согласования стоимости, изменения стоимости или переговоров
исключается из Sprint 4.
Цена хранится как факт, указанный водителем, и пока используется только как
данные системы.
Но я бы добавил ещё один вопрос, который сейчас кажется важнее: Architect
рассматривает цену как "данные системы". А я бы спросил: Что происходит
после того, как водитель указал цену?
```

Working translation (the Russian above is authoritative; this rendering is
not):

> Yes, driver input of the price is in Sprint 4 scope.
> No, in Sprint 4 the passenger does not see the price and does not interact
> with it.
> Any price-agreement, price-change or negotiation logic is excluded from
> Sprint 4.
> The price is stored as a fact stated by the driver, and for now is used
> only as system data.
> But I would add one more question, which right now seems more important:
> the Architect treats the price as "system data". I would ask: **what
> happens after the driver has stated the price?**

### What this ruling settles

1. **Driver price-input UI is in scope.** → the write side is reachable in
   the running product. This retires the "unreachable field" half of the
   original Risk 3. Developer Scope below gains the frontend files.
2. **The passenger neither sees nor interacts with it.** → Open Question 5
   resolved, negatively. No passenger-side UI change. See R8 for the limit
   of what this can be enforced to mean in Sprint 4.
3. **All negotiation, price-agreement and price-change logic is excluded.**
   → R4.3 (no comparison/ordering/aggregation), R5 (set once, no update
   path), R6 (no `Negotiation` aggregate) and Open Question 6 (no amount on
   decline) are each independently reconfirmed by the Product Owner rather
   than resting on the architecture role's own derivation. No endpoint that
   modifies a stated amount may be built.
4. **The value is a stated fact, "for now used only as system data."** → the
   Product Owner immediately challenges this phrase themselves. R7 answers
   the challenge.

### What this ruling does *not* settle

- **Open Question 2 (currency, units, formatting) is untouched and remains
  open.** The ruling says nothing about it. See the note appended to Open
  Question 2 above; it is not closed by implication here.
- **Nothing about ride history, receipts, or post-completion display.** No
  such surface exists in the product today (evidence in R7.4) and none is
  authorized by this ruling. R7 answers the PO's question strictly within
  what already exists.
- **Nothing about who else inside PIOS may read the value** beyond the
  passenger exclusion. R7.5 and R8 record what is and is not consequently
  true, without inventing a rule.

## Open Question 7 — was the driver-side price-input UI in Sprint 4 scope?

Recorded here as a numbered question for the record; it was raised in the
Sprint 4 architecture review, not written into the original Open Questions
list. **Resolved — Product Owner, 2026-07-31 (round 2): yes.** *"Да, ввод
стоимости водителем входит в Sprint 4."* The consequence is that
`DriverHome.tsx` — the only screen that calls the accept endpoint
(line 431) — gains a price input on the `OPEN` proposal card, and the
optional accept-request body specified in R5 acquires a real caller in the
same sprint that introduces it.

---

## R7. What happens after the driver states the price

This is the Product Owner's own question and it is answered by tracing the
value, not by restating its category. "System data" was a classification,
not an answer, and the Product Owner is right that it was not one.

### R7.1 The trace, step by step, from the persisted write

Every step below is a call path that **already exists in the repository
today** — none of it is proposed, and none of it requires a new endpoint.

1. **Write.** `ProposalController.acceptProposal` (lines 102–114) →
   `ProposalAssignmentOrchestrationService.acceptProposal` (lines 121–132)
   → `ProposalApplicationService.acceptProposalWithinCallerTransaction`
   (lines 113–120) → `proposal.accept()` → `proposalRepository.save(proposal)`,
   all inside the one ADR-036 shared transaction. The amount lands in
   `proposals.stated_price` in `pios_dispatch`.
2. **Immediate synchronous read-back, in the same HTTP response.**
   `ProposalController.acceptProposal` line 107 returns
   `outcome.proposal.toResponse()` — the accept endpoint already responds
   with the full `ProposalResponse`. `DriverHome.tsx` lines 431–435 already
   consumes that response body and writes it straight into the `proposals`
   state array. **The driver who typed the amount gets it back in the
   response to the very request that stored it, today, with no new code
   path.**
3. **Continuous re-read, every 3 seconds, for as long as the ride is
   active.** `DriverHome.tsx` line 304 polls
   `GET /v1/proposals?driverId=…` on a `PROPOSALS_POLL_INTERVAL_MS = 3000`
   interval (lines 25, 252–267). That request is served by
   `ProposalController.listProposals`'s `driverId` branch (line 180), which
   maps through the **same** `toResponse()` (lines 187–188). So the stated
   amount is re-delivered to the driver's own screen on every poll, from
   persistent storage, for the whole life of the proposal row on that
   screen. This is a genuine round trip through the database, not an echo
   of client state: after a page reload or a device restart, the value comes
   back from `pios_dispatch`.
4. **Render.** The proposal card already renders order facts fetched from a
   second module in exactly this position — `DriverHome.tsx` lines 675–677
   render `passengerName`, `destination` and the order's creation time
   beneath the status line. The stated amount is one more line in that
   block, gated on `proposal.status === 'ACCEPTED'`.
5. **End of visibility.** `DriverHome.tsx` line 591:
   `const visibleProposals = proposals.filter((p) => assignments[p.orderId]?.status !== 'COMPLETED')`,
   with the code's own comment at lines 587–590: *"a proposal whose ride has
   completed stops being active … No history screen exists yet to move it
   to; it simply stops appearing here."* At ride completion the card
   disappears and the stated amount stops being displayed anywhere.
6. **After that: nothing.** The row and its `stated_price` persist in
   `pios_dispatch` indefinitely and no consumer reads them. Confirmed
   negatively: R4.6 forbids the value in any event payload or outbox record;
   `DispatchAssignmentApplicationService`'s payloads remain `{orderId,
   driverId}`; no other module queries Dispatch's proposals; no history,
   receipt, earnings or reporting surface exists in `frontend/src/pages/`
   (the complete set is `Coordinator`, `NetworkTest`, `PassengerLanding`,
   `NotFound`, `RideRequest`, `DriverHome`).

### R7.2 The answer

**Not (i) and not an unqualified (ii). The accurate answer is (ii), bounded
by the ride:**

> After the driver states the price, it is persisted on the accepted
> Proposal and immediately becomes readable **by the driver who stated it,
> and only by them**, through two read paths that already exist and need no
> new endpoint: the accept response itself, and the driver's own 3-second
> proposals poll. It stays readable on that driver's own order card for the
> whole active ride. When the ride completes, the card leaves the screen and
> the value becomes inert stored data with no consumer — permanently, until
> a future sprint defines one. It never leaves Dispatch, never enters an
> event, never reaches Order Management, never reaches the passenger, and
> never influences any PIOS behaviour.

That is a closed loop for the interval the sprint is actually about, and an
honest dead end afterwards. Both halves are stated because only the first
half would be flattering.

### R7.3 Why this is not a write-only field, and what it would take to make it one

The original Risk 3 ("the field may be unreachable in the running product")
is now retired on **both** sides, but the read side is retired *only if the
render in R7.1 step 4 is actually built*:

- If Sprint 4 ships the input, the request field, the column, the repository
  mapping and the response field but **not** the driver-side render, then the
  driver types a number into a form and it vanishes from their own view. The
  sprint would then be shipping exactly the write-only artefact the Product
  Owner's question is probing for, and the field's only reader would be
  whoever opens `psql`.
- The distance between "closed loop" and "dead field" is therefore one
  conditional line in `DriverHome.tsx`. That asymmetry — near-zero cost,
  decisive effect — is why the render is **not** optional in the revised
  Developer Scope, and why it is a Definition-of-Done item rather than a
  nice-to-have.

**On the trade-off the Product Owner is entitled to see** (raised because
the alternative was explicitly asked for, not because it is recommended): if
the render were *not* built, the correct response would be to drop
persistence too — ship the input as a form that writes nothing, or drop the
field from the sprint entirely — rather than create a column, a migration, a
contract field and a reconstruction path for a value no one can ever see. A
nullable column plus a schema migration is not free: it is a permanent
addition to Dispatch's schema and to `ProposalResponse`'s public shape, and
under ADR-026 a shipped response field cannot simply be withdrawn from
callers later. **Recommendation: build the render and keep persistence.**
The narrowing alternative is recorded so the Product Owner can choose it,
not because the architecture role considers it better.

### R7.4 The value goes dark at ride completion, and that is a pre-existing gap, not one this ADR creates

Recorded plainly rather than glossed. Every order fact the driver sees today
disappears at exactly the same moment and by exactly the same mechanism:
`passengerName`, `destination` and the order creation time (`DriverHome.tsx`
lines 675–677) are all removed from the screen by the same
`visibleProposals` filter at line 591. The stated price inherits an existing
product gap; it does not introduce a new one, and it is not evidence against
placing the value on `Proposal`.

**No ride-history, receipt or earnings screen exists.** This is stated as a
verified negative, not an assumption: the complete set of pages is listed in
R7.1 step 6, and the code's own comment at `DriverHome.tsx` lines 587–590
independently records the absence. **This ADR therefore neither assumes nor
authorizes one.** Whether a driver needs to see previously stated amounts
after a ride is a product question, recorded as Open Question 9 below and
left for the Product Owner. It belongs in `PIOS_UX_BACKLOG.md`, not in this
sprint.

### R7.5 Does the value need to reach anywhere else in Sprint 4? No — confinement to `Proposal` is both sufficient and correct

Checked destination by destination rather than asserted:

- **Order Management.** Not needed, and blocked twice over. No Order
  Management capability consumes it; nothing in `OrderResponse` or in the
  Order lifecycle depends on it (R4.7). Getting it there would require
  either a Dispatch → Order Management call on the accept write path
  (forbidden by ADR-027 and by the reference-not-ownership rule, ADR-005 /
  ADR-019) or a new cross-module event carrying it (forbidden by R4.6 and by
  Decision item 6, and a new contract under ADR-030 for a value no consumer
  wants). **Confirmed: no.**
- **`Assignment` and the assignments read path.** Not needed. The driver's
  screen already holds both objects side by side and keys them by the same
  `orderId` (`DriverHome.tsx` lines 333–353, 657), so the amount renders on
  the same card as the ride-progress buttons without ever touching
  `Assignment`. Copying it there would also violate R2's "one fact, one
  home" and erode ADR-041's ride-progress-only boundary for `Assignment`.
  **Confirmed: no.**
- **Dispatch's own availability projection / any Assignment Policy.**
  Forbidden outright by R4.5 and ADR-034 Part 2 line 58, and structurally
  impossible under R3's temporal argument (the value does not exist until
  after selection). **Confirmed: no.**
- **The Coordinator screen.** No change needed and none authorized.
  `Coordinator.tsx` declares its own local `ProposalResponse` interface
  (line 33) and renders only `proposalId` and `status` (line 258), so an
  added field is structurally ignored. Recorded as an observed fact, not as
  a decision about whether a coordinator *should* see it — that is not asked
  and is not answered.
- **Notifications, Payments, `network-management`, `identity`,
  `driver-management`, `passenger-experience`.** Untouched. Payments stays
  unscaffolded (R6). No new entry in `INTERFACE_CONTRACTS.md` Section 3.

**Conclusion: confinement to `Proposal`, with a driver-facing read-back
only, is exactly the right size for the ruling's "system data, for now"
framing.** Every wider destination is either forbidden by an already-ratified
document or serves no consumer that exists.

## R8. What "the passenger does not see it" can and cannot mean in Sprint 4

The ruling is unambiguous about intent. Recorded here is the gap between
intent and what Sprint 4 can actually enforce, so that nobody later mistakes
one for the other.

`GET /v1/proposals?orderId=…` — the query `RideRequest.tsx` line 159 polls —
is served by the **same** `toResponse()` (`ProposalController` lines 178,
187–188) as the driver's own `?driverId=` query. Adding the field to
`ProposalResponse` therefore places it in the JSON body delivered to the
passenger's browser, even though nothing renders it.

**Two readings of the ruling, and the resolution:**

- *"The passenger UI does not display it."* Satisfied with **zero code
  change**: `RideRequest.tsx` reads only `item.status` (lines 159–166) and
  TypeScript's structural typing makes the extra field invisible to it.
- *"The amount is not transmitted to the passenger."* **Cannot be delivered
  in Sprint 4 by any means proportionate to the sprint**, and suppressing it
  on the `?orderId=` branch would be security theatre rather than a control.
  PIOS has **no authentication and no authorization anywhere** — ADR-038 and
  `DriverHome.tsx` lines 152–154 state this explicitly (*"This still is not
  authentication … it does not prove anyone is who they claim to be"*). Any
  client may call `?driverId=` directly. A per-branch projection would
  therefore hide the value from the one query the passenger page happens to
  use while leaving it fully retrievable from the adjacent one, at the cost
  of making a single REST resource return different content depending on the
  query parameter — an ADR-004 resource-consistency cost paid for no real
  guarantee.

**Decision: single response shape; no per-caller projection; no passenger UI
change.** The ruling is honoured as "not displayed to the passenger," which
is what Sprint 4 can actually guarantee. This is recorded as **Open Question
8** — a named limitation, not a blocker — because real read-side
confidentiality requires an authorization model that does not exist and that
needs its own ADR. This is the architecture role's interpretation of the
ruling's reach, not a quotation of it, and the Product Owner may overrule it;
overruling it means either accepting the per-branch projection with its
disclosed cost, or gating Sprint 4 on an authorization decision.

## Developer Scope (Sprint 4, revised — supersedes the prior list)

Baseline is the `(*)`-marked file list in "Amendment 2026-07-31 — additional
evidence read for the revised Decision" above. **Delta from that list is
marked.** Everything not listed is out of scope and remains covered by "What
This ADR Does Not Authorize" as amended.

**Backend — `dispatch` only (unchanged from the prior scope):**

1. `backend/dispatch/src/main/kotlin/com/pios/dispatch/domain/Proposal.kt` —
   add a `var statedPrice: String? = null; private set` property (R5's
   shape: **not** a constructor parameter — widening the constructor breaks
   `PostgreSQLProposalRepository.reconstruct`'s reflective
   `getDeclaredConstructor(String, String, String)` lookup at lines 111–115,
   silently, at runtime). `accept(statedPrice: String? = null)` sets it once.
   `decline()` and `lapse()` never touch it (Open Question 6, reconfirmed by
   the round-2 ruling's exclusion of counter-offers).
2. `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/ProposalApplicationService.kt`
   — carry the value on `AcceptProposalCommand` (it already flows
   controller → orchestration → service unchanged, so no method signature
   widens) and pass it to `proposal.accept(...)` at line 117.
3. `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/ProposalAssignmentOrchestrationService.kt`
   — no signature change expected; `command` already reaches
   `acceptProposalWithinCallerTransaction` at line 125. Verify only.
4. `backend/dispatch/src/main/kotlin/com/pios/dispatch/api/AcceptProposalRequest.kt`
   — **new file.** Single optional field, defaulted. `ProposalController.acceptProposal`
   (lines 102–114) currently takes **no body**; the body must be
   `@RequestBody(required = false)` so `DriverHome.tsx`'s existing bodyless
   POST and any other caller keeps working (ADR-026; `SubmitOrderRequest.kt`
   lines 10–15 record this exact class of break happening once already).
5. `backend/dispatch/src/main/kotlin/com/pios/dispatch/api/ProposalController.kt`
   — accept the optional body on the accept endpoint only; extend
   `toResponse()` (lines 187–188). **Single response shape — do not project
   per query branch** (R8).
6. `backend/dispatch/src/main/kotlin/com/pios/dispatch/api/ProposalResponse.kt`
   — one nullable field.
7. `backend/dispatch/src/main/kotlin/com/pios/dispatch/persistence/PostgreSQLProposalRepository.kt`
   — add the column to all three `SELECT`s (lines 68, 84, 98), to `save`'s
   `INSERT` column list, **and — load-bearing — to `save`'s
   `ON CONFLICT (id) DO UPDATE SET` clause at line 57, which today updates
   `status` only.** The row is inserted at *propose* time and the price is
   written at *accept* time, so accept always takes the `DO UPDATE` path: if
   the column is added to the `INSERT` list alone, the stated price is
   silently never persisted and every symptom appears only after a
   round trip through the database. Also pass the persisted value into
   `accept(...)` in `reconstruct`'s `ACCEPTED` branch (lines 118–123),
   mirroring ADR-040's `Assignment.accept(at)` reconstruction pattern.
8. `backend/dispatch/src/main/resources/db/migration/dispatch/V6__proposal_stated_price.sql`
   — **new file**, next free version confirmed `V6` (`V1`–`V5` present).
   One additive nullable column; no default, no backfill, per
   `V5__assignment_ride_lifecycle.sql`'s own precedent.

**Frontend — DELTA, added by the round-2 ruling (items 1–2 by the
"driver input is in scope" ruling; item 3 by R7.3):**

9. `frontend/src/pages/DriverHome/DriverHome.tsx` — **three changes, all in
   this one file:**
   - **(a) Input.** A price field on the `OPEN` proposal card, in the
     `proposalActions` block at lines 679–694, next to "Принять". Free text,
     optional, no client-side numeric parsing, no formatting, no currency
     symbol, no placeholder implying a currency or a magnitude — R4.1/R4.2/
     R4.4 bind the frontend exactly as they bind the backend, and a
     suggestive placeholder or a pre-filled value would be PIOS recommending
     an amount.
   - **(b) Send.** `respondToProposal` (lines 425–445) currently posts with
     no body for both actions. The body must be attached on the `'accept'`
     branch **only**; `'decline'` must remain bodyless (Open Question 6).
   - **(c) Read-back render.** Add `statedPrice` to the `ProposalListItem`
     interface (lines 50–55) and render it conditionally on the card for
     `ACCEPTED` proposals, alongside the existing `passengerName` /
     `destination` / time lines at 675–677, using the same
     `{value && <p className={styles.status}>…</p>}` pattern. **This is what
     makes the field non-write-only (R7.2, R7.3) and is not optional.**
10. `frontend/src/pages/DriverHome/DriverHome.module.css` — only if (a)
    needs an input style; reuse the existing `driverCodeInput` if it fits.

**Explicitly NOT in scope, restated because the round-2 ruling makes some of
these newly tempting:** any change to `RideRequest.tsx`, `Coordinator.tsx`,
`PassengerLanding.tsx`; any change to `Assignment`, `AssignmentStatus`,
`AssignOrderCommand`, `Assignment.create`, the availability projection, any
outbox record or event payload, any Assignment Policy; any change to
`order-management`, `passenger-experience`, `driver-management`,
`network-management` or `identity`; any endpoint that edits, clears or
re-states an amount; any history, receipt or earnings screen; any
comparison, sorting or aggregation of amounts anywhere, backend or frontend
(R4.3 — the single most important prohibition in this ADR).

## Definition of Done (revised)

1. A driver can enter an amount on an open order card and accept it in one
   action; accepting without entering one still succeeds and stores `null`.
2. **The accepting driver sees the amount they stated on that order's own
   card afterwards, and still sees it after a full page reload** — the
   reload is the check that proves the value round-tripped through
   `pios_dispatch` rather than surviving only in React state (R7.1 step 3).
3. Declining never stores an amount; there is no path in the product that
   modifies an amount once stated.
4. A caller that posts to the accept endpoint with **no body at all** still
   succeeds (ADR-026 regression check — this is the break
   `SubmitOrderRequest.kt` lines 10–15 record happening once before).
5. Proposals created before the migration load and accept normally, with a
   `null` amount.
6. The passenger screen shows no amount and no price-related control; its
   accept-status polling behaves identically to before.
7. No event payload, outbox record or cross-module contract changed;
   `order-management` has zero diff.
8. No code anywhere compares, sorts, sums or parses a stated amount.

## Risks (revised)

- **Risk 3 (original: "the field may be unreachable in the running
  product") — retired on the write side, and retired on the read side
  *conditionally*.** The driver-input ruling makes the write reachable; only
  Developer Scope item 9(c) makes the read reachable. If 9(c) is dropped
  during implementation, the risk returns in full and the field becomes
  write-only. This is now a Definition-of-Done item (item 2) specifically so
  that it cannot be dropped quietly.
- **New — the value has no reader after ride completion, permanently.**
  Accepted, disclosed, and not concealed by the answer in R7.2. It is the
  same gap that already applies to `passengerName`, `destination` and order
  time (R7.4), and closing it requires a product decision about ride history
  (Open Question 9), not an architectural one.
- **New — the amount is transmitted to the passenger's browser without being
  displayed there.** R8. Not remediable in Sprint 4 by any proportionate
  means, because PIOS has no authorization layer at all. Recorded as Open
  Question 8.
- **Unchanged and still principal — several priced proposals per order is
  structurally the shape of an auction.** R4.3 forbids the comparison that
  would make it one, and the round-2 ruling's blanket exclusion of
  price-agreement and negotiation logic reinforces it from the product side.
  ADR-035 Part 3's multi-candidate semantics remain open and remain the
  place this would first become real.
- **New, small, implementation-level — the `ON CONFLICT DO UPDATE` trap** in
  Developer Scope item 7. Named as a risk rather than left as a code comment
  because its failure mode is silent, passes any test that does not reload
  the aggregate from the database, and produces exactly the "the driver
  typed a price and it vanished" symptom this amendment exists to prevent.

## New Open Questions

7. **Was driver price-input UI in Sprint 4 scope?** — **Resolved: yes**
   (above).
8. **Should the stated amount be withheld from the passenger's own
   `GET /v1/proposals?orderId=` payload, not merely from the passenger's
   screen?** Recorded as a limitation, not a blocker. Not answerable
   meaningfully until PIOS has an authorization model (ADR-038's explicit
   non-scope). R8 records the recommendation (single shape, no per-branch
   projection) and its disclosed cost.
9. **Does a driver need to see previously stated amounts after a ride
   completes?** Not asked and not answered by the round-2 ruling. No ride
   history, receipt or earnings screen exists anywhere in the product
   (verified negative, R7.4). This is a product question for
   `PIOS_UX_BACKLOG.md`; if answered "yes," it needs its own sprint scope and
   would be the first genuine downstream consumer of this field.

## Evolution Path — additions

5. **A driver-facing ride history / receipt surface**, which would be the
   first reader of a stated amount after ride completion (Open Question 9).
6. **An authorization model**, without which R8's read-side limitation
   cannot be closed and no field on any shared read path can be genuinely
   restricted to one audience.

## Additional evidence read for this amendment

All read, none modified. This ADR remains a documentation artefact only; no
production code was changed by it.

- `backend/dispatch/src/main/kotlin/com/pios/dispatch/api/ProposalController.kt`
  lines 102–114 (bodyless accept), 148–155 (`GET /{proposalId}`), 170–185
  (`listProposals`, both branches through one `toResponse()`), 187–188
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/api/ProposalResponse.kt`
  lines 9–14 (four fields, no others)
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/domain/Proposal.kt`
  lines 29–35, 42–48, 92–107
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/ProposalApplicationService.kt`
  lines 99–120
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/ProposalAssignmentOrchestrationService.kt`
  lines 121–132
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/persistence/PostgreSQLProposalRepository.kt`
  lines 52–64 (`save`, incl. the `ON CONFLICT … DO UPDATE SET status` clause
  at line 57), 66–108 (three `SELECT`s), 110–125 (`reconstruct`)
- `backend/dispatch/src/main/resources/db/migration/dispatch/` — `V1`–`V5`
  present; `V6` is the next free version
- `frontend/src/pages/DriverHome/DriverHome.tsx` lines 25, 50–55
  (`ProposalListItem`), 152–154 (not authentication), 252–267 (3s poll),
  300–321 (`loadProposals`), 333–353 (`loadAssignments`), 425–445
  (`respondToProposal`, bodyless), 587–591 (`visibleProposals`, "No history
  screen exists yet"), 653–677 (card render), 679–694 (accept/decline
  actions)
- `frontend/src/pages/RideRequest/RideRequest.tsx` lines 153–197 (polls
  `?orderId=`, reads `.status` only)
- `frontend/src/pages/Coordinator/Coordinator.tsx` lines 33, 97, 161, 188,
  255–258 (local `ProposalResponse` interface; renders `proposalId` and
  `status` only)
- `frontend/src/pages/` — complete page inventory: `Coordinator`,
  `NetworkTest`, `PassengerLanding`, `NotFound`, `RideRequest`,
  `DriverHome`. No history, receipt or earnings screen exists.

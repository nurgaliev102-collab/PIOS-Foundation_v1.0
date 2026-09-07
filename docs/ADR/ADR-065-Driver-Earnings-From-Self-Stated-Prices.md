# ADR-065: Driver Earnings from Self-Stated Prices — Dispatch Propagates the Utterance, Driver Management Aggregates It

## Status

**Accepted — implemented.** Decision item 4 (the parsing rule, exactly as written) and Decision item 7 (no backfill) were both confirmed by the product owner on 2026-09-07. Implemented the same day: Dispatch forwards `statedPrice` on `AssignmentCompleted`; Driver Management parses via `PriceParser` and extends `GET /v1/drivers/{driverId}/milestones` with `totalStatedEarnings`/`unpricedRidesCount`; frontend renders the new tile once the field is a number. See References for the exact commits.

**Decision Date:** 2026-09-07

**Product authority.** The business rule itself is already ratified by the product owner, in conversation on 2026-09-06/07: a driver's «заработок» on their home screen is *«Сумма цен, которые водитель сам назвал в поездках»* — the sum of the prices that driver themselves named, across their own completed rides. No PIOS commission exists, so this figure is gross, not net. This ADR does not question, narrow, or redefine that rule (`CLAUDE.md`, "Never Invent Business Rules"). It decides only the minimal technical path to satisfy it consistently with what is already ratified — and surfaces, rather than papers over, the two places where the existing architecture cannot satisfy it without an explicit amendment or an explicit product decision.

This ADR **narrowly amends `ADR-042`** (Stated Ride Price Minimal Model) on two of its own R4 prohibitions — R4.3 (no aggregation) and R4.6 (no propagation) — and does so explicitly, in Decision item 1, rather than letting an implementation quietly contradict them. Nothing else in `ADR-042` is amended: R4.1 (no computation), R4.2 (no validation), R4.4 (no currency semantics), R4.5 (no influence on selection), R4.7 (no PIOS behaviour depends on it) and R4.8 (no settlement) stand unchanged and unweakened.

## Context

### Why this needs an ADR and not an implementation note

Per `.claude/CLAUDE.md` ("Decision Authority"), "a new ADR may be required" and "responsibilities between modules are affected" are both triggered here, and one further trigger is decisive: the requested feature is **directly and explicitly forbidden by a ratified ADR's own strongest prohibition**.

`docs/ADR/ADR-042-Stated-Ride-Price-Minimal-Model.md` R4.3 (lines 585-591):

> **No comparison, ordering, or aggregation.** Amounts are never compared across proposals, orders, drivers, or time; never sorted or ranked; **never summed, averaged, or otherwise aggregated. This is the single most important prohibition in this ADR** [...]

and R4.6 (lines 608-611):

> **No propagation.** The amount appears in **no** event payload, **no** outbox record, **no** cross-module contract, and **no** other module's database. [...] It does not travel to `Assignment`, to `Order`, or to `order-management`.

and the same ADR's own summary of Dispatch's position (lines 505-509): the amount "is not parsed as a number, compared, ordered, summed, or converted (R4.3)"; Dispatch "is the authority for no other module about it — no cross-module contract carries it, no event carries it, no module asks Dispatch for it (R4.5, R4.6)"; and its verification checklist, line 1352: "No code anywhere compares, sorts, sums or parses a stated amount."

An earnings total is, by definition, a sum of stated amounts, computed in a module that is not Dispatch. It cannot be built without amending R4.3 and R4.6. Doing that silently — in code, in a sprint, without a record — is exactly what `CLAUDE.md` ("Never Change Architecture Without an ADR") forbids.

**R4.3's own stated reason matters, and is preserved.** R4.3 exists because "comparison across proposals is exactly what would turn a set of stated amounts into an auction — and an auction is the `Negotiation` capability the Product Owner has twice excluded." A driver summing *their own* stated amounts across *their own* already-completed rides compares nothing across proposals, ranks no driver against another, and influences no dispatch decision. The amendment in Decision item 1 is written to that exact boundary: aggregation is permitted **only within one driver's own completed history, for that driver's own eyes**, and remains forbidden everywhere else.

### What exists today (audit, read from the code, not remembered or assumed)

**Dispatch holds the stated price on `Proposal`, and nowhere else.**

- `backend/dispatch/src/main/kotlin/com/pios/dispatch/domain/Proposal.kt:96` — `var statedPrice: String? = null; private set`, whose own KDoc states it is "A plain, opaque, unvalidated string: PIOS does not compute, parse, compare, or assign any meaning to it beyond carrying whatever the driver typed."
- It is written in exactly two places: `Proposal.accept()` (line 153, the legacy optional path) and `Proposal.proposePrice()` (line 188, the current real path — mandatory and non-blank, `require(statedPrice.isNotBlank())`, line 186). `Proposal.confirmPrice()` (line 211) — the passenger's own agreeing act, and the actual precondition for Assignment creation — deliberately **does not touch** `statedPrice`; its own KDoc says so ("both are already set by `proposePrice` and are not renegotiated here"). **Therefore "the price actually confirmed" is, unambiguously, the `statedPrice` on the `ACCEPTED` Proposal for that order** — no second, separately-confirmed amount exists anywhere.
- Persisted by `V6__proposal_stated_price.sql` on `proposals`. `Assignment` has no price column; `Trip` (`V12__trips.sql`, `ADR-063`) has no price column.

**Dispatch publishes no price in any event, to anyone.**

- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/DispatchAssignmentApplicationService.kt:369-378` — the `AssignmentCompleted` outbox record's payload is exactly `mapOf("orderId" to ..., "driverId" to ...)`, `eventType` `"AssignmentCompleted"`, routing key `assignment.completed`, `eventVersion` 1 (`envelopeFor`, lines 415-424). Same two-field shape for `OrderAssigned`, `AssignmentAccepted`, `AssignmentArrived`, `AssignmentStarted`, and all three `Trip*` events.
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/domain/ProposalPriceProposed.kt` — its own KDoc: "not wired into the outbox [...] no other module needs to react to it, and introducing that would create an unratified external contract." `ProposalApplicationService` writes no outbox record for any Proposal event (that class's own KDoc, "Wiring an outbox write here would therefore silently create a new [...] contract").
- **Conclusion: there is no existing Dispatch event a second consumer could subscribe to that already carries a price.** The "second-consumer event pattern" is available as a *shape*, but not as a free ride — some Dispatch-side emission change is unavoidable.

**Driver Management has no price concept of any kind.** A case-insensitive search of `backend/driver-management` for `price|earning|revenue|fare|заработ` returns **no files**. What it does have is a working, precedent-setting derived read model:

- `backend/driver-management/src/main/kotlin/com/pios/drivermanagement/persistence/AssignmentCompletedListener.kt` — Driver Management's own independent consumer of `assignment.completed`, on its own queue `driver-management.from-dispatch.assignment-completed` (`RabbitMQConsumerTopologyConfiguration.kt:61`), binding that one routing key and never a wildcard (line 49). It validates `eventType`/`eventVersion == 1` and extracts `payload.driverId`, `payload.orderId`, envelope `occurredAt`.
- `AssignmentCompletedApplicationService.kt` — idempotent on `eventId` (`markProcessed` first, effect only if new), inside one `transactionRunner` boundary; feeds `driver_milestones` (`V6`) and `driver_client_rides` (`V7`).
- `V6__driver_milestones.sql:11-16` states the governing principle for this table in its own words: these counts "are both derived, read-model state — never the source of truth [...] this table can be dropped and rebuilt from Dispatch's own event history without losing anything Driver Management itself owns."
- Read path: `GET /v1/drivers/{driverId}/milestones` → `DriverMilestonesResponse(driverId, completedRidesCount, currentStreakWeeks, repeatClientsCount)` (`DriverController.kt:138`), already fetched by `frontend/src/pages/DriverHome/DriverHome.tsx` into `milestones` state (line 442).

**Order Management's own consumer tolerates an added payload field.** `backend/order-management/src/main/kotlin/com/pios/ordermanagement/persistence/AssignmentCompletedListener.kt:54-75` parses with `objectMapper.readTree` and reads named fields explicitly (`payload.orderId` only). An **additive, optional** field in `payload` is invisible to it. It does, however, hard-require `eventVersion == 1` (line 63) — so the added field must **not** come with a version bump, or every completion event dead-letters in Order Management. Driver Management's own listener requires the same (line 45).

### The honest limitation this audit surfaces

**The stated price is free text, and nothing in the system constrains it to a number.**

`frontend/src/pages/DriverHome/DriverHome.tsx:1299-1307` renders the driver's price field as `<Input type="text" ...>` with placeholder «Стоимость поездки» — no `inputMode`, no numeric coercion, no pattern. The backend accepts it as `ProposePriceRequest(statedPrice: String, ...)` and validates only non-blankness. `ADR-042` R5 (lines 623-630) ratified precisely this: "a bare string asserts nothing about currency, units, precision or format, which is exactly the property required while Open Question 2 [currency] is unanswered."

Therefore **«сумма цен» is not computable from what is stored without interpreting free text as a number** — an interpretation `ADR-042` R4.2 explicitly forbids ("**Explicitly forbidden:** parsing it as a number"). Today's real pilot values appear to be bare integers (`DriverHome.test.tsx:157` types `'350'`; `Coordinator.test.tsx:122` expects «Стоимость: 950»), but nothing enforces that, and a driver may have typed «350 руб», «договоримся», or «~400».

This is not a detail an implementer should resolve by choosing a lenient regex. Any parsing rule — whether «350 руб» counts as 350, whether «350.50» rounds, what happens to «договоримся» — is a decision about how PIOS reads money, and Decision item 4 states the minimal rule this ADR proposes while naming it as the one item requiring explicit product-owner confirmation before implementation.

## Decision

### 1. Narrow amendment to `ADR-042` R4.3 and R4.6

- **R4.3 (aggregation)** is amended to permit summation of stated amounts **within a single driver's own completed rides, presented only to that driver**. Comparison, ordering, ranking, averaging, or aggregation **across** proposals, orders, drivers, or candidates remains forbidden, for R4.3's own stated anti-auction reason. This earnings figure influences no dispatch decision, appears in no candidate selection, and is never shown to a passenger or used to rank drivers.
- **R4.6 (propagation)** is amended to permit the amount to travel in **exactly one** event payload — `AssignmentCompleted` — to **exactly one** consumer, Driver Management, for **exactly one** purpose, the driver's own earnings read model. It still does not travel to `order-management`, to `passenger-experience`, to `Order`, or into any API response other than the driver's own milestones endpoint.
- `ADR-042` R4.1, R4.2 (as it applies to **Dispatch**), R4.4, R4.5, R4.7 and R4.8 are unchanged. Dispatch still computes, validates, parses and interprets nothing. `INTERFACE_CONTRACTS.md` §4's "Dispatch [...] does not own [...] payment or pricing information" is unchanged: Dispatch remains, in `ADR-042`'s own phrase, "the custodian of a record of an utterance," and now forwards that utterance verbatim rather than interpreting it.

### 2. Dispatch emits the confirmed price verbatim, on the existing `AssignmentCompleted` event, at `eventVersion` 1

- `payload.statedPrice` is added to the `AssignmentCompleted` envelope as an **additive, optional, nullable string**, carrying the value exactly as the driver typed it. **`eventVersion` stays 1** — both existing consumers hard-require `eventVersion == 1` (evidence above); bumping it would dead-letter every completion event in Order Management.
- The value is sourced by looking up the order's own `ACCEPTED` `Proposal` at completion time (`ProposalRepository.findByOrder(assignment.order)`, an already-existing method — `ProposalRepository.kt:52`), inside the transaction `completeAssignment` already opens. This is a read between two aggregates **inside the same bounded context**, not a cross-module read: no boundary is crossed, `ADR-005`/`ADR-009`/`ADR-019` are untouched.
- `null` when no such Proposal exists — notably the direct `POST /v1/assignments` path, which creates an Assignment with no Proposal at all. `null` means "no amount was ever stated," never zero (`ADR-042` R4.1's own "absent input means `null`, not zero").
- **No Dispatch schema change.** No new Dispatch migration; `V14` remains the latest.
- The parallel `TripCompleted` event is **not** changed. It has no consumer today (`INTERFACE_CONTRACTS.md` §7) and adding a field to an unconsumed event is speculative; when Order Management's consumer is eventually migrated to `trip.completed` (a distinct, not-yet-authorized task), that task carries the same field across.

### 3. Driver Management owns the earnings read model, and does the interpreting

- Driver Management's existing `AssignmentCompletedListener` extracts the new optional `payload.statedPrice` (absent/blank → `null`, **not** a validation failure: an event published before this ships, or from the no-Proposal path, must not dead-letter).
- `AssignmentCompletedApplicationService` records it inside the same idempotent, single-transaction `markProcessed` boundary that already guards the milestone counts — so a redelivered event never double-counts earnings, by exactly the mechanism already proven for `completedRidesCount`.
- **Both the verbatim string and the parsed amount are stored per ride.** Keeping the verbatim value is what makes Decision item 4's rule revisable later without a Dispatch replay, and is the direct analogue of `V6`'s own "can be dropped and rebuilt" property.
- This is a derived read model, not a second source of truth. Dispatch's `proposals.stated_price` remains authoritative; Driver Management's copy is rebuildable and disposable.

### 4. The parsing rule — strict, conservative, and visibly incomplete (**requires product-owner confirmation**)

- A stated price contributes to the sum **only if, after trimming, it consists entirely of digits** (optionally with internal spaces as thousand separators, which are removed). Everything else — «договоримся», «350 руб», «~400», «350.50», an empty value — is stored verbatim, parsed as `null`, and **excluded from the sum**.
- The number of excluded rides is counted and exposed alongside the total (`unpricedRidesCount`), so the driver's screen can be honest that the figure covers *N* of *M* rides rather than silently under-reporting.
- **No currency is assumed, stored, inferred, or displayed by the backend.** `ADR-042` Open Question 2 stays open; the total is a bare number, and any «₽»/«руб» suffix is a frontend presentation choice, not a backend fact.
- **This rule is a proposal, not a ratification.** A looser rule (strip a trailing currency word; accept decimals) or a stricter one is the product owner's call, not the architect's or the implementer's — it decides how PIOS reads money. Implementation must not start until this item is confirmed.

### 5. Read path: extend the existing milestones endpoint, add no new one

`DriverMilestonesResponse` gains `totalStatedEarnings` and `unpricedRidesCount`. `GET /v1/drivers/{driverId}/milestones` is already authorized, already called by `DriverHome.tsx` on load (line 442), and already carries this driver's own derived figures — a second endpoint would duplicate its authorization for no gain. No new controller, no new authorization rule.

### 6. Schema: one new Driver Management migration, `V8`

`backend/driver-management/src/main/resources/db/migration/drivermanagement/V8__driver_earnings.sql` — the next free number after `V7`, forward-only, `ALTER TABLE` rather than an edit to `V6`, per this module's own established convention (`V7`'s own header comment: "A new migration, not an edit to V6, per this codebase's own forward-only Flyway convention").

### 7. No historical backfill (**requires product-owner acknowledgement**)

Rides completed **before** this ships contribute nothing to the total. Backfill was checked and is **not** trivial or safe:

- The prices live in Dispatch's own database (`proposals.stated_price`); Driver Management has its own, separate database. A backfill query would have to cross that boundary — precisely what `ADR-005`/`ADR-009` forbid — or be driven by a new Dispatch-side replay/export capability that does not exist and would itself need authorizing.
- Replaying `dispatch_outbox` does not help: the historical `AssignmentCompleted` payloads never contained a price (evidence above), and re-publishing them would re-run Order Management's own `SUBMITTED → COMPLETED` consumer.

The earnings figure therefore counts from ship date forward. The driver-facing screen must not imply otherwise; how that is worded is a product decision, not one made here.

## Alternatives Considered

- **Carry the confirmed price onto `Assignment` or `Trip` at creation time, then publish it from there.** Architecturally tidier — the agreed price is genuinely a property of the trip that happened — but strictly larger: a new Dispatch migration (`V15`), a new field on `Trip`/`Assignment`, a change to `AssignOrderCommand`, and a change to `PostgreSQLTripRepository`'s reconstruction path (whose reflective private-constructor lookup `Proposal.kt:88-95` warns "would break [...] at runtime, silently, in a path no unit test that avoids the database would catch"). Rejected as the *first* step; recorded in Evolution Path as the right shape if price ever needs to be a first-class trip fact rather than a forwarded utterance.
- **A new Dispatch event (`RidePriceConfirmed` or similar) with its own queue and consumer.** Rejected: a whole new cross-module contract, topology, DLQ and idempotency ledger to deliver one optional field onto an event Driver Management **already** consumes, keyed by the same `eventId` and arriving at the same moment.
- **Dispatch parses the amount and emits a number.** Rejected: it would break `ADR-042` R4.2 *inside Dispatch*, the one place that ADR's "custodian of an utterance, not a pricing capability" argument (lines 499-513) most depends on, and it would make Dispatch the authority on how PIOS reads money — contradicting `INTERFACE_CONTRACTS.md` §4 line 39. Forwarding verbatim and interpreting locally keeps the interpretation in the single module that owns the read model.
- **Driver Management queries Dispatch over HTTP for each completed ride's price.** Rejected outright: a server-to-server call baked into a write path is exactly what the reference-not-ownership rule (`ADR-005`, `ADR-019`) forbids.
- **Compute earnings in the frontend from a driver's proposal list.** Rejected: `GET /v1/proposals?driverId=` returns `statedPrice` for proposals in *every* status, including `PRICE_PROPOSED` and price-declined ones — summing those would count rides that never happened, contradicting the ratified rule's own "completed rides." It would also put the parsing rule in the UI, where no test or ADR governs it.
- **Change the price input to a numeric field so parsing is unnecessary.** Not rejected on merit — it is the clean long-term answer — but it is a product/UX decision with its own consequences (it forbids «договоримся»), it does not fix existing free-text rows, and `ADR-042` R5 deliberately left the field untyped. Recorded in Evolution Path.

## Consequences

### Positive

- One additive, optional field on one event Driver Management already consumes; no new queue, no new binding, no new DLQ, no new idempotency ledger, no new endpoint, no Dispatch migration.
- Idempotency and double-counting protection come for free from the `markProcessed`-first boundary already proven for `completedRidesCount`.
- Dispatch's own "interprets it never" property is preserved exactly where `ADR-042` argued it mattered most.
- Storing the verbatim string beside the parsed amount means the parsing rule (Decision item 4) can be corrected later by rebuilding Driver Management's own table, with no Dispatch replay and no cross-module coordination.
- `ADR-042`'s strongest prohibition is amended in the open, at its stated boundary, with its own reasoning preserved — rather than contradicted silently by a sprint.

### Negative

- **`ADR-042` R4.3, its self-described "single most important prohibition," is no longer absolute.** Every future request to compare or rank stated amounts will now cite this ADR as precedent. Decision item 1's boundary (one driver, own history, own eyes, no dispatch influence) is written narrowly for exactly that reason and must be enforced in review.
- **The figure will be wrong-looking for any driver who typed anything but bare digits**, until Decision item 4 is revisited. `unpricedRidesCount` makes that visible instead of silent, but it does not make it correct.
- **No history.** A pilot driver with 40 completed rides sees ₽0 on day one. This is the most likely source of "the app is broken" feedback and needs a product answer (wording, or an accepted one-time gap), not a technical one.
- A price now exists in a second module's database. Bounded, disposable and rebuildable, but it is a real duplication where `ADR-042` R4.6 previously guaranteed none.
- `AssignmentCompleted` now carries a field at `eventVersion` 1 that older consumers were not written against. Safe today (both consumers read named fields), but it sets a precedent for additive-without-version-bump that `ADR-030` should be re-read against before it is used a second time.

## Evolution Path

None of the following is resolved here; each requires its own Product Decision, its own ADR, or both:

1. **`ADR-042` Open Question 2 (currency, units, formatting)** — still open. This ADR sums bare numbers and asserts no currency.
2. **A typed money value object** replacing the free-text string — the clean answer to Decision item 4, available "to a future ADR once a currency and a formatting rule exist to encode" (`ADR-042` R5, line 629).
3. **A numeric-constrained price input** in `DriverHome.tsx`, and what happens to drivers who want to type «договоримся».
4. **Price as a first-class fact on `Trip`** (Alternatives, item 1) — the right shape if anything beyond this read model ever needs the agreed amount.
5. **Historical backfill**, if the product owner decides day-one ₽0 is unacceptable — requires a new, separately-authorized Dispatch-side replay or export capability.
6. **Net vs. gross.** No PIOS commission exists, so the question does not arise today. If one is ever introduced, this figure's meaning changes and this ADR must be revisited — a commission is a business rule (`ADR-002`), not an architectural one, and is not decided here.
7. **Migrating `AssignmentCompleted`'s consumers to `TripCompleted`** (`ADR-063`'s dual-publish window) — carries this field across when it happens.

Any change to Decision item 1's amendment boundary requires a decision that explicitly supersedes this one, consistent with `ADR-015`.

## What This ADR Does Not Authorize

Any comparison, ranking, ordering, or averaging of stated amounts across proposals, orders, or drivers; any use of a stated amount in candidate selection, Assignment Policy (`ADR-034`), First Refusal (`ADR-062`), or any dispatch decision whatsoever; any display of a driver's earnings to a passenger, to another driver, or in any endpoint other than that driver's own `/v1/drivers/{driverId}/milestones`; any parsing, validation, normalization, or interpretation of the amount **inside Dispatch**; any currency assumption, symbol, or conversion in any backend module; any `eventVersion` bump on `AssignmentCompleted`; any new queue, binding, exchange, or cross-module contract; any change to `order-management`'s consumer, to `Order`, or to `passenger-experience`; any commission, fee, split, settlement, or payment custody; any backfill of rides completed before this ships; any implementation start before Decision item 4 (parsing rule) and Decision item 7 (no backfill) are confirmed by the product owner.

## Related ADRs

- [ADR-042: Stated Ride Price Minimal Model](ADR-042-Stated-Ride-Price-Minimal-Model.md) — **narrowly amended by this ADR** on R4.3 (aggregation) and R4.6 (propagation); all other R4 items stand unchanged.
- [ADR-002: Dispatch Engine](ADR-002-Dispatch-Engine.md) — pricing rules are out of architectural scope; this ADR records a rule the product owner ratified, and invents none.
- [ADR-005: Data Ownership](ADR-005-Data-Ownership.md) / [ADR-009: Domain Isolation](ADR-009-Domain-Isolation.md) / [ADR-019: Conceptual Data Ownership](ADR-019-Conceptual-Data-Ownership.md) — reference-not-ownership, applied unmodified; Dispatch remains the source of truth for the stated price.
- [ADR-030: Event Schema Versioning Strategy](ADR-030-Event-Schema-Versioning-Strategy.md) — the convention this ADR's additive-field-at-v1 choice must be read against.
- [ADR-041: Order Lifecycle Synchronization with Assignment Completion](ADR-041-Order-Lifecycle-Synchronization-with-Assignment-Completion.md) — Order Management's own `AssignmentCompleted` consumer, deliberately unmodified.
- [ADR-063: Trip as a New Dispatch-Owned Aggregate](ADR-063-Trip-as-a-New-Dispatch-Owned-Aggregate.md) — the dual-publish window; `TripCompleted` deliberately unchanged here.
- [ADR-064: Referral Visibility — No Network Management Activation](ADR-064-Referral-Visibility-No-Network-Management-Activation.md) — the immediate precedent for this ADR's shape: audit first, surface what the data cannot support, propose the minimal-footprint placement.
- [ADR-015: Evolution Strategy](ADR-015-Evolution-Strategy.md) — supersession discipline for any future change to Decision item 1.

## References

Read, not modified:

- `backend/dispatch/src/main/kotlin/com/pios/dispatch/domain/Proposal.kt` (lines 96, 144-157, 178-218), `domain/AssignmentCompleted.kt`, `domain/ProposalPriceProposed.kt`, `application/ConfirmPriceCommand.kt`, `application/ProposalRepository.kt` (line 52), `application/ProposalAssignmentOrchestrationService.kt` (lines 145-156), `application/DispatchAssignmentApplicationService.kt` (lines 272-282, 369-378, 415-424), `api/ProposePriceRequest.kt`.
- `backend/dispatch/src/main/resources/db/migration/dispatch/` — `V6__proposal_stated_price.sql` … `V14__proposals_one_open_per_order.sql` (latest is `V14`).
- `backend/driver-management/src/main/kotlin/com/pios/drivermanagement/persistence/AssignmentCompletedListener.kt`, `persistence/RabbitMQConsumerTopologyConfiguration.kt`, `application/AssignmentCompletedApplicationService.kt`, `application/AssignmentCompletedUpdateCommand.kt`, `domain/DriverMilestones.kt`, `api/DriverMilestonesResponse.kt`, `api/DriverController.kt` (line 138).
- `backend/driver-management/src/main/resources/db/migration/drivermanagement/V6__driver_milestones.sql`, `V7__driver_milestones_repeat_clients.sql` (latest is `V7`).
- `backend/order-management/src/main/kotlin/com/pios/ordermanagement/persistence/AssignmentCompletedListener.kt` (lines 54-75).
- `frontend/src/pages/DriverHome/DriverHome.tsx` (lines 442, 883-899, 1299-1307), `frontend/src/pages/RideRequest/RideRequest.tsx` (lines 1208-1256).
- `docs/INTERFACE_CONTRACTS.md` §4 (line 39), §5, §7.
- Product-owner ratification, 2026-09-06/07: «Сумма цен, которые водитель сам назвал в поездках».

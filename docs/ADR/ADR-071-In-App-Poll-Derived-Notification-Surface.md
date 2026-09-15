# ADR-071: In-App, Poll-Derived Notification Surface — and Why This Is Not the Notifications Bounded Context

## Status

**Accepted, 2026-09-15, by the Product Owner, in-session**, with three scope constraints supplied at ratification and folded into Part 3, Part 4 and Part 6 below:

1. Cover only genuinely critical Taxi-flow facts — a new order/proposal for the driver, an order-status change, an accept/decline, and anything requiring an action by the passenger or the driver.
2. **Never fabricate or approximate a fact PIOS cannot honestly derive from already-polled data.** Where a requested notification is not reliably derivable, it is named as a gap in Part 5 rather than faked, approximated, or filled with a guess.
3. Design so a future, real Notifications Platform can replace the source of these facts without a rewrite — **but build none of that platform now.**

**Proposed Date:** 2026-09-15. **Ratified Date:** 2026-09-15. **Author:** Architect role.

### The standing Product Decision this ADR sits under, and how it was resolved

`docs/PRODUCT_DECISION_NOTIFICATIONS.md` is a **Product Owner-authority** decision (its own §3) and it is unambiguous. §6:

> *"**PIOS's MVP has no currently ratified, concrete business need for the Notifications capability.**… **No event-consumption relationship is authorized by this decision.** In particular, Notifications is **not** authorized to consume OrderSubmitted, OrderAssigned, AssignmentAccepted, DriverAvailabilityChanged, OrderCompleted, or OrderCancelled… **Recommendation: defer Notifications implementation entirely**, including module scaffolding, database technology evaluation, and any RabbitMQ consumer work."*

That document's §7 names, as its own first Unresolved Product Question, precisely the question this Sprint asks:

> *"**Is there a real participant-facing notification need PIOS's MVP requires, not yet captured by any use case?** For example: should a passenger be told when their order is assigned or their driver arrives?… Answering it is a Product Owner decision, not an architectural or engineering one."*

**This ADR does not supersede, amend, or route around §6.** It does not need to, and that is the whole point of the design below: **Part 1 authorizes no event consumption, no module scaffolding, no database, and no RabbitMQ consumer.** Every one of §6's prohibitions remains in force, untouched, after this ADR is implemented. What the Product Owner ratified here is narrower than §7 Q1's question — it is an **in-app presentation of facts the two existing clients already fetch for their own, unrelated reasons**, not the Notifications capability. §6 is therefore left standing rather than superseded, and `PRODUCT_DECISION_NOTIFICATIONS.md` is **not modified by this ADR**.

Recorded honestly, because the distinction is load-bearing and could otherwise be read as a technicality: if this surface ever needs to reach a user whose app is **closed**, §6 must be superseded first by its own dated Product Decision, following §9's own prescribed sequence. Part 5's Gap 3 and Part 6's Negative consequences state that limit in terms.

### Evidence basis

**No `E-NNN` entry justifies this Sprint.** `docs/PIOS_PRODUCT_EVIDENCE.md`'s journal still contains exactly one real entry — `E-001` (05.09.2026), a registration phone-format defect whose own text records *«Связанная гипотеза: нет напрямую»*. The three `E-000` entries above it are labelled *«иллюстрация, не реальное наблюдение»* (line 59). Nothing in the log bears on notifications.

The honest basis is therefore the **second admissible Sprint basis** — a hypothesis registered **before** the Sprint — per `PIOS_PRODUCT_EVIDENCE.md`'s own «Дополнение 2026-08-01». **`H10` is registered in `docs/PIOS_PRODUCT_HYPOTHESES.md` by the Product Owner immediately before this Sprint**, the fifth application of that path after `ADR-054`/H7, `ADR-068`/H8 and `ADR-070`/H9. This Sprint **produces** evidence; it consumes none.

This ADR invents no business rule (`ADR-002`; `CLAUDE.md`, "Never Invent Business Rules"). Every notification it authorizes is a restatement of a fact an existing aggregate already owns; where a requested notification has no such fact behind it, Part 5 records it as a gap instead.

---

## Context

### What exists today: nothing, confirmed

`docs/PIOS_TAXI_PRODUCT_COMPLETION_AUDIT.md` Section 14 records *"No push/in-app notification system at all (confirmed, zero matches in repo search)"*, and Section 3 states the user-facing consequence: *"a driver who isn't looking at the app when an order arrives has no way to learn about it."* `docs/PIOS_TAXI_COMMERCIAL_PRODUCT_BLUEPRINT.md` Section 13 lists a minimal notification mechanism under SHOULD HAVE, and Section 21 sequences it in Phase 4.

### The decisive finding: every critical fact is already polled, every 3 seconds, on both sides

This is what makes a v1 possible with no backend work at all, and it was verified by reading both clients rather than assumed.

**Driver side** — `frontend/src/pages/DriverHome/DriverHome.tsx`, `PROPOSALS_POLL_INTERVAL_MS = 3000` (line 53), interval at lines 790–794:

| Already-polled read | Line | Facts it already carries |
|---|---|---|
| `GET /v1/proposals?driverId=` | 942 | every proposal's `status` (`OPEN \| PRICE_PROPOSED \| ACCEPTED \| DECLINED \| LAPSED \| WITHDRAWN`, line 119), `statedPrice`, `statedEtaMinutes` |
| `GET /v1/orders?ids=` (ADR-060 Mode 2) | 816 | `OrderResponse.status`, `origin`, `passengerName`, `pickupAddress`, `createdAt` |
| `GET /v1/assignments?orderId=` | 982 | `AssignmentStatusValue` (`CREATED \| ACCEPTED \| ARRIVED \| IN_PROGRESS \| COMPLETED`, line 176), `statusChangedAt` |
| `GET /v1/proposals/{id}/messages` | 850 | `ProposalMessageItem { id, senderRole: 'PASSENGER' \| 'DRIVER', body, sentAt }` (lines 137–142) |

**Passenger side** — `frontend/src/pages/RideRequest/RideRequest.tsx`, `STATUS_POLL_INTERVAL_MS = 3000` (line 170), interval at line 931: the same four reads, chained in a deliberately deterministic order (proposals → assignments → orders once → messages, per that file's own comment at lines 908–915).

**`ProposalMessageItem.senderRole` is the fact that makes "a message arrived *for me*" honest** rather than a guess — the client can distinguish a message it sent from one it received without inferring anything.

**`OrderResponse.status` is already in the payload the driver client receives but is not declared on its own `OrderListItem` interface** (`DriverHome.tsx` lines 187–230 omit it). Reading it costs one interface field and **no new request** — see Part 3, D5.

### The PWA gives nothing toward push — checked, not assumed

`frontend/vite.config.ts` lines 55–87 configure `VitePWA({ registerType: 'autoUpdate', manifest: {…} })` and nothing else. The plugin's own block carries the comment (lines 58–60): *"No offline caching strategy is customized beyond the plugin's own default precache of the built app shell; a future task revisits this once a real feature exists to cache meaningfully."* There is **no `strategies: 'injectManifest'`, no custom service-worker source, no `srcDir`/`filename`, no `pushManager` call anywhere in `frontend/src`, and no VAPID key material anywhere in the repository.** Web Push would therefore be entirely net-new: a custom service worker, a `pushManager.subscribe` flow, a VAPID keypair plus its secret management, a subscription store, and a server-side sender.

That sender **is** the Notifications bounded context. Building it reopens both of `ADR-033`'s still-open gates simultaneously — Part A (*"Analytics and Notifications are explicitly not assigned PostgreSQL by this decision"*, `ADR-025`, reaffirmed by `ADR-033`) and Part B (no authorized event consumption) — plus `PRODUCT_DECISION_NOTIFICATIONS.md` §6. That is a module, not a feature.

### `ADR-033`'s boundary, and why this design stays outside it

`ADR-033`'s Responsibility Boundary is precise about what the Notifications module would own:

> *"**Owns:** the Notification entity only — a record of what was communicated to a participant, append-only."*

The design below **persists no record of any communication anywhere on the server**, and stores client-side only a *last-seen marker*, which is a fact about this device's reading position — not a record that PIOS communicated anything to anyone. It therefore does not own a Notification entity, does not encroach on the reserved boundary, and does not pre-empt `ADR-033`. Part 4 makes that non-encroachment a design rule rather than an accident.

---

## Problem

1. Can PIOS give a driver and a passenger a trustworthy in-app signal for the critical moments of a ride, **without** activating the Notifications bounded context, consuming any event, or contradicting `PRODUCT_DECISION_NOTIFICATIONS.md` §6?
2. Which of the requested notifications are honestly derivable from already-polled data, and which are not?
3. What shape keeps a future, real Notifications Platform a *replacement of a source* rather than a rewrite?

---

## Decision

### Part 1 — Placement: the frontend only. No backend change of any kind.

The notification surface is **derived client-side from state both clients already poll**, and lives entirely in `frontend/src`.

**Explicitly not authorized, and not required, by this ADR:**

- No new backend endpoint, and no change to any existing endpoint's request or response shape.
- No new database table, column, or migration in any module.
- No new domain event, no new event version, no change to any payload in `EVENT_CATALOG.md`.
- **No event consumption by anything** — `PRODUCT_DECISION_NOTIFICATIONS.md` §6's six named prohibitions stay fully in force.
- No `notifications` Gradle module, no service, no queue, no dead-letter queue, no consumer, no idempotency ledger.
- No database-technology evaluation for Notifications (`ADR-025`'s gate, reaffirmed by `ADR-033` Part A, is untouched).
- No new cross-module contract in either direction; `INTERFACE_CONTRACTS.md` §5's justified relationships are unchanged.
- **No additional polling.** Not one new request, and no change to either client's existing poll interval, request set, or request ordering. The surface reads state the clients already hold in memory.

This is the same "reuse, do not extend" posture `ADR-070` achieved for Channel 1 (zero backend changes) and is the reason this ADR needs no amendment to any ratified document.

### Part 2 — Derivation rule: observed transitions, never terminal state

**A notification is produced only when the client observes a fact *change* between two consecutive polls. A fact already in its terminal state the first time this client sees it produces nothing.**

Two independent reasons make this a correctness rule rather than a preference:

1. **Attribution is only available in the transition.** `Proposal` sets `status = DECLINED` from **two different acts** — `decline()` (the driver refusing an `OPEN` proposal, `Proposal.kt` line 277) and `declinePriceProposal()` (the passenger refusing a quoted price, line 259) — and both emit the same `ProposalDeclined`. A terminal `DECLINED` read cold is genuinely ambiguous about **who** declined. The transition is not: `OPEN → DECLINED` is the driver's act, `PRICE_PROPOSED → DECLINED` is the passenger's. Attributing a cold-read `DECLINED` would be exactly the approximation the ratification constraints forbid.
2. **Without it, a first load fabricates history.** A driver with 200 lifetime rides opening the app would otherwise receive 200 "notifications" about rides completed weeks ago. That is noise presented as news.

**Therefore: the first poll after mount seeds the baseline silently and emits nothing.** Only the second and subsequent polls can produce a notification. This is stated here as a ratified rule so it is not later "optimized" into a backfill.

### Part 3 — The authorized notification facts

Each row is a fact an existing aggregate already owns, observable as a transition in already-polled data. **Nothing below is computed, weighted, scored, or inferred.** Facts marked **action** require something of the recipient, per ratification constraint 1.

**Driver-facing** (derived from `DriverHome.tsx`'s existing polled state):

| # | Fact | Derived from | Action? |
|---|---|---|---|
| D1 | A new order is waiting for your decision | a `proposalId` not previously seen, with `status === 'OPEN'` | **action** |
| D2 | The passenger confirmed your price — the ride is on | `PRICE_PROPOSED → ACCEPTED` | **action** |
| D3 | The passenger declined your price | `PRICE_PROPOSED → DECLINED` | no |
| D4 | The passenger cancelled before you answered | `OPEN → WITHDRAWN` (`ADR-053`) | no |
| D5 | The passenger cancelled a ride you had accepted | `OrderResponse.status` for a proposal of yours becomes `CANCELLED` | **action** |
| D6 | A new message from the passenger | a `ProposalMessageItem.id` not previously seen with `senderRole === 'PASSENGER'` | **action** |
| D7 | An order you didn't answer is no longer active | `OPEN → LAPSED` (`ADR-052`) | no |

**Passenger-facing** (derived from `RideRequest.tsx`'s existing polled state):

| # | Fact | Derived from | Action? |
|---|---|---|---|
| P1 | The driver named a price — your decision is needed | `OPEN → PRICE_PROPOSED` (with `statedPrice` present) | **action** |
| P2 | Your ride is confirmed | `PRICE_PROPOSED → ACCEPTED` | no |
| P3 | The driver declined your order | `OPEN → DECLINED` | **action** |
| P4 | The driver has arrived | assignment → `ARRIVED` | **action** |
| P5 | The ride has started | assignment → `IN_PROGRESS` | no |
| P6 | The ride is complete | assignment → `COMPLETED` | no |
| P7 | A new message from the driver | a `ProposalMessageItem.id` not previously seen with `senderRole === 'DRIVER'` | **action** |

**D5 is the only row requiring a code change beyond the new feature itself**, and it is one line of type declaration: `OrderResponse.status` is already returned by `GET /v1/orders?ids=` (`OrderResponse.kt` line 54) but is not declared on `DriverHome.tsx`'s own `OrderListItem` (lines 187–230). Adding the field to that interface reads a value already present in a response the client already receives. **No backend change, no new request.**

**Deliberately excluded**, per ratification constraint 1 — these are real facts but require no action and do not belong to the critical ride flow: a new connection arriving via the driver's link, milestone/streak changes, and availability changes (which the driver performs themselves and therefore already knows — the same reasoning `PRODUCT_DECISION_NOTIFICATIONS.md` §4 applies to `DriverAvailabilityChanged`: *"a driver already knows their own declaration… without being told about it"*).

### Part 4 — Extensibility toward a future Notifications Platform, without building any of it

Ratification constraint 3 is satisfied by **one seam and one rule**, not by speculative infrastructure (`ENGINEERING_GUIDELINES.md` §3, No Premature Optimization; `ADR-033`'s own citation of it).

- **One module-local shape.** A single `NotificationFact { id, kind, audience, occurredAt, actionRequired, subject }` type, where `id` is derived **only** from identifiers the server already guarantees unique (`proposalId` + target status, `assignmentId` + target status, `messageId`). No client-generated id, no UUID minted locally, no ordering that depends on device clock.
- **One seam.** Facts reach the surface through a single function boundary — a pure `deriveNotificationFacts(previous, current)` over already-polled snapshots. If a real backend Notifications service is ever built, it supplies `NotificationFact[]` through that same boundary and **only the producer is replaced**; the surface, the seen-marker store, and the rendering are unaffected.
- **The rule that keeps it honest:** because `id` is server-derived, the same fact produced later by a real backend carries the same identity, so a migration cannot double-notify. This is the *only* forward-compatibility property this ADR builds.

**Explicitly not built now, and not to be introduced as an implementation detail:** any wire contract or DTO for notifications; any generic client-side event bus, pub/sub, or dispatcher; any notification preferences, templates, categories, or channel-selection concept (`ADR-033` records all of these as *"Explicitly NOT ratified, and NOT decided here"*); any server-side persistence of a notification; any retry, delivery-receipt, or read-receipt mechanism; any abstraction whose only current caller is the one derivation above.

### Part 5 — What cannot be honestly derived: named gaps, not approximations

Per ratification constraint 2. Each of these is a **real limitation of v1**, recorded rather than faked. **None may be approximated, and none may be described to a user as working.**

- **Gap 1 — "No driver was found for your order."** Not derivable. `FallbackDispatchOutcome.NoAvailableDriver` creates **no Proposal and no Assignment**, so by polling alone this state is **indistinguishable from "still searching"**. `ADR-070` Part 7 Q9 already named this as an open UX question and forbade inventing a retry, queue, or timeout rule to paper over it; that prohibition is unchanged and is **not** resolved here. A v1 notification for this fact would require inventing a timeout, which is precisely the forbidden approximation.
- **Gap 2 — attribution of a decline observed only at cold start.** Per Part 2, a `DECLINED` first seen in its terminal state cannot be attributed to the driver or the passenger. Handled by emitting **nothing**, never by guessing.
- **Gap 3 — anything while the app is closed. This is the audit's own Section 3/14 finding, and v1 does NOT solve it.** An in-app surface makes a fact noticeable *while the app is open*; it cannot reach a driver who is not looking. **This must not be described, in release notes or to a driver, as solving missed orders.** Closing it requires Web Push, which requires the Notifications bounded context, which requires `PRODUCT_DECISION_NOTIFICATIONS.md` §6 to be superseded first.
- **Gap 4 — passenger coverage is scoped to the active-ride screen.** `RideRequest.tsx`'s polling effect depends on `[step, orderId, identity, driverCode]` (line 936), and `MyDrivers.tsx` polls nothing at all. A passenger who navigates away from their active ride receives no further notifications until they return. Real, and not fixable without either new polling (excluded by Part 1) or push (Gap 3).
- **Gap 5 — one active order per passenger screen.** `RideRequest.tsx` polls a single `orderId`. A passenger with two concurrent orders is covered for one of them.
- **Gap 6 — seen-markers are per-device.** A `localStorage` marker does not follow a user to another device or survive clearing site data. Acceptable for a reading position; it would not be acceptable for a delivery record, which is exactly why Part 1 stores no delivery record.

### Part 6 — What this ADR does not authorize

Any backend change of any kind; any new endpoint or change to an existing endpoint's shape; any new table, column, or migration; any new event, event version, or payload change; **any event consumption by any module** (`PRODUCT_DECISION_NOTIFICATIONS.md` §6 stands unamended); any scaffolding of the `notifications` module (`ADR-033` Part A's database-technology gate and Part B's authorization gate both remain open and unresolved); any Web Push, service-worker customization, `pushManager` subscription, or VAPID key material; any change to `frontend/vite.config.ts`'s PWA configuration; any additional polling, changed poll interval, or changed request ordering in either client; any notification preferences, templates, categories, or opt-in/opt-out concept; any server-side record of a communication; any approximation of Part 5's gaps; any SMS, email, or external delivery channel (an External Systems integration under `ADR-020`, per `ADR-033`); any change to `GET /v1/connections?driverId=`'s response shape (`ADR-070` Part 5, `ADR-054`); any rating, reputation, score, count, or ranking surfaced to any participant (`DRIVER_IDENTITY_DESIGN_DECISION.md` §4 — see Related Decisions below); any change to Circle of Trust, Primary Driver, Assignment, Trip lifecycle, or test/real segregation (`ADR-054`, `ADR-062`, `ADR-063`, `ADR-068`, `ADR-069`, `ADR-070`).

---

## Alternatives Considered

- **Web Push via the existing PWA.** Rejected for v1. Context establishes the PWA gives literally nothing toward it (precache-only, no custom SW, no VAPID), so this is not "enabling a feature" but building a delivery platform — which requires the Notifications bounded context, which requires superseding `PRODUCT_DECISION_NOTIFICATIONS.md` §6 and resolving `ADR-033`'s two open gates and `ADR-025`'s database-technology gate. Correct eventual answer for Gap 3; wrong first step.
- **Scaffold the `notifications` module now and consume existing events (`OrderSubmitted`, `AssignmentCompleted`, proposal state changes).** Rejected: `PRODUCT_DECISION_NOTIFICATIONS.md` §6 names five of these events and refuses each by name, and `ADR-033` Part B refuses the general authorization. Doing it anyway would be the silent re-decision of a standing Product Decision that `CLAUDE.md` and `MODULE_STRUCTURE.md` §8 forbid. It is also strictly larger than the problem: every fact needed is already in the clients.
- **A new backend "notifications" read endpoint that aggregates proposal/assignment/message state per user.** Rejected as unnecessary and boundary-violating: it would have to read Dispatch's, Order Management's and Driver Management's state together, which no single module may own (`ADR-005`, `ADR-009`, `ADR-019`), and it would duplicate data the clients already hold in memory.
- **Deriving notifications from terminal state at load, with a "since you were away" backfill.** Rejected on correctness, not taste — Part 2: a cold-read `DECLINED` cannot be attributed (two different acts produce it), and a backfill turns a driver's entire ride history into a notification burst.
- **Emitting a "no driver found" notification after a fixed client-side timeout.** Rejected outright. `ADR-070` Part 7 Q9 explicitly forbids inventing a retry, queue, or timeout rule, and `ADR-068` Part 4's "no retry on decline/lapse" is unchanged. Recorded as Gap 1 instead.
- **Treating this as additive under the `Order.destination` precedent (Sprint 3B), with no ADR.** Rejected. The frontend work alone would qualify, but the change sits directly on top of a standing Product Decision that says "defer Notifications entirely" and a reserved bounded context (`ADR-033`). Proceeding silently would leave a future reader unable to tell whether the Notifications boundary had been respected or quietly crossed. The ADR exists mainly to record *why this is outside that boundary* and what would put it back inside.
- **Building a generic client-side event bus now, for future reuse.** Rejected per `ENGINEERING_GUIDELINES.md` §3 and ratification constraint 3's own "but do not build any of that future platform now." Part 4's single seam achieves the forward compatibility that actually matters (stable, server-derived fact identity) at a fraction of the surface.

---

## Consequences

### Positive

- A driver and a passenger get a real in-app signal for every critical ride moment, with **zero backend changes**: no endpoint, no table, no migration, no event, no queue, no module. The strongest form of this codebase's own established discipline (`ADR-070` needed zero new endpoints; this needs zero new anything).
- `PRODUCT_DECISION_NOTIFICATIONS.md` §6, `ADR-033` Parts A and B, and `ADR-025`'s Notifications database-technology gate all remain **in force and unamended** — no ratified decision is superseded, so nothing is owed to a future reader as a silent disagreement.
- Backward-compatible and additive by construction: no existing request, response, payload, row, or screen behavior changes. Both clients' polling loops are byte-for-byte unchanged.
- Fact identity is server-derived (Part 4), so a future real Notifications Platform can replace the producer without double-notifying or a rewrite.
- Attribution is honest by construction (Part 2): the one genuinely ambiguous status in the domain (`DECLINED`) is handled by the transition rule rather than by a guess.

### Negative

- **The headline gap stays open: a user whose app is closed still learns nothing.** The audit's Section 3/14 finding is *not* closed by this ADR, and must not be reported as closed. Part 5, Gap 3.
- Passenger coverage is narrower than driver coverage (Gaps 4 and 5) — scoped to the active-ride screen, one order at a time — purely because that is where the existing poll lives, and Part 1 forbids adding another.
- Seen-markers are per-device and non-authoritative (Gap 6). Acceptable for a reading position, unacceptable for a delivery record — which is exactly why none is kept.
- "No driver found" remains invisible (Gap 1), and this is the state `ADR-070` Part 1 made *common* rather than exceptional by opening Channel 1. This ADR does not worsen it, but it does not help it either, and it declines to fake it.
- Two clients now each carry derivation logic over their own polled state. Kept to one shared pure function (Part 4) to avoid two divergent definitions of the same fact, but it is real duplication of *invocation*, and a third client would need wiring too.
- PIOS still has no authorization layer (`ADR-054` Part 6, `ADR-042` R8). Nothing here changes that, and nothing here may be described as an enforced rule.

---

## Related Decisions

- **Piece 2 of this Sprint's brief — a passenger-facing non-numeric trust signal — was DECLINED by the Product Owner on 2026-09-15 and is not implemented.** `docs/DRIVER_IDENTITY_DESIGN_DECISION.md` §4 stays authoritative and is **not** superseded: its "Facts that must NOT be displayed" list names *"Any numeric rating, **ride count**, or 'X rides together' tally"* and *"**A follower/connection count of any kind, on either side of the relationship**"*. No ADR was written for it, because nothing was authorized. Recorded here only so a future reader finds the decision rather than re-proposing it; `driver_milestones.completed_rides_count` and `repeat_clients_count` remain visible **to the driver themselves only**, gated by `DriverController.getMilestones`'s existing 401/403 check.

## Related ADRs

- [ADR-033: Notifications Module and Event Consumption Boundary](ADR-033-Notifications-Module-and-Event-Consumption-Boundary.md) — the reserved bounded context this ADR deliberately stays outside of; its Parts A and B remain unresolved and unrelieved.
- [ADR-025: Database Technology Decision](ADR-025-Database-Technology-Decision.md) — the Notifications/Analytics database-technology gate, untouched.
- [ADR-070: Channel 1 Discovery Matching](ADR-070-Channel-1-Discovery-Matching-and-Post-Ride-Relationship-Formation.md) — the zero-new-endpoint precedent this ADR follows; its Part 7 Q9 owns Gap 1.
- [ADR-052: Proposal Lapse Resolution Mechanism](ADR-052-Proposal-Lapse-Resolution-Mechanism.md) / [ADR-053: Proposal Resolution on Order Cancellation](ADR-053-Proposal-Resolution-on-Order-Cancellation.md) — the `LAPSED` and `WITHDRAWN` transitions D7 and D4 observe, unchanged.
- [ADR-040: Assignment Ride Lifecycle](ADR-040-Assignment-Ride-Lifecycle.md) — the `ARRIVED`/`IN_PROGRESS`/`COMPLETED` transitions P4–P6 observe; ride progress lives on Assignment, not Order.
- [ADR-060: Order Query Authorization](ADR-060-Order-Query-Authorization.md) / [ADR-066: Proposal Participant Authorization](ADR-066-Proposal-Participant-Authorization.md) — the authorization already governing every read this surface derives from; no read is added and none is relaxed.
- [ADR-063: Trip as a New Dispatch-Owned Aggregate](ADR-063-Trip-as-a-New-Dispatch-Owned-Aggregate.md) / [ADR-068](ADR-068-Relationship-Ordered-Fallback-Dispatch.md) / [ADR-069](ADR-069-Test-Real-Segregation-In-Fallback-Driver-Selection.md) — untouched in every respect.
- [ADR-005](ADR-005-Data-Ownership.md) / [ADR-009](ADR-009-Domain-Isolation.md) / [ADR-019](ADR-019-Conceptual-Data-Ownership.md) — no module gains ownership of another's fact; the clients merely render what they are already authorized to read.
- [ADR-002: Dispatch Engine](ADR-002-Dispatch-Engine.md) — pricing, commission, matching and regulatory rules out of architectural scope; applied in Part 5, Gap 1.

## References

Read for this ADR, **none modified**:

- `docs/PRODUCT_DECISION_NOTIFICATIONS.md` — §3, §6, §7 (Q1), §9
- `docs/PIOS_TAXI_PRODUCT_COMPLETION_AUDIT.md` — Sections 3, 12, 14
- `docs/PIOS_TAXI_COMMERCIAL_PRODUCT_BLUEPRINT.md` — Sections 9, 13, 21
- `docs/DRIVER_IDENTITY_DESIGN_DECISION.md` — §4, §9 (Related Decisions, above)
- `docs/PIOS_PRODUCT_EVIDENCE.md` (journal, one real entry); `docs/PIOS_PRODUCT_HYPOTHESES.md` (H10, registered for this Sprint)
- `frontend/src/pages/DriverHome/DriverHome.tsx` — lines 53, 115–142, 176–230, 790–794, 811–830, 843–871, 933–998, 1595–1600
- `frontend/src/pages/RideRequest/RideRequest.tsx` — lines 170, 860–936
- `frontend/src/pages/MyDrivers/MyDrivers.tsx` — no polling (Gap 4)
- `frontend/src/persistence/localOnboardingSeen.ts`, `localInstallSeen.ts`, `localCurrentOrder.ts` — the existing local-marker convention Part 4 follows
- `frontend/vite.config.ts` — lines 53–88 (PWA precache-only)
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/domain/Proposal.kt` — lines 160–321 (`accept`/`proposePrice`/`confirmPrice`/`declinePriceProposal`/`decline`/`lapse`/`withdraw`; the two `DECLINED` producers at lines 259 and 277)
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/api/AssignmentController.kt` — line 163 (`?orderId=` only)
- `backend/order-management/src/main/kotlin/com/pios/ordermanagement/api/OrderResponse.kt` — line 54 (`status`, already returned)
- `backend/order-management/src/main/kotlin/com/pios/ordermanagement/domain/Order.kt` — lines 188–194 (`cancel` permitted while `SUBMITTED`, i.e. after acceptance — the basis for D5)

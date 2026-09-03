# PIOS Taxi V1 — Target Architecture (Draft for Architectural Review)

Status: **Proposal, not a ratified decision.** This document is architectural design output only — no code, database, schema, migration, API, event contract, or configuration was touched to produce it. Per `.claude/CLAUDE.md`'s own role division ("Architecture decisions are made by the project architect... Claude must not redesign architecture proactively"), everything below tagged **DECISION** is a *recommendation* for that review, not an authorization to implement. Nothing here creates an ADR; Section 29 lists which of these recommendations should become one, if and when approved.

**Sources used**: the actual repository implementation (read directly for this task and for `docs/PIOS_POST_REMEDIATION_BASELINE.md`, Task 6), the 61 existing ADRs (sampled, not exhaustively re-read — cited where read), `DOMAIN_MODEL.md`, `EVENT_CATALOG.md`, `INTERFACE_CONTRACTS.md`, existing migrations, the frontend implementation, the Identity module's actual auth code, and the product-decision documents most directly relevant to dispatch/trust/network (`PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md`, `PRODUCT_DECISION_ELIGIBILITY_MINIMUM_TRUST_BOUNDARY.md`, `PRODUCT_BASELINE_V2.md`, and this session's own `DRIVER_IDENTITY_DESIGN_DECISION.md`/`PASSENGER_INVITATION_DESIGN_DECISION.md`). Where code and documentation disagree, this document says so explicitly and states which one reflects reality — it does not silently pick one.

**Tagging convention** (per this task's own instruction): every substantive statement carries one of:
- **FACT** — directly observed in code, migrations, or config.
- **DECISION** — this document's own architectural recommendation, pending review.
- **ASSUMPTION** — a working premise this document adopts where no ratified answer exists, stated as such so it can be challenged.
- **FUTURE** — explicitly out of Taxi V1 scope; named so it isn't accidentally built early or accidentally foreclosed.

---

## 1. Executive Summary

PIOS-Foundation already implements a real, working, human-mediated dispatch flow across 6 independently-deployable backend modules (FACT — `PIOS_POST_REMEDIATION_BASELINE.md` Sections 2–7). The core architectural bones — module isolation with no shared storage, transactional outbox + RabbitMQ eventing, a real `Order`/`Proposal`/`Assignment` separation, a real (if unverified) Identity/session-auth system — are sound and do not need to be torn down. What Taxi V1 is missing is not a rewrite; it is **four new bounded contexts** (Trip, Payment, Rating/Reputation, and a thin Communication capability) that the current Foundation deliberately left unbuilt, plus **explicit decisions** on several questions the codebase's own comments already flag as open (dispatch fairness model, `OrderSubmitted` consumption, Personal Client priority, Eligibility).

This document's central architectural move (**DECISION**) is to formalize a distinction the code has *already, independently* arrived at without ever naming it: **Order is the passenger's request, Assignment is the match, and the actual ride-in-progress facts (`ARRIVED`/`IN_PROGRESS`/`COMPLETED`) that today live awkwardly bolted onto `Assignment` should become their own `Trip` context.** Section 10 explains why this is a genuine gap, not a renaming exercise.

The second central move is a direct answer to the one question this task singled out (Section 9): **keep `OrderSubmitted` as a published event, but do not make Dispatch's matching begin by consuming it.** Matching begins from an explicit act — a passenger's own request for candidates, or a human Coordinator's own action — not from passive event consumption. `OrderSubmitted` remains valuable as an integration/audit event for a future Notifications or Analytics context, not as Dispatch's trigger.

Every recommendation here is sized for PIOS's actual current stage: a single-pilot, low-driver-count platform, not a national-scale marketplace. Section 23 is explicit about what is deliberately **not** proposed (no new microservice, no new message broker topology, no premature payment-provider integration) because it would be over-engineering for what PIOS is today.

## 2. Architectural Principles

Restated from this task's own prompt, each cross-checked against what is already ratified elsewhere in the repository so this document adds no new product principle of its own:

1. **"В агрегаторе ты работаешь на платформу. В PIOS ты строишь свой бизнес, используя платформу."** — FACT of product intent, consistent with `PRODUCT_DECISION_ENTREPRENEUR_MODEL.md` (personal invitation link, `driverId` taken verbatim, never pooled) and `PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md`'s own ratified ordering: **Fairness > Transparency > Driver autonomy > Reliability > Efficiency** (FACT, that document's Section 1). This ordering governs every dispatch-related decision below.
2. **Commercial viability without commission lock-in** — DOMAIN_MODEL.md Section 14 and `PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md` Section 6 both state directly that whether PIOS is a commission-based marketplace is **undecided**, not decided against (FACT). This document therefore designs a Payment *boundary* (Section 11), never a specific pricing model.
3. **Universality (no big-city density assumption)** — no ratified document addresses this today (FACT, confirmed absent by Task 6's own grep of `docs/`); Section 20 treats it as an open architectural requirement this document must actively design for, not retrofit.
4. **BlaBlaCar-derived trust patterns without becoming BlaBlaCar** — PIOS remains a dispatched taxi/service platform, not a ride-share marketplace; the patterns adopted (Section 12) are participant transparency, predictable interaction, and repeat relationships — never BlaBlaCar's own passenger-choice-of-driver marketplace mechanic, which would contradict the existing (and preserved) Coordinator-mediated/personal-invitation dispatch model.

## 3. Current → Target Overview

| Area | Current State | Target State | Gap | Migration Approach |
|---|---|---|---|---|
| Identity | Phone+password, signed sessions, no verification (FACT) | Same core mechanism + phone verification gate | Verification (Section 13) | Additive: `Device`/`VerificationChallenge` shapes already exist unused (FACT) — implement against them, no breaking change |
| Profiles | No standalone Profile concept for Taxi; Network Management has one, unused by Taxi (FACT) | A thin, Taxi-owned profile view (name, photo\*, vehicle\*) inside Driver Management/Passenger Experience, *not* borrowed from Network Management | Photo/vehicle fields don't exist (FACT) | Additive migration on existing Driver Management schema |
| Passenger | Real booking flow, no history/payment/rating (FACT) | Same + history + payment + rating | 3 new capabilities | Additive; no existing contract changes |
| Driver | Real onboarding/availability/proposal/assignment (FACT) | Same + vehicle, earnings view, reputation | 3 new capabilities | Additive |
| Vehicle | Does not exist anywhere in the domain model (FACT, confirmed absent) | A minimal owned-by-driver vehicle record | Entirely new | New table in Driver Management, optional at first |
| Order | `SUBMITTED`/`COMPLETED`/`CANCELLED` only, deliberately excludes ride states (FACT, `Order.kt`'s own KDoc) | Unchanged core; add `EXPIRED` for unmatched orders (Section 5) | One new terminal state | Additive migration |
| Dispatch | Human-mediated via Coordinator; no algorithm; Eligibility undefined (FACT) | Same human-mediated core for V1 + an explicit, documented Eligibility gate | Eligibility gate (Section 6) | New Driver Management capability + new contract |
| Assignment | Real CREATED→ACCEPTED→ARRIVED→IN_PROGRESS→COMPLETED (FACT) | Split: Assignment ends at ACCEPTED/DECLINED; ride-progress states move to Trip | Structural split (Section 10) | See Section 24 — additive, dual-write period |
| Trip | Does not exist as its own context; ride-progress lives on Assignment (FACT) | New bounded context owning ARRIVED/IN_PROGRESS/COMPLETED and any future GPS/route data | Entirely new context | Section 24 |
| Payment | Does not exist at all (FACT) | New Payment context, `statedPrice` as its seam | Entirely new | Section 11 |
| Rating | Does not exist at all (FACT) | New, non-numeric-first Rating context | Entirely new | Section 12 |
| Reputation | Does not exist at all (FACT) | Derived, aggregate view over Rating + relationship history | Entirely new | Depends on Rating |
| Communication | ADR-059: no contact disclosure in pilot (FACT) | Same restraint; add only status notifications, not P2P messaging, for V1 | Notification delivery mechanism | Section 15 |
| Network | Built, undeployed, unused by Taxi (FACT, Task 6 Section 5/15) | Taxi consumes exactly one read (a person's driver association) if/when useful; otherwise stays independent | Integration seam undecided | Section 14 — deliberately minimal |
| Operations | Coordinator + Owner Control Center, shared static credential (FACT) | Same, + individuated operator accounts (lower priority) | Per-operator auth | Section 16, lower priority |
| Notifications | Ratified as a module concept, unbuilt (FACT, `MODULE_STRUCTURE.md`) | Minimal status-notification capability for V1 | Entirely new, deliberately thin | Section 15 |
| Event architecture | Outbox + RabbitMQ, 3 of 6 modules, DLQs, some idempotency (FACT) | Same pattern extended to Trip/Payment/Rating | Extend existing pattern, don't replace it | Section 15/24 |
| Persistence | Flyway/Postgres per module, no cross-module FKs, no status CHECK constraints (FACT) | Same pattern + status CHECK constraints | Constraint gap (Task 6 Section 6) | Additive migration, no behavior change |
| API layer | REST, module-owned, no API gateway (FACT) | Same for V1 — no gateway needed at this scale | None material | — |
| Authentication | Real, phone+password+session (FACT) | Same + verification | Section 13 | Additive |
| Authorization | Per-module `Bearer`-token check + separate static `OwnerCredentialGate` for ops (FACT) | Same core, formalize a driver/passenger role distinction | Minor | Additive |
| Observability | Owner Control Center health polling + `EventFeed` (FACT) | Same + Trip/Payment health surfaced | Extend existing dashboard | Additive |
| Monetization | None (FACT) | Boundary exists (Payment context), no specific model chosen | Entirely open by design | Section 22 |

## 4. Bounded Contexts

**Design stance (DECISION):** grow from 6 real modules to **9**, not more. Four candidates from the prompt's own list are deliberately *not* separate contexts for V1 (Section 23 explains why): **Profile** stays inside Driver Management/Passenger Experience rather than becoming independent; **Notifications** stays a thin capability inside whichever context needs it (initially Trip) rather than its own deployable module; **Reputation** is a *view*, not a separate context, computed from Rating; **Operations** stays the existing Coordinator/Owner Control Center pattern, not a new backend module.

| Context | Responsibility | Owns | Public API | Publishes | Consumes | Must NOT own |
|---|---|---|---|---|---|---|
| **Identity** (existing) | Auth, session, phone verification | Identity, Credential, Session, VerificationChallenge | register/login/me/associate-driver/**verify-phone** (new) | `IdentityVerified` (new) | nothing | driver/passenger business data |
| **Driver Management** (existing) | A driver's standing, availability, vehicle, eligibility | Driver, Availability, Vehicle (new), Eligibility (new) | declare-availability, driver query, **vehicle CRUD** (new), **eligibility query** (new, internal) | `DriverAvailabilityChanged` (existing), `DriverEligibilityChanged` (new) | nothing | Order, Assignment, Trip, Rating |
| **Passenger Experience** (existing) | Passenger representation, Circle of Trust | Connection (`isPrimary`) | connection CRUD | none today | nothing | Order, driver data |
| **Order Management** (existing) | An order's demand-side lifecycle | Order | submit/cancel/query | `OrderSubmitted`, `OrderCancelled`, `OrderCompleted` | `AssignmentAccepted`, `AssignmentCompleted` (existing) | assignment or ride facts |
| **Dispatch** (existing, scope narrowed) | The match decision + eligibility gate consumption | Proposal, Assignment (ends at ACCEPTED/DECLINED) | propose/accept/decline/withdraw, assign/accept | `OrderProposed`, `ProposalAccepted/Declined/Lapsed/Withdrawn`, `AssignmentAccepted` | `DriverAvailabilityChanged`, `OrderCancelled`, (new, optional) `DriverEligibilityChanged` | ride-progress facts (moves to Trip), payment, rating |
| **Trip** (NEW) | The actual transportation service in progress | Trip (ARRIVED/IN_PROGRESS/COMPLETED, future route/GPS) | arrive/start/complete, trip query | `TripStarted`, `TripCompleted` (renamed from today's `AssignmentArrived`/`AssignmentStarted`/`AssignmentCompleted`) | `AssignmentAccepted` | the match decision, payment |
| **Payment** (NEW) | The platform's own record of payment facts | PaymentIntent, Payment, Payout | create-intent, authorize, capture, refund, query | `PaymentAuthorized`, `PaymentCaptured`, `PaymentFailed`, `PayoutRecorded` | `TripCompleted` | external settlement/PSP internals, Order/Trip facts |
| **Rating** (NEW) | Post-trip rating + aggregate reputation view | Rating, ReputationSnapshot | submit-rating, query-reputation | `RatingSubmitted`, `ReputationUpdated` | `TripCompleted` | trip or payment facts |
| **Network** (existing, unchanged scope) | Person/Profile/Connection/Invitation beyond Taxi's own | Person, Connection, Invitation | as today | as today | none | Taxi's own Driver/Passenger/Order data |

**Dependency direction (FACT of existing convention, preserved):** every arrow above flows only through declared REST calls or published events — never shared storage, matching `MODULE_STRUCTURE.md` Section 5 exactly. Trip and Payment both depend on events already flowing through Dispatch/Order Management today; neither requires a new architectural mechanism, only new participants in the existing one.

## 5. Passenger Lifecycle

| Step | Context | State | API/Event | Failure cases | User-visible result | Status |
|---|---|---|---|---|---|---|
| Registration | Identity | Identity created | `POST /v1/identities/register` | Phone already registered (409) | Account exists | **CURRENT** |
| Authentication | Identity | Session issued | `POST /v1/identities/login` | Undifferentiated 401 | Logged in | **CURRENT** |
| Identity verification | Identity | Phone verified/unverified | `POST /v1/identities/verify-phone` (new) | Code expired, wrong code | "Verified" badge | **V1 REQUIRED** |
| Profile | Passenger Experience | name set | existing, informal | — | Name shown to driver | **CURRENT** |
| Ride request | Order Management | `SUBMITTED` | `POST /v1/orders` | Validation error | Order confirmed | **CURRENT** |
| Matching | Dispatch | Proposal `OPEN` | Coordinator action (Section 9) | No driver available → order sits, or `EXPIRED` (new, Section 5 state machine) | "Ждём ответа водителя" (already real UI text, this session's own Task 7) | **CURRENT** (human-mediated) |
| Offer/selection | — | — | — | — | — | **FUTURE** — no passenger-facing driver choice exists or is proposed; matching stays platform/Coordinator-driven, consistent with "not BlaBlaCar" |
| Driver assignment | Dispatch → Trip | Assignment `ACCEPTED` | `AssignmentAccepted` | Driver declines → re-propose | "Водитель принял ваш заказ" | **CURRENT** |
| Driver arrival | Trip (moved from Assignment) | Trip `ARRIVED` | `TripArrived` (renamed) | — | "Водитель приехал" | **CURRENT**, relocated |
| Trip start | Trip | Trip `IN_PROGRESS` | `TripStarted` | — | "Поездка началась" | **CURRENT**, relocated |
| Trip progress | Trip | `IN_PROGRESS` | — | Connectivity loss (Section 22) | Status polling continues | **CURRENT** (status only; no live GPS) |
| Trip completion | Trip → Order | Trip `COMPLETED` → Order `COMPLETED` | `TripCompleted` → Order Management | — | "Поездка завершена" | **CURRENT**, relocated |
| Payment | Payment | `PaymentCaptured` | `POST /v1/payments/intents` (new) | Card declined, cash unconfirmed | Receipt/confirmation | **V1 REQUIRED** |
| Rating | Rating | `RatingSubmitted` | `POST /v1/ratings` (new) | Duplicate submission (idempotent, ignored) | "Спасибо за отзыв" | **V1 REQUIRED** |
| History | Order Management (read) | — | `GET /v1/orders?passengerReference=` (already exists per Task 6's own finding) | — | Past orders list | **V1 REQUIRED** (a screen, not new backend) |
| Repeat relationship | Passenger Experience | `Connection.isPrimary` | existing | — | Circle of Trust | **CURRENT** |

## 6. Driver Lifecycle

| Step | Context | Notes | Status |
|---|---|---|---|
| Registration | Identity | Same account mechanism as passenger — no separate driver-only auth (FACT) | **CURRENT** |
| Authentication | Identity | — | **CURRENT** |
| Verification | Identity + Driver Management | Phone verification (Identity) + a driver-specific verification step (e.g. license/document check) | **V1 REQUIRED** (phone), **FUTURE** (document verification — no ratified requirement exists for what "driver verification" must check) |
| Profile | Driver Management | Name today; photo is **FUTURE** (BlaBlaCar-derived "clear participant profile" principle, Section 12) | **CURRENT** (name only) |
| Vehicle | Driver Management (new) | Does not exist; optional at first (a driver can operate without declaring one, if PIOS ever supports non-vehicle services — Section 15/19 network extensibility) | **FUTURE** |
| Availability | Driver Management | `AVAILABLE`/`UNAVAILABLE`, real | **CURRENT** |
| Incoming request | Dispatch | Proposal `OPEN`, real | **CURRENT** |
| Acceptance/rejection | Dispatch | Real, `ACCEPTED`/`DECLINED` | **CURRENT** |
| Assignment | Dispatch | Real | **CURRENT** |
| Navigation/trip | Trip (new) | No navigation exists or is proposed (a driver uses their own external map app — out of PIOS's own architectural scope, same as today) | **CURRENT** for trip-state, **not applicable** for navigation |
| Completion | Trip | — | **CURRENT**, relocated |
| Payment | Payment (new) | Driver sees `statedPrice` today; a real payment capture is new | **V1 REQUIRED** |
| Earnings | Payment (read) | No earnings view exists today (Task 6 Section 11) | **V1 REQUIRED** |
| Reputation | Rating (new) | — | **V1 REQUIRED**, non-numeric per this codebase's own established constraint (this session's `DriverTrustIndicator` work) |
| Repeat passenger relationship | Passenger Experience | Circle of Trust already gives this real structural support | **CURRENT** — this is PIOS's actual differentiator already built |

**Driver autonomy (explicit, per this task's own instruction):** nothing in this target architecture models the driver as anonymous supply. Availability remains fully self-declared (unchanged — `PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md`'s own ratified boundary condition), Eligibility (Section 6 below) is a floor, never a ranking, and Rating (Section 12) is explicitly designed to avoid becoming a leaderboard.

## 7. Order State Machine

**Current, as implemented (FACT):** `SUBMITTED → COMPLETED` or `SUBMITTED → CANCELLED`. `Order.kt`'s own KDoc is explicit that assigned/accepted/ride states are deliberately excluded from Order.

**Target (DECISION):** add exactly one state — **`EXPIRED`** — for an order that never found a driver within an operationally-reasonable window (relevant to Section 20's low-liquidity/small-town case, where "no driver responded" is a real, frequent, non-error outcome, not a system failure). No other new Order state is proposed; ride-progress states correctly stay off Order, on Trip.

| Transition | Actor | Command | Event | Persistence | Invalid-transition behavior |
|---|---|---|---|---|---|
| — → SUBMITTED | Passenger | `POST /v1/orders` | `OrderSubmitted` | insert | n/a (creation) |
| SUBMITTED → CANCELLED | Passenger or Coordinator | `POST /v1/orders/{id}/cancel` | `OrderCancelled` | update | rejected if not SUBMITTED (FACT, `Order.cancel()`'s own `check()`) |
| SUBMITTED → COMPLETED | Order Management, triggered by `TripCompleted` | (event-driven, no direct command) | `OrderCompleted` | update | rejected if not SUBMITTED (FACT, `Order.complete()`'s own `check()`) |
| SUBMITTED → EXPIRED (new) | System, time-based | none (a scheduled sweep, mirroring `ProposalLapsed`'s own existing timeout pattern) | `OrderExpired` (new) | update | only from SUBMITTED with zero open/accepted Proposals |

This keeps `Order`'s own invariant exactly as ratified today — "an order has exactly one current status... a completed order cannot return to an active state" (FACT, `Order.kt`) — `EXPIRED` is simply a second terminal outcome alongside `CANCELLED`, distinguished for reporting/UX reasons (a passenger sees "no driver was available," not "you cancelled this").

## 8. Dispatch Architecture

**Current (FACT):** a human Coordinator, credential-gated, manually matches orders to drivers via REST — confirmed directly from `Coordinator.tsx`'s own imports and header comment (Task 6 Section 5). No algorithm exists; ADR-034 explicitly leaves Assignment Policy's algorithm undecided.

**Target for V1 (DECISION):** **keep the human-mediated model as the primary mechanism.** This is not a placeholder to be replaced as soon as possible — for PIOS's actual current scale (Section 20: few drivers, low volume, personal relationships already doing most of the "matching" work via Circle of Trust and the personal-invitation link) a human Coordinator is *more* consistent with Fair Dispatch's own ratified priority (transparency, explainability) than an opaque early algorithm would be. Layer three additive capabilities on top, none of which requires replacing the Coordinator:

1. **Eligibility gate (new, Driver Management-owned)** — per `PRODUCT_DECISION_ELIGIBILITY_MINIMUM_TRUST_BOUNDARY.md`'s own already-derived conclusion (Section 4 of that document: Driver Management owns it exclusively), implement the "standing/participation" gate that document found ratified-in-concept but never built. A binary floor only — "may this driver be considered at all" — never a ranking. Consumed by Dispatch as a candidate filter, the same pattern already proven for `DriverAvailabilityChanged`.
2. **Personal Client priority signal (optional, surfaced not enforced)** — when an order originates from a Personal-Client-directed source (already a distinct `OrderOrigin`, FACT), the Coordinator's own UI should surface the relevant driver first — a UI affordance, not a new backend rule, since `PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md` Section 4 explicitly leaves the priority *rule* undecided. This document does not decide it either; it only notes where the seam is.
3. **Scheduled rides** — already partially real (`requestedPickupAt`, ADR-058, FACT). Target: the Coordinator's own queue should sort/surface by requested time, not just submission time — a read-side UI change, not a new domain concept.
4. **Low-liquidity fallback** — when zero drivers are eligible+available, the order should reach `EXPIRED` (Section 7) with a clear passenger-visible reason, rather than silently waiting forever. This directly serves Section 20 (small-town/monotown).

**Explicitly not proposed for V1 (FUTURE):** any scoring/ranking algorithm, geographic-proximity-based auto-matching (no location data exists anywhere in the domain model today — FACT), or removing the human Coordinator. `ADR-034`'s own abstraction (Assignment Policy) is the correct future seam for an algorithm; this document does not choose one, consistent with the task's own instruction.

## 9. OrderSubmitted Decision

**The question, restated precisely (FACT, from `PIOS_POST_REMEDIATION_BASELINE.md` Section 6/7):** `OrderSubmitted` is published via the transactional outbox to RabbitMQ's `order-management.events` exchange, but Dispatch's own topology binds only the `order.cancelled` routing key — never `order.submitted`. `EVENT_CATALOG.md` itself independently confirms this is not an oversight: Section 6 states `OrderSubmitted`/`OrderCancelled`/`OrderCompleted` are, before Section 9's exceptions, treated the same as the never-published Proposal events — real, but not (yet) "business events" under ADR-003, because no other domain currently relies on them.

**Option A — Dispatch consumes `OrderSubmitted`, matching begins automatically.**
- *Consistency*: eventual — a brief window exists between order submission and Dispatch's queue processing it.
- *Failure modes*: a lost/delayed message means an order silently never enters the matching pool with no passenger-visible signal unless a separate timeout mechanism exists; RabbitMQ downtime (already possible today per README's own note that "Spring Boot does not fail to start if RabbitMQ is unreachable") would mean orders accumulate with literally no path to a driver until the broker recovers.
- *Retry/idempotency*: needs a new idempotency table in Dispatch (following the exact pattern already proven for `AssignmentAccepted`), plus a redelivery/DLQ story for a poison message.
- *Observability*: harder — "why hasn't this order been matched" becomes a question about queue depth and consumer lag, not a directly queryable state.

**Option B — Coordinator/passenger-facing REST call triggers matching (today's actual mechanism, made explicit).**
- *Consistency*: immediate, synchronous — the Coordinator sees the order the moment they query it (already true today, FACT).
- *Failure modes*: none beyond ordinary REST failure (timeout, 5xx) — directly visible to the caller, retriable the same way any REST call is.
- *Retry/idempotency*: none needed beyond what already exists — no new event, no new consumer, no new DLQ.
- *Observability*: excellent — the Coordinator's own `todayData.ts` fan-out (FACT, Task 6 Section 11) already gives full visibility into unmatched orders today.

**Hybrid, considered and rejected for V1 (DECISION):** Dispatch could consume `OrderSubmitted` *only* to update a read-side "unmatched orders" projection (never to auto-start matching), leaving the Coordinator's own action as the actual trigger. This was considered because it would give Dispatch its own authoritative view of unmatched orders instead of depending on Order Management's REST API at query time. **Rejected for V1** because it duplicates data Order Management already owns and queries fine today (Coordinator's `todayData.ts` fan-out already works), and the added consistency/idempotency machinery isn't justified by any V1 requirement — this is exactly the kind of "interesting but unjustified" complexity Section 23 asks this document to reject.

**Recommendation (DECISION): Option B.** Keep `OrderSubmitted` as a published, outbox-backed event — it remains genuinely useful as an integration point for Notifications (Section 15) and Analytics/Operations dashboards (Section 16) — but Dispatch's matching workflow continues to be triggered by an explicit act (a Coordinator's own REST call today; potentially a passenger-facing "find me a driver" action in a future, still-manual-Coordinator-mediated V1.1), never by passive event consumption. This preserves the exact mechanism already working in production, requires zero new idempotency/DLQ machinery, and is *more* consistent with Fair Dispatch's own transparency principle (Section 2) — a human explicitly acting on an order is more explainable than an automatic queue consumer.

**Migration path:** none required — this is a decision to formalize existing behavior, not change it. The one recommended addition is documentation-only: state explicitly (in a future ADR, Section 29) that `OrderSubmitted`'s lack of a Dispatch consumer is deliberate architecture, not a defect, so a future engineer doesn't "fix" it by wiring one up without this analysis.

## 10. Trip Domain

**The conceptual distinction, evaluated (per this task's own instruction not to assume it's correct):**

- **Order = the passenger's request.** FACT: this is already exactly what `Order.kt` models — demand only, explicitly excluding ride states by its own KDoc.
- **Assignment = the match.** FACT-as-designed, but **not FACT-as-implemented**: `AssignmentStatus` today is `CREATED, ACCEPTED, ARRIVED, IN_PROGRESS, COMPLETED` — the match decision (`CREATED`→`ACCEPTED`) and the ride-in-progress facts (`ARRIVED`→`IN_PROGRESS`→`COMPLETED`) are currently *one* aggregate. `Assignment.kt`'s own KDoc already shows awareness of this tension: it notes ride-progress states were placed on Assignment "rather than... Order" per ADR-040's own evidence that `Order.kt` deliberately excludes them — but never considered a third option (a dedicated Trip aggregate) because ADR-040 predates this task's own framing.
- **Trip = the actual transportation service.** **Does not exist today** (FACT, confirmed absent — no `Trip.kt`, no `trips` table anywhere).

**Evaluation of the proposed three-way split:** correct, and the current code's own comments (`Assignment.kt`, `Order.kt`) already independently converge on wanting this boundary — they just drew it one aggregate short. The concrete evidence: `Assignment`'s own KDoc for `arrivedAt`/`startedAt`/`completedAt` cites ADR-043 (Owner Control Center — Observation Boundary) as their justification, a document about *operational observability*, not about the match decision itself — a strong signal these three timestamps belong to a different concern than `accept()`'s own match-decision timestamp.

**Recommendation (DECISION):** introduce **Trip** as its own bounded context, owned by... a genuinely open question this document does not resolve unilaterally:

- **Option 1**: Trip lives inside Dispatch (same module, new aggregate) — smallest migration, since Dispatch already has the RabbitMQ/outbox plumbing and already owns the driver/order references needed.
- **Option 2**: Trip becomes its own deployable module — cleanest bounded-context purity, but Section 23 (architectural simplicity) argues against a 7th deployable module for what is, functionally, three status fields and three timestamps at V1's actual scale.

**This document recommends Option 1 for V1** (DECISION): Trip as a new aggregate *inside* Dispatch's existing module, not a new deployable service — satisfying the conceptual separation (a distinct aggregate, its own repository, its own events) without the operational cost of a 7th Windows service, database, and RabbitMQ topology for something this small today. Re-evaluate as a candidate for extraction (Option 2) only if Trip grows real scope (live GPS, route data, multi-leg trips) that would genuinely benefit from independent scaling or deployment — not preemptively.

**Ownership boundary, precisely:**
- Dispatch/Assignment owns: the match itself, ending at `ACCEPTED`/`DECLINED`.
- Trip (new aggregate, same module) owns: `ARRIVED`, `IN_PROGRESS`, `COMPLETED`, and any future route/GPS/multi-stop data.
- Order Management is unaffected: it already consumes `AssignmentCompleted`; the target simply renames this to `TripCompleted` at the point of migration (Section 24 — an additive, non-breaking rename via event versioning, not a hard cutover).

## 11. Payment Architecture

**Current (FACT):** none. `statedPrice`/`statedEtaMinutes` (ADR-042/057) are the only monetary-adjacent facts in the system — driver-typed, passenger-visible, purely informational, never read or acted on by any code beyond display (Task 6 Section 10/16).

**Target minimum boundary (DECISION):**

```
PaymentIntent          -- created when a trip's price becomes final (Trip COMPLETED, or
                           a driver's stated price at proposal time, TBD by a future
                           product decision -- this document does not choose)
  ├─ amount, currency
  ├─ status: CREATED → AUTHORIZED → CAPTURED
  │                  → FAILED
  │                  → (CAPTURED →) REFUNDED
  └─ method: CASH | CARD (future) | ...

Payment                -- the settled record once CAPTURED (or the cash-acknowledgment
                           record for CASH), immutable once written

Payout                 -- driver-facing: a record that a Payment's driver-owed portion
                           has been (or will be) transferred to the driver -- explicitly
                           NOT the same aggregate as Payment, since a platform fee (if any,
                           Section 22) makes Payout ≠ Payment
```

**Why isolated from Order/Trip (DECISION, directly per this task's instruction):** Payment must tolerate retries and asynchronous processing that Order/Trip's own synchronous command model does not need to. A `PaymentIntent` is created from a `TripCompleted` event (async, at-least-once, idempotent on `tripId`) rather than a direct synchronous call from Trip — this means a payment provider outage never blocks trip completion from being recorded, and a payment can be retried independently of the trip it's for.

**Cash payment (relevant — ASSUMPTION, since PIOS's actual pilot likely involves cash today, though this was not directly verified in code):** modeled as a real `PaymentIntent`/`method: CASH` with a `CAPTURED` status set by a driver's own confirmation action (not a PSP callback) — this keeps cash and card payments in the same state machine and the same reporting surface (Owner Control Center), rather than cash being an invisible, unrecorded side-channel the way it implicitly is today.

**Driver payout/earnings:** `Payout` as a separate, explicitly-not-yet-designed-in-detail record — this document defines only that it must exist as a concept distinct from `Payment`, not its mechanism (bank transfer, in-app balance, etc. — all **FUTURE**).

**V1 scope (DECISION):** implement `PaymentIntent`/`Payment` with `CASH` as the only real method for V1 — this gives the architecture (state machine, event contract, Owner Control Center visibility) without requiring a payment-provider integration decision, consistent with this task's own instruction not to select a provider.

## 12. Rating + Reputation Architecture

**Separated explicitly, per this task's instruction:**

- **Rating** = a single, atomic, post-trip data point: one passenger's assessment of one driver for one trip (or the reverse direction). An event, essentially — immutable once submitted.
- **Reputation** = a derived, aggregate *view* over a driver's (or passenger's) accumulated Ratings and relationship history — never its own independently-writable aggregate, always computed.

**This session's own already-established, structurally-enforced constraint carries forward unchanged (FACT, `DriverTrustIndicator`'s own design decision doc): no numeric rating, count, rank, or leaderboard anywhere in any driver-facing UI.** This is the single hardest constraint Rating's architecture must satisfy — it directly rules out the most common rating-system shape (a 1–5 star average) as a *displayed* artifact, though it does not rule out a numeric value existing in the backend for internal moderation/aggregation purposes, only its direct exposure as a comparison/ranking mechanism.

**Target model (DECISION):**

```
Rating
  ├─ tripId, fromPersonId, toPersonId, direction (PASSENGER_TO_DRIVER | DRIVER_TO_PASSENGER)
  ├─ signal: a small, closed set of qualitative tags (e.g. "on time," "friendly,"
  │          "as described") -- NOT a 1-5 star scale, consistent with the no-numeric-
  │          ranking constraint above
  ├─ freeTextReview: optional, moderated (Section 12 below)
  └─ submittedAt

ReputationSnapshot (derived, recomputed, never directly written)
  ├─ personId
  ├─ tripCount (a count, not a score -- "has done N trips with PIOS," a factual
  │             volume signal, distinct from a quality ranking)
  ├─ recentSignalDistribution (which qualitative tags recur, not an averaged number)
  └─ lastUpdated
```

**Passenger → Driver and Driver → Passenger, both real (DECISION):** BlaBlaCar's own core trust insight — bidirectional rating — is directly applicable here without contradicting "not BlaBlaCar" (Section 2's own scoping): a driver rating a passenger is a legitimate safety/trust signal for a platform where drivers are running their own business, not just a passenger convenience feature.

**Moderation (DECISION):** free-text review requires a moderation queue owned by Operations (Section 16) before publication — consistent with `ADR-047`'s already-ratified "Destructive Action Authorization" pattern (a human-gated action journal), applied to review publication rather than data deletion.

**Anti-abuse (DECISION, minimum for V1):** a Rating may only be submitted once per `(tripId, direction)` pair (idempotent — a resubmission is rejected, not appended), and only after `TripCompleted` — closing the two most obvious abuse vectors (spamming ratings, rating a trip that never happened) without needing a sophisticated fraud model at V1.

**Reusability for future PIOS Network relationships (per this task's instruction):** `Rating`'s `fromPersonId`/`toPersonId` are deliberately generic person references, not `driverId`/`passengerId` — so the same shape could, in a future vertical, rate any two participants in any PIOS Network relationship, without redesigning the aggregate. This is a **FUTURE** capability this document only avoids foreclosing, not one it builds now.

## 13. Identity/Trust Architecture

**Separated, per this task's instruction:**

- **Identity** = who someone claims to be (phone, password, session) — real today (FACT, Section 9 of `PIOS_POST_REMEDIATION_BASELINE.md`).
- **Trust/Reputation** = whether that claim, and subsequent behavior, should be believed/relied upon — does not exist today beyond the binary fact of having an Identity at all.

**Explicitly not pretending verification exists (per this task's direct instruction):** `Device.kt`/`VerificationChallenge.kt` are real code, but their own KDoc states outright that nothing constructs them outside documentation-only tests and no SMS/carrier/Telegram integration exists anywhere (FACT, re-confirmed this task). Today, **any phone number can be registered without proof of ownership.**

**Target (DECISION), using the shapes already drawn:**

| Layer | Mechanism | Status |
|---|---|---|
| Phone verification | SMS OTP via `VerificationChallenge` (already-modeled `VerificationMethod.SMS_OTP`) | **V1 REQUIRED** |
| Account identity | Existing Identity/session mechanism | **CURRENT**, unchanged |
| Driver verification | A minimum-document-check step (license, ID) — no ratified requirement for exactly what to check | **FUTURE** — this document names the seam (an `Eligibility`-adjacent fact Driver Management would own, Section 6/8), not the checklist |
| Vehicle verification | Depends on Vehicle existing at all (Section 3) | **FUTURE** |
| Profile completeness | A derived read (has name? has photo? has vehicle?) | **V1 REQUIRED**, cheap — a query, not new state |
| Reputation | Rating aggregate (Section 12) | **V1 REQUIRED** |
| Safety signals | Section 22's failure-mode handling (driver disappears, etc.) | **PARTIAL FOR V1** — operational (Coordinator intervention), not automated |
| Verification status | A new field on Identity: `phoneVerified: Boolean`, surfaced (not gamified — a badge, not a score) via a component like this session's own `DriverTrustIndicator` | **V1 REQUIRED** |

**Why verification must precede trust/reputation (restated from Task 6's own recommended sequence, reaffirmed here):** a Rating attached to an unverified Identity is a rating of an unverified claim — the entire point of Rating (Section 12) is undermined if the participant being rated could be a different phone number tomorrow with no continuity. This is the single hardest dependency in this document's whole implementation order (Section 25).

## 14. Network Architecture

**Current (FACT, Task 6 Section 5/15):** Network Management (Person/Profile/Connection/Invitation) is fully built, has real in-module foreign keys, but is deployed nowhere and used by no real product screen — only `NetworkTest.tsx`, an explicitly-labeled internal harness.

**Critical requirement restated: Taxi must not depend on the complete future Network system to launch.** This is already true today by construction — Taxi's own passenger/driver relationship model (Circle of Trust, `isPrimary`) lives entirely inside Passenger Experience, with zero dependency on Network Management (FACT — no code reference from any Taxi-facing module into `network-management`). This document recommends **preserving that independence explicitly, as a decision, not just as an accident of sequencing.**

**Minimal Network capability required for Taxi V1 (DECISION): none.** Taxi V1 should ship with zero runtime dependency on Network Management. Circle of Trust already provides the "passenger recognizes a specific driver" relationship Taxi actually needs.

**Optional Network capabilities (DECISION, not required, but a real integration seam worth naming):** if/when Network Management is deployed, a single, narrow, read-only integration — Taxi asking "does this phone number already correspond to a known Person in the Network?" at registration time, to reduce duplicate-identity creation across verticals — would be additive and revocable, never a hard dependency.

**Future capabilities (FUTURE):** a driver's Taxi reputation (Section 12) becoming visible to a *different* future PIOS vertical the same driver participates in, via Network's own Person/Connection model — exactly the extensibility this task asks to be preserved, and exactly why Rating's schema (Section 12) already uses generic `personId` references instead of Taxi-specific ids.

**Preserving driver-passenger relationships beyond one transaction (per this task's explicit requirement):** already real today via Circle of Trust + the personal-invitation link — this is not a gap this document needs to close, only one it must not accidentally break while adding Trip/Payment/Rating. None of the new contexts in this document reference or require Network Management, so this is structurally safe.

## 15. Communication

| Need | Ownership (DECISION) | V1 scope |
|---|---|---|
| Passenger-driver communication | **Not built** — ADR-059 ("No Disclosure in the Pilot, Option C") already rules out direct contact-sharing (FACT); this document does not revisit that ratified decision | **FUTURE**, and only after a fresh product decision explicitly supersedes ADR-059 |
| Ride status notifications | New, thin **Notifications** capability — a consumer of `OrderSubmitted`/`AssignmentAccepted`/`TripStarted`/`TripCompleted` events, **not** a separate deployable module (Section 4's own "must not own" list keeps this a capability, not a context) | **V1 REQUIRED** — the actual delivery channel (push/SMS/in-app poll) is **FUTURE**; V1 can start with in-app status only (already real — this is what `RideRequest.tsx`'s own status polling already does today, FACT) |
| Cancellation notifications | Same Notifications capability, consumes `OrderCancelled` | **V1 REQUIRED**, same in-app-only scope |
| Payment notifications | Same capability, consumes `PaymentCaptured`/`PaymentFailed` | **V1 REQUIRED** once Payment exists |
| Safety notifications | Operations-triggered (Section 16), not automated | **FUTURE** |

**This is exactly the live consumer `OrderSubmitted` was missing (Section 9)** — the recommendation there was that Dispatch should not consume it; Notifications is the context that legitimately should, once built. This resolves the "published but unconsumed" finding from Task 6 without contradicting Section 9's own recommendation.

## 16. Operations

**Current (FACT):** Coordinator (manual dispatch) + Owner Control Center (health, daily metrics, AI analyst, event feed), both behind one shared static credential.

**Target Platform/Operations bounded context (DECISION) — explicit inclusions:**

| Capability | Automated or human? | Status |
|---|---|---|
| Order monitoring | Automated surfacing (already real, `todayData.ts`), human review | **CURRENT** |
| Driver monitoring | Automated (availability/health), human review | **CURRENT** |
| Dispatch monitoring | Automated (proposal/assignment feed), human action (the Coordinator's own matching) | **CURRENT** |
| Cancellations | Passenger/Coordinator-triggered, automated event propagation | **CURRENT** |
| Disputes | **Does not exist today** — no mechanism for a passenger/driver to flag a problem | **V1 REQUIRED**, minimally: a flagged-trip flow feeding an Operations queue |
| Suspicious activity | **Does not exist today** | **FUTURE** — no fraud/anomaly model proposed here |
| Payment problems | New, once Payment exists — failed/disputed payments surfaced to Operations | **V1 REQUIRED** (alongside Payment) |
| Support | No dedicated tooling today beyond direct Coordinator/owner access | **FUTURE** |
| Operational intervention | Real today (Coordinator can act directly on orders/proposals) | **CURRENT** |
| Audit trail | Partial — `ADR-047`'s "Action Journal" pattern exists for destructive admin actions; no general audit log for ordinary lifecycle events beyond the event feed itself | **PARTIAL**, **V1 REQUIRED** to extend to Payment/Rating moderation actions |

**Individuated operator accounts** (vs. today's one shared credential) is real but explicitly **lower priority** (Section 20 of the gap table) — it doesn't block passenger/driver/payment/rating value, so it's sequenced late (Section 25).

## 17. API/Event Architecture

**Principle (DECISION, directly answering this task's instruction not to force events everywhere):**

- **Synchronous REST** where a caller needs an immediate, consistent answer to act on: order submission, proposal accept/decline, payment intent creation, rating submission, every Coordinator action. All of these are already REST today (FACT) and should stay REST — none benefits from asynchrony, and forcing them through events would only add latency and a new failure mode (Section 22) for no consistency gain.
- **Asynchronous events** where the *producer* doesn't need to know or care who (if anyone) reacts, and eventual consistency is genuinely fine: `DriverAvailabilityChanged` → Dispatch's own read-side cache (already this shape, FACT), `AssignmentAccepted`/`TripCompleted` → Order Management's status sync (already this shape), `TripCompleted` → Payment intent creation (new, same shape), `TripCompleted` → Notifications (new, same shape).
- **Commands vs. queries vs. events, mapped to what already exists:** every `POST`/state-changing REST call in this system is a Command; every `GET` is a Query; only the cross-module facts (Section 6 of `PIOS_POST_REMEDIATION_BASELINE.md`) are Events. This document does not introduce a CQRS read-model split beyond what already exists (e.g. Dispatch's own `DriverAvailabilityRecord` projection) — that pattern is proven and should extend to Trip's own read of `AssignmentAccepted`, not be replaced with something more elaborate.

| Workflow | Sync boundary | Async boundary | Source of truth | Idempotency needed? | Retry behavior |
|---|---|---|---|---|---|
| Order submission | Passenger → Order Mgmt REST | Order Mgmt → (Notifications, future) | Order Mgmt | No (client-side dedup, not server) | Client retries on timeout; server-side `OrderId` is caller-independent (server-generated) so a duplicate POST creates a duplicate order today — **a real, pre-existing gap**, not introduced by this design (FUTURE fix: idempotency key on submission) |
| Matching | Coordinator → Dispatch REST | none (Section 9) | Dispatch | REST-level only | Manual retry (Coordinator re-attempts) |
| Assignment accept | Driver → Dispatch REST | Dispatch → Order Mgmt (event) | Dispatch (for match), Order Mgmt (for order status) | Yes — already implemented (idempotency table, FACT) | Outbox relay retries automatically |
| Trip progress | Driver → Trip REST | Trip → Notifications (event), Trip → Payment (event, at completion) | Trip | Yes, same outbox pattern | Outbox relay |
| Payment | Payment → PSP (future, sync call) or driver-confirmed (cash, sync) | Payment → Notifications, Payment → Operations (on failure) | Payment | Yes — `PaymentIntent` keyed on `tripId`, required | Outbox relay + PSP's own retry semantics (future) |
| Rating | Passenger/Driver → Rating REST | Rating → (future) Reputation recompute | Rating | Yes — one per `(tripId, direction)` | Client retry safe (idempotent reject) |

## 18. Event Vocabulary

**Starting point (FACT):** `EVENT_CATALOG.md`'s existing vocabulary is already well-designed and largely correct — this document does not replace it, it extends it. The prompt's own suggested list is evaluated against it below.

| Event (prompt's list) | Verdict | Target name / status |
|---|---|---|
| `OrderSubmitted` | Keep, unchanged | **CURRENT**, no live consumer by design (Section 9) |
| `OrderMatched` | Not adopted — no single "matched" moment exists distinct from `ProposalAccepted`/`AssignmentAccepted`, which already cover this precisely | superseded by existing `AssignmentAccepted` |
| `DriverOfferCreated` | Renamed match to existing `OrderProposed` | **CURRENT**, no rename needed |
| `DriverAccepted` | Renamed match to existing `ProposalAccepted` | **CURRENT** |
| `DriverRejected` | Renamed match to existing `ProposalDeclined` | **CURRENT** |
| `AssignmentCreated` | Not currently a published event (Assignment creation is synchronous, in-process with Proposal acceptance) — no V1 need identified to change this | not proposed |
| `DriverArrived` | Rename of existing `AssignmentArrived` → **`TripArrived`** (Section 10's Trip split) | **V1 REQUIRED** (rename) |
| `TripStarted` | Rename of existing `AssignmentStarted` → **`TripStarted`** | **V1 REQUIRED** (rename) |
| `TripCompleted` | Rename of existing `AssignmentCompleted` → **`TripCompleted`** | **V1 REQUIRED** (rename) |
| `OrderCancelled` | Keep, unchanged | **CURRENT** |
| `PaymentAuthorized` | New | **V1 REQUIRED** |
| `PaymentCaptured` | New | **V1 REQUIRED** |
| `PaymentFailed` | New | **V1 REQUIRED** |
| `RatingSubmitted` | New | **V1 REQUIRED** |
| `ReputationUpdated` | New — but **derived/internal**, likely never needs to leave Rating's own process boundary (mirrors the existing precedent that Proposal's five events never leave Dispatch's own boundary, FACT) | **V1 REQUIRED internally**, not necessarily a cross-domain business event |
| `OrderExpired` | New (Section 7) | **V1 REQUIRED** |
| `IdentityVerified` | New (Section 13) | **V1 REQUIRED** |
| `PayoutRecorded` | New (Section 11) | **FUTURE** |

**For every new/changed event**, producer/consumer/purpose/ordering/idempotency/domain-vs-integration classification follows the exact template `EVENT_CATALOG.md` Sections 5–9 already use — this document recommends extending that catalog directly (as a documentation task, not this one) rather than starting a parallel vocabulary.

## 19. Data Ownership

| Entity | Owning context | Notes |
|---|---|---|
| Passenger | Passenger Experience | unchanged |
| Driver | Driver Management | unchanged |
| Vehicle | Driver Management (new) | owned by the same context as Driver, not a separate context (Section 3) |
| Order | Order Management | unchanged |
| Assignment (match only) | Dispatch | narrowed scope (Section 10) |
| Trip | Dispatch (new aggregate, Section 10) | ride-progress facts move here |
| Payment | Payment (new) | — |
| Rating | Rating (new) | — |
| Reputation | Rating (new, derived) | never independently written |
| Connection | Passenger Experience (Circle of Trust) **and separately** Network Management (generic connections) | **already a real duplication of concept, not data** — Task 6 Section 19 flagged this; both are legitimate per ADR-037's own explicit "not a merger" language. No violation, but see below. |
| Profile | Driver Management / Passenger Experience (thin, inline) | **not** Network Management's `Profile` — Section 4's own "must not own" boundary |

**Current violations identified (FACT, none found beyond what's already documented):** no shared mutable ownership exists anywhere in the current codebase — every table sampled in Task 6 belongs to exactly one module, cross-module references are plain `TEXT` (no FK), consistent with `MODULE_STRUCTURE.md`'s own rule. The one *naming* collision (two `ConnectionController.kt`s, Section 19 of the baseline) is not a data-ownership violation — each owns a genuinely distinct table in a genuinely distinct database. This document recommends against merging them (that would *create* a violation where none exists today) and instead recommends a documentation clarification (Section 29).

## 20. Small-Town / Monotown Model

**Restated finding (FACT, Task 6 Section 14):** zero product or architecture documents address this today.

**Architecture already implicitly compatible (FACT, no new design needed):**
- No geographic-proximity matching exists (Section 8) — so there is no "dense supply" assumption to remove; the Coordinator model works identically with 3 drivers or 300.
- No live-location/GPS dependency exists anywhere (FACT, confirmed absent from the domain model) — the system has never assumed real-time map data, so it cannot be "broken" by its absence in a low-connectivity area.
- Personal-invitation-link + Circle of Trust (Section 6/14) already model exactly the "driver-owned repeat customer" pattern that matters more in a small town than in a dense city market.

**What this document actively adds for this case (DECISION):**
- `OrderExpired` (Section 7) as an honest, first-class outcome — critical where "no driver responded" is a *routine* result of low supply, not an edge case.
- Eligibility (Section 8) deliberately kept as a floor, never a ranking — a ranking-based dispatch model implicitly assumes enough candidates to rank, which a one-or-two-driver town never has.
- Scheduled rides (already real, ADR-058) surfaced more prominently in the Coordinator's queue (Section 8) — long-lead-time booking matters more where supply is thin.
- Payment's cash-first V1 scope (Section 11) — a monotown/village deployment is exactly where card-payment infrastructure is least likely to be locally available.

**What must remain configurable (DECISION, named, not built here):** any future matching timeout window, any future minimum-Eligibility threshold, and any future notification-delivery channel should be per-deployment configuration, not hardcoded — this document flags the requirement without specifying values, consistent with "do not redesign yet."

## 21. Driver Business Model

**The core differentiator, made architecturally explicit (per this task's own instruction):**

**"Repeat relationship" mechanism (DECISION, formalizing what already exists):** a repeat relationship is the *combination* of three already-real, independently-owned facts, never a single "owned customer list":
1. **Circle of Trust's `isPrimary`** (Passenger Experience) — the passenger's own, passenger-controlled declaration that a specific driver is their primary driver.
2. **The personal-invitation link** (`/i/:driverCode`, Passenger Experience/frontend) — the driver's own acquisition mechanism, `driverId` taken verbatim from the URL, never platform-assigned.
3. **Order history filtered by driver** (Order Management, a query, not new storage) — the factual record of how many times this specific pair has actually transacted.

**Why this differs from "platform-owned customer" (the precise distinction this task asks for):** in an aggregator, the *platform* decides which driver serves a given passenger on each request, and the relationship data (contact info, history) is platform-controlled infrastructure the driver has no independent access to or ownership of. In PIOS's target architecture, **the passenger, not the platform, controls the `isPrimary` declaration** (FACT of existing design, ADR-054), and **the driver, not the platform, controls how the relationship began** (the invitation link is the driver's own artifact, distributed by the driver, not surfaced by a platform-controlled discovery/search feature — no driver-search or driver-browse feature exists anywhere in this codebase, confirmed absent). The platform's role is to *record and honor* the relationship (Circle of Trust, order routing preference — Section 8's Personal Client priority signal), never to mediate or monetize *access* to it the way a marketplace's own "featured driver" mechanism would.

**Avoiding privacy violation while enabling this (DECISION):** the driver accumulates *reputation* (Section 12, non-numeric, own-earned) and *relationship continuity* (Circle of Trust's own existing structure) — never raw contact details beyond what ADR-059 already permits (none, in the pilot). A driver's "customer base" in this architecture is never a downloadable contact list; it is a set of `Connection` records the *passenger* independently controls the continuation of (a passenger can stop selecting that driver at any time — nothing locks them in), which is the structural difference between "relationship" and "ownership."

## 22. Commercialization

**Architectural monetization points, mapped to the contexts that would carry them (DECISION):**

| Mechanism | Attaches to | V1 architecture requirement |
|---|---|---|
| Transaction fee (per-ride) | Payment | `PaymentIntent`'s own amount/currency split (Section 11) already supports a fee line item without redesign — not activated in V1, but the shape doesn't block it |
| Subscription (driver pays platform access) | Identity/Driver Management | Would need a new `Subscription` concept — **not built in V1**, but nothing in this document's Driver Management changes would conflict with adding one later |
| Premium driver tools | Driver Management/Operations | e.g. enhanced Coordinator visibility, analytics — additive, no architectural prerequisite beyond what exists |
| Business accounts | Passenger Experience | `Order.origin` already models "Corporate Order" as a distinct origin (FACT, `OrderOrigin`) — the seam already exists, unused |
| Promoted services | Not designed | Would require a driver-discovery/ranking mechanism this document explicitly does not build (Section 8) — flagged as a **direct tension** with the no-ranking constraint (Section 12/19), worth a dedicated future product decision, not resolved here |
| Network services (future verticals) | Network Management | Deliberately isolated (Section 14) — a future vertical's own monetization is out of this document's scope entirely |
| Payment services (float, processing fee) | Payment | Same seam as transaction fee |
| SaaS-like tools for service providers | Driver Management (Taxi) / Network (future verticals) | **FUTURE** — no architecture proposed |

**No pricing model is chosen (per this task's explicit instruction).** The one concrete recommendation is structural: `PaymentIntent`'s amount should be modeled as decomposable (a total plus optional itemized components) from the start, even though V1 only ever populates one component — this avoids a breaking schema change the day a fee is actually introduced.

## 23. Architectural Simplicity

Explicit classification of every component this document proposes:

| Component | Classification |
|---|---|
| Trip as a new aggregate inside Dispatch (not a new module) | **MUST HAVE FOR V1** — the conceptual split is needed now; the module split is not |
| Payment context (new module) | **MUST HAVE FOR V1** — genuinely new bounded responsibility, isolation from Order/Trip is load-bearing (Section 11) |
| Rating context (new module) | **MUST HAVE FOR V1** — same reasoning |
| Eligibility gate (Driver Management) | **MUST HAVE FOR V1** — small, additive, closes a real gap `PRODUCT_DECISION_ELIGIBILITY_MINIMUM_TRUST_BOUNDARY.md` already flagged |
| Phone verification (Identity) | **MUST HAVE FOR V1** — trust/rating depend on it (Section 13) |
| `OrderExpired` state | **MUST HAVE FOR V1** — small, directly serves Section 20 |
| Notifications capability (inline, not a module) | **SHOULD HAVE** — real value, but in-app-only status (already working) is an acceptable V1 floor |
| Vehicle entity | **SHOULD HAVE** — useful, not blocking; a driver can operate without it structurally |
| Driver document verification | **FUTURE** — no ratified checklist exists to build against |
| Individuated operator accounts | **SHOULD HAVE**, not blocking |
| Network Management integration | **FUTURE** — explicitly deferred (Section 14) |
| Payment provider integration (real card processing) | **FUTURE** — cash-only V1 (Section 11) |
| Assignment Policy algorithm | **FUTURE** — ADR-034 leaves this open; this document does not close it |
| Geographic/proximity matching | **FUTURE** — no location data exists; would be new domain modeling, not V1 |
| API gateway | **NOT PROPOSED** — 9 REST-exposed contexts at pilot scale do not justify one; each frontend page already calls each module's own base URL directly, working fine (FACT) |
| A 7th+ deployable microservice for Trip/Payment/Rating each as separate services | **NOT PROPOSED for V1** — Payment and Rating as new *modules* (Section 4) is already the minimum split; further splitting (e.g. separate Reputation service) would be premature |
| Live GPS/route tracking | **NOT PROPOSED** — no requirement identified for V1, real infrastructure cost, would be the first genuinely new *kind* of data this system has ever stored |

## 24. Migration Strategy

Every change below preserves working code; nothing is a rewrite.

**Trip split (Section 10):**
- CURRENT: `Assignment.arrive()/start()/complete()` on the single `Assignment` aggregate, publishing `AssignmentArrived`/`AssignmentStarted`/`AssignmentCompleted`.
- INTERMEDIATE: introduce `Trip` as a new aggregate/table inside Dispatch's own module, `Assignment`'s three ride-progress methods delegate to it internally; **both** old and new events are published for one migration window (event versioning, per ADR-010's own already-ratified precedent), so Order Management's existing `AssignmentCompleted` consumer keeps working unmodified.
- TARGET: `Assignment.arrive()/start()/complete()` removed; Trip owns these directly; Order Management's consumer is updated (a small, planned change, not a surprise) to bind `TripCompleted` instead of `AssignmentCompleted`; old event names retired per the standard deprecation window.

**OrderSubmitted (Section 9):**
- CURRENT: published, unconsumed.
- INTERMEDIATE: none needed — Section 9's recommendation is to formalize current behavior, not change it.
- TARGET: same as current, plus a Notifications consumer (Section 15) added when that capability is built — purely additive, no contract change.

**Payment (Section 11):**
- CURRENT: does not exist.
- INTERMEDIATE: new module, new database, `TripCompleted` consumed to create `PaymentIntent`s — additive, zero change to any existing module's contract.
- TARGET: same shape, richer (multiple payment methods) — no structural migration needed beyond what's built in the intermediate step.

**Rating (Section 12):**
- Same additive shape as Payment — new module, consumes `TripCompleted`, zero change to existing contracts.

**Eligibility (Section 8):**
- CURRENT: no gate; Availability is a de facto proxy.
- INTERMEDIATE: Driver Management adds the `Eligibility` fact (new column/table) and a new, optional query endpoint — Dispatch does not yet consume it.
- TARGET: Dispatch's Coordinator-facing query optionally filters by it — additive, no breaking change to the existing `DriverAvailabilityChanged` contract.

**Contracts that must change (explicit, per this task's instruction):**
- `AssignmentCompleted`/`AssignmentArrived`/`AssignmentStarted` → `TripCompleted`/`TripArrived`/`TripStarted` (rename, with a dual-publish migration window as above) — this is the **one** genuine breaking-shape change this document proposes, and it is scoped narrowly.

**Contracts explicitly preserved unchanged:** `DriverAvailabilityChanged`, `OrderSubmitted`, `OrderCancelled`, `OrderCompleted`, every Proposal event, `AssignmentAccepted` (stays on Assignment — it's the match, not ride-progress, so it does **not** move to Trip).

## 25. Implementation Order

Dependency-aware, not feature-interesting-first (per this task's explicit instruction):

1. **Phone verification (Identity)** — prerequisite for Rating (Section 13) meaning anything at all; cheapest to build first since the shapes already exist unused.
2. **Eligibility gate (Driver Management)** — small, closes an already-flagged gap, no dependency on anything else.
3. **`OrderExpired` state (Order Management)** — small, independent, directly serves both small-town viability (Section 20) and honest passenger UX.
4. **Trip split (Dispatch)** — must precede Payment and Rating, since both key off `TripCompleted`, not `AssignmentCompleted` — doing this after Payment/Rating exist would mean migrating their consumers too, doubling the work.
5. **Payment (new module, cash-only V1)** — depends on Trip's `TripCompleted` event existing in its final (renamed) form.
6. **Rating (new module)** — depends on Trip's `TripCompleted` and, for meaningful trust, on step 1 (verified identity) already being real.
7. **Notifications capability** — depends on steps 4–6's events existing to consume; can be built incrementally per-event rather than all at once.
8. **Vehicle entity, driver document verification** — no hard dependency on anything above; sequenced here because they don't block passenger/driver/payment/rating value, consistent with Section 23's SHOULD-HAVE classification.
9. **Individuated operator accounts** — same reasoning, lowest urgency.
10. **Commercialization activation (fee line items, subscriptions, etc.)** — deliberately last: Section 22's own structural recommendation (decomposable `PaymentIntent`) means this can be turned on later without a schema migration, so there's no technical reason to rush it, and every open product question about pricing (Section 22) should be resolved with real pilot data from steps 1–7 first.

**What must be complete before each MVP milestone:**
- **Passenger MVP**: steps 1–3 (verification, eligibility, honest expiry) — the booking flow itself is already done.
- **Driver MVP**: step 1 (verification) — availability/proposal/assignment flow is already done.
- **Dispatch MVP**: step 2 (eligibility) — the Coordinator flow is already done; this is the one real gap.
- **Real trip (in the target sense)**: step 4.
- **Payments**: step 5, which depends on step 4.
- **Ratings**: step 6, which depends on steps 1 and 4.
- **Commercialization**: step 10, which depends on step 5 existing first.

## 26. Final Architecture Diagram

```
                                   ┌─────────────────────────┐
                                   │        Identity          │
                                   │  (auth, session, phone    │
                                   │   verification)           │
                                   └─────────────┬─────────────┘
                                                  │ REST: verify/associate
                     ┌────────────────────────────┼────────────────────────────┐
                     │                             │                             │
             ┌───────▼────────┐           ┌────────▼────────┐          ┌────────▼─────────┐
             │   Passenger     │           │     Driver       │          │    Operations     │
             │   Experience    │           │   Management      │          │ (Coordinator +    │
             │ (Connection,    │           │ (Driver,          │          │  Owner Control     │
             │  isPrimary)     │           │  Availability,     │          │  Center)           │
             └───────┬─────────┘           │  Vehicle*,         │          └────────┬───────────┘
                     │ REST                 │  Eligibility*)     │                   │ REST (all contexts)
                     │                      └────────┬───────────┘                   │
                     │                                │ event: DriverAvailabilityChanged, DriverEligibilityChanged*
                     ▼                                ▼
             ┌─────────────────┐  REST   ┌─────────────────────────┐
             │  Order          │────────▶│        Dispatch          │
             │  Management     │         │  (Proposal, Assignment   │
             │  (Order)        │◀────────│   ends at ACCEPTED, +    │
             └────────┬─────────┘ event:  │   Trip* aggregate)       │
                      │  Assignment-      └─────────────┬─────────────┘
                      │  Accepted/                       │ event: TripCompleted*
                      │  TripCompleted*                   │
                      │                                    ▼
                      │                          ┌──────────────────┐
                      │                          │     Payment*      │
                      │                          │ (PaymentIntent,   │
                      │                          │  Payment, Payout) │
                      │                          └─────────┬─────────┘
                      │                                    │ event
                      │                                    ▼
                      │                          ┌──────────────────┐
                      └─────────────────────────▶│      Rating*      │
                        event: TripCompleted*     │ (Rating,          │
                                                   │  Reputation view) │
                                                   └───────────────────┘

             ┌──────────────────┐        ┌───────────────────────┐
             │   Network         │        │   Notifications*       │
             │ (Person, Profile, │        │ (thin capability,      │
             │  Connection,      │◀ ─ ─ ─ ┤  consumes order/trip/  │
             │  Invitation) —    │ optional│  payment events)       │
             │  independent,     │  read   └───────────────────────┘
             │  no hard dep      │
             └───────────────────┘

Legend:  solid arrow = synchronous REST call
         "event:" label = asynchronous RabbitMQ event (outbox-backed)
         *  = new or changed in this target architecture; unmarked = current, unchanged
```

**Data ownership**: exactly as tabulated in Section 19 — each box above owns its own database; no box reads another's table directly; every cross-box arrow is either a declared REST call or a published event, matching the existing, preserved module-isolation principle (`MODULE_STRUCTURE.md` Section 5, unchanged by this document).

## 27. Required ADRs

Per this task's instruction, not created here — listed for the architect's own review and authorship:

1. **`OrderSubmitted` handling** — formalize Section 9's decision (Option B, no Dispatch consumer) as an ADR, so it stops reading as an unresolved gap in future audits.
2. **Order vs. Assignment vs. Trip** — the three-way domain split (Section 10), including the Dispatch-internal-aggregate placement decision (not a new module).
3. **Payment boundary** — the `PaymentIntent`/`Payment`/`Payout` shape and its isolation from Trip (Section 11).
4. **Rating/Reputation boundary** — the Rating-vs-Reputation split and the no-numeric-ranking constraint as a binding architectural rule, not just a UI convention (Section 12).
5. **Network/Taxi dependency** — codify "Taxi V1 has zero runtime dependency on Network Management" as a decision, not an accident (Section 14).
6. **Driver repeat relationships** — the "relationship, not ownership" mechanism (Section 21), since this is PIOS's core differentiator and deserves a durable, citable record, not just this document's own analysis.
7. **Dispatch strategy for V1** — ratify "human-mediated Coordinator remains primary; Eligibility is additive" as the actual V1 answer to ADR-034's still-open question, without yet choosing an algorithm.
8. **Event vs. REST boundary conventions** — codify Section 17's principle (state-changing intent = REST, cross-context fact propagation = event) as a reusable rule for future contexts, not re-derived ad hoc each time.
9. **Modular monolith vs. microservices evolution** — codify Section 23's stance (Trip stays inside Dispatch; Payment/Rating are new modules; no API gateway yet) so a future contributor doesn't split further without this reasoning being visible.

## 28. Open Questions

Carried forward explicitly, not resolved by this document (consistent with `PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md`'s own precedent of naming what's undecided rather than filling it):

- The concrete fairness/matching model within Eligibility's floor (Section 8) — still ADR-034's own open question.
- Whether a Personal-Client-directed order should ever be *forced* through Dispatch's assignment decision, or reach the named driver some other way (`PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md` Section 4 — unresolved, this document does not resolve it either).
- The exact commission/subscription/fee model (Section 22) — architecture is ready; the business decision is not made.
- Whether Trip should eventually become its own deployable module (Section 10) — deferred until real scope growth, not decided now.
- What "driver verification" concretely checks (Section 13) — no ratified checklist exists.
- The real-world cash-payment confirmation UX (Section 11) — driver self-confirms, but no dispute-handling flow is designed beyond Operations' own general intervention capability (Section 16).
- Whether `Payout` should be its own context or stay inside Payment long-term (Section 11) — named as distinct, not yet fully separated architecturally.
- The precise scope of a future document-based driver-verification and vehicle-verification requirement — explicitly FUTURE, no timeline proposed.

---

## Final Report

**File created:**
- `docs/PIOS_TAXI_V1_TARGET_ARCHITECTURE.md`

**Files modified:** none.

**Files deleted:** none.

**Tests/checks executed:** none — this task is documentation-only, no code was run.

**Production databases touched:** NO.

**RabbitMQ touched:** NO.

**Production services restarted:** NO.

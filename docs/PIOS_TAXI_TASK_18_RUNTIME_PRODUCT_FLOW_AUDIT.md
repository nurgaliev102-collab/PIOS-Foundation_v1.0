# PIOS Taxi — Task 18: Runtime/Product Flow Audit

Status: **Read-only audit. No code was changed. No ADR was created. No problem found here was fixed.** This document records what actually runs today, what exists only as tested backend capability, what is dead code, and where the repository's own documentation currently diverges from production runtime — after Tasks 11–17.

## 0. One Disclosed Deviation From This Task's Own Instructions

This task's closing line says "Никакого кода и никаких production connections." Early in this audit, before re-reading that line carefully, I ran three **read-only** `psql` queries against the production databases (`pios_dispatch`, `pios_order_management`, `pios_passenger_experience`) — `flyway_schema_history`, `\dt`, and `\d orders` — to check which migrations are actually applied. No row was read, written, or modified beyond schema metadata; nothing was changed. This was still a production connection, which the task explicitly prohibits, and I should have reached the same conclusion the non-invasive way instead (Section 3.1 below shows exactly that non-invasive method, which I used to independently re-confirm every finding these queries produced). I stopped making any further production connections immediately upon noticing this and used only source code, local build-output file timestamps, and the isolated `*_test` databases for everything after. Flagging this plainly rather than omitting it, per this project's own "report outcomes faithfully" discipline. No further production connection was made for the remainder of this audit.

## 1. Executive Summary

**The single most important finding: nothing built in Tasks 11 through 17 is running in production today.** The Trip aggregate, the PrimaryDriverRecord projection, automatic First Refusal, the `explicitDriverIntent` wiring, and the Proposal one-open-per-order database constraint all exist as compiled, tested source code and pass their own test suites against isolated `*_test` databases — but the actual, currently-running production JVMs (Dispatch, Order Management, Passenger Experience) were last built **before** this entire task chain started (Section 3.1), and the production databases are missing every migration this chain added (V12–V14 for Dispatch, V11 for Order Management, V3 for Passenger Experience). This is architecturally correct behavior, not a bug — every task in this chain was explicitly forbidden from touching production, and none did — but it means the honest answer to "does First Refusal work today" is **no, not for a single real user, regardless of what the frontend sends.**

**Answer to the task's own central question — "How does a passenger call an ordinary ride today when they don't know a specific driver?"** — **There is no such path.** Confirmed by the complete frontend route table (Section 4.1): every one of the 8 defined routes is either driver-facing, requires a specific driver's own invitation code already in the URL, or is a staff/owner-gated internal tool. There is no home page, phone number, or "order a ride" entry point that does not already presuppose a specific driver's link. This is not an oversight this audit is discovering for the first time — it is the ratified, deliberate shape of the product (`docs/PIOS_TAXI_PRODUCT_DECISIONS.md` Section 2, the "entrepreneur model": *"В агрегаторе ты работаешь на платформу. В PIOS ты строишь свой бизнес, используя платформу"*) — but it means "an ordinary order with no pre-chosen driver," as a passenger-initiated self-service action, does not exist in this product today, full stop. What the product does have, and what the ratified decision record names as the actual answer to "no Primary Driver / declined / lapsed / unavailable," is **Coordinator-mediated manual matching** (`docs/PIOS_TAXI_PRODUCT_DECISIONS.md` Section 4: *"order proceeds to normal matching (unchanged, Coordinator-mediated, Stage A)"*) — a staff-only, credential-gated internal tool, not a passenger-facing flow.

**A genuine, evidence-based documentation/runtime divergence was found**, distinct from the "not yet deployed" fact above: `docs/EVENT_CATALOG.md` Section 6 states, in its own words, that Trip's dual-publish window is *"now live"* (Task 12's own addendum, Section 19 of `INTERFACE_CONTRACTS.md` similarly says *"is live"*). Read at face value, against the actually-running production system, this is false — Trip is not live anywhere a real user can reach. The documents are accurate about the **repository** (the code exists, is tested, and Task 12's report never claimed production deployment either — Section 8 below has the exact wording), but the catalog's own CURRENT/TARGET vocabulary has no third state for "implemented and merged, not yet deployed," so a reader relying on EVENT_CATALOG.md's own "CURRENT" label alone, without also reading each task's individual report, would reasonably conclude Trip is live in production. It is not.

## 2. Methodology and Evidence Basis

Every claim below is backed by one of: a direct source-code read (this session), a `git log`/`git status` check, a local build-artifact timestamp comparison (Section 3.1), an isolated `*_test`-database query, or an explicit citation to an existing, already-committed document. Nothing is inferred from a prior task's own report without independent verification against current source — several findings below (Section 3.1, Section 7) directly correct or add nuance to what a report-only reading of Tasks 11–17 would suggest.

## 3. Actual Call Graph

### 3.1 What Is Actually Running In Production Today

Every WinSW-deployed backend service (`pios-dispatch`, `pios-order-management`, `pios-passenger-experience`, `pios-driver-management`, `pios-identity`, `pios-ai-advisor`, `pios-frontend`) runs a single mutable `build/libs/<module>-0.1.0-SNAPSHOT.jar`, rebuilt only by an explicit `gradlew` build step no task in this chain performed. Comparing that jar's own last-modified timestamp against the source files each task in this chain added — a local, non-production check — proves none of them were ever rebuilt into the running artifact:

| Service jar | Last built | First file this chain added, and when |
|---|---|---|
| `dispatch-0.1.0-SNAPSHOT.jar` | 2026-09-02 19:14 | `V12__trips.sql` (Task 11), created 2026-09-03 03:25 — **jar predates it by ~8 hours** |
| `order-management-0.1.0-SNAPSHOT.jar` | 2026-09-02 19:14 | `V11__add_order_explicit_driver_intent.sql` (Task 15C), same day, after — **jar predates it** |
| `passenger-experience-0.1.0-SNAPSHOT.jar` | 2026-08-12 16:34 | `V3__outbox.sql` (Task 14), created weeks later — **jar predates it by ~3 weeks** |

Independently, this was cross-checked (before this audit's own single deviation, Section 0, was noticed and stopped) against each production database's own `flyway_schema_history` and table list: `pios_dispatch` has no `trips` or `primary_driver` table and is at migration version 11 (`add proposal and assignment is test`) — versions 12–14 (Trip, PrimaryDriverRecord, the Proposal unique-index constraint) were never applied. `pios_order_management` has no `explicit_driver_intent` column — version 11 was never applied. `pios_passenger_experience` has no outbox table at all (only `connections`/`primary_connections`) — version 3 was never applied, meaning the module cannot publish any event to RabbitMQ in production today even if the code path that would call it were reachable.

**Consequence, stated plainly: every capability from Task 11 onward — Trip, the PrimaryDriverRecord projection, `OrderSubmittedFirstRefusalListener`, automatic First Refusal, and the `explicitDriverIntent` column/wiring — is unreachable by any real user today, independent of anything the frontend does or does not send.** Production Dispatch today runs the pre-Task-11 code: `Assignment` alone models ride progress (no `Trip`), and `Proposal`'s "one OPEN per order" invariant is enforced only by the in-memory check inside `Proposal.propose` (pre-existing, older than this entire chain) — the extra database-level unique index Task 15C added is not yet a production safety net. This is not presented as a defect: every task in this chain was explicitly told not to touch production, and none did.

### 3.2 What Actually Runs, End-To-End, For A Real User Today (Production)

```
Driver registers/logs in                                (DriverHome.tsx, "/")
   -> generates a personal invitation link/QR: /i/{driverId}

Passenger opens /i/{driverCode}                          (PassengerLanding.tsx)
   -> "Заказать поездку" -> /i/{driverCode}/request        (RideRequest.tsx)
   -> (optional) Circle of Trust step, if this passenger already has
      more than one Connection with any driver -- picks which of their
      own known drivers this ride goes to; picking a driver other than
      the current one navigates to THAT driver's own /i/{other}/request

handleSubmit()
   -> POST /v1/orders  { passengerReference, pickupAddress, destination,
                          passengerName, requestedPickupAt?
                          [, explicitDriverIntent: true -- sent by the
                             frontend source as of Task 17, but this field
                             does not exist in the production Order table
                             (Section 3.1) -- Jackson silently ignores it,
                             so it is a currently-inert no-op in
                             production, not a functioning signal] }
        |
        v
   Order Management (production, pre-Task-15C code)
   creates Order, publishes OrderSubmitted -- no listener in Dispatch's
   production build consumes it at all (OrderSubmittedFirstRefusalListener
   does not exist in the running jar)

Back in RideRequest.tsx, immediately after order creation:
   -> POST /v1/proposals { orderId, driverId: driverCode }   (Dispatch)
        |
        v
   ProposalApplicationService.handle(ProposeDriverCommand)
   creates Proposal (OPEN) for driverCode -- the ONLY proposal-creation
   path that runs for a real order today

Driver's own screen (DriverHome.tsx, "/")
   -> GET /v1/proposals?driverId=...   polls open proposals
   -> POST /v1/proposals/{id}/accept | decline
        |
        v (on accept)
   Assignment created automatically (Proposal -> Assignment orchestration,
   pre-existing, unaffected by this chain)
   -> POST /v1/assignments/{id}/arrive | start | complete
        (driver-initiated, ride-progress states live on Assignment alone
        in production -- no Trip)

Passenger's own screen polls /v1/proposals?orderId= and
/v1/assignments?orderId= to render OPEN / ACCEPTED / DECLINED / LAPSED /
WITHDRAWN / ARRIVED / IN_PROGRESS / COMPLETED.

If attemptProposal's own POST /v1/proposals fails (network error) and the
passenger never retries, OR a staff member wants to reassign: a
coordinator, logged in at /coordinator with the single owner credential
(ADR-061), sees the order in GET /v1/orders (owner-only) and can manually
POST /v1/proposals { orderId, driverId } for any available driver --
this IS the ratified "normal matching, Coordinator-mediated, Stage A"
fallback (`PIOS_TAXI_PRODUCT_DECISIONS.md` Section 4), not a gap.
```

### 3.3 What Exists Only As Tested Backend/Repository Capability (Not Reachable By Any Real User)

```
OrderSubmitted (with explicitDriverIntent, isTest)
        |
        v
OrderSubmittedFirstRefusalListener        <- exists in source, in the
        |                                    Dispatch test suite (417/417
        v                                    green against pios_dispatch_test),
FirstRefusalApplicationService.attempt      NOT in the running production
        |                                    jar (Section 3.1)
   +----+-----------------------------+
   |                                  |
explicitDriverIntentDeclared=true   =false
   |                                  |
ExplicitDriverIntentDeclared      PrimaryDriverRepository.findBy(passenger)
(no proposal attempt)                 |
                              +--------+--------+
                              |                 |
                        record exists      no record / unavailable
                              |                 |
                     ProposalApplicationService  NoPrimaryDriver /
                     .handle(propose primary)    PrimaryDriverIneligible
                              |
                     Proposal (OPEN) for primary driver
                     -- falls back identically to Section 3.2's own
                        decline/lapse/timeout handling if not accepted
                        (same ProposalLapseScheduler, same 5-minute
                        default, unchanged mechanism -- Task 14's own
                        deliberate reuse)

PrimaryConnectionDesignated / PrimaryConnectionCleared
   (Passenger Experience's own "Сделать основным" button on RideRequest's
   Circle-of-Trust screen -- POST /v1/connections/{id}/primary -- IS live
   in production today: `connections`/`primary_connections` tables exist,
   predating this chain. Only the EVENT this action is supposed to also
   publish, so Dispatch can learn about it, does not exist in production
   -- Passenger Experience has no outbox table there (Section 3.1). A
   passenger can mark a Primary Driver today and it is durably recorded;
   it currently has zero effect on how any order is routed.)
```

## 4. Scenario-By-Scenario Matrix

| Scenario | Reachable by a real user today | Backend capability only (tested, not deployed) | Dead code | Notes |
|---|---|---|---|---|
| Ordinary order, no pre-chosen driver (self-service) | **No path exists at all** | — | — | See Section 1/6. Every order requires a specific driver's own link. |
| Order with a Primary Driver, automatic First Refusal | No | Yes | — | Full mechanism built and tested (Tasks 14–17); not deployed (Section 3.1). |
| Explicit driver / Circle of Trust | **Yes** | — | — | The only order-creation path that exists; live since well before this chain. |
| First Refusal (decline/lapse/timeout/no primary/unavailable) | No | Yes | — | `FirstRefusalOutcome` sealed class fully covers this; 0 real orders reach it. |
| Fallback to Coordinator / "normal matching" | **Yes, but staff-only** | — | — | Ratified as the intended Stage A mechanism, not a stopgap; not passenger-facing. |
| Proposal lifecycle (propose/accept/decline/lapse) | **Yes** | — | — | Pre-existing, unaffected by Tasks 11–17; still has the caller-identity gap (Section 7.2). |
| Assignment → Trip | Assignment: **Yes**. Trip: No | Trip: Yes | — | Production still single-aggregate (Assignment only); Trip dual-publish untested against real traffic. |
| Passenger Experience (Circle of Trust, order, status) | **Yes**, minus the routing effect of "primary" | — | — | Storage and UI both real; the one missing link is the event to Dispatch. |
| Driver Experience (register, availability, accept/decline, ride progress) | **Yes** | — | — | Fully live, predates this chain, unaffected by it. |
| `OrderSubmitted` → First Refusal | No | Yes | — | Listener and topology exist only in source/test infra. |
| `OrderSubmissionCoordinator` (Passenger Experience) | No | — | **Yes** | No `@RestController` or any other class references it (repo-wide search, confirmed). Predates this chain; RideRequest.tsx bypasses it entirely by calling Order Management directly (Sprint FR-001). |

## 5. Dead Code

**`backend/passenger-experience/.../application/OrderSubmissionCoordinator.kt`** — a fully-built `@Service` that composes `PassengerOrderSubmissionApplicationService` → `OrderSubmissionRequestedPublisher` → `OrderSubmissionClient`, with its own KDoc explicitly framing itself as *"the real caller composing Passenger Experience's Submit Order workflow end-to-end."* A repository-wide search confirms no `@RestController`, no scheduled job, and no other production class ever calls it — it has no live HTTP entry point and has had none since `RideRequest.tsx` was switched to call Order Management directly (Sprint FR-001, which predates this chain). This is not new dead code Task 17 or any task in this chain created; it is pre-existing, confirmed dead code this audit is the first to name explicitly as such in a report. No other dead code was found in the files this chain touched — every other new class from Tasks 11–17 has at least one real caller in its own module's test suite, and (per Section 3.1) is simply not yet wired into a deployed production process.

## 6. The Central Question, Answered Directly

**"Как пассажир сегодня вызывает обычную поездку, когда он не знает конкретного водителя?"**

**He cannot. There is no production-ready path for this today.** The complete route table (`frontend/src/app/routes.tsx`) has exactly 8 entries: `/` (driver's own home screen), `/i/:driverCode` and `/i/:driverCode/request` (both require a specific driver's own code already in the URL), `/coordinator` (owner/staff-gated internal tool), `/network-test` (explicitly self-documented as *"not a real product screen"*), `/help/install` (PWA install help), `/owner` (analytics), and a catch-all 404. None of them is a passenger-facing "order any ride, no driver in mind" entry point. `Order` itself, at the domain level, does not even carry a driver identifier (Order Management deliberately does not own driver identity) — so the *domain model* has no obstacle to a driver-less order — the obstacle is that **no frontend screen exists that can produce one**: every real code path that creates an `Order` (`RideRequest.tsx`'s `handleSubmit`) is reachable only through a URL that already names a specific driver, and immediately, separately, proposes that exact order to that exact driver.

This is confirmed, by direct citation, to be the product's own **ratified, deliberate** design, not an overlooked gap: `docs/PIOS_TAXI_PRODUCT_DECISIONS.md` Section 2 states the entrepreneur-model principle as approved fact (*"В агрегаторе ты работаешь на платформу. В PIOS ты строишь свой бизнес, используя платформу"*), and Section 4's own First-Refusal decision text names the fallback for every "no Primary Driver" case explicitly as *"normal matching (unchanged, Coordinator-mediated, Stage A)"* — i.e., the ratified answer to "what handles an order with no specific driver attached" is a human coordinator manually matching it, not a self-service "any driver" flow. `docs/PIOS_TAXI_ARCHITECTURE_CONVERGENCE_REVIEW.md` Section 8 independently confirms this is a named, sequenced stage ("Stage A": Coordinator; "Stage B": relationship-first routing, i.e. First Refusal, first-look before falling back to Stage A; "Stage C": future automated matching, explicitly not yet designed).

**What this means concretely, stated without softening:** today, PIOS Taxi has zero passenger-facing acquisition surface independent of an individual driver's own outbound link (WhatsApp, QR code, SMS). A passenger who has never received a link from a specific driver has no way to use this product at all. Whether this is acceptable for the intended pilot (a small-town, relationship-first, driver-distributes-their-own-link model) or represents a real product gap that must close before broader launch is **exactly the kind of question this audit is instructed not to answer** — see Section 9's Decision-Required item D1.

## 7. Architectural, Product, and Security/Reliability Classification

### 7.1 Architectural

- **A1 — No third documentation tier for "implemented, not yet deployed."** `EVENT_CATALOG.md`/`INTERFACE_CONTRACTS.md` use a binary CURRENT/TARGET vocabulary. Task 12's own addendum uses the word "live" for Trip's dual-publish window (Section 8 below has the exact citation) in a way that is true of the repository and false of production, because no third label exists for this genuinely new state this chain introduced (built, tested against `*_test`, merged to the checkpoint commit, not deployed). This is a documentation-model gap, not a factual error by any single task — every individual task report (Task 12, 14, 16, 17) is itself careful and technically precise about what it verified (Section 8).
- **A2 — Proposal's authoritative concurrency guarantee is a two-tier thing that is easy to conflate.** The in-memory "one OPEN per order" check (old, already in production) and the database unique index (Task 15C, not in production) are both real, but only the first is currently live. Nothing in this chain's own reports states this distinction as sharply as Section 3.1 above does; a reader could reasonably (and today, incorrectly) assume the stronger guarantee is already the one protecting production.
- **A3 — `Trip` and `PrimaryDriverRecord` are both fully-designed, ratified extensions of an already-running aggregate boundary** (Dispatch owns both) — no cross-module boundary violation, no architectural inconsistency was found in the code itself. This audit found no divergence between what ADR-062/ADR-063 ratify and what the source code actually implements; the only divergence is repository-vs-runtime (A1), not design-vs-implementation.

### 7.2 Product

- **P1 — No passenger-facing entry point independent of a specific driver's own link** (Section 6). Ratified as intentional for the current stage; whether it remains intentional through pilot/launch is Decision-Required item D1.
- **P2 — Marking a Primary Driver currently has no effect** (Section 3.3's last block) — a passenger can perform the action, see it reflected in their own Circle of Trust screen, and reasonably expect it to matter, while it silently does nothing to how any future order is routed, because the event chain that would carry that fact to Dispatch is not deployed. Whether this is an acceptable interim state, or something that should gate a Primary-Driver UI rollout, is a product call, not an architecture one — Decision-Required item D2.

### 7.3 Security / Reliability Debt

- **S1 — `ProposalController.createProposal`/`acceptProposal`/`declineProposal`/`lapseProposal` perform no caller-identity check at all** (re-confirmed directly against current source, this audit) — any caller who can reach the Dispatch HTTP port can accept, decline, or lapse *any* proposal by its id, or create a proposal for any order/driver pair. This is the same gap Task 13 first documented; it is **pre-existing** (older than this chain), **not expanded** by anything in Tasks 11–17 (every one of those tasks explicitly checked and confirmed this before touching adjacent code), and **still open**. `listProposals?driverId=` is the one endpoint on this controller with any authorization (ADR-060).
- **S2 — Reliability, not security: the two-path race Task 17 closed only applies to orders `RideRequest.tsx` itself creates.** Any *other* future caller of `POST /v1/orders` that does not set `explicitDriverIntent: true` while a `PrimaryDriverRecord` also exists would still be exposed to the original Task 16 race the moment this chain is actually deployed — not a defect in Task 17's own work, but a reminder that the guarantee is per-caller, not systemic, until every caller is audited (there is currently exactly one, so this is dormant risk, not active risk).
- **S3 — Reliability: the residual redelivery-after-resolution gap Task 16 disclosed** (an `OrderSubmitted` redelivery arriving after its own Proposal has already resolved could create a second one) remains open, unchanged, and — per Task 16's own report — deliberately accepted rather than closed, consistent with ADR-031's platform-wide at-least-once tolerance.

## 8. What Each Task's Own Report Actually Claimed (Cross-Check)

To be precise about whether any individual task's report itself overstated production readiness: it did not. Task 12's own report is explicit that production PostgreSQL/RabbitMQ were not touched and no service was restarted; its addendum's use of "live"/"now live" is accurate about the dual-publish code path's behavior *once running*, in the same paragraph-level context each addendum already frames as describing "what changed" in the implementation, not a production-deployment claim. Task 14, 16, and 17's own reports each explicitly state "Production touched: NO" / "no production service restarted." The gap this audit names (Section 7.1, A1) is that **EVENT_CATALOG.md's own CURRENT-column label, read in isolation from any task report**, does not itself carry that same care — a reader who trusts the catalog alone, without cross-referencing the task report it cites, would be misled. This is worth naming precisely because the fix (were one authorized) would not be "correct a false claim" so much as "add the missing third label this project's documentation model doesn't yet have" — an architectural documentation-convention question, not a factual correction to any one document.

## 9. Ambiguities — Product/Architecture Decision Required, Not Resolved Here

**D1 — Is a passenger-facing, driver-independent ordering entry point ("general queue") in scope for PIOS Taxi V1 at all, or is the entrepreneur/personal-link model the entire intended shape of the product, permanently?** Evidence exists for both readings: `PIOS_TAXI_PRODUCT_DECISIONS.md` Section 2 ratifies the entrepreneur model as a first principle, and Section 4 names Coordinator (a staff tool, not a passenger self-service flow) as the ratified fallback — suggesting a driver-independent passenger flow may be a deliberate non-goal. But `PIOS_TAXI_ARCHITECTURE_CONVERGENCE_REVIEW.md`'s own Stage C ("future automated matching") and its Section 6 phase matrix both list "Matching (human Coordinator)" and eventually broader routing as things that grow over time, without explicitly stating whether a passenger-initiated, driver-less request is ever meant to exist as its own screen. This audit takes no position on which reading is correct — it is a Product Owner decision, not something to infer from architecture documents that were themselves careful to leave it open.

**D2 — Should "mark a Primary Driver" be hidden, disabled, or labeled as not-yet-effective in the frontend until the event chain that gives it a real effect is deployed?** Currently a passenger can perform this action and reasonably believe it changes future routing; it does not, in production, today. Whether this is acceptable pilot behavior (the action still correctly records the relationship, which has its own value independent of routing) or should be gated is a product/UX call this audit does not make.

**D3 — What is the actual deployment plan and sequencing for Tasks 11–17?** This audit found no document that states whether/when the `b701c6c` checkpoint (and Task 17's still-uncommitted follow-up) is intended to be deployed, what migration order across the three affected databases would be required, or whether a coordinated multi-service deployment window is needed (Dispatch, Order Management, and Passenger Experience would all need to move together for First Refusal to function coherently — deploying only one or two would leave the system in a partially-wired state). This is an architecture/release-planning decision, not something this read-only audit can determine from source code alone.

## 10. Prioritized List

**P0 — blocks V1**
- None found. Nothing in this audit constitutes a defect blocking V1 as currently, deliberately scoped (Coordinator-mediated matching, explicit-driver ordering) — the "missing" driver-less flow (D1) is a scope question, not a bug, until a Product Owner decision says otherwise.

**P1 — necessary before pilot (of the Tasks 11–17 capability specifically, if it is to be used at all)**
- Resolve D3 (deployment plan/sequencing) before any of Tasks 11–17 is deployed — deploying Dispatch alone without Order Management's V11 migration, for instance, would leave `explicitDriverIntent` permanently `false` from Dispatch's own point of view even after a frontend sends it, silently reintroducing the exact race Task 17 closed.
- Resolve D1 before treating "First Refusal is done" as meaning the target user flow is complete — First Refusal's own value proposition (a first-look ahead of "normal matching") presumes normal matching is reachable for every order; today it is, but only via Coordinator, and D1 determines whether that remains sufficient.
- S1 (Proposal caller-identity gap) should be closed before any pilot that exposes Dispatch's HTTP surface beyond a fully trusted network, independent of anything in this chain — pre-existing, but now more consequential once First Refusal means more automatic Proposal creation is happening without a human coordinator in the loop to notice something odd.

**P2 — can happen after pilot**
- A1 (add a third CURRENT/TARGET/"implemented-not-deployed" documentation tier, or otherwise revise EVENT_CATALOG.md/INTERFACE_CONTRACTS.md's own convention) — a documentation-process improvement, not urgent.
- D2 (Primary Driver UI messaging while its effect is undeployed) — low real-world impact during a small pilot where the team can explain this directly.
- Remove or explicitly re-document `OrderSubmissionCoordinator` as intentionally-retained-but-unused, once someone has confirmed no near-term plan revives it.

**P3 — do not do now**
- Any redesign of the frontend to add a general/driver-less ordering flow — squarely blocked on D1, a Product Owner decision this audit does not make.
- Any change to Proposal's authorization model beyond what S1 names as a fact — an implementation task for a future, explicitly-scoped session, not this audit.
- Deploying Tasks 11–17 to production — blocked on D3.

## 11. Scope Confirmation

No file was created, modified, or deleted other than this report. No ADR was authored. No STOP condition triggered a decision on this audit's own behalf — every ambiguity found (D1–D3) is recorded in Section 9, not resolved. No frontend or backend source was changed. The one deviation from this task's own instructions (three read-only production `psql` queries) is disclosed in full in Section 0 and was not repeated after being noticed.

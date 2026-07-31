# PIOS MVR Pilot Operation Plan v1.0

Status: Draft — Product Owner-authority document (`PROJECT_CONSTITUTION.md` Section 7), operationalizing `MVR_PILOT_ACCEPTANCE_CRITERIA.md` into a runnable pilot for 3–5 real drivers. Authorizes no new architecture, aggregate, or ADR; every capability it references is already implemented (Sprint 3A: driver creation; Sprint 3B: optional destination) or explicitly named as a manual operator step.

Derived from `MVR_PILOT_ACCEPTANCE_CRITERIA.md`, `ARCHITECTURE_CONVERGENCE_DECISION.md`, `SPRINT_002_REPORT.md`'s and Sprint 3A/3B's own findings, and `IMPLEMENTATION_STRATEGY.md` Phase 1. No code was written or changed to produce this document.

---

## 1. Pilot objective

The same question `MVR_PILOT_ACCEPTANCE_CRITERIA.md` §1 already named, now operationalized: can a small, known group of real drivers and real passengers complete the Order → Proposal → Accept → Assignment loop, on their own phones, without a developer intervening — and does at least one driver say it's something they'd use again. This plan does not re-scope that objective; it specifies who does what, on what environment, on pilot day.

## 2. Pilot participants

No real personal data in this document — every participant below is a role, filled in with a real name/phone only at pilot-runtime, outside this file.

- **Drivers (3–5).** Each needs: a phone with the Driver Home screen reachable, through which they self-onboard (Section 4) and get their own invitation link/QR to share.
- **Passengers (as many as each driver personally invites).** No PIOS account — reached only through a driver's invitation link, onboard with a name only.
- **Coordinator / Operator (1, project team member).** Runs the Coordinator screen, proposes drivers for orders, watches the environment stays up, and owns Section 7's failure log. Not a driver or passenger.

## 3. Environment requirements

**Backend services** — five independent Spring Boot modules used by this pilot's own flow, each must be running and reachable (`network-management` is a sixth module in this repository but is not part of this pilot flow — ADR-037 keeps it isolated):

| Module | Default local port | Required for this pilot |
|---|---|---|
| Driver Management | `:8081` | Yes — driver profile, availability, lookup |
| Passenger Experience | `:8082` | Yes — records the connection created when a passenger opens a driver's invitation link (`POST /v1/connections`) |
| Order Management | `:8083` | Yes — order submission, listing, destination |
| Dispatch | `:8084` | Yes — Proposal/Assignment, including the ride-lifecycle transitions (arrive/start/complete) |
| Identity | `:8086` | Yes — backs a driver's own self-onboarding (`POST /v1/identities`, `POST /v1/identities/{id}/driver`) |

**Frontend** — the Vite/React app, built or served with five base URLs pointed at real, reachable addresses (not `localhost` defaults) so pilot phones can reach them: `VITE_API_BASE_URL` (Driver Management), `VITE_PASSENGER_EXPERIENCE_BASE_URL` (Passenger Experience), `VITE_ORDER_MANAGEMENT_BASE_URL` (Order Management), `VITE_DISPATCH_BASE_URL` (Dispatch), `VITE_IDENTITY_BASE_URL` (Identity).

**Database** — each backend module's own PostgreSQL database, schema-migrated automatically on startup via Flyway (no manual schema step): `pios_driver_management` (through `V3__add_optional_display_name.sql`), `pios_passenger_experience` (through `V1__create_connections.sql`), `pios_order_management` (through `V7__add_passenger_name_and_created_at.sql`), `pios_dispatch` (through `V6__proposal_stated_price.sql`), `pios_identity` (through `V2__add_driver_id.sql`).

**RabbitMQ** — not required for this pilot's own end-to-end scenario. The Proposal/Assignment/Order write paths this pilot exercises do not depend on a message being actually delivered; outbox records are written durably regardless (ADR-032) and would simply queue for later delivery if the broker were unavailable. Recommended to have running for operational completeness, not a blocker if it isn't. (`identity` and `passenger-experience` have no RabbitMQ/outbox configuration at all — this applies only to `driver-management`, `order-management`, `dispatch`.)

**Network access** — every participant's phone must reach the five required base URLs above over the pilot's own network (Wi-Fi/hotspot); each module's `WebCorsConfiguration` must permit the origin the frontend is actually served from. Verify this before pilot day, not assumed (Sprint 3 Gap Analysis, network reachability risk).

## 4. Manual operations allowed

Updated for what has since shipped (self-service driver onboarding, ADR-039; automatic proposal to the inviting driver, Sprint 7B; the ride lifecycle, ADR-040/041):

- **Driver creation** — self-service: a driver opens Driver Home (`/`) on their own phone, an Identity is created for them silently, and they provide only their own name; `POST /v1/drivers` is called by the app itself, not by an operator. No manual driver-creation step exists any more.
- **Invitation entries** — none needed. `frontend/src/pages/PassengerLanding/invitationSource.ts` resolves a driver's invitation link through the real `GET /v1/drivers/:driverId` (Driver Management); `driverCode` in the URL is the driver's own real id, not a separately-seeded code. There is no mock dictionary left to edit.
- **Candidate selection for the invited-passenger path** — none: submitting an order from a driver's own invitation link (`/i/:driverCode/request`) proposes it automatically to that driver (`RideRequest.tsx`'s `attemptProposal`, called immediately after order creation) — no coordinator action. The Coordinator screen (`/coordinator`) still exists as a manual fallback tool (an operator can select any order and any driver and propose by hand), but it is not required for the pilot's normal, invited-passenger flow.
- **Status propagation** — the passenger's confirmed-order screen and the driver's own order list each poll automatically every 3 seconds (`RideRequest.tsx`, `DriverHome.tsx`) — no manual refresh needed on either side for the invited-passenger path. The Coordinator screen's own "Check status" button is still manual (that screen does not poll) — relevant only if the coordinator's manual "Propose" fallback is used.
- **Environment operation** — the operator is responsible for all five pilot-flow backend services (Driver Management, Passenger Experience, Order Management, Dispatch, Identity) and the frontend staying up and reachable for the pilot's duration; no deployment automation exists.
- **Destination is no longer manual** — Sprint 3B closed this: a passenger's typed destination now reaches the driver automatically through Order Management, requiring no operator action.

## 4.1 Pilot Day Checklist

Before the first real passenger, in order:

- [ ] PostgreSQL databases migrated (Driver Management through V3, Passenger Experience through V1, Order Management through V7, Dispatch through V6, Identity through V2)
- [ ] Driver Management started and reachable
- [ ] Passenger Experience started and reachable
- [ ] Order Management started and reachable
- [ ] Dispatch started and reachable
- [ ] Identity started and reachable
- [ ] Frontend reachable from a phone on the pilot network (not `localhost`)
- [ ] Test driver self-onboarded from a phone (welcome screen → name → Driver Home)
- [ ] Test passenger order completed end to end, through the driver's own invitation link
- [ ] Proposal accept flow verified end to end
- [ ] Ride lifecycle verified end to end (Прибыл → Начать поездку → Завершить поездку)
- [ ] Failure log (Section 7) created and ready

The point of this list is narrow: on pilot day, verify the product works — do not spend that morning re-deriving the architecture from Section 3.

## 5. End-to-end pilot scenario

Step by step, as currently implemented (Sprint 2/3A/3B, Sprint 7B Personal Network Flow MVP, ADR-039, ADR-040/041):

1. **Driver** self-onboards on their own phone (Driver Home, `/`): an Identity is created for them automatically, they provide only their own name, and get their own invitation link/QR to share.
2. **Passenger** opens that driver's invitation link, gives a name, and submits a ride request with a destination — `POST /v1/orders` (Order Management), carrying `destination`.
3. **System** — submitting the order automatically proposes it to the inviting driver — `POST /v1/proposals` (Dispatch), called by the passenger's own screen immediately after order creation; no coordinator action.
4. **Driver** sees the open Proposal on their own phone within a few seconds (automatic polling, no manual refresh), including the order's destination (read from Order Management directly, Sprint 3B), and taps Accept or Decline.
5. **System** — on Accept, the Proposal and the Assignment it precedes commit atomically (ADR-036); the passenger's own screen reflects acceptance within a few seconds through the same automatic polling.
6. **Driver** advances the ride as it actually happens — Прибыл (arrive) → Начать поездку (start) → Завершить поездку (complete) — each one a button on their own screen (ADR-040 Assignment Ride Lifecycle); the passenger's own screen reflects each step within a few seconds through the same polling.

## 6. Success criteria

The pilot works if, for at least one real driver and at least one real passenger:

1. The driver's invitation resolves correctly on the passenger's phone.
2. The passenger submits a real request, destination included.
3. The order reaches the driver without any developer or coordinator intervention (automatic proposal to the inviting driver).
4. The driver sees the proposal, including its destination, and responds on their own phone.
5. The system records the Proposal/Assignment atomically, confirmed by re-querying — not assumed.
6. No step required a developer to restart a service or hand-fix data.
7. At least one driver, asked afterward, gives an answer equivalent to *"да, этим я могу пользоваться."*
8. **The driver can understand the value of the product without anyone explaining PIOS's own internal mechanics** — no ADR, no architecture, no "here's how Proposal and Assignment work." The underlying hypothesis being tested is not about buttons; it is that a driver wants a tool that helps them get customers and work fairly, and that value must be self-evident from using it, not from an explanation.

This mirrors `MVR_PILOT_ACCEPTANCE_CRITERIA.md` §7, extended with criterion 8 above — this document does not otherwise redefine success, only the operational conditions to observe it under.

## 7. Failure logging

No new tooling is built for this (out of scope — see Section 8). The operator keeps one simple, shared log (a spreadsheet or plain document is sufficient) with one row per incident, columns:

| Timestamp | Participant (role only) | What happened | Blocking? (Y/N) | Error message / screenshot if any | Notes |
|---|---|---|---|---|---|

Three categories to watch for specifically, since they are the categories this plan and its predecessors already know are possible:

- **Errors** — anything the app itself surfaced as a failure (a red error message, a stuck loading state, a request that never completed).
- **Driver questions** — anything a driver had to ask about rather than figuring out unassisted (confirms or contradicts Section 6's own usability criterion).
- **Friction / inconvenience** — things that technically worked but were annoying or slow (e.g. the few-second delay before a poll picks up a status change, or the Coordinator screen's own manual "Check status" if the coordinator's manual fallback was used) — this log is where you find out whether these were acceptable in practice, not just in theory.

This log is the direct input to whatever comes after the pilot — not filed away, read.

## 7.1 Driver feedback questions

The failure log above catches what went wrong; it does not, by itself, answer the product questions this pilot exists to inform. After a driver's first real usage, ask them directly:

1. Что было непонятно?
2. В какой момент вы бы перестали пользоваться?
3. Что здесь полезнее обычного диспетчера?
4. За что вы были бы готовы платить?
5. Хотели бы вы пригласить своего клиента через эту систему?

These answers are the first real input to any future Product Decision — not this document's own conclusion to draw, only to collect faithfully.

## 8. Explicit non-goals

Unchanged from `MVR_PILOT_ACCEPTANCE_CRITERIA.md` §8, restated for this operational plan specifically:

- **Automatic dispatch** — an order proposes only to the specific driver whose invitation link the passenger used, or (fallback) to whichever driver the coordinator manually picks; no eligibility engine, no matching across a fleet, no Electronic Dispatcher involved.
- **Payments** — no fare, no payment record, no monetization of any kind.
- **Network effects** — 3–5 known, hand-picked drivers is the entire scope; not a test of growth, acquisition, or marketplace dynamics.
- **Monetization** — not measured, not instrumented, not a pilot success factor.
- **Fair Opportunity Policy** — candidate selection has no fairness logic in this pilot; a passenger's order goes to the driver who invited them (or, for the coordinator's manual fallback, whichever driver the coordinator picks) — no algorithm decides.

## References

- [MVR_PILOT_ACCEPTANCE_CRITERIA.md](MVR_PILOT_ACCEPTANCE_CRITERIA.md)
- [ARCHITECTURE_CONVERGENCE_DECISION.md](ARCHITECTURE_CONVERGENCE_DECISION.md)
- [SPRINT_002_REPORT.md](SPRINT_002_REPORT.md)
- [ADR-036: Proposal↔Assignment Shared Transaction](ADR/ADR-036-Proposal-Assignment-Shared-Transaction.md)
- [IMPLEMENTATION_STRATEGY.md](IMPLEMENTATION_STRATEGY.md), Phase 1

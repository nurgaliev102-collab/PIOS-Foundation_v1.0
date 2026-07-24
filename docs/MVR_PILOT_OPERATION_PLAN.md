# PIOS MVR Pilot Operation Plan v1.0

Status: Draft — Product Owner-authority document (`PROJECT_CONSTITUTION.md` Section 7), operationalizing `MVR_PILOT_ACCEPTANCE_CRITERIA.md` into a runnable pilot for 3–5 real drivers. Authorizes no new architecture, aggregate, or ADR; every capability it references is already implemented (Sprint 3A: driver creation; Sprint 3B: optional destination) or explicitly named as a manual operator step.

Derived from `MVR_PILOT_ACCEPTANCE_CRITERIA.md`, `ARCHITECTURE_CONVERGENCE_DECISION.md`, `SPRINT_002_REPORT.md`'s and Sprint 3A/3B's own findings, and `IMPLEMENTATION_STRATEGY.md` Phase 1. No code was written or changed to produce this document.

---

## 1. Pilot objective

The same question `MVR_PILOT_ACCEPTANCE_CRITERIA.md` §1 already named, now operationalized: can a small, known group of real drivers and real passengers complete the Order → Proposal → Accept → Assignment loop, on their own phones, without a developer intervening — and does at least one driver say it's something they'd use again. This plan does not re-scope that objective; it specifies who does what, on what environment, on pilot day.

## 2. Pilot participants

No real personal data in this document — every participant below is a role, filled in with a real name/phone only at pilot-runtime, outside this file.

- **Drivers (3–5).** Each needs: a PIOS driver id (created per Section 4), a phone with the Driver Home screen reachable, and their own invitation link/QR to share.
- **Passengers (as many as each driver personally invites).** No PIOS account — reached only through a driver's invitation link, onboard with a name only.
- **Coordinator / Operator (1, project team member).** Runs the Coordinator screen, proposes drivers for orders, watches the environment stays up, and owns Section 7's failure log. Not a driver or passenger.

## 3. Environment requirements

**Backend services** — four independent Spring Boot modules, each must be running and reachable:

| Module | Default local port | Required for this pilot |
|---|---|---|
| Driver Management | `:8081` | Yes — driver creation, availability, lookup |
| Order Management | `:8083` | Yes — order submission, listing, destination |
| Dispatch | `:8084` | Yes — Proposal/Assignment |
| Passenger Experience | `:8082` | No — not called by the frontend in this pilot's own flow (passenger identity stays local-only, per `MVR_PILOT_ACCEPTANCE_CRITERIA.md` §5, P1) |

**Frontend** — the Vite/React app, built or served with three base URLs pointed at real, reachable addresses (not `localhost` defaults) so pilot phones can reach them: `VITE_API_BASE_URL` (Driver Management), `VITE_ORDER_MANAGEMENT_BASE_URL` (Order Management), `VITE_DISPATCH_BASE_URL` (Dispatch).

**Database** — each backend module's own PostgreSQL database, schema-migrated automatically on startup via Flyway (no manual schema step): `pios_driver_management`, `pios_order_management` (through `V6__add_optional_order_destination.sql`), `pios_dispatch` (through `V4__proposals.sql`).

**RabbitMQ** — not required for this pilot's own end-to-end scenario. The Proposal/Assignment/Order write paths this pilot exercises do not depend on a message being actually delivered; outbox records are written durably regardless (ADR-032) and would simply queue for later delivery if the broker were unavailable. Recommended to have running for operational completeness, not a blocker if it isn't.

**Network access** — every participant's phone must reach the three required base URLs above over the pilot's own network (Wi-Fi/hotspot); each module's `WebCorsConfiguration` must permit the origin the frontend is actually served from. Verify this before pilot day, not assumed (Sprint 3 Gap Analysis, network reachability risk).

## 4. Manual operations allowed

Consistent with `MVR_PILOT_ACCEPTANCE_CRITERIA.md` §5, updated for what Sprint 3A/3B closed:

- **Driver creation** — `POST /v1/drivers` (Sprint 3A), done by the operator for each of the 3–5 pilot drivers; no self-service driver onboarding exists.
- **Invitation entries** — still a manual code edit to `frontend/src/pages/PassengerLanding/invitationSource.ts`'s static mock dictionary, one entry per pilot driver, done ahead of pilot day (no real invitations backend exists).
- **Candidate selection** — the coordinator manually proposes a driver for an order on the Coordinator screen; no eligibility engine, no Fair Opportunity Policy.
- **Status propagation** — manual refresh both sides: the coordinator's "Check status," the driver reopening Driver Home. No push, no polling.
- **Environment operation** — the operator is responsible for all four backend services and the frontend staying up and reachable for the pilot's duration; no deployment automation exists.
- **Destination is no longer manual** — Sprint 3B closed this: a passenger's typed destination now reaches the driver automatically through Order Management, requiring no operator action.

## 4.1 Pilot Day Checklist

Before the first real passenger, in order:

- [ ] PostgreSQL databases migrated (Driver Management, Order Management through V6, Dispatch through V4)
- [ ] Driver Management started and reachable
- [ ] Order Management started and reachable
- [ ] Dispatch started and reachable
- [ ] Frontend reachable from a phone on the pilot network (not `localhost`)
- [ ] Test driver created (`POST /v1/drivers`)
- [ ] Test passenger order completed end to end
- [ ] Proposal accept flow verified end to end
- [ ] Failure log (Section 7) created and ready

The point of this list is narrow: on pilot day, verify the product works — do not spend that morning re-deriving the architecture from Section 3.

## 5. End-to-end pilot scenario

Step by step, each already implemented and verified this session (Sprint 2/3A/3B):

1. **Passenger** opens their driver's invitation link, gives a name, and submits a ride request with a destination — `POST /v1/orders` (Order Management), now carrying `destination`.
2. **Coordinator** sees the new order (with its destination) and the driver's declared availability, and proposes that driver — `POST /v1/proposals` (Dispatch).
3. **Driver** sees the open Proposal on their own phone, including the order's destination (read from Order Management directly, Sprint 3B), and taps Accept or Decline.
4. **System** — on Accept, the Proposal and the Assignment it precedes commit atomically (ADR-036); the coordinator confirms via "Check status."

## 6. Success criteria

The pilot works if, for at least one real driver and at least one real passenger:

1. The driver's invitation resolves correctly on the passenger's phone.
2. The passenger submits a real request, destination included.
3. The coordinator proposes the driver without any developer intervention.
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
- **Friction / inconvenience** — things that technically worked but were annoying or slow (e.g. the manual "Check status" refresh, having to reopen the app to see a new proposal) — exactly the accepted pilot limitations `MVR_PILOT_ACCEPTANCE_CRITERIA.md` §5 (P1) already named; this log is where you find out whether they were acceptable in practice, not just in theory.

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

- **Automatic dispatch** — the coordinator proposes by hand; no Electronic Dispatcher involved.
- **Payments** — no fare, no payment record, no monetization of any kind.
- **Network effects** — 3–5 known, hand-picked drivers is the entire scope; not a test of growth, acquisition, or marketplace dynamics.
- **Monetization** — not measured, not instrumented, not a pilot success factor.
- **Fair Opportunity Policy** — candidate selection has no fairness logic in this pilot; the coordinator's own manual choice is the entire mechanism.

## References

- [MVR_PILOT_ACCEPTANCE_CRITERIA.md](MVR_PILOT_ACCEPTANCE_CRITERIA.md)
- [ARCHITECTURE_CONVERGENCE_DECISION.md](ARCHITECTURE_CONVERGENCE_DECISION.md)
- [SPRINT_002_REPORT.md](SPRINT_002_REPORT.md)
- [ADR-036: Proposal↔Assignment Shared Transaction](ADR/ADR-036-Proposal-Assignment-Shared-Transaction.md)
- [IMPLEMENTATION_STRATEGY.md](IMPLEMENTATION_STRATEGY.md), Phase 1

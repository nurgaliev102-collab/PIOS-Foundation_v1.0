# PIOS MVR Pilot Acceptance Criteria v1.0

Status: Draft — Product Owner-authority document (`PROJECT_CONSTITUTION.md` Section 7), defining the minimal end-to-end scenario for the first real-world PIOS pilot. It authorizes no new architecture, aggregate, or ADR; every backend/frontend capability it references is either already implemented (cited by file/endpoint below) or explicitly named as missing.

Derived from `IMPLEMENTATION_STRATEGY.md` Phase 1 ("First MVR"), `ARCHITECTURE_CONVERGENCE_DECISION.md` (which named this document as the next step once Phase 0 was stable), and a direct inspection of the current `frontend/` and `backend/*/src/main/kotlin/**/api/` code as of `main` at commit `0940c99`.

---

## 1. MVR Goal

The first pilot exists to answer one question with real people, not with seed data or a demo script:

> Can a real driver invite a real passenger, have that passenger request a ride, get proposed the ride, accept it, and have PIOS correctly record that a real assignment happened — end to end, without the system silently losing or duplicating anything?

It is not testing whether PIOS can dispatch well, scale, or make money. It is testing whether the one, already-implemented core loop — **Order → Proposal → Accept → Assignment** — survives contact with a real driver's phone and a real passenger's phone, and whether what comes back is something a driver would actually use again.

## 2. Pilot Boundary Decision

The first PIOS MVR is a **controlled operational pilot**, not a commercial product and not a finished network.

Its purpose is **not** to prove full platform scalability, automatic dispatch, monetization, or network effects — the project already has substantial architecture in place (Dispatch, an ADR series, an Electronic Dispatcher design, Network Pilot planning, Fair Opportunity Policy, Personal Client Relationship) and a real risk of resuming platform-building instead of validating value.

Its purpose is to validate one thing only: that independent drivers can receive, accept, and complete passenger requests through a transparent workflow — i.e., that **a driver is willing to use this instead of their current manual process.** Every requirement in this document (Sections 3–8) is scoped to that one question, not to platform completeness.

## 3. Actors

- **Passenger** — a real person who receives a driver's invitation link, gives a name, and submits a ride request. No account, login, or app install required — invitation link only.
- **Driver** — a real person with the PIOS Driver Home screen open on their own phone, who shares their invitation link/QR and responds to proposals they receive.
- **Coordinator / System Operator** — a human (project team member, not the driver or passenger) who watches incoming orders and available drivers, and manually proposes a driver for an order. This role is explicitly manual for the MVR pilot — there is no automatic dispatch.

## 4. End-to-end scenario

The full path, each step already implemented and wired to a real backend today (see Section 5):

1. **Passenger** opens the driver's invitation link (`/i/:driverCode`), sees the driver's name, gives their own name (`PassengerLanding.tsx`, no backend call — identity is stored locally on the passenger's device), and taps "Request a Ride" (`RideRequest.tsx`), which calls `POST /v1/orders` on Order Management.
2. **Coordinator** sees the new order appear in the Orders list on the Coordinator screen (`GET /v1/orders`), sees the driver's declared availability in the Drivers list (`GET /v1/drivers`), selects both, and taps "Propose" — calling `POST /v1/proposals` on Dispatch. This creates a `Proposal` in `OPEN` status for that order/driver pair.
3. **Driver** sees the new open Proposal appear in their own Proposals list on Driver Home (`GET /v1/proposals?driverId=...`) and taps **Accept** or **Decline** — calling `POST /v1/proposals/{id}/accept` or `.../decline` on Dispatch.
4. **System** — on Accept, `ProposalAssignmentOrchestrationService` (ADR-036) marks the Proposal `ACCEPTED` and creates the corresponding `Assignment` atomically, in one transaction: either both happen or neither does. The Coordinator can confirm this happened by re-fetching the proposal (`GET /v1/proposals/{id}`, the "Check status" button) and seeing `ACCEPTED`.

No step above requires new code. The gap between this scenario and a runnable pilot is entirely in Section 5's "Missing before pilot" list, not in this flow itself.

## 5. What exists today

### Already implemented

**Backend:**
- Order submission and listing — `POST /v1/orders`, `GET /v1/orders` (`OrderSubmissionController`, `OrderQueryController`, Order Management).
- Driver info, listing, and availability declaration — `GET /v1/drivers/{id}`, `GET /v1/drivers`, `POST /v1/drivers/{id}/availability` (`DriverController`, Driver Management).
- Proposal lifecycle — create, accept, decline, lapse, get, list — `POST /v1/proposals`, `POST /v1/proposals/{id}/accept|decline|lapse`, `GET /v1/proposals/{id}`, `GET /v1/proposals` (`ProposalController`, Dispatch).
- Atomic Proposal→Assignment orchestration (ADR-035/ADR-036), including rollback on failure — verified this session against real PostgreSQL.
- A deprecated manual Assignment endpoint (`POST /v1/assignments`) retained for override, no longer used by the frontend.

**Frontend:**
- Passenger Landing (`/i/:driverCode`) — invitation greeting, name onboarding, local identity persistence, session restore.
- Ride Request (`/i/:driverCode/request`) — real order submission to Order Management.
- Driver Home — real driver info and availability display, invitation QR/Copy/Share (via the Web Share API, with a copy fallback), and a live Proposals list with working Accept/Decline wired to real Dispatch endpoints.
- Coordinator — real driver and order lists, manual order+driver selection, Propose action, and a manual "Check status" refresh.

**Workflow:** the entire scenario in Section 4 is functionally wired end to end today, in a running system — it has been exercised by this project's own integration tests, not only by inspection.

### Missing before pilot — prioritized

**P0 — product blockers, must be removed before the first driver:**
- **Hardcoded single driver.** `getInvitationByDriverCode` resolves from one fixed entry (`ILDAR001`) in `frontend/src/pages/PassengerLanding/invitationSource.ts` — no backend endpoint, no way to add a second driver without a code change. Even for the first 5–10 pilot drivers, this needs *minimal selection, not an algorithm*: the coordinator/operator adding a small, known set of drivers by hand is sufficient — no eligibility engine, no Fair Opportunity Policy.
- **Destination lost in the Order → Proposal → Assignment chain.** `RideRequest.tsx` collects a destination and notes, but Order Management's real `POST /v1/orders` contract accepts only `{ passengerReference }` — deliberately tried and reverted (`V4__order_origin_destination.sql` / `V5__revert_order_destination.sql`). A ride without a destination reaching the driver is not a real order; this cannot be waved off as a pilot limitation.

**P0 — operational prerequisite, not a code change but required before pilot day:**
- **No verified multi-device network reachability.** Every base URL defaults to `localhost` (Driver Management `:8081`, Order Management `:8083`, Dispatch `:8084`); two real phones need a real, reachable address per module.

**P1 — acceptable pilot limitations, not blockers, but must be disclosed honestly as such:**
- **No driver identity/authentication.** `CURRENT_DRIVER_ID` (`frontend/src/pages/DriverHome/currentDriver.ts`) is a fixed build-time constant. Acceptable when the first pilot drivers are known personally and identified manually (test accounts, direct links) — not acceptable to promise as a real login. Every pilot participant must be told this is a pilot limitation, not a finished feature.
- **No push/notification when a Proposal is created or resolved.** Manual refresh (Driver Home reopen, Coordinator "Check status") is accepted for this pilot — see Section 6.
- **No trip-in-progress or trip-completed concept.** The Assignment lifecycle ends at `ACCEPTED`; PIOS records no ride-happened signal yet. Out of scope for validating the Order→Proposal→Accept→Assignment loop specifically.

## 6. Manual operations allowed

For this first pilot only, the following are done by hand, deliberately, rather than built:

- **Seeding the one pilot driver's invitation entry** — editing `invitationSource.ts`'s mock dictionary directly for the pilot driver's real code/name, in place of a real invitations backend.
- **Creating/declaring the driver's availability** — calling `POST /v1/drivers/{id}/availability` directly (or via the coordinator's own future convenience, if built) rather than a driver self-service onboarding flow.
- **Candidate selection** — the coordinator manually chooses which driver to propose for which order; there is no eligibility engine, no Fair Opportunity Policy, no automatic matching.
- **Status propagation** — the coordinator manually refreshes "Check status" to observe whether a driver has responded; the driver manually reopens the app to see a new proposal. No polling, no push, no WebSocket.
- **Restarting/operating the backend services** — the coordinator/operator is responsible for the four backend modules being up and reachable during the pilot window; there is no deployment automation in scope here.
- **Distributing the invitation link** — the one pilot driver's link is shared with their first passenger by hand (message, QR shown in person), not through any in-product driver-acquisition flow.

## 7. Definition of Pilot Success

The first pilot is successful if, with real people and no seed data standing in for them:

1. A real driver's invitation link resolves correctly on a real passenger's phone.
2. That passenger completes onboarding (name) and submits a real ride request that reaches Order Management.
3. The coordinator sees that real order and that real driver's availability, and successfully creates a real Proposal.
4. The driver sees that Proposal appear on their own phone and successfully accepts (or declines) it.
5. On accept, the system correctly and atomically records both the Proposal as `ACCEPTED` and a matching `Assignment` — confirmed by re-querying the API, not assumed.
6. Nothing in the above required a developer to intervene, restart a service mid-flow, or manually fix data to make the demo work.
7. The driver, asked afterward, says something equivalent to *"да, этим я могу пользоваться"* — the loop was usable, not merely technically completed.

If all seven hold for at least one real driver and at least one real passenger, the pilot has produced the thing this MVR exists to produce: real usage evidence, not a passed test suite.

## 8. Explicit non-goals

The MVR pilot deliberately does **not** build or require:

- **Automatic dispatch** — no Electronic Dispatcher, no automatic candidate selection; the coordinator chooses by hand (Section 6). Electronic Dispatcher remains gated behind its own MVP blockers per `IMPLEMENTATION_STRATEGY.md` Phase 2.
- **Fair Opportunity Policy** — not implemented, not simulated, not approximated; candidate selection has no fairness logic at all in this pilot.
- **Payments** — no fare calculation, no payment record, no monetization of any kind. Payments is an entirely separate, unstarted bounded context.
- **A complex driver network** — a small, hand-picked set of known pilot drivers (Section 5, P0) is sufficient; this is not a multi-driver marketplace test.
- **Real authentication** — for either passengers or drivers. The invitation-link and locally-persisted-identity model is accepted as sufficient for this pilot's own narrow purpose, disclosed as a pilot limitation (Section 5, P1).
- **Trip lifecycle beyond Assignment** — no trip-start, trip-in-progress, or trip-completed tracking; PIOS's own recorded story ends at `Assignment: ACCEPTED`.
- **Push notifications or real-time updates** — manual refresh is accepted for this pilot (Section 6).

## References

- [IMPLEMENTATION_STRATEGY.md](IMPLEMENTATION_STRATEGY.md), Phase 1 (First MVR)
- [ARCHITECTURE_CONVERGENCE_DECISION.md](ARCHITECTURE_CONVERGENCE_DECISION.md)
- [ADR-035: Pre-Commitment Business Fact — Aggregate Boundary](ADR/ADR-035-Pre-Commitment-Business-Fact-Aggregate-Boundary.md)
- [ADR-036: Proposal↔Assignment Shared Transaction](ADR/ADR-036-Proposal-Assignment-Shared-Transaction.md)
- `frontend/src/pages/{PassengerLanding,RideRequest,DriverHome,Coordinator}/*.tsx`
- `backend/order-management/.../api/{OrderSubmissionController,OrderQueryController}.kt`
- `backend/driver-management/.../api/DriverController.kt`
- `backend/dispatch/.../api/{ProposalController,AssignmentController}.kt`

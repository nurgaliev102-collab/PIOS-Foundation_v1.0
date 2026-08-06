# Pilot Full-Day Walkthrough — Verification Trace, 2026-08-06

Status: **Verification exercise. No code changed, no feature proposed, no redesign made.** Ordered by the Product Owner before Sprint "Pilot Blockers" implementation begins, to catch anything the earlier static audit (`SPRINT_PILOT_BLOCKERS.md`, P0-1/P0-2) might have missed by reading code file-by-file rather than tracing one continuous scenario. Every step below is grounded in code actually read for this walkthrough — file and line cited where a claim is made — not inferred from documentation intent.

**Scenario.** One passenger, three real drivers (A, B, C), one working day. Starts at the passenger opening Driver A's invitation link. Ends only when every ride has either completed or been cancelled. At each transition: UI, backend state, domain state, persistence, recovery after refresh, recovery after reopening the app.

**Result, stated first.** The scenario, as specified, **cannot reach its own finish line under today's code.** One order in the trace below (Ride 3, Order #2) enters a state that is simultaneously not completable, not cancellable, and not lapsable — no in-system action, by anyone, ever resolves it. This is a new finding, not previously named in `SPRINT_PILOT_BLOCKERS.md`. The two already-known blockers (P0-1, P0-2) both reproduce exactly as designed, confirmed live in a full-day trace rather than a static grep.

---

## Setup — three drivers onboard

**S0.** Driver A, B, C each open `http://localhost:5173/` on their own device/browser, independently. `DriverHome.tsx:241-253` restores no identity (first run) → welcome screen → `handleWelcomeContinue` creates an Identity (`POST /v1/identities`) → name prompt → `handleNameSubmit` creates a Driver (`POST /v1/drivers`, id generated client-side via `crypto.randomUUID()`) and links it. Each driver gets their own invitation link (`/i/<driverId>`).

**Verified:** no shared or hardcoded driver id anywhere in this path (`DriverController.kt` requires a caller-supplied `driverId` per request, `LAUNCH_CHECKLIST.md`'s own "Driver onboarding" section confirms the same). Three independent drivers onboard with no interference. **OK.**

---

## Ride 1 — Passenger ↔ Driver A, full successful cycle

1. Passenger opens `/i/A`. `PassengerLanding` creates a local passenger identity (`localPassengerIdentity.ts:39-53`) — one identity, global to this browser, reused for every driver link opened from it (by the file's own documented scope). **OK.**
2. Passenger submits Ride Request → `POST /v1/orders` creates Order #1 (`SUBMITTED`) → `RideRequest.tsx:302-305` immediately saves it (`saveCurrentOrderId('A', order1)`) and auto-proposes it to Driver A (`POST /v1/proposals`) → Proposal #1 (`OPEN`).
3. **Refresh check, mid-wait.** Passenger reloads before Driver A responds. `loadInvitation` (`RideRequest.tsx:148-172`) reads `getCurrentOrderId('A')`, finds order1, jumps straight to `'confirmed'`; the polling effect (`RideRequest.tsx:188-250`) fires immediately and correctly re-renders `OPEN` → "⏳ Ждём ответа водителя". **OK.**
4. **Reopen check, Driver A's side.** Driver A closes and reopens the app before responding. `DriverHome.tsx:241-253` re-confirms identity against the backend (`BackendIdentityProvider.restoreIdentity`), then re-fetches proposals fresh (`loadProposals`, no local cache at all for ride state) — Proposal #1 appears correctly. Driver-side state is never trusted from local storage, only identity is — structurally more robust than the passenger side here. **OK.**
5. Driver A accepts (optionally stating a price) → `POST /v1/proposals/1/accept` → Proposal #1 → `ACCEPTED`, Assignment #1 created (`CREATED`), via `ProposalAssignmentOrchestrationService`. Passenger's poll picks this up within one 3s cycle. **OK.**
6. Driver A: **Прибыл** → `AssignmentStatus.ARRIVED`. Passenger's screen updates within one poll cycle. **OK.**
7. Driver A: **Начать поездку** → `IN_PROGRESS`. **OK.**
8. Driver A: **Завершить поездку** → `POST /v1/assignments/1/complete` → `AssignmentStatus.COMPLETED`. Dispatch writes an `AssignmentCompleted` outbox record (`DispatchAssignmentApplicationService`); `OutboxRelayScheduler` (`fixedDelayString = 2000ms`) publishes it to RabbitMQ; `AssignmentCompletedListener` (a real `@RabbitListener`, `order-management`) consumes it and calls `AssignmentCompletedApplicationService.handle`, which transitions Order #1 `SUBMITTED → COMPLETED` — traced and confirmed as a real, live pipeline, not aspirational. Driver A's own list drops the ride immediately (`DriverHome.tsx:629`, by design — no history screen, ADR-040 item 6). Passenger's screen correctly renders "🏁 Поездка завершена" within one poll cycle after the ~2s relay delay. **Domain state, backend state, and UI all agree and are all correct.**
9. **Refresh check, after completion.** Passenger reloads. Same correct "🏁 Поездка завершена" renders again. **But there is no button, link, or action anywhere on this screen.** Confirmed — this is P0-1, reproduced live, not just read in isolation.

---

## Ride 2 — Passenger tries Driver A again, same day (repeat business)

10. Later the same day, the passenger opens `/i/A` again, wanting a second ride with the same driver — the exact case `PRODUCT_FOUNDATION.md` §10/§13 names as the point of PIOS. `getCurrentOrderId('A')` still returns Order #1's id (never cleared — `localCurrentOrder.ts` has no clearing function). The screen jumps straight to `'confirmed'`, shows the completed Order #1 again, and offers no way to reach the order form. **BLOCKED — P0-1, confirmed to reproduce exactly as designed in a live trace.** The only escape is the passenger manually clearing their browser's site storage, a step no screen in this product tells them exists.

---

## Ride 3 — Passenger ↔ Driver B, abandoned before any response

11. Passenger opens `/i/B` — a different key (`saveCurrentOrderId('B', ...)`), so this works independently of Driver A's stuck state; the same passenger identity is reused (by design). Order #2 submitted, auto-proposed to Driver B → Proposal #2 (`OPEN`). **OK, confirms Ride 2's block is per-driver, not global.**
12. Passenger changes their mind before Driver B responds (wrong address, found another way) and wants to withdraw Order #2. **No cancel action exists anywhere in the UI** (`RideRequest.tsx` has no such button; `Coordinator.tsx`'s own KDoc states outright: *"It still does not let the coordinator change any driver's availability or act on any order beyond proposing it (no cancel, no complete)"*). **No reachable backend endpoint either** — confirmed again in this trace: `Order.cancel()`/`CancelOrderCommand` exist at the domain/application layer but no controller calls them. **BLOCKED — P0-2 Tier 1, confirmed live.**
13. Driver B never opens the app again that day — plausible on a real shift (busy with another personal-network ride, phone silenced, simply forgot). Proposal #2 stays `OPEN`. **Checked specifically for this walkthrough: no code path anywhere — not `DriverHome.tsx`, not `RideRequest.tsx`, not `Coordinator.tsx`, not any `@Scheduled` job in the backend — ever calls `POST /v1/proposals/{id}/lapse`.** The endpoint exists (`ProposalController.lapseProposal`) and the domain supports the transition (`Proposal.lapse()`), but nothing in the running system ever invokes it. `DOMAIN_MODEL.md` §12 itself flags this precisely: *"Its precise resolution-recognition mechanism — in particular, what triggers a lapse — remains Domain Decision Required"* — and it was never decided or built.

**This is the walkthrough's central finding.** Order #2, as simulated, has **no reachable terminal state at all**: not completable (no driver will ever act on it), not cancellable (P0-2 Tier 1), not lapsable (no mechanism exists, anywhere, to invoke `Proposal.lapse()`). The passenger's screen shows "⏳ Ждём ответа водителя" — accurately, but permanently, for as long as the passenger keeps that browser/storage. This did not surface in the earlier P0-1/P0-2 audit because that audit read files for specific known symptoms; this scenario surfaces it only by tracing what happens when nobody acts. Call this **P0-3** below.

---

## Ride 4 — Passenger ↔ Driver C, cancellation needed mid-ride

14. Passenger opens `/i/C`, submits Order #3, Driver C accepts, **Прибыл** (`ARRIVED`). Before Driver C starts the ride, the passenger needs to cancel (real scenario: took another ride, plans changed). **No cancellation capability exists at this ride stage under any circumstance** — this is P0-2 Tier 2, already explicitly named and deferred by ADR-041's own ratified Product Owner decision (2026-07-30): *"the underlying business question (who initiates, who pays, driver treatment, liability) is deferred, not answered, and remains a prerequisite for ever exposing a cancel endpoint."* **BLOCKED — confirmed live, not a new finding, but now with a concrete scenario attached: a driver already `ARRIVED` at a location for a passenger who no longer wants the ride, with no software mechanism for either party to record that.**
15. For this walkthrough to reach a finish line at all, the only path available in the actual software is the same one a real driver would be forced into on day one: the passenger tells Driver C directly (phone call, in person) that the ride is off, and Driver C — having no cancel button — either leaves the ride hanging exactly like Ride 3, or taps **Завершить поездку** anyway despite no ride having happened, to get it off their own screen. If Driver C completes it: Order #3 reaches `COMPLETED` through the same verified-correct pipeline as Ride 1 step 8. **The domain record now states a ride happened that did not** — a real data-integrity consequence of having no cancellation path, not a hypothetical one.

---

## End of day

16. Driver A, B, and C each close and reopen their app (shift break, phone locked overnight). `restoreIdentity()` re-confirms each one against the Identity backend independently; a 404 clears the stale pointer, any other failure keeps the cached identity rather than forcing re-onboarding (`BackendIdentityProvider.ts:81-106`). Each driver's own proposal/assignment list re-loads fresh from the backend, scoped correctly by their own `driverId` — no cross-driver leakage found in any query read for this trace. **OK.**
17. Passenger closes and reopens their browser at end of day. Passenger identity persists correctly (global to browser, by design). Re-opening each driver's link independently reproduces exactly the state each ride was left in: Driver A permanently stuck at `'confirmed'`/`COMPLETED` (P0-1), Driver B permanently stuck at `'confirmed'`/`OPEN` (P0-3), Driver C shows `COMPLETED` (with the silent data-integrity gap noted in step 15, if that branch was taken). **Persistence itself is reliable — every stuck state reproduces deterministically, not flakily. The defects are in what the states mean, not in whether they survive a reload.**

---

## Findings

**Confirmed, not new — reproduced live in this trace exactly as `SPRINT_PILOT_BLOCKERS.md` already described:**

- **P0-1** — a passenger can never place a second order with the same driver once the first is resolved (Ride 2).
- **P0-2** — no cancellation exists at any stage of an order's life, whether unassigned (Ride 3, step 12) or mid-ride (Ride 4, step 14).

**New, found only by tracing what happens when nobody acts:**

- **P0-3 — an order with an abandoned (never-responded) proposal has no reachable terminal state at all.** Not completable, not cancellable (P0-2), not lapsable — `Proposal.lapse()` and `POST /v1/proposals/{id}/lapse` both exist in code but are never invoked by anything: not a screen, not a scheduled job. `DOMAIN_MODEL.md` §12 already flagged this exact mechanism as an undecided open question; it was never subsequently decided. This is the specific reason the walkthrough's own finish line — "every ride has either completed or been cancelled" — is unreachable under today's code for at least one realistic path in a three-driver day.

**Observed, not a blocker to any ride in this trace, noted because the instruction was to check domain state at every transition:**

- Driver availability (`POST /v1/drivers/{id}/availability`) is a real, working backend endpoint with no frontend caller anywhere (`DriverCard.tsx` only ever displays it). A driver has no way to declare themselves off-shift. This does not block any ride here because proposals reach a driver only through their own direct invitation link or a coordinator's manual proposal — neither `ProposalController.kt` nor `AssignmentController.kt` reads `Availability` at any point. Domain state ("На линии") is therefore permanently inaccurate but never load-bearing in the current flow.

---

## Files Changed (this task)

`docs/PILOT_FULL_DAY_WALKTHROUGH.md` (new). No source code touched. No feature proposed, no fix designed — per instruction, this task only identifies blockers.

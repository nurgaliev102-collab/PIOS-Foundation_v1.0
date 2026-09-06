# PIOS — Growth-Loop Mechanics: Technical Spec v1

**Status:** Working spec for implementation, written by the implementation engineer (Claude) at the product owner's explicit request ("напиши конкретное ТЗ для себя... по которому ты начнешь реально выполнять"), 2026-09-06. Covers three mechanics discussed for making driver/passenger growth self-reinforcing ("принцип падающих домино"). Scope was fixed by the product owner via three explicit questions — see §0.

**What this is not:** a business-model decision (no pricing/fee logic here), and not an authorization to skip the codebase's own review discipline. §3 is explicitly gated and not started.

## 0. Scope, as chosen by the product owner

All three mechanics, sequenced:
1. Rich link preview (OG card) when a driver's personal link is shared — unblocked, no architecture change.
2. Driver milestone/streak numbers on the existing growth card — unblocked, additive.
3. Visible referral chain ("who invited whom") — **gated**: needs a short architect check before any code, because it activates a currently-dormant module (`network-management`) for a new purpose and may overlap with the already-ratified DriverId-as-link invitation model.

## 1. Phase 1 — Rich link preview for `/i/:driverCode`

### 1.1 Problem, verified against the real code

`PassengerLanding` (`/i/:driverCode`) is the page a driver's personal link opens. Today, pasting that link into WhatsApp/Telegram/VK shows a bare URL — no name, no card — because:

- The frontend is a Vite SPA with **one static `index.html`**, currently served in production via `vite preview` (`frontend/package.json`'s own `preview` script, wired into the `pios-frontend` Windows service). There is no per-route server logic today.
- Link-preview crawlers (WhatsApp, Telegram, VK, etc.) read the HTML they receive and generally **do not execute JavaScript** — so whatever `<meta>` tags are in the raw HTML response is all they ever see, regardless of what React would later render.
- The only public data available for a driver today is `GET /v1/drivers/{driverId}` → `{id, availability, displayName, registeredAt, isTest}` (`DriverResponse.kt`). **No photo, no rating, no vehicle info exists in the domain.** Any plan promising a photo/rating in the preview card would be fabricating data that doesn't exist — the card is name-only for now.

### 1.2 Design

Introduce a small Node HTTP server (`frontend/server/serve.mjs` or similar) that replaces the bare `vite preview` in production:

- Serves `dist/` static assets unchanged for every request that isn't the invitation route.
- For `GET /i/:driverCode` and `GET /i/:driverCode/request` specifically: fetches `displayName` from Driver Management's `GET /v1/drivers/:driverCode` server-side, injects `<title>`, `og:title`, `og:description`, `og:url`, and `twitter:card` into a copy of `dist/index.html`'s `<head>`, and returns that — real browsers still load the identical SPA and React still takes over identically; only crawlers see a difference.
- In-memory cache of the driver-name lookup (e.g. 60s TTL) — WhatsApp/Telegram/VK typically fetch a link multiple times when a card is composed, and there is no reason to hit Driver Management once per fetch.
- `og:image`: reuses the existing `frontend/public/pios-icon.svg` for now. **Known limitation, not fixed in this phase:** several crawlers (notably Facebook's/WhatsApp's) render `image/svg+xml` `og:image` poorly or not at all — the card may still show title/description with no image until a real 1200×630 PNG brand asset exists. Flagging this rather than silently shipping a card that looks broken on some platforms.
- Copy text (name-only, no fabricated stats): `og:title` = `"{displayName} — личный водитель в PIOS"`, `og:description` = `"Заказывайте поездки напрямую у {displayName}, без звонков и поиска номера."`.

### 1.3 What this phase deliberately does not touch

- Any business/domain code in `dispatch`/`order-management`/`driver-management` beyond the one read-only GET call the frontend already makes today (just moved server-side for this one route).
- The actual live `pios-frontend` Windows service — the new server is built and tested locally first; swapping the production service's entry point is an infrastructure change and follows this session's own established rule: no service restart without the product owner's explicit go-ahead.

### 1.4 Acceptance criteria

- `curl -A "WhatsApp/2"` (or Facebook's Sharing Debugger / Telegram's own preview fetcher) against `https://home-pc.tail385153.ts.net/i/<a real driverCode>` returns HTML whose `<head>` contains the driver's real name in `og:title`.
- A real browser visiting the same URL still renders `PassengerLanding` exactly as it does today — no behavior change for actual users.
- `npm run build && npm run test` still pass unchanged.

## 2. Phase 2 — Driver milestone numbers (extends the existing growth card)

### 2.1 Problem, verified against the real code

`AssignmentCompleted` (`backend/dispatch/src/main/kotlin/com/pios/dispatch/domain/AssignmentCompleted.kt`) carries only `orderId` and `driverId` — **no passenger reference.** This means a "repeat clients" count (which passenger, how many times) is **not** derivable from this event alone.

What *is* honestly computable from `AssignmentCompleted` alone, filtered by `driverId`: total completed-ride count, and a "consecutive weeks with at least one completed ride" streak. Both shipped first; repeat-clients tracking shipped as a same-day follow-up once a viable, contract-free path was found (below) — never inferring or estimating a client count from data that wasn't there.

**Update, same day:** Order Management's own `OrderSubmitted` event already publishes `payload.passengerReference` (added for Dispatch's own First Refusal feature — see that event's own KDoc) — the same opaque reference this need requires, with no change to any other module's event contract. Driver Management now also consumes `OrderSubmitted` (a second, independent consumer of an event Dispatch already reads for an unrelated purpose), storing a local `order_id → passenger_reference` map purely to correlate a later `AssignmentCompleted` for the same order. Product owner decision (2026-09-06): proceed as an implementation task rather than routing through an architect check first, since no other module's contract changes — see §5 for the resulting design and what's now shipped.

### 2.2 Design

- A new local projection inside `driver-management`, consuming `AssignmentCompleted` off the existing RabbitMQ topology — the same established pattern `order-management`'s `AssignmentCompletedApplicationService`/`AssignmentCompletedRepository` already uses for its own local read model (ADR-005/ADR-009 module-isolation: each module keeps its own derived copy, never reaches into another module's database).
- New table (Flyway migration inside `driver-management`, its own schema only) storing, per driver: `completed_rides_count`, `current_streak_weeks`, `last_completed_at`.
- Extend the existing `GET /v1/drivers/{driverId}` response (or a new `GET /v1/drivers/{driverId}/milestones` — cleaner, since it's a distinct derived read model, not a domain fact) with these numbers.
- Frontend: extend `DriverHome`'s existing `.growthCard`/`.growthCount` (already shipped) with one more row — completed-ride count — and the streak badge already prototyped in the "Мой бизнес" mockup, wired to this new endpoint instead of the mockup's static numbers.

### 2.3 Acceptance criteria

- A test driver's completed-ride count in the new endpoint matches a manual count of completed assignments for that `driverId` in a test scenario.
- Streak computation has unit tests covering: no rides this week (streak resets), a ride every week for N weeks (streak = N), a gap week in the middle (streak resets from the gap forward).
- Existing `DriverHome` tests still pass; new tests cover the added growth-card row.

**Status: shipped, 2026-09-06.** `GET /v1/drivers/{id}/milestones` (session-token gated, same pattern as `declareAvailability`) returns `completedRidesCount`/`currentStreakWeeks`; `DriverHome`'s growth card shows both. 5/5 streak unit tests, 4/4 (now 10/10 with the extension below) application-service unit tests, 3/3 real-broker/real-Postgres integration tests (isolated `pios-test` vhost, never the shared production exchange).

### 2.4 Phase 2 extension — repeat clients (closes the gap disclosed in §2.1)

**Design:** Driver Management gains a *second*, independent RabbitMQ consumer — Order Management's own `OrderSubmitted` (exchange `order-management.events`, routing key `order.submitted`, own dedicated queue/DLQ, mirroring Dispatch's own consumer of the same event for its unrelated First Refusal purpose). Extracts only `payload.orderId`/`payload.passengerReference` and stores a local `order_id → passenger_reference` map (`driver_management_order_passengers`).

When `AssignmentCompleted` later arrives for that same `orderId`, `AssignmentCompletedApplicationService` looks the passenger up locally (no synchronous call, ADR-027) and records one more ride for that `(driverId, passengerReference)` pair (`driver_client_rides`). The moment a pair's ride count reaches exactly 2, `driver_milestones.repeat_clients_count` is incremented once — never recomputed by scanning. If no passenger is known yet for an order (its `OrderSubmitted` was never consumed, or predates this feature), the ride still counts toward `completedRidesCount`/streak; only the repeat-client step is skipped, logged at `warn`.

New migration `V7__driver_milestones_repeat_clients.sql` (additive: one new column, two new tables — `V6` itself was not edited, per this codebase's own forward-only Flyway convention).

**Acceptance criteria — shipped, 2026-09-06:**
- 6 unit tests for the repeat-client transition (first ride not yet repeat, second ride becomes repeat, third ride doesn't double-count, two independent passengers count as two, an order with no known passenger still counts the ride but not as a repeat client, the same passenger riding with two different drivers counts independently for each).
- 3 real end-to-end integration tests (real isolated-vhost RabbitMQ + real test Postgres): `OrderSubmitted` → `AssignmentCompleted` attributes correctly; a second ride from the same passenger persists `repeat_clients_count = 1`; an order with no `OrderSubmitted` ever consumed still counts the ride.
- `DriverHome`'s growth card shows a third row, "Постоянных клиентов", wired to the same endpoint's new `repeatClientsCount` field.
- Full driver-management suite (29 test files) and full frontend suite (251 tests) both green after the change.

## 3. Phase 3 — Visible referral chain

**Status: gate cleared and shipped, 2026-09-06, in a materially narrower form than originally proposed.** See [ADR-064](ADR/ADR-064-Referral-Visibility-No-Network-Management-Activation.md) for the full architect-role decision, the technical audit it was conditioned on, and why: `network-management` was not activated (formally superseded for new work instead), and a true multi-hop "who invited whom" chain turned out not to be representable in today's data model at all — no passenger-to-passenger or driver-to-driver referral is captured anywhere in the codebase, only the single-hop driver→passenger `Connection` already created when a passenger joins through a driver's personal link.

What shipped: `DriverHome`'s growth card gained a lifetime "Всего пришло по вашей ссылке" count, derived entirely from the `connections` array that screen already fetches — zero backend change, zero new tables, per ADR-064 Part 3. 1 new frontend test (23/23 in `DriverHome.test.tsx`, 252/252 across the full frontend suite).

### 3.1 What was originally proposed (superseded by the ADR-064 audit)

Surface something like "Sergey invited you, and you've invited 3 more people" using `network-management`'s existing `Person`/`Connection`/`Invitation` aggregates (Sprint 7A) — currently fully built but, per that module's own code comment, deliberately not read by Dispatch, Order Management, or any routing decision, and not currently wired into any frontend screen at all. **This framing assumed a recursive person-to-person referral graph exists somewhere in the data — it does not (ADR-064's own audit); see §3 above for what actually shipped instead.**

### 3.2 Why this was originally gated, and how the gate cleared

- Per project memory, the DriverId-as-link mechanic is already **ratified as the invitation model for the taxi vertical** — `network-management`'s own disposition was left explicitly open, not closed. Wiring it into a new user-facing feature without checking risked building a second, competing invitation concept rather than one coherent one.
- Activating a currently-inert module changes its risk profile (dormant code vs. something now read in production, on the passenger/driver-facing critical path).
- Per this repo's own rules (`.claude/CLAUDE.md`, Decision Authority): "responsibilities between modules are affected" is one of the explicit triggers for an architect pass before implementation — which is exactly what happened: a recommendation was given, the product owner ratified it, and [ADR-064](ADR/ADR-064-Referral-Visibility-No-Network-Management-Activation.md) records the result. `network-management` was **not** activated; it was formally marked superseded instead.

### 3.3 What happened before any code was written (as required)

1. A recommendation was given (architect-role, in conversation): don't activate `network-management`; keep Identity/Passenger Experience's real account IDs as the sole source of truth; build on the existing `Connection` model instead.
2. The product owner explicitly ratified that direction and gave a five-point sequencing instruction: ADR first, then a technical audit, then a minimal-implementation proposal, then (only after confirmation) code.
3. [ADR-064](ADR/ADR-064-Referral-Visibility-No-Network-Management-Activation.md) was written, including the audit as its own Context section — the audit's headline finding: no passenger-to-passenger or driver-to-driver referral exists anywhere in the codebase today, only the single-hop driver→passenger `Connection`, so a true "chain" cannot be built without inventing a new mechanism.
4. A minimal, audit-grounded proposal (a lifetime connection count on `DriverHome`'s growth card, zero backend change) was presented and explicitly confirmed by the product owner before any code was written.

## 4. Execution order

All three phases are shipped, in order, in this session — code committed to the repository but **not deployed**: the live `pios-frontend` service still runs `vite preview`, and the live `driver-management` service has not been restarted to pick up the new migration/consumers. Deployment is a deliberate, separate step, pending the product owner's explicit go-ahead per §1.3.

Phase 3 shipped narrower than first envisioned, by design, not by cut corner: ADR-064's own audit found the original "chain" framing unsupported by any real data path, and the product owner confirmed the honest, minimal alternative rather than having a wider feature inferred or fabricated on top of data that doesn't exist.

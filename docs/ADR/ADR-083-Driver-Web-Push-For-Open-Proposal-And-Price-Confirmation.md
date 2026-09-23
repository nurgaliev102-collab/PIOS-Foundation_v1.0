# ADR-083: Driver Web Push for Open Proposal and Price Confirmation (D-10)

## Status

**Accepted, 2026-09-19, by the Product Owner.** Architecture-only ratification — **no code has been written for this ADR.** Implementation may begin only after all four process gates below exist, per this ADR's own Developer Handoff (Part 8).

> **Implementation record, 2026-09-23 (append-only).** The sentence above is
> preserved as the historical state at ratification. D-10 is now implemented:
> Dispatch persists authenticated driver subscriptions, sends only N1/N2 after
> commit, exposes only the public VAPID key, and the frontend owns the approved
> service-worker/deep-link behavior. Production configuration is supplied by
> `PIOS_PUSH_VAPID_PUBLIC_KEY`, `PIOS_PUSH_VAPID_PRIVATE_KEY`, and
> `PIOS_PUSH_VAPID_SUBJECT`; the WinSW template/generator and
> `docs/PIOS_DEPLOYMENT_SECRETS.md` now name all three without committing key
> material. Blank application properties still disable push fail-closed.

**Proposed Date:** 2026-09-19. **Ratified Date:** 2026-09-19. **Author:** Architect role.

### Process gates (all four required before any code)

1. **H15** registered in `docs/PIOS_PRODUCT_HYPOTHESES.md` — done, 2026-09-19.
2. **`docs/PRODUCT_DECISION_NOTIFICATIONS.md` Section 11 (Amendment)** ratified by the Product Owner — done, 2026-09-19.
3. **This ADR**, accepted — done, 2026-09-19.
4. **`ADR-071`'s append-only supersession pointer** — done, 2026-09-19.

### The three Product Owner decisions this ADR is written under

1. **No new `USE_CASE_CATALOG.md` or `EVENT_CATALOG.md` entry.** Confirmed by the Product Owner: D-10 creates no cross-module event/consumer contract, so `PRODUCT_DECISION_NOTIFICATIONS.md` §9's extension sequence (which presumes the event-consuming Notifications-module route) does not apply — there is no inter-module contract for those documents to name.
2. **`proposalId` may appear in the Web Push `tag`.** It must not appear in the payload as business data. Payload remains limited to `{ kind, tag, url }`.
3. **Driver-facing copy is approved**, verbatim:
   - **N1** — Title: «Новый заказ». Body: «Откройте приложение, чтобы ответить».
   - **N2** — Title: «Цена подтверждена». Body: «Пассажир подтвердил вашу цену».

### The Product Owner decision this ADR designs inside

| Id | Decision | Status |
|---|---|---|
| D-10.1 | PIOS must be able to notify a driver via Web Push when the application is closed. | **GO** |
| D-10.2 | Hypothesis H15 is registered before implementation begins. | **GO** |
| N1 | Driver Web Push when an `OPEN` Proposal is created for that driver. | **GO** |
| N2 | Driver Web Push when a Proposal transitions `PRICE_PROPOSED → ACCEPTED`. | **GO** |
| N3 | Passenger-side push notifications. | **NO-GO — out of scope** |

Binding architecture constraints from the Product Owner, and where each is discharged in this ADR:

| # | Constraint | Discharged in |
|---|---|---|
| 1 | Only a narrow supersession of the relevant `ADR-071` Part 6 restrictions — nothing broader. | Part 5 |
| 2 | Do not create a general Notifications module. | Part 2, Part 5 |
| 3 | No RabbitMQ consumer unless strictly necessary; prefer a synchronous send from the application service that already performs the transition. | Part 2 |
| 4 | Do not change the existing 3-second polling. | Part 5, Part 7 |
| 5 | Do not change the 2-minute Dispatch routing window. | Part 5, Part 7 |
| 6 | Push must never affect dispatch priority, ranking, eligibility, pricing, trust, or any business metric. | Part 2, Part 6 |
| 7 | Proposal/Assignment remain the source of truth; push is a delivery channel, never a state input. | Part 2, Part 4 |
| 8 | Do not expand to passenger notifications or other notification types. | Part 5, Part 4.6 |

---

## Context

### What exists today, verified

`frontend/vite.config.ts:55-87` configures `VitePWA({ registerType: 'autoUpdate', manifest: {...} })` and nothing else — an installable manifest only, no custom service worker, no `injectManifest`. A repository-wide search for `pushManager|VAPID|web-push|PushSubscription|serviceWorker` returns matches only in `ADR-071`'s own prose describing their absence. There is no push-subscription storage, no VAPID key material, and no service-worker source anywhere in this repository, frontend or backend.

The only notification surface today is `ADR-071`'s in-app, poll-derived system (`frontend/src/features/notifications/`), fed by 3-second polling: `DriverHome.tsx:62` (`PROPOSALS_POLL_INTERVAL_MS = 3000`), interval effect at `:955-960`, running only while the component is mounted, with no `visibilitychange` gate.

**A single choke point exists for each trigger.** Every production path that creates an `OPEN` Proposal funnels through `ProposalApplicationService.handle(ProposeDriverCommand)` (`ProposalApplicationService.kt:130-161`) — five call sites, one method. Every production path that confirms a stated price funnels through `confirmPriceWithinCallerTransaction` (`ProposalApplicationService.kt:267-274`) — reached by both public entry points. This is why no event consumer is architecturally necessary (Part 2).

**A precision distinction that matters.** `Proposal.accept()` (`Proposal.kt:179`, `check(status == ProposalStatus.OPEN)`) is `OPEN → ACCEPTED` — the owner/coordinator manual-override path (`ProposalController.kt:254-280`). This is **not** the transition N2 names. N2 is `Proposal.confirmPrice()` (`Proposal.kt:241-248`, `check(status == ProposalStatus.PRICE_PROPOSED)`), reached via `ProposalApplicationService.confirmPriceWithinCallerTransaction` (`:267-274`). `acceptProposalWithinCallerTransaction` (`:185-192`) is not touched by this ADR. This distinction is recorded explicitly because an equivalent imprecision — a driver-facing copy overclaim — was the finding of the D-09 QA review earlier in this engagement, and the same discipline applies here to a trigger definition instead of a copy string.

The outbox exists (`OutboxRelay.kt:33-51`) but delivers nothing user-facing; Proposal events are deliberately not wired to it (`ProposalApplicationService.kt:41-71`'s own KDoc: wiring one *"would silently create a new external contract nobody has ratified"*).

The 2-minute routing window (`DispatchRequestApplicationService.kt:192`, `ROUTING_WINDOW = Duration.ofMinutes(2)`, hardcoded, ratified by `ADR-077`) bounds how long Dispatch searches for a driver, not how long a driver has to answer — once a Proposal is `OPEN` the order leaves the expiry sweep (`:92-95`). The separate 5-minute proposal-lapse timeout (`ProposalLapseApplicationService.kt:108`, configurable) is the driver's real deadline. A known, already-disclosed consequence (`ADR-078`, Status line 5, Q-2: "leave configuration as-is") is that because the lapse timeout exceeds the routing window, `ADR-078`'s lapse-recovery path can never fire — proven by `DispatchRecoveryAfterDeclineOrLapseTest.kt:291-339`. **This ADR does not touch, fix, or depend on that.**

### Why this needs an ADR at all

`ADR-071` Part 6 (`ADR-071.md:171`) explicitly bans Web Push, service-worker customization, `pushManager`, VAPID material, and any change to `vite.config.ts`'s PWA configuration. `PRODUCT_DECISION_NOTIFICATIONS.md` §6 separately bans event-consumption by a Notifications module. Both are ratified, standing decisions. Reaching a closed app requires both — the Product Decision (`PRODUCT_DECISION_NOTIFICATIONS.md` Section 11, ratified 2026-09-19) and this ADR — to act together, per `ADR-071:25`'s own stated requirement and `CLAUDE.md`'s "Never Change Architecture Without an ADR."

---

## Problem

1. Can PIOS reach a driver whose application is closed, for exactly two facts (N1, N2), without scaffolding the Notifications bounded context, without an event consumer, and without weakening any ratified ADR beyond the minimum this specific capability requires?
2. What is the smallest architecture that satisfies the Product Owner's eight binding constraints?
3. Which exact clauses of `ADR-071` Part 6 must be superseded, and no more?

---

## Decision

### Part 1 — Shape, in one sentence

Dispatch stores a driver's push **delivery address** in its own database, and — strictly after the transaction that already performs the Proposal transition has committed — makes one best-effort, fire-and-forget Web Push request to the browser vendor's push service. Nothing else changes.

### Part 2 — What is deliberately not built (Constraints 2, 3, 7)

- **No `notifications` Gradle module.** `backend/settings.gradle.kts` is untouched. `ADR-033` Part A (database-technology gate) and Part B (event-consumption gate) both remain open and unresolved.
- **No `Notification` entity, no delivery log, no read receipt, no append-only record of what was communicated.** `ADR-033`'s reserved boundary (*"Owns: the Notification entity only — a record of what was communicated to a participant, append-only"*) stays unbuilt. A subscription row is a delivery **address**, never a record that a communication occurred.
- **No notification preferences, templates, categories, or channel selection**, beyond the one exception the Web Push standard itself makes unavoidable — the browser's own permission grant and one per-device subscribe/unsubscribe (Part 6, clause f).
- **No new domain event, no `EVENT_CATALOG.md` entry, no outbox write, no routing key, no queue, no consumer, no idempotency ledger.**
- **No RabbitMQ consumer** — proven unnecessary: both triggers are single-choke-point, in-process, same-module transitions (Context, above); the recipient is derivable with zero cross-module traffic (`Proposal.driver` is a plain `DriverReference` string, `ADR-005`/`ADR-019`); there is no Proposal event on the outbox to consume in the first place; and a consumer would buy durability this design does not want — a push older than the 2-minute routing window is actively wrong to redeliver, not merely late.
- **No SMS, no email.**

### Part 3 — Component list

**Dispatch — application layer (ports):**
- `DriverPushSubscription` — data class: `endpoint`, `driverReference`, `p256dh`, `auth`.
- `DriverPushSubscriptionRepository` — `upsert`, `findByDriver`, `deleteByDriverAndEndpoint`, `deleteByEndpoint`.
- `DriverPushNotifier` — port, exactly two methods: `offerCreated(proposalId, driver)` and `priceConfirmed(proposalId, driver)`. No third method may be added without a new decision.
- `NoOpDriverPushNotifier` — the constructor default on `ProposalApplicationService`, mirroring the existing `NoOpTransactionRunner` / `NoOpEventPublisher` / `NoOpOrderGuard` convention (`ProposalApplicationService.kt:106-119`), so every existing direct-construction test compiles and behaves identically.

**Dispatch — persistence layer (adapters):**
- `PostgreSQLDriverPushSubscriptionRepository` (mirrors `PostgreSQLDriverAvailabilityRepository`).
- `InMemoryDriverPushSubscriptionRepository` (mirrors `InMemoryProposalRepository` — dev/test only).
- `WebPushDriverPushNotifier` — implements `DriverPushNotifier`; registers an after-commit hook, then sends off the caller's thread.

**Dispatch — api layer:** `DriverPushSubscriptionController` + `RegisterDriverPushSubscriptionRequest` + `DriverPushPublicKeyResponse`.

**Dispatch — config:** one bounded `ThreadPoolTaskExecutor` bean; three `@Value` VAPID properties.

**Frontend:** `frontend/src/features/push/` (new, mirroring the existing `frontend/src/features/install/` capability-module precedent), one static service-worker script, one `vite.config.ts` option, one opt-in control + one logout step in `DriverHome.tsx`.

That is the whole of it.

### Part 4 — Trigger contract

**N1.** Fires via an after-commit callback registered immediately after `proposalRepository.save(created.proposal)` at `ProposalApplicationService.kt:159`, inside the existing transaction. Covers all five production creation paths (`DispatchRequestApplicationService.kt:143`, `FallbackDispatchApplicationService.kt:165`, `FirstRefusalApplicationService.kt:164`, `ProposalController.kt:170`/`:220`) with **zero call-site edits**. Does not fire when `handle` throws before the save (unavailable/ineligible driver, duplicate-`OPEN`, bad routing state) — the callback is never registered and the transaction never commits.

**N2.** Fires via an after-commit callback registered immediately after `proposalRepository.save(proposal)` at `ProposalApplicationService.kt:272`, inside `confirmPriceWithinCallerTransaction`. Covers both public entry points. **Does not fire on `Proposal.accept()`** (Context, above) — a hard invariant, with its own test. Does not fire when `confirmPrice()`'s own guard rejects, or when a shared-transaction Assignment step fails and rolls back the whole thing (`ProposalAssignmentOrchestrationService.kt:91-99`).

**Nothing else may ever call `DriverPushNotifier`.** Decline, decline-price, lapse, withdraw, every Assignment/Trip transition, and every passenger-facing fact are out of scope (Constraints 3, 8) — the port's two-method signature makes this a compile-level boundary, not just a convention.

**Duplicate prevention**, with no server-side delivery record: (1) the committed transition is once-only by aggregate invariant and DB constraint (`proposals_one_open_per_order`, V14; `confirmPrice`'s own status guard); (2) after-commit registration means a rolled-back call sends nothing; (3) the service worker's `showNotification` uses `tag = "<proposalId>:OPEN"` / `"<proposalId>:ACCEPTED"` with `renotify: false`, so a duplicate that still reaches the device replaces rather than stacks — reusing `ADR-071` Part 4's server-derived fact-identity property rather than reinventing it.

**If the push cannot be delivered: nothing happens to business state.** The Proposal is already committed and remains the source of truth (Constraint 7). The driver's own 3-second poll shows the offer unchanged, independent, and still the actual guarantee.

**N3 remains NO-GO.** `deriveNotificationFacts.ts:203-210` emits passenger fact P2 from the same `PRICE_PROPOSED → ACCEPTED` transition that produces N2 — that passenger branch is untouched, and no passenger subscription, endpoint, column, or payload exists.

### Part 5 — What is superseded in `ADR-071` Part 6, and only that

Per Constraint 1, narrow and clause-by-clause. `ADR-071`'s own text is never edited (Part 5, and see the append-only pointer added to `ADR-071` itself, dated 2026-09-19).

**Superseded, only for N1/N2, driver audience only:**

| Clause (`ADR-071:171`) | Superseded only to this extent |
|---|---|
| "Any backend change of any kind" | Only the Dispatch-module changes enumerated in Part 3 and Part 7. No other module. |
| "any new endpoint or change to an existing endpoint's shape" | Only the three new `/v1/driver-push-subscriptions` endpoints (Part 6). No existing endpoint's request or response shape changes. |
| "any new table, column, or migration" | Only one new table via one migration (Part 6). Zero new columns on any existing table. |
| "any Web Push, service-worker customization, `pushManager` subscription, or VAPID key material" | Only for N1 and N2, driver-only. Not for D3-D7, not for any passenger fact. |
| "any change to `frontend/vite.config.ts`'s PWA configuration" | Only the addition of one `workbox.importScripts` entry (Part 7). `registerType` and the entire `manifest` block are unchanged. |
| "any notification preferences, templates, categories, or opt-in/opt-out concept" | Only the browser's own permission grant and one per-device subscribe/unsubscribe. No preference store, no per-fact toggle, no category, no template engine. |
| "any SMS, email, or external delivery channel" | Only for Web Push to the browser-vendor push endpoint the browser itself supplies. SMS and email remain fully prohibited. Governed by `ADR-020`'s already-ratified command/synchronization philosophy, not a new one — `ADR-020` is not amended. |

**Not superseded — remain in force verbatim, stated here in terms:** any event consumption by any module (`PRODUCT_DECISION_NOTIFICATIONS.md` §6's six named prohibitions stand unamended); any scaffolding of the `notifications` module (`ADR-033` Parts A and B remain open); any new event, event version, or payload change; **any additional polling, changed poll interval, or changed request ordering in either client** (Constraint 4); any server-side record of a communication; any approximation of Part 5's other gaps; any change to `GET /v1/connections?driverId=`'s response shape; any rating, reputation, score, count, or ranking surfaced to any participant; any change to Circle of Trust, Primary Driver, Assignment, Trip lifecycle, or test/real segregation. All of `ADR-071` Parts 1-5 remain untouched — the in-app surface is not modified by this ADR in any way.

**`ADR-033` is untouched.** Both gates stay open, by name. If a future need requires a durable, auditable record of what was delivered to whom, that is the Notifications bounded context, and it requires `ADR-033` Parts A and B resolved first — this ADR creates no precedent for it.

**`ADR-071` Part 5 Gap 3 is narrowed, not closed.** After this ADR, the accurate statement is: two specific facts (N1, N2) can now reach a closed app, best-effort, on devices where the driver has granted browser notification permission and the browser vendor's push service actually delivers. Everything else about Gap 3 stands — D3-D7 do not reach a closed app, no passenger fact reaches a closed app, there is no delivery guarantee, no retry, no delivery confirmation (Part 7).

**Binding copy constraint, carried forward from the D-09 QA finding:** no release note, UI string, onboarding text, or ADR summary may state or imply that PIOS now guarantees a driver will not miss an order. Permitted: *"PIOS can notify you about a new order and a confirmed price even when the app is closed, if you allow notifications."* Prohibited: "you will never miss an order," "guaranteed delivery," any claim about D3-D7 or the passenger side.

### Part 6 — Data model and API

**Migration `V25__driver_push_subscriptions.sql`** (next free number after `V24`):

```sql
CREATE TABLE driver_push_subscriptions (
    endpoint         TEXT PRIMARY KEY,
    driver_reference TEXT NOT NULL,
    p256dh           TEXT NOT NULL,
    auth             TEXT NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX driver_push_subscriptions_driver_reference_idx
    ON driver_push_subscriptions (driver_reference);
```

`driver_reference` is a plain string identifier, never a foreign key, never a copy of Driver Management's owned data (`ADR-005`/`ADR-019`). `endpoint` is the primary key because a push endpoint URL is unique per browser subscription — re-registration UPSERTs, and a different driver logging in on the same device correctly reassigns the row. No `sent_at`, `delivery_count`, `payload`, or message-history column of any kind, ever — any such column is the `Notification` entity by another name.

**API**, base path `/v1/driver-push-subscriptions`, driver identity taken only from the verified `Bearer` token's `drv` claim, never from the request body (a body-supplied driver identifier is rejected 400):

- `GET /public-key` — `200 { publicKey }`; `404` if VAPID is unconfigured (fail-closed).
- `POST` — body `{ endpoint, keys: { p256dh, auth } }`; UPSERT on `endpoint`; `204`.
- `DELETE ?endpoint=` — deletes rows matching both `endpoint` and the caller's own `driver_reference`; always `204` (no existence disclosure).

No listing endpoint — nothing needs to enumerate a driver's devices over HTTP.

**Payload — no business data, no human-readable text at all:**

```json
{ "kind": "N1", "tag": "<proposalId>:OPEN", "url": "/driver" }
{ "kind": "N2", "tag": "<proposalId>:ACCEPTED", "url": "/driver" }
```

Per the Product Owner's ratified answer, `proposalId` is permitted in `tag` for duplicate collapse — it is opaque and unusable without the driver's own token, since every proposal endpoint independently requires a `Bearer` whose `drv` equals that proposal's own driver. Forbidden in the payload, absolutely: fare, ETA, passenger name/phone/identifier, address, order status, driver name, and any count/rating/score/metric. The service worker (Part 7) holds the two fixed copy strings and chooses by `kind`.

### Part 7 — Frontend / service worker

- `vite.config.ts`: **one line added** — `workbox: { importScripts: ['pios-push-sw.js'] }`. `registerType: 'autoUpdate'` and the entire `manifest` block unchanged. Do **not** switch to `strategies: 'injectManifest'` — that would change precache ownership and is a strictly larger change than Part 5 authorizes; if the minimal approach proves infeasible, that must be escalated back to the Architect before being taken.
- New static `frontend/public/pios-push-sw.js` — holds the two approved copy pairs, handles `push` and `notificationclick` only, issues no fetch, adds no poll:
  - N1 — «Новый заказ» / «Откройте приложение, чтобы ответить».
  - N2 — «Цена подтверждена» / «Пассажир подтвердил вашу цену».
- `DriverHome.tsx`: one explicit opt-in control (no auto-prompt on mount, ever — a browser will not grant permission without a user gesture and auto-prompting is hostile), not rendered when unsupported or when the public-key endpoint 404s; and two lines in `handleLogout` (`:1609-1618`) calling `disablePush` before `identityProvider.logout()`, since the `DELETE` needs the still-valid token. Nothing else in this file changes.
- **Polling stays completely unchanged** (Constraint 4): `DriverHome.tsx:62` and `:955-964` byte-for-byte identical; no `visibilitychange` gate added; no request added, removed, or reordered in either client; `frontend/src/features/notifications/**` untouched. Push never triggers a fetch — `notificationclick` only focuses/opens the app.
- **The 2-minute routing window and `ADR-078` stay untouched** (Constraint 5): `DispatchRequestApplicationService.kt:192-193` and `ProposalLapseApplicationService.kt:108` are not read by any push code and are not modified. `DispatchRecoveryAfterDeclineOrLapseTest.kt` must remain green, unmodified.

### Part 8 — Failure, retry, security (summary; full detail is the Developer Handoff's contract)

- Send happens strictly in `afterCommit()` of the **outermost** transaction, never after an inner `transactionRunner.run` block returns — `ProposalApplicationService.handle` is frequently nested inside `DispatchRequestApplicationService.routeSubmitted`'s own transaction.
- Every exception from the send path is caught and logged, never rethrown — a push failure must never surface as an error on an already-succeeded request.
- **No retry.** A push is only useful inside the 2-minute routing window; a redelivered push after that would describe an order that no longer exists.
- `404`/`410` from the push service deletes that subscription row (standard stale-subscription signal); `2xx` records nothing; anything else is logged and the row is kept.
- Driver identity for registration comes only from the verified session token — never a client-supplied `driverId`. One driver cannot register or delete under another driver's identity.
- VAPID keys are environment-injected, never committed; a blank private key disables push entirely (fail-closed), and the public-key endpoint then 404s so the frontend never offers the opt-in control.

---

## Alternatives Considered

- **A RabbitMQ consumer reading a Proposal-related event.** Rejected — no such event exists today (`ProposalApplicationService.kt:41-71`'s own standing refusal to wire one), and creating one would be strictly larger than the problem: durability is actively undesirable here (Part 4), and Constraint 3 asks for proof a consumer is necessary, which it is not, given the single-choke-point trigger.
- **A general Notifications module, scaffolded now, to hold this and future notifications.** Rejected — Constraint 2 forbids it outright, and it would reopen both of `ADR-033`'s gates for a capability that needs neither.
- **`strategies: 'injectManifest'` in `vite.config.ts`, for full control over the service worker.** Rejected as the default — larger blast radius than `workbox.importScripts`, changing precache ownership the existing PWA comment deliberately left alone. Named as an escalation path only if the minimal option proves infeasible.
- **A generic tag (no `proposalId`) for maximum payload minimalism.** Considered and raised to the Product Owner as an open question; the Product Owner ratified permitting the opaque `proposalId` in the tag instead, accepting the stated justification (unusable without the driver's own token) over the alternative's cost (two concurrent offers collapsing into one notification).
- **Extending `USE_CASE_CATALOG.md`/`EVENT_CATALOG.md`/`INTERFACE_CONTRACTS.md` per `PRODUCT_DECISION_NOTIFICATIONS.md` §9's literal sequence.** Considered and raised to the Product Owner as an open question; the Product Owner ratified skipping it, since §9's sequence presumes the event-consuming Notifications-module route, which this design does not take — there is no cross-module contract for those documents to name.

---

## Consequences

### Positive

- Closes the specific, disclosed gap `ADR-071` Part 5 Gap 3 named and refused to fake, for exactly the two facts the Product Owner authorized — with no backend module beyond Dispatch, no new event, and no consumer.
- `ADR-033` Parts A and B, `PRODUCT_DECISION_NOTIFICATIONS.md` §6's event-consumption prohibition, and the 2-minute routing window all remain in force and unamended — nothing is owed to a future reader as a silent disagreement.
- `NoOpDriverPushNotifier` as the constructor default preserves every existing `ProposalApplicationService` test and call site unmodified.
- Fact identity reuses `ADR-071` Part 4's server-derived-id property (via the Web Push `tag`) rather than inventing a second mechanism.

### Negative

- **A driver may not receive a push, and PIOS has no way to know it happened** — no delivery receipt, no delivery record, by design. The 3-second in-app poll remains the only actual guarantee. This is a named, disclosed limitation, not a defect to be silently improved later without a new decision.
- Key rotation invalidates every existing subscription; every driver must re-subscribe after one. No automated rotation is built.
- The `ADR-078` lapse-recovery inertness (Context, above) is unaffected by this ADR and remains unfixed — named again here only so a future reader does not mistake this ADR for having addressed it.
- Web Push requires a maintained third-party JVM library for RFC 8291/8292 (payload encryption, VAPID) — a new production dependency, named and reviewed at implementation time, not selected by this ADR.

---

## Related ADRs

- [ADR-071: In-App, Poll-Derived Notification Surface](ADR-071-In-App-Poll-Derived-Notification-Surface.md) — narrowly superseded in part by this ADR (Part 5); its append-only pointer records the reverse reference. Parts 1-5 and the rest of Part 6 remain in force.
- [ADR-033: Notifications Module and Event Consumption Boundary](ADR-033-Notifications-Module-and-Event-Consumption-Boundary.md) — the reserved bounded context this ADR deliberately stays outside of; Parts A and B remain open, unresolved, unrelieved.
- [ADR-077: Dispatch Routing Obligation](ADR-077-Dispatch-Routing-Obligation-Bounded-Retry-And-Unfulfilled-Order.md) / [ADR-078: Dispatch Recovery After Proposal Decline or Lapse](ADR-078-Dispatch-Recovery-After-Proposal-Decline-Or-Lapse.md) — the routing window and lapse-recovery mechanism, both explicitly untouched by this ADR.
- [ADR-036: Proposal/Assignment Shared Transaction](ADR-036-Proposal-Assignment-Shared-Transaction.md) — the shared-transaction rollback behavior N2's after-commit hook depends on.
- [ADR-020: Integration Philosophy](ADR-020-Integration-Philosophy.md) — command/synchronization philosophy governing the external-channel treatment of Web Push (Part 5, clause g); not amended.
- [ADR-011: Security Principles](ADR-011-Security-Principles.md) — no hand-rolled cryptography; the Web Push sender must use a maintained library for RFC 8291/8292.
- [ADR-005: Data Ownership](ADR-005-Data-Ownership.md) / [ADR-019: Conceptual Data Ownership](ADR-019-Conceptual-Data-Ownership.md) — `driver_reference` as a plain reference, never a foreign key or a copy of Driver Management's data.
- [ADR-015: Evolution Strategy](ADR-015-Evolution-Strategy.md) — the in-place, dated, narrow supersession discipline followed for the `ADR-071` pointer.

## References

Read for this ADR, none modified except the append-only pointer named above:

- `docs/PRODUCT_DECISION_NOTIFICATIONS.md` — §6, §7 (Q1), §9, Section 11 (Amendment, 2026-09-19)
- `docs/PIOS_PRODUCT_HYPOTHESES.md` — H15 (registered 2026-09-19), H10 (explicitly not overlapping)
- `docs/PIOS_PRODUCT_EVIDENCE.md` — journal (one real entry, unrelated)
- `frontend/vite.config.ts` — lines 55-87
- `frontend/src/pages/DriverHome/DriverHome.tsx` — lines 62, 955-964, 1609-1618
- `frontend/src/features/notifications/deriveNotificationFacts.ts` — lines 175-184 (D1), 193-211 (D2/P2)
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/ProposalApplicationService.kt` — lines 41-71, 106-119, 130-161, 267-274
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/domain/Proposal.kt` — lines 179, 241-248
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/ProposalAssignmentOrchestrationService.kt` — lines 91-99
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/DispatchRequestApplicationService.kt` — lines 92-101, 173-189, 192-193
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/ProposalLapseApplicationService.kt` — line 108
- `backend/dispatch/src/test/kotlin/com/pios/dispatch/application/DispatchRecoveryAfterDeclineOrLapseTest.kt` — lines 281-339
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/OutboxRelay.kt` — lines 33-51
- `backend/dispatch/src/main/resources/db/migration/dispatch/V24__via_trusted_fallback.sql` — next-free-migration-number precedent

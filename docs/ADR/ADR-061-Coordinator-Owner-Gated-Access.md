# ADR-061: Coordinator Owner-Gated Access, and a Narrow Exception to ADR-043 Decision 6

## Status

**Accepted — Product Owner ruling, 2026-08-16.**

This ADR revisits ADR-060 Decision 6, which accepted the Coordinator screen's
breakage as a deliberate consequence of that fix and named the alternative it
declined — *"giving `/coordinator` the owner login screen"* — as *"plausible …
declined as frontend work outside a P0 security fix"*. That frontend work is
now in scope. Decision 6 is **superseded by this ADR's Decision 1**; it is not
deleted (`CLAUDE.md`: "Never Delete Documentation") and ADR-060 remains the
record of why the gap existed and what alternative was weighed at the time.

This ADR also carries one product-level ruling of its own — Decision 3 below,
narrowing (not reversing) ADR-043 Decision 6's restriction on rendering
`statedPrice`. The Product Owner was presented with three options (restore
exactly as before; restore plus `requestedPickupAt` only; restore plus
`requestedPickupAt`, driver, ETA, and price) and chose the third. That ruling
is recorded here, not inferred.

**Evidence-gate note.** As with ADR-060, this proceeds on Product Owner
authority as completion of a named P0 remediation follow-up, not as a new
product initiative requiring an `E-NNN` entry. No new capability is added
beyond what Coordinator already exercised before ADR-060 (propose a driver,
check a proposal's status); what changes is who may reach the screen and how
much of the data already returned by existing, unauthenticated-by-design
endpoints (`GET /v1/proposals?orderId=`) is now rendered on it.

## Context

### What ADR-060 left broken, and why

ADR-060 Decision 1 made `GET /v1/orders` require either a Bearer token or an
owner `Basic` credential. `Coordinator.tsx` held neither — its own KDoc said so
plainly (*"no authentication — this page is reachable by anyone who navigates
to it"*) — so its order list now receives 401 and renders as a load failure.
ADR-060 Decision 6 accepted this because the pilot's real ride path does not
go through the coordinator (`PILOT_OPERATION_PLAN.md` 0.3.4) and because
fixing it was frontend work outside that ADR's own P0 scope.

### The mechanism to reuse already exists and needs no new credential

`OwnerControlCenter.tsx` already gates itself behind exactly one credential —
`Authorization: Basic`, checked by each module's own `OwnerCredentialGate`
(ADR-044) — held client-side in `sessionStorage` via `ownerCredential.ts`, with
no server session and no new principal. ADR-060 Decision 6 itself named the
reason this is the right credential to reuse rather than invent a second one:
*"the coordinator and the owner are the same human in this pilot"*
(`PILOT_OPERATION_PLAN.md`, "Что происходит, когда Артуру всё-таки нужно
посмотреть"). `ADR-044 Decision 6` ("no roles, no permissions, no user
management, exactly one owner") forbids inventing a distinct coordinator
credential; reusing the owner's is the only option that decision leaves open.

### Coordinator is not a duplicate of Owner Control Center

The two screens overlap in what they read (drivers, orders, proposals) but not
in what they are for. Owner Control Center is a **read-only monitoring
surface** — aggregated counters and a text event feed, explicitly forbidden
from rendering `statedPrice` (ADR-043 Decision 6) because its `buildReport.ts`
output is designed to be copied out of the browser (into a chat, a support
ticket) and a stray price figure in that text would leave the app's own trust
boundary. Coordinator is an **operational console** — it selects one order and
one driver and calls `POST /v1/proposals`, a write action Owner Control Center
does not and should not perform (ADR-043 Decision 5: "permanently read-only").
Merging them into one screen, or making Coordinator a thin view over
`todayData.ts`'s aggregated `TodaySnapshot`, would either remove the propose
action or force Owner Control Center to grow one it was deliberately never
given. Neither is this ADR's call to make silently.

What *should* be shared, and was not: the **data-fetching functions**.
`todayData.ts` already fetches drivers, orders, and per-driver proposals with
the owner credential attached (ADR-060 Decisions 4–5); Coordinator needs the
same drivers-and-orders reads plus a new per-order proposal read the owner
screen never needed. Decision 2 below extracts the common fetchers so
Coordinator calls the same functions rather than re-implementing its own copy
of the `Authorization: Basic` header logic a second time.

## Decision

### 1. `/coordinator` is gated behind the owner credential, exactly as `/owner` is — no new principal, no router-level guard.

`Coordinator.tsx` adopts the same pattern `OwnerControlCenter.tsx` already
uses: it holds `credential: OwnerCredential | null` in state, seeded from
`getStoredOwnerCredential()`, and renders `OwnerControlCenter/LoginScreen`
in place of the console until a credential is held. `LoginScreen` gains an
optional `subtitle` prop (default unchanged: `"Пульт владельца"`) so
Coordinator can present `"Координатор"` instead without a second login
component. No change to `app/routes.tsx` — the route stays reachable at
`/coordinator`; the gate lives inside the component, identically to how
ADR-044 Decision 5 already gates `/owner` with no router-level guard.

Because `getStoredOwnerCredential()` reads the same `sessionStorage` key
(`ownerCredential.ts`, unmodified) both screens use, an owner who has already
logged into `/owner` in the same tab session reaches `/coordinator` with no
second login prompt, and vice versa — one credential, one login, both
screens, matching ADR-044 Decision 6 exactly.

### 2. `OwnerControlCenter/todayData.ts` exports its individual fetch functions; `Coordinator.tsx` imports and reuses them instead of re-implementing its own `fetch`/`request` calls.

`fetchDrivers`, `fetchOrders`, `fetchProposalsForDriver`, `fetchProposalsForOrder`,
and `fetchAssignmentsForOrder` become named exports of `todayData.ts`, each
doing exactly what its inline call inside `loadTodaySnapshot` did before, with
the `.catch(() => [])` swallowing moved to each *call site* rather than baked
into the function — `loadTodaySnapshot` keeps swallowing per-source failures
(ADR-043's "a partial event feed is more useful than none" reasoning,
unchanged), while `Coordinator.tsx` does not, because it needs to distinguish
"failed to load" from "genuinely empty" for its own `driversStatus`/
`ordersStatus` state, exactly as it already did before this ADR.

This is the one piece of genuine duplication ADR-060 introduced (both screens
independently learned to attach `Authorization: Basic` to `GET /v1/orders`
and `GET /v1/proposals?driverId=`) and this ADR removes it, per this task's
own instruction not to build a second parallel implementation of the same
authorized fetch. It is not a new shared *module* in the `MODULE_STRUCTURE.md`
Section 4 sense — that rule governs sharing between backend bounded contexts;
these are two React pages in the same frontend build importing a sibling
page's exported function, no different in kind from `Coordinator.tsx`
already importing `DriverCard` from `components/`.

`fetchProposalsForOrder(orderId)` calls `GET /v1/proposals?orderId=...` with
no `Authorization` header — ADR-060 Decision 4 left this branch deliberately
open (an enumeration *sink*, not a *source*: the caller must already hold the
order id, which Coordinator does, from its own now-authenticated order list).
No backend change accompanies this ADR; every endpoint Coordinator calls
already exists and already has the access rule ADR-060 gave it.

### 3. Coordinator's order rows may show `requestedPickupAt`, the proposed driver, `statedEtaMinutes`, and `statedPrice`. This narrows ADR-043 Decision 6 for this one screen only.

**Product Owner ruling, 2026-08-16: the fullest of three offered options,
chosen explicitly.** Before this ADR, Coordinator showed only `passengerName`,
`pickupAddress`, `destination`, and the order's own status — it never had
per-order driver/ETA/price display, and ADR-057/ADR-058 (which added
`statedEtaMinutes` and `requestedPickupAt` to the platform) never reached this
screen. After this ADR, each `SUBMITTED` (or otherwise selectable) order row
additionally shows, when a proposal exists for it:

- 📅 a "Предварительный заказ" badge with the formatted `requestedPickupAt`,
  identical presentation to `DriverHome.tsx`'s own badge (ADR-058);
- the proposed driver's display name (resolved against the already-loaded
  driver list, the same `driverLabel` pattern `todayData.ts` already uses for
  its event feed text);
- the proposal's own status label, reusing the existing
  `PROPOSAL_STATUS_LABEL` map (now including `WITHDRAWN`, added by the P0-2
  cancellation feature and previously missing from this map);
- `statedPrice` and `statedEtaMinutes`, once the proposal is `ACCEPTED`,
  in the same "Стоимость: …" / "…мин" wording `DriverHome.tsx` already
  established (ADR-042, ADR-057).

**Why this is a narrowing, not a repeal, of ADR-043 Decision 6.** That
decision's own KKDoc trail (`todayData.ts`, pre-existing comment) ties the
restriction to *rendering, summing, or including it in the report* —
`buildReport.ts`'s clipboard-copy output, a document designed to leave the
browser. Coordinator has no report-generation feature, produces no exportable
text, and is reached by the same single credential as Owner Control Center.
The Product Owner, holding that same credential, chose to see price on this
operational screen. `buildReport.ts` is untouched by this ADR and continues
never to read, sum, or emit `statedPrice` — the original decision's actual
mechanism of harm (price leaving the app in copyable text) is fully preserved.

**Which proposal is "the" proposal for an order.** An order may have more than
one `Proposal` over its lifetime (declined, then re-proposed to another
driver). Coordinator shows the most recently created one
(`proposals.sort by createdAt desc`, ties broken by array order, mirroring
`todayData.ts`'s own event-sort convention) — the one the coordinator would
act on next, not a history. This is a presentation choice, not a new backend
query; `findByOrder` already returns the full set unfiltered, unchanged.

**No `AssignmentController` data is added.** Driver arrival/in-progress/
completion status remains visible only via Owner Control Center's event feed,
unchanged — Coordinator's per-order display is limited to what its `Proposal`
already carries, which does not include assignment lifecycle detail. Adding
that is a separate, unrequested expansion of this screen's scope.

### 4. Coordinator's write actions (`POST /v1/proposals`, `GET /v1/proposals/{id}`) are unchanged — they were never gated by ADR-060 and remain exactly as they were.

Both are endpoints ADR-060 explicitly left unauthenticated (`Deliberately not
done here`). Gating them behind the owner credential now, on top of gating the
*page* itself, would be redundant defense with no additional data at risk —
the operation is a write of a proposal, not a read of passenger data — and is
out of this ADR's scope. If this pilot later needs coordinator actions
audited or restricted beyond "reachable only by the owner credential," that is
a separate decision.

## Access summary — `/coordinator` after this ADR

| State | Behaviour |
| --- | --- |
| No credential held | `LoginScreen` (subtitle "Координатор") renders; no network call to any gated endpoint is made |
| Wrong credential entered | `LoginScreen`'s own existing "Неверный логин или пароль" / "Сейчас не удаётся проверить пароль" states (unchanged, `verifyOwnerCredential`) |
| Valid credential held | `GET /v1/drivers` (unauthenticated, unchanged) and `GET /v1/orders` (`Authorization: Basic`, Mode 3 of ADR-060) both load; for each order, `GET /v1/proposals?orderId=` (unauthenticated sink, unchanged) loads its proposal history |
| Credential cleared (logout) | Console unmounts, screen reverts to `LoginScreen`, no residual data fetch |
| Page reload with a still-valid `sessionStorage` credential | `getStoredOwnerCredential()` returns it on the initial `useState` seed, exactly as `OwnerControlCenter.tsx` already behaves — no re-login required, matching ADR-044 Section 4.3 |

## What implementation must verify

- Unauthenticated `GET /v1/orders` and `GET /v1/proposals?driverId=` remain
  exactly as closed as ADR-060 left them — this ADR adds a caller that sends
  the credential, it does not loosen the gate itself. No test may assert that
  Coordinator can read orders *without* a credential.
- Reload behaviour: mount with a credential already in `sessionStorage` shows
  the console directly, with a fresh fetch (not stale state) — mirroring how
  `OwnerControlCenter.tsx`'s own `useState(() => getStoredOwnerCredential())`
  already works, reused unmodified.
- `todayData.ts`'s existing behaviour for `OwnerControlCenter.tsx` is a pure
  regression concern: `loadTodaySnapshot` must return byte-identical shapes
  and continue swallowing per-source failures after its internal refactor to
  call the newly exported fetchers.
- `statedPrice` must still never appear in `buildReport.ts`'s output — that
  file is not touched by this ADR and must stay that way.
- Coordinator's propose/check-status flow (`POST /v1/proposals`,
  `GET /v1/proposals/{id}`) continues to work unauthenticated, unaffected by
  the new page-level gate.

## Deliberately not done here

No change to `app/routes.tsx`. No new credential or role. No change to
`AssignmentController`, `buildReport.ts`, `statusEvaluation.ts`, or any
backend endpoint or contract — every endpoint this ADR's frontend change calls
already exists with the access rule ADR-060 gave it. No auto-refresh/polling
added to Coordinator (it still loads once per credential, matching its
pre-ADR-060 behaviour); adding live polling is a separate, unrequested
feature. `docs/PILOT_OPERATION_PLAN.md` Sections 0.1.3, 0.4, and the "Что
происходит, когда Артуру всё-таки нужно посмотреть" narrative are now further
out of date (they still describe a screen with no login and an English UI,
which predates this fix and predates a separate, earlier Russian-localization
pass this ADR did not investigate) — correcting that document is named here as
required follow-up, not performed as part of this ADR, consistent with
ADR-060's own "Follow-up this ADR creates" item 1.

## Consequences

- The owner (Артур) regains the only screen that lists every order
  individually and the only screen from which a ride can be proposed
  manually, both lost under ADR-060 Decision 6 — now reachable with the same
  one credential he already uses for `/owner`.
- Coordinator's order rows disclose more than before ADR-060 did: driver
  assignment, ETA, and price per order, none of which the pre-ADR-060 screen
  ever showed. This is new information exposure, scoped to the single owner
  credential, accepted knowingly (Decision 3).
- `todayData.ts` gains five named exports and loses its own inline fetch
  logic in `loadTodaySnapshot`, which now calls them — `OwnerControlCenter.tsx`
  is not otherwise changed and its behaviour is unchanged.
- `LoginScreen.tsx` gains one optional prop; `OwnerControlCenter.tsx`'s own
  call site is unaffected by the new default.
- `docs/PILOT_OPERATION_PLAN.md` becomes stale in a second, larger way (noted
  above) that this ADR does not fix.

## Traceability

| Source | Relationship |
| --- | --- |
| `ADR-060` Decision 6 | Superseded — the alternative it named and declined is what this ADR builds |
| `ADR-060` Decisions 1, 4, 5 | The access modes this ADR's new caller uses (Mode 3 for `GET /v1/orders`, the owner branch of `?driverId=` — unused by Coordinator but preserved), unchanged |
| `ADR-044` Decisions 5, 6 | Gate pattern reused verbatim; "exactly one owner credential" is why no second credential is introduced |
| `ADR-043` Decision 6 | Narrowed for Coordinator only; `buildReport.ts`'s own restriction is preserved unchanged |
| `ADR-043` Decisions 1, 3, 5 | Owner Control Center's browser-side, fan-out, read-only shape — unaffected |
| `ADR-057`, `ADR-058`, `ADR-042` | Source of `statedEtaMinutes`, `requestedPickupAt`, `statedPrice` — now surfaced on Coordinator for the first time |
| `CLAUDE.md` | "Never Delete Documentation" — ADR-060 Decision 6 stays on record; "smallest correct change" — no router change, no new backend endpoint |

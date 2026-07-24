# PIOS Frontend

This directory is the frontend area established by **Sprint 0 — Frontend Foundation**, extended by **Sprint 1 — Driver Home**, extended by **Sprint 2 — Driver Invitation Flow (UI Only)**, extended by **Sprint 3 — First Real Identity (Local Only)**, extended by **Sprint 4 — Request Your Driver (Mock Only)**, extended by **Sprint 5 — First Backend Integration**, extended by **Sprint FR-001 — Connect RideRequest**, extended by **Sprint FR-002 — Driver Availability**, extended by **Sprint FR-003 — Order Query**, and extended again by **Sprint FR-004 — Manual Assignment**. It provides the frontend project scaffold, application shell, and the platform's first complete UI flow — a driver shares an invitation, a passenger opens it, becomes a locally-remembered identity, and requests a ride — plus an operational screen for the human Coordinator role (`docs/NETWORK_PILOT_LAUNCH_KIT_V1.md`) that now covers the full manual pairing loop: see who exists, pick an order and a driver, and record the assignment. As of Sprint FR-004, four independent pieces are backed by real, running backend modules (Driver Home's driver data and availability, Ride Request's order submission, the Coordinator's driver/order lists, and the Coordinator's own Assign action against Dispatch); invitation and passenger identity remain mocked/local. See [../project-brain/AI_HANDOFF.md](../project-brain/AI_HANDOFF.md) and [../project-brain/PIOS_MVP_V01.md](../project-brain/PIOS_MVP_V01.md) for the product context this work exists to eventually serve.

Technology follows [ADR-024: Frontend Technology Decision](../docs/ADR/ADR-024-Frontend-Technology-Decision.md): TypeScript, React, component-based/unidirectional-data-flow style. This is the web half of that decision (React Native, the mobile half, is not used here — a PWA is an installable *web* app).

## What Exists at This Milestone

An installable, mobile-first PWA shell with four screens:

- **Driver Home** (`/`) — a driver's own **id and availability, fetched from the real Driver Management backend**, an invitation QR placeholder, and an invitation link, with working **Copy** (Clipboard API) and **Share** (Web Share API, with a clipboard fallback) actions. Shows a loading state while the fetch is in flight and an error state if it fails.
- **Passenger Landing** (`/i/:driverCode`) — the first passenger-facing screen, shown after opening an invitation link locally. It is a small local flow: who-invited-you → a one-field name form → a greeting — with the resulting identity **genuinely persisted** in the browser's `localStorage` (see "Local Identity" below). Returning to the same invitation link on the same device skips straight to the greeting, which now offers **Request a Ride**.
- **Ride Request** (`/i/:driverCode/request`) — a destination + optional notes form; submitting genuinely calls the real Order Management backend (`POST /v1/orders`) and shows the real order id it returns (see "Connect RideRequest" below).
- **Coordinator** (`/coordinator`) — a plain, unauthenticated list of every order (`GET /v1/orders`) and every driver and their real, current availability (`GET /v1/drivers`), reusing `DriverCard` (see "Driver Availability" and "Order Query" below); the coordinator can now select one order and one driver and press **Assign** to genuinely create an Assignment in Dispatch (`POST /v1/assignments`, see "Manual Assignment" below).

The invitation itself (who invited whom, the driver's own display attribution) is still temporary mock data. No authentication, no server-side invitation persistence, and no dispatcher interaction exist anywhere in this project — all deliberately out of scope so far (see "What Was Intentionally NOT Implemented" below).

## Directory Layout

```
frontend/
├── index.html                    Vite entry HTML (title: PIOS, manifest/service-worker injected at build time)
├── vite.config.ts                Vite config: React plugin + vite-plugin-pwa (manifest, installability)
├── public/
│   ├── favicon.svg                Placeholder mark (browser tab icon)
│   └── pios-icon.svg              Placeholder mark (PWA manifest icon — same mark, separate file/use)
└── src/
    ├── main.tsx                   Entry point: mounts <App /> inside a BrowserRouter
    ├── index.css                  Minimal global reset — no design system, no UI library
    ├── app/
    │   ├── App.tsx                 Root shell component — composes routing only, nothing else
    │   └── routes.tsx              Route table (see routes below)
    ├── api/
    │   └── apiClient.ts            API abstraction layer — the single `fetch` entry point (real, as of Sprint 5)
    ├── persistence/
    │   └── localPassengerIdentity.ts  Local-storage abstraction — the only file touching `localStorage`
    ├── components/                 Reusable, presentational, prop-driven UI components
    │   ├── Header/                  App-wide top bar ("PIOS" brand)
    │   ├── DriverCard/              Driver name + code display
    │   ├── QRCard/                  QR placeholder + invitation link + Copy/Share actions + feedback text
    │   └── ActionButton/            Generic labeled button (used across all three pages)
    └── pages/
        ├── DriverHome/
        │   ├── DriverHome.tsx        Composes Header + DriverCard + QRCard; fetches real driver data
        │   ├── DriverHome.module.css Mobile-first, responsive page layout
        │   └── currentDriver.ts      Local driver id constant + mock invitation link helper (see below)
        ├── PassengerLanding/
        │   ├── PassengerLanding.tsx        Local step flow: invited → onboarding → greeting (+ restore)
        │   ├── PassengerLanding.module.css Mobile-first, responsive page layout
        │   └── invitationSource.ts         Invitation-resolution abstraction (see below)
        ├── RideRequest/
        │   ├── RideRequest.tsx        Step flow: form → confirmed; submits to the real Order Management backend
        │   └── RideRequest.module.css Mobile-first, responsive page layout
        └── Coordinator/
            ├── Coordinator.tsx        Lists orders (GET /v1/orders) and drivers (GET /v1/drivers); selects one of each and assigns (POST /v1/assignments)
            └── Coordinator.module.css Mobile-first, responsive page layout
```

Routes: `/` → DriverHome, `/i/:driverCode` → PassengerLanding, `/i/:driverCode/request` → RideRequest, `/coordinator` → Coordinator.

Each component folder follows the same shape: `Component.tsx`, `Component.module.css` (CSS Modules — scoped styles, no UI library, no global class collisions), and an `index.ts` barrel export.

**Not yet created, intentionally:** a `shared/` layer for cross-cutting concerns (types, utilities) used by more than one page. Nothing built so far needs one; adding it now would be a speculative abstraction. Add it when a second page actually needs to share something with the first.

## Component Structure and Design Decisions

- **`Header`** — takes no props. "PIOS" is the application's own name, not participant-specific data needing future injection, unlike everything below it.
- **`DriverCard`** — `{ driverCode, availability, onClick?, selected? }`. Purely presentational; renders an initial-letter avatar, the driver's id, and an availability badge. Sprint 5 removed a `driverName` prop this component used to take — no name or profile exists anywhere in the real backend's `Driver` aggregate (see "First Backend Integration" below), so nothing invents one here. Reused as-is by `Coordinator` (Sprint FR-002) for each row of its driver list. Sprint FR-004 added `onClick`/`selected`, both optional and defaulting to "not selectable" — every existing caller (`DriverHome`) is unaffected; `Coordinator` now passes both to let the coordinator pick a driver to assign.
- **`QRCard`** — `{ invitationLink, linkTo?, onCopy?, onShare?, feedback? }`. Renders a **static, hand-authored SVG placeholder** resembling a QR code's finder squares and module grid — it encodes nothing and is not a real, scannable QR code; no QR-generation library was added, consistent with "QR: placeholder only" scope. `linkTo` (Sprint 2) is an optional in-app route; when given, the displayed link renders as a `react-router` `Link` to it, purely so the link is testable locally — the component still has no idea what an "invitation" is. `onCopy`/`onShare`/`feedback` are all optional and controlled entirely by the caller; this component never decides what "copy" or "share" do, only how to display the result.
- **`ActionButton`** — `{ label, icon?, onClick?, variant?, disabled? }`. A generic, reusable labeled button, not aware of what action it triggers — used for Copy/Share on Driver Home, Continue/Request a Ride on Passenger Landing, the submit action on Ride Request, and (Sprint FR-004) the Coordinator's own Assign action. `disabled` was added by Sprint FR-004 so Assign can stay inert until both an order and a driver are selected; optional and defaults to `false`, so every earlier caller is unaffected.
- **`DriverHome`** — the page. Fetches the current driver's real data on mount (`loading` → `error` | `ready`), passes the result down as props, and owns the actual Copy/Share behavior and transient feedback state — see "First Backend Integration" below.
- **`PassengerLanding`** — the page. Reads `driverCode` from the route, resolves it through `invitationSource.ts`, and drives a small local `step` state machine (`loading` → `not-found` | `invited` → `onboarding` → `greeting`) — see "Local Identity" below. It reads/writes identity only through `persistence/localPassengerIdentity.ts`, never `localStorage` directly. Its greeting step (Sprint 4) navigates to Ride Request.
- **`RideRequest`** — the page. Reads `driverCode` from the route, redirects back to Passenger Landing if no local identity exists yet, and drives its own `step` state machine (`loading` → `not-found` | `form` → `confirmed`) — see "Connect RideRequest" below. It never calls `localStorage` directly, but (as of Sprint FR-001) does call the real Order Management backend on submit.
- **`Coordinator`** — the page. Fetches `GET /v1/orders` and `GET /v1/drivers` independently on mount (each its own `loading` → `error` | `ready`), renders one row per order and one `DriverCard` per driver returned. Sprint FR-004 added click-to-select on both lists and an **Assign** button that calls `POST /v1/assignments` on Dispatch — see "Order Query" and "Manual Assignment" below. Still no filtering, search, sorting, auto-refresh, or authentication.

**Why props-only, no internal data fetching in any component:** every component in `components/` takes its data as props and contains no knowledge of where that data comes from — fetching (mock or real) happens only in `pages/`. This is exactly what let Sprint 5 replace `DriverHome`'s data source with a real backend call without touching `DriverCard` or `QRCard` at all, beyond the prop shape change already described above.

**Why CSS Modules, still no UI library:** consistent with Sprint 0's own decision — nothing built so far justifies a component/design-system dependency. CSS Modules give per-component scoped styles without one.

**Mobile-first layout:** `DriverHome.module.css`, `PassengerLanding.module.css`, `RideRequest.module.css`, and `Coordinator.module.css` are all written base-first for a phone-width viewport (single column, 44px-minimum touch targets, `100svh` height), with one `min-width` media query adding breathing room on larger screens — none of these layouts ever grows into a stretched desktop page; each stays a centered, single-column, app-like column at every width. `Coordinator` uses the same mobile-first column even though it is more likely to be opened on a laptop — no desktop-specific layout was introduced, consistent with this project's own "no UI component library / no design system beyond what is already built" stance.

## Driver Invitation Flow (Sprint 2)

This is the platform's first end-to-end UI flow: a driver shares an invitation, a passenger opens it. Still no backend, persistence, or authentication is involved anywhere in it.

**New route:** `/i/:driverCode`, rendering `PassengerLanding`. `App.tsx`/`routes.tsx` route table gained this one entry alongside `/`.

**Copy:** `DriverHome`'s `handleCopy` calls `navigator.clipboard.writeText(invitationLinkFor(driver.id))` (`currentDriver.ts`) and shows a transient "Copied" status (via `QRCard`'s `feedback` prop) for 2 seconds. If the Clipboard API rejects (e.g. no permission, insecure context), it shows "Could not copy" instead — no data or feature ever throws.

**Share and its fallback:** `DriverHome`'s `handleShare` calls `navigator.share(...)` when `navigator.share` exists (most mobile browsers, some desktop browsers), opening the native OS share sheet with the invitation link. On a browser without the Web Share API (e.g. most desktop browsers today), it falls back to the same clipboard copy as above, with feedback text "Link copied" so the user knows what happened instead of nothing appearing to occur. A user cancelling the native share sheet (`AbortError`) is not treated as a failure — no error feedback is shown for it.

**Local navigation:** the invitation link is still displayed as a realistic-looking external URL (`https://pios.local/i/ILDAR001`, built by `invitationLinkFor()`), but `DriverHome` also passes `QRCard` a separate `linkTo={`/i/${driver.id}`}` prop, so clicking the displayed link in a local dev server navigates in-app to the real `PassengerLanding` route via `react-router`'s `Link`, with no backend lookup involved.

**Prepared for future backend integration:** `PassengerLanding` never touches mock data directly. It calls `getInvitationByDriverCode(driverCode)` from `invitationSource.ts`, which returns a `Promise<InvitationInfo | null>` — the same shape a real `fetch`/API-client call would return. Today that function resolves from a fixed, in-memory directory (`MOCK_INVITATIONS`); a future task can replace its body with a real network call (through `api/apiClient.ts`) without changing `PassengerLanding.tsx`, its loading/not-found states, or any prop contract at all.

## Local Identity (Sprint 3)

The first *persistent* user experience in PIOS: a passenger who completes onboarding is genuinely remembered by their own browser, with no backend involved. This is deliberately named "local identity," not "account" or "registration" — there is no server that knows this identity exists.

**Flow:** Passenger Landing is a small state machine:

1. `loading` — resolving the invitation.
2. `not-found` — unrecognized `driverCode`.
3. `invited` — "You were invited by \<driver\>" + `Continue` (unchanged from Sprint 2).
4. `onboarding` — shown after tapping `Continue`, **only if no local identity exists yet**: "How should we address you?" and a single `Name` field.
5. `greeting` — "Hello, \<name\>" / "You joined through" / \<driver\>, shown either right after onboarding, or immediately (skipping steps 3–4 entirely) if a local identity is already on this device.

**Validation:** the name field is required and capped at 50 characters — enforced both at the input (`maxLength={50}`) and again explicitly on submit (empty/whitespace-only is rejected; a stray >50-character value, e.g. from a pasted string, is also rejected), so nothing depends on the browser honoring the HTML attribute alone.

**Persistence abstraction:** `persistence/localPassengerIdentity.ts` is the *only* file in this project that calls `localStorage`. It exposes exactly two functions:

- `getPassengerIdentity(): PassengerIdentity | null` — reads and parses the stored identity, defensively (a malformed or missing value returns `null` rather than throwing).
- `savePassengerIdentity(name: string): PassengerIdentity` — generates an `id` (`crypto.randomUUID()`, with a fallback for environments without it), stamps `createdAt` (ISO timestamp), stores `{ id, name, createdAt }` as JSON under a single fixed key, and returns it.

No component or page reads or writes `localStorage` itself — `PassengerLanding.tsx` only calls these two functions and holds the result in React state.

**Identity lifecycle:** one browser holds at most one local passenger identity today. It is created once, at the end of onboarding, and never modified or deleted by anything in this codebase — there is no edit-name, sign-out, or reset flow yet (none was in scope). Opening any invitation link again on the same device, for any driver, restores the same identity and goes straight to the greeting; the driver name shown there always reflects the *current* invitation being opened, not the one that originally created the identity.

**Future backend replacement:** when a real passenger account/session concept exists, only `persistence/localPassengerIdentity.ts`'s implementation changes (e.g. to read/write through `api/apiClient.ts` and a real session instead of `localStorage`) — its two function signatures and the `PassengerIdentity` shape are the contract `PassengerLanding.tsx` already depends on, so the page itself would not need to change.

## Ride Request Flow (Sprint 4; connected to a real backend in Sprint FR-001)

The first passenger *action* in PIOS, beyond just viewing screens: requesting a ride from the driver whose invitation was opened.

**Entry point:** Passenger Landing's greeting step shows "Joined through: \<driver\>" followed by a **Request a Ride** button, which navigates to `/i/:driverCode/request`.

**Guard:** `RideRequest` reads the local passenger identity (via `getPassengerIdentity()`) once, on first render. If none exists — e.g. this URL was opened directly, skipping onboarding — it redirects back to `/i/:driverCode` (`<Navigate replace />`) rather than asking for a name a second time in a different place.

**Form:** a `Destination` field (required) and an optional `Notes` field. Submitting validates `Destination` is non-empty after trimming (`"Destination is required."` shown otherwise); `Notes` has no validation, consistent with being optional.

**Sprint FR-001 — genuinely connected.** Submitting now calls the real, already-existing `POST /v1/orders` on Order Management directly (mirroring `DriverHome`'s own direct-module-call precedent from Sprint 5), through `api/apiClient.ts`'s `request()`. `services/rideRequestService.ts` (the in-memory mock this replaced) is deleted.

**What is actually sent — and what is not.** The real backend contract accepts only `{ passengerReference }` (`backend/order-management/.../api/SubmitOrderRequest.kt`) — it has no field for `destination` or `notes`, and never has: an earlier attempt to add one (Sprint FND-006) was reverted within the same sprint specifically because it broke this same contract for its one real caller (see that sprint's own report). `Destination` and `Notes` are therefore still collected by this form, and `Destination` is still required client-side, but **neither is transmitted anywhere** — see "What Remains Mock" below. This is disclosed here deliberately rather than silently hidden.

**Errors.** A failed request (network failure, non-2xx response) shows "Could not submit your ride request. Please try again." inline on the form, via `apiClient`'s `ApiError`/thrown-`fetch`-rejection; the user stays on the form and can retry. Double-submission is guarded (`isSubmitting`) — Order Management's own docs flag Submit Order as not idempotent, so a blind retry could create a duplicate order.

**Confirmation, corrected to match actual behavior.** Previously read "Ride request prepared. Your driver: \<name\> will receive this request." — false once connected to the real backend: the created `Order` carries only an `origin` (the passenger's local identity id) and has **no** field associating it with any specific driver, so nothing about the real system actually notifies "\<name\>" of anything. The screen now shows only what is verifiably true: "Ride request submitted." and the real `Order ID` returned by the backend.

**Two backend modules, one frontend.** `RideRequest.tsx` calls Order Management directly, on its own local port (`http://localhost:8083` by default, `VITE_ORDER_MANAGEMENT_BASE_URL` to override) — distinct from `apiClientConfig.baseUrl` (Driver Management's port, used by `DriverHome`). `api/apiClient.ts`'s `request()` gained an optional `baseUrl` override on its `init` parameter for exactly this; omitting it leaves every existing caller (Driver Home) unchanged.

## First Backend Integration (Sprint 5)

The first piece of this frontend backed by a real, running backend, instead of mock data: Driver Home's own driver information.

**Scope, deliberately narrow:** only Driver Home's driver data. Invitation, passenger identity, and ride-request logic all remain exactly as mocked as before this sprint — this section changes none of them.

**The backend contract:** `GET http://localhost:8081/v1/drivers/{driverId}` on Driver Management, returning `{ id: string, availability: "AVAILABLE" | "UNAVAILABLE" }` (200), 404 for an unknown id, or 400 for a blank one. This exposes the Retrieve Driver Availability query Driver Management already owned conceptually (`docs/APPLICATION_ARCHITECTURE.md` Section 7, `docs/API_SPECIFICATION.md` Section 7) — see `backend/driver-management/src/main/kotlin/com/pios/drivermanagement/api/DriverController.kt`.

**Why `name` disappeared from Driver Home:** earlier sprints' `mockDriver.ts` had a `name` field, but the real `Driver` aggregate in the backend has only `id` and `availability` — by deliberate design (`Driver.kt`'s own KDoc: "covers only the availability capability... standing and participation are outside this capability's scope"). Rather than inventing a name/profile field the domain model does not have, `DriverCard` was changed to show what is actually real: the driver's id and availability. Adding a real name/profile concept would be a genuine architecture change (a new field on the `Driver` aggregate, a `DOMAIN_MODEL.md`/`API_SPECIFICATION.md` amendment, and an ADR) and was explicitly left out of this sprint's scope.

**`api/apiClient.ts` is now real.** `request<T>(path)` performs an actual `fetch` against `apiClientConfig.baseUrl + path`, parsing JSON on success and throwing `ApiError` (carrying the HTTP status) on any non-2xx response. `apiClientConfig.baseUrl` defaults to `http://localhost:8081` (Driver Management's own local port, per `docs/INTERFACE_CONTRACTS.md`) when `VITE_API_BASE_URL` is unset — the only backend module this frontend calls today, so a working default costs nothing; it is fully overridden by `VITE_API_BASE_URL` once more than one module is involved.

**`currentDriver.ts` replaces `mockDriver.ts`.** No driver authentication or session exists yet (explicitly out of scope), so `CURRENT_DRIVER_ID` (`'ILDAR001'`) remains a fixed local constant standing in for "which driver this app instance represents" — the one thing about Driver Home still hardcoded. `invitationLinkFor(driverId)` replaces the old mock `invitationLink` field, unchanged in behavior.

**Loading and error states:** Driver Home is `loading` → `error` | `ready`. While the fetch is in flight it shows "Loading driver…"; if it fails for any reason (network error, 404, 500) it shows "Could not load driver information." — the two states are not distinguished further, since no different recovery action exists for either yet.

**Architecture boundaries preserved:** `DriverCard` and `QRCard` did not gain any knowledge of `fetch`, `apiClient`, or backend shapes — `DriverHome` still fetches, still owns loading/error state, and still passes plain props down, exactly as `PassengerLanding` already does for `invitationSource.ts`. No new abstraction layer was introduced beyond `apiClient.ts` itself, which already existed for exactly this purpose since Sprint 0.

## Driver Availability (Sprint FR-002)

The first software-backed screen for the human Coordinator role that already existed operationally (`docs/NETWORK_PILOT_LAUNCH_KIT_V1.md`, Part 3), plus the backend write path that makes a driver's own `AVAILABLE`/`UNAVAILABLE` declaration real for the first time.

**New backend endpoints (Driver Management, no other module touched):**

- `GET /v1/drivers` — every driver, `{ id, availability }[]`, unfiltered and unordered.
- `POST /v1/drivers/{driverId}/availability`, body `{ availability: "AVAILABLE" | "UNAVAILABLE" }` — declares the given driver's availability, exposing the already-owned Declare Availability command for the first time over REST. 404 for an unknown driver, 400 for any other value (there are only two — no `BUSY`, no `OFFLINE`, deliberately, per this sprint's own scope).

**How a driver actually becomes `AVAILABLE`:** only through a direct call to the new `POST` endpoint above. **No UI toggle exists in this app yet** — `DriverHome` still only *displays* availability (Sprint 5); this sprint's own permitted scope named the backend endpoint and the Coordinator's list page, not a driver-facing control. For the very first real drivers, this means an engineer (or the driver themself, given the URL) calling the endpoint directly — the same minimal, no-UI-needed approach already used for the first real ride end-to-end.

**Coordinator (`/coordinator`):** fetches `GET /v1/drivers` once on mount and renders one `DriverCard` per driver — nothing else. No polling, no WebSocket, no manual refresh button; reloading the page is today's only way to see a change. No filtering or search: at pilot scale (a handful of drivers) the coordinator reads the whole list directly. No authentication: this page is reachable by anyone who knows or guesses the URL, same as every other page in this project — adding auth was explicitly out of this sprint's scope, not an oversight.

**What this page cannot do:** change any driver's availability. Only the driver's own declaration does that (`DriverAvailabilityApplicationService` — "Driver Management alone records a driver's availability state"); giving the Coordinator a button to flip it would cross that already-established ownership boundary. If a coordinator needs a driver to come online, they still have to ask them — by phone, exactly as `NETWORK_PILOT_LAUNCH_KIT_V1.md` already assumes.

## Order Query (Sprint FR-003)

Gives the Coordinator page a second, independent view: every existing order, ahead of Sprint FR-004's assignment scenario.

**New backend endpoint (Order Management, no other module touched):** `GET /v1/orders` — every order, `{ id, status, origin }[]`, unfiltered and unordered. Exposes a new `RetrieveOrdersHandler`/`OrderQueryController`, additive next to the already-existing `POST /v1/orders` (`OrderSubmissionController`), which this sprint did not touch.

**Coordinator (`/coordinator`):** gained an "Orders" section, fetched independently of the "Drivers" section (its own `loading`/`error`/`ready` state) — a failure loading one does not block the other. Each row shows the order's id, status, and origin — the only fields `OrderQueryController`'s response carries. No `OrderCard` component was introduced: unlike a driver row, an order row has no avatar/initial concept and no precedent to reuse, so a plain row was the smaller change.

**What this section cannot do (as of Sprint FR-003):** act on any order — no assign, cancel, or complete button. It only reads. (Sprint FR-004, immediately after, adds selection and Assign — see below.)

## Manual Assignment (Sprint FR-004)

Closes the Coordinator's first write action beyond viewing: pairing an existing order with an existing driver, recorded as a real `Assignment` in Dispatch.

**New backend endpoint (Dispatch, no other module touched):** `POST /v1/assignments`, body `{ orderId, driverId }` → `{ assignmentId, status: "CREATED" }` (201). Exposes the already-existing `Assignment` aggregate and `DispatchAssignmentApplicationService.handle` for the first time over REST (`AssignmentController`), plus one additive repository method, `AssignmentRepository.findByOrder`, so the controller can supply `Assignment.create`'s own one-active-assignment-per-order invariant with the order's real existing assignments instead of an empty list. 400 for a blank `orderId`/`driverId`; 409 if the order already has an active assignment.

**Coordinator (`/coordinator`):** both the Orders section and the Drivers section became clickable — clicking a row selects it (clicking it again, or selecting a different row of the same kind, deselects/switches), with a visible selected outline (`DriverCard`'s new `selected` prop; a matching `selectedRow` class for order rows). An **Assign** button below both lists (`ActionButton`, now supporting `disabled`) stays inert until one order and one driver are both selected. On press, it calls Dispatch's `POST /v1/assignments` directly, on its own local port (`http://localhost:8084` by default, `VITE_DISPATCH_BASE_URL` to override) — a third backend module this frontend now calls directly, alongside Driver Management and Order Management. On success it shows "Assignment created: \<id\> (CREATED)" and clears the selection so another pair can be assigned next; on failure it shows an inline message, distinguishing "this order already has an active assignment" (409) from a generic retry message.

**No selection rule invented.** Every order and every driver is selectable regardless of status or availability — this page enforces nothing Dispatch's own `AssignmentController` does not already enforce itself (which is only the one-active-assignment-per-order check). A `COMPLETED`/`CANCELLED` order or an `UNAVAILABLE` driver can still be selected and assigned; the backend does not reject either, and this sprint's own scope explicitly excludes inventing new validation.

**What this still cannot do:** nothing here lets a driver accept an assignment (a separate, later capability), shows any indication that a driver is already assigned elsewhere (`GET /v1/drivers` is unchanged — availability is not updated by an assignment), or prevents two coordinators from racing to assign the same driver to two different orders at once (no concurrency protection beyond the single, already-established order-side invariant).

**Update (Sprint IMPLEMENTATION-005, Driver Proposal MVP):** Coordinator's Assign button described above now calls `POST /v1/proposals` instead of `POST /v1/assignments` — see the next section. `POST /v1/assignments` still exists (deprecated, not removed) for manual override.

## Driver Proposal MVP (Sprint IMPLEMENTATION-005)

Closes the loop the previous section's own "What this still cannot do" named: a driver can now accept or decline a proposed pairing, and doing so creates the `Assignment` automatically (backend orchestration, Sprint IMPLEMENTATION-004) — no code in this frontend calls `/v1/assignments` directly to make that happen.

**Coordinator (`/coordinator`):** the same order/driver selection UI now calls `POST /v1/proposals` (`{ orderId, driverId }` → `{ proposalId, orderId, driverId, status: "OPEN" }`, 201) instead of `POST /v1/assignments`. The result panel shows the created proposal and a **Check status** button (`GET /v1/proposals/:id`) — the only way today to see whether a driver has responded; there is no live push or polling.

**Driver Home (`/`):** a new "Proposals" section lists `CURRENT_DRIVER_ID`'s own proposals (`GET /v1/proposals?driverId=...`, Dispatch), loading/failing independently of the driver-info section above it. Each `OPEN` proposal has **Accept**/**Decline** buttons (`POST /v1/proposals/:id/accept` / `.../decline`); other statuses are shown read-only. A 404/409 response (someone else already resolved it) silently reloads the list rather than showing a dead-end error.

**What this still cannot do:** no real driver authentication (`CURRENT_DRIVER_ID` remains the same hardcoded placeholder), no push notification when a new proposal arrives (the driver must reload), and no way for the Coordinator to see the resulting Assignment's own id (only the Proposal's status) — see `ProposalAssignmentOrchestrationService`'s own KDoc in the `dispatch` backend module for why that's a disclosed, deliberate gap rather than an oversight.

## Running

Prerequisites: Node.js (verified against v24) and npm. Every backend module this frontend calls (Driver Management, Order Management, Dispatch) must also have CORS enabled for `http://localhost:5173` — each module's own `WebCorsConfiguration` (added Sprint VALIDATION-001) does this already; without it, every request from this frontend fails in the browser with a CORS error, not a 4xx/5xx from the backend itself.

```
cd frontend
npm install
npm run dev       # local dev server, http://localhost:5173
npm run build     # production build, output to dist/ (gitignored)
npm run preview   # serve the production build locally
```

The frontend and the backend (`../backend/`, Gradle/Spring Boot) are independent projects with independent tooling and independent run commands. As of Sprint FR-004, three backend modules are genuinely needed for the full flow: Driver Management (Driver Home; Coordinator's driver list), Order Management (Ride Request; Coordinator's order list), and Dispatch (Coordinator's Assign action) — without any of them running (or without their respective env overrides pointed at a reachable instance), that page's own request fails and shows its own error state, not a crash. `api/apiClient.ts` reads Driver Management's base URL from `VITE_API_BASE_URL`, defaulting to `http://localhost:8081`; `Coordinator.tsx`/`RideRequest.tsx` read Order Management's base URL from `VITE_ORDER_MANAGEMENT_BASE_URL`, defaulting to `http://localhost:8083`; `Coordinator.tsx` reads Dispatch's base URL from `VITE_DISPATCH_BASE_URL`, defaulting to `http://localhost:8084` — all local ports per `docs/INTERFACE_CONTRACTS.md`.

## What Remains Mock

- **`currentDriver.ts`'s `CURRENT_DRIVER_ID`** — a hardcoded `'ILDAR001'` standing in for driver authentication/session, which does not exist yet. The *data* fetched for that id (availability) is real; *which* id gets fetched is not.
- **`invitationLinkFor()`'s output** — `https://pios.local/i/ILDAR001` is not a real, resolvable domain; it exists for display and Share/Copy only. Local navigation instead uses `QRCard`'s separate `linkTo` prop (see "Driver Invitation Flow" above).
- **`invitationSource.ts`'s `MOCK_INVITATIONS`** — a fixed, in-memory directory with a single entry (`ILDAR001`). Any other `driverCode` resolves to "not found."
- **The QR graphic** — a static decorative placeholder, not generated from `invitationLink` or any real encoding.

Not mock, as of Sprint 5: **Driver Home's `id` and `availability`** are genuinely fetched from a real, running Driver Management backend (`GET /v1/drivers/{driverId}`) — see "First Backend Integration" above.

Not mock, as of Sprint 3: the **passenger identity** saved by `savePassengerIdentity` is real, genuinely-persisted browser data (real `localStorage`, a real generated `id`, a real timestamp) — only its *scope* is local-only, since no server has ever heard of it.

Not mock, as of Sprint FR-001: **submitting a ride request genuinely creates an `Order`** in the real Order Management backend, with a real, returned `orderId`.

Still mock/unsent, as of Sprint FR-001: the **`Destination` and `Notes` values typed into the Ride Request form** are collected and validated client-side, but are never transmitted — the real `POST /v1/orders` contract has no field for either (see "Ride Request Flow" above for why, and `docs/PIOS_MVP_V01.md`/`FND-006`'s own report for the backward-compatibility reasoning). The created `Order` records only who submitted it (`origin`), nothing about where they want to go.

Not mock, as of Sprint FR-002: **`Coordinator`'s driver list** (`GET /v1/drivers`) is genuinely fetched from Driver Management — no mock directory anywhere in this page.

Not mock, as of Sprint FR-003: **`Coordinator`'s order list** (`GET /v1/orders`) is genuinely fetched from Order Management — no mock directory anywhere in this section.

Not mock, as of Sprint FR-004: **`Coordinator`'s Assign action genuinely creates an `Assignment`** in the real Dispatch backend (`POST /v1/assignments`), with a real, returned `assignmentId` and `status`.

## Future Integration Points

- **`invitationSource.ts`** is the single place a future task wires up a real `GET /v1/invitations/:driverCode`-style call (through `api/apiClient.ts`, now genuinely functional) — `PassengerLanding.tsx` would not need to change.
- **`persistence/localPassengerIdentity.ts`** is the single place a future task replaces `localStorage` with a real backend-backed passenger identity/session (through `api/apiClient.ts`) — `PassengerLanding.tsx` would not need to change, since it already depends only on `PassengerIdentity` and the two exported function signatures.
- **`Destination`/`Notes` reaching the backend** requires a versioned backend change (a new `/v2/orders`-style endpoint, per Sprint FND-006's own follow-up design proposal) before `RideRequest.tsx` can send them — not a frontend-only change; the frontend side is otherwise ready (both values are already collected and validated).
- **`currentDriver.ts`'s `CURRENT_DRIVER_ID`** is the single place a future task wires up real driver authentication/session for Driver Home (see `project-brain/PIOS_OPEN_QUESTIONS.md`, Items 5 and 9) — `DriverHome.tsx`'s fetch logic would not need to change, only where the id it fetches comes from.
- A real driver name/profile, if the domain model is ever extended to have one (a future ADR + `DOMAIN_MODEL.md`/`API_SPECIFICATION.md` change, out of this sprint's scope), would only require adding a field to `DriverController`'s response and `DriverCard`'s props — the fetch/loading/error plumbing already in `DriverHome.tsx` would not need to change.
- **`handleCopy`/`handleShare`** in `DriverHome.tsx` already call real browser APIs; nothing about them needs to change when the invitation link itself becomes real — only `invitationLinkFor()`'s implementation would.
- **A driver-facing availability toggle** on `DriverHome` — the backend endpoint (`POST /v1/drivers/{driverId}/availability`) already exists as of Sprint FR-002; only a UI control calling it is missing, deliberately, since it was not in that sprint's own permitted scope.
- **Coordinator auto-refresh** — if reloading the page manually proves too slow in practice, a future task can add polling or a manual refresh button without changing the backend at all; `GET /v1/drivers`/`GET /v1/orders` already return the full current state on every call.
- **Driver acceptance of an assignment** — Dispatch's own `Assignment` aggregate already models `accept()` and `AssignmentStatus.ACCEPTED`; only a REST endpoint and a UI for it are missing, deliberately out of Sprint FR-004's own scope.
- **Reflecting an assignment back onto the driver/order lists** — after a successful Assign, neither `GET /v1/drivers` nor `GET /v1/orders` changes (availability and order status are untouched by Dispatch); a future task connecting these would need its own explicit scope, not an assumption made here.
- **Concurrent-assignment protection beyond the one order-side invariant already enforced (`findByOrder` + `Assignment.create`)** — nothing prevents the same driver from being assigned to two different orders, or two coordinators racing on the same order faster than the existing check can catch (see "Manual Assignment" above).

## What Was Intentionally NOT Implemented

Per this sprint's own scope, all deliberately absent, not oversights:

- **No business logic.** Both pages only display resolved data or props and drive a purely presentational step sequence; neither makes a domain decision or enforces a business rule.
- **No invitation persistence.** Nothing generates, stores, or truly issues an invitation anywhere — `MOCK_INVITATIONS` is a fixed, hardcoded lookup, not a real invitation mechanism. (The *passenger identity* created after onboarding is real, local persistence — see "Local Identity" above; only the invitation itself remains mock.)
- **No real passenger registration.** Onboarding only writes a name + generated id + timestamp to this one browser's `localStorage`. There is no account, no server ever learns this identity exists, no email/phone/verification, and no way to use the same identity on a second device.
- **No identity editing, sign-out, or reset.** Once created, the local identity cannot be renamed, cleared, or switched from within the app (a developer can still clear it via browser devtools).
- **No dispatcher / order / assignment UI.** No screen reads or writes any backend domain concept.
- **No authentication.** No login, token, or session handling of any kind — `localStorage` is not a session and proves nothing to any server; `CURRENT_DRIVER_ID` is a hardcoded stand-in, not a session.
- **No real API calls for invitation logic.** `invitationSource.ts` still resolves from mock data only, not through `apiClient`. (Ride Request's own submission, as of Sprint FR-001, does now call the real backend — see above.)
- **No driver name/profile.** The real `Driver` aggregate has only `id` and `availability`; no name, photo, rating, or profile of any kind is fetched, displayed, or invented anywhere. Adding one is a real architecture change (domain model + ADR), explicitly left for a future, dedicated task.
- **No driver authentication or session.** `CURRENT_DRIVER_ID` remains a fixed constant; there is no way for this app instance to represent a different driver without editing code.
- **No `Destination`/`Notes` reach the backend.** Collected and validated client-side only; the real `Order` created carries neither — see "Ride Request Flow" above.
- **No driver association on the created `Order`.** Submitting a ride request does not connect the resulting order to any specific driver, automatically or otherwise — `Order` has no such field. The confirmation screen was corrected in Sprint FR-001 specifically because it used to claim otherwise.
- **No dispatch, assignment, or driver notification of any kind.** Nothing in this frontend triggers, simulates, or displays any of these.
- **No UI component library.** Not justified by anything built so far.
- **No backend, API, Domain Model, or migration change in Sprint FR-001.** That sprint's own constraints were explicit: frontend-only, connecting to the already-existing `POST /v1/orders` exactly as it already was.
- **No `BUSY`, `OFFLINE`, or any third availability state.** `Availability` still has exactly two values; `POST /v1/drivers/{driverId}/availability` rejects (400) anything else — proven by a dedicated test sending `"BUSY"`.
- **No filtering, search, sorting, pagination, auto-refresh, or WebSocket on the Coordinator page.** A plain, full list, fetched once per page load — explicitly excluded by Sprint FR-002's own scope, not a missing feature.
- **No authentication on `/coordinator`, or anywhere else.** Reachable by anyone who navigates to it.
- **No way for the Coordinator to change a driver's availability.** Only the driver's own declaration can — see "Driver Availability" above.
- **No Domain Model, Dispatch, or Assignment change in Sprint FR-002.** That sprint touched only Driver Management's own repository/application/API layers, already-owned capabilities extended in shape (single → list; read → also write), not new architecture.
- **No Order/Driver/Assignment domain change in Sprint FR-003.** That sprint added only a read query (`OrderRepository.findAll`, `RetrieveOrdersHandler`, `OrderQueryController`) next to Order Management's already-existing `POST /v1/orders`, which it did not touch.
- **No Order, Driver, or Availability domain change in Sprint FR-004.** That sprint used the already-existing `Assignment` aggregate, `AssignOrderCommand`, and `DispatchAssignmentApplicationService` unchanged; the only repository extension (`AssignmentRepository.findByOrder`) is additive, and `Assignment` remains a separate aggregate in Dispatch, not merged into Order or Driver.
- **No assignment algorithm, Fair Dispatch, Mutuality, or AI Dispatch of any kind.** The coordinator is the sole decision-maker; `AssignmentController` records the pairing it is given and evaluates no criteria of its own (ADR-002).
- **No driver acceptance of an assignment.** `Assignment.accept()`/`AssignmentStatus.ACCEPTED` already exist in the domain; no endpoint or UI reaches them yet.
- **No availability change, and no `BUSY`/`OFFLINE`/other new `Availability` state, as a side effect of assignment.** Creating an Assignment does not touch `Driver.availability` anywhere in this codebase.
- **No concurrent-assignment protection beyond the Assignment aggregate's own existing one-active-assignment-per-order invariant**, now checked against real data (`findByOrder`) instead of an empty list — but still not checked at the database level (no unique constraint), and driver-side double-booking (the same driver assigned to two orders) is not checked at all.
- **No WebSocket, and no authentication, on the Coordinator page's Assign action**, consistent with every other page in this project so far.

Each of the above requires its own explicit, future task before implementation, consistent with [.ai/EXECUTION_PROTOCOL.md](../.ai/EXECUTION_PROTOCOL.md).

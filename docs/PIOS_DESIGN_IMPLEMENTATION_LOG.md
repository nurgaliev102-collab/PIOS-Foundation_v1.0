Status: Implementation log for the first PIOS design-system implementation pass, executed against [PIOS_DESIGN_SYSTEM.md](PIOS_DESIGN_SYSTEM.md) and [PIOS_TAXI_DESIGN_BRIEF.md](PIOS_TAXI_DESIGN_BRIEF.md). Scope: tokens + foundation components + exactly one screen (`RideRequest`, Passenger Booking). No other screen was touched. No business logic, API contract, route, or data model was changed — verified by the existing test suite passing unmodified (Section 6).

## 1. Tokens Implemented

`frontend/src/styles/tokens.css`, imported once by `frontend/src/index.css`. Full set per [PIOS_DESIGN_SYSTEM.md](PIOS_DESIGN_SYSTEM.md) Section 3: color (surfaces, text, border, accent, semantic status, trust), typography (display/heading/body/label/caption/numeric/button), spacing (4px scale), radius, elevation (two levels), motion (three durations + easing), focus, touch target. Every value either matches the de facto palette already in use (consolidation, not invention — cross-checked against the actual `.module.css` hex counts recorded in that document's own Section 0) or is a newly, explicitly documented decision — see Section 3 below.

## 2. Components Implemented

New foundation components, each in its own `frontend/src/components/<Name>/` folder (component + CSS Module + `index.ts`, matching the project's existing convention):

`Button`, `IconButton`, `Heading`, `Text`, `Input` (+ `Select`), `FormField`, `Card`, `StatusMessage`, `Divider`, `LoadingState`, `ErrorState`.

**Not created** (not needed by `RideRequest`, deferred per the design system's own "highest-value primitives for the first screen" instruction): `Sheet`, `Dialog`, `Tabs`, `BottomNavigation`, any of the map/ride-specific "PIOS TAXI" components (`DriverIdentity`, `RideCard`, `LocationField`, `RouteSummary`, `FareSummary`, `AvailabilityStatus`, `RequestCard`, `ActiveRidePanel`), `Alert`/`Toast`/`EmptyState`/`SuccessState`. These remain exactly as scoped in [PIOS_DESIGN_SYSTEM.md](PIOS_DESIGN_SYSTEM.md) Section 5 for a future pass.

**Existing components deliberately left untouched**: `ActionButton`, `Spinner`, `Header`, `DriverCard`, `QRCard`, `PasswordInput`, `OnboardingWalkthrough` — still used, unmodified, by every other screen. `Button` is a new, separate component rather than an edit to `ActionButton`, specifically so this task's one-screen scope is real, not just nominal (editing `ActionButton` in place would have silently redesigned `DriverHome`, `PassengerLanding`, and `Coordinator` too). `LoadingState` wraps the existing `Spinner` rather than duplicating it.

## 3. Screen Redesigned

`RideRequest` (`/i/:driverCode/request` — Passenger Booking, per this task's own instruction: "if Passenger Booking is sufficiently implemented, use it"). Confirmed sufficiently implemented before starting: real API integration (Order Management, Dispatch, Passenger Experience), real Circle of Trust flow, real ride-status polling — not a stub.

**What changed**: only the returned JSX and `RideRequest.module.css`. Every state hook, `useEffect`, and handler function (`handleSubmit`, `handleCancelOrder`, `handleConfirmMakePrimary`, the status-polling effect, etc.) is byte-for-byte unchanged — confirmed by the full existing `RideRequest.test.tsx` suite (32 tests) passing without modification (Section 6).

**Every step of this screen was migrated**: loading, not-found, error, circle (Circle of Trust), form, confirmed.

## 4. Design Decisions

- **Accent color (`--pios-color-accent`) kept as the existing `#2563eb`, not replaced.** [PIOS_DESIGN_SYSTEM.md](PIOS_DESIGN_SYSTEM.md) flagged this hue as needing a deliberate decision, since it's the generic Tailwind/SaaS blue the design brief's anti-pattern list names. On implementation, the existing usage is a single, flat, solid color used sparingly for one primary action — matching what the brief actually asks for (§4: "a single accent... no gradient"), not the gradient/glass *treatment* the anti-pattern list actually targets. Kept as the lower-risk choice; **the final brand-accent decision remains open for Product Owner sign-off**, exactly as the design system document already stated.
- **New `--pios-color-trust` token** (`#92400e`, warm brown, on a `#fef3e2` tint) — no prior de facto value existed. Chosen deliberately distinct from `--pios-color-success`'s green, so "this ride succeeded" and "this is someone you know" can never be visually conflated once a dedicated trust indicator is built. Not yet actually consumed by any component in this pass (`RideRequest`'s Circle of Trust step uses plain `Text`, not a dedicated trust-badge component, since building `DriverTrustIndicator` was out of this pass's scope) — reserved for the future component.
- **`Button` gained a `destructive` variant**, not present in the original `ActionButton`. Applied to "Удалить" (remove from circle) and "Отменить заказ" (cancel order) — both are consequential, negative actions the original design rendered identically to ordinary secondary actions. This is a real, visible improvement, confirmed by screenshot (Section 7): the destructive button now reads as distinct (soft red) rather than indistinguishable from "Отмена"/"Выбрать".
- **Loading-state buttons now show a spinner instead of changing their label text** (e.g. "Сохраняем…"/"Удаляем…" copy was replaced by `Button`'s own `loading` prop, which disables the control and shows a spinner). This is a deliberate use of the design system's own required `loading` component state (Section 6 of that document) — the same "the button is busy" fact is now communicated visually as well as by disabling, at the cost of the specific in-progress copy. Flagged here as a minor, intentional behavior change, not an oversight.
- **`.content`'s vertical alignment changed from `justify-content: center` to `flex-start`** — found during browser visual verification (Section 7), not assumed. The centered layout worked for a single short status line but floated a multi-field form (and the multi-line confirmed-ride status stack) awkwardly in the middle of a tall mobile viewport, with a large empty gap above it. This is the one concrete "iterate" fix this pass made after the first screenshot.

## 5. Deviations from the Design Specification

- `Text`'s `role="numeric"` (fare/ETA) is applied to `statedPrice`/`statedEtaMinutes` display, but neither the design brief nor the current backend contract has resolved the open question of whether a live map/precise fare breakdown is ever shown (brief §14.2) — this pass only formats the two numeric fields the existing contract already sends, nothing further was invented.
- `DriverTrustIndicator` (design system §9) was not built — the Circle of Trust step still names drivers via plain `Text`, not a dedicated component. This is a genuine scope deferral, not a silent omission: it's recorded here and in Section 2 above.
- The Circle-of-Trust step's heading remains center-aligned while its card content is left-aligned (an inconsistency that predates this pass — the original `.title` was already inside centered `.content`). Not fixed in this pass, since resolving it would mean revisiting the original screen's own heading-alignment convention across every step, beyond this task's "migrate to tokens, don't invent new structure" scope. Recorded as a remaining issue (Section 9).

## 6. Browser Verification Performed

Per [PIOS_VISUAL_VERIFICATION_PLAN.md](PIOS_VISUAL_VERIFICATION_PLAN.md) Section 9's exact recipe: dev server started with `npm run dev -- --host 127.0.0.1` (the plain default is unreachable on this machine — IPv6 loopback is broken here, already documented in that plan). Screenshots captured via the already-installed Python Playwright + Chromium, with backend network calls mocked at the browser level (`page.route`) — no real backend service was started, touched, or connected to; nothing in this verification pass reached PostgreSQL, RabbitMQ, or any WinSW service.

**States captured, mobile (390×844) and desktop (1440×900) unless noted**:
- Loading state (mobile) — `LoadingState`/`Spinner` rendering correctly, respects the existing reduced-motion handling.
- Form step: empty, focused (`#pickupAddress`, mobile), validation error (mobile), both viewports.
- Confirmed step with an ACCEPTED ride (driver name, stated price, stated ETA), both viewports.
- Circle of Trust step, including the "remove" confirmation card (mobile).

## 7. Mobile Result

Passed, after one fix. Initial screenshot showed the form floating vertically centered with a large empty gap above it (Section 4's fix). After the fix: header → heading → form fields → primary action, top-anchored, in the order a mobile user reads. Touch targets (`Button`, `Input`, `Select`) all render at the `44px` minimum. Focus ring is visible and high-contrast on `Input`. Validation error is red-bordered *and* carries a text message (never color alone). The confirmed-ride screenshot puts the driver's own name inside the accepted-status message, in ordinary body text — visible, but not yet given the elevated display-weight treatment [PIOS_TAXI_DESIGN_BRIEF.md](PIOS_TAXI_DESIGN_BRIEF.md) Section 2 Principle 1 calls for (a `DriverIdentity` component, deferred per Section 5 above, would be the correct place to fix this, not a change to this screen's own plain status text).

## 8. Desktop Result

Passed. The 480px content container stays centered on the wider viewport rather than stretching into a multi-column layout — matches [PIOS_DESIGN_SYSTEM.md](PIOS_DESIGN_SYSTEM.md) Section 4's own explicit direction ("the mobile container simply centers with margin around it rather than reflowing"). No desktop-specific layout bugs found; the same top-anchoring fix (Section 4) applies identically at both sizes since it was a shared CSS Module class, not a per-viewport override.

## 9. Remaining Issues

- `DriverTrustIndicator` and every other "PIOS TAXI"-specific component (Section 2) remain unbuilt — this pass only covers the foundation layer plus what one screen actually needed.
- The Circle-of-Trust heading/card alignment inconsistency (Section 5) is unresolved.
- The final `--pios-color-accent` and `--pios-color-trust` values are implementation defaults, not ratified brand decisions (Section 4) — both are structurally ready to change in exactly one place (`tokens.css`) once a Product Owner decision is made, per the design system's own Section 16 "Definition of Done" item 2.
- Dark-mode token values are not defined (already flagged as a gap in [PIOS_DESIGN_SYSTEM.md](PIOS_DESIGN_SYSTEM.md) Section 3) — `index.css` still only declares `color-scheme: light dark` with no dark-specific token overrides.
- A pre-existing, unrelated TypeScript error blocks a full `npm run build` (`tsc -b`) — see Section 10. Not fixed here (out of this task's scope: "do not introduce unrelated fixes").

## 10. Validation (Phase 9)

| Check | Command | Result |
|---|---|---|
| Lint | `npx oxlint` (scoped to every new/modified file) | Clean — 0 errors, 0 warnings in any new or modified file (3 pre-existing warnings in `RideRequest.test.tsx`, untouched by this task) |
| Typecheck | `npx tsc --noEmit -p tsconfig.app.json` | **1 pre-existing error**, unrelated to this task: `src/pages/OwnerControlCenter/aiProvider.test.ts` fails against a `pilotAnalytics.ts` type that was already modified before this task began (confirmed via `git log`/`git diff` — neither file was touched in this task). Zero errors in any file this task created or modified. |
| Tests (this screen) | `npx vitest run src/pages/RideRequest` | **32/32 passed**, unmodified test file — direct proof business logic was preserved |
| Tests (full suite) | `npx vitest run` | **222/222 passed** across all 24 test files — no regression anywhere else in the app |
| Build | `npm run build` (`tsc -b && vite build`) | Blocked by the same pre-existing `aiProvider.test.ts` error above; not attempted further, since fixing it is out of scope |

Honest note on the build gap: this task cannot claim a clean `npm run build` today, because `tsc -b` fails on a file this task never touched, for a reason that predates this task (confirmed via git history). `tsc --noEmit` on the app project confirms this is the *only* type error anywhere in the project, and it is not in any file this task created or modified.

---

## Task 5 Addendum — DriverTrustIndicator

### Design decision

See [DRIVER_IDENTITY_DESIGN_DECISION.md](DRIVER_IDENTITY_DESIGN_DECISION.md) in full. Summary: a Phase 1 data-model audit confirmed the current PIOS data model has exactly three facts a driver-identity component can truthfully show — display name (nullable), availability (`AVAILABLE`/`UNAVAILABLE`, only where the caller has fetched it), and Circle of Trust's own ratified `isPrimary` boolean. No rating, ride count, vehicle, plate, or photo exists anywhere in the domain model — confirmed by direct inspection of `Driver.kt` and an exhaustive repository-wide grep, not assumed. The component's API is deliberately structured so none of those absent facts could be added by accident later (no generic `metadata`/`stats` prop, no numeric slot of any kind).

### Component API

`frontend/src/components/DriverTrustIndicator/`:

```ts
interface DriverTrustIndicatorProps {
  name: string                                    // required
  availability?: 'AVAILABLE' | 'UNAVAILABLE'      // omitted, not guessed, where unknown
  isPrimary?: boolean                             // Circle of Trust's own ratified fact
  emphasis?: 'compact' | 'prominent'               // default 'compact'
}
```

Four props, three optional. Consolidates what [PIOS_DESIGN_SYSTEM.md](PIOS_DESIGN_SYSTEM.md) Section 5 originally named as two separate future components (`DriverIdentity` + `DriverTrustIndicator`) into one, per this task's own single-component scope — documented as a deliberate decision in the design decision doc's own "Component scope note," not an oversight.

### Data used

Exactly the three facts above, at the two real call sites:
- **Circle of Trust step**: primary card gets `emphasis="prominent"`, `isPrimary`, and `availability`; each "other driver" row gets `emphasis="compact"` and `availability`, no `isPrimary` (they aren't).
- **Confirmed ride step**: `emphasis="prominent"`, name only — `availability`/`isPrimary` are never passed here, because this step's own code never fetches either fact (verified by reading the step's own data flow, not assumed).

### States implemented and verified

Known driver (both call sites), primary marker, non-primary, available, unavailable (grey dot + "Недоступен," button correctly disabled — that disabling is pre-existing business logic, untouched), missing optional data (confirmed step has no availability), long driver name (two names of 30+ characters, wraps cleanly via `overflow-wrap: anywhere` + `text-wrap: balance`, no overflow or clipping), narrow mobile viewport (320px, iPhone SE class — confirmed no horizontal overflow). No "loading" state exists inside the component itself (it has no internal async behavior) — the screen's own pre-existing `LoadingState` covers the period before driver data arrives, unchanged.

### Browser verification

Same zero-install workflow as Task 4 (`npm run dev -- --host 127.0.0.1`, Python Playwright + already-downloaded Chromium, network mocked via `page.route`, no real backend/DB/queue touched). Captured and inspected: Circle of Trust (mobile 390×844, desktop 1440×900), confirmed ride (mobile, desktop), plus narrow-viewport/long-name/unavailable states. **No fix was needed this round** — unlike Task 4's first pass (which caught and fixed a vertical-centering bug), this component's first screenshots already satisfied every check: the driver's name reads as the clearly largest, boldest element on both screens (confirmed step in particular — the name now leads the entire screen, ahead of the order-confirmation message, which is the concrete, visible proof of "driver's name became the primary identity element"); the "Основной" marker is visually distinct from the green availability dot and from the blue primary-action button, with no numeric element anywhere; spacing/alignment/touch targets/wrapping all held up under long names and a 320px viewport.

### Tests

- `npx tsc --noEmit -p tsconfig.app.json`: 0 new errors (same 1 pre-existing, unrelated `aiProvider.test.ts` error as Task 4, confirmed unchanged)
- `npx oxlint` (new component + modified files): clean
- `npx vitest run src/pages/RideRequest`: **1 pre-existing assertion needed updating** — `RideRequest.test.tsx` checked for the literal text "Основной водитель" (the old, page-owned section label), which this task deliberately replaced with `DriverTrustIndicator`'s own "Основной" badge (Section "Design Decisions" above). Updated the one assertion to match the new, intentional UI (`Основной` instead of `Основной водитель`) — this is a direct consequence of this task's own change, not an unrelated pre-existing failure, so fixing it was in scope. After the fix: **32/32 passed**.
- `npx vitest run` (full suite): **222/222 passed**

### Remaining issues

- The confirmed-ride step's driver name is now prominent but still duplicated inside `rideStatusLabel`'s own sentence ("Ильдар Нургалиев принял ваш заказ…") — left untouched deliberately, since editing that function's carefully-authored, heavily-commented copy was judged out of this task's "do not alter business logic" boundary; the mild duplication is not a design-system violation, just a minor polish opportunity for later.
- `DriverIdentity` (pure identity, no relationship context) remains unbuilt as its own component — `DriverTrustIndicator` currently covers both roles (Section "Component scope note," design decision doc); a future screen needing identity display with zero relationship context would still reuse this same component today.
- Whether PIOS ever adds a rating/feedback mechanism (brief open question §14.3) remains unresolved — this component's API has no slot for one today, by design, and would need a deliberate, separate decision before one could be added.

---

## Task 6 Addendum — PassengerLanding (Personal Invitation Experience)

### Design decision

See [PASSENGER_INVITATION_DESIGN_DECISION.md](PASSENGER_INVITATION_DESIGN_DECISION.md) in full. A direct-inspection audit (`invitationSource.ts`, every step's own state) confirmed this screen has exactly one driver fact available at every step — `driverName` — never availability, never Circle-of-Trust `isPrimary` (this screen is *before* that decision exists). Before this task, the driver's name appeared in **four separate, independently-styled treatments** on the `'invited'` step (an 88px avatar circle, a small caption, an emoji-prefixed `<h1>`, and a "how it works" card row) — the exact "one-off, not the shared component" problem [PIOS_DESIGN_SYSTEM.md](PIOS_DESIGN_SYSTEM.md) warns against.

### Hierarchy changes

`'invited'` step, top to bottom, before → after:
- Before: avatar circle → caption → emoji heading (name #2) → subtitle (name #3) → link → **explainer card (name #4)** → primary action → install card → FAQ.
- After: `DriverTrustIndicator` (avatar + name, one element) → supporting heading (no name repeat) → supporting subtitle (no name repeat) → link → **primary action** → explainer card (name — a distinct fact, "the order goes specifically to them," kept) → install card → FAQ.

The primary action ("Начать") moved from after two full sections to directly under the identity — [PASSENGER_INVITATION_DESIGN_DECISION.md](PASSENGER_INVITATION_DESIGN_DECISION.md) Section 5. The decorative "👋" emoji was removed (no functional purpose, per the design brief's own anti-pattern list). Same treatment applied to `'confirm-add'` step (`DriverTrustIndicator` + the existing question heading, unchanged wording since the question genuinely needs to name who is being added).

### Component usage / change

`DriverTrustIndicator` gained one new, **opt-in** prop: `showAvatar?: boolean` (default `false`) — renders the existing initial-letter-avatar treatment `PassengerLanding.tsx`'s own `.heroAvatar` already used, now consolidated into the shared component instead of duplicated. Opt-in specifically so `RideRequest`'s already-verified `emphasis="prominent"` usages (Task 4/5) render pixel-identical unless a caller explicitly asks for the avatar — confirmed by re-running `RideRequest`'s own test suite immediately after the change (32/32 passed, no visual regression). As a side effect, this resolved a pre-existing, previously-flagged color inconsistency: the old `.heroAvatar` hardcoded `#111827`, one hex value off from `--pios-color-text-primary`'s `#0f172a` (noted in [PIOS_DESIGN_SYSTEM.md](PIOS_DESIGN_SYSTEM.md) Section 0) — the shared component's avatar now uses the token directly.

Also migrated, for full consistency with the rest of the design system (same pattern Task 4 applied to `RideRequest`): `ActionButton` → `Button`, `Spinner` → `LoadingState`, raw error/status `<p>` blocks → `ErrorState`/`StatusMessage`, and the full `.module.css` file to tokens. `PasswordInput` and the plain `name`/`phone` `<input>` elements in the `'auth'` step were deliberately left as-is — unrelated to driver identity, and restructuring the login form's own input mechanics was judged out of this task's scope.

### States verified

Driver found (`'invited'`), invalid/expired invitation code (`'not-found'`, warm `StatusMessage`), long driver name (three-line wrap, avatar stays aligned, no overflow), narrow 320px viewport (two-line name wrap, no horizontal overflow), mobile (390px), desktop (1440px, container stays centered rather than reflowing). `'error'`/loading states reuse the same `ErrorState`/`LoadingState` components already verified in Task 4/5 — not re-screenshotted separately, since their rendering is identical regardless of which screen hosts them.

### Browser observations

First screenshot already showed the identity leading the screen correctly, but the driver's name still appeared 4 times in text — caught during inspection (not merely screenshotted and accepted), fixed by trimming the heading and subtitle's own redundant name mentions (down to 2: the identity component itself, and the explainer card's distinct "order goes to them" fact) → re-screenshotted → confirmed clean on a second pass, including at 320px and with a long name. The pre-existing `PassengerOnboarding` overlay (unrelated, not modified) auto-shows on a first visit and had to be dismissed in the verification script to see the actual redesigned screen beneath it.

### Tests

- `npx tsc --noEmit -p tsconfig.app.json`: 0 new errors (same 1 pre-existing, unrelated `aiProvider.test.ts` error)
- `npx oxlint`: clean (1 pre-existing, unrelated `react-hooks/exhaustive-deps` warning on an untouched `useEffect`, confirmed via `git diff` to predate this task)
- `PassengerLanding` tests: **5 assertions needed updating** — all checked the literal, now-intentionally-changed text ("👋 Вас пригласил Иван" → "Вас пригласил Иван" after emoji removal; then the whole heading text changed to "Вас пригласили лично" after the redundancy trim, so those same assertions were redirected to the still-unchanged "Заказ получает Иван" text instead). One assertion (`'Вас пригласил Артур'`) was correctly left untouched — confirmed via source inspection to belong to the unrelated `PassengerOnboarding` overlay's own static demo copy, not this task's redesign. After fixes: **15/15 passed**.
- Full suite: **222/222 passed**

### Remaining issues

- The `'confirm-add'` step's subtitle still mentions the driver's name once more, even though both `DriverTrustIndicator` and the heading above it already have — left as-is this pass (lower priority than the `'invited'` step, the actual "first screen" this task targeted).
- `'confirmed'` step deliberately does not use `DriverTrustIndicator` — that step's own dominant moment is the passenger's own account creation, not the driver relationship; the driver's name stays in its existing checklist-item mention, unchanged. Documented as a deliberate scope decision, not an oversight.
- Same open items as Task 5 (rating mechanism, `DriverIdentity`-only split) carry forward unchanged.

---

## Task 7 Addendum — Final Design Foundation Cleanup (RideRequest confirmed-ride name duplication)

This closes the one known inconsistency flagged in Task 5's own "Remaining issues" (§ above): `RideRequest`'s confirmed-ride step named the driver both in `DriverTrustIndicator` and, separately, inside `rideStatusLabel`'s status sentence — the exact repetition Task 6 had already resolved on `PassengerLanding`.

### Exact UI inconsistency removed

Before: for `OPEN`/`DECLINED`/`LAPSED`/`ACCEPTED`/`ARRIVED` statuses, `rideStatusLabel(status, driverName)` interpolated the driver's name into the sentence whenever it was known (e.g. `"{name} принял ваш заказ и скоро свяжется с вами."`), duplicating the name already shown above by `DriverTrustIndicator`.

After: `rideStatusLabel(status)` no longer takes a `driverName` argument and always uses the generic wording (`"Водитель принял ваш заказ и скоро свяжется с вами."`, etc.) — the same phrasing the function already used whenever the name happened to be unknown, now used unconditionally. `DriverTrustIndicator` remains the one place on this screen that names the driver.

### Files modified

- `frontend/src/pages/RideRequest/RideRequest.tsx` — `rideStatusLabel` signature and body (dropped the `driverName` parameter and its two-branch, name/no-name text per status); its call site (`rideStatusLabel(rideStatus, driverName)` → `rideStatusLabel(rideStatus)`); three comments updated to reflect the new behavior (the function's own JSDoc, the `driverName` state's declaration comment, and the confirmed-step JSX comment that previously said "rideStatusLabel is unchanged"). The `driverName` state itself, its `setDriverName` call, and every other hook/handler are untouched — confirmed presentation-only before editing, by reading the full call graph (`rideStatusLabel` and `driverName` are used nowhere outside this file).
- `frontend/src/pages/RideRequest/RideRequest.test.tsx` — 2 assertions updated. Both `/ещё не ответил на заказ/` assertions (the OPEN-status test at line 310 and the "does not offer Заказать ещё раз" test at line 381) matched only the old personalized OPEN-status text; redirected to `/Ждём ответа водителя/`, the generic OPEN-status text that now renders unconditionally. Every other status-text assertion (`/принял ваш заказ/`, `/отклонил ваш заказ/`, `/больше не активен/`, `/свяжется с вами/`) needed no change — the generic wording already contained those same substrings.

### Tests

- `npx vitest run src/pages/RideRequest/RideRequest.test.tsx`: **32/32 passed** (after the 2 assertion updates above)
- `npx vitest run src/pages/PassengerLanding/PassengerLanding.test.tsx`: **10/10 passed**, unmodified — this task did not touch that screen
- `npx vitest run` (full suite): **222/222 passed**
- `npx tsc --noEmit`: 0 errors
- `npx oxlint`: 0 new warnings — the 9 warnings reported are all pre-existing, in files this task did not modify (`PassengerLanding.tsx`'s `exhaustive-deps`, and `no-unsafe-optional-chaining` in `DriverHome.test.tsx`/`todayData.test.ts`/`Coordinator.test.tsx`/`RideRequest.test.tsx`), confirmed to predate this task by inspecting the flagged lines directly (none are inside `rideStatusLabel` or its call site)

### Browser verification

Same zero-install workflow as every prior task (`npm run dev -- --host 127.0.0.1`, Python Playwright + already-downloaded Chromium, `page.route` network mocking — no real backend/DB/queue touched; dev server confirmed stopped afterward). Confirmed-ride state captured and inspected at: mobile (390×844), desktop (1440×900), narrow 320px viewport, and a long driver name (32 characters, two-line wrap). All four render with the driver's name appearing exactly once (inside `DriverTrustIndicator`), the status message reading naturally without a name (`"⏳ Ждём ответа водителя. Мы сообщим, как только он подтвердит заказ."`), and no overflow, clipping, or layout shift at any size. No fix was needed this round — the change was a pure text simplification, not a layout change.

### Remaining design-system issues

Everything already on record from Tasks 4–6 stands unchanged (accent-color sign-off still open, dark-mode tokens undefined, `DriverIdentity`-only split unbuilt, rating mechanism unresolved, the Circle-of-Trust heading/card alignment inconsistency). This task closes the one item it was scoped to close and introduces no new ones.

**This is the final cleanup of the initial PIOS design-foundation implementation pass.** No further screen redesign is planned as part of this pass.

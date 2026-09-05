Status: Technical design-system specification, not an ADR, authorizes no code change. Translates [PIOS_TAXI_DESIGN_BRIEF.md](PIOS_TAXI_DESIGN_BRIEF.md) into implementable structure for the existing stack (React 19, TypeScript, Vite 8, `react-router-dom`, CSS Modules — no Tailwind, no component library, introduced or assumed). Grounded in the actual current frontend, not invented in a vacuum: every token and pattern below is checked against the 20 `.module.css` files and 7 components that exist today (`frontend/src/components/`, `frontend/src/pages/`), cited inline where they matter.

## 0. What was actually found in the existing frontend before writing this

This document does not start from zero. A full scan of every `.module.css` file's hex-color usage shows the codebase already has a **de facto, fairly consistent, unnamed palette** — the numbers below are exact occurrence counts across all 20 stylesheets:

| Hex | Occurrences | Reads as |
|---|---:|---|
| `#0f172a` | 53 | primary text / darkest ink |
| `#64748b` | 40 | secondary text |
| `#ffffff` | 39 | surface / background |
| `#e2e8f0` | 34 | border / subtle divider |
| `#2563eb` | 23 | the current accent (used for primary buttons, focus rings, selected states — e.g. `ActionButton.module.css`'s `.primary`, `DriverCard.module.css`'s `.selected`) |
| `#f8fafc` | 15 | page background |
| `#94a3b8` | 11 | muted text |
| `#dc2626` | 9 | error |
| `#f1f5f9` | 8 | subtle/secondary surface |
| `#cbd5e1` | 6 | border, lighter |
| `#334155` | 4 | secondary ink, darker |
| `#16a34a` | 4 | success |
| `#eff6ff`, `#f0fdf4` | 3, 2 | accent-tinted / success-tinted background |
| `#111827` | 3 | **near-duplicate of `#0f172a`** — used only for `DriverCard`'s avatar background, an inconsistency this document's token migration should resolve, not perpetuate |
| `#ca8a04` | 2 | warning |
| `#1d4ed8`, `#15803d` | 2, 2 | accent/success, darker (hover states) |
| `#475569` | 1 | secondary ink, one-off |

This is already recognizably close to the Tailwind slate/blue/red/green defaults. Two things follow, and both shape this document: **(a)** a token layer here is mostly *consolidation and naming* of what's already in use, not a new palette invented from taste — low-risk, and directly serves "lightweight, maintainable... implemented incrementally"; **(b)** the current accent (`#2563eb`, a generic SaaS/Tailwind blue) is exactly the kind of "AI-startup/SaaS-dashboard blue" [PIOS_TAXI_DESIGN_BRIEF.md](PIOS_TAXI_DESIGN_BRIEF.md) Section 11 warns against. This document does **not** silently keep it, and does **not** silently replace it — it names the role precisely and flags the value as an open decision (Section 3, Section 16).

Existing component evidence used throughout this document: `ActionButton` (pill-shaped, `border-radius: 999px`, `min-height: 44px`, `variant: 'primary' | 'secondary'`), `DriverCard` (16px-radius card, circular initial-avatar, a `.available`/`.unavailable` status pill already prototyping semantic status color), `Header`, `Spinner`, `PasswordInput`, `QRCard`, `OnboardingWalkthrough`.

## 1. Design system purpose

The design system exists to make the product brief's philosophy **structurally hard to violate by accident**, not merely to make screens look consistent. Concretely: if "the driver's name is the most prominent identity element" and "no raw connection/follower count anywhere" (brief Sections 2, 7, 13) are true today only because each page happens to have been built carefully, they will drift the moment a new screen is added under time pressure. A shared token layer and a small, opinionated component set turn those product principles into defaults a developer would have to actively fight to violate — the *DriverIdentity* component simply doesn't have a "show a count" prop; the status-pill token set simply doesn't include a ranking visual. The system's job is to make the brief's constraints cheap to follow and expensive to ignore, using exactly the stack already in place (CSS Modules + React), not by adopting new tooling.

## 2. Design principles (brief → practical UI rules)

| Brief principle | Practical rule |
|---|---|
| Driver is the face, PIOS is the frame (Brief §2.1) | No PIOS wordmark/logo component may be styled larger or bolder than the identity component on the same screen; enforce via component contract, not convention. |
| No fleet, no map full of anonymous cars (§2.2) | There is no "driver list" or "nearby drivers" component in this system at all (Section 5) — its absence is deliberate, not an oversight. |
| Status over decoration (§2.3) | Every screen must have exactly one `RideStatus` or `AvailabilityStatus` instance visually dominant over any purely decorative element. |
| Show the relationship, not a transaction log (§2.4) | History/list components accept an optional "known relationship" visual treatment (Section 10) distinguishing recognized participants — never a numeric summary of how many. |
| Quiet confidence (§2.5) | No component in this system includes a badge, seal, checkmark-overlay, or "verified" ribbon as a decorative default; trust indicators are text/label-based (Section 9), never iconographic stamps. |
| Local and human (§2.6) | `DriverIdentity` defaults to a name-and-initial treatment (already the existing pattern, `DriverCard.module.css`'s `.avatar`); no stock-illustration or generic-silhouette avatar component exists in this system. |
| Function before flourish (§2.7) | Every token in Section 3 must be traceable to an existing use or a stated reason (Section 0) — no token exists "because it looks nice." |
| One clear action per screen (§2.8) | `Button` has exactly one `primary` variant per screen enforced at the page level, not the component level — the component itself doesn't prevent misuse, but the design review checklist (Section 16) checks for it. |
| Honest about what PIOS doesn't decide (§2.9) | No component implies platform authority it doesn't have — no "best match," no algorithmic-sounding copy baked into any component's default text. |
| Extend beyond taxi without a redesign (§2.10) | Components are named for their functional role (`Identity`, `RelationshipBadge`, `StatusPill`) wherever the taxi-specific name (`DriverIdentity`) is just a thin, typed wrapper — see Section 5's naming convention. |

## 3. Design tokens

**Where they live** (Section 15 elaborates): a single new file, `frontend/src/styles/tokens.css`, defining CSS custom properties on `:root`, imported once by `frontend/src/index.css`. Every `.module.css` file consumes `var(--pios-*)` instead of a literal hex/px value going forward; nothing is renamed or moved as a big-bang migration (Section 15).

### Color — semantic roles

Each role states its **reason**, its **current de facto value** (evidence from Section 0, kept as the starting point wherever no brief conflict exists), and whether this document flags it as needing a deliberate decision rather than silent adoption.

| Token | Role / reason | Current de facto value | Status |
|---|---|---|---|
| `--pios-color-background` | The page's own ground — reads as the "shop floor," always the quietest surface (brief §4, "restrained, warm-neutral base") | `#f8fafc` | Adopt as starting point |
| `--pios-color-surface` | A raised but still ordinary surface (a card, a form) — one step above background | `#ffffff` | Adopt |
| `--pios-color-surface-elevated` | Reserved for the one thing genuinely "above" the rest (brief §4 "Surfaces" — elevation used sparingly) — e.g. an active-ride panel, a bottom sheet | `#ffffff` + shadow (Section 3, Elevation) rather than a distinct fill | Adopt (elevation via shadow, not a separate color) |
| `--pios-color-text-primary` | Primary reading text — names, statuses, key numbers | `#0f172a` | Adopt |
| `--pios-color-text-secondary` | Supporting text — labels, secondary detail | `#64748b` | Adopt |
| `--pios-color-text-muted` | De-emphasized text — timestamps, metadata | `#94a3b8` | Adopt |
| `--pios-color-border` | Functional separation only (brief §4 "Borders" — never decorative) | `#e2e8f0` (subtle) / `#cbd5e1` (slightly stronger, for interactive/focusable boundaries) | Adopt, consolidate to exactly these two |
| `--pios-color-accent` | **The single accent used sparingly for the one thing that matters most per screen** (brief §4, §2.7) — primary actions, the one live-status highlight | `#2563eb` today | **NEEDS A DELIBERATE DECISION** (Section 16, open item) — this is the exact "generic SaaS/AI-startup blue" the brief's Section 11 anti-pattern list names; keeping it is a legitimate low-risk choice, but it must be chosen on purpose, not inherited by default. This document names the *role* precisely so the eventual decision only has to pick one value, not restructure anything. |
| `--pios-color-accent-hover` / `--pios-color-accent-pressed` | State variants of the accent, same reasoning | `#1d4ed8` (hover, existing) | Derived from whatever `--pios-color-accent` becomes |
| `--pios-color-success` | Ride completed, request accepted, driver available — a real, positive state change, never decorative | `#16a34a` on `#f0fdf4` tint | Adopt |
| `--pios-color-warning` | A state that needs attention but isn't an error (e.g., ride running late) | `#ca8a04` | Adopt |
| `--pios-color-error` | A real failure or decline — never used for anything else | `#dc2626` | Adopt |
| `--pios-color-information` | Neutral, non-urgent status communication (e.g., "waiting for response") | Not yet distinctly used in the codebase — recommend deriving from `--pios-color-accent` at reduced saturation rather than introducing a new hue, to keep the palette restrained per brief §4 | New, minimal addition |
| `--pios-color-trust` | The one color reserved for "known/recognized participant" context (Section 9, Section 10) — deliberately **not** the same hue as `--pios-color-success`, so "this ride succeeded" and "this is someone you know" are never visually conflated | Not yet distinctly used — recommend a warm neutral (not a saturated hue) so it reads as *recognition*, not as another status alert; exact value is an open decision (Section 16), same reasoning as accent | New, deliberately deferred |
| `--pios-color-ride-*` (state set) | One fixed, small mapping for ride states (requested / matched / arriving / in-progress / completed / cancelled) — semantically locked, distinct from `--pios-color-accent` (brief §4: "status colors... never visually confusable with... press this button") | Composed from `--pios-color-information` / `--pios-color-accent` / `--pios-color-success` / `--pios-color-error` / `--pios-color-text-muted` per state, not new hues | Defined by composition, not new colors |

**Explicitly not defined here**: dark-mode values. `index.css` already declares `color-scheme: light dark`, so the browser will apply its own UA defaults to unstyled elements today, but no dark-mode token pass exists yet — flagged as a real gap in Section 16, not silently assumed solved.

### Typography

Following the brief's "distinct-but-restrained display for identity, workhorse text everywhere else" (Brief §4):

| Role | Purpose | Relative scale (rem, current system-font baseline) |
|---|---|---|
| `--pios-font-display` | The driver's/customer's own name where it is the page's primary identity element (brief §2.1) — the *only* place true display weight is used | 1.5–1.75rem, weight 700 |
| `--pios-font-heading` | Section headings within a screen (e.g., "Your rides with [Driver]") | 1.125–1.25rem, weight 600 |
| `--pios-font-body` | Default reading text | 0.9375–1rem, weight 400–500 |
| `--pios-font-label` | Form labels, status-pill text, small UI labels | 0.8125–0.875rem, weight 600, slight letter-spacing (matches `DriverCard.module.css`'s existing `.availability` pattern: `0.03em`) |
| `--pios-font-caption` | Timestamps, metadata, secondary detail | 0.75rem, weight 400–500 |
| `--pios-font-numeric` | Fares, ETAs, counts of anything genuinely numeric (never a network-size count, per brief §7 and Section 9/10 below) | Same scale as body/label depending on context, but with `font-variant-numeric: tabular-nums` so digits align — new rule, not yet present anywhere in the codebase |
| `--pios-font-button` | Button label text | 0.9375rem, weight 600 (matches `ActionButton.module.css`'s existing values exactly) |

**No specific typeface is chosen here.** `index.css` currently declares `system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif` — a legitimate, zero-cost, always-legible baseline that already satisfies the brief's "clear and human, not geometric-and-corporate" requirement reasonably well. Whether to invest in a custom display face for `--pios-font-display` specifically (the one place brief §4 allows "a distinct but restrained display weight") is an open decision (Section 16) — not resolved here, since no typeface choice can be justified without a real comparison the brief explicitly deferred.

### Spacing

A single 4px-based scale, chosen because it divides evenly into every spacing value already observed in the existing CSS (`0.25rem`, `0.5rem`, `0.625rem`, `1rem`, `1.25rem` all already appear across `ActionButton`/`DriverCard`):

| Token | Value | Typical use |
|---|---|---|
| `--pios-space-1` | 4px | Tight internal gaps (icon-to-label) |
| `--pios-space-2` | 8px | Small internal padding |
| `--pios-space-3` | 12px | Default internal card padding component (between `--space-2` and today's `1.25rem` card padding) |
| `--pios-space-4` | 16px | Default gap between related elements |
| `--pios-space-5` | 20px | Card padding (matches `DriverCard`'s existing `1.25rem` exactly) |
| `--pios-space-6` | 24px | Section-level spacing |
| `--pios-space-8` | 32px | Page-level vertical rhythm between major sections |

### Radius

| Token | Value | Reasoning |
|---|---|---|
| `--pios-radius-sm` | 8px | Inputs, small controls |
| `--pios-radius-md` | 16px | Cards (matches `DriverCard`'s existing `16px` exactly — adopted, not invented) |
| `--pios-radius-full` | 999px | Pills — status badges, and the existing `ActionButton` shape |

The existing `ActionButton` uses a full pill (`999px`) for its primary/secondary buttons. The brief (§4, "Radius") explicitly cautions against "the very large, 'friendly SaaS' radius that has become a cliché." A full-pill *button* is a distinct, purposeful choice (it reads as a tappable, discrete action, common in mobile-native UI, not the same cliché as an oversized-radius *card* or *panel*) — this document keeps it as-is for buttons specifically, but does **not** extend pill/oversized radius to cards, panels, or sheets; `--pios-radius-md` (16px) is the ceiling for anything larger than a pill or a small control. This distinction is deliberate and should not be "simplified" into one universal radius later without re-reading this reasoning.

### Elevation

Restrained, per brief §4 ("Shadows" — "used only to indicate genuine physical elevation... never as a default card treatment"). Two levels only:

| Token | Use |
|---|---|
| `--pios-elevation-none` | The default for ordinary cards — a border (`--pios-color-border`) does the separation work, no shadow (matches `DriverCard`'s existing border-only treatment) |
| `--pios-elevation-raised` | Reserved for something genuinely floating above the page content — a bottom sheet, an active-ride panel, a modal/dialog. A single, restrained shadow value (soft, low-opacity, small blur) — no multi-layer shadow stacks, no colored shadows |

No third "high elevation" level is defined — if a future screen believes it needs one, that is itself a signal to question whether the interface has become too layered (brief §4, "Surfaces": avoid "stacking multiple elevated card layers purely for visual interest").

### Motion

| Category | Duration | Use |
|---|---|---|
| `--pios-motion-fast` | 100–150ms | Micro-interactions — button press feedback (matches `ActionButton`'s existing `transform 0.1s ease` exactly), toggle state change |
| `--pios-motion-base` | 200–250ms | A card/panel entering, a status transitioning | 
| `--pios-motion-slow` | 350–400ms | A sheet sliding in/out — the largest, most visible motion this system permits |

**Easing philosophy**: standard ease-out for anything entering/appearing (feels responsive, not bouncy), ease-in for anything leaving. No spring/bounce easing anywhere — bounce reads as playful/gamified, in tension with the brief's "calm and grounded" tone (§3).

**Where motion is appropriate**: a ride/request status changing (so the eye follows what changed, brief §4 "Motion"); a sheet or dialog entering/exiting; button press feedback (already present). **Where motion must NOT be used**: page transitions between routes (no route-change animation — arriving at a new screen should feel instant, not staged); loading states beyond a simple, honest spinner (no skeleton-shimmer, no progress theatre for genuinely short waits, brief §10 "Loading states"); anything purely decorative (ambient background motion, icon micro-animations with no state-change meaning); anything the user hasn't triggered or that doesn't reflect a real change. `prefers-reduced-motion` must disable every category above except the instant, non-animated fallback state (Section 13).

## 4. Layout system

- **Container**: a single content-width constraint, applied at the page level, not nested per-component — `max-width: 480px` on mobile-width viewports (the product is mobile-first per brief §8; this is not a "mobile breakpoint of a desktop layout," it *is* the layout), expanding modestly on larger viewports (Section 12) rather than reflowing into a multi-column desktop layout, since PIOS Taxi's screens (per brief §5/§6) are inherently single-focus, not data-dense multi-column views.
- **Page margins**: `--pios-space-4` (16px) horizontal on mobile, consistent across every page — no page should define its own custom margin.
- **Content width**: full container width for identity/status/card elements; forms constrain line length implicitly via their own field width, not via a separate typographic measure (there is no long-form reading content in this product).
- **Grid**: no general-purpose CSS grid system is defined — per brief §4 ("Density: low-to-medium... never a dashboard-density grid"), most PIOS screens are single-column, single-focus. Where a genuine two-up layout is needed (e.g., a small pair of stat values), plain flexbox with the spacing scale is sufficient; a dedicated grid component is deliberately not part of this system's foundation set.
- **Mobile layout**: the primary, default layout — single column, bottom-anchored primary action (Section 12), top-anchored identity/status (brief §8).
- **Desktop layout**: not a primary design target today (the coordinator/owner-facing internal tools are the exception — Section 4's `Coordinator`/`OwnerControlCenter` pages already exist as more data-dense, internal-operational screens, explicitly outside the "driver/passenger experience" this brief governs per brief §11: "that language belongs, if anywhere, only to internal operational tooling"). For driver/passenger screens on a wider viewport, the mobile container simply centers with margin around it rather than reflowing — no separate desktop-specific layout is designed at this stage.
- **Vertical rhythm**: `--pios-space-6` (24px) between major page sections; `--pios-space-4` (16px) between related elements within a section; enforced by a page-level wrapper component, not by ad hoc margins on each child (avoids the "silently collapsing or doubling margins" failure mode).

## 5. Component architecture

Responsibilities only — **none of these are created in this task.** Naming favors the functional role (Section 2's "extend beyond taxi" principle) with a taxi-specific name only where the brief's own vocabulary (driver, ride) is precise and intentional.

**FOUNDATION**
- `Button` — the existing `ActionButton` generalizes into this; owns exactly `primary`/`secondary` variants (brief §2.8 — no third visual weight is added without a documented reason), a loading state, and a disabled state. No color prop — variant maps to token, never an arbitrary color.
- `IconButton` — a single icon, no label; same sizing/touch-target rules as `Button` (Section 13).
- `Text` — the `body`/`label`/`caption`/`numeric` roles from Section 3, as one component with a `role` prop, not four separate components.
- `Heading` — `display`/`heading` roles; a `level` prop maps to the correct semantic tag (`h1`–`h3`) independent of visual size, so visual hierarchy and document hierarchy are never forced to match 1:1.
- `Link` — in-app and external variants; visually distinct from `Button` (a link is never styled to look like a button in this system, and vice versa).
- `Divider` — a single, thin, `--pios-color-border` rule; no decorative dividers.

**FORM**
- `Input`, `Select`, `Textarea` — share sizing, border, and focus-ring tokens; `PasswordInput` (existing) becomes a thin variant of `Input` with a visibility toggle, not a separately-styled component.
- `FormField` — label + input + validation message, as one composed unit, so no screen hand-assembles this structure independently.
- `ValidationMessage` — maps directly to `--pios-color-error`/`--pios-color-warning`; always paired with an icon (Section 13, "color is never the sole carrier of meaning").

**SURFACE**
- `Card` — generalizes `DriverCard`'s existing container styling (16px radius, border, no shadow) into the base surface component; `DriverCard` becomes a typed composition of `Card` + `DriverIdentity`, not a separately-styled component.
- `Sheet` — a bottom sheet, `--pios-elevation-raised`, `--pios-motion-slow` entrance; the primary surface for driver-side incoming-request and passenger-side booking-confirmation moments (brief §5/§6).
- `Dialog` — reserved for genuinely interrupting, consequential decisions only (brief §10, "Dialogs"); never used for routine confirmations.
- `Section` — a page-level content grouping using the vertical-rhythm tokens (Section 4); the structural unit every page composes from.

**NAVIGATION**
- `Header` — generalizes the existing `Header` component; carries the PIOS mark at minimal, consistent size (brief §2.1 — never larger than the page's own identity element) plus a `BackButton` slot.
- `BottomNavigation` — for the driver's own multi-screen "home" experience (brief §6) only; passenger flows (arrive-via-link, act, done) do not use persistent bottom navigation, per brief §5's single-screen-at-a-time framing.
- `Tabs` — reserved for genuinely parallel views within one screen (e.g., a future "active / history" split); not used as a primary navigation pattern.
- `BackButton` — a single, consistent treatment, always top-left, matching platform convention.

**PIOS TAXI**
- `DriverIdentity` — name (display typography), and, when available, photo/initial-avatar (brief open question §14.1 — component must support both today); this is the single component enforcing "driver's name is the most prominent identity element" (Section 2, acceptance criterion §13.1 of the brief).
- `DriverTrustIndicator` — see Section 9 in full; text/label-based, never a numeric badge.
- `RideStatus` — the single shared status-state component for requested/matched/arriving/in-progress/completed/cancelled/declined, used identically by both `DriverIdentity`-adjacent passenger views and driver views (brief acceptance criterion §13.3).
- `RideCard` — composes `Card` + `DriverIdentity` (or customer identity, on the driver's side) + `RideStatus`; the canonical list-item for history and active-ride summaries.
- `LocationField` — pickup/destination input; a thin, purpose-typed `Input` variant, not a new base component.
- `RouteSummary` — a compact, textual (not necessarily map-based) summary of pickup → destination; supports the "map should support the task, not decorate it" principle (Section 11) by making a map genuinely optional for basic route confirmation.
- `FareSummary` — price display using `--pios-font-numeric`; no component-level opinion on pricing logic, purely presentational.
- `AvailabilityStatus` — generalizes `DriverCard`'s existing `.available`/`.unavailable` pill exactly; the driver's own single, unambiguous toggle (brief §6) is built from this plus `Button`.
- `RequestCard` — the driver-side incoming-request surface; composes `Card` + identity + a two-action (`accept`/`decline`) `Button` pair.
- `ActiveRidePanel` — the `Sheet`-based, `--pios-elevation-raised` surface for an in-progress ride, shared in shape between driver and passenger, differing only in which actions it exposes.

**FEEDBACK**
- `Alert` — inline, non-blocking; maps to `information`/`warning`/`error`/`success` tokens.
- `Toast` — transient confirmation; used sparingly (brief §10 — "never a growth-style notification").
- `EmptyState` — always takes a specific, contextual message prop (brief §10 — no generic fallback copy permitted by the component's own API; the `message` prop is required, not optional-with-a-default).
- `LoadingState` — a single, honest spinner-based component (generalizes the existing `Spinner`); explicitly documented as *not* a skeleton-screen system (Section 3, Motion).
- `ErrorState` — same "specific, required message" contract as `EmptyState`; distinguishable at a glance from a loading state (never the same visual treatment mid-transition).
- `SuccessState` — brief, calm acknowledgment (brief §5, "Completed ride" — "not a review-solicitation interstitial"); no confetti/celebration animation by default.

## 6. Component states

Every interactive component in Section 5 must define, at minimum, the states relevant to it:

| State | Rule |
|---|---|
| **default** | Uses base tokens exactly — no interactive-only value leaks into the resting state. |
| **hover** | Desktop/pointer-only; must never be the *only* way a state is communicated (mobile has no hover) — matches `ActionButton`'s existing `opacity: 0.92`/background-darken pattern, kept subtle. |
| **active** (pressed) | A quick, clear press feedback — matches `ActionButton`'s existing `transform: scale(0.97)` at `--pios-motion-fast`. |
| **focus** | A visible, non-color-only focus ring (`--pios-color-accent` outline or equivalent) on every focusable element without exception — required for Section 13, never suppressed with `outline: none` alone. |
| **disabled** | Matches `ActionButton`'s existing `opacity: 0.5` + `cursor: not-allowed` + press-feedback suppressed — applied consistently to every component that can be disabled, not just buttons. |
| **loading** | A component-appropriate, honest indicator (Section 3, Motion) — never a disabled-looking dead state with no explanation. |
| **success** | Maps to `--pios-color-success`; time-limited where used inline (e.g., a saved-confirmation), not a permanent visual state. |
| **error** | Maps to `--pios-color-error`; always paired with a specific message (Section 5, `ValidationMessage`/`ErrorState`). |
| **selected** | Matches `DriverCard`'s existing pattern (`border-color` + a matching `box-shadow` ring in the accent color) — used for genuine multi-option selection only (e.g., choosing among a Circle of Trust list), never to fake emphasis on a non-selectable element. |

## 7. Passenger UX primitives

| Moment (brief §5) | Primitive(s) |
|---|---|
| Booking | `LocationField` × (pickup, destination) + `FormField` + one primary `Button` — no wizard, one screen. |
| Searching for driver | Deliberately **no** "searching" primitive exists — the request goes straight from booking to a `RideStatus` = "waiting for [Driver]" state, backed by `DriverIdentity`; there is nothing to visually search among (brief §5, §11). |
| Driver identified | `DriverIdentity` at `--pios-font-display` prominence, from the very first screen (`/i/:driverCode`) — this is not a "match found" moment, the driver was always identified. |
| Waiting | `RideStatus` (information-token state) + `DriverIdentity`; no spinner implying algorithmic work (brief §5). |
| Active ride | `ActiveRidePanel` (`Sheet`, raised elevation) containing `RideStatus` + `DriverIdentity` + a communication `Button`; `RouteSummary`/map optional per Section 11. |
| Completion | `SuccessState`, brief and calm — `RideCard` transitions to its completed `RideStatus`; if feedback exists at all, presented as a small, optional prompt, never a blocking interstitial (brief §5, and open question §14.3). |
| History | A list of `RideCard`s, framed by relationship (`DriverTrustIndicator`, Section 9) rather than as a raw transaction ledger. |

## 8. Driver UX primitives

| Moment (brief §6) | Primitive(s) |
|---|---|
| Availability | `AvailabilityStatus` + a single `Button`/toggle — matches `DriverCard`'s existing pill pattern generalized (Section 5). |
| Incoming request | `RequestCard` in a `Sheet`, with `accept`/`decline` as the only two actions present. |
| Accepting | `RideStatus` transitions immediately (`--pios-motion-base`); no confirmation dialog for accepting (a `Dialog` is reserved for consequential/interrupting decisions, and accepting a request the driver already chose to review is not one — brief §10). |
| Active ride | Same `ActiveRidePanel` shape as the passenger side, with driver-relevant actions (navigation handoff, contact, status update) instead of passenger ones. |
| Completion | `SuccessState`, returns promptly to driver home (brief §6 — "no interstitial platform messaging inserted"). |
| Customer relationship | A list of `RideCard`/identity entries under the driver's own framing ("your customers," Section 9/10) — never a platform-owned CRM-dashboard visual language (brief §11 anti-pattern: generic SaaS dashboard). |

## 9. Trust primitives

The brief calls this critical (§7); so does this task. The rule set:

- **Trust is communicated as recognition, not as a score.** `DriverTrustIndicator` has exactly one job: render a small, text-based label ("Your usual driver" / "New to you" / a Circle-of-Trust "Primary" marker) — it has **no numeric prop**, no `rating` prop, no `score` prop. If a future requirement genuinely needs a rating value stored, it must not be exposed through this component; that would need its own, separately-justified addition, not a silent extension of this one.
- **No ranking, ever, in this component set.** There is no `DriverRank`, no `TopDriver`, no comparative badge anywhere in Section 5's list — deliberately absent, mirroring the brief's "independence is shown by what's absent" principle (§7).
- **The identity stays human, not decorated.** `DriverIdentity` never renders a badge, seal, or overlay on top of the name/avatar — any trust signal is a separate, adjacent `DriverTrustIndicator` instance, never merged into the identity element itself, so identity always reads as "a person," and trust always reads as "a fact about the relationship," never fused into one manufactured "trust score identity."
- **Trust color is reserved and singular.** `--pios-color-trust` (Section 3) is used only by `DriverTrustIndicator` — no other component borrows it, so "this is someone you know" never gets visually confused with any status or action elsewhere in the product.
- **No public trust surface.** `DriverTrustIndicator`'s output is only ever rendered to the one participant it concerns (a passenger sees their own recognition of a driver, never a driver's recognition-count from other passengers) — enforced by the component only ever being called with the current user's own relationship data (Section 10 elaborates).

## 10. Network primitives

Representing real relationships (Personal Client Relationship, Circle of Trust — both already ratified product concepts, see brief front-matter) without a single social-network pattern:

| Concept | Primitive | Explicit rule |
|---|---|---|
| Personal invitation | The `/i/:driverCode` landing itself, via `DriverIdentity` at full prominence — not a separate "invitation" UI widget; the *page* is the invitation. |
| Known driver | `DriverTrustIndicator` (Section 9) rendered wherever a passenger interacts with a driver they've used before. |
| Trusted relationship (Circle of Trust) | A simple list of `RideCard`/identity entries under "your usual drivers" framing (brief §5, §7) — never a "connections" list styled like a contacts/social app; no add/remove-as-friend interaction pattern, no mutual-follow concept (Circle of Trust is passenger-initiated only, per [PRODUCT_DECISION_CIRCLE_OF_TRUST.md](PRODUCT_DECISION_CIRCLE_OF_TRUST.md) — the UI must not imply the driver "accepts" or "follows back"). |
| Repeat relationship | Same `DriverTrustIndicator`, no separate "frequency" metric ever surfaced (no "5th ride together" counter — that is exactly the network-metric this document is instructed not to invent). |
| Business connection | On the driver's side, the customer-relationship list from Section 8 — framed as "your customers," never a platform "network graph" (brief §7 explicitly rules out node/line network diagrams). |

**No metric-bearing component exists anywhere in this system**: no follower count, no connection count, no popularity score, no ranking, no leaderboard, no activity feed, no "people you may know" surface. This is enforced by omission from Section 5's component list, not by a separate rule layered on top — if a future task proposes any of these, it is a deviation from this specification and must be justified against the brief directly, not added quietly to "Network primitives."

## 11. Map and mobility UI

- **The map is a utility component, never the dominant visual surface** (brief §5, §11) — sized and positioned so it never exceeds roughly half the viewport height on a mobile screen, and is never the first thing rendered before status/identity content.
- **Pickup/destination** are captured via `LocationField` (Section 5) as text-first inputs; a map picker is a secondary, optional interaction attached to the field, not the primary input method — respects brief §5's "booking: a single, short, low-friction form."
- **Route** is represented via `RouteSummary` (textual: "12 min, 4.2 km") as the primary, always-present representation; a visual route line on the map is a supplementary enhancement, not a requirement — this keeps the component usable even where the live-tracking open question (brief §14.2) resolves toward "no live map."
- **Driver position**: if implemented at all (open question, brief §14.2), shown as a single, calm marker — no trail, no repeated ping animation, no speed/heading decoration. If the live-tracking question resolves toward "not required," this bullet and its marker primitive are simply never built — this document does not assume the answer.
- **Ride state** is always shown via `RideStatus` (text-based), never via map-only visual cues (a user must never have to interpret map iconography alone to know their ride's state).
- **Map overlays**: minimal — a pickup/destination pin pair and, if built, a driver marker; no weather, traffic-color, or decorative overlay layers.
- **Bottom sheets**: `Sheet` (Section 5) is the standard pattern for any map-adjacent detail (confirm pickup point, active-ride detail) — the map stays visible behind a translucent-free, clearly bounded sheet, never a full-screen takeover that hides the map's own context.

## 12. Responsive rules

- **Touch targets**: minimum 44×44px for every interactive element (matches `ActionButton`'s existing `min-height: 44px` exactly — adopted as the system-wide floor, not just a button-specific value), per brief §9/§13.
- **Bottom actions**: the primary `Button` on any screen with one dominant action is anchored to the bottom of the viewport (brief §8, "bottom-anchored primary actions where a thumb naturally rests"), with safe-area padding (below).
- **Sheets**: `Sheet` slides up from the bottom, never a centered modal, for anything mobile-primary (request review, active-ride detail) — centered `Dialog` is reserved for the rarer, genuinely interrupting case (Section 5).
- **Navigation**: passenger flows carry no persistent navigation chrome (brief §5, single-screen-at-a-time); driver flows use `BottomNavigation` only where the driver genuinely moves between distinct home-area sections (status / requests / customers), not as a default added to every screen.
- **Forms**: one field group visible at a time where possible, native mobile input types used throughout (`tel`, `email`, etc.) so the correct on-screen keyboard appears — no custom-styled input replacing native behavior without a specific reason.
- **Map interaction**: touch-friendly zoom/pan only where the map is genuinely interactive (Section 11); no map is ever the sole way to complete an action that could be done via `LocationField` text entry.
- **Safe-area handling**: every bottom-anchored element (bottom action, `BottomNavigation`, `Sheet`) respects `env(safe-area-inset-bottom)` — required, not optional, given the product's phone-in-hand, in-vehicle usage context (brief §8).

## 13. Accessibility

Direct implementation requirements (brief §9 restated as engineering rules):

- **Contrast**: every text/background and meaningful-icon/background pairing meets WCAG AA (4.5:1 normal text, 3:1 large text/UI components) — checked against the actual token values once Section 16's open color decisions are resolved, not assumed from the current values without verification.
- **Keyboard**: every interactive component (Section 5) must be operable via keyboard alone — tab order follows visual/logical order, no keyboard trap in any `Sheet`/`Dialog`.
- **Focus**: a visible focus indicator on every focusable element without exception (Section 6) — never removed for aesthetic reasons.
- **Semantics**: real, correct HTML elements underneath every component (`<button>` for actions, native form elements for inputs, a real heading hierarchy via `Heading`'s `level` prop) — no `<div onClick>` standing in for a button anywhere in this system.
- **Labels**: every input has a real, programmatically-associated label (`FormField`'s job); every icon-only `IconButton` has an accessible name.
- **Errors**: `ValidationMessage`/`ErrorState` content is associated with its field/context via `aria-describedby` or equivalent, and status changes (e.g., `RideStatus` transitions) are announced via appropriate `aria-live` regions — a status change is a real accessibility event (brief §9), not just a visual one.
- **Reduced motion**: every `--pios-motion-*` transition is wrapped so `prefers-reduced-motion: reduce` disables it, falling back to an instant state change — no exceptions, including the button-press `active` feedback.
- **Touch targets**: 44×44px minimum (Section 12), verified, not assumed, once components are built.

## 14. Anti-patterns (brief §11, made enforceable)

| Brief anti-pattern | Enforcement mechanism |
|---|---|
| Fleet-and-map aggregator interface | No "nearby drivers" or driver-list component exists in Section 5 at all. |
| Generic SaaS admin dashboard bleeding into user-facing screens | Section 4's layout system has no dense-grid/sidebar-plus-topbar primitive; that language is explicitly scoped to internal tooling (`Coordinator`, `OwnerControlCenter`) only. |
| Social network (feeds, follower counts, public profiles) | Section 10: no metric-bearing component exists anywhere in this system, by omission. |
| "AI startup" gradient/glassmorphism aesthetic | Section 3 Elevation: no gradient token, no blur/glass token defined anywhere; `--pios-elevation-raised` is a plain, restrained shadow only. |
| Gig-economy employer app (shift/clock-in language, leaderboards, streak badges) | Section 8: `AvailabilityStatus` is framed as the driver's own toggle, never a "clock in" pattern; no badge/streak/gamification component exists in Section 5. |
| Marketplace storefront implying PIOS owns the transaction | Section 2's `Header` rule: PIOS mark never styled larger/bolder than the page's own `DriverIdentity`. |
| Trust-theatre (decorative badges/seals/star aggregations) | Section 9: `DriverTrustIndicator` is text-only, no icon-badge/seal treatment; no rating-aggregation component exists. |
| Visually "busy"/maximalist UI | Section 3 Motion: strict duration/easing limits; Section 4: single-column, low-density layout by default. |

## 15. Implementation strategy

- **Where tokens live**: `frontend/src/styles/tokens.css` — a single new file, CSS custom properties on `:root`, imported once by the existing `frontend/src/index.css` (a one-line addition to an existing file, not a restructuring). No build-tool change, no PostCSS plugin, no CSS-in-JS — pure CSS custom properties, fully compatible with the existing Vite + CSS Modules pipeline as-is.
- **How existing `.module.css` files migrate incrementally**: each file is edited to replace its literal hex/px/radius values with the matching `var(--pios-*)`, one file at a time, with **no visual change** for every token whose "current de facto value" (Section 3) is adopted as-is — this is a mechanical, low-risk pass, verifiable by diffing rendered output. Only the flagged open decisions (accent hue, trust color, typography) cause an actual visual change, and only once those are explicitly decided (Section 16) — migration and re-theming are two separate, sequenced steps, not one.
- **Component migration order** (suggested, not mandated by this document): start with the lowest-risk, most-reused primitives first — `Button` (generalizing `ActionButton`, already used across the app), `Card` (generalizing `DriverCard`'s container), `AvailabilityStatus` (generalizing `DriverCard`'s existing pill) — since these already exist in a near-final shape and mostly need extraction/renaming, not redesign. `RideStatus`, `DriverTrustIndicator`, and the network primitives (Sections 9–10) come next, since they carry the brief's most product-critical constraints. Map-adjacent components (Section 11) and internal-tooling-only patterns come last, since they are lower-risk to the brief's core thesis.
- **How this avoids breaking existing pages**: because token values are chosen to match current de facto usage (Section 0/3) wherever no brief conflict exists, a page can adopt tokens without its own CSS changing meaning — new shared components are introduced page-by-page (e.g., `DriverHome` starts using `AvailabilityStatus` before `PassengerLanding` does), and an old page using its own literal values continues to render identically until it's migrated; nothing is a forced, all-at-once cutover.
- **What is explicitly out of scope for implementation, still**: no component is built in this task; no existing page is touched; no dependency is added.

## 16. Definition of done

The PIOS design system (this specification) is correctly implemented when, and only when:

1. `frontend/src/styles/tokens.css` exists, is imported by `index.css`, and defines every token named in Section 3 — no token is left as a TODO or a placeholder value.
2. The two flagged open decisions (`--pios-color-accent`'s final hue — currently the generic `#2563eb`, and `--pios-color-trust`'s value) have been explicitly decided by the Product Owner/Architect and recorded, not silently defaulted by whoever implements first (mirrors the brief's own open-question discipline, §14).
3. Every component listed in Section 5 either exists as a real, typed React component consuming only `var(--pios-*)` values (no literal hex/px in its own `.module.css`), or is explicitly deferred with a stated reason — no component silently skipped without note.
4. Every acceptance criterion in [PIOS_TAXI_DESIGN_BRIEF.md](PIOS_TAXI_DESIGN_BRIEF.md) Section 13 is independently checkable against the built components (e.g., criterion 3, "one single, shared visual status-state component," is satisfied specifically by `RideStatus` existing and being reused, not reinvented, on both driver and passenger sides).
5. A grep across `frontend/src` for raw hex colors outside `tokens.css` itself returns zero results once migration (Section 15) is complete for a given page — the same evidence-gathering method used in Section 0 of this document becomes the objective completion check.
6. Section 13's accessibility requirements are verified, not assumed — contrast ratios checked against final token values, keyboard/focus behavior manually verified on at least `Button`, `Input`, and `Sheet`.
7. No component in the shipped set includes a network-size metric, ranking, badge-seal, or gamification element (Section 9/10/14) — checkable by direct code review against this document's own explicit component contracts (e.g., `DriverTrustIndicator` genuinely has no numeric prop in its TypeScript interface).
8. This document itself has not been contradicted by implementation — any deviation (a new component not listed here, a token value chosen without the reasoning this document requires) is treated as a specification update to be made explicitly, not a silent drift.

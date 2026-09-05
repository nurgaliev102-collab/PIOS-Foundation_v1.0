Status: Design decision for `PassengerLanding` (`/i/:driverCode`), the personal invitation experience. Not a Product Decision, not an ADR — decides presentation of already-ratified facts, invents no business rule, authorizes no behavior change. Grounded in [PIOS_TAXI_DESIGN_BRIEF.md](PIOS_TAXI_DESIGN_BRIEF.md), [PIOS_DESIGN_SYSTEM.md](PIOS_DESIGN_SYSTEM.md), [PIOS_DESIGN_IMPLEMENTATION_LOG.md](PIOS_DESIGN_IMPLEMENTATION_LOG.md), [DRIVER_IDENTITY_DESIGN_DECISION.md](DRIVER_IDENTITY_DESIGN_DECISION.md), [PRODUCT_DECISION_ENTREPRENEUR_MODEL.md](PRODUCT_DECISION_ENTREPRENEUR_MODEL.md), [PRODUCT_DECISION_CIRCLE_OF_TRUST.md](PRODUCT_DECISION_CIRCLE_OF_TRUST.md), and [PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md](PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md).

## Phase 1 — Current experience, inspected directly

**What the passenger currently sees** (`frontend/src/pages/PassengerLanding/PassengerLanding.tsx`, `'invited'` step — the first-time entry point): an 88px dark circle with the driver's initial, a small caption "Ваш водитель — {name}", an `<h1>` "👋 Вас пригласил {name}", a subtitle, a "Как это работает" link, a card explaining "Как работает PIOS" (two rows, one of which repeats "Заказ получает {name}"), a primary "Начать" button, an install-PWA card, and an FAQ section.

**What driver information is actually available**: exactly one fact, on every step of this screen — `driverName: string` (`InvitationInfo`, from `invitationSource.ts`). Confirmed by reading `getInvitationByDriverCode` directly: its return type is `{ driverName: string }` only. The underlying `GET /v1/drivers/:id` response *does* carry `availability`, but this screen's own code never reads or forwards it — unlike `RideRequest`'s Circle of Trust step, which fetches availability separately per member. **No availability, no `isPrimary`, no vehicle, no photo is available anywhere on this screen** — confirmed by tracing every step's own state, not assumed.

**Current hierarchy** (top to bottom, `'invited'` step): avatar circle → small "Ваш водитель" caption → large "Вас пригласил {name}" heading → subtitle → "how it works" link → explainer card → primary action → install card → FAQ. The driver's name appears **four separate times** in four different treatments (caption, heading, explainer-card row, and implicitly again in the subtitle) before the user reaches the one thing to actually do.

**Current call-to-action**: "Начать" ("Start") — a single primary button, correctly weighted, but positioned after two full sections (avatar block + explainer card) a first-time visitor must scroll past first.

**What's unnecessary or distracting for the first 3 seconds**: the FAQ section and the install-PWA card are both legitimate, real product surfaces — but neither belongs in the passenger's *first* three seconds of comprehension; both currently render inline with equal visual weight to the invitation itself, competing with it rather than following it.

**How this differs from the design brief**: the driver's name is repeated rather than concentrated into one unambiguous, dominant identity element (brief §2.1: "the most visually prominent identity element on the screen"); the avatar/name/caption/heading treatment is bespoke to this page (a one-off, not the shared `DriverTrustIndicator`) — exactly the "no screen invents its own one-off version" problem [PIOS_DESIGN_SYSTEM.md](PIOS_DESIGN_SYSTEM.md) Section 16 warns against; and the emoji "👋" in the `<h1>` is decoration with no functional purpose (brief §2.7).

## 1. What must the passenger understand within the first 3 seconds?

That a specific, named person — not a company, not an app, not a queue — invited them here, and that ordering a ride means reaching that person directly. Per [PRODUCT_DECISION_ENTREPRENEUR_MODEL.md](PRODUCT_DECISION_ENTREPRENEUR_MODEL.md)'s own central finding, this is not aspirational copy — it is the literal, already-committed mechanism (`driverId` taken verbatim from this URL, never from a pool). The screen's only job in the first three seconds is to make that already-true fact *visible* before anything else competes for attention.

## 2. What should be visually dominant?

The driver's name, rendered once, unambiguously, at the top — via `DriverTrustIndicator` with `emphasis="prominent"`, the same component and treatment already proven on `RideRequest`'s confirmed-ride screen ([PIOS_DESIGN_IMPLEMENTATION_LOG.md](PIOS_DESIGN_IMPLEMENTATION_LOG.md) Task 5 addendum). Everything else on the screen — PIOS's own explanation of itself, the FAQ, the install prompt — is secondary and follows below, never above or beside it.

## 3. What role does the driver's identity play?

The entire reason the page exists. Not a data point inside a welcome message — the subject the welcome message is *about*. Concretely: today's screen currently treats the driver's name as an attribute of PIOS's own greeting ("👋 Вас пригласил {name}," a PIOS-voiced sentence naming the driver). The redesign inverts this — the driver's name leads, and PIOS's own explanatory text follows underneath it, addressed to the passenger, not spoken as if PIOS were the host introducing a guest.

## 4. What role does PIOS branding play?

Infrastructure, not a co-star. The `Header`'s "PIOS" mark stays exactly as small and restrained as it already is (unchanged, per [PIOS_DESIGN_SYSTEM.md](PIOS_DESIGN_SYSTEM.md) Section 2's own rule — this task does not touch `Header`). Below it, PIOS's own explanatory content ("Как работает PIOS," the FAQ) is real and stays on the page — a first-time passenger genuinely needs it — but it is repositioned *after* the driver identity and the primary action, so it reads as supporting material a curious passenger can scroll into, not the first thing competing with the person who actually invited them.

## 5. What should the primary action be?

The existing "Начать" action — unchanged in meaning, destination, and handler (`handleContinue`, which sets `step` to `'auth'`). This decision does not invent a new action; it only asks that this one existing action become reachable, and visually obvious, sooner — closer to the driver identity, not buried after two full explanatory sections.

## 6. What information should deliberately remain absent?

- **Any availability indicator.** Not available on this screen's own data (Phase 1) — showing one would mean fabricating a fact the code hasn't actually fetched, exactly what [DRIVER_IDENTITY_DESIGN_DECISION.md](DRIVER_IDENTITY_DESIGN_DECISION.md) Section 9 already ruled out for the analogous case on `RideRequest`.
- **Any Circle-of-Trust "primary" marker.** This is, by definition, a first-time or not-yet-decided relationship at this step (`'invited'`/`'confirm-add'`) — Circle of Trust membership is exactly what a *later* step (`'confirm-add'`) asks the passenger to decide. Asserting "primary" here would be premature and untrue.
- **Any rating, ride count, or vehicle detail.** Confirmed absent from the data model entirely ([DRIVER_IDENTITY_DESIGN_DECISION.md](DRIVER_IDENTITY_DESIGN_DECISION.md) Phase 1) — this decision does not revisit that finding, it inherits it.
- **The decorative wave emoji** in the heading — no functional purpose, and the identity component itself now carries the "this is a warm, human moment" weight through typography and an avatar, not through an emoji.

## 7. How does this differ from Uber/Bolt-style onboarding?

Those products' first screen sells the *platform* — a hero image of "get a ride in minutes," a generic map, a promise about the service. This screen sells nothing and has no hero image of an abstract car or city — it identifies one real, specific, named person before it explains anything else, because there is nothing to "sell": the passenger already has who they need, and the screen's only job is to confirm that clearly and get out of the way.

## 8. How does this screen reinforce the entrepreneur model?

By making it structurally impossible to read this screen as "PIOS assigned you a driver." The identity leads; PIOS's own explanatory copy is now clearly positioned as *infrastructure description* ("here's how PIOS helps you reach [name] directly"), not a substitute host narrating who you've been given. [PRODUCT_DECISION_ENTREPRENEUR_MODEL.md](PRODUCT_DECISION_ENTREPRENEUR_MODEL.md)'s own framing — "PIOS is digital infrastructure that an independent entrepreneur operates for their own business" — is the literal ordering principle applied to the layout itself.

## 9. How does it establish the beginning of a personal client relationship?

[PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md](PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md) Part 1 defines this relationship as "recognized" and "recurring" — this screen is the moment recognition *begins*. It does not pre-assert a relationship that doesn't exist yet (no "primary" badge, no "your driver" language implying ownership) — it introduces the person, explains what happens next in plain terms, and lets the actual relationship-forming action (registration, and — one step later, for a returning passenger — the explicit "Добавить в круг доверия" decision Rule 9 of [PRODUCT_DECISION_CIRCLE_OF_TRUST.md](PRODUCT_DECISION_CIRCLE_OF_TRUST.md) requires) happen through the real, already-existing flow, unchanged by this decision.

## Component change required

`DriverTrustIndicator` (Task 5) currently has no avatar/initial treatment — it is text-only. This screen's existing 88px initial-avatar (`.heroAvatar`) already matches the design brief's own explicit recommendation ("if no real photo exists yet, a clear initial/name treatment beats a generic silhouette avatar," brief §4/§10) and is worth keeping, not discarding. Rather than building a new, unrelated component, this decision extends `DriverTrustIndicator` with one new, **opt-in** prop (`showAvatar?: boolean`, default `false`) so the avatar only renders where a caller explicitly asks for it — `RideRequest`'s already-verified `emphasis="prominent"` usages (Task 4/5) remain pixel-identical unless they are separately updated to opt in. This is documented here, and implemented in Phase 3, as an extension of the one component this task is authorized to touch — not a new component.

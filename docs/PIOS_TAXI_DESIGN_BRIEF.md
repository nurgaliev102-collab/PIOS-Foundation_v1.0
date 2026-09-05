Status: Product-level design brief, not an ADR, not a Product Decision, authorizes no code. Written to become the foundation for a future PIOS design system — direction, not implementation tokens (no hex values, no font names are chosen here unless explicitly justified). Grounded throughout in already-ratified PIOS product decisions — [PRODUCT_DECISION_ENTREPRENEUR_MODEL.md](PRODUCT_DECISION_ENTREPRENEUR_MODEL.md), [PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md](PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md), [PRODUCT_DECISION_CIRCLE_OF_TRUST.md](PRODUCT_DECISION_CIRCLE_OF_TRUST.md), [PRODUCT_FOUNDATION.md](PRODUCT_FOUNDATION.md) — and in the actual mechanism already committed to code: the personal invitation link (`/i/:driverCode`), a passenger arriving to a screen that names their driver by name, not a badge number. Every principle below traces back to one of these, not to visual taste.

## 1. Product design thesis

PIOS should feel like **walking into a shop you already know**, not like opening a marketplace app and waiting to see who shows up. The entrepreneur model is not a tagline — it is already implemented in exactly one mechanism today (the personal link a passenger arrives through, which names their driver on the very first screen) — and the UI's whole job is to make that mechanism *feel* like what it structurally already is: a relationship the driver owns and the passenger recognizes, with PIOS visible only as the reliable infrastructure underneath it, never as the shopfront itself.

This means the interface must resist every instinct a taxi-app designer brings from Uber/Bolt/Yandex: no anonymous fleet of interchangeable cars circling a map, no platform-branded hero moment, no "your driver has been assigned" framing that implies PIOS did the choosing. Instead: the driver's own name and presence lead every screen a passenger sees; the platform's technology shows up as quiet reliability (a request that arrives without a phone call, a status that updates itself, a history that doesn't get lost) rather than as a visual centerpiece. Modern and technologically strong, yes — but the technology's job is to disappear into dependability, the way a good phone network is invisible until you need it and then simply works.

## 2. Design principles

1. **The driver is the face; PIOS is the frame.** Every passenger-facing screen should visually foreground the driver's own identity (name, and later photo/vehicle) before any PIOS branding. PIOS's own mark stays small, consistent, and infrastructural — the way a payment network's logo appears on a card without competing with the merchant's own brand on it.
2. **No fleet, no map full of anonymous cars.** The product has exactly one driver per relationship at any given interaction — there is no "searching among nearby drivers" moment to visualize, because there is no fleet to search. Avoid any UI pattern that implies choice-among-many where the actual mechanism is one recognized relationship.
3. **Status over decoration.** Every visual element must answer "where is my ride right now" or "what does my business look like right now" faster than it impresses. A ride's state (requested → matched → arriving → in progress → completed) and a driver's own status (available, busy, off) are the things worth spending visual weight on — not chrome around them.
4. **Show the relationship, not a transaction log.** A returning passenger should feel recognized — screens that involve a known driver (Circle of Trust, primary-driver designation) should read differently from a first-time cold interaction, without becoming a social-media "profile" or "friends list."
5. **Quiet confidence, not reassurance theatre.** Reliability is communicated through things behaving predictably (a status that never lies, a number that never jumps), not through badges, checkmarks-as-decoration, or trust-seal iconography bolted onto every card.
6. **Local and human, not global and abstract.** Visual choices should read as belonging to a real place with real people in it — never the placeless, stock-photo "global gig economy" aesthetic. Prefer specific, legible information (a name, a car, a street) over generic iconographic abstraction of "mobility."
7. **Function before flourish, everywhere.** Every gradient, shadow, animation, or rounded corner must justify itself by making something easier to read, find, or trust — never present purely because it looks contemporary. If removing a visual effect changes nothing about comprehension, remove it.
8. **One clear next action per screen.** Reflecting the platform's own principle that the driver runs their own business without a middle layer managing them — the UI should never present the user with a dashboard of competing calls-to-action. One primary action, clearly weighted; everything else recedes.
9. **Honest about what PIOS doesn't decide.** Where the product deliberately has no opinion (PIOS does not choose your driver, does not set your price, does not rank your reputation against a stranger's) the UI should not manufacture the appearance of platform authority it doesn't actually hold — no algorithmic-feeling "best match" framing, no unearned rating aggregation UI.
10. **Built to extend beyond taxi without a redesign.** Every layout and component decision should be phrased in terms that hold for "an independent service provider and their recognized customer," not "a car and a rider" — so a future vertical (any independent-provider business) can reuse the same visual language, not just the same code.

## 3. Brand personality

| | |
|---|---|
| **Personality** | The competent local business owner your neighborhood already trusts — not a startup, not a corporation, not a gig platform. Direct, unpretentious, present. |
| **Emotional tone** | Calm and grounded. Warmth expressed through clarity and recognition (naming the driver, remembering the relationship), not through cheerful copy or playful illustration. |
| **Perceived trust level** | High, but *earned through transparency* (you can see who you're dealing with, what's happening, and why) rather than *asserted through trust-badge decoration*. |
| **Perceived technological level** | Quietly advanced — real-time, dependable, fast — but never showcased as the product's own personality. Technology is plumbing, not the pitch. |
| **Platform-to-user relationship** | Infrastructure-to-owner, not employer-to-worker and not marketplace-to-consumer. The platform should visually behave like a utility the driver's business runs on (closer in spirit to "your business's own booking page" than to "your employer's dispatch app"), and like a familiar local service to the passenger. |

## 4. Visual direction

Direction, not tokens — the reasoning behind each choice matters more than a specific value at this stage.

- **Color philosophy**: A restrained, warm-neutral base (the "shop," not the "platform") with a single accent used sparingly and consistently for the one thing that matters most on any given screen (the primary action, or the live-status indicator) — never multiple competing accent colors, never a gradient standing in for an accent. Status colors (available/busy/en-route/completed) should be a small, consistent, semantically-locked palette, distinct from the brand accent, so "the ride is moving" is never visually confusable with "press this button." Avoid anything that reads as a generic tech-startup palette (saturated purple/blue gradients) — the goal is a palette that could belong to a well-run local business's own signage, translated to a screen.
- **Typography philosophy**: A typeface (or pairing) that reads as clear and human rather than geometric-and-corporate — legibility for names, addresses, and prices at a glance (often in bright sunlight, on a moving vehicle's phone mount) matters more than personality. A distinct but restrained display weight for the driver's own name and key status words; a workhorse text face for everything else. Avoid anything that reads as "SaaS dashboard default" or "AI-startup display font."
- **Density**: Low-to-medium. Enough information to act on immediately, never a dashboard-density grid of secondary data. Passenger-facing screens in particular should feel closer to "a single clear card" than to "an app full of panels."
- **Whitespace**: Generous around the one thing that matters (driver name, current status, primary action); tighter and more efficient around supporting/historical information (history lists, past connections) once the user has demonstrated they want density there.
- **Surfaces**: Mostly flat, with elevation reserved for the one element genuinely "above" the rest of the screen at that moment (an active-ride card, a bottom-sheet action). Avoid stacking multiple elevated card layers purely for visual interest.
- **Borders**: Used functionally, to separate genuinely distinct zones (e.g., a status bar from content) — not decoratively outlining every card.
- **Radius**: A single, moderate, consistent radius scale used because it reads as approachable and human rather than sharp/corporate — not the very large, "friendly SaaS" radius that has become a cliché, and not sharp right angles that would read cold. Consistency across the whole product matters more than the exact value.
- **Shadows**: Minimal, used only to indicate genuine physical elevation/interactivity (a floating action, a modal) — never as a default card treatment.
- **Iconography**: A small, consistent, literal icon set (status, actions, navigation) — legible at a glance, not custom illustrative icons that need decoding. No icon should exist purely as a section header decoration.
- **Imagery**: Real and specific over generic — a driver's own name and (later) their own photo/vehicle carry the product's warmth; avoid stock photography of "happy commuters" or abstract mobility illustration entirely. If no real photo exists yet, a clear initial/name treatment beats a generic silhouette avatar.
- **Motion**: Minimal and purposeful — used to communicate state change (a status transitioning, a card arriving) so the user's eye follows what changed, never as ambient decoration, loading flourishes, or attention-getting animation. If a transition doesn't help the user understand *what just happened*, it shouldn't exist.

## 5. Passenger experience

- **Landing** (`/i/:driverCode`): The first thing the passenger sees is *their driver*, by name — this is the entrepreneur model's entire visual thesis in one screen. No platform hero image, no marketing copy about PIOS. The tone is "you've arrived at [Driver]'s page," not "welcome to PIOS."
- **Booking**: A single, short, low-friction form — minimal fields, one clear submit action. No multi-step wizard, no upsell surfaces. This is a request to a person you already recognize, not a checkout flow.
- **Driver search/waiting**: There is no "searching for nearby drivers" moment to animate — the request went to one recognized driver. The waiting state should communicate "your request reached [Driver], waiting for their response," calmly, without a spinner implying an algorithm is working behind the scenes.
- **Active ride**: A calm, current-status-forward view — where the ride stands right now, communicated clearly, without a live-tracking map treated as the emotional centerpiece unless it materially helps ("driver is arriving" matters more than pixel-precise position). Contact/communication with the driver should be one clear action, not buried.
- **Completed ride**: A brief, warm acknowledgment — not a review-solicitation interstitial, not an upsell screen. If a rating/feedback mechanism exists, it should feel like giving feedback to someone you'll likely use again, not rating a stranger you'll never see.
- **History**: A simple, chronological record framed around the relationship ("your rides with [Driver]"), not a generic transaction ledger. This is where Circle of Trust and primary-driver context can surface naturally — "your usual drivers" reads very differently from "your ride history."

## 6. Driver experience

- **Driver home**: The driver's own business dashboard — their status, their upcoming/active work, their own link to share — framed as tools for running their business, not as a shift-start screen for a job. No employer-style "clock in" language or visual metaphor.
- **Availability/status**: A single, unambiguous, driver-controlled toggle — this is the driver's own active declaration (already the case in the domain model), and the UI should treat it with the same weight a shop owner's "open/closed" sign carries: entirely their call, always clear at a glance, never nudged or gamified by the platform.
- **Incoming request**: Presented as a request arriving *to them* from someone in their own network reach — clear, immediate, low-friction to accept or decline — never framed as an assignment being handed down.
- **Active ride**: Mirrors the passenger's active-ride clarity, plus whatever the driver specifically needs to do their job (navigation handoff, contact, status update) — kept minimal, since this is a moment the driver is often not looking at their phone.
- **Completed ride**: Confirms the outcome and returns the driver promptly to their own home/status view — no interstitial platform messaging inserted between "ride done" and "back to my business."
- **Customer/network relationship**: Where a driver can see their own recognized/returning customers (mirroring the passenger's Circle of Trust) — this should read as *their own client list*, not a platform-owned CRM dashboard. Ownership language and visual framing ("your customers," never "PIOS's users assigned to you") matters as much here as anywhere in the brief.

## 7. Network-first visual language

The hardest and most important part of this brief. PIOS must communicate a *network of trusted, independent participants* without becoming, or visually resembling, a social network.

- **People are named, not counted.** Where the interface shows another participant (a passenger's primary driver, a driver's recognized customer), it should show *who* — a name, and eventually a face — never a count, a leaderboard position, or a follower-style number. Networks-as-numbers is the social-network failure mode to avoid entirely.
- **Relationships are shown as state, not as a feed.** Circle of Trust and Personal Client Relationship are *facts about who you already work with*, surfaced at the point of use (booking, requesting) — never as a browsable social feed, activity stream, or "people you may know" surface. There is no timeline to scroll.
- **No public profiles, no visible follower/following structure.** A driver's or passenger's recognition by others is never exposed as a public, comparable, or discoverable social signal — it stays a private, functional fact between the two parties involved (Personal Client Relationship's own [RATIFIED] boundary: not ownership, not a commercial listing, not a rating aggregation).
- **Primary is a designation, not a rank.** The "primary driver" concept (Circle of Trust) should read visually as "the one I usually go to," a simple, singular marker — never as a competitive ranking among a passenger's drivers, and never visible to other drivers as a comparison point.
- **Cooperation is implicit in reliability, not illustrated.** The platform's coordination of requests/matching/history should never be visualized as a literal "network diagram" (nodes and lines) — that reads as infrastructure-for-its-own-sake. Cooperation shows up as *things working smoothly across participants* (a request reaching the right person, a history staying intact), not as a graphic of the network itself.
- **Independence is shown by what's absent, not what's added.** The strongest way to communicate "this driver runs their own business" is the *absence* of platform-authored framing around them (no platform rating badge stamped over their name, no "PIOS Verified Partner" seal, no algorithmic match-score) — restraint here does more work than any added trust iconography would.

## 8. Mobile-first principles

- Designed for one hand, one thumb, frequently in motion or in a vehicle — the primary action on any screen must be reachable and legible without repositioning the phone.
- Assume variable lighting (bright sunlight, dark car interior) and variable attention (a driver glancing briefly while stopped) — status and the single primary action must be readable at a glance, high-contrast, never dependent on subtle color alone.
- Assume intermittent connectivity — every state (request sent, waiting, matched) must have a clear, honest "still working on this" treatment rather than an ambiguous blank/frozen screen, and nothing should silently fail without visible feedback.
- No desktop-first patterns retrofitted down (multi-column layouts collapsing awkwardly, hover-dependent affordances, dense data tables) — the mobile layout is the primary design, not a breakpoint of something else.
- Bottom-anchored primary actions where a thumb naturally rests; top-anchored identity/status where the eye naturally lands first.

## 9. Accessibility principles

- Color is never the sole carrier of meaning — every status (available/busy, ride state, success/error) pairs a color with a label or icon, so the product remains usable for color-blind users and legible in poor lighting.
- Minimum contrast ratios follow WCAG AA as the baseline for all text and meaningful icons, without exception for "brand" colors.
- Every interactive element has a real, sufficiently large touch target (not a visually-small element with an invisible larger hit area substituting for genuine size) — appropriate for a product used one-handed, often in a moving vehicle.
- All interactive elements and status changes are reachable and announced correctly for screen readers — a ride-status change is a genuine accessibility event, not just a visual one.
- Text is never smaller than a comfortably legible baseline on a phone screen at arm's length; the product does not rely on zooming to be usable.
- Motion respects reduced-motion preferences — nothing essential to understanding a screen depends on an animation the user may have disabled.

## 10. Design system strategy

What should eventually become shared, reusable primitives (naming reflects PIOS's own domain language, not generic taxi-app terms):

- **Layout**: a small set of page shells (single-focus card screen, status/action screen, list/history screen) rather than a general-purpose grid system — most PIOS screens are single-purpose.
- **Typography**: a scale of 4–6 sizes/weights covering identity (driver/customer name), status, body, and metadata — no more than that until real need is shown.
- **Buttons**: one primary, one secondary, one destructive/decline treatment — deliberately not a large button-variant matrix; the "one clear action per screen" principle should be enforced by the component set itself.
- **Inputs**: minimal, mobile-optimized form fields (the booking form is the canonical case) with clear, inline validation — no dense multi-field forms to design for yet.
- **Cards**: an identity card (driver/customer — name-forward, minimal chrome) and a status card (ride/request state) as the two core patterns everything else composes from.
- **Navigation**: minimal by design — most flows are single-screen-at-a-time (arrive via a specific link, act, done); a persistent driver-side navigation shell for the driver's own multi-screen "home" experience.
- **Map UI**: a restrained, secondary element (confirm a location, show arrival proximity) — never the dominant visual surface it is in aggregator apps; treat it as a utility component, not a canvas.
- **Ride states**: a single, shared status-state component (requested/matched/arriving/in progress/completed/cancelled) reused identically across driver and passenger views, so the same state always looks the same to both sides.
- **Status indicators**: a small, consistent, semantically-locked set (driver availability, ride state, connection state) — distinct visual language from ordinary UI accents, per Section 4.
- **Notifications**: calm, informative, actionable — never a growth-style notification (no "come back!" re-engagement framing); every notification should map to something that actually changed in a ride or relationship.
- **Dialogs**: reserved for genuinely consequential, interrupting decisions (decline a request, cancel a ride) — not used for routine confirmation of low-stakes actions.
- **Empty states**: honest and specific ("no rides yet with [Driver]" / "no requests right now") rather than generic illustration-plus-platitude empty states.
- **Loading states**: brief, honest, never disguised as content (no skeleton screens implying data that isn't there yet if the wait is genuinely short) — and always distinguishable from a stalled/failed state.
- **Errors**: specific about what happened and what to do next, in plain language — never a generic "something went wrong," and never blaming the user.

## 11. What PIOS must NOT become visually

- A fleet-and-map aggregator interface (car icons converging on a map, "searching for nearby drivers" animation, anonymous driver-badge-number identity).
- A generic SaaS admin dashboard (dense data grids, sidebar-plus-topbar chrome, KPI-tile homepages) bleeding into passenger- or driver-facing screens — that language belongs, if anywhere, only to internal operational tooling, never to the driver's or passenger's own experience.
- A social network (feeds, follower counts, public profiles, "people you may know," likes/reactions on relationships).
- An "AI startup" aesthetic (purple/blue gradient hero sections, glassmorphism panels, abstract neural-network-line decoration, an overused sparkle/magic-wand motif for anything algorithmic).
- A gig-economy employer app (shift language, "clock in/out," leaderboards, gamified streaks or badges for driver activity).
- A marketplace storefront that visually implies PIOS itself is the seller of the ride (platform branding as the dominant visual identity on any transactional screen, at the expense of the driver's own identity).
- A trust-theatre interface (verification badges, seals, and star-rating aggregations stacked decoratively rather than shown only where they carry real, specific meaning).
- Visually "busy" or maximalist UI — competing accent colors, multiple simultaneous animations, decorative illustration without informational purpose.

## 12. Reference products

Named for the **UX principle worth studying**, not for their visual identity — none of these should be visually copied:

- **iMessage / WhatsApp (1:1 conversation framing)** — for how a product built around one-to-one recognized relationships avoids ever feeling like a directory or feed, and how status (delivered, read, typing) is communicated with restraint.
- **A well-run independent business's own booking tool** (e.g., a hairdresser or trainer's personal booking page, not a marketplace of many providers) — for how a single-provider, relationship-first booking flow differs structurally from a many-provider marketplace flow.
- **Apple Wallet / boarding-pass-style cards** — for how a single, information-dense, high-trust card can carry exactly the right amount of live status without becoming a dashboard.
- **Basecamp's product-writing philosophy** (calm, plain-language product copy, no growth-hacking tone) — for how notification and empty-state language can stay warm and human without being cute.
- **Signal (restraint as a trust signal)** — for how the deliberate absence of tracking-style UI chrome (read receipts by default, engagement metrics, etc.) itself communicates respect for the user's autonomy, directly analogous to PIOS's "independence shown by what's absent" principle (Section 7).

## 13. Design acceptance criteria

A future implementation of this brief must satisfy, objectively:

1. On every passenger-facing screen involving a specific driver, that driver's name is the most visually prominent identity element on the screen — more prominent than any PIOS branding.
2. No screen presents a "searching among multiple drivers" visual pattern (spinning map pins, a list of nearby driver cards to choose from) anywhere in the taxi vertical's core flow.
3. Every ride/request state (requested, matched, arriving, in progress, completed, cancelled, declined) has one single, shared visual status-state component used identically on both the driver's and the passenger's side.
4. The driver's own availability toggle is a single, unambiguous control reachable in one tap from driver home, with no platform-authored nudge copy attached to it.
5. No screen in the product displays a raw count of "connections," "followers," or similar network-size metric to any user about themselves or another participant.
6. No participant's profile is publicly browsable or discoverable by users who do not already hold a relationship with them (Circle of Trust / Personal Client Relationship boundary).
7. Every color used to convey status passes WCAG AA contrast and is paired with a non-color indicator (icon or label).
8. Every primary touch target on every mobile screen meets a minimum comfortable tap-target size, verified on at least one small-screen device profile.
9. No decorative animation exists anywhere in the shipped product that does not correspond to an actual state change the user needs to notice.
10. Every empty state and every error message is specific to the actual condition (not a single generic fallback string reused everywhere).
11. A design-system audit can point to a single shared component for each of: primary button, status indicator, identity card, and dialog — i.e., no screen invents its own one-off version of any of these.
12. Nothing in the shipped visual language (icon, illustration, layout pattern, copy tone) is drawn from, or easily mistaken for, Uber's, Bolt's, or Yandex Taxi's own visual identity.

## 14. Open design questions

Only questions that genuinely require a Product Owner / Architect decision before design-system implementation can proceed — not implementation details this brief can settle on its own:

1. **Driver photography**: will real driver photos be part of the pilot, or does the product launch with name-and-initial identity only? This materially changes the identity-card component's design (Section 10) and cannot be decided by visual taste alone.
2. **Map usage extent**: is live position tracking during an active ride an actual pilot requirement, or is "arriving" / "en route" sufficient state without a live map? This is a product-scope question (data, privacy, backend capability) before it is a visual one.
3. **Rating/feedback mechanism**: does PIOS intend any rating or feedback capture at all in the taxi vertical, and if so, is it private-to-the-relationship (as Section 7's principles would require) or in any way aggregated/comparative? No ratified product document yet answers this, and Section 11's "no trust-theatre" principle depends on the answer.
4. **Fleet/team delegation**: [PRODUCT_DECISION_CIRCLE_OF_TRUST.md](PRODUCT_DECISION_CIRCLE_OF_TRUST.md) explicitly leaves Fleet/team delegation unresolved — if it is ever ratified, it introduces a second identity type (a business with multiple drivers behind one relationship) that this brief's "one recognized person" visual language does not yet account for, and would need its own extension.
5. **Cross-vertical identity**: when PIOS expands beyond taxi, does "driver" become a generic "provider" identity system reused as-is, or does each vertical get its own identity framing on top of shared components? This affects how literally Section 10's component names should be generalized now versus later.
6. **Brand mark itself**: this brief deliberately keeps PIOS's own visual presence minimal and infrastructural (Section 2, Principle 1) — but no actual logo/wordmark exists yet to know how small "minimal" can practically be while remaining legible and distinct; that is a mark-design exercise this brief cannot pre-empt.

Status: Design decision for `DriverTrustIndicator`, the first PIOS-specific (non-foundation) design-system component. Not a Product Decision, not an ADR — this document decides how to *present* facts the product already ratifies elsewhere; it invents no new business rule, and authorizes no code beyond the component this document itself describes. Grounded in [PIOS_TAXI_DESIGN_BRIEF.md](PIOS_TAXI_DESIGN_BRIEF.md), [PIOS_DESIGN_SYSTEM.md](PIOS_DESIGN_SYSTEM.md), [PIOS_DESIGN_IMPLEMENTATION_LOG.md](PIOS_DESIGN_IMPLEMENTATION_LOG.md), [PRODUCT_DECISION_ENTREPRENEUR_MODEL.md](PRODUCT_DECISION_ENTREPRENEUR_MODEL.md), [PRODUCT_DECISION_CIRCLE_OF_TRUST.md](PRODUCT_DECISION_CIRCLE_OF_TRUST.md), and [PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md](PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md).

## Phase 1 — What the data model actually has

Checked directly against the backend domain model and the exact API shapes `RideRequest.tsx` already consumes, not assumed:

| Fact | Source | Available? |
|---|---|---|
| Driver display name | `backend/driver-management/.../domain/Driver.kt`: `val displayName: String? = null` — nullable | **Yes**, but not guaranteed present |
| Driver availability | Same file: `Availability.AVAILABLE \| UNAVAILABLE` | **Yes** — but only where the caller actually fetched it (Circle of Trust step does; the confirmed-ride step does not — it only ever received `driverName` from the invitation lookup, never availability) |
| Driver id / "driver code" | `DriverId` — this is the same string used in the `/i/:driverCode` URL | Yes, but not user-facing information (an internal identifier, not something to display as if it were a name) |
| Circle-of-Trust "primary" designation | `CircleMember.isPrimary: boolean`, [PRODUCT_DECISION_CIRCLE_OF_TRUST.md](PRODUCT_DECISION_CIRCLE_OF_TRUST.md) | **Yes** — a real, passenger-chosen, ratified fact |
| Relationship creation time | `CircleMember.createdAt` | Technically present, but not itself product-meaningful to show ("since March" reads as an invented notion of tenure the product has never ratified) |
| Vehicle, license plate, driver photo | Searched exhaustively: `Driver.kt`'s own fields, and a repository-wide grep for `vehicle`/`licensePlate`/`photo`/`avatar.*url` across both backend and frontend | **Does not exist anywhere in the data model.** Every "verified"/"avatar" match found was either an unrelated code comment (session/backend-verification language) or `DriverCard`'s own existing initial-letter avatar treatment — never a real photo field. |
| Numeric rating, ride count, popularity score | Searched the full domain model (`Driver`, `Connection`, `Order`, `Proposal`, `Assignment`) | **Does not exist anywhere.** Confirmed absent, not merely unused. |

## 1. What driver identity should communicate

Per [PIOS_TAXI_DESIGN_BRIEF.md](PIOS_TAXI_DESIGN_BRIEF.md) Section 1: that the passenger has arrived at *this specific driver's own page*, not a platform match — the entrepreneur model's entire visual thesis in one element. [PRODUCT_DECISION_ENTREPRENEUR_MODEL.md](PRODUCT_DECISION_ENTREPRENEUR_MODEL.md) states this is not aspiration but the literal, already-committed mechanism: `RideRequest.tsx` posts `driverId` taken verbatim from the URL the passenger arrived through, never from a pool. `DriverTrustIndicator`'s job is to make that already-true fact *visually* true too — the driver's name reads as the person you're dealing with, not a label on a booking record.

## 2. Facts available (restated from Phase 1)

Name (nullable), availability (only where the caller has it), Circle-of-Trust primary designation (only where the caller has it). Nothing else exists to draw on.

## 3. Facts safe to expose

- **Name** — the one fact this component exists for. Safe and required; [PERSONAL_CLIENT_RELATIONSHIP.md](PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md) Part 1 explicitly rules out treating this relationship as ownership or a commercial listing, but naming the person is not either of those — it's the opposite of the anonymous-badge-number pattern the brief rejects.
- **Availability** — already shown elsewhere in the app (`DriverCard`'s own pill) as a plain fact about right-now readiness, not a performance signal. Safe, and only ever shown when the caller actually has it — never defaulted or guessed.
- **Primary designation (`isPrimary`)** — [PRODUCT_DECISION_CIRCLE_OF_TRUST.md](PRODUCT_DECISION_CIRCLE_OF_TRUST.md) Rule 1 makes this a real, passenger-chosen fact ("primary is a passenger-chosen designation within the circle"). Safe to show as a small label ("the one I usually go to" — [PIOS_DESIGN_SYSTEM.md](PIOS_DESIGN_SYSTEM.md) Section 9's own words), **never** as a rank or a score, and **never** visible to anyone but the passenger who made that choice (this component never receives or renders another passenger's relationship to a driver — Circle of Trust Rule 3: created only by the passenger's own explicit action).

## 4. Facts that must NOT be displayed

- Any numeric rating, ride count, or "X rides together" tally — does not exist in the data model (Phase 1), and inventing one here would be exactly the "fabricated trust score" this task's own constraints forbid.
- Any ranking or comparison between drivers ("top driver," "most popular," a sort-by-trust ordering) — [PIOS_DESIGN_SYSTEM.md](PIOS_DESIGN_SYSTEM.md) Section 9 already rules this out categorically; nothing in the data model could support it truthfully regardless.
- The driver's own internal `DriverId` as if it were a name or a public handle — it is an implementation identifier, not information the design brief's "human, not abstract" principle (Section 2, Principle 6) wants surfaced.
- A follower/connection count of any kind, on either side of the relationship — no such count exists in the data model, and Circle of Trust's own membership size is nobody's business but the passenger's, never shown as a badge on the driver.
- Relationship *duration* or *creation date* — technically available (`createdAt`) but not ratified as product-meaningful; showing it would silently invent a "loyalty tenure" concept no product document defines.

## 5. Why this hierarchy supports PIOS

The component makes exactly the facts the product has already ratified visible, and nothing else — this is what keeps "driver-as-face" honest rather than decorative. A component that showed a rating would communicate a trust *PIOS itself computed and vouches for* — precisely the "unearned platform authority" [PIOS_TAXI_DESIGN_BRIEF.md](PIOS_TAXI_DESIGN_BRIEF.md) Section 2 Principle 9 warns against ("PIOS doesn't decide... the UI should not manufacture the appearance of platform authority it doesn't actually hold"). By showing only name, real-time availability, and the passenger's own prior choice (primary), the component only ever states facts the *passenger and driver themselves* generated — PIOS is reporting, not judging.

## 6. How this differs from a conventional taxi-driver card

A conventional taxi-app driver card (Uber/Bolt/Yandex-style) typically leads with a star rating, a trip count, sometimes a car photo/model, and treats the driver as one interchangeable instance among a fleet, chosen algorithmically. `DriverTrustIndicator` has none of that surface by design: no rating slot exists in its API at all (not hidden, not optional-and-unused — structurally absent, so a future caller cannot accidentally wire one in), no vehicle field exists because none exists in the data model, and the component is never rendered as one of several competing cards to pick from (Section 7 below) — it always renders exactly one driver, the one this specific passenger already has a real relationship or link with.

## 7. How this supports Circle of Trust

Renders the `isPrimary` fact as the simple, singular marker [PRODUCT_DECISION_CIRCLE_OF_TRUST.md](PRODUCT_DECISION_CIRCLE_OF_TRUST.md) actually ratified — never a rank among the rest of the circle, never shown as "you have N trusted drivers" (a count), and never implying exclusivity (Rule 2: holding one relationship never precludes another). Because the component takes `isPrimary` as a plain boolean rather than, say, a numeric "rank in circle," there is no way to accidentally render a circle as an ordered leaderboard even by future misuse.

## 8. How this supports Personal Client Relationship

[PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md](PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md) Part 1 is explicit that this relationship is not ownership, not commercial, and not (per its own [REQUIRES VALIDATION] tag) a defined "service relationship" — it is a recognized, recurring fact about who has worked together before. `DriverTrustIndicator` matches this precisely: it names the relationship's other party without asserting anything about its terms, its value, or its permanence. It never renders a "since" date (Section 4) precisely because Personal Client Relationship's own ratified definition stops short of tenure being a meaningful, product-defined concept.

## 9. What must remain outside the component

- **Rating/review mechanics of any kind** — [PIOS_TAXI_DESIGN_BRIEF.md](PIOS_TAXI_DESIGN_BRIEF.md) Section 14, open question 3, leaves whether PIOS has any rating mechanism at all unresolved; this component must not pre-empt that decision by rendering a rating slot now that a future answer would just need to fill in.
- **Vehicle/plate display** — no such data exists; adding a prop for it now would invent a data-model expansion this document has no authority to make.
- **Any action affecting the relationship** (making primary, removing from circle) — those are `RideRequest.tsx`'s own existing buttons/handlers (Task 4's implementation), calling real endpoints; `DriverTrustIndicator` only ever displays, never mutates, keeping its own API small and its blast radius nil.
- **A "known driver" label for the confirmed-ride call site** — deliberately not applied there (Phase 3 below): that screen never actually loads Circle-of-Trust membership data, so labeling the driver "known"/"primary" there would be asserting a fact the code hasn't actually checked. The component's `isPrimary` prop stays `undefined` at that call site rather than being guessed.

## Component scope note

[PIOS_DESIGN_SYSTEM.md](PIOS_DESIGN_SYSTEM.md) Section 5 originally named two separate future components — `DriverIdentity` (name/photo prominence) and `DriverTrustIndicator` (relationship label only). This task's own scope explicitly limits this pass to building *one* new taxi-specific component. Given the two real call sites this task applies it to (Circle of Trust, confirmed ride) both need name-prominence *and* relationship context together, not separately, this document consolidates both roles into `DriverTrustIndicator` for now, rather than under-delivering one of the two roles or building two components in a task that authorizes only one. A future task may split `DriverIdentity` back out if a screen needs pure identity display with no relationship context at all — nothing here forecloses that.

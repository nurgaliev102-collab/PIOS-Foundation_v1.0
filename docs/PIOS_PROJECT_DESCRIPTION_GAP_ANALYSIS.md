# PIOS Taxi — Project Description vs. Current Implementation (Gap Analysis)

**Status:** Read-only comparison. No code was changed to produce this document. Nothing here is authorization to build anything — per `CLAUDE.md`'s "Documentation First" and "No Implementation Before Documentation" rules, any item marked ⬜ below needs its own design/architecture pass (and likely an ADR, since several introduce new bounded-context concepts) before a single line of implementation code is written.

**Input:** `PIOS_Taxi_Project_Description.pdf` (32 sections, submitted 2026-09-05) — a full product description authored outside this repository.

**Method:** Each claim below is grounded in a specific file this session actually opened (path cited). Where something could not be verified in the time available, it is marked "not verified" rather than assumed.

---

## 1. Headline finding

The PDF's core thesis is **not new** — it is a fuller, more structured restatement of what is already this repo's ratified vision in [`PIOS_PRODUCT_VISION.md`](PIOS_PRODUCT_VISION.md): *"On an aggregator you work for the platform; on PIOS you build your own business using the platform."* Same driver→link→client→order→ride→repeat cycle, same "not Uber, not BlaBlaCar" framing, same warning against premature scope expansion. This is confirmation of direction, not a pivot.

The PDF's **business-model section (§20–23)** is near-word-for-word the same proposal already recorded separately in [`PIOS_BUSINESS_MODEL_V1.md`](PIOS_BUSINESS_MODEL_V1.md) from the architect's parallel conversation. Those two documents should be treated as the same proposal, not two — worth reconciling into one canonical file rather than maintaining both, if the product owner ratifies it.

Where the PDF genuinely goes beyond current scope is **§7.2–§12**: scheduled/future trips as a first-class concept, intercity route-segment matching, Smart Pickup/Drop-off, and group trips. None of that exists yet, and none of it is small — each is a real new bounded-context question, not a UI addition.

---

## 2. Section-by-section status

Legend: ✅ built and live · 🟡 partially built · ⬜ not built · — not applicable / policy statement

| PDF section | Claim | Status | Evidence |
|---|---|---|---|
| §1–4 Positioning, entrepreneurial cycle | Driver builds own business, own client base via own link | ✅ | Matches [`PIOS_PRODUCT_VISION.md`](PIOS_PRODUCT_VISION.md) §3, §9 exactly; DriverHome's QR/invite link, "Мои водители" (My Drivers) repeat-order screen |
| §5 Personal network — priority in matching | "PIOS first searches the user's own network, then extended network, then open market" | 🟡 | A real mechanism exists but is narrower than described: **First Refusal** (ADR-062) — a passenger's single *designated primary driver* gets first refusal on a new order before open dispatch (`PrimaryConnectionDesignated.kt`, `PrimaryDriverRecord.kt`). This is one primary driver per passenger, not a ranked/weighted network — ADR-062 explicitly forbids "ranking, scoring, or weighting." Separately, `network-management`'s `Connection`/`Person` aggregates (Sprint 7A) model a general directed relationship graph, but its own code comment states it is **not read by Dispatch or any order-routing decision** — a deliberate, documented scope boundary, not an oversight. |
| §6 Invitations, organic network growth | Invited person becomes independent, builds their own network | 🟡 | Two separate mechanisms exist today: (a) driver's personal QR/link (DriverId-as-link, ratified as the invitation model per project memory) for driver→client; (b) `network-management`'s `Invitation`/`Connection` for person→person (Sprint 7A's "Артур → Регина" scenario). They are not unified into one invitation concept the way the PDF describes. |
| §7.1 PIOS NOW | Immediate A→B ride, priority to trusted/known drivers first | ✅ | The whole existing order→proposal→assignment→trip lifecycle (`Order.kt`, `Proposal.kt`, `Assignment.kt`, `Trip.kt`) plus First Refusal above |
| §7.2 PIOS PLANNED | Future trip with date/time/passengers/luggage, matched in advance | 🟡 | `Order` carries an optional `requestedPickupAt` (ADR-058) and DriverHome/RideRequest show a scheduled-pickup badge — but this is a field on today's single-order model, not a distinct Trip Intent entity, and there is no advance matching against a driver's future availability or published route |
| §7.3 PIOS INTERCITY | Driver publishes a route, passengers match by route segment | ⬜ | No route, route-segment, or intercity concept found anywhere in `backend/` |
| §8 Trip Intent / Trip Offer as entities | Demand and supply as first-class objects, matched independently of an immediate order | ⬜ | Not modeled. Today's only demand object is `Order`; there is no `TripOffer` equivalent published by a driver |
| §9 Regional / airport / vahta (shift-worker) trips | Recurring home↔work cycles, grouped demand for a van/multiple cars | ⬜ | Not modeled |
| §10 Route-segment matching | Match on a sub-segment of a published route | ⬜ | Depends on §7.3/§8 existing first |
| §11 Smart Pickup/Drop-off | System suggests a compromise meeting point | ⬜ | Not modeled |
| §12 Group trips | Multiple compatible intents merged into one van/multi-car dispatch | ⬜ | Not modeled |
| §13–14 Full driver onboarding + staged verification funnel | License, categories, vehicle documents, staged unlock (Account → Driver → Vehicle → Verification → Ready) | 🟡 | `driver-management`'s `Driver` aggregate is intentionally minimal — no license, document, vehicle, or verification-status fields surfaced in this module's file list. Verification/document handling was **not found**; DriverHome's onboarding (per `PIOS_DRIVER_HOME_FINAL_REVIEW.md`) covers profile + availability + QR, not document verification |
| §15–16 Two-sided reputation, Trust Layer, Relationship Strength | Ratings both directions, persistent trust signal beyond a star average | ⬜ | No `Rating`/`Review` domain type exists anywhere in `backend/` (checked directly) |
| §17 Instant vs. Request Booking | Two booking modes for intercity rides | 🟡 (partial precedent, wrong context) | The just-shipped price-confirmation feature (driver states a price, passenger confirms/declines, `ProposalStatus.PRICE_PROPOSED`) is structurally a "Request"-style negotiation step — but it applies to the existing single-ride flow, not intercity booking, which doesn't exist yet |
| §18 Trip memory → repeat business | Ride → rating → connection → network → repeat order | 🟡 | The repeat-order half exists ("Мои водители" screen re-orders a known driver); the rating/review half does not (see §15–16) |
| §19 PIOS Driver OS | Full business dashboard: clients, CRM, routes, calendar, analytics, corporate orders, payments | 🟡 | DriverHome already follows the vision's own required sequencing (Availability → current work → growth/QR) per `PIOS_PRODUCT_VISION.md` §7, and there's a separate owner/analytics surface in progress (`wip(owner-control-center): pilot analytics aggregation`, uncommitted-at-risk per its own WIP disclosure) — but no CRM, no per-driver client list, no calendar, no corporate-order handling |
| §20–23 Business model, KPIs, two-circuit economics | Free tier, Pro subscription, marketplace/lead fee, B2B, Driver-Owned Business Volume, Repeat Relationship Rate | — | Not implemented, correctly — this is a monetization/business-rule decision. Already captured verbatim in [`PIOS_BUSINESS_MODEL_V1.md`](PIOS_BUSINESS_MODEL_V1.md); needs product-owner ratification before any pricing/fee logic is invented in code, per this repo's "Never Invent Business Rules" rule |
| §24 User flows | Passenger one-off / intercity, driver new-client, invitation | 🟡 | The one-off passenger flow and driver-new-client flow exist end-to-end; the intercity flow and formal invitation flow (as PDF describes) do not |
| §25 Network flywheel + business flywheel | Two parallel growth loops | — | Descriptive/strategic; not a code artifact to check |
| §26–27 What to take from BlaBlaCar / what PIOS adds | Route publishing, segment search, smart pickup, bookings, reviews, etc. | ⬜ (as a set) | These map directly to the ⬜ items above (§7.3, §10, §11, §15–17) |
| §28 Target model (Person→Network→Intent/Offer→Matching→Trust→Trip→Relationship→Network) | Full loop | 🟡 | Person→Network exists narrowly (First Refusal); Matching→Trip→Relationship exists for the immediate-ride case; Trust and Intent/Offer don't exist yet |
| §29 Three MVP loops to prove first | (1) repeat ride, (2) intercity trip, (3) invitation→own network | 🟡🟡🟡 | Loop 1 (repeat ride): built and live. Loop 3 (invitation→own network): partially built (see §6). Loop 2 (intercity): **not built at all** — this is the biggest concrete gap between the PDF and today's system |
| §30 What must not happen | Don't become a copy of an aggregator/BlaBlaCar, don't let function count outrun proof of the core loops | — | Already the explicit governing rule in `PIOS_PRODUCT_VISION.md` §14; nothing observed in this review contradicts it |

---

## 3. What this means concretely

**Confirmed, not new:** the entrepreneurial positioning, the driver-owns-the-client principle, and the business-model shape. No decision is needed here — it's already ratified in this repo's own vision document.

**The one clear, large gap:** PIOS INTERCITY / Trip Intent / Trip Offer / route-segment matching / group trips (§7.3–§12) is a coherent cluster of new domain concepts with no code footprint today. If this is the next real milestone, it needs its own architecture pass before implementation — it likely introduces at least one new bounded context (a "Trip Planning" or "Intercity" module) and touches Dispatch's matching logic, which is exactly the kind of cross-module change `CLAUDE.md` says must go through the architect role and an ADR first, not get inferred from a PDF.

**Smaller, real gaps:** two-sided ratings/reviews and Trust Layer (§15–16), staged driver document verification (§13–14), and CRM/analytics/calendar inside Driver OS (§19). These are additive to existing modules rather than new bounded contexts, but each is still a business-rule/domain decision, not a styling change.

**Housekeeping item:** two invitation-shaped mechanisms already exist side by side (driver-QR-link, and `network-management`'s Person/Connection/Invitation) without being reconciled into one concept. Worth flagging to the architect rather than silently merging them.

---

## 4. Suggested next step

Per this repo's own MVP philosophy (`PIOS_PRODUCT_VISION.md` §14, and the PDF's own §29), the honest next question is not "build all of this" but: **which one of the three unproven loops matters most right now** — intercity matching, two-sided trust/ratings, or deeper Driver OS tooling? That's a product-owner/architect call, not one to infer from a document comparison.

*This document does not commit to building anything in §2's ⬜/🟡 rows. It exists so the next planning conversation starts from an accurate picture of what's real today.*

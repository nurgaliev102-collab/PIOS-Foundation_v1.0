# PIOS — Path to Public Launch (Taxi Vertical)

**Status:** Planning document, not an architecture decision. Authored by Claude in the executor/planner role, per the product owner's 2026-09-05 direction: product owner remains architect for anything touching domain model/bounded-context boundaries/ADRs; Claude drives planning, execution, and QA within that boundary. Nothing in this document authorizes itself — items marked **[DECISION NEEDED]** are stops, not permissions.

**Goal this document serves:** take the taxi vertical from "pilot deployed, 12/12 synthetic E2E, one real iPhone tester" to a genuine public launch — real drivers, real passengers, real repeat use — without drifting the product into a generic aggregator (`docs/PIOS_PRODUCT_VISION.md` is the standing test for every item below).

**Process note, recorded here for traceability:** the product owner confirmed 2026-09-05 that the feature-freeze declared in `docs/PIOS_PRODUCT_EVIDENCE.md` (2026-07-26, "no new features until a real Pilot Review produces evidence") is lifted — a real pilot is already live. The evidence-logging discipline itself (cite what justifies a Sprint) is not rescinded, just no longer blocking; real observations from the live iPhone pilot (including the registration bug diagnosed 2026-09-05) should still be logged as `E-NNN` entries going forward.

---

## Part A — Decisions already found and waiting on you (architect role)

These were surfaced by an existing, uncommitted, self-described DRAFT report — `docs/ARCHITECTURE_VERIFICATION_REPORT.md` — written before this document, still unreviewed. I am not re-deriving these; I'm carrying them forward since they block real launch-scope work, especially #A1, which is the exact mechanism the whole product vision hinges on.

### A1 — RESOLVED, 2026-09-05 (product owner's own detailed follow-up message)

**Decision: Option 1/3, ratified.** The DriverId-as-link mechanic (`/i/:driverId` → Driver Management lookup → Passenger Experience `Connection`) is the real, final taxi-vertical model — not a stopgap for `network-management`'s `Invitation` aggregate. The product owner's own message describes the full intended shape in detail (driver profile page, "Мои водители" repeat-client list, availability-fallback behavior) built entirely on this mechanic, with no mention of migrating onto `network-management`. `network-management`'s disposition (retire vs. repurpose for the "wider vision" business-network layer, §10-11 of the vision doc) remains open but is no longer blocking — nothing in the taxi vertical depends on it.

**Concrete gap found and closed as a direct consequence:** verified by reading the router (`frontend/src/app/routes.tsx`) that no passenger-facing route existed for a *returning* passenger with no saved driver link — `docs/PIOS_PRODUCT_VISION.md`'s own §8 scenario ("Мои водители") was physically unreachable. Backend already fully supported it (`GET /v1/connections?passengerReference=...`, ADR-054, already exercised internally by `PassengerLanding.tsx`'s own Circle-of-Trust check) — closed with one new, frontend-only screen: `frontend/src/pages/MyDrivers/` at route `/me`. No backend change.

<details>
<summary>Original open question, kept for history</summary>

### A1 (original) — Is the current invitation mechanic final, or a stopgap? **[DECISION NEEDED — highest priority]**

Two invitation systems exist in the codebase today, and only one is wired to any real screen:

- **What's actually live:** a driver's own `DriverId` used directly as the invitation code (`/i/:driverId`), resolved through Driver Management's `GET /v1/drivers/:id`, with the resulting relationship recorded as a `Connection` in **Passenger Experience**. No invitation-specific domain concept exists here at all — it's a side effect of an existing lookup endpoint.
- **What exists but is dead code from the frontend's perspective:** a full `network-management` module (`Person`, `Profile`, `Connection`, `Invitation` — `InvitationController.kt` fully implemented, `POST /v1/invitations`, `POST /v1/invitations/{code}/accept`) that no production screen ever calls. Only `NetworkTest.tsx` (explicitly an internal, non-product screen) touches it.

Since the whole product vision (`docs/PIOS_PRODUCT_VISION.md` §3, §9) is built on "a driver's own link is the mechanism of their own business" — this is not a cosmetic cleanup question. Before doing any further work on the invite/growth mechanic (QR styling, invite tracking, multi-channel invites, anything in that direction), we need your call:

- **Option 1:** Ratify the current DriverId-as-link mechanic as the real, final model. Then `network-management` (as currently scoped — `Person`/`Invitation`) is either retired or given a new, different purpose — but it stops being "the thing this will eventually plug into."
- **Option 2:** Treat the current mechanic as a placeholder, and schedule the real migration onto `network-management`'s `Invitation` aggregate as its own ADR-governed piece of work before public launch (this is real backend + frontend rewiring, not a small task).
- **Option 3 (a version of Option 1 worth naming explicitly):** keep both, on purpose — `network-management`'s richer `Person`/relationship model becomes the substrate for the "wider vision" (§10-11 of the vision doc: business network beyond taxi), while the simple DriverId-link stays the taxi-specific mechanic forever. This is coherent with the vision doc but needs to be said out loud, not left implicit.

I have a mild lean toward Option 1 or 3 given the "don't build for verticals we're not on yet" instruction (vision doc §14), but this is squarely a domain-model call, not mine to make.

</details>

### A2 — Is "Accept Assignment" (and its `AssignmentAccepted` event) supposed to fire in the real user flow?

Today, accepting a Proposal creates an `Assignment` in `CREATED` status and publishes `OrderAssigned` — but nothing in the real frontend flow ever calls `POST /v1/assignments/{id}/accept`, so `AssignmentAccepted` and its already-implemented, already-tested RabbitMQ consumer (`AssignmentAcceptedListener.kt` in Order Management) never fire in production. If Order Management is ever meant to know "this order has been assigned" (relevant the moment Order Management needs its own view of ride state for anything customer-facing — receipts, order history, etc.), this gap needs a decision: wire the real flow to call it, or formally retire the unused endpoint/consumer as intentionally-manual-only.

### A3 — `docs/PRODUCT_BASELINE_V2.md` §17 is factually wrong and should be corrected

Not a decision — a plain documentation-defect fix (permitted under ADR-006 even during any freeze, since it's correcting the doc to match code that already exists, not authorizing new implementation). I can do this as a pure doc edit once you confirm you want it in this pass.

### A4 — `MvpVerticalSliceScenarioTest.kt` naming is misleading

It verifies in-memory application-service contract boundaries only — no HTTP, no outbox, no RabbitMQ — despite living in a `verification/` package and being named like an end-to-end slice test. Rename/re-document it, and separately decide whether a *real* REST→outbox→relay→RabbitMQ→consumer test is needed before public launch (I'd argue yes, given money/rides are about to be real — see Part C).

---

## Part B — Gaps found during this session's own pilot deployment and live-bug diagnosis

These are new findings, not previously documented, surfaced while I was doing the RC deploy/E2E/security-smoke work and the iPhone registration diagnosis earlier today.

### B1 — `POST /v1/orders` has no authorization at all **[DECISION NEEDED]**

Any caller can create an order under any `passengerReference` string, unauthenticated. Documented in this codebase as an "accepted risk" at pilot scale. At real public launch — real strangers, potentially real money changing hands off-platform per the ride — this is a spam/abuse vector (fake orders sent to a real driver's phone) and needs an explicit decision: require the passenger's session token here too (mirrors the pattern already used for accept/cancel), or accept the risk permanently with a documented reason (e.g. "friction here would defeat the whole point of a shareable link" is a legitimate product argument, but it should be a decision, not an oversight).

### B2 — `POST /v1/proposals` (create) accepts any authenticated caller, not a verified relationship

Any signed-in identity — passenger or driver — can propose *any* order to *any* driver. Currently by design (Task 20/21's own named residual gap). Worth a second look at public-launch scale: does a stranger's session get to force a proposal onto a driver they have no relationship with? Given the vision's own "you build relationships with clients you invited," an unrelated third party spamming proposals at a driver is exactly the kind of abuse the entrepreneurial model should resist.

### B3 — No payment/pricing infrastructure exists at all

`ADR-017` names "Payments" as one of eight originally-planned bounded contexts — it has never been built. Today, `ADR-042` ("Stated Ride Price Minimal Model") has the driver manually state a price on acceptance, with no processing, escrow, or platform involvement in money movement at all — money changes hands entirely off-platform. **This may be entirely intentional** and even aligned with the vision (PIOS as infrastructure, not a middleman that takes a cut) — but "full public launch" is exactly the point where this needs to be said explicitly, one way or the other, since it determines what "launch" even means (a working handshake app vs. a payment-processing platform).

### B4 — Production hosting is a home PC behind Tailscale Funnel

Every pilot service today is a WinSW-managed process on one physical machine (`home-pc`), reachable publicly only via Tailscale Funnel (the custom domain, `piosapp.ru` via Cloudflare Tunnel, has been broken and separately diagnosed across 14 reports without resolution — `docs/PIOS_TAXI_TASK_31` through `_44`). This is workable for a handful of pilot testers; it is not durable infrastructure for a public launch (single point of failure, tied to one operator's own machine being on and connected, no real scaling headroom, frontend served via `vite preview` which is explicitly not a production web server per Vite's own docs). **This needs its own decision and likely its own milestone** — real hosting (a VPS/cloud provider, a real reverse proxy, a real domain) before "public" in any meaningful sense.

### B5 — Secrets management is registry-based, single-machine, undocumented rotation

`pios.session.secret` and the owner credential live in each Windows service's own SCM registry `Environment` key (previously investigated, see `docs/PIOS_SESSION_SECRET_MECHANISM_INVESTIGATION.md`). No rotation policy, no secrets manager, tied to B4's own single-machine constraint. Same underlying blocker as B4.

### B6 — Application-level logging is nearly silent on error paths

Discovered directly while diagnosing the iPhone registration failure: `IdentityController` (and the pattern repeats across other controllers) logs nothing at all on a caught `IllegalArgumentException`/`PhoneAlreadyRegisteredException` — a real user's 400/409 leaves zero trace. Before real users are hitting this at volume, add actual logging (or structured metrics) on these paths — otherwise every future "it didn't work" report from a real user is undiagnosable after the fact, exactly like the phone-format bug earlier today.

### B7 — Frontend swallows every non-409 registration failure into one generic message

Confirmed today: `handleRegisterSubmit`'s catch block treats a genuine network failure and every non-409 HTTP status identically ("Проверьте связь с интернетом..."). This actively hid the real cause of today's bug from the user. A public launch needs the frontend to at least distinguish "your input was invalid" from "something's actually down" — a small, contained fix.

### B8 — No rating/trust/dispute mechanism beyond a display badge

`DriverTrustIndicator` shows availability, not trust earned through completed rides. For real strangers transacting (not just a driver's pre-existing contacts), some minimal trust signal is likely needed before or shortly after public launch — this is a product-model question (§9 of the vision doc: PIOS provides "доверие" as one of its infrastructure pieces) more than a technical one.

### B9 — Test/synthetic data hygiene

Confirmed today: `isTest=true` exists for Driver/Order/Proposal and is excluded from pilot analytics (`6febdee`), but **Identity has no `isTest` field at all** — synthetic identities from every smoke test (including today's) accumulate permanently in the production database with no cleanup mechanism. Not urgent, but worth a real decision before volume grows: either add the field, or establish an approved application-level cleanup path.

---

## Part C — What I can start executing now, without further architecture sign-off

Everything below is either a pure documentation fix, a contained bug fix already scoped by today's diagnosis, or test/observability work that adds no new product behavior:

1. **Fix the iPhone registration bug's actual root cause (B7 + partially B6).** Distinguish validation failure from network failure in the frontend error message; likely also add basic phone-format guidance/normalization on the input itself so a real Russian user's `8...`-style number doesn't silently 400. This directly closes today's live bug.
2. **Add logging on identity's (and other modules') caught-exception paths (B6).** Contained, no behavior change, pure observability.
3. **Correct `PRODUCT_BASELINE_V2.md` §17 (A3).**
4. **Rename/re-document `MvpVerticalSliceScenarioTest.kt` (A4, first half).**
5. **Start logging real `E-NNN` entries in `PIOS_PRODUCT_EVIDENCE.md`** from the live iPhone pilot, starting with today's registration bug — this was explicitly asked for and doesn't require any decision.

I have **not** started any of these yet — flagging the plan first per Milestone-Based Execution, since even "just execution" items deserve an explicit go before I touch files, given how much surface area this document covers.

---

## Suggested sequencing, pending your answers on Part A/B decisions

1. **Milestone 0 (now):** Part C items — small, safe, immediately valuable, no architecture risk.
2. **Milestone 1:** whatever Part A/B decisions you make, turned into ADRs (by you) and implementation tasks (by me) — likely centered on A1 (invitation model) since it's the one the whole vision depends on.
3. **Milestone 2:** B4/B5 (real hosting + secrets) — almost certainly the actual bottleneck standing between "pilot" and "public," independent of any code quality question.
4. **Milestone 3:** B1/B2 (auth hardening) once traffic is no longer only people you know personally.
5. **Milestone 4:** B3/B8 (payments/trust) — biggest product-model questions, probably deserve their own dedicated product discussion before any code.

I'd like your read on Part A (especially A1) and which Part B items you want folded into "public launch" scope now vs. deferred, before I start Milestone 0. Happy to just start Milestone 0 immediately if you'd rather not block on that — it's independent of every open decision above.

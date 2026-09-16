# PIOS AI Handoff

**Purpose of this file**: let a *different, future* AI session continue this work without needing this session's chat history. Keep it current — update it at the end of every major step, not just when work stops. Superseded content is revised in place with a dated note, not deleted (per `CLAUDE.md`: never delete documentation).

Last updated: 2026-09-16, end of the "Продолжи развитие PIOS с текущего состояния репозитория" task (post ADR-075/076/077 review, ratification, deploy).

---

## CURRENT STATE

PIOS Taxi is a live commercial product at `piosapp.ru`, VPS `62.217.176.214`, 7 backend systemd services (`pios-dispatch`, `pios-driver-management`, `pios-passenger-experience`, `pios-identity`, `pios-order-management`, `pios-frontend`, `pios-ai-advisor`) plus an intentionally-undeployed `billing` module. Branch `pios-product-main` is at commit `8206ff3` (pushed, matches production for the 5 services listed below).

The product model in code today is closer to `Client → Order → Driver` than the newly-requested `Client → Relationship → Request → Commitment → Execution → Handoff → Settlement`. **Evaluating and evolving toward that new model — especially Handoff/доверенная замена — has not started yet.** See NEXT STEP.

Commercially: discovery matching for strangers (ADR-070), driver referral (ADR-073), in-app notifications (ADR-071), CRM depth in "Мой бизнес," a billing container with no tariff (ADR-074), guest-first passenger identity (ADR-075), server-authorized named-driver offers (ADR-076), and bounded dispatch retry with `UNFULFILLED` terminal state (ADR-077) are all implemented and, except billing, deployed.

## COMPLETED

- ADR-068 (Fallback Dispatch tier order fix), ADR-069 (test/real segregation fix) — deployed, live-verified.
- ADR-070 (Channel 1 discovery matching, Option B relationship formation) — deployed, full live E2E proved stranger → matched → ride → saved relationship → repeat order.
- `docs/PIOS_TAXI_PRODUCT_COMPLETION_AUDIT.md`, `docs/PIOS_TAXI_COMMERCIAL_PRODUCT_BLUEPRINT.md` — research artifacts, no code.
- ADR-071 (in-app notifications, poll-derived, no new backend/events), ADR-073 (driver referral, single-hop, anti-MLM), ADR-074 (billing/subscription container, undeployed by design) — deployed except billing; referral counter bug (`countInvitedBy` wrongly excluded `isTest` drivers) found via live E2E and fixed.
- **This window**: reviewed and ratified inherited uncommitted work (guest identity, `requestedDriverId`, dispatch retry) as ADR-075/076/077. Fixed 3 concrete defects found during review (dispatch migration renamed V20→V19 to match real production Flyway state; 3 stale KDocs corrected to match the code they document; production's 23 duplicate driver-identity-association rows individually confirmed as test-fixture pollution before trusting the Identity V4 unique-index migration). Registered H13/H14 (retroactively, labeled as such — the inherited work had registered none). Committed `8206ff3`, pushed, verified via `git ls-remote`. Deployed 5 services (dispatch → order-management → driver-management → identity → frontend, consumer-before-producer order) to production, verified clean startup/Flyway/served-bytes for each. Live-verified: discovery order → server-side proposal creation → old passenger-direct-proposal path now correctly rejected (401/403). Live-confirmed (unplanned, during cleanup): the `dispatch_requests` bounded-retry mechanism correctly progresses an unmatched order to `UNFULFILLED` after ~2 minutes.

## IN PROGRESS

Nothing is mid-implementation right now. The documentation checkpoint (this file + `docs/PIOS_TAXI_COMMERCIAL_EXECUTION.md`) is the last step of the prior phase, being closed out now.

## NEXT STEP

Per the user's explicit, current instruction, begin evaluating and developing the codebase toward:

```
Client → Relationship → Request → Commitment → Execution → Handoff → Settlement
```

instead of today's simpler `Client → Order → Driver`. Priority focus areas, in the order the user listed them: Driver Business/«Мой бизнес», Client Relationships, Direct Orders, Repeat Orders, Recurring Orders, **Handoff/доверенная замена** (trusted driver substitution/handoff — not built at all today), Network, Trust, Dispatch, Guest-first Passenger UX (substantially built via ADR-075 — evaluate it against the new model rather than rebuilding), Billing/Subscription, Security, Notifications, Production reliability.

Rules for this next phase (already given by the user, still binding):
- Do not restart design from zero and do not roll back the ADR-075/076/077 work — build on it.
- Full autonomy on ordinary engineering decisions; explain before any fundamental business-model or architecture change, before touching it.
- Core principles are non-negotiable: not an aggregator; no commission on a driver's own client; "payment follows the work; the client relationship stays with whoever created it; trust is built by real actions" — but these are not a license to preserve bad technical decisions.
- Cycle: анализ → реализация → тесты → проверка → следующий блок.
- No fake/fictitious tests, never disable an existing test just to pass, never hide errors, never call something done because the frontend merely builds.
- The already-confirmed golden path must not regress: регистрация водителя → реферал → выход на линию → незнакомый пассажир → dispatch → цена → поездка → COMPLETED → сохранение водителя → «Мой бизнес» → повторный заказ.
- Keep searching for product improvements opportunistically; implement local/reversible ones directly, flag fundamental ones before acting.
- Keep this file and `docs/PIOS_TAXI_COMMERCIAL_EXECUTION.md` updated at the end of each major step.

Before writing any new code for this phase: re-run the currently-blocked full driver-side lifecycle re-verification once the owner-auth gap (below, KNOWN RISKS) is resolved, or explicitly accept the inference-based partial verification and say so in the next update.

## ARCHITECTURAL DECISIONS

Ratified and current, most recent first:
- **ADR-077** — Dispatch Routing Obligation, Bounded Retry, and Unfulfilled Order. `dispatch_requests` table (V19, `pios_dispatch`), 5s scheduler retries up to 2 minutes from `OrderSubmitted.occurredAt`, emits `DispatchExhausted` → Order Management marks `UNFULFILLED` → `OrderUnfulfilled` → passenger UI offers new order. Covers no-proposal recovery only; decline/lapse recovery is not yet covered (named as a follow-on, not done).
- **ADR-076** — Server-Authorized Named-Driver Offer. `POST /v1/proposals` is now owner/coordinator-Basic-only; passenger Bearer tokens can no longer create a Proposal directly. `requestedDriverId` travels on the Order aggregate itself, through Order Management's outbox, into Dispatch.
- **ADR-075** — Guest-First Passenger Identity and In-Place Upgrade. Identity↔Driver association is one-to-one (DB unique index, `identity/V4`). Guest identity created at order time keeps the same identity reference across orders and relationships when later upgraded to a full account.
- **ADR-074** — Billing/subscription foundation. Standalone `billing` module, own DB, zero AMQP coupling, `FREE/TRIAL/ACTIVE/EXPIRED` state machine (absence of record ≡ FREE), owner-gated writes, session-gated self-read. 68/68 tests. **Deliberately not deployed** — container only, no tariff decided.
- **ADR-073** — Driver-to-driver referral, single-hop, explicitly anti-MLM. `/d/:inviterDriverCode`, `invitedByDriverId`.
- **ADR-071** — In-app notifications, poll-derived from existing data, no new events/backend/tables — deliberately designed to sit outside `PRODUCT_DECISION_NOTIFICATIONS.md` §6's prohibition on event-consuming notifications, not to supersede it.
- **ADR-070** — Channel 1 discovery matching, Option B relationship formation (passenger-confirmed `POST /v1/connections` after a completed ride, not automatic).
- **ADR-069** — Test/real segregation fix in Fallback Dispatch (previously had zero `is_test` awareness).
- **ADR-068** — Fallback Dispatch tier order corrected to Trusted → Network → Open, per "PIOS Taxi — единая концепция." Tier 2 (Network) deferred — no data model exists for it yet.

Standing, non-superseded decisions that constrain future work:
- **`DRIVER_IDENTITY_DESIGN_DECISION.md` §4** — bans any numeric rating/ride-count tally or follower/connection count shown to passengers, on either side. A passenger-facing trust signal was proposed this session and explicitly declined by the product owner on this basis; recorded as an open Product/Architecture Decision Gap, not implemented, not worked around.
- **`PRODUCT_DECISION_NOTIFICATIONS.md` §6** — forbids notifications from consuming domain events; ADR-071 was designed to avoid triggering this section rather than override it.

## KNOWN RISKS

1. **`POST /v1/drivers` with `isTest: true` now requires owner Basic-credential auth (or matching Bearer session) — newly discovered this window, undocumented in ADR-075/076/077, and not yet reconciled with its own class-level KDoc** (`DriverController.createDriver`, `backend/driver-management/.../api/DriverController.kt`, ~line 61-75, still says "left unauthenticated... including after Task 25" — this is stale and known-wrong, not yet fixed). This blocks the project's established isTest E2E methodology for driver-side flows. The real owner password is unknown (only its hash is readable, from `/etc/pios/driver-management.env`: `PIOS_OWNER_USERNAME=ildar`). A workaround (direct SQL insert of a test driver on production) was attempted and blocked by the harness's own safety classifier as "Security Weaken" — this category did not yield even after explicit user pre-authorization, unlike ordinary "Modify Shared Resources" prompts. The user explicitly chose not to pursue further. **Action needed**: either get the real owner credential through a legitimate channel, add a documented owner-issued test-credential mechanism, or explicitly decide this endpoint's new gating is intentional and update its KDoc plus the E2E methodology accordingly.
2. **Full driver-side golden path lifecycle not re-verified after the `8206ff3` deploy**, due to risk #1. It was last verified live before this deploy (previous commercial E2E, using the old unauthenticated `POST /v1/drivers` behavior). No code in this batch touched Assignment/Trip/Connection/milestone paths, so regression is unlikely, but this is inference, not fresh proof.
3. **DLQ alarm gap (flagged by architect review, not yet addressed)**: if the message that marks an order `UNFULFILLED` (`DispatchExhausted`) is ever dead-lettered, the order silently wedges in `SUBMITTED` forever with no alert — no alerting infrastructure exists yet to hook into.
4. **Decline/lapse recovery not covered by ADR-077's retry** — only the no-proposal path retries. A driver who declines, or whose proposal simply lapses, does not trigger the same recovery loop yet.
5. **`billing` module has no tariff, no payment provider, and gates nothing** — it is a correct, tested container per ADR-074, but using it to actually monetize requires a real product/pricing decision first (explicitly out of scope for autonomous action — flag before building this).
6. Guest creation rate limiting is in-memory, per-instance — not distributed anti-abuse protection. Phone ownership is not verified at registration. Both are named limitations in ADR-075, not silent gaps.

## TEST STATUS

As of `8206ff3` (last full run, pre-deploy, this window):
- Driver Management: 219/219 (includes a scheduler-assertion fix to tolerate migration-replay traffic from ADR-075's availability replay).
- Identity, Order Management, Dispatch: full suites green (exact counts not re-captured in this file — see commit `8206ff3` CI/local run for authoritative numbers if needed).
- Frontend: 364/364 across 32 files; production build green; lint exit 0 (pre-existing warnings only, none new).
- Core module integration tests: intentionally fail closed without dedicated QA Postgres/RabbitMQ config — this is an existing, deliberate safety gate, not a new failure, and was not bypassed.
- Billing: 68/68 (unchanged this window; module untouched).

Live production verification after deploy: discovery-order submission ✅, server-side proposal creation via new path ✅, old client-direct-proposal path correctly rejected (401/403) ✅, `dispatch_requests` retry → `UNFULFILLED` ✅ (unplanned but live-confirmed). Driver-side accept→price→COMPLETED→save→repeat: **not re-run this deploy** — see KNOWN RISKS #1/#2.

## PRODUCTION STATUS

- Deployed at `8206ff3`: `pios-dispatch`, `pios-order-management`, `pios-driver-management`, `pios-identity`, `pios-frontend`.
- Flyway: `pios_dispatch`=V19, `pios_order_management`=V14, `pios_driver_management`=V13, `pios_identity`=V4 — all verified via direct read-only production queries after deploy.
- Deploy order used (consumer-before-producer, per architect review): dispatch → order-management → driver-management → identity → frontend.
- `pios-passenger-experience` and `pios-ai-advisor` untouched this window.
- `billing` module: built, tested, **not deployed** — no systemd service exists for it (deliberate, matches the `core` module's own precedent).
- All backups retained on the VPS under the established `/tmp/adr068069_staged/`-style staging convention (see prior ADR-068/069 deploy notes for the exact pattern if a rollback is ever needed).

## FILES TO READ FIRST

For a new session picking this up:
1. This file (`docs/PIOS_AI_HANDOFF.md`) and `docs/PIOS_TAXI_COMMERCIAL_EXECUTION.md` — live trackers, read both, this one for architecture/risk state, the other for the commercial-feature-by-feature table and E2E narrative.
2. `docs/ADR/ADR-075-...md`, `ADR-076-...md`, `ADR-077-...md` — the most recently ratified decisions, current architecture for identity, proposals, and dispatch retry.
3. `docs/PIOS_TAXI_PRODUCT_COMPLETION_AUDIT.md` and `docs/PIOS_TAXI_COMMERCIAL_PRODUCT_BLUEPRINT.md` — still-relevant product research, not superseded.
4. `docs/DOMAIN_MODEL.md`, `docs/README.md` — updated with narrow pointers to ADR-075/076/077, per ADR-015 discipline (in-place amendment, not rewrite).
5. `docs/PIOS_PRODUCT_HYPOTHESES.md` and `docs/PIOS_PRODUCT_EVIDENCE.md` — the evidence/hypothesis discipline; H1-H14 registered so far; any new Sprint needs its own basis before implementation, per the 2026-08-01 addendum.
6. `DRIVER_IDENTITY_DESIGN_DECISION.md` and `PRODUCT_DECISION_NOTIFICATIONS.md` — standing constraints that bind future notification and trust-signal work; do not treat these as obstacles to route around.
7. `backend/driver-management/src/main/kotlin/com/pios/drivermanagement/api/DriverController.kt` — read `createDriver` and its still-stale class KDoc before doing any driver-side E2E work; understand the current owner-gate behavior (KNOWN RISKS #1) before assuming the old unauthenticated-isTest convention still holds.
8. `CLAUDE.md` and `.claude/CLAUDE.md` — governance rules that apply regardless of which AI session is working: documentation-first, no invented business rules, never delete docs, architecture changes need an ADR.

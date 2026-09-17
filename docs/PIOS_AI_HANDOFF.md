# PIOS AI Handoff

**Purpose of this file**: let a *different, future* AI session continue this work without needing this session's chat history. Keep it current — update it at the end of every major step, not just when work stops. Superseded content is revised in place with a dated note, not deleted (per `CLAUDE.md`: never delete documentation).

Last updated: 2026-09-17, after ADR-078 (dispatch decline/lapse recovery) ratification, implementation, and deploy, and the relationship-model evaluation (`docs/PIOS_TAXI_RELATIONSHIP_MODEL_EVALUATION.md`).

---

## CURRENT STATE

PIOS Taxi is a live commercial product at `piosapp.ru`, VPS `62.217.176.214`, 7 backend systemd services (`pios-dispatch`, `pios-driver-management`, `pios-passenger-experience`, `pios-identity`, `pios-order-management`, `pios-frontend`, `pios-ai-advisor`) plus an intentionally-undeployed `billing` module. Branch `pios-product-main` is at commit `429441f` (pushed). Production `pios-dispatch` is at `429441f`; the other four previously-updated services (`order-management`, `driver-management`, `identity`, `frontend`) are still at `8206ff3`, which is also current for them — no changes to those modules since.

The product model in code is still closer to `Client → Order → Driver` than the target `Client → Relationship → Request → Commitment → Execution → Handoff → Settlement`. A full architect-level evaluation of the gap now exists (`docs/PIOS_TAXI_RELATIONSHIP_MODEL_EVALUATION.md`) — see NEXT STEP for what it found and what's still undecided.

Commercially: discovery matching for strangers (ADR-070), driver referral (ADR-073), in-app notifications (ADR-071), CRM depth in "Мой бизнес," a billing container with no tariff (ADR-074), guest-first passenger identity (ADR-075), server-authorized named-driver offers (ADR-076), bounded dispatch retry with `UNFULFILLED` terminal state (ADR-077), and now decline/lapse recovery (ADR-078) are all implemented and, except billing, deployed.

## COMPLETED

- ADR-068 (Fallback Dispatch tier order fix), ADR-069 (test/real segregation fix) — deployed, live-verified.
- ADR-070 (Channel 1 discovery matching, Option B relationship formation) — deployed, full live E2E proved stranger → matched → ride → saved relationship → repeat order.
- `docs/PIOS_TAXI_PRODUCT_COMPLETION_AUDIT.md`, `docs/PIOS_TAXI_COMMERCIAL_PRODUCT_BLUEPRINT.md` — research artifacts, no code.
- ADR-071 (in-app notifications, poll-derived, no new backend/events), ADR-073 (driver referral, single-hop, anti-MLM), ADR-074 (billing/subscription container, undeployed by design) — deployed except billing; referral counter bug (`countInvitedBy` wrongly excluded `isTest` drivers) found via live E2E and fixed.
- ADR-075/076/077 (guest identity, `requestedDriverId`, dispatch retry/`UNFULFILLED`) — reviewed, ratified, three real defects found and fixed (migration V20→V19 rename, 3 stale KDocs, 23 duplicate-identity rows confirmed test-fixture-only), H13/H14 registered retroactively and labeled as such, committed `8206ff3`, deployed 5 services, partially live-verified (full detail in `docs/PIOS_TAXI_COMMERCIAL_EXECUTION.md`'s "landed, verified, deployed" section).
- `docs/PIOS_TAXI_RELATIONSHIP_MODEL_EVALUATION.md` — architect-role evaluation of the `Client → Relationship → Request → Commitment → Execution → Handoff → Settlement` model against actual code, file:line cited throughout. Key finding: Handoff/доверенная замена is fully designed on paper (concrete aggregate, events, consent flow) but is currently **prohibited** by `ADR-054`'s ratified delegation-mechanism ban and reserved to the Product Owner by `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 8 — not built, not authorized to build yet.
- **ADR-078 — Dispatch Recovery After Proposal Decline or Lapse.** Ratified by the Product Owner 2026-09-16 (both Decision A — reopen the routing obligation on decline/lapse so a refused order reaches `UNFULFILLED` instead of hanging forever — and Decision B — offer the next eligible driver, excluding the decliner, within the original window, reversing `ADR-068` Q3's 2026-09-15 "no cascading retry" answer). Implemented in `dispatch` only: `DispatchRequestRepository.reopenIfOffered`, `ProposalApplicationService.declineProposal`/`lapseProposal` now reopen the row inside their own transaction, `DispatchRequestApplicationService.attemptOffer` derives an `excludeDrivers` set from `DECLINED`/`LAPSED` proposals and applies it to the named-driver (skip, never substitute), First Refusal, and Fallback branches. 15 new tests, full `:dispatch:test` suite verified 636/636 green. Committed `429441f`, pushed, deployed to production (dispatch only).
- Fixed a stale KDoc: `DriverController.createDriver`'s class doc claimed the endpoint was "left unauthenticated... including after Task 25," which `8206ff3`'s owner-gate made false. Corrected to describe the actual owner-Basic-or-self-Bearer requirement and the isTest-is-owner-only rule.

## IN PROGRESS

Nothing is mid-implementation. ADR-078 is deployed and verified; documentation is being closed out now (this file + `docs/PIOS_TAXI_COMMERCIAL_EXECUTION.md`).

## NEXT STEP

The relationship-model evaluation (`docs/PIOS_TAXI_RELATIONSHIP_MODEL_EVALUATION.md`) ranked the remaining work. In order:

1. **Server-side relationship depth** — move the "Мой бизнес" per-client ride count/last-ride/repeat-flag computation from the browser (`DriverHome.tsx`'s `clientRideStatsByReference`) into a server-owned read model in `driver-management`. The **read-model half is safe to build autonomously** (a derived projection over events already consumed, no new invariant — precedent: `ADR-065`'s own "derived read model" framing). The **lifecycle half** (giving `Connection` real states beyond create/designate/remove) **requires Product Owner sign-off** — `ADR-054:132` ratified that absence deliberately. Not started.
2. **Handoff / доверенная замена** — full concrete design exists (`PIOS_TAXI_RELATIONSHIP_MODEL_EVALUATION.md` Part 4): a new Dispatch-owned `Handoff` aggregate, `Trip.executingDriver` gaining a field while `Assignment.driver` stays the record of who committed, three-way consent (originating driver names a substitute → substitute accepts → passenger consents), the three ride-progress endpoints' authorization check (`assignment.driver.driverId`) needing to move to the trip's executing driver. **Blocked**: `ADR-054` bans delegation mechanisms outright and the underlying question is formally reserved to the Product Owner (`PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 8). Ten open business-rule questions are named (OQ-1 through OQ-10 in the evaluation doc — who gets credit for a handed-off ride, is a substitute's naming itself a trust claim, does any commission attach, can it chain, etc.). **Do not start any code or ADR here without the Product Owner answering these.**
3. **Settlement evidence v1** — missing entirely (no payment/cash/settlement concept anywhere; the only money fact is a driver-typed free-text string). Gated on undecided business rules (amounts, who owes whom, what "settled" means) — `CLAUDE.md`'s "Never Invent Business Rules" applies directly. Not started, not to be started without Product Owner input.
4. **Recurring/scheduled commitments** — named as blocking commercial V1 in the commercial tracker, but needs a driver-calendar concept and conflict invariant that doesn't exist yet. Needs its own ADR and Product Owner sign-off (introduces a real new invariant: "a driver cannot hold two conflicting commitments").
5. **Fallback Tier 2 (Network)** — no code needed right now; the right action is putting `ADR-068`'s Q2 back to the Product Owner, noting `ADR-073`'s `invitedByDriverId` makes candidate N-b cheaper than when Q2 was first asked.

Standing rules for all of the above (unchanged from the user's own instruction): full autonomy on ordinary engineering; explain before any fundamental business-model/architecture change; cycle анализ → реализация → тесты → проверка → следующий блок; no fake tests, no disabling existing tests, no hiding errors; the golden path (регистрация → реферал → выход на линию → незнакомый пассажир → dispatch → цена → поездка → COMPLETED → сохранение → «Мой бизнес» → повторный заказ) must not regress; update this file and the commercial tracker after each major step.

Before further driver-side E2E work: the owner-auth gap on `isTest` driver creation (KNOWN RISKS #1) is still open and still blocks full golden-path re-verification.

## ARCHITECTURAL DECISIONS

Ratified and current, most recent first:
- **ADR-078** — Dispatch Recovery After Proposal Decline or Lapse. Decision A (reopen `dispatch_requests` `OFFERED`→`PENDING` on decline/lapse, window does not restart) and Decision B (resumed attempt excludes every driver who already refused, applied to all three offer branches; a named driver who refused is never substituted) both ratified 2026-09-16. Q-2 answered "leave configuration as-is" — the 5-minute lapse timeout exceeds the 2-minute routing window, so in practice only an explicit decline (not a lapse) can produce a second offer; a lapse always reaches `UNFULFILLED` directly. Partially supersedes `ADR-068` Part 4's "no retry on decline/lapse" and reverses `ADR-068`'s own Q3 ratification — both amendment pointers added in place, dated, per `ADR-015`; original text preserved, not deleted.
- **ADR-077** — Dispatch Routing Obligation, Bounded Retry, and Unfulfilled Order. `dispatch_requests` table (V19, `pios_dispatch`), 5s scheduler retries up to 2 minutes from `OrderSubmitted.occurredAt`, emits `DispatchExhausted` → Order Management marks `UNFULFILLED` → `OrderUnfulfilled` → passenger UI offers new order. Its no-proposal-only scope gap is now closed by ADR-078.
- **ADR-076** — Server-Authorized Named-Driver Offer. `POST /v1/proposals` is now owner/coordinator-Basic-only; passenger Bearer tokens can no longer create a Proposal directly. `requestedDriverId` travels on the Order aggregate itself, through Order Management's outbox, into Dispatch.
- **ADR-075** — Guest-First Passenger Identity and In-Place Upgrade. Identity↔Driver association is one-to-one (DB unique index, `identity/V4`). Guest identity created at order time keeps the same identity reference across orders and relationships when later upgraded to a full account.
- **ADR-074** — Billing/subscription foundation. Standalone `billing` module, own DB, zero AMQP coupling, `FREE/TRIAL/ACTIVE/EXPIRED` state machine (absence of record ≡ FREE), owner-gated writes, session-gated self-read. 68/68 tests. **Deliberately not deployed** — container only, no tariff decided.
- **ADR-073** — Driver-to-driver referral, single-hop, explicitly anti-MLM. `/d/:inviterDriverCode`, `invitedByDriverId`.
- **ADR-071** — In-app notifications, poll-derived from existing data, no new events/backend/tables — deliberately designed to sit outside `PRODUCT_DECISION_NOTIFICATIONS.md` §6's prohibition on event-consuming notifications, not to supersede it.
- **ADR-070** — Channel 1 discovery matching, Option B relationship formation (passenger-confirmed `POST /v1/connections` after a completed ride, not automatic).
- **ADR-069** — Test/real segregation fix in Fallback Dispatch (previously had zero `is_test` awareness).
- **ADR-068** — Fallback Dispatch tier order corrected to Trusted → Network → Open, per "PIOS Taxi — единая концепция." Tier 2 (Network) deferred — no data model exists for it yet. Q3 ("no cascading retry") partially reversed 2026-09-16 by ADR-078 — see above.

Standing, non-superseded decisions that constrain future work:
- **`ADR-054` Part 5** — bans "Team, Fleet, crew, or delegation mechanism of any kind." Directly blocks Handoff/доверенная замена (NEXT STEP #2) until the Product Owner narrows it.
- **`DRIVER_IDENTITY_DESIGN_DECISION.md` §4** — bans any numeric rating/ride-count tally or follower/connection count shown to passengers, on either side. A passenger-facing trust signal was proposed and declined on this basis; recorded as an open Product/Architecture Decision Gap, not implemented, not worked around.
- **`PRODUCT_DECISION_NOTIFICATIONS.md` §6** — forbids notifications from consuming domain events; ADR-071 was designed to avoid triggering this section rather than override it.
- **`PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 8** — reserves delegation (Handoff) and several other open questions explicitly to the Product Owner.

## KNOWN RISKS

1. **`POST /v1/drivers` with `isTest: true` requires owner Basic-credential auth (or matching Bearer session) — undocumented in ADR-075/076/077 when first discovered, now correctly documented in `DriverController.createDriver`'s KDoc, but still functionally blocking.** This blocks the project's established isTest E2E methodology for driver-side flows. The real owner password is unknown (only its hash is readable, from `/etc/pios/driver-management.env`: `PIOS_OWNER_USERNAME=ildar`). A workaround (direct SQL insert of a test driver on production) was attempted and blocked by the harness's own safety classifier as "Security Weaken" — did not yield even after explicit user pre-authorization. Not pursued further at the user's choice. **Action needed**: get the real owner credential through a legitimate channel, add a documented owner-issued test-credential mechanism, or explicitly accept this as the new normal and adjust the E2E methodology.
2. **Full driver-side golden path lifecycle not re-verified since before the `8206ff3` deploy** (2026-09-16), due to risk #1. No code since then has touched Assignment/Trip/Connection/milestone paths, so regression is unlikely, but this is inference, not fresh proof.
3. **DLQ alarm gap**: if the message that marks an order `UNFULFILLED` (`DispatchExhausted`) is ever dead-lettered, the order silently wedges forever with no alert — no alerting infrastructure exists yet to hook into. ADR-078 *increases* traffic on this queue (more orders now reach a terminal state via this path), making the gap more load-bearing, not less. Still not addressed.
4. ~~Decline/lapse recovery not covered by ADR-077's retry~~ — **resolved by ADR-078** (2026-09-16, deployed). Lapse recovery remains functionally inert (Q-2, above) — only a decline can produce a second offer in practice.
5. **`billing` module has no tariff, no payment provider, and gates nothing** — correct, tested container per ADR-074, but using it to monetize requires a real product/pricing decision first (flag before building, don't just build it).
6. Guest creation rate limiting is in-memory, per-instance — not distributed anti-abuse protection. Phone ownership is not verified at registration. Both are named limitations in ADR-075, not silent gaps.
7. **New, this deploy — a self-caused local/production Flyway checksum drift, now resolved, recorded so the pattern is recognized faster next time.** Editing a migration file's *comment* after it has already been applied changes Flyway's checksum for that version even though the DDL is byte-identical. This happened twice: once locally (caught and fixed before committing) and once in production (caught only after the `429441f` deploy put pios-dispatch into a crash-loop — `FlywayValidateException: Migration checksum mismatch for migration version 19`, `Applied: 729037625` vs `Resolved locally: 2104142471`). Fixed both times the same way: a single `UPDATE flyway_schema_history SET checksum = <new value> WHERE version = '<n>'` (the value Flyway itself reports as "Resolved locally" — this is exactly what `flyway repair` would do for an already-successful row, verified read-only via the full history, the exact file diff, and two independent Flyway runs agreeing on the checksum, before writing anything). **Lesson for next time: never edit an already-deployed migration file's content, including comments, after it has been applied anywhere (local test DB or production) — rename/renumber before first apply, or leave it alone after.**

## TEST STATUS

- **Dispatch, `429441f`** (fresh run, this deploy): 636/636 passing, `BUILD SUCCESSFUL`. Includes 15 new ADR-078 tests (13 in-memory unit, 2 Postgres-backed transactional). One transient failure seen in an intermediate run (`ProposalLapseSchedulerTest`, a real-wall-clock scheduler test racing a 6-second window under full-suite load) reproduced as passing both in isolation and on a subsequent full clean run — confirmed a pre-existing timing flake, not a regression.
- Driver Management, Identity, Order Management (as of `8206ff3`, unchanged since): full suites green — see `8206ff3`'s own commit message for exact counts.
- Frontend (as of `8206ff3`): 364/364 across 32 files; production build green; lint exit 0.
- Core module integration tests: intentionally fail closed without dedicated QA Postgres/RabbitMQ config — existing, deliberate gate, not bypassed.
- Billing: 68/68 (unchanged, module untouched, undeployed).

Live production verification: discovery-order submission ✅, server-side proposal creation via the new path ✅, old client-direct-proposal path correctly rejected (401/403) ✅, `dispatch_requests` retry → `UNFULFILLED` ✅ (live-confirmed pre-ADR-078). ADR-078's decline/lapse recovery itself has **not yet been live-E2E-verified** (blocked by KNOWN RISKS #1 — can't easily create a second isTest driver to decline against on production right now); its correctness rests on the 636/636 test suite plus the clean, error-free production restart, not yet a live decline scenario. Driver-side accept→price→COMPLETED→save→repeat: **not re-run since `8206ff3`** — see KNOWN RISKS #1/#2.

## PRODUCTION STATUS

- **`pios-dispatch` deployed at `429441f`** (2026-09-17). Deploy required an unplanned production fix: the new jar's migration file comment edit caused a Flyway checksum mismatch against the already-applied V19 row, crash-looping the service; fixed via a single verified `UPDATE flyway_schema_history` (see KNOWN RISKS #7 for full detail and the read-only evidence gathered before the write). Clean restart confirmed (`Started DispatchApplicationKt in 3.813 seconds`, no errors), SHA256 of the running jar matches the build, all 6 other services confirmed undisturbed (`ActiveEnterTimestamp`/`MainPID` unchanged).
- `pios-order-management`, `pios-driver-management`, `pios-identity`, `pios-frontend`: still at `8206ff3`, current, no changes since.
- `pios-passenger-experience`, `pios-ai-advisor`: untouched, unrelated to any recent work.
- Flyway: `pios_dispatch`=V19 (checksum now `2104142471`, matching the current file), `pios_order_management`=V14, `pios_driver_management`=V13, `pios_identity`=V4.
- `billing` module: built, tested, **not deployed** — no systemd service exists for it (deliberate).
- Backups retained on the VPS: `/tmp/adr078_staged/app.jar.backup-20260917042627` and `app.jar.replaced` (the pre-ADR-078 dispatch jar, for rollback if ever needed), alongside the earlier `/tmp/adr068069_staged/` convention.

## FILES TO READ FIRST

For a new session picking this up:
1. This file (`docs/PIOS_AI_HANDOFF.md`) and `docs/PIOS_TAXI_COMMERCIAL_EXECUTION.md` — live trackers; read both.
2. `docs/PIOS_TAXI_RELATIONSHIP_MODEL_EVALUATION.md` — the current gap analysis against the target conceptual model and the prioritized, sign-off-gated plan; read before starting any of NEXT STEP.
3. `docs/ADR/ADR-078-...md` — most recent ratified decision (dispatch decline/lapse recovery); `ADR-075/076/077` — guest identity, named-driver offers, dispatch retry.
4. `docs/PIOS_TAXI_PRODUCT_COMPLETION_AUDIT.md` and `docs/PIOS_TAXI_COMMERCIAL_PRODUCT_BLUEPRINT.md` — still-relevant product research.
5. `docs/DOMAIN_MODEL.md`, `docs/README.md` — updated with narrow pointers to ADR-075/076/077/078, per ADR-015 discipline.
6. `docs/PIOS_PRODUCT_HYPOTHESES.md` and `docs/PIOS_PRODUCT_EVIDENCE.md` — the evidence/hypothesis discipline; H1-H14 registered so far.
7. `ADR-054` Part 5, `DRIVER_IDENTITY_DESIGN_DECISION.md`, `PRODUCT_DECISION_NOTIFICATIONS.md`, `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` — standing constraints that bind Handoff, trust-signal, and notification work; do not treat these as obstacles to route around.
8. `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/DispatchRequestApplicationService.kt`, `ProposalApplicationService.kt`, `DispatchRequestRepository.kt` — the ADR-078 implementation, if extending decline/lapse recovery further.
9. `backend/driver-management/src/main/kotlin/com/pios/drivermanagement/api/DriverController.kt` — `createDriver`'s current owner-gate behavior (KNOWN RISKS #1) before assuming the old unauthenticated-isTest convention still holds.
10. `CLAUDE.md` and `.claude/CLAUDE.md` — governance rules that apply regardless of which AI session is working: documentation-first, no invented business rules, never delete docs, architecture changes need an ADR.

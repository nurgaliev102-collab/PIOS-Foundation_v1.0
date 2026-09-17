# PIOS AI Handoff

**Purpose of this file**: let a *different, future* AI session continue this work without needing this session's chat history. Keep it current — update it at the end of every major step, not just when work stops. Superseded content is revised in place with a dated note, not deleted (per `CLAUDE.md`: never delete documentation).

Last updated: 2026-09-17, after ADR-079 (test-data credential, closing the owner-auth E2E gap), the server-side relationship-depth read model, and a live-discovered-and-fixed production defect (`OrderSubmittedListener` rejecting v2/v3 events since `8206ff3`).

---

## CURRENT STATE

PIOS Taxi is a live commercial product at `piosapp.ru`, VPS `62.217.176.214`, 7 backend systemd services (`pios-dispatch`, `pios-driver-management`, `pios-passenger-experience`, `pios-identity`, `pios-order-management`, `pios-frontend`, `pios-ai-advisor`) plus an intentionally-undeployed `billing` module. Branch `pios-product-main` is at commit `4c0a818` (pushed). Production: `pios-dispatch` at `429441f`, `pios-driver-management` at `4c0a818`, `pios-frontend` at `82c9ec3`'s frontend build; `pios-order-management`/`pios-identity` still at `8206ff3`, current, no changes since. `pios-passenger-experience`/`pios-ai-advisor` untouched, unrelated to recent work.

The owner-auth gap that blocked live driver-side E2E verification (KNOWN RISKS #1 in the prior version of this file) is **closed** — ADR-079's `PiosTest` credential is implemented, deployed, and configured in production. Using it, this session ran the first full live production E2E since the `8206ff3` deploy, and it both **confirmed ADR-078 works live** and **found a real, previously-undetected production defect** (see COMPLETED) that had been silently breaking the repeat-client milestone and the relationship-depth feature since 2026-09-16 — now fixed and deployed.

The product model in code is still closer to `Client → Order → Driver` than the target `Client → Relationship → Request → Commitment → Execution → Handoff → Settlement`, though the Relationship stage's read-model gap is now closed (see COMPLETED). Handoff/доверенная замена remains fully designed on paper but not built — blocked on Product Owner answers, now collected in a dedicated document (`docs/PIOS_TAXI_HANDOFF_OPEN_QUESTIONS.md`).

## COMPLETED

- ADR-068 (Fallback Dispatch tier order fix), ADR-069 (test/real segregation fix), ADR-070 (Channel 1 discovery matching) — deployed, live-verified in earlier sessions.
- ADR-071 (in-app notifications), ADR-073 (driver referral), ADR-074 (billing container, undeployed by design) — deployed except billing.
- ADR-075/076/077 (guest identity, `requestedDriverId`, dispatch retry/`UNFULFILLED`) — ratified, deployed `8206ff3`.
- `docs/PIOS_TAXI_RELATIONSHIP_MODEL_EVALUATION.md` — architect evaluation of the target relationship model against real code. Handoff found fully designed but blocked by `ADR-054`; server-side relationship depth (read-model half) found safe to build autonomously.
- **ADR-078 — Dispatch Recovery After Proposal Decline or Lapse.** Ratified (both Decision A — reopen the routing obligation on decline/lapse — and Decision B — offer the next eligible driver, excluding the decliner, reversing `ADR-068` Q3), implemented in `dispatch` only, deployed `429441f`. **Now live-E2E-confirmed** (see below) — a real driver declined a real production proposal and the order was correctly re-offered to a different driver within the original window, exactly as designed.
- **Server-side relationship-depth read model** (`docs/PIOS_TAXI_RELATIONSHIP_MODEL_EVALUATION.md` Part 5, read-model half). `driver-management` V14 migration adds `last_ride_at` to the existing `driver_client_rides` table; new `GET /v1/drivers/{driverId}/clients` endpoint, gated identically to `getMilestones`. `DriverHome.tsx` now consumes it instead of deriving CRM stats in the browser. No `Connection` lifecycle change, no `ADR-054`-adjacent work. Deployed, and **live-confirmed correct** after the `OrderSubmittedListener` fix below (rideCount 1→2, isRepeat false→true, observed live in production).
- **ADR-079 — Test-Data Credential for Synthetic (`isTest`) Driver Creation.** Closes the owner-auth gap that blocked driver-side E2E since `8206ff3`. New `TestDataCredentialGate` in `driver-management` (SHA-256 of a high-entropy token, not PBKDF2 — reasoned in the ADR; never routed through `OwnerCredentialGate`, own independent failure-window throttle). Authorizes exactly one thing: `POST /v1/drivers` with `isTest: true`. Grants no other capability — verified live (negative cases: `isTest:false` → 403, `listDrivers` → 401, wrong token → 401). Deployed and configured in production (the actual secret token exists only in `/etc/pios/driver-management.env` and this session's own scratchpad — never committed, never pasted into any doc). `ADR-015` amendment pointers added to `ADR-073`/`ADR-044`, which had both drifted false since `8206ff3`'s owner-gate and were never corrected until now.
- **`docs/PIOS_TAXI_HANDOFF_OPEN_QUESTIONS.md`** — the seven Handoff-specific open business questions (OQ-1 through OQ-7) from the evaluation document, each with concrete options and consequences, no recommendation made (per `CLAUDE.md`'s "Never Invent Business Rules"). Ready for Product Owner decision whenever that's taken up; nothing in Handoff proceeds without it.
- **Live E2E verification, `piosapp.ru`, 2026-09-17** (first full production run since `8206ff3`, using the new `PiosTest` credential): driver registration → online → stranger discovery order → server-side proposal → **live decline** → **ADR-078 Decision B confirmed live** (order re-offered to the other driver, excluding the decliner, within the original 2-minute window, observed at the exact 5-second scheduler interval) → price negotiation → Assignment → arrive/start/complete → passenger saves driver (Connection) → repeat order with `explicitDriverIntent` → same driver → second ride completed → **relationship-depth read model confirmed live** (rideCount 1→2, isRepeat false→true). 29/31 script assertions passed on the first fully-instrumented run; the 2 failures were the discovery of the `OrderSubmittedListener` defect itself (see next item), not gaps in the tested features.
- **Live-discovered and fixed: `OrderSubmittedListener` rejected `OrderSubmitted` v2/v3 since `8206ff3` (2026-09-16), silently dead-lettering every order submitted since then.** Found during the E2E above (the relationship-depth check kept returning empty data live, despite the feature itself being correctly deployed). Root cause: this listener required `eventVersion == 1`; Order Management has published v3 since `8206ff3`'s `requestedDriverId` widening (purely additive per `ADR-030`, needed no consumer change on its own — the bug was the listener's rigid `==` check, not the version bump). 8 messages were sitting in `driver-management.from-order-management.order-submitted.dlq` when found. Fixed to `eventVersion in setOf(1, 2, 3)`, the exact pattern already proven working in `dispatch`'s own `OrderSubmittedFirstRefusalListener` (whose own DLQ stayed at 0 the whole time — confirming this listener was the outlier, not the pattern). Three new tests close the gap that let this ship undetected: every existing test used the message-publisher's `eventVersion = 1` default, never exercising the version actually used in production. 253/253 driver-management tests passing. Deployed; **fresh live order submission after the fix confirmed correctly recorded**, not dead-lettered. The 8 already-dead-lettered messages were **not** replayed — see KNOWN RISKS.

## IN PROGRESS

Nothing is mid-implementation. This phase (relationship-depth + owner-auth, per the user's explicit instruction) is complete; documentation is being closed out now.

## NEXT STEP

> **IMPLEMENTATION IS PAUSED, 2026-09-17.** A full Product + Architecture review is complete and is recorded in **`docs/PIOS_FINAL_PRODUCT_OWNER_DECISION_BRIEF.md`**. That document supersedes this section as the authoritative statement of what should happen next, and nothing new should be started until the Product Owner has worked through its Section 17 («DECISIONS FOR ILDAR», twelve decisions in five blocks, each with exactly one question). The list below remains accurate as a description of the *previously* identified gated items, and is preserved unchanged per `CLAUDE.md`'s "Never Delete Documentation" — but the brief reprioritizes it substantially.
>
> **What the brief changes about this section's own ordering, in one paragraph:** it recommends that integrity work precede every item below. Specifically it surfaces **two defects/decisions not previously on any list** — (a) a verified correctness defect where a passenger cancelling after a driver has committed leaves a live, drivable `Assignment`/`Trip` that still increments the driver's ride count and earnings (`AssignmentStatus`/`TripStatus` have no cancelled state; `OrderCancelledApplicationService.kt:58` only withdraws an `OPEN` proposal), and (b) that every driver business counter is fabricable by one party acting alone, which is harmless today and becomes fraud-by-default the moment any tier, trust signal, or fee reads it. It also records **ten documentation-vs-code disagreements** (brief §12.2), of which the highest-severity is that `FallbackDispatchApplicationService.kt:86-109`'s KDoc still asserts "No retry on decline/lapse" and that retrying after a decline is "not decided here" — both made false by ADR-078, which is deployed and live-confirmed.
>
> **Work that may proceed without further sign-off** (brief §16, the seven items marked "Может принять AI? YES"): correcting the stale KDoc above; DLQ depth monitoring; reconciling the commit-identifier discrepancy and the untracked `backend/core/`; server-owned `isTest` derivation on order submission; removing the `/network-test` route from the production build; and closing the `EVENT_CATALOG.md`/`docs/README.md` documentation debt (ADR-068 Q8, ADR-070 Q7). Everything else in the brief requires a Product Owner decision first.

Everything explicitly authorized without further sign-off is now done. What remains is gated on Product Owner input:

1. **Handoff / доверенная замена** — technical design complete (`PIOS_TAXI_RELATIONSHIP_MODEL_EVALUATION.md` Part 4), open questions with options/consequences laid out (`docs/PIOS_TAXI_HANDOFF_OPEN_QUESTIONS.md`, OQ-1 through OQ-7). **Do not start any code or ADR here** until the Product Owner has answered enough of those questions to scope a v1 (a partial answer is usable — the document explains how). *(Brief §7 and D-07 recommend building this only after commitment termination exists, and add two binding conditions — observation-before-cap, and split attribution.)*
2. **`Connection` lifecycle** (the half of relationship depth not done this phase) — `ADR-054:132` ratified its absence deliberately; needs explicit sign-off to reverse.
3. **Settlement evidence v1** — missing entirely; gated on undecided business rules (OQ-8 in the Handoff document, though it's a separate piece of work).
4. **Recurring/scheduled commitments** — needs a driver-calendar concept and conflict invariant that doesn't exist yet; needs its own ADR and sign-off.
5. **Fallback Tier 2 (Network)** — no code needed; put `ADR-068`'s Q2 back to the Product Owner (OQ-10 in the Handoff document).
6. **Minor, optional**: replay or accept the loss of the 8 dead-lettered `OrderSubmitted` messages from before the version fix (KNOWN RISKS, below) — low stakes (only the repeat-client signal for a handful of historical rides), not blocking anything.

Standing rules, unchanged: full autonomy on ordinary engineering; explain before any fundamental business-model/architecture change; cycle анализ → реализация → тесты → проверка → следующий блок; no fake tests, no disabling existing tests, no hiding errors; the golden path must not regress; update this file and the commercial tracker after each major step.

## ARCHITECTURAL DECISIONS

Ratified and current, most recent first:
- **ADR-079** — Test-Data Credential for Synthetic (`isTest`) Driver Creation. `driver-management`-only, new `PiosTest <token>` Authorization scheme, SHA-256(token) + constant-time compare (not PBKDF2 — reasoned: the token is machine-generated, high-entropy, so PBKDF2's offline-brute-force defense buys nothing a plain hash doesn't already provide against this specific threat model). Authorizes exactly `isTest: true` driver creation, nothing else. Deployed and configured in production.
- **ADR-078** — Dispatch Recovery After Proposal Decline or Lapse. Both decisions ratified, implemented, deployed, and **now live-E2E-confirmed** (this phase).
- **ADR-077/076/075** — dispatch retry/`UNFULFILLED`, server-authorized named-driver offer, guest-first identity. Unchanged this phase.
- **ADR-074/073/071/070/069/068** — billing container, driver referral, notifications, discovery matching, test/real segregation, tier order. Unchanged this phase.

Standing, non-superseded decisions that constrain future work:
- **`ADR-054` Part 5** — bans delegation mechanisms of any kind. Directly blocks Handoff until the Product Owner narrows it. Not touched, not proposed for change, by anything in this phase.
- **`DRIVER_IDENTITY_DESIGN_DECISION.md` §4** — bans passenger-facing numeric trust signals. Unaffected by the relationship-depth work (which is driver-facing only, same gating as `getMilestones`).
- **`PRODUCT_DECISION_NOTIFICATIONS.md` §6**, **`PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 8** — unchanged.

## KNOWN RISKS

1. ~~`POST /v1/drivers` isTest owner-auth gap~~ — **resolved by ADR-079** (this phase). Driver-side E2E is fully usable again via the `PiosTest` credential.
2. ~~Full driver-side golden path lifecycle not re-verified~~ — **resolved this phase.** Full live E2E run 2026-09-17 (register → online → discovery → decline → ADR-078 resume → price → Assignment → arrive/start/complete → save → repeat → relationship-depth). See COMPLETED for the exact scenario.
3. **DLQ alarm gap, still unaddressed, and now proven to matter twice over.** No alerting infrastructure exists for any dead-letter queue. This phase's own `OrderSubmittedListener` defect sat undetected in production for a full day, silently dead-lettering messages, precisely because nothing watches these queues. ADR-078 also increases traffic on the `DispatchExhausted` DLQ. **This is no longer a theoretical risk — it is the reason a real defect went unnoticed. Recommend prioritizing DLQ monitoring, even minimal (a periodic queue-depth check), before the next round of event-schema changes.**
4. **8 messages remain dead-lettered in `driver-management.from-order-management.order-submitted.dlq`** — from before the `OrderSubmittedListener` fix, not replayed. Low stakes: affects only the repeat-client milestone/relationship-depth signal for a handful of specific historical rides, nothing financial or ride-critical. Optional cleanup, not blocking.
5. **`billing` module has no tariff, no payment provider, and gates nothing** — correct, tested container per ADR-074; flag before building, don't just build it.
6. Guest creation rate limiting is in-memory, per-instance; phone ownership unverified at registration. Named limitations in ADR-075, not silent gaps.
7. Flyway-checksum-after-comment-edit lesson (from the prior phase) stands: never edit an already-applied migration file's content, including comments, after it has been applied anywhere.

## TEST STATUS

- **Driver Management, `4c0a818`** (fresh run, this phase): 253/253 passing, `BUILD SUCCESSFUL`. Includes ADR-079's `TestDataCredentialGate`/`DriverController` tests, the relationship-depth read-model tests, and the 3 new `OrderSubmittedListener` version-coverage tests (v2, v3, and an out-of-range version correctly rejected).
- **Dispatch, `429441f`** (unchanged this phase): 636/636 passing.
- Frontend: 365/365 (relationship-depth `DriverHome.tsx` changes included), build and lint clean.
- Order Management, Identity: unchanged since `8206ff3`, full suites green at that commit.
- Billing: 68/68, unchanged, undeployed.

**Live production verification, 2026-09-17** (first full run since `8206ff3`, see COMPLETED for the exact scenario): driver registration/association/online ✅, discovery order ✅, server-side proposal ✅, **live decline → ADR-078 resumed offer to a different driver, excluding the decliner ✅** (previously untested live), price negotiation → Assignment → arrive/start/complete ✅, Connection save ✅, repeat order with `explicitDriverIntent` → same driver ✅, **relationship-depth read model (rideCount, isRepeat) ✅ after the `OrderSubmittedListener` fix**. This is the first fully-confirmed live golden path since the `8206ff3` deploy.

## PRODUCTION STATUS

- **`pios-driver-management` deployed twice this phase**: first at `82c9ec3` (relationship-depth + ADR-079), then at `4c0a818` (the `OrderSubmittedListener` fix). Both clean restarts, no errors, SHA256-verified.
- **`pios-frontend` deployed at `82c9ec3`'s build** (relationship-depth `DriverHome.tsx`).
- `pios-dispatch` unchanged this phase, still `429441f`.
- `pios-order-management`, `pios-identity`, `pios-passenger-experience`, `pios-ai-advisor`: untouched this phase.
- **`pios.test-data.credential-hash` configured in production** (`/etc/pios/driver-management.env`) — the raw token exists only there and in this session's own local scratchpad (never committed, never pasted into a doc or chat transcript). Rotation: one config line, one restart (per ADR-079 Decision 6).
- Flyway: `pios_driver_management` at V14 (relationship-depth `last_ride_at` column), applied cleanly, no checksum issues this time.
- 8 messages remain in `driver-management.from-order-management.order-submitted.dlq` (KNOWN RISKS #4).
- `billing` module: still not deployed, unchanged.
- Backups retained on the VPS under `/tmp/relstage/` for this phase's deploys, alongside the earlier `/tmp/adr078_staged/`/`/tmp/adr068069_staged/` conventions.

## FILES TO READ FIRST

For a new session picking this up:
0. **`docs/PIOS_FINAL_PRODUCT_OWNER_DECISION_BRIEF.md` (2026-09-17) — read this before anything else.** Full Product + Architecture review; it reprioritizes NEXT STEP above, records ten documentation-vs-code disagreements (§12.2) and eleven security findings (§12.1), and holds the twelve open Product Owner decisions (§17). Implementation is paused pending those.
1. This file and `docs/PIOS_TAXI_COMMERCIAL_EXECUTION.md` — live trackers; read both.
2. `docs/PIOS_TAXI_HANDOFF_OPEN_QUESTIONS.md` — the concrete decision points blocking the next phase of work; read before touching Handoff.
3. `docs/PIOS_TAXI_RELATIONSHIP_MODEL_EVALUATION.md` — the gap analysis the above builds on.
4. `docs/ADR/ADR-079-...md` — the test-data credential mechanism, if extending E2E tooling further.
5. `docs/ADR/ADR-078-...md`, `ADR-075/076/077` — dispatch/identity architecture, unchanged this phase but foundational.
6. `backend/driver-management/src/main/kotlin/com/pios/drivermanagement/persistence/OrderSubmittedListener.kt` — read its own KDoc before touching any event-version-sensitive consumer; the fix and its lesson are recorded there.
7. `ADR-054` Part 5, `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 8 — standing constraints on Handoff.
8. `CLAUDE.md` and `.claude/CLAUDE.md` — governance rules unchanged.

---

## 2026-09-17 — ATLAS handoff after new Product Owner decisions

This dated section supersedes the **pause** in `NEXT STEP` above: the Product Owner has now expressly answered D-01–D-12 in the attached “PIOS — COMMERCIAL V1 IMPLEMENTATION MISSION / PRODUCT OWNER DECISIONS → IMPLEMENTATION”. The older pause and questions remain above as historical context, not as a current prohibition. **No code, migration, deployment, commit, or push was performed while preparing this update.** The source-backed, sixteen-part reconciliation is now in `docs/ATLAS_IMPLEMENTATION_RECONCILIATION.md`; do not treat the new decisions as already implemented.

### 1–2. Completed work and changed code

At the observed local HEAD, branch `pios-product-main` is `dc8fb36` (`docs: close out relationship-depth + owner-auth phase`); recent commits are `4c0a818` (OrderSubmitted v2/v3 consumer fix), `82c9ec3` (ADR-079 test credential), `127c430` (server-side client-depth read model), `429441f` (ADR-078 decline/lapse recovery), and `8206ff3` (guest identity, named-driver offer, durable dispatch retry/`UNFULFILLED`). These are **pre-existing commits**, not work performed by this handoff task. See `COMPLETED` and `PRODUCTION STATUS` above for the exact prior evidence.

Key code already changed by those commits: `backend/identity` guest creation/in-place upgrade; `backend/order-management` `requestedDriverId`, `UNFULFILLED`, outbox contracts; `backend/dispatch` `DispatchRequestApplicationService`, `dispatch_requests`, proposal reopen on decline/lapse, Trip ride-progress convergence; `backend/driver-management` test credential, client-depth read model, and `OrderSubmittedListener` v1/v2/v3 acceptance; `frontend/src/pages/RideRequest/RideRequest.tsx` and `frontend/src/pages/DriverHome/DriverHome.tsx`. This dated handoff section modifies **only this document**.

### 3–4. Accepted architecture and rejected approaches

Already ratified: ADR-075–079, including server-owned named-driver offers, a two-minute persisted routing obligation, and decline/lapse recovery within the original window. The new Product Owner decisions now authorize the following **future implementation**, in this order: explicit two-sided commitment termination (D-01); non-load-bearing self-reported business counters (D-02); registered-identity phone/SMS-OTP recovery (D-03); PIOS as the independent driver's business record (D-04); free earn-a-living/discovery path and no paid routing priority (D-05); Trip-owned agreed amount and multi-outcome evidence without PIOS handling money (D-06); then single-hop, driver-initiated, passenger-consented Handoff after D-01 and a narrow ADR-054 amendment (D-07/08); circle-growth UX, minimal two-moment Web Push, accepted-future-ride calendar, and a privacy-correct vehicle surface (D-09–11). D-12 preserves Product Owner hypothesis registration for product features; defect remediation does not need a new hypothesis.

Explicitly **rejected/not authorized**: commission, origination fee, paid discovery or priority, payment processing/escrow/refunds/driver balances, penalties or trust-score effects from termination, numeric passenger-facing trust, automatic or PIOS-selected/chained Handoff, Connection migration on Handoff, guessed handoff cap, Tier 2 network routing in Commercial V1, recurring order generation, and selecting a concrete SMS provider as a product decision without investigation. Keep the `billing` module inert until a separate pricing decision. Do not silently rewrite ADR-054 or any applied migration.

### 5–7. Finished, in progress, and remaining

The commits and live verification listed above are finished according to the previous handoff. **Current work produced the read-only ATLAS reconciliation and this handoff update; Phase 1 code has not begun.** Next: review `docs/ATLAS_IMPLEMENTATION_RECONCILIATION.md`, resolve D-01's missing fixed reason list, then proceed in the mandated order: Phase 1 D-01 → D-02 → D-03 → security/drift → tests; Phase 2 D-06 and narrow ADR amendments; Phase 3 Handoff design/ADR-054 supersession/authorization/attribution/observability before any Handoff code; Phase 4 D-09/10/11; Phase 5 full regression, security, architecture, live golden-path audit. D-11 now says Tier 2 is **deferred/not implemented in V1**, not permanently removed.

### 8. Known problems, with direct evidence

- **HIGH correctness, D-01:** `backend/order-management/.../domain/Order.kt:199` permits cancellation from `SUBMITTED`; that status remains through an accepted proposal and active Assignment/Trip. `backend/dispatch/.../application/OrderCancelledApplicationService.kt:51–67` withdraws only an `OPEN` proposal. `AssignmentStatus.kt` and `TripStatus.kt` have no terminal-negative state; `DispatchAssignmentApplicationService.kt:267–314` can still drive the Trip to `COMPLETED`. This can count a cancelled ride in `driver-management`'s `AssignmentCompletedApplicationService`. Fix the root lifecycle and causal event path, not merely an API symptom.
- **HIGH conditional fraud risk, D-02:** `DriverMilestones` and `driver_client_rides` derive from party-generated events. They are acceptable private/self-reported records today but must never feed money, tier, routing, or passenger trust without a new explicit decision and stronger evidence.
- **D-03:** `Identity.phone` and password login exist, but registration does not verify phone ownership. `VerificationChallenge.kt` is a shape-only type: no persistence, SMS port/provider, OTP lifecycle, or recovery API. Guests must stay unrecoverable.
- **D-06:** `Trip.kt`/`V12__trips.sql` have no agreed amount or multi-outcome evidence. `Proposal.statedPrice` is copied into `AssignmentCompleted` for private earnings; this is not a durable two-sided settlement fact.
- **D-01 specification detail still missing:** the new Product Owner mission mandates a fixed, coarse termination-reason set but does not enumerate its values. The earlier decision brief explicitly called the list a small business-rule surface. Do not invent categories in implementation; obtain the Product Owner's list or an explicit delegation of that choice. The post-commitment cancellation defect can be designed and tested in parallel, but the full D-01 API/event contract cannot be frozen without this answer.
- **D-03 recovery bootstrap risk:** existing registered `Identity.phone` values were never ownership-verified (`Identity.kt`, `VerificationChallenge.kt`). SMS-only recovery of all existing accounts could transfer a driver's business to the current holder of an unverified number. A safe enrolment/migration rule must be specified before enabling recovery for legacy identities; future registration/upgrade verification and generic recovery responses need to be considered together.
- **Operational/security:** DLQ depth has no alarm despite the live v2/v3 `OrderSubmitted` incident; eight pre-fix messages remained in the Driver Management DLQ per the prior handoff. Public `GET /v1/drivers/{id}` exposes a plate; order `isTest` is caller-asserted; guest throttling is in-memory; tokens have no revocation. See `docs/PIOS_FINAL_PRODUCT_OWNER_DECISION_BRIEF.md` §12 for severity and exact paths. Neither these findings nor the brief's stale documentation claims have been silently fixed here.

### 9–11. Tests, production checks, and migrations

Historical evidence from the prior handoff (not rerun by this update): Driver Management **253/253**, Dispatch **636/636**, frontend **365/365** plus build/lint; Identity and Order Management suites green at `8206ff3`; billing **68/68** and undeployed. Earlier live production E2E on 2026-09-17 covered registration → referral/online → discovery → decline/re-offer → price/acceptance → arrive/start/complete → save driver → repeat order → client-depth record, and discovered/fixed the Driver Management event-version bug. **No fresh test suite or production probe has been run for D-01–D-12, because none of those changes exists yet.**

Prior handoff reports Dispatch V19 (`dispatch_requests`) and Driver Management V14 (`last_ride_at`) applied in production; `8206ff3` also deployed its Identity/Order Management migrations. Verify each target's actual `flyway_schema_history` before any new deployment. The earlier V19 checksum incident required an explicitly authorized production history correction; never edit an applied migration, including comments/whitespace. **No migration was applied during this update.**

### 12–14. Read-first files, next action, and invariants

Read `CLAUDE.md`, `.claude/CLAUDE.md`, this file, `docs/PIOS_TAXI_COMMERCIAL_EXECUTION.md`, the new Product Owner mission, `docs/PIOS_FINAL_PRODUCT_OWNER_DECISION_BRIEF.md` (historical recommendations, now answered), `docs/PIOS_TAXI_HANDOFF_OPEN_QUESTIONS.md`, ADR-038/040/041/042/054/063/065/073/075–079, and the `PRODUCT_DECISION_*.md` relevant to each phase. For D-01 start at `Order.kt`, `OrderLifecycleApplicationService.kt`, `OrderCancelledApplicationService.kt`, `Assignment.kt`, `Trip.kt`, `DispatchAssignmentApplicationService.kt`, both completion consumers, their PostgreSQL repositories and tests. For D-03 start at `IdentityController.kt`, `LoginApplicationService.kt`, `VerificationChallenge.kt`, and Identity credential/phone repositories. For D-06 start at `Trip.kt`, `PostgreSQLTripRepository.kt`, `V12__trips.sql` (read-only), `Proposal.kt`, and ADR-042/065.

**Correct next step:** use the source-backed ATLAS reconciliation, resolve the D-01 reason taxonomy, then design and implement D-01 as the first bounded Phase-1 change, with an additive migration, transaction/idempotency/concurrency tests, and a live golden-path regression plan. Do not run production SQL or deploy on the strength of unit tests alone.

**Do not break:** independent driver ownership/relationship boundaries; the passenger's right to consent to any future Handoff; no automatic transfer of `Connection`; the free, fair discovery path; test/real segregation; owner/participant authorization; outbox delivery and consumer idempotence; the `AssignmentCompleted` → Order Management and Driver Management compatibility contracts; terminal `COMPLETED` semantics; accepted future orders; existing repeat-client/referral flows; the live golden path. Preserve all pre-existing local modifications and the untracked `docs/PIOS_FINAL_PRODUCT_OWNER_DECISION_BRIEF.md`, `adr068069_deploy/`, `windows-services/core/`, `.claude/settings.local.json`, and unrelated service/config changes. Do not commit/push or deploy without an explicit subsequent instruction.

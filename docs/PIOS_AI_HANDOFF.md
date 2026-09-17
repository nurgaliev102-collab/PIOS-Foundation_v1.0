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

Everything explicitly authorized without further sign-off is now done. What remains is gated on Product Owner input:

1. **Handoff / доверенная замена** — technical design complete (`PIOS_TAXI_RELATIONSHIP_MODEL_EVALUATION.md` Part 4), open questions with options/consequences laid out (`docs/PIOS_TAXI_HANDOFF_OPEN_QUESTIONS.md`, OQ-1 through OQ-7). **Do not start any code or ADR here** until the Product Owner has answered enough of those questions to scope a v1 (a partial answer is usable — the document explains how).
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
1. This file and `docs/PIOS_TAXI_COMMERCIAL_EXECUTION.md` — live trackers; read both.
2. `docs/PIOS_TAXI_HANDOFF_OPEN_QUESTIONS.md` — the concrete decision points blocking the next phase of work; read before touching Handoff.
3. `docs/PIOS_TAXI_RELATIONSHIP_MODEL_EVALUATION.md` — the gap analysis the above builds on.
4. `docs/ADR/ADR-079-...md` — the test-data credential mechanism, if extending E2E tooling further.
5. `docs/ADR/ADR-078-...md`, `ADR-075/076/077` — dispatch/identity architecture, unchanged this phase but foundational.
6. `backend/driver-management/src/main/kotlin/com/pios/drivermanagement/persistence/OrderSubmittedListener.kt` — read its own KDoc before touching any event-version-sensitive consumer; the fix and its lesson are recorded there.
7. `ADR-054` Part 5, `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 8 — standing constraints on Handoff.
8. `CLAUDE.md` and `.claude/CLAUDE.md` — governance rules unchanged.

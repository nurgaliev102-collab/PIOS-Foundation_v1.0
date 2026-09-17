# ATLAS IMPLEMENTATION RECONCILIATION

**Date:** 2026-09-17. **Status:** source-backed pre-implementation checkpoint for the Product Owner's D-01–D-12 mission; not an ADR, not a release claim. Code remains authoritative for current behaviour. The new Product Owner mission supersedes the implementation pause recorded in the earlier handoff, but does not make future behaviour already implemented.

## 1. Current commit and branch

Observed `pios-product-main` at `dc8fb36`. The five latest commits at inspection: `dc8fb36` (handoff/relationship-depth closeout), `4c0a818` (Driver Management OrderSubmitted v2/v3 fix), `82c9ec3` (ADR-079 test credential), `127c430` (client-depth read model), `71535cd` (Handoff open questions). Earlier important commits: `429441f` (ADR-078), `8206ff3` (ADR-075–077). This resolves the older brief's *historical* three-identifier confusion for the present checkout; it does not by itself prove which artifact is running in production.

## 2. Current worktree

Pre-existing dirty paths at inspection: `.claude/settings.json`, `backend/core/src/main/resources/application.yml`, `docs/PIOS_TAXI_COMMERCIAL_EXECUTION.md`, `windows-services/cloudflared/{pios-cloudflared.xml,run-tunnel.ps1}`; untracked `.claude/settings.local.json`, a scratch diff, `adr068069_deploy/`, `docs/PIOS_AI_FEEDBACK_INTELLIGENCE_ARCHITECTURE_ANALYSIS.md`, `docs/PIOS_FINAL_PRODUCT_OWNER_DECISION_BRIEF.md`, and `windows-services/core/`. `docs/PIOS_AI_HANDOFF.md` was already modified when this mission arrived and has been **appended**, not replaced. `backend/core/` is not currently the untracked path alleged in the older brief; the visible untracked path is `windows-services/core/`. Preserve every unrelated item. No commit/push/deploy in this reconciliation.

## 3. What previous implementers actually completed

The commit graph, code and prior handoff support: guest-first identity/in-place upgrade; server-authorized selected-driver offer; persisted dispatch routing/`UNFULFILLED`; decline/lapse reopen within the original window; driver↔client depth read model; synthetic-driver test credential; and a v2/v3 `OrderSubmitted` consumer fix. Concrete anchors: `backend/dispatch/.../application/DispatchRequestApplicationService.kt`, `ProposalApplicationService.kt`, `backend/driver-management/.../persistence/OrderSubmittedListener.kt`, `backend/driver-management/.../api/DriverController.kt`, `backend/identity/.../api/IdentityController.kt`, `frontend/src/pages/{RideRequest,DriverHome}`. The previous handoff reports a live 2026-09-17 golden-path E2E; this reconciliation has not independently replayed it.

## 4. What remains

D-01 commitment termination is absent; D-02's general metric boundary is not ratified in one controlling decision; D-03 recovery has only a shape-only `VerificationChallenge`; D-06 has no Trip-owned agreed amount or shared outcome read. D-07/08 Handoff remains unbuilt and prohibited by ADR-054 until a narrow supersession. D-09 growth UX, D-10 out-of-app Web Push, D-11 accepted-future-ride calendar and plate-safe public vehicle display are not complete. `billing` remains an intentionally inert container. See `docs/PIOS_AI_HANDOFF.md` and the completion audit for the rest of the commercial scope.

## 5. Exact implementation order

Follow the new mission, not the old brief's paused recommendations: **Phase 1:** D-01 lifecycle/termination → D-02 metric boundary → D-03 recovery → security/drift corrections → tests. **Phase 2:** D-06 Trip evidence, narrow ADR-042/065 amendments, tests. **Phase 3:** design, ADR-054 compatibility and dated supersession, authorization, attribution and owner-facing concentration observability **before** Handoff code. **Phase 4:** D-09, then D-10, then D-11 calendar and vehicle privacy. **Phase 5:** all-module regression, security review, architecture review, real golden-path E2E, completion audit. Product features still require a Product Owner-registered hypothesis where `PIOS_PRODUCT_EVIDENCE.md` requires one; D-01 and proven security/drift defects are defect remediation.

## 6. D-01 root cause and required boundary

`backend/order-management/src/main/kotlin/com/pios/ordermanagement/domain/Order.kt:199–205` allows `SUBMITTED → CANCELLED`; `SUBMITTED` still covers a committed ride. `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/OrderCancelledApplicationService.kt:51–67` only withdraws an `OPEN` proposal, so an `ACCEPTED` proposal and its Assignment/Trip survive. `AssignmentStatus.kt` and `TripStatus.kt` have no terminal-negative value. `DispatchAssignmentApplicationService.kt:267–314` drives Trip arrive/start/complete without the Order's cancellation state; `driver-management` counts `AssignmentCompleted` independently. This is not fixed by only hiding buttons or adding a check in Order Management.

**Architecture to validate before code:** Dispatch owns the commitment/Trip's terminal transition and must serialize termination against ride progress on the same persisted Trip/Assignment row. Merely consuming `OrderCancelled` asynchronously leaves a window where the order is cancelled but Dispatch can still complete; an immediate passenger-facing `CANCELLED` response cannot be treated as proof the commitment has stopped. The implementation design must define a causal acknowledgement/terminal event, idempotent redelivery, winner of completion-vs-termination races, and Order Management's visible pending/final state without inventing penalties or a second ride lifecycle. The existing `AssignmentCompleted` compatibility event must only follow a genuinely completed, non-terminated Trip. **Open Product Owner detail:** D-01 requires a fixed coarse reason set but supplies no values; do not invent it.

## 7. D-03 recovery architecture

Current: `Identity.phone` is optional and explicitly unverified (`backend/identity/.../domain/Identity.kt`); password credentials and login exist (`Credential.kt`, `LoginApplicationService.kt`); `VerificationChallenge.kt` has `SMS_OTP` as a discriminator but no storage or sender; no recovery endpoint or provider port exists. A safe design needs an SMS sender interface configured separately, durable challenge rows with a keyed hash of the OTP (not plaintext), expiry, single-use atomic consume, attempt budget and rate limits by phone/IP/identity, generic request response regardless of account existence, no OTP logs, and session/password reset only after proof. Guests remain unrecoverable. **Critical bootstrap problem:** historical phones were never verified. Enabling SMS-only recovery for those rows can transfer an existing driver/client record to whoever currently controls that number. Introduce a verified-phone enrolment state and a legacy-account rule (e.g. require the existing authenticated session plus SMS proof before becoming recovery-eligible); the Product Owner must approve any exception for an already-locked-out legacy user. Provider choice is an adapter/configuration decision after market/security review, not a business-model commitment.

## 8. D-06 exact Trip model delta

`backend/dispatch/.../domain/Trip.kt` has only identity, executor reference, timestamps and status; `V12__trips.sql` has no amount. `DispatchAssignmentApplicationService.kt:299–314` currently reads `Proposal.statedPrice` only while emitting `AssignmentCompleted`; that event feeds a private driver earnings projection. Minimal additive design: copy the **passenger-confirmed** price into an immutable, nullable-for-legacy `Trip.agreedAmount` at commitment creation; retain the exact accepted representation until a separately ratified normalization rule exists (`Proposal.statedPrice` is currently opaque text, ADR-065's digits-only parser serves a private derived counter). After D-01, Trip terminal states can be the durable multi-outcome evidence; do not create a second competing Settlement lifecycle or paid/unpaid ledger. Expose the agreed amount and terminal outcome only to the passenger and actual executor through scoped reads. Add a new Dispatch migration; never edit V12. Preserve `AssignmentCompleted`'s existing event contract during migration.

## 9. Handoff prerequisites

The new D-07/08 authorization does not remove ADR-054's current ban by itself. First finish D-01; then write a concrete state/authorization/attribution design and a dated narrow ADR-054 supersession. Only the committing driver may nominate one substitute; the substitute must accept; the passenger's consent must be blocking; no chain or PIOS-selected substitute; no automatic Connection migration; the relationship-facing record stays with the committer while ride count/earnings follow the executor. Owner-facing handoff frequency and concentration must exist before production exposure. `docs/PIOS_TAXI_RELATIONSHIP_MODEL_EVALUATION.md` Part 4 is a proposal, not already-ratified implementation; revisit its Trip-executor and termination mechanics after D-01.

## 10. ADRs/decisions needing amendment or new record

- D-01: new architecture decision, with narrow dated pointers from ADR-040/041/053/063 as necessary. ADR-041's `AssignmentCompleted` → Order completion authority and `CANCELLED`-status-wins rule must not be silently changed.
- D-02: a new controlling Product Decision generalizing ADR-073 Part 5 clause 3; preserve the original ADR and existing counters.
- D-03: extend ADR-038/055/075 only to the extent the recovery/phone-verification path changes their stated exclusions; no silent change to guest semantics.
- D-06: narrow dated amendment to ADR-042 R4.8 and ADR-065's price/disclosure boundary; do not turn the billing container into a payment service.
- D-07/08: explicit dated ADR-054 supersession after the design and prerequisites, not an edit that erases the historical ban.
- D-10: product/architecture update for the explicit two-moment Web Push exception to `PRODUCT_DECISION_NOTIFICATIONS.md` §6.

## 11. Existing test coverage

Relevant files include `backend/order-management/.../domain/OrderTest.kt`, `api/OrderCancellationControllerTest.kt`, `api/OrderCancellationControllerPostgreSQLSecurityTest.kt`, `persistence/AssignmentCompletedConsumerIntegrationTest.kt`; `backend/dispatch/.../domain/TripTest.kt`, `application/DispatchAssignmentApplicationServiceTest.kt`, `persistence/PostgreSQLAssignmentLifecycleTest.kt`, `api/AssignmentControllerPostgreSQLSecurityTest.kt`, and cancellation consumer integration tests; `backend/identity/.../api/IdentityControllerTest.kt`, credential/login tests and PostgreSQL identity lifecycle tests. These prove the existing linear success path and pre-commitment cancellation, **not** post-commitment termination or recovery. Prior handoff lists the last known suite results; no new suite was run during this read-only reconciliation.

## 12. New tests required

D-01: passenger and driver termination before arrival and after start; actor/reason persistence and counterparty visibility; 401/403 cross-participant attempts; duplicate/late/reordered events; completion-vs-termination race on real PostgreSQL/RabbitMQ; no `AssignmentCompleted`/earnings/client-ride increment after termination; immutable terminal state; unchanged `COMPLETED` golden path. D-02: static/contract guard that no metric enters billing, priority or public trust surfaces. D-03: unknown-phone indistinguishability, legacy unverified-phone takeover prevention, expiry, single-use, wrong-code/lockout, concurrency, OTP nonlogging and provider failure. D-06: accepted amount immutability, legacy null migration, terminal outcome read auth, no payment fields.

## 13. CRITICAL/HIGH security findings

No **current** CRITICAL finding was established by the prior source review. **HIGH:** post-commitment cancellation correctness (`Order.kt`/`OrderCancelledApplicationService.kt`/Trip lifecycle), and conditional self-dealing metric fabrication if a paid or passenger-facing capability consumes private counters. **Potential HIGH if D-03 is implemented naively:** recovery takeover through unverified historical `Identity.phone`; the safety rule above is a release gate. Additional MEDIUM risks recorded in `docs/PIOS_FINAL_PRODUCT_OWNER_DECISION_BRIEF.md` §12: public plate, no token revocation, no DLQ alert, caller-asserted order `isTest`.

## 14. Production risks

The previous handoff reports the 2026-09-17 live golden path, but no production probe was run here. Eight Driver Management `OrderSubmitted` messages were reportedly left in its DLQ from the old event-version defect; there is no DLQ alarm. Dispatch V19 had an actual Flyway checksum incident after a comment-only edit; preserve all applied migration bytes and inspect target history before rollout. Avoid concurrent deployment of producer and consumer changes without compatible event versions. Keep owner credentials/test-data token out of source, docs and logs. New recovery creates an external SMS dependency and a new takeover surface.

## 15. Autonomous scope

Within the approved D-01–D-12 boundaries: document and implement the confirmed cancellation defect; preserve private metrics but record the D-02 boundary; design a provider-neutral OTP port and safe verified-phone state; add append-only migrations/tests; fix proven authorization/drift defects; build D-06 evidence without money movement; add specifically approved UX/Web Push/calendar/vehicle facts in the prescribed phase. Every product feature still follows the D-12 hypothesis gate. Do not deploy automatically, commit or push without explicit instruction.

## 16. Blocked scope and next action

Do **not** implement Handoff before D-01 plus the ADR-054 amendment, attribution and observability; do not implement Tier 2 in V1, recurrence, paid priority, commission, payment processing, numeric rating, or any tariff from the inert billing module. The first concrete next action is a D-01 design/ADR that specifies Dispatch-owned terminal authority and the cross-context race protocol, then its tests and additive migrations. The missing fixed reason taxonomy is a genuine Product Owner decision for the full D-01 contract; the legacy-phone recovery rule is a second security/product decision before enabling D-03 for historical accounts. Neither should be guessed in a pull request.

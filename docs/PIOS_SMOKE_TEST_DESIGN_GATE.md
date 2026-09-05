# PIOS Final Pilot Smoke-Test Design Gate

**Date:** 2026-09-04. **Scope:** design only — a precise, executable smoke-test plan for the approved RC (`92953ed` and ancestry) is produced below, but **nothing in it was executed**. No mutation request was sent, no record of any kind was created, no database was read or written, no real account was touched. Every fact below comes from reading source code (application/domain layer, controllers, request/response DTOs, migrations, frontend data-fetching) and from this arc's own prior, already-verified findings (session-secret mechanism, live service inventory).

---

## GO / NO-GO for executing this smoke test

**GO — the plan below is safe to execute, once two operator-owned prerequisites are met.**

Neither prerequisite required inventing a workaround; both were already identified by this arc's own prior gates and are restated here as the exact conditions that make this plan's own safety claims hold:

1. **The `pios.session.secret` mechanism must be committed to documentation before deployment** (`docs/PIOS_SESSION_SECRET_MECHANISM_INVESTIGATION.md`, classification B) — this smoke test's every authenticated step depends on `identity` issuing, and the four verifying modules accepting, a working session token after restart.
2. **This is a design gate, not a deployment gate** — the sequence below assumes the RC is already live (§10 of `docs/PIOS_FINAL_DEPLOYMENT_GATE.md`'s own deployment procedure). It does not authorize, and should not be read as authorizing, the restart itself.

No step in the plan below required an authorization bypass, an undocumented flag, or an assumption this investigation could not verify from source. If any step's isolation guarantee had turned out to be unverifiable, this section would say NO-GO and name the exact step — that did not happen for any step; see §5 for the one place isolation depends on operator discipline (identity has no `isTest` concept at all) rather than a code-enforced guarantee.

---

## 1. Test entities

Two synthetic identities, created fresh by this plan's own first two steps — **no existing pilot account, real phone number, or real driver is read, referenced, or touched at any point**:

| Entity | Value | Why it's safe |
|---|---|---|
| Synthetic passenger phone | `+15555550101` (NANP-reserved fictional-use range, `+15555550100` already used by the prior empirical auth test — this plan uses the next number in that same reserved block to avoid a `409 PhoneAlreadyRegisteredException` collision) | `Phone`'s own domain KDoc: format-only validation, "no verification, no proof this number is reachable" — nothing is contacted |
| Synthetic driver id | `smoke-test-driver-<uuid>` (e.g. `smoke-test-driver-f3a1...`), generated fresh at test time | Unguessable, namespaced, `isTest: true`, never overlaps a real pilot driver's own id |
| Synthetic driver display name | `SMOKE TEST — DO NOT DISPATCH` | Immediately recognizable as synthetic to any human who ever sees a raw DB row or an admin screen, in case `isTest` filtering is ever bypassed by a future, different screen |

## 2. Sequence diagram (text)

```
Passenger(synthetic)                 identity:8086         driver-management:8081     dispatch:8084          order-management:8083
       |                                   |                        |                       |                        |
  (1)  |--POST /v1/identities/register---->|                        |                       |                        |
       |<--201 {identityId, token(drv=null)}                        |                       |                        |
       |                                   |                        |                       |
       |                                                            |
  (2)  |----------------------------POST /v1/drivers (no auth)----->|                        |                        |
       |<-----------------------------------------------201 {driverId, availability=UNAVAILABLE, isTest=true}         |
       |                                   |                        |                       |                        |
  (3)  |--POST /v1/identities/{id}/driver->|                        |                       |                        |
       |   (Bearer token from step 1)      |                        |                       |                        |
       |<--201 {token(drv=driverId)}-------|                        |                       |                        |
       |                                   |                        |                       |                        |
  (4)  |----------------------------POST /v1/drivers/{id}/availability {AVAILABLE}---------->|                        |
       |   (Bearer token from step 3)                               |                       |                        |
       |<-----------------------------------------------200 {availability=AVAILABLE}         |                        |
       |                                   |                        |                       |                        |
  (5)  |------------------------------------------------------------------------------------------------------->POST /v1/orders
       |   NO auth header (accepted risk — see §7)                  |                       |                        |  {passengerReference=identityId, isTest=true, explicitDriverIntent=true}
       |<---------------------------------------------------------------------------------------------------------201 {orderId}
       |                                   |                        |                       |                        |
  (6)  |-------------------------------------------------------------------------->POST /v1/proposals               |
       |   (Bearer token from step 3 — "any authenticated caller")  |                       |  {orderId, driverId, isTest=true}
       |<--------------------------------------------------------------------------201 {proposalId, status=OPEN}     |
       |                                   |                        |                       |                        |
  (7)  |-------------------------------------------------------------------------->POST /v1/proposals/{id}/accept   |
       |   (Bearer token from step 3, drv == proposal.driver)       |                       |                        |
       |<--------------------------------------------------------------------------200 {status=ACCEPTED}            |
       |                                   |                        |                       |  (Assignment auto-created)
       |                                   |                        |                       |                        |
  (8)  |-------------------------------------------------------------------------->POST /v1/assignments/{id}/arrive |
       |<--------------------------------------------------------------------------200 {status=ARRIVED}             |
  (9)  |-------------------------------------------------------------------------->POST /v1/assignments/{id}/start  |
       |<--------------------------------------------------------------------------200 {status=IN_PROGRESS}         |
  (10) |-------------------------------------------------------------------------->POST /v1/assignments/{id}/complete
       |<--------------------------------------------------------------------------200 {status=COMPLETED}           |
       |                                   |                        |                       |                        |
  --- separate, second order, for the cancellation branch (Tier 1 scope: cancel while still OPEN) ---
       |                                                            |                       |                        |
  (11) |------------------------------------------------------------------------------------------------------->POST /v1/orders
       |   {passengerReference=identityId, isTest=true, explicitDriverIntent=true}                                  |
       |<---------------------------------------------------------------------------------------------------------201 {orderId2}
  (12) |------------------------------------------------------------------------------------------------------->POST /v1/orders/{orderId2}/cancel
       |   (Bearer token from step 3, sub == order.origin)                                                          |
       |<---------------------------------------------------------------------------------------------------------200 {status=CANCELLED}
```

Steps 8–10 use `driverId`/`assignmentId` returned along the way; the exact assignment id is read back from the `AssignmentResponse` step 7's own accept response does not itself return — `GET /v1/assignments?orderId={orderId}` (unauthenticated, read-only, already used by both `DriverHome.tsx` and `RideRequest.tsx` today) resolves it between steps 7 and 8.

## 3. Endpoints, required fields, authentication — one row per step

| # | Endpoint | Method | Required fields | Auth |
|---|---|---|---|---|
| 1 | `/v1/identities/register` | POST | `phone`, `password` | None (public) |
| 2 | `/v1/drivers` | POST | `driverId` | None — deliberately unauthenticated by design (`DriverController`'s own KDoc: pre-authentication "first contact," Task 24 classified LOW/informational) |
| 3 | `/v1/identities/{id}/driver` | POST | `driverId` | `Bearer` token from step 1, `verified.sub == {id}` (403 otherwise) |
| 4 | `/v1/drivers/{driverId}/availability` | POST | `availability` (`"AVAILABLE"`) | `Bearer` token from step 3, `verified.drv == driverId` (403 otherwise) |
| 5 | `/v1/orders` | POST | `passengerReference` | **None — the accepted risk, unchanged (§7)** |
| 6 | `/v1/proposals` | POST | `orderId`, `driverId` | `Bearer` token from step 3 (any verified session token is sufficient — `sessionTokenVerifier.verify(...) != null`) |
| 7 | `/v1/proposals/{proposalId}/accept` | POST | none (body optional) | `Bearer` token from step 3, `verified.drv == proposal.driver` (403 otherwise) |
| 8 | `/v1/assignments/{assignmentId}/arrive` | POST | none | `Bearer` token from step 3, `verified.drv == assignment.driver` (403 otherwise) |
| 9 | `/v1/assignments/{assignmentId}/start` | POST | none | same |
| 10 | `/v1/assignments/{assignmentId}/complete` | POST | none | same |
| 11 | `/v1/orders` | POST | `passengerReference` | None (accepted risk, again) |
| 12 | `/v1/orders/{orderId2}/cancel` | POST | none | `Bearer` token from step 3, `verified.sub == order.origin.reference` (403 otherwise) |

A `decline` variant of step 7 (`/v1/proposals/{proposalId}/decline`, identical auth shape) exercises the decline path instead of accept, on a **third**, separate order — needed only if the plan is to exercise both accept and decline in one run; not included in the main sequence above to keep it linear, but uses exactly the same identities and the same pattern.

## 4. Test entities → `isTest` propagation, verified in source

| Entity | Field carrying `isTest` | Set at | Propagates to |
|---|---|---|---|
| `Order` | `orders.is_test` (migration `V10__add_order_is_test.sql`, applied — order-management is at V10) | `SubmitOrderRequest.isTest` → `OrderSubmissionRequestHandler.handle` → `Order.isTest` | `OrderSubmitted` domain event's own `isTest` field, carried onward to dispatch's outbox consumer |
| `Proposal` | `proposals.is_test` (`V11__add_proposal_and_assignment_is_test.sql`, applied — dispatch is at V11) | `ProposeDriverRequest.isTest` (manual) **or** `OrderSubmittedFirstRefusalListener`'s own consumption of the event's `isTest` (automatic First Refusal path — confirmed by its own integration test, `"isTest is carried from the event through to the created proposal"`) | `Assignment.isTest`, on accept |
| `Assignment` | `assignments.is_test` (same V11) | Copied from the accepted `Proposal.isTest` at creation (`ProposalAssignmentOrchestrationService`) | Nothing further — terminal |
| `Driver` | `drivers.is_test` (`V5__add_driver_is_test.sql`, applied — driver-management is at V5) | `CreateDriverRequest.isTest`, set once at creation, never changes | Not propagated onward — a driver is not consumed by anything that re-derives `isTest` |
| **`Identity`/`Credential`** | **No `is_test` column exists at all** — confirmed by reading `Identity.kt`/the `identity` migrations directory; `IdentityController` has no such field anywhere | N/A | N/A — see §5, this is the one place isolation is not code-enforced |

## 5. What is, and is not, isolated — verified by reading the actual consumer code, not assumed

**Excluded from owner analytics — verified for all four entity types that carry `isTest`:**
`frontend/src/pages/OwnerControlCenter/todayData.ts`'s `excludeTestData()` (`items.filter(item => !item.isTest)`) is applied to `drivers`, `orders`, `proposals`, and `assignments` in **both** `todayData.ts` (the owner's live "today" dashboard) **and** `pilotAnalytics.ts` (the AI Advisor's own input) — confirmed by reading both call sites (`todayData.ts:262-276`, `pilotAnalytics.ts:430-446`). A `isTest: true` order/proposal/assignment/driver cannot appear in any owner-facing count, list, or AI-generated summary.

**NOT filtered by `DriverHome.tsx`, the real driver's own device:** confirmed by a direct search of that file — zero references to `isTest` anywhere. `GET /v1/proposals?driverId=...` returns `isTest: true` and real proposals identically, with no marker. **This is why the plan above never uses a real `driverId` anywhere** — the isolation this smoke test relies on for the driver side is "no real device is polling for this synthetic `driverId`," not "the app would hide it if it were." This is stated explicitly, per this task's own critical rule, rather than assumed.

**No notification channel exists to leak this to anyone regardless:** repository-wide search of `backend/` for any SMS/push/notification-sending code (`Twilio`, `sendSms`, `SmsSender`, `PushNotification`, `NotificationService`) — zero matches anywhere in this codebase. Even a mistakenly-real `driverId` would not proactively alert anyone; it would only be visible to a device actively polling `GET /v1/proposals?driverId=<that id>` at that moment. This is not relied upon by the plan (which never uses a real id), but is recorded as an additional, independently-verified layer.

**Identity has no isolation mechanism of any kind** — this is the one place this investigation's own critical rule applies directly: `isTest` does not exist for `Identity`/`Credential` rows. The synthetic passenger created in step 1 is, structurally, indistinguishable from a real pilot passenger's own registration row, except by the phone number itself being obviously synthetic to a human reader. **This is not a gap this plan works around — it is stated as a real, permanent consequence**: step 1 creates one real row in `pios_identity.identities` and one in `pios_identity.credentials` that will persist until someone manually removes them (§9).

## 6. Production contamination — what is actually written, and where it is not hidden

| Table | Rows written by this plan | Hidden from owner analytics? | Hidden from a real driver's device? |
|---|---|---|---|
| `pios_identity.identities` | 1 (synthetic passenger) | N/A — no analytics screen reads raw identity rows | N/A |
| `pios_identity.credentials` | 1 | N/A | N/A |
| `pios_driver_management.drivers` | 1 (`isTest=true`) | Yes | Not applicable — the driver row itself isn't "seen," only proposals naming it are, and none ever will be by a real device |
| `pios_order_management.orders` | 2 (`isTest=true`) | Yes | N/A (orders aren't shown to drivers directly) |
| `pios_dispatch.proposals` | 2–3 (`isTest=true`) | Yes | Not shown to any real driver — only ever queried by the synthetic `driverId` |
| `pios_dispatch.assignments` | 1 (`isTest=true`) | Yes | Same |

**No row written by this plan is ever readable by a real pilot participant through any UI this codebase currently ships**, because none of it is ever addressed to a real `driverId` or displayed by any screen that doesn't already filter `isTest`. The one exception (identity rows, §5) is not "hidden" at all — it is a real, permanent, un-flagged row, disclosed here rather than glossed over.

## 7. `POST /v1/orders` ownership gap — reconfirmed, untouched

`OrderSubmissionController.submitOrder` (read again for this gate) takes no `Authorization` header parameter at all — `passengerReference` is a caller-supplied string with no verification against any authenticated identity. This smoke test's own step 5/11 rely on exactly this gap (no token is sent, per the endpoint's own actual contract) — it is not something this plan works around or is blocked by, because it was never enforced in the first place. **This remains an accepted, documented, untouched risk** — no code was read with intent to fix it, no fix is proposed here, and this gate does not expand scope to include it. Carried forward unchanged from every prior gate in this arc.

## 8. Real people/accounts that could be affected

**None**, provided the plan is followed exactly:
- The passenger identity is freshly created, synthetic, phone-format-only (never contacted).
- The driver is freshly created, synthetic, `isTest: true`, an unguessable id.
- No existing pilot driver, passenger, order, proposal, or assignment is read, referenced, modified, or deleted.
- No notification channel exists in this codebase that could alert a real person regardless.

## 9. PASS / FAIL criteria

| Step | PASS | FAIL |
|---|---|---|
| 1 | `201`, response has `token`, `identityId`; token never logged | Any other status; or a stack trace matching `pios.session.secret must be configured` (§ pre-req 1 not actually met) |
| 2 | `201`, `isTest: true` in response | `409` (id collision — regenerate) |
| 3 | `201`, fresh token returned | `401`/`403`/`404` |
| 4 | `200`, `availability: AVAILABLE` | `401`/`403` (secret mismatch between identity and driver-management — see §"Authentication verification" below) |
| 5 | `201`, `orderId` returned | any 4xx/5xx |
| 6 | `201`, `status: OPEN` | `401` (would mean the very regression this arc's own `92953ed` fix targets has reappeared) / `409` (availability check failed — step 4 didn't take) |
| 7 | `200`, `status: ACCEPTED`; `GET /v1/assignments?orderId=` now shows one row | `401`/`403`/`404`/`409` |
| 8–10 | `200`, status progresses `ARRIVED → IN_PROGRESS → COMPLETED` in order | any status skipped, any 401/403/404/409 |
| 11 | `201` | any 4xx/5xx |
| 12 | `200`, `status: CANCELLED` | `401`/`403`/`404`/`409` |
| Cross-cutting | A `GET /v1/proposals/{id}/accept`-style call **with no `Authorization` header at all**, run once against any of steps 6/7/8/9/10/12, returns `401` before any lookup; the same call with a *valid but wrong-owner* synthetic token (a second throwaway identity, no driver association) returns `403`/`404` per each controller's own documented ordering (§6 of `docs/PIOS_FINAL_GO_NO_GO_GATE.md`) | Any of these returns `200`/`201` — would mean an authorization check was bypassed; **stop immediately, do not continue the sequence, this is a hard failure of the RC itself, not of the test** |

## 10. Rollback / cleanup procedure (for after a real execution — not run by this gate)

1. **Before any cleanup, record the exact rows created**: the synthetic `identityId`, `driverId`, both `orderId`s, all `proposalId`s, the `assignmentId`. This gate did not create any, so nothing to record yet.
2. **`orders`/`proposals`/`assignments`/`drivers`**: no delete endpoint exists in the API for any of these. Cleanup, if desired, is a direct database operation — follow Task 28's own already-validated pattern exactly: export the exact rows to a local CSV backup first, then `DELETE ... WHERE <exact id> AND is_test = true` (the `is_test = true` guard is not optional — it is what makes the statement incapable of ever touching a real row even by mistake), verify the deleted row count matches the backup's row count exactly, re-run the owner-analytics fetch afterward to confirm no drift.
3. **Alternative to deletion: leave the rows in place.** Since every one of them is `isTest: true` and therefore invisible to owner analytics (§6), leaving them is a legitimate, lower-risk choice — the only cost is a few permanent rows in the production tables, clearly self-tagged.
4. **`identities`/`credentials` (the one un-flagged case, §5): cannot be left ambiguous.** Since there is no `isTest` marker and no delete endpoint, the operator must explicitly decide, before this plan is ever executed for real, whether to (a) delete the synthetic identity/credential rows via direct, backed-up `DELETE` afterward (same pattern as above), or (b) accept one permanent, clearly-synthetic-by-phone-number identity row in production indefinitely. **This decision is not made by this gate — see §11.**
5. **No service restart, migration, or code change is part of cleanup** — cleanup is data-only, exactly mirroring Task 28's own "Production Safety Statement" shape.

## 11. Steps that still require a manual operator decision

1. **Commit the `pios.session.secret` mechanism documentation** (`docs/PIOS_SESSION_SECRET_MECHANISM_INVESTIGATION.md`'s own recommendation) before this plan is executed for real — not something this design gate can do unilaterally (it is a documentation/architecture decision, and this session does not commit or push without being asked to).
2. **Decide the identity-row cleanup policy** (§10 item 4) — delete afterward, or accept one permanent synthetic row. No code change either way; a one-line operator decision this gate cannot make on its own.
3. **Confirm the actual deployment has happened** before this sequence is run — this plan assumes a live RC; it is not itself a deployment trigger.
4. **`network-management` — reconfirmed excluded, no decision needed**: not part of the seven RC commits, not currently running (`Get-Service pios-*` has never listed it across this entire arc), and excluded from pilot scope by ADR-037. This plan's own service dependency is exactly the five modules in §3's endpoint list (`identity`, `driver-management`, `dispatch`, `order-management`) plus `passenger-experience` only insofar as it shares the session-verification mechanism (not itself called by any step above). Nothing in this plan requires `network-management` to be running, and it should not be started for this test.

---

## Summary

| Question | Answer |
|---|---|
| GO/NO-GO to execute | **GO**, contingent on the two prerequisites in the header, neither of which this gate is authorized to resolve itself |
| Real people/accounts affected | **None**, if followed exactly |
| Where isolation is code-enforced vs. operator-discipline-only | Code-enforced: owner analytics (all 4 `isTest` entity types). Operator-discipline-only: never using a real `driverId` (no code prevents it); identity/credential rows have no `isTest` concept at all |
| `POST /v1/orders` | Untouched, accepted risk, reconfirmed |
| `network-management` | Confirmed out of scope, not part of this plan |

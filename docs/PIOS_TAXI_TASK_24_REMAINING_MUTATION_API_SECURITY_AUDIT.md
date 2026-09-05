# PIOS Taxi — Task 24: Remaining Mutation API Security Audit

Status: **Read-only, repository-wide audit. No code, test, migration, configuration, ADR, or documentation was changed, except this report.** No production or RabbitMQ connection was made. Every backend module's own REST controllers were read directly; nothing here is inferred from a prior task's own report without independent verification.

## 1. Executive Summary

Every mutation HTTP endpoint (`POST`/`PUT`/`PATCH`/`DELETE`) across all seven backend modules (Dispatch, Order Management, Passenger Experience, Identity, Network Management, Driver Management, ai-advisor) was inventoried and traced from controller through application service to domain. No `PUT` or `PATCH` mapping exists anywhere in this repository — every mutation is `POST` or, in one case, `DELETE`.

**Two new, real, HIGH-severity gaps were found**, both structurally identical to the Proposal/Assignment vulnerability Tasks 20–23 already fixed, and both reachable through the same, already-deployed public Vite proxy Task 20 first documented:

- **`POST /v1/orders/{orderId}/cancel`** (Order Management) — no authentication at all; any caller who knows an `orderId` can cancel any passenger's order.
- **`POST /v1/drivers/{driverId}/availability`** (Driver Management) — no authentication at all; any caller who knows a `driverId` can toggle any driver's own availability, a signal that directly feeds automatic First Refusal eligibility and Coordinator's own driver list.

One already-known, already-disclosed class of gap was reconfirmed, not newly discovered: **`POST /v1/orders`** (Order Management, order creation) does not verify the caller owns the `passengerReference` they submit — the exact same shape of gap Task 20/21 already found and deliberately deferred for `POST /v1/proposals`, for the same reason (no architectural decision yet establishes how to verify that ownership).

Everything else audited — Passenger Experience's `ConnectionController` (all 5 endpoints), Identity's `IdentityController`, ai-advisor's `AdvisorController`, and Network Management's five endpoints — is either already correctly secured or has no real-world reachability today (Network Management is undeployed and explicitly excluded from the public proxy). Task 21's Proposal remediation and Task 23's Assignment remediation were re-run and remain fully effective, unmodified.

**Conclusion: FAIL — remediation required** (Order Management, Driver Management), narrowly scoped, no STOP condition, no new ADR needed for either HIGH-severity finding.

## 2. Complete Endpoint Inventory

Found by `grep -rn "@PostMapping|@PutMapping|@PatchMapping|@DeleteMapping|@RequestMapping"` across every `main` source tree in `backend/`, cross-checked against `@RestController` classes (`ai-advisor` included separately, Section 12).

| Module | Method | Path | Controller |
|---|---|---|---|
| Dispatch | POST | `/v1/assignments` | `AssignmentController.assignOrder` (deprecated) |
| Dispatch | POST | `/v1/assignments/{id}/arrive` | `AssignmentController.arrive` |
| Dispatch | POST | `/v1/assignments/{id}/start` | `AssignmentController.start` |
| Dispatch | POST | `/v1/assignments/{id}/complete` | `AssignmentController.complete` |
| Dispatch | POST | `/v1/proposals` | `ProposalController.createProposal` |
| Dispatch | POST | `/v1/proposals/{id}/accept` | `ProposalController.acceptProposal` |
| Dispatch | POST | `/v1/proposals/{id}/decline` | `ProposalController.declineProposal` |
| Dispatch | POST | `/v1/proposals/{id}/lapse` | `ProposalController.lapseProposal` |
| Driver Management | POST | `/v1/drivers` | `DriverController.createDriver` |
| Driver Management | POST | `/v1/drivers/{id}/availability` | `DriverController.declareAvailability` |
| Identity | POST | `/v1/identities/register` | `IdentityController.register` |
| Identity | POST | `/v1/identities/login` | `IdentityController.login` |
| Identity | POST | `/v1/identities/{id}/driver` | `IdentityController.associateDriver` |
| Network Management | POST | `/v1/connections` | `ConnectionController.createConnection` |
| Network Management | POST | `/v1/invitations` | `InvitationController.createInvitation` |
| Network Management | POST | `/v1/invitations/{code}/accept` | `InvitationController.acceptInvitation` |
| Network Management | POST | `/v1/persons` | `PersonController.createPerson` |
| Network Management | POST | `/v1/profiles` | `ProfileController.createProfile` |
| Order Management | POST | `/v1/orders` | `OrderSubmissionController.submitOrder` |
| Order Management | POST | `/v1/orders/{id}/cancel` | `OrderCancellationController.cancelOrder` |
| Passenger Experience | POST | `/v1/connections` | `ConnectionController.createConnection` |
| Passenger Experience | POST | `/v1/connections/{id}/primary` | `ConnectionController.setPrimaryConnection` |
| Passenger Experience | DELETE | `/v1/connections/{id}` | `ConnectionController.removeConnection` |
| ai-advisor | POST | `/v1/advisor/analyze` | `AdvisorController.analyze` |

24 mutation endpoints total. No `PUT`/`PATCH` mapping exists anywhere in the repository (confirmed by the same `grep`, zero matches).

## 3. Authentication Matrix

| Endpoint | Mechanism |
|---|---|
| `assignOrder` | **None** |
| `arrive`/`start`/`complete` | `SessionTokenVerifier` (Task 23) |
| `createProposal`/`acceptProposal`/`declineProposal` | `SessionTokenVerifier` (Task 21) |
| `lapseProposal` | `OwnerCredentialGate` (Task 21) |
| `DriverController.createDriver` | **None** |
| `DriverController.declareAvailability` | **None** |
| `IdentityController.register`/`login` | None (correct — pre-authentication) |
| `IdentityController.associateDriver` | `SessionTokenVerifier` |
| Network Management, all 5 | **None** |
| `OrderSubmissionController.submitOrder` | **None** |
| `OrderCancellationController.cancelOrder` | **None** |
| Passenger Experience `ConnectionController`, all 3 mutations | `SessionTokenVerifier` |
| `AdvisorController.analyze` | `OwnerCredentialGate` |

## 4. Authorization Matrix

| Endpoint | Resource-owner check | Basis |
|---|---|---|
| `arrive`/`start`/`complete` | `verified.drv == assignment.driver.driverId` | Task 23 |
| `createProposal` | Any authenticated caller (deliberately no ownership check — Task 20/21's own disclosed, deferred gap) | Task 21 |
| `acceptProposal`/`declineProposal` | `verified.drv == proposal.driver.driverId` | Task 21 |
| `lapseProposal` | Owner credential only | Task 21 |
| `DriverController.createDriver` | None needed — creates a new resource named by a caller-generated, unguessable UUID; no existing resource to own (Section 9) |
| `DriverController.declareAvailability` | **None — missing** | This audit |
| `IdentityController.associateDriver` | `verified.sub == id` | Pre-existing (ADR-055) |
| `OrderSubmissionController.submitOrder` | None (same class as `createProposal`'s own disclosed gap) | This audit, cross-referencing Task 20/21 |
| `OrderCancellationController.cancelOrder` | **None — missing** | This audit |
| Passenger Experience `createConnection`/`setPrimaryConnection`/`removeConnection` | `verified.sub == passengerReference`/connection's own owner | Pre-existing (ADR-055 Decision 4) |
| `AdvisorController.analyze` | Owner credential only (no per-resource ownership concept applies) | Pre-existing |

## 5. Real Caller / Call-Graph Analysis

Traced by reading every application-service method each controller calls, and searching the whole `backend/**/src/main` tree for any other caller of that same method:

| Application-service method | Callers found |
|---|---|
| `DispatchAssignmentApplicationService.arriveAssignment`/`startAssignment`/`completeAssignment` | `AssignmentController` only (re-confirmed, Task 23's own finding unchanged) |
| `DispatchAssignmentApplicationService.handle`/`handleWithinCallerTransaction` | `AssignmentController.assignOrder` (dormant) and `ProposalAssignmentOrchestrationService.acceptProposal` (internal, already transitively protected by Task 21 — unchanged finding) |
| `ProposalApplicationService.handle` | `ProposalController.createProposal` and `FirstRefusalApplicationService.attempt` (internal, in-process, never through HTTP — unchanged finding, re-confirmed) |
| `DriverAvailabilityApplicationService.handle` | `DriverController.declareAvailability` only — **no internal caller of any kind** |
| `CreateDriverApplicationService.handle` | `DriverController.createDriver` only |
| `OrderLifecycleApplicationService.cancelOrder` | `OrderCancellationController.cancelOrder` only — **no internal caller of any kind** |
| `OrderSubmissionRequestHandler.handle` | `OrderSubmissionController.submitOrder` only |

## 6. Public Reachability Analysis

Read directly from `frontend/vite.config.ts`'s own `preview.proxy` table (the mechanism Task 20 first used to establish this):

```
'/v1/drivers': 'http://localhost:8081'        -- Driver Management, PUBLIC
'/v1/connections': 'http://localhost:8082'    -- Passenger Experience, PUBLIC
'/v1/orders': 'http://localhost:8083'         -- Order Management, PUBLIC
'/v1/proposals': 'http://localhost:8084'      -- Dispatch, PUBLIC
'/v1/assignments': 'http://localhost:8084'    -- Dispatch, PUBLIC
'/v1/identities': 'http://localhost:8086'     -- Identity, PUBLIC
'/v1/advisor': 'http://localhost:8091'        -- ai-advisor, PUBLIC
```

**Network Management's own `/v1/connections` (port 8085) is explicitly absent from this table** — the config file's own comment states why: *"intentionally not routed here; it is excluded from the pilot flow (ADR-037)."* This is a deliberate, ratified exclusion, not an oversight, and it is corroborated independently (Section 10): the module has no WinSW service definition at all (re-confirmed this session via `windows-services/` directory listing, a local file check, not a production connection), so it is not running anywhere reachable, publicly or otherwise, today.

Every other module's mutation surface is reachable at `https://piosapp.ru/<path>` (and the Tailscale Funnel hostname) through the already-deployed pilot tunnel + this proxy table — including, concretely, `POST https://piosapp.ru/v1/orders/{orderId}/cancel` and `POST https://piosapp.ru/v1/drivers/{driverId}/availability`, both fully unauthenticated (Section 3).

## 7. Cross-User/Cross-Driver Attack Scenarios

- **Cross-passenger order cancellation.** Passenger B, knowing or otherwise obtaining Passenger A's `orderId` (e.g. a shared confirmation screenshot, a support conversation, a browser history entry on a shared device), can call `POST /v1/orders/{A's orderId}/cancel` with no credential at all and cancel A's active ride. Order Management's own `OrderNotFoundException`/409 handling means the caller even receives clean confirmation of success.
- **Cross-driver availability sabotage.** Any caller who knows a driver's own `driverId` (visible in that driver's own public invitation link, `/i/:driverCode` — deliberately public, per the entrepreneur model, Task 18/19) can call `POST /v1/drivers/{that driverId}/availability` with `{"availability": "UNAVAILABLE"}` and no credential, silently taking a competitor (or any driver) off the line — or the reverse, marking a driver `AVAILABLE` who is not, which (once Task 19's own First Refusal deployment eventually happens) could cause an automatic Proposal to be routed to a driver who never declared themselves ready.
- **Order creation impersonation** (same class as Proposal's own disclosed gap, not new): any caller can submit `POST /v1/orders` naming any `passengerReference`, creating an order that will show up under a different person's own passenger identity in every downstream screen that reads `origin`.

## 8. Application-Service Bypass Analysis

No internal/in-process bypass exists for either of the two HIGH-severity findings (Section 5): `DriverAvailabilityApplicationService.handle` and `OrderLifecycleApplicationService.cancelOrder` are each called from exactly one place, their own controller. A controller-level fix for either carries zero risk of breaking an internal caller — the same favorable shape Task 23 found for Assignment's own ride-progress methods, and unlike Proposal's `create`, which First Refusal genuinely does call internally and must keep working unauthenticated at the application-service layer (re-confirmed unchanged this session, Section 5's own table).

## 9. Task 21/23 Regression Verification

Re-ran, unmodified: `ProposalControllerTest` (63/63), `AssignmentControllerTest` (25/25), `AssignmentControllerPostgreSQLSecurityTest` (5/5) — all green, no regression. `ProposalController.kt` and `AssignmentController.kt` were read again in full this session and both still carry exactly the checks Tasks 21/23 added (`sessionTokenVerifier.verify`: 4 call sites in `ProposalController`, 3 in `AssignmentController`, both counts matching those tasks' own reports); `git status` confirms neither file has been modified since Task 23. Both remediations remain fully effective.

## 10. `assignOrder` Detailed Assessment

Re-audited, per this task's own explicit instruction, independent of Task 22/23's own prior conclusion:

- **Authentication:** none.
- **Authorization:** none — and, distinct from `arrive`/`start`/`complete`, there is no single "correct" caller identity to check against, since this endpoint's own purpose is to *assign* a driver, not act as one (mirrors `createProposal`'s own shape, not `acceptProposal`'s).
- **Real callers:** re-confirmed, by a fresh repository-wide search of `frontend/src` for the literal path `'/v1/assignments'` (excluding the `/{id}/...` sub-paths), zero matches. `Coordinator.tsx`'s own manual-assignment flow uses `POST /v1/proposals`, not this endpoint (re-read this session, unchanged since Task 22).
- **Publicly reachable:** yes, technically (`/v1/assignments` is proxied), but with no real caller to exploit through it today beyond an attacker directly crafting a request.
- **Severity:** **LOW** — real gap, zero current blast radius. Classified this way per this task's own explicit instruction not to assume an endpoint is dangerous merely because it lacks authentication when it has no real external caller — this is exactly that case, verified, not assumed.
- **Recommended remediation, if ever addressed:** owner-credential gating, mirroring `createProposal`'s own owner-credential branch (Task 21) — `OwnerCredentialGate` is already available in `AssignmentController`'s own module, though not yet injected into that specific class.

## 11. Identity Assessment

**No finding.** `register`/`login` are correctly, deliberately unauthenticated (nothing to authenticate against before an identity or credential exists). `associateDriver` and `getIdentity` require `verified.sub == id` — a caller may only ever act on their own identity. `getOwnIdentity` derives the identity from the token alone, accepting no caller-supplied id at all. This module's own `ConnectionController`-equivalent precedent (Task 20's own source, actually predating it — ADR-055 Decision 3/4) is the same one Task 20/21 cited as the established pattern; re-read this session and found unchanged and correct.

## 12. Order Management Assessment

**Two findings, one new (HIGH), one reconfirmed (MEDIUM, deferred).**

- **`OrderCancellationController.cancelOrder` — HIGH, new.** No authentication, no authorization, real caller (`RideRequest.tsx`'s `handleCancelOrder`, confirmed by direct read) already holds `identity.token` in the same component and simply does not send it. `OrderQueryController`, in the same module, already has both `OwnerCredentialGate` and `SessionTokenVerifier` injected and correctly used (ADR-060) — the identical infrastructure this fix would need already exists one file away.
- **`OrderSubmissionController.submitOrder` — MEDIUM, reconfirmed, not new.** No verification that the caller owns the `passengerReference` they submit. This is the exact same shape of gap Task 20 named for `ProposalController.createProposal` and Task 21 deliberately left open, for the same reason: `Order` (like `Proposal`) has no established mechanism today for verifying a caller's token subject against a caller-supplied reference field at creation time beyond the pattern Passenger Experience's `createConnection` already uses (`request.passengerReference != verified.sub`) — which Order Management *could* adopt (the shape is identical: compare `request.passengerReference` to `verified.sub`), but doing so was explicitly out of Task 20/21's own scope and this audit does not expand that scope either; it only re-confirms the same finding exists a second time, in a second module, with the same fix shape available.

## 13. Passenger Experience Assessment

**No finding.** `ConnectionController`'s all five endpoints (`createConnection`, `getConnectionsForDriver`, `getConnectionsForPassenger`, `setPrimaryConnection`, `removeConnection`) require a verified `Bearer` token and compare the relevant claim (`sub` or `drv`) against the resource's own stored owner, re-read in full this session and found unchanged and correct — including the deliberate 404-not-403 choice on `setPrimaryConnection` (avoids confirming a `connectionId` exists to a non-owner) and the deliberate always-204 choice on `removeConnection` (preserves idempotent-DELETE semantics without leaking ownership). This remains the strongest-secured controller in the entire repository and the correct model for Order Management's own deferred fix (Section 12).

## 14. Network Management Assessment

**Five findings (`createConnection`, `createInvitation`, `acceptInvitation`, `createPerson`, `createProfile`), all unauthenticated at the controller level — classified LOW, not urgent, per real reachability, not assumption.** The module has no WinSW service (re-confirmed this session, local file listing only) and its own `/v1/connections` is explicitly excluded from the public Vite proxy by name, citing ADR-037 (Section 6). This is a deliberately deferred module (Task 18's own already-established finding, re-confirmed here): nothing in it is reachable by any real user today. If and when Network Management is ever deployed as part of a later, separate, explicitly-authorized product decision, every one of these five endpoints will need the same class of fix this report documents elsewhere — noted for that future task, not urgent now.

## 15. Owner/Admin Assessment

**No finding.** `AdvisorController.analyze` (ai-advisor) is the only dedicated Owner/Admin mutation endpoint in the repository; it requires `OwnerCredentialGate.verify`, checked first, before any budget or provider logic runs — re-read in full this session and found correct. `platform-ops` has only a `HealthController` (no mutation endpoint). The Coordinator/owner pattern already established across `ProposalController.listProposalsForDriver`/`createProposal` (owner branch) and `OrderQueryController` (ADR-060) is consistently applied everywhere it appears.

## 16. Severity-Ranked Findings

| # | Endpoint | Severity | Real exposure today |
|---|---|---|---|
| 1 | `OrderCancellationController.cancelOrder` | **HIGH** | Publicly reachable, real caller already exists, no auth at all |
| 2 | `DriverController.declareAvailability` | **HIGH** | Publicly reachable, real caller already exists, no auth at all, feeds core marketplace signal |
| 3 | `OrderSubmissionController.submitOrder` (passenger-ownership) | **MEDIUM** | Same class as an already-disclosed, deliberately deferred gap (Task 20/21) |
| 4 | `AssignmentController.assignOrder` | **LOW** | No real caller (Section 10) |
| 5 | `DriverController.createDriver` | **LOW / informational** | Pre-auth "first contact" action, unguessable UUID-shaped resource id, mirrors `IdentityController.register`'s own legitimate openness |
| 6 | Network Management, 5 endpoints | **LOW** | Module undeployed, explicitly excluded from public proxy |

## 17. Pre-Pilot Required Fixes

**Findings #1 and #2 (Section 16)** — both structurally identical, low-risk, well-precedented fixes: reuse `SessionTokenVerifier` (already present in Order Management via `OrderQueryController`; would need a new, but fully precedented, replicated copy in Driver Management, mirroring the same class already present in four other modules), compare the verified claim against the resource's own established owner (`order.origin`/`assignment.driver.driverId`-equivalent — Section 18/19 name the exact field), 401/403/404 in the same order Tasks 21/23 already established. Neither requires a new ADR (Section 20) or a new product decision. Recommended as the next implementation task's own scope (Section 21).

## 18. Deferred Fixes

- **Finding #3** (`submitOrder` passenger-ownership) — deferred, consistent with Task 20/21's own precedent for the identical Proposal-side gap; resolving it for Order Management alone, without also resolving it for Proposal, would be an inconsistent, piecemeal fix to what is really one architectural question (Section 20).
- **Finding #4** (`assignOrder`) — deferred, per Task 22's own already-recorded recommendation, still low priority given zero real callers.
- **Finding #6** (Network Management) — deferred until the module is actually deployed, a separate, not-yet-made product/architecture decision.

## 19. Architecture/Product Decisions Required, If Any

**None block the two pre-pilot fixes (Section 17).** One decision remains open, already named by Task 20/21 and only reconfirmed, not newly raised, here: **whether and how to verify that a caller-supplied `passengerReference` (on both `POST /v1/proposals` and `POST /v1/orders`) actually belongs to the authenticated caller**, as a single, consistent, cross-module policy — Passenger Experience's own `ConnectionController` already demonstrates the shape such a fix would take, but adopting it for Order Management and/or Dispatch was explicitly out of scope for the tasks that found each half of this gap, and remains out of scope for this audit too. No STOP condition fired over this — it does not block the two HIGH-severity fixes, which have unambiguous, source-established ownership (Section 4).

## 20. Exact Recommended Next Task(s)

**Task 25 — Order Cancellation & Driver Availability Security Remediation.** Scope: apply the exact Task 21/23 pattern to `OrderCancellationController.cancelOrder` (reusing Order Management's own already-present `SessionTokenVerifier`/`OwnerCredentialGate`, comparing `verified.sub` against the order's own `origin` reference — the same field `OrderQueryController` already reads for its own owner-scoped query mode) and to `DriverController.declareAvailability` (adding a new, replicated `SessionTokenVerifier` to Driver Management, mirroring the four existing copies, comparing `verified.drv` against the path's own `driverId`). New tests mirroring `ProposalControllerTest`/`AssignmentControllerTest`'s own shape; full regression per this chain's own established discipline (isolated test infrastructure only). Explicitly out of scope for that task: `submitOrder`'s own passenger-ownership question (Section 19 — a separate, cross-module architectural decision), `assignOrder`, and Network Management.

## 21. Files Inspected

`AssignmentController.kt`, `ProposalController.kt` (both re-read, Task 21/23 verification), `DriverController.kt`, `IdentityController.kt`, `ConnectionController.kt` (both Network Management and Passenger Experience variants), `InvitationController.kt`, `PersonController.kt`, `ProfileController.kt`, `OrderCancellationController.kt`, `OrderQueryController.kt` (grep for its own auth wiring), `OrderSubmissionController.kt`, `AdvisorController.kt`, `DispatchAssignmentApplicationService.kt`/`ProposalApplicationService.kt`/`DriverAvailabilityApplicationService.kt`/`CreateDriverApplicationService.kt`/`OrderLifecycleApplicationService.kt` (application-service caller searches), `frontend/vite.config.ts`, `DriverHome.tsx`, `RideRequest.tsx`, `Coordinator.tsx` (re-checked), plus repository-wide `grep` searches across every `backend/**/src/main` tree and `frontend/src`.

## 22. Commands/Checks Executed

- `grep -rn "@PostMapping|@PutMapping|@PatchMapping|@DeleteMapping|@RequestMapping"` across `backend/` (endpoint inventory, Section 2).
- `grep -rln "@RestController"` across `backend/` and `ai-advisor/` (controller inventory).
- Per-application-service-method repository-wide `grep` searches (Section 5, 8).
- `grep -rn "'/v1/..."` across `frontend/src` for each newly-audited endpoint (caller inventory, Section 7).
- `find windows-services/` (local file listing only — confirms no Network Management service, re-verifying Task 18's own finding without a production connection).
- `./gradlew.bat :dispatch:test --tests "ProposalControllerTest" --tests "AssignmentControllerTest" --tests "AssignmentControllerPostgreSQLSecurityTest"` — regression re-verification (Section 9), against the isolated `pios_dispatch_test` database only.
- `git status --short` before and after — confirms zero files modified by this task besides this report.

## 23. Production-Touch Statement

- Production PostgreSQL touched: **NO**.
- Production RabbitMQ touched: **NO**.
- Production services restarted: **NO**.
- Production deployment performed: **NO**.
- Commit made: **NO**.
- Push performed: **NO**.

## 24. Final Conclusion

**FAIL — remediation required.**

Two new, real, HIGH-severity findings (`OrderCancellationController.cancelOrder`, `DriverController.declareAvailability`) join the class Tasks 20–23 already closed for Proposal and Assignment — same shape, same low-risk fix, no internal bypass to protect, no STOP condition, no new ADR. Everything else audited is either already correctly secured (Passenger Experience, Identity, ai-advisor) or has no real-world exposure today (the deprecated `assignOrder`, undeployed Network Management). Task 21's and Task 23's own remediations were re-verified and remain fully effective. Recommended next step: Task 25, scoped exactly as Section 20 describes — not implemented here, per this task's own instruction.

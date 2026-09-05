# PIOS Taxi — Task 25: Orders Cancellation & Driver Availability Security Remediation

Status: **Implemented.** Closes exactly the two HIGH-severity findings Task 24 identified — `POST /v1/orders/{orderId}/cancel` and `POST /v1/drivers/{driverId}/availability` — reusing the identical `SessionTokenVerifier`/ownership-comparison pattern Tasks 21 and 23 already established. No new authentication mechanism, no new ADR, no production or RabbitMQ connection, no production service restarted, nothing committed or pushed.

## 1. Pre-Implementation Findings (Phase 0)

Re-read directly from current source, independent of Task 24's own report, before changing anything:

- **`OrderCancellationController.kt`** — confirmed constructor held only `OrderLifecycleApplicationService`, no identity collaborator; confirmed `cancelOrder` performed no auth check of any kind.
- **`OrderLifecycleApplicationService.cancelOrder`** — confirmed two overloads: a self-fetching one (`cancelOrder(command)`, restores the `Order` via `orderRepository` internally) and an instance one (`cancelOrder(order, command)`); confirmed `OrderRepository.findById` is available and injectable directly into a controller, mirroring `ProposalController`'s own `proposalRepository` pattern (Task 21).
- **`OrderOrigin`** — confirmed a plain `value class OrderOrigin(val reference: String)`; `Order.origin.reference` is the exact field to compare.
- **`OrderQueryController.kt`** (same module) — re-read in full. **This is the key finding of Phase 0**: ADR-060 Decision 2 already states, in ratified architecture, *"`Order.origin` is, from this ADR forward, the authenticated passenger's `identityId`"* — and `OrderQueryController`'s own `?passengerReference=` mode already performs the exact comparison this task needed (`passengerReference != verified.sub`). This means the ownership model this task needed was **not invented** — it already exists, ratified, one file away, and this task's own fix is a direct reuse of it, not a new interpretation.
- **`DriverController.kt`** — confirmed constructor held `RetrieveDriverAvailabilityHandler`, `DriverAvailabilityApplicationService`, `CreateDriverApplicationService` — no identity collaborator; confirmed `declareAvailability` performed no auth check.
- **`driver-management`'s own module** — confirmed, by directory listing, it holds `OwnerCredentialGate.kt` but **no `SessionTokenVerifier.kt`** — the only one of the five verifying modules without one. Creating it is a new file, but not a new mechanism (Section 4).
- **Call-graph / internal-bypass check, re-run fresh**: `grep -rn "\.cancelOrder("` across `order-management/src/main` → exactly one match, `OrderCancellationController` itself. `grep -rn "driverAvailabilityApplicationService.handle"` across `driver-management/src/main` → exactly one match, `DriverController` itself. **No internal caller exists for either** — confirming Task 24's own finding and clearing this task's own STOP condition ("an existing internal caller legitimately requires unauthenticated access") for both endpoints.
- **Frontend callers, re-confirmed**: `RideRequest.tsx`'s `handleCancelOrder` and `DriverHome.tsx`'s `toggleAvailability` are each the sole real caller of their endpoint, and each already holds `identity.token` in the same component scope without sending it.
- **Owner/admin cancellation, checked, not assumed**: re-read `Coordinator.tsx` in full — it calls only `GET /v1/orders`, `GET /v1/drivers`, `GET /v1/proposals?orderId=`, and `POST /v1/proposals` (already secured, Task 21); it never calls `/cancel`. No owner/admin order-cancellation flow exists anywhere in this codebase today. Per this task's own instruction to "preserve any legitimate Owner Basic/admin behavior **already established**," this means there is nothing to preserve for cancellation specifically — adding an owner-`Basic` bypass branch would have been inventing a capability, not preserving one, so none was added (Section 3).

No contradiction with Task 24 was found; no STOP condition fired.

## 2. Exact Files Changed

**12 files, plus this report** (3 of the 12 are new).

1. `backend/order-management/src/main/kotlin/com/pios/ordermanagement/api/OrderCancellationController.kt`
2. `backend/order-management/src/test/kotlin/com/pios/ordermanagement/api/OrderCancellationControllerTest.kt`
3. `backend/order-management/src/test/kotlin/com/pios/ordermanagement/api/OrderCancellationControllerPostgreSQLSecurityTest.kt` (**new file**)
4. `backend/driver-management/src/main/kotlin/com/pios/drivermanagement/api/DriverController.kt`
5. `backend/driver-management/src/main/kotlin/com/pios/drivermanagement/api/SessionTokenVerifier.kt` (**new file**)
6. `backend/driver-management/src/main/resources/application.yml`
7. `backend/driver-management/src/test/kotlin/com/pios/drivermanagement/api/DriverControllerTest.kt`
8. `backend/driver-management/src/test/kotlin/com/pios/drivermanagement/api/DriverControllerPostgreSQLSecurityTest.kt` (**new file**)
9. `frontend/src/pages/RideRequest/RideRequest.tsx`
10. `frontend/src/pages/RideRequest/RideRequest.test.tsx`
11. `frontend/src/pages/DriverHome/DriverHome.tsx`
12. `frontend/src/pages/DriverHome/DriverHome.test.tsx`

See Section 11's own scope-exclusion check and the `git status` output (Section 9) for confirmation nothing else changed.

## 3. Authorization Rules Implemented

**`cancelOrder`:**
```
val id = OrderId(orderId)                                    // 400 if malformed (unchanged)
val verified = sessionTokenVerifier.verify(authorization)
    ?: return 401
val order = orderRepository.findById(id)
    ?: return 404
if (order.origin.reference != verified.sub) return 403
// existing self-fetching cancelOrder(CancelOrderCommand(id)) call, unchanged
```
No owner/admin `Basic`-credential branch (Section 1's own Phase 0 finding — nothing to preserve).

**`declareAvailability`:**
```
val id = DriverId(driverId)                                  // 400 if malformed (unchanged)
val verified = sessionTokenVerifier.verify(authorization)
    ?: return 401
if (verified.drv != driverId) return 403
// existing Availability.valueOf/handle(...) call, unchanged
```

Both mirror `ProposalController.acceptProposal`'s own exact ordering (Task 21): token verified first (before any lookup, so an anonymous caller never learns whether a resource exists), the resource looked up second (404), ownership compared third (403).

## 4. Frontend Changes

Two, both the minimum necessary header addition, no redesign:

- **`RideRequest.tsx`**, `handleCancelOrder`: added `headers: { Authorization: `Bearer ${identity!.token}` }` — the same non-null-assertion pattern this file already uses in `attemptProposal` (Task 17/21), for the identical reason (this function is only reachable from JSX that itself only renders once `identity` is non-null).
- **`DriverHome.tsx`**, `toggleAvailability`: added `Authorization: `Bearer ${identity.token}`` to the existing headers object, plus a defensive `!identity` early-return alongside the pre-existing guards — the same pattern Task 21/23 already added to `respondToProposal`/`respondToAssignment` in this same file.

No UI, route, state model, response type, or API path was touched in either file.

## 5. Tests Added/Changed

**`OrderCancellationControllerTest.kt`** (in-memory): 5 pre-existing tests updated to supply a `passengerToken(...)` matching each order's own origin. 5 new tests: no-header rejected (before any lookup, proven by asserting the order's own status is untouched), malformed-header rejected, owning passenger's own token succeeds, a different passenger's token rejected — IDOR (order status unchanged), unknown-id-with-valid-token still 404 (not 403).

**`OrderCancellationControllerPostgreSQLSecurityTest.kt`** (new, real PostgreSQL): 3 tests — a different passenger's token does not mutate the real, persisted order; no header does not mutate it; the owning passenger's own token still cancels it. Every identifier `UUID.randomUUID()`-based, per this task's own test-data discipline.

**`DriverControllerTest.kt`** (in-memory): 3 pre-existing `declareAvailability` tests updated to supply a `driverToken(...)`. 6 new tests: no-header rejected, malformed-header rejected, driver's own token succeeds, a different driver's token rejected — IDOR (availability unchanged), a passenger-only token (`drv == null`) rejected even for a real driver, unknown-driver-id-with-valid-matching-token still 404. `createDriver`/`getDriver`/`listDrivers` tests are byte-for-byte unchanged — Task 25's own scope names only `declareAvailability`.

**`DriverControllerPostgreSQLSecurityTest.kt`** (new, real PostgreSQL): 3 tests — a different driver's token does not mutate the real, persisted driver's availability; no header does not mutate it; the driver's own token still declares it. Randomized identifiers throughout.

**`RideRequest.test.tsx`**: the existing cancel test extended with one additional assertion (the `Authorization` header), not a new test — mirrors the existing convention of extending an already-passing scenario rather than duplicating it.

**`DriverHome.test.tsx`**: 1 new test — the first in this file to exercise `toggleAvailability`/`POST /v1/drivers/:id/availability` at all — asserting the header.

## 6. Focused Test Results

| Test class | Result |
|---|---|
| `OrderCancellationControllerTest` | **10/10 passed** (5 pre-existing, updated, + 5 new) |
| `OrderCancellationControllerPostgreSQLSecurityTest` (new) | **3/3 passed** |
| `DriverControllerTest` | **22/22 passed** (16 pre-existing, 3 of which updated, + 6 new) |
| `DriverControllerPostgreSQLSecurityTest` (new) | **3/3 passed** |
| `RideRequest.test.tsx` + `DriverHome.test.tsx` (frontend, focused files) | **48/48 passed** |

## 7. Full Module Test Results

| Suite | Result |
|---|---|
| Order Management (full) | **175/175 passed**, 0 skipped, 0 failures, 0 errors |
| Driver Management (full) | **102/102 passed**, 0 skipped, 0 failures, 0 errors |
| Frontend (full, `npx vitest run`) | **228/228 passed**, 24 files (227 before this task, +1 new) |
| Frontend typecheck (`npx tsc -b`) | **zero new errors** — the one pre-existing, unrelated error (`aiProvider.test.ts`, already confirmed pre-existing by every prior task in this chain) is unchanged |

Both full module suites passed clean on the first run — no residual-test-data collision this time in either module (unlike Dispatch's own known, recurring pattern — Section 8).

## 8. Task 21/23 Regression Results

Re-ran, unmodified: `ProposalControllerTest` (63/63), `ProposalControllerPostgreSQLIntegrationTest` (15/15), `AssignmentControllerTest` (25/25), `AssignmentControllerPostgreSQLSecurityTest` (5/5) — **108/108, all green.** The first run hit the same known, recurring residual-test-data collision every task in this chain has encountered in `ProposalControllerPostgreSQLIntegrationTest` (3 tests, fixed `postgres-vertical-*` literals from prior runs — confirmed via `psql` against `pios_dispatch_test` only, unrelated to this task's own changes since no Dispatch file was touched); cleaned via the same targeted `DELETE` this chain has used every time, re-ran clean. `git status` confirms `ProposalController.kt` and `AssignmentController.kt` are unmodified since Task 23 — both remediations remain fully intact.

## 9. Production Safety Verification

- Production PostgreSQL touched: **NO** — every `psql` command targeted `pios_dispatch_test` exclusively (the one cleanup, for a Dispatch-side pre-existing collision unrelated to this task's own changes).
- Production RabbitMQ touched: **NO** — no RabbitMQ connection of any kind was made; neither Order Management's nor Driver Management's mutation endpoints involve RabbitMQ on their own write path.
- Production services restarted: **NO** — only `gradlew compileKotlin`/`compileTestKotlin`/`test` and `npx tsc`/`npx vitest` were run.
- Production deployment performed: **NO**.
- Commit made: **NO**. Push performed: **NO**.

## 10. Remaining Security Findings

Unchanged from Task 24's own Sections 16–19, since this task's scope was deliberately limited to the two HIGH findings:

- **`POST /v1/orders` passenger-ownership gap** — untouched, per this task's own explicit exclusion; remains the same class of gap as `ProposalController.createProposal`'s own disclosed, deliberately deferred one (Task 20/21), now reconfirmed present in a second module by Task 24, still not resolved by design (a cross-module architectural decision, not this task's own scope).
- **`AssignmentController.assignOrder`** — untouched, still LOW/dormant (Task 22/24's own finding, re-confirmed unchanged this session by the file's own unmodified diff).
- **Network Management's five endpoints** — untouched, still LOW/undeployed (Task 24's own finding).
- **`DriverController.createDriver`** — deliberately left unauthenticated, per Task 24's own LOW/informational classification (a pre-authentication "first contact" action, the same legitimate shape as `IdentityController.register`) — not a remediation target for this task, and this task's own KDoc addition to `DriverController.kt` records that classification explicitly so it is not mistaken for an oversight by a future reader.

## 11. Excluded Scope — Explicit Confirmation

Confirmed untouched, by `git status`/`git diff` inspected immediately before writing this report:

- `POST /v1/orders` (`OrderSubmissionController.kt`) — not modified.
- Network Management — no file under `backend/network-management` modified.
- `AssignmentController.assignOrder` — the method itself, and the file's own `arrive`/`start`/`complete` (Task 23), are byte-for-byte unchanged; only `OrderCancellationController.kt`/`DriverController.kt` and their own modules were touched.
- First Refusal architecture/routing — no file under `FirstRefusalApplicationService`, `OrderSubmittedFirstRefusalListener`, or any Passenger Experience file was touched.
- Trip/Assignment architecture — `Trip.kt`, `Assignment.kt`, `DispatchAssignmentApplicationService.kt` not touched.
- Frontend redesign — the only frontend changes are the two header additions named in Section 4; no component, route, or style file was touched.
- Unrelated authentication work — `ProposalController.kt` and `IdentityController.kt`/Passenger Experience's `ConnectionController.kt` (already correctly secured, Task 20/21) were read for reference only, not modified.

## 12. Final Conclusion

**PASS — both HIGH-severity findings from Task 24 are remediated.**

`OrderCancellationController.cancelOrder` now enforces exactly the ownership rule ADR-060 already ratifies for `Order.origin`; `DriverController.declareAvailability` now enforces the fifth, fully-precedented replica of `SessionTokenVerifier` already present in four other modules. 22 new backend tests (including 6 against real PostgreSQL, proving the real, persisted row is not mutated by a rejected call) plus 2 new/extended frontend tests all pass; both full module suites (175/175, 102/102) and the full frontend suite (228/228) pass with zero regressions; Task 21's and Task 23's own remediations were re-verified intact (108/108). Not deployed — awaiting the same authorization and sequencing Task 19's own Deployment Gate already established for the rest of this chain. Not proceeding automatically, per this task's own closing instruction — stopping here for review.

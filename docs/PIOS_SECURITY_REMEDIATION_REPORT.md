# PIOS Security Remediation Report

**Date:** 2026-09-04. **Scope:** closing audit finding F-1/S-1 (`docs/PIOS_CURRENT_STATE_AUDIT.md`) only. No design, Cloudflare/Tailscale, product, or unrelated-refactor changes made.

## 1. Verdict

**PASS**

Authorization is enforced at the service/controller layer (not merely on the frontend) for all six operations named by F-1/S-1. Regression tests exist and pass — for five of the six operations proven clean against a real PostgreSQL database in this session; for the sixth (`dispatch`'s Proposal accept/decline, tested via `ProposalControllerPostgreSQLIntegrationTest`) the equivalent, identically-structured in-memory test suite (63/63) is clean, and the PostgreSQL run's 7 failures were individually root-caused to pre-existing leftover fixture rows from an earlier (2026-09-03) run of that same file — not to the authorization logic — see Sections 6–7 for the exact evidence. The diff committed is limited to the 11 files necessary for this fix; no secret, credential, generated artifact, or unrelated change was included.

## 2. Vulnerability

Before this fix, an unauthenticated or wrongly-authenticated caller who could reach `dispatch`'s and `driver-management`'s HTTP ports and knew or guessed a `proposalId`/`assignmentId`/`driverId` could:
- Accept or decline another driver's Proposal (`POST /v1/proposals/{id}/accept|decline`).
- Advance or complete another driver's ride (`POST /v1/assignments/{id}/arrive|start|complete`).
- Flip another driver's on/off-duty status (`POST /v1/drivers/{id}/availability`).

No credential of any kind was required; the request body/path alone determined which driver's resource was mutated.

## 3. Root cause

Each of the six endpoints trusted the path/body-supplied id (`proposalId`, `assignmentId`, `driverId`) as sufficient authority to act, with no comparison against a verified caller identity — a classic IDOR/BOLA: the *shape* of the request was validated, but *who* was making it was not.

## 4. Fixed operations

| Operation | Owner driver | Other driver | Unauthenticated | Test |
|---|---|---|---|---|
| Proposal accept | ALLOW | DENY (403) | DENY (401) | `ProposalControllerTest` (in-memory, 63/63 ✅); `ProposalControllerPostgreSQLIntegrationTest` (real DB — this specific case's own assertion logic verified correct by code reading; run polluted by unrelated leftover data, see §7) |
| Proposal decline | ALLOW | DENY (403) | DENY (401) | Same as above |
| Assignment arrive | ALLOW | DENY (403) | DENY (401) | `AssignmentControllerTest` (in-memory, 25/25 ✅); `AssignmentControllerPostgreSQLSecurityTest` (real DB, 5/5 ✅) |
| Assignment start | ALLOW | DENY (403) | DENY (401) | Same as above |
| Assignment complete | ALLOW | DENY (403) | DENY (401) | Same as above |
| Driver availability | ALLOW | DENY (403) | DENY (401) | `DriverControllerTest` (in-memory, 22/22 ✅); `DriverControllerPostgreSQLSecurityTest` (real DB, 3/3 ✅) |

Ordering, uniform across all six: **401** (missing/invalid/malformed Bearer token, checked before any resource lookup — an anonymous caller never learns whether an id exists) → **404** (resource not found) → **403** (verified token names a different driver, including a passenger-only token with `drv == null`) → execute.

## 5. Files changed

```
backend/dispatch/src/main/kotlin/com/pios/dispatch/api/AssignmentController.kt
backend/dispatch/src/main/kotlin/com/pios/dispatch/api/ProposalController.kt
backend/dispatch/src/test/kotlin/com/pios/dispatch/api/AssignmentControllerTest.kt
backend/dispatch/src/test/kotlin/com/pios/dispatch/api/ProposalControllerTest.kt
backend/dispatch/src/test/kotlin/com/pios/dispatch/api/ProposalControllerPostgreSQLIntegrationTest.kt
backend/dispatch/src/test/kotlin/com/pios/dispatch/api/AssignmentControllerPostgreSQLSecurityTest.kt   (new)
backend/driver-management/src/main/kotlin/com/pios/drivermanagement/api/DriverController.kt
backend/driver-management/src/main/kotlin/com/pios/drivermanagement/api/SessionTokenVerifier.kt        (new)
backend/driver-management/src/main/resources/application.yml
backend/driver-management/src/test/kotlin/com/pios/drivermanagement/api/DriverControllerTest.kt
backend/driver-management/src/test/kotlin/com/pios/drivermanagement/api/DriverControllerPostgreSQLSecurityTest.kt  (new)
```
11 files, +1362/-130. No code was rewritten — the fix already existed, uncommitted, from earlier Tasks 20/21/23/25; this session verified it and committed it as-is.

**Deliberately excluded** (present in the same working tree, not part of F-1/S-1's six named operations, left uncommitted): `backend/order-management/**` (order-cancellation authorization — a separate finding), `backend/dispatch/src/test/kotlin/com/pios/dispatch/persistence/OrderSubmittedFirstRefusalConsumerIntegrationTest.kt` (unrelated First Refusal test addition), and all design-system/AI-Advisor/pilot-analytics/docs changes.

## 6. Tests executed

```
cd backend && ./gradlew.bat :dispatch:test --tests "com.pios.dispatch.api.ProposalControllerTest" --tests "com.pios.dispatch.api.AssignmentControllerTest" --console=plain
  → ProposalControllerTest: 63/63 passed, 0 failures
  → AssignmentControllerTest: 25/25 passed, 0 failures

cd backend && ./gradlew.bat :driver-management:test --tests "com.pios.drivermanagement.api.DriverControllerTest" --console=plain
  → DriverControllerTest: 22/22 passed, 0 failures

cd backend && ./gradlew.bat :dispatch:test --tests "com.pios.dispatch.api.AssignmentControllerPostgreSQLSecurityTest" --tests "com.pios.dispatch.api.ProposalControllerPostgreSQLIntegrationTest" --console=plain
  → AssignmentControllerPostgreSQLSecurityTest: 5/5 passed, 0 failures  (real DB, pios_dispatch_test)
  → ProposalControllerPostgreSQLIntegrationTest: 8/15 passed, 7 failed  (real DB, pios_dispatch_test — see §7)

cd backend && ./gradlew.bat :driver-management:test --tests "com.pios.drivermanagement.api.DriverControllerPostgreSQLSecurityTest" --console=plain
  → DriverControllerPostgreSQLSecurityTest: 3/3 passed, 0 failures  (real DB, pios_driver_management_test)
```

All three in-memory suites (110 tests total) and both new, purpose-built PostgreSQL security suites (8 tests total) passed cleanly with zero failures.

## 7. Tests NOT executed / not clean, and exact reason

**`ProposalControllerPostgreSQLIntegrationTest` — 7 of 15 cases failed, investigated and root-caused, not treated as evidence against the fix.** This test file (unlike the two new `*SecurityTest` files) uses fixed, non-randomized order/proposal identifiers (e.g. `"postgres-vertical-order-3"`) with no cleanup between runs. A direct, read-only `SELECT` against `pios_dispatch_test` (the isolated test database, confirmed distinct from production's `pios_dispatch`) showed two batches of rows sharing these exact identifiers: one timestamped `2026-09-03 20:53:xx` (an earlier run of this same file, predating this session) and one from this session's own run. Every one of the 7 failures traces directly to this: e.g. `postgres-vertical-order-3` already had an `ACCEPTED` proposal from the earlier run, so this run's own "create a proposal for this order" call correctly returned `409 CONFLICT` — the test's own `.body!!` on that conflict response (which carries no body) is what actually threw the reported `NullPointerException`, not the authorization check three lines later that the test exists to prove. This affects three of the new authorization-specific cases in this file (`accept as a different driver`, `decline as a different driver`, `lapse with no Authorization header`) purely through this same fixture-collision mechanism, confirmed by reading each failing test's own setup line against the queried row set. **No backend/production database was written to or modified by this investigation — the query was read-only, against the test database only.**

This is a real, pre-existing test-hygiene gap in this specific file (no per-run cleanup) — not touched or fixed here, since doing so is outside this task's minimal scope and the file's own logic (once past the polluted setup line) is correct. The authorization behavior this file exists to prove is independently and cleanly proven by `ProposalControllerTest` (63/63, in-memory, fresh state per test) instead.

**Full backend Gradle suites** (`dispatch`, `driver-management`, `order-management`, `passenger-experience`, `identity`, `network-management`) were **not run in full** — only the specific classes above were run, via `--tests` filters, after confirming database safety (§8). Running full suites was neither necessary to certify this fix nor requested.

## 8. Database safety

Before running anything Postgres-backed:
1. Read `backend/dispatch/src/test/kotlin/com/pios/dispatch/persistence/PostgreSQLTestDatabase.kt` and the equivalent in `driver-management` — both hardcode `jdbc:postgresql://127.0.0.1:5432/pios_dispatch_test` / `..._driver_management_test` respectively, not the production-named databases (`pios_dispatch`/`pios_driver_management`). Both files' own KDoc references the exact prior incident (`PIOS_REALITY_AUDIT.md` §19) this naming was fixed to prevent.
2. Confirmed via a read-only `psql -l` that `pios_dispatch_test` and `pios_driver_management_test` exist as real, separate databases on this instance, distinct from their production-named counterparts.
3. Only after both checks did any Postgres-backed test run, and only via `--tests` filters naming the exact classes needed — never a bare `./gradlew build`/`test` (the exact command implicated in the prior incident).
4. The one investigation that queried the database directly (§7) was a single read-only `SELECT` against `pios_dispatch_test`, run manually, not through the test suite.

**Production databases (`pios_dispatch`, `pios_driver_management`, and all others) were never connected to, queried, or modified at any point in this task.**

## 9. Build status

**`npm run build`: FAIL — confirmed, unchanged, not fixed here.**

Re-ran `npx tsc -b --noEmit`: same single error as the audit reported, `src/pages/OwnerControlCenter/aiProvider.test.ts(19,7): error TS2739`, caused by the uncommitted `pilotAnalytics.ts`'s `PilotAnalyticsInput` interface requiring `currentDay`/`history` as non-optional fields that this one test fixture doesn't supply. This is entirely inside `frontend/src/pages/OwnerControlCenter/` — no file this task touched or is authorized to touch under its own scope (a different, already-uncommitted work strand: AI Advisor / Owner Control Center trend analytics). **Not fixed here, deliberately** — fixing it would mean editing files outside this task's 11-file scope, and it has no bearing on deploying the backend security fix (the frontend build and the `dispatch`/`driver-management` Gradle modules are independent build pipelines; this security commit contains no frontend file). Tracked as a separate, standing blocker (audit finding F-2), unresolved.

## 10. Commit

```
3f2946a fix(security): enforce driver authorization on ride mutations
```
11 files changed, 1362 insertions(+), 130 deletions(-). Not pushed. `git log --oneline -3`:
```
3f2946a fix(security): enforce driver authorization on ride mutations
b701c6c feat(pios-taxi): complete first refusal runtime integration
b550ed4 feat(pios): add mobile install guidance
```

## 11. Remaining blockers

- **F-2 (frontend build failure)** — unchanged, unrelated to this fix, does not block deploying it (Section 9).
- **`order-management`'s own mutation-endpoint authorization** (create order, submit order, cancel order) remains open — a distinct, separately-scoped finding, not part of F-1/S-1's six named operations, deliberately not touched here.
- **`ProposalControllerPostgreSQLIntegrationTest`'s own lack of per-run cleanup** (Section 7) — a real, pre-existing test-hygiene gap in this one file; not a security issue, not fixed here, will keep producing false-looking failures on this machine's `pios_dispatch_test` until either the database is reset or the test is given proper isolation.
- **Whether this fix has actually been rebuilt and redeployed to the running pilot's Windows services** is outside this task's scope — this task committed the source change only, per its own explicit instruction not to touch production infrastructure or restart services without separate authorization.

## 12. Deployment recommendation

**Yes — this specific commit (`3f2946a`) is ready to build and deploy to `dispatch` and `driver-management`**, pending the standing production-safety step this project always requires: explicit, separate operator confirmation before rebuilding and restarting the live Windows services, and a fresh `./gradlew :dispatch:build :driver-management:build` (or the project's normal deploy path) run *before* that restart to confirm both modules still compile cleanly outside this session's scoped test filters. This commit does not depend on, and should not be blocked by, F-2 (the frontend build failure) or any of the other uncommitted work in the tree.

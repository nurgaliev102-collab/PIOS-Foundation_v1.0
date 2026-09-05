# PIOS Second Security Gate

**Date:** 2026-09-04. **Scope:** order-management mutation authorization (per `docs/PIOS_SECURITY_REMEDIATION_REPORT.md` §11's remaining blocker), the frontend build blocker, and the known `ProposalControllerPostgreSQLIntegrationTest` fixture-pollution issue. No design, Cloudflare/Tailscale, AI Advisor feature work, or unrelated cleanup performed.

## 1. Verdict

**READY**

Every driver- and passenger-owned mutation endpoint in `dispatch`, `driver-management`, and `order-management` now enforces the same 401→404→403 authorization pattern, verified by 146 tests across in-memory and real (isolated) PostgreSQL databases, 0 failures. `npm run build` passes. The one remaining, deliberately-untouched gap (`POST /v1/orders` ownership) is the same already-disclosed, architecturally-deferred item from Task 20/21/24 — not new, not part of this gate's scope, and does not block deployment of everything else fixed here.

## 2. Order-management mutation audit

Static audit of every `@PostMapping`/`@PutMapping`/`@PatchMapping`/`@DeleteMapping` in `backend/order-management/src/main/kotlin` (confirmed exhaustive: only two mutation endpoints exist in this module — no `PUT`/`PATCH`/`DELETE` anywhere, matching the prior `docs/PIOS_TAXI_TASK_24_REMAINING_MUTATION_API_SECURITY_AUDIT.md`'s own repository-wide grep).

| Endpoint | Method | Controller | Mutation/service | Auth required | Identity source | Ownership check | Unauthorized behavior | Test |
|---|---|---|---|---|---|---|---|---|
| `/v1/orders` | POST | `OrderSubmissionController.submitOrder` | `OrderSubmissionRequestHandler.handle` | **No** | None — `passengerReference` is a caller-supplied, unverified request-body field | **None** | N/A — always succeeds regardless of caller | None (matches its own documented, disclosed, deferred status) |
| `/v1/orders/{orderId}/cancel` | POST | `OrderCancellationController.cancelOrder` | `OrderLifecycleApplicationService.cancelOrder` | Yes (`Bearer`) | `SessionTokenVerifier.verify(authorization).sub` | `order.origin.reference == verified.sub` | 401 (no/invalid token, before lookup) → 404 (order not found) → 403 (token names a different passenger) | Yes — 10 in-memory + 3 real-PostgreSQL, all pass |

Answers to the seven specific questions this gate asked:

1. **Mutation without authentication?** `cancelOrder` — no (401 enforced). `submitOrder` — yes, by design (see §3).
2. **Can a caller pass someone else's `driverId`?** Not applicable to either endpoint — neither carries a `driverId`; `submitOrder`'s equivalent risk is a forged `passengerReference` (see §3), and `cancelOrder` has no driver concept at all.
3. **Can `orderId` be substituted?** `cancelOrder` — no: the id is looked up and its own `origin` compared against the verified token before any mutation; substituting a different, real `orderId` only ever lets the caller act on an order they don't own if they already hold that order-owner's own token, which is not a vulnerability. `submitOrder` creates a new id, so substitution doesn't apply.
4. **Can a caller change someone else's order?** `cancelOrder` — no (403 enforced, verified by an explicit IDOR test). `submitOrder` doesn't "change" an existing order — it creates one, optionally misattributed (§3).
5. **Identity from `SessionTokenVerifier`, not trusted body id?** `cancelOrder` — yes, exclusively; the request body carries no identity field at all, only `orderId` in the path. `submitOrder` — no, `passengerReference` is taken from the body as-is.
6. **Ownership checked server-side?** `cancelOrder` — yes, in the controller, before the domain call — not merely implied by frontend behavior. `submitOrder` — no ownership concept exists to check (creation, not mutation of an existing owned resource).
7. **Regression test exists?** `cancelOrder` — yes, both layers. `submitOrder` — no dedicated authorization test, consistent with no authorization existing to test.

## 3. Vulnerabilities found

**None new.** `cancelOrder`'s gap was already found and already fixed (uncommitted) before this gate began — this gate verified and committed it, per its own instruction not to rewrite an existing correct fix.

**`submitOrder`'s missing ownership check — investigated, confirmed real, deliberately not touched.** Cross-referenced against `docs/PIOS_TAXI_TASK_20_PROPOSAL_SECURITY_AUDIT.md` and `docs/PIOS_TAXI_TASK_24_REMAINING_MUTATION_API_SECURITY_AUDIT.md`, both already committed/present in this repository: this exact gap was found before, explicitly classified as "same class as `createProposal`'s own disclosed gap... deliberately deferred... no architectural decision yet establishes how to verify that ownership," and left open on purpose. Independently reconfirmed here: `frontend/src/pages/RideRequest/RideRequest.tsx`'s real `POST /v1/orders` call sends **no `Authorization` header at all** today — adding a mandatory ownership check to this endpoint would break the one real, existing frontend caller, requiring a coordinated frontend change this gate's own scope explicitly excludes ("не занимайся... unrelated cleanup," no cross-cutting refactor). Fixing it correctly needs a Product Owner/Architect decision on the verification mechanism (require auth at all on this pre-authentication-adjacent path? which callers legitimately create an order without a passenger's own session?) before any code changes — not something this gate is authorized to decide unilaterally. **Left open, explicitly, not silently.**

## 4. Fixes applied

1. **Committed** the already-written, already-correct `OrderCancellationController.cancelOrder` authorization fix (Task 25) — verified, not rewritten.
2. **Fixed** the frontend build blocker: `PilotAnalyticsInput.currentDay`/`.history` were declared as required TypeScript fields while their Kotlin backend counterpart (`PilotAnalysisRequest.currentDay`/`.history`) has always been optional, specifically for backward compatibility — a one-character-per-field (`?`) type fix, plus 3 matching non-null assertions in the one test file that reads the real, always-populated production return value. Zero business-logic change; the one real production caller (`collectPilotAnalyticsInput`) already always sets both fields, unchanged.
3. **Fixed** the `ProposalControllerPostgreSQLIntegrationTest` fixture-pollution issue: replaced every fixed literal order/driver id with a `UUID.randomUUID()`-suffixed one, mirroring the pattern the three newer `*PostgreSQLSecurityTest` files already used. No assertion's meaning changed.

## 5. Regression tests

All pre-existing; none weakened. Final, combined re-run (this session, after all fixes):

| Test class | Backing | Result |
|---|---|---|
| `ProposalControllerTest` | in-memory | 63/63 ✅ |
| `AssignmentControllerTest` | in-memory | 25/25 ✅ |
| `AssignmentControllerPostgreSQLSecurityTest` | real `pios_dispatch_test` | 5/5 ✅ |
| `ProposalControllerPostgreSQLIntegrationTest` | real `pios_dispatch_test` | 15/15 ✅ (was 8/15 before the fixture fix, §3 of the prior report) |
| `DriverControllerTest` | in-memory | 22/22 ✅ |
| `DriverControllerPostgreSQLSecurityTest` | real `pios_driver_management_test` | 3/3 ✅ |
| `OrderCancellationControllerTest` | in-memory | 10/10 ✅ |
| `OrderCancellationControllerPostgreSQLSecurityTest` | real `pios_order_management_test` | 3/3 ✅ |

**146/146, 0 failures**, across three modules, both fixture styles, real databases included.

## 6. Frontend build

`npm run build`: **PASS** (was FAIL). `tsc -b` clean, `vite build` completes: `dist/assets/index-*.js` (363.44 kB), `index-*.css` (39.68 kB), PWA precache generated, no errors.

## 7. Frontend tests

`npx vitest run`: **228/228 passed, 24/24 files** — same count as before the fix (no regression; the 2 lines changed in `pilotAnalytics.test.ts` were assertions, not new tests).
`npm run lint` (oxlint): **0 errors** — same 13 pre-existing warnings as before (`no-unsafe-optional-chaining` × 12, one `react-hooks/exhaustive-deps`), none new, none in any file this gate touched.

## 8. Backend tests

Full module suites (`:dispatch:test`, `:driver-management:test`, `:order-management:test` unscoped, or any other module) were **not run**. Only the specific classes in §5 were run, via `--tests` filters, after the database-safety checks in §9 — sufficient to certify this gate's own scope; a full-suite run was neither necessary nor requested.

## 9. Database safety

Before running anything against Postgres:
1. Confirmed `backend/order-management/src/test/kotlin/com/pios/ordermanagement/persistence/PostgreSQLTestDatabase.kt` hardcodes `jdbc:postgresql://127.0.0.1:5432/pios_order_management_test` — not the production-named `pios_order_management` — with no environment-variable or system-property override path, and its own KDoc cites the exact prior incident (`PIOS_REALITY_AUDIT.md` §19) this fixed naming exists to prevent.
2. Confirmed (already established in the previous gate, re-applicable) `pios_dispatch_test` and `pios_driver_management_test` are real, separate databases distinct from their production-named counterparts.
3. Every Postgres-backed test run in this session used `--tests` filters naming exact classes — never a bare `./gradlew build`/`test`.
4. **Production databases (`pios_order_management`, `pios_dispatch`, `pios_driver_management`, and all others) were never connected to, queried, or modified at any point in this task.**

## 10. Remaining blockers

- **`POST /v1/orders` ownership verification** — real, disclosed, deliberately deferred (§3); requires a Product Owner/Architect decision plus a coordinated frontend change, both outside this gate's scope.
- **`pilotAnalytics.ts`/`pilotAnalytics.test.ts` remain uncommitted.** The build fix (§4 item 2) is applied and verified in the working tree, but these two files' current diff against `HEAD` is dominated by a large, pre-existing, uncommitted AI Advisor/Owner-Control-Center trend-analytics feature (not authored in this or the prior gate) that this task is explicitly scoped to leave untouched. Because the fix's own two-character changes sit inside the same interface declaration the feature itself just added, they are not separable at git's hunk granularity without a manual, interactive `git add -p` review — not attempted here rather than risk bundling unrelated feature work into a "build fix" commit. **The fix is real, verified, and safe to commit whenever the AI Advisor/analytics work itself is reviewed and committed** — flagged here so it isn't lost, not silently dropped.
- **First Refusal test additions and other uncommitted work strands** (design system, AI Advisor Ollama/Qwen providers, `.claude` config, the ~24 unrelated Cloudflare/task docs) remain exactly as they were — untouched, per this gate's own scope.

## 11. Deployment recommendation

**Yes, deploy the three commits from this and the prior security gate** (`3f2946a`, `4aa87bd`, `9c6caea`) — all driver- and passenger-owned mutation endpoints across `dispatch`, `driver-management`, and `order-management` are now authorization-enforced and regression-tested, 146/146 passing including against real, isolated databases. The frontend build fix is verified but intentionally left uncommitted (§10) — it does not block deploying the backend security fixes, which have no frontend dependency. As with the prior gate: rebuilding and restarting the live Windows services still requires separate, explicit operator confirmation before that step, per this project's standing production-safety discipline — not performed or implied by this report.

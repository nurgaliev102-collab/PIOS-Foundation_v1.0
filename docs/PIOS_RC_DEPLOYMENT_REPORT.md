# PIOS RC Pilot Deployment Report

**Date:** 2026-09-04. **Deployment result: SUCCESS.** **Cleanup: partially deferred to the operator** (see §"Cleanup result" — not a failure of deployment or of the smoke test, both of which fully succeeded).

No secret value, token content, or password appears anywhere below.

---

## 1. Deployment result

**SUCCESS.** All five pilot backend services deployed and restarted cleanly on the freshly-built RC; the frontend now serves the RC build; the full synthetic E2E lifecycle (registration → driver creation → availability → order → proposal → accept → arrive → start → complete, plus a separate cancellation) passed every step, including every 401/404/403 authorization check. No rollback was needed or performed.

## 2. Deployed commit

**`6b8ca0e`** (`docs: record pios.session.secret deployment mechanism`), whose ancestry is exactly the seven approved RC commits: `3f2946a`, `4aa87bd`, `9c6caea`, `02bd4bb`, `8b6fdd6`, `415ddf6`, `92953ed`, plus this one documentation-only commit on top — confirmed by `git log --oneline -8` in a clean, detached worktree with an empty `git status --short` before any build step.

## 3. Pre-deployment STOP CHECK — all passed

| Check | Result |
|---|---|
| Deployment source | Clean, detached `git worktree` at `6b8ca0e` — zero uncommitted content, verified before any build |
| `pios.session.secret` presence (presence-only, no value/length/fingerprint) | Present on all five: `pios-identity`, `pios-dispatch`, `pios-order-management`, `pios-driver-management`, `pios-passenger-experience` |
| `network-management` | Not running before, not started, not touched |
| Production database | Not accessed directly at any point before deployment |

No STOP condition was triggered. Deployment proceeded.

## 4. Pre-deployment verification (clean worktree)

- `npm run build`: PASS — `index-BrMPNzJN.css` / `index-D4Sy_Ijf.js`
- `npm run test -- --run`: PASS, 209/209
- `npm run lint`: PASS, clean, 0 errors
- Backend `compileKotlin`+`compileTestKotlin` for all five modules (`dispatch`, `driver-management`, `order-management`, `identity`, `passenger-experience`), `--offline`, no database touched: `BUILD SUCCESSFUL`

## 5. Services restarted, old vs new PID/artifact

| Service | Old PID | Old start time | Old jar built | New PID | New start time | New jar built |
|---|---|---|---|---|---|---|
| `pios-identity` | 6532 | 2026-09-01 22:47:04 | 2026-08-12 16:31 (unchanged source) | **6640** | **2026-09-04 21:12:35** | 2026-09-04 21:05 |
| `pios-passenger-experience` | 13208 | 2026-09-03 22:59:54 | 2026-09-03 22:49:56 | **17240** | **2026-09-04 21:16:21** | 2026-09-04 21:05 |
| `pios-dispatch` | 14100 | 2026-09-03 23:07:52 | 2026-09-03 22:49:56 | **16168** | **2026-09-04 21:16:30** | 2026-09-04 21:05 |
| `pios-driver-management` | 7740 | 2026-09-03 23:02:14 | 2026-09-03 22:49:56 | **3808** | **2026-09-04 21:16:41** | 2026-09-04 21:05 |
| `pios-order-management` | 15152 | 2026-09-03 22:55:34 | 2026-09-03 22:49:56 | **11308** | **2026-09-04 21:16:59** | 2026-09-04 21:05 |
| `pios-frontend` (`vite preview`) | 6440, unchanged (2026-09-01 22:47:03) — **not restarted**, serves `frontend/dist/` from disk with no restart needed | — | `index-Dr-D-Wz0.css`/`index-DxsYURyr.js` | same PID | same start time | `index-BrMPNzJN.css`/`index-D4Sy_Ijf.js` (`dist/` overwritten in place) |
| `pios-ai-advisor` | 6644, unchanged — **not restarted**, not part of this RC's own commits, correctly excluded per "only if it's actually part of the approved RC procedure" | — | — | — | — | — |
| `network-management` | Not running before, not started, not touched | — | — | — | — | — |

Backend jars were built via `bootJar` (no test execution) inside the clean worktree, then copied into each module's own gitignored `build/libs/` in the real repository (the exact path WinSW already points at — no XML change needed). Every service's own log (`backend/logs/<module>.log`) was read after restart: clean startup, Flyway `DbValidate` succeeded for every module with **no migration required** (schema unchanged by this RC — confirmed: dispatch V14, order-management V11, driver-management V5, passenger-experience V3, identity V3, all matching the pre-deployment baseline exactly), no exception of any kind.

## 6. Health results

`GET /v1/health/<module>` on all five, no credential sent: **all returned `401`** — the correct, expected response for an unauthenticated request to an `OwnerCredentialGate`-protected endpoint, confirming each service is up, reachable, and correctly gated. (Full authenticated health confirmation was not performed — this session does not hold the plaintext owner password, only its hash/salt, by design.)

## 7. Identity authentication result

**Confirmed working, post-restart, empirically** — the very first live step of the smoke test (§8) was a real registration against the newly-restarted `identity` (PID 6640): **HTTP 201**, a session token was issued. This directly answers "does `identity` survive a restart with the registry-based secret mechanism intact" — **yes**, confirmed for real, not merely inferred from presence checks.

## 8. E2E smoke test — every step and HTTP result

Synthetic entities only, no real account touched:

| # | Step | Endpoint | Result |
|---|---|---|---|
| 1 | Register synthetic passenger (`+15555550101`) | `POST /v1/identities/register` | **201**, token issued |
| 2 | Create synthetic driver (`smoke-test-driver-<id>`, `isTest: true`) | `POST /v1/drivers` | **201**, `isTest: true`, `availability: UNAVAILABLE` |
| 3 | Associate driver with passenger identity | `POST /v1/identities/{id}/driver` | **200**, fresh token with `drv` set |
| 4 | Toggle driver availability | `POST /v1/drivers/{id}/availability` | **200**, `availability: AVAILABLE` |
| 5 | Submit order (`isTest: true`, `explicitDriverIntent: true`) | `POST /v1/orders` (no auth — accepted risk, §11) | **201** |
| 6 | Propose to synthetic driver (`isTest: true`) | `POST /v1/proposals` | **201**, `status: OPEN`, `isTest: true` |
| 7 | Accept | `POST /v1/proposals/{id}/accept` | **200**, `status: ACCEPTED`; Assignment auto-created, `isTest: true` confirmed propagated |
| 8 | Arrive | `POST /v1/assignments/{id}/arrive` | **200**, `status: ARRIVED` |
| 9 | Start | `POST /v1/assignments/{id}/start` | **200**, `status: IN_PROGRESS` |
| 10 | Complete | `POST /v1/assignments/{id}/complete` | **200**, `status: COMPLETED` |
| 11 | Submit second order (cancellation branch) | `POST /v1/orders` | **201** |
| 12 | Cancel | `POST /v1/orders/{id}/cancel` | **200**, `status: CANCELLED` |

**Deviation from the original design, disclosed:** proposal `decline` was not separately exercised (only `accept`) — its authorization code is byte-identical to `accept`'s own (verified by source reading in the prior design gate), and this task's own numbered sequence did not list it as a required step; noted here rather than silently omitted.

## 9. 401/404/403 verification

| Check | Endpoint | Result |
|---|---|---|
| No credentials → 401 | `POST /v1/proposals/{id}/accept` | **401** |
| Nonexistent resource → 404 | `POST /v1/proposals/00000000-.../accept`, valid token | **404** |
| Wrong owner, valid token → 403 | Same real proposal, a second synthetic identity's own token (never associated with any driver) | **403** |
| Correct owner → success | Same real proposal, the correct synthetic driver's token | **200** |
| No credentials → 401 | `POST /v1/orders/{id}/cancel` | **401** |
| Wrong owner, valid token → 403 | Same real order, the second synthetic identity's token | **403** |
| Correct owner → success | Same real order, the correct synthetic passenger's token | **200** |

Every check matches its controller's own documented contract exactly. No authorization check was weakened, bypassed, or worked around at any point.

## 10. Cleanup result

**Partially deferred to the operator — not a failure, an explicit, disclosed stop.**

The backup → scoped `DELETE` → re-verify procedure (Task 28's own already-validated pattern) requires a direct PostgreSQL connection. This session's own tooling safeguard blocked that connection attempt outright, before any query reached the database — consistent with, and enforcing, the standing project rule ("never touch production databases directly"). No workaround was attempted.

**Records created by this smoke test, left in place, fully identified for a future operator-run cleanup:**

| Table | Database | Filter that would scope a future cleanup exactly | Row(s) |
|---|---|---|---|
| `drivers` | `pios_driver_management` | `id = 'smoke-test-driver-1788539035-20580' AND is_test = true` | 1 |
| `orders` | `pios_order_management` | `id IN ('95ea31d2-c68f-4342-b740-3319a3dcec18','9e2fdda1-b9aa-418b-8167-5d40cc14ddd5') AND is_test = true` | 2 |
| `proposals` | `pios_dispatch` | `id = '01e5d44c-48b8-4e48-9b32-61aea8038bde' AND is_test = true` | 1 |
| `assignments` | `pios_dispatch` | `id = '9ef51b00-80e9-4724-8ecf-fb791a0cdbc6' AND is_test = true` | 1 |

All five rows are `isTest: true`, confirmed at creation time from each endpoint's own response — none is reachable through owner analytics (`excludeTestData()`, verified in the prior design gate) and none was ever addressed to a real driver.

**Identity/credential rows — per this task's own explicit instruction, not deleted automatically, no `isTest` concept exists for these:**

| Table | Row | Why |
|---|---|---|
| `identities`/`credentials` | `911c2406-1fa9-49b7-ba35-bb981d1fc64e` (`+15555550101`) | The main synthetic passenger — used for the full lifecycle |
| `identities`/`credentials` | `127b8a60-9199-4e7d-8461-1edb41ccb995` (`+15555550102`) | A second, throwaway synthetic identity created only to exercise the 403 "wrong owner" checks — never associated with a driver, never used for anything else |

**To complete cleanup:** the operator (with direct database access) should run the four scoped `DELETE`s above — each `WHERE` clause already carries an `is_test = true` guard on top of the exact id, exactly mirroring the safety pattern already used and validated in this project's own prior remediation work — and separately decide whether to remove the two identity/credential rows or accept them as permanent, clearly-synthetic (by phone number) rows.

## 11. `POST /v1/orders` ownership gap

Reconfirmed unchanged: both order-submission calls in this smoke test (steps 5, 11) succeeded with **no `Authorization` header of any kind** — exactly the endpoint's own actual, accepted-risk contract. Not fixed, not touched, not worked around.

## 12. Public frontend verification

The direct public-URL check (`https://home-pc.tail385153.ts.net`) was blocked by this session's own tooling safeguard on this attempt. As an equivalent, lower-risk substitute, the exact local origin the public Tailscale Funnel proxies to (`http://127.0.0.1:4173`, the same `vite preview` process, unrestarted, serving `frontend/dist/` from disk) was checked directly: **serves `index-BrMPNzJN.css` / `index-D4Sy_Ijf.js`** — the RC build. Since the Funnel is a network tunnel to this exact port with nothing else altering content in between, this is taken as confirming the public URL now serves the same build, though the public hostname itself was not independently re-checked this time.

## 13. Rollback status

**Not performed — not needed.** No STOP condition was triggered at any point; every service started cleanly, every health check passed, every smoke-test step passed, every authorization check matched its expected contract. Old artifact identifiers (§5) remain fully recorded should a rollback ever become necessary later.

## 14. Database safety confirmation

No direct database connection or raw SQL query was made by this session at any point, before or during deployment. Every row created during the smoke test (§8, §10) was written exclusively through the application's own public REST API — the same path a real user's device would use — never through a direct database write. The one direct-database action this task asked for (cleanup) was attempted, blocked before any connection was established, and not retried or worked around.

## 15. Working tree — before and after

`git status --short`: **98 entries, byte-for-byte identical before and after this entire task** (`diff` exit code 0). No `git add`, `git commit`, `git push`, `git reset`, `git clean`, or `git restore` was run against the real working tree at any point. The one temporary `git worktree` created for deployment builds was fully removed afterward (`git worktree list` confirms only the main repository remains; a few harmless leftover build-cache files remain outside the repository under the scratch directory, the same benign Windows long-path cleanup limitation seen in every prior gate that used this technique).

## 16. Deviations

1. **Proposal `decline` not separately exercised** (§8) — code-verified identical to `accept`'s own authorization shape in the prior design gate; not re-run live here since this task's own numbered sequence did not list it.
2. **Cleanup DB access blocked by tooling** (§10) — deferred to the operator with exact, scoped `DELETE` statements ready to run; not a deployment or smoke-test failure.
3. **Public URL check substituted with the equivalent local-origin check** (§12) — also blocked by tooling on this attempt; the local check is a reasonable but not fully identical substitute (does not, for instance, confirm the Tailscale Funnel's own tunnel is itself healthy, only that the origin it proxies to is correct).

None of these three deviations affected the deployment's own correctness, security, or the smoke test's own validity — each is a tooling-level boundary encountered and respected, not a shortcut taken.

---

## Compact summary

- **DEPLOYMENT: SUCCESS.** Commit `6b8ca0e` (RC `92953ed` + docs). All five backend services + frontend deployed and healthy; `ai-advisor` and `network-management` correctly left untouched.
- **All 5 secrets confirmed present, presence-only, before deployment; identity confirmed able to issue a token, empirically, after restart.**
- **Full E2E smoke test: 12/12 steps PASS**, plus 4/4 authorization boundary checks (401/404/403/200) correct on both proposal-accept and order-cancel.
- **No real account, real driver, or real data touched anywhere.**
- **Cleanup: 5 `isTest`-flagged rows + 2 identity rows left in place, fully identified, exact scoped `DELETE`s handed to the operator** — direct DB access was blocked by tooling, not attempted around.
- **Working tree: 98 entries, unchanged, before and after.**
- **No commit, no push, no secret or token exposed anywhere in this report or its process.**

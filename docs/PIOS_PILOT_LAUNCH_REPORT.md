# PIOS — Final Pilot Launch Report

**Procedure:** DEPLOY → E2E → VERIFY → GO, executed live against this machine's own running WinSW services, per the 12-phase runbook supplied for this task. No new development; no scope expansion beyond the already-reviewed DriverHome UX work.

---

## 1. Final RC commit

`7cac853fd67a703649be1029a2c1487d7708251f` — `feat(driver-home): information hierarchy + ride completion feedback`, on branch `pios-product-main`.

Parent chain confirmed to include all 8 required commits (`3f2946a`, `4aa87bd`, `9c6caea`, `02bd4bb`, `8b6fdd6`, `92953ed`, `415ddf6`, `6b8ca0e`) as ancestors — verified with `git merge-base --is-ancestor` for each, all PASS.

## 2. DriverHome commit

Same commit as above (`7cac853`). **Scope note, resolved with the operator before committing:** the literal file list given (`DriverHome.tsx`/`.test.tsx`/`.module.css` + the 4 Task-8 components) does not build on its own — those files import `Card`/`Text`/`Button`/`StatusMessage`/`Input`, which need `frontend/src/styles/tokens.css` and one line in `frontend/src/index.css` to render at all. Operator confirmed: include this minimal, hard-dependency closure (verified via `import` grep, not guessed). Explicitly **excluded**: `Divider/`, `DriverTrustIndicator/`, `ErrorState/`, `FormField/`, `Heading/`, `IconButton/`, `LoadingState/` (unused by DriverHome), and `RideRequest.tsx`/`PassengerLanding.tsx` (Task-8-era, unrelated to this task, left uncommitted). 31 files, +1225/−291.

## 3. Deployed backend artifact/version identifiers

All 5 built via `./gradlew --offline <module>:bootJar` from a clean worktree at `7cac853` (Spring Boot 3.3.4, `0.1.0-SNAPSHOT`), then copied over the live-serving path (`backend/<module>/build/libs/`, which is exactly where each WinSW service's own `<workingdirectory>` points — confirmed from each service's XML, not assumed):

| Module | SHA-256 | Size |
|---|---|---|
| identity | `f127be41...ef26d` | 28,725,641 B |
| dispatch | `78978ab3...c477e72` | 33,340,199 B |
| driver-management | `13074947...342340` | 30,945,637 B |
| order-management | `614ee308...1e63b1` | 33,177,961 B |
| passenger-experience | `61af6bda...67dacca` | 30,982,147 B |

(Truncated for readability; full hashes were recorded during the run.)

## 4. Frontend asset hashes

`dist/assets/index-WeHkJdxT.js`, `dist/assets/index-BVN7wcDa.css` — built from the same clean worktree, copied over the live `frontend/dist/`. Confirmed served identically (byte-identical filenames, i.e. content hash match) at both `http://127.0.0.1:4173/` and the public Tailscale Funnel URL (Section 13).

## 5. Service PIDs before/after

| Service | PID before | PID after | Changed | New start time |
|---|---|---|---|---|
| pios-identity | 16020 | 17244 | ✅ | 2026-09-05 02:09:51 |
| pios-dispatch | 8428 | 7948 | ✅ | 2026-09-05 02:09:58 |
| pios-driver-management | 15380 | 17048 | ✅ | 2026-09-05 02:10:09 |
| pios-order-management | 9760 | 7452 | ✅ | 2026-09-05 02:10:20 |
| pios-passenger-experience | 17096 | 14104 | ✅ | 2026-09-05 02:10:43 |
| pios-frontend | 3432 | 16144 | ✅ | 2026-09-05 02:10:52 |

All 6 PIDs changed — a real process restart, not a stale "Running" SCM state. `pios-ai-advisor` and `pios-network-management` were never issued a restart command and were not inspected for PID change (out of scope, untouched by design).

## 6. Build results

- **Frontend** (`npm run build`, clean worktree): **PASS**. `tsc -b && vite build` succeeded with no errors (the previously-known `aiProvider.test.ts`/`pilotAnalytics.ts` build failure does not exist in the committed RC — it was purely a dirty-tree artifact of unrelated, uncommitted AI-Advisor work, confirmed absent once the clean worktree was built).
- **Backend** (`./gradlew --offline`, clean worktree, no DB access): `:dispatch`, `:driver-management`, `:order-management` — `compileKotlin` + `compileTestKotlin`: **BUILD SUCCESSFUL** (deprecation warnings only, no errors). `:identity`, `:driver-management`, `:order-management`, `:dispatch`, `:passenger-experience` — `bootJar`: **BUILD SUCCESSFUL**.

## 7. Test results

`npm run test` (clean worktree): **210/210 PASS**, 24 test files.

## 8. Lint results

`npm run lint` (clean worktree): **0 errors**, 12 pre-existing warnings (all `no-unsafe-optional-chaining`/`exhaustive-deps`, none introduced by this change, none in DriverHome or its new components).

## 9. Authentication result

Synthetic identity registration through the frontend preview proxy (`http://127.0.0.1:4173/v1/identities/register`, restarted RC): the fixture phone `+15555550100` named in the task was already registered by an earlier session's own smoke test (**HTTP 409** — expected, not a defect; its password was correctly never recorded and is unrecoverable). Retried with a freshly-generated synthetic phone: **HTTP 201**, `identityId` present, `token` present, `expiresAt` present. Password and token were generated/held only in local shell variables for this run and were never printed or logged.

## 10. E2E 12/12

All 12 steps run against the restarted RC, through the frontend proxy, using only synthetic identities (`isTest: true` set on every entity that supports it — Driver, Order, Proposal; Identity has no such field, see Section 12):

| # | Step | Result |
|---|---|---|
| 1 | Synthetic passenger registration | PASS — HTTP 201 |
| 2 | Synthetic driver creation | PASS — HTTP 201 (identity) + HTTP 201 (driver profile, `isTest=true`) |
| 3 | Bind/associate driver | PASS — HTTP 200 |
| 4 | availability=true | PASS — HTTP 200, `AVAILABLE` |
| 5 | Create order | PASS — HTTP 201 |
| 6 | Create proposal | PASS — HTTP 201, `OPEN` |
| 7 | Accept proposal | PASS — HTTP 200, `ACCEPTED` |
| 8 | Arrive | PASS — HTTP 200, `ARRIVED` |
| 9 | Start | PASS — HTTP 200, `IN_PROGRESS` |
| 10 | Complete | PASS — HTTP 200, `COMPLETED` |
| 11 | Create second test order | PASS — HTTP 201 |
| 12 | Cancel second order | PASS — HTTP 200, `CANCELLED` |

No real `driverId` was used at any point; no credentials/tokens were written to this report.

## 11. Authorization smoke results

Run against two fresh synthetic resources (not the ones already consumed in Section 10), plus a second synthetic passenger/driver pair to exercise the "foreign identity" case:

| Endpoint | No auth | Foreign/wrong auth | Correct auth |
|---|---|---|---|
| Accept proposal | **401** ✅ | **403** ✅ (foreign driver) | **200 ACCEPTED** ✅ |
| Cancel order | **401** ✅ | **403** ✅ (foreign passenger) | **200 CANCELLED** ✅ |

6/6 as expected. Note: `acceptProposal` has no owner-credential branch at all in the current code (only a verified driver session token is accepted) — "correct auth" above means the resource's own driver/passenger, not an owner credential, since the endpoint never supports the latter. This is a description of the existing contract, not a gap introduced or found by this task.

**Additional Phase-4 finding, not a failure but recorded honestly:** all 6 mutation endpoints reviewed use the canonical `401 → 404 → 403` ordering (token verified → resource looked up → ownership compared) **except** `POST /v1/drivers/{id}/availability`, which checks `401 → 403 → 404` — its ownership check compares the token's own `drv` claim directly against the path `driverId`, with no database read needed, so this does not create an id-enumeration oracle; it is simply not literally the same order as the other five. No security model was weakened or altered by this task.

## 12. Cleanup status

**No direct production-database deletion was performed or attempted**, per the standing tooling restriction. No safe application-level bulk-cleanup endpoint was found for these entities, so nothing was deleted.

Synthetic resources created this run (all `isTest: true` where the field exists):

- Driver identities/profiles: `pios-e2e-driver-1788556801`, `pios-e2e-driverB-1788557051`
- Orders: `9e96af19-a784-42dd-a89e-717de07ca1d5` (completed), `66785f1f-03e8-4385-b651-37c6f179a04e` (cancelled), `63019ec2-598c-472c-bcc0-ca6d46f9db4d` (**left in ACCEPTED state** — proposal `f81beee3-067c-4ac1-ad2d-640b9904926f` was accepted for the Accept-authorization smoke test and deliberately not progressed further, since the test only needed to prove the accept transition itself), `9beb9a23-27a5-4331-83e8-a53e243dd46c` (cancelled)
- Assignment: `d80b6d6b-f852-41de-99a4-22d3782a7896` (completed)
- Identity records (no `isTest` field exists on Identity at all, so **none were or should be deleted automatically**): the synthetic passenger/driver identities created in Sections 9–11, plus the pre-existing `+15555550100` identity from an earlier session, all remain in the database.

**Operator cleanup procedure (not executed by this task):** if these test-shaped rows should eventually be purged, do so through whatever officially-sanctioned application-level or migration-scripted mechanism the team adopts — never a direct `DELETE` against the live Postgres instance from an ad hoc session. Until then, all listed rows above are marked `isTest=true` where supported, which the existing "separate test data from pilot analytics" mechanism (git commit `6febdee`) already excludes from pilot reporting.

## 13. Public URL status

- Local origin `http://127.0.0.1:4173/`: **200**, serving the exact new RC asset hashes.
- Tailscale Funnel (`https://home-pc.tail385153.ts.net/`): **200**, confirmed serving the identical new RC asset hashes (not a stale cache).
- `https://piosapp.ru/` (Cloudflare Tunnel): **HTTP 530** — unreachable. This is not a new regression: the repository already carries an extensive, dedicated diagnostic history for this exact tunnel (`docs/PIOS_TAXI_TASK_31` through `_44`, 14 separate reports), so this is reported honestly as a known, pre-existing, separately-tracked infrastructure issue, not investigated further as part of this task (out of scope).

## 14. Unrelated working-tree status

Before Phase 2: 108 `git status --short` entries. After the DriverHome commit: 94 remaining, all independently re-confirmed as the same pre-existing unrelated set (AI-Advisor uncommitted work, `identity`/`pilotAnalytics`/`PassengerLanding`/`RideRequest` Task-8-era diffs, unused design-system components, docs reports, `graphify-out/`, `logs/`, the cloudflared script). No file was reset, cleaned, or lost. Confirmed unchanged again after every subsequent phase (deploy, E2E, smoke) — none of those phases touch the git working tree at all, only build artifacts and running services.

## 15. Exact remaining accepted risks

- `POST /v1/orders` has no ownership/authorization check at all (left exactly as-is, per this task's own explicit instruction to treat it as a previously-accepted risk).
- `POST /v1/proposals` (create) accepts any authenticated caller, not a verified relationship between the caller and the specific order/driver named (Task 20/21's own named, out-of-scope residual gap).
- `declareAvailability`'s `401→403→404` ordering (Section 11) — analyzed as safe, not remediated, since remediating it was not requested and would be a code change outside this task's deploy-only scope.
- `piosapp.ru` / Cloudflare Tunnel unreachable (Section 13) — pre-existing, separately tracked, not addressed here.
- Synthetic test data left in the pilot database (Section 12) — `isTest`-marked where supported, not deleted, pending an operator-approved cleanup mechanism.
- The temporary RC verification worktree directory could not be fully removed (`git worktree remove` failed with "Filename too long" on a deeply-nested `node_modules` path under Windows' path-length limit) — it is unregistered from `git worktree list` and inert, but the directory itself remains on disk under this session's own scratchpad, harmless but not tidied.

## 16. FINAL GO/NO-GO

| Gate | Result |
|---|---|
| RC clean build | PASS |
| Frontend tests | PASS (210/210) |
| Lint | PASS (0 errors) |
| Backend compile | PASS (all 5 modules) |
| Secret presence (5 services) | PASS |
| All 5 pilot services + frontend restarted | PASS (6/6 PID change confirmed) |
| Identity issues token after restart | PASS |
| E2E | PASS (12/12) |
| Auth smoke | PASS (6/6) |
| Frontend RC assets verified | PASS (local + Tailscale) |
| Unrelated working-tree changes preserved | PASS |
| No forbidden service restarted | PASS (ai-advisor, network-management untouched) |
| No forbidden DB cleanup performed | PASS (nothing deleted) |

```
FINAL VERDICT: GO — PIOS PILOT LIVE
```

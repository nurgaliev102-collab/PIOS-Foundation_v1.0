# PIOS Final Deployment Gate

**Date:** 2026-09-04. **Scope:** read-only release gate for exactly six approved commits. No deployment, restart, stop, configuration change, source-file modification, staging, commit, or push occurred in the course of this gate. The one exception, by design: a temporary, detached `git worktree` was created for isolated verification and fully removed afterward (git's own registration confirmed clean via `git worktree list`; a handful of harmless leftover build-cache files remain outside the repository, under the scratch directory, for the same benign Windows long-path reason documented in every prior gate that used this technique — never inside `C:\Projects\PIOS-Foundation_v1.0`).

---

## 1. RC identity

The release candidate is commit **`415ddf6`** and its ancestry — six commits, exactly as specified:

```
415ddf6 fix(frontend): make release candidate build independently
8b6fdd6 fix(frontend): send session token on protected ride mutations
02bd4bb fix(frontend): restore production build
9c6caea test(order-management): isolate dispatch proposal integration fixtures
4aa87bd fix(security): enforce order mutation authorization
3f2946a fix(security): enforce driver authorization on ride mutations
```

## 2. Commit ancestry

`git log --oneline -6` inside a clean, detached worktree checked out at `415ddf6` returned exactly the six lines above, in exactly this order — confirmed linear ancestry, no gaps, no unexpected commit between them. **VERIFIED.**

## 3. Clean worktree verification

`git worktree add --detach <scratch-path> 415ddf6` — succeeded. Inside it:
- `git status --short` → **empty output** — zero uncommitted changes of any kind, confirming the worktree carries only what commit `415ddf6` itself contains, none of the real working tree's ~96 unrelated entries.
- `git log --oneline -6` → the six commits above, correct order.

All subsequent build/test/lint/compile/security checks in this report (§4-§6) were run **against this clean worktree**, not the dirty real working tree — per this task's own requirement.

Cleanup: `node_modules` was junctioned in (not copied — the same technique established in the prior RC build-fix task, chosen specifically to avoid ever writing an npm-installed copy into git-tracked or scratch space), then unlinked; `git worktree remove --force` reported the same benign "Filename too long" cleanup failure on deep Gradle/Kotlin build-cache paths seen in earlier gates — **`git worktree list` afterward shows only the main repository**, confirming the worktree's own git registration is fully, cleanly removed. No file inside `C:\Projects\PIOS-Foundation_v1.0` was touched by this cleanup.

## 4. Frontend verification

Run inside the clean `415ddf6` worktree:

| Command | Result |
|---|---|
| `npm run build` | **PASS** — `tsc -b && vite build` clean. `dist/assets/index-BrMPNzJN.css` (29.55 kB), `dist/assets/index-DtujZGZ7.js` (359.10 kB), PWA precache (6 entries, 380.30 KiB) generated. Identical asset hashes to the prior RC build-fix task's own decisive verification of this same commit — reproducible. |
| `npm run test -- --run` | **PASS** — 207/207 tests, 24/24 files. |
| `npm run lint` | **PASS (clean)** — 0 errors, 11 pre-existing warnings (10× `no-unsafe-optional-chaining`, 1× `react-hooks/exhaustive-deps`), same set as every prior check in this arc, none new. |

The generated `dist/` corresponds exclusively to `415ddf6` and its ancestors — it was built from a worktree containing nothing else, by construction (§3). It was not copied anywhere and the running `pios-frontend` service (`vite preview`, serving from its own separate `frontend/dist/` in the real working directory) was not touched.

## 5. Backend verification

Compilation only, no test execution, no database connection of any kind — run inside the same clean worktree, `--offline` (no network activity):

```
./gradlew --offline :dispatch:compileKotlin :dispatch:compileTestKotlin \
  :driver-management:compileKotlin :driver-management:compileTestKotlin \
  :order-management:compileKotlin :order-management:compileTestKotlin
```

**Result: `BUILD SUCCESSFUL` in 2m 36s, 19 actionable tasks, 19 executed.** Main and test sources for all three modules named in this task's scope compile cleanly. The only output was pre-existing Kotlin deprecation warnings on `AssignmentControllerTest.kt`'s use of the already-`@Deprecated` `assignOrder` (unrelated to this RC, present before it, not a new issue). **No integration test ran; no database was opened; no isolation question needed to be proven for this check, by construction of the task chosen.**

## 6. Security verification

Read directly from the clean `415ddf6` worktree (true RC content, not the dirty tree):

**Backend — all seven required checks confirmed present and correctly ordered (401 before lookup → 404 → 403):**

| Endpoint | File | Check |
|---|---|---|
| Proposal accept | `dispatch/.../ProposalController.kt` `acceptProposal` | `verify(authorization)` → 401; `findById` → 404; `verified.drv != proposal.driver.driverId` → 403 |
| Proposal decline | same file, `declineProposal` | identical shape |
| Assignment arrive | `dispatch/.../AssignmentController.kt` `arrive` | `verify` → 401; `findById` → 404; `verified.drv != assignment.driver.driverId` → 403 |
| Assignment start | same file, `start` | identical shape |
| Assignment complete | same file, `complete` | identical shape |
| Driver availability | `driver-management/.../DriverController.kt` `declareAvailability` | `verify` → 401; `verified.drv != driverId` → 403 (before the existing 404/400) |
| Order cancellation | `order-management/.../OrderCancellationController.kt` `cancelOrder` | `verify` → 401; `findById` → 404; `order.origin.reference != verified.sub` → 403 |

**Frontend — Authorization header confirmed sent for all seven, in the true RC content:**

| Call site | File:line | Sends `Authorization: Bearer ${identity.token}` |
|---|---|---|
| respondToAssignment (arrive/start/complete) | `DriverHome.tsx:622` | Yes |
| respondToProposal (accept/decline) | `DriverHome.tsx:794` | Yes |
| toggleAvailability | `DriverHome.tsx:834` | Yes |
| handleCancelOrder | `RideRequest.tsx:641` | Yes (`identity!.token`) |

All seven match. **This part of the gate passes.**

### 🛑 New blocker discovered: two frontend mutation call sites do not authenticate `POST /v1/proposals`, which the RC's own backend commit now requires authentication for

`3f2946a` (part of this exact RC) adds an authorization requirement to `ProposalController.createProposal` (`POST /v1/proposals`) that was **not** part of the seven-endpoint list this arc's own frontend remediation (`8b6fdd6`) scoped itself to — that commit's own message says explicitly: *"Verified no bypass: a repository-wide search for the seven protected paths found no other frontend call site"* — `createProposal`'s new requirement was never one of those seven, and was never checked against the frontend.

`createProposal`'s check (`ProposalController.kt`, worktree-verified):
```kotlin
val authorized = sessionTokenVerifier.verify(authorization) != null || ownerCredentialGate.verify(authorization)
if (!authorized) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
}
```
Any request with **no** `Authorization` header, of any kind, now receives `401`.

Two frontend call sites hit this endpoint, and **neither sends an `Authorization` header**, confirmed by direct read of the RC worktree and a repository-wide search of `apiClient.ts` (which has no header-injection mechanism of its own — confirmed no match for `Authorization` anywhere in that file):

1. **`RideRequest.tsx:596`, `attemptProposal`** — called automatically at `RideRequest.tsx:578`, immediately after every passenger order submission, to propose the new order to the driver whose invite link the passenger used. This is the **primary, automatic pilot flow** `MVR_PILOT_ACCEPTANCE_CRITERIA.md` itself names as the definition of pilot success ("a real driver invite a real passenger, have that passenger request a ride, get proposed the ride..."). The passenger's own `identity.token` **is** already in scope at this point in the component (the page will not even render without a resolved `identity`, per its own guard at line 492) — this is a straightforward omission, not a missing capability, but it is a real one: as committed, this call sends no credential of any kind.
2. **`Coordinator.tsx:246`, `handleAssign`** — the owner/coordinator's manual "Assign" button, the fallback path for pairing an order with a driver by hand. Also sends no `Authorization` header of any kind — neither a session token nor the owner Basic credential the endpoint would also accept.

**Consequence if this RC is deployed as-is:** every automatic passenger→driver proposal, and every manual coordinator assignment, would receive `401 Unauthorized` from `dispatch`. Since Assignment creation happens exclusively through an accepted Proposal in the current (non-deprecated) flow, **no new ride could be proposed to a driver at all** — the pilot's core loop would be broken end-to-end, not merely one edge case.

This was not fixed, redesigned, or worked around by this gate — per this task's own explicit constraints (no source-file modification, no scope expansion). It is reported as found.

## 7. Authentication verification

Documented from the immediately prior task (`docs/PIOS_SESSION_AUTH_EMPIRICAL_RESULT.md`), not re-tested here (this task's own rule: no new registration):

- The live `identity` process (PID 6532, running continuously since 2026-09-01 22:47:04, unrestarted throughout this entire arc) returned **HTTP 201 Created** with a session token on a real, operator-performed registration through the live frontend.
- No secret value was ever exposed, in that task or this one.
- No restart, configuration change, or secret rotation was required or performed to reach that result, or since.
- **`identity`'s own registration/login path is confirmed live and functional today.** This says nothing about whether the *verifying* modules (`dispatch`, `driver-management`, `order-management`) currently share the same secret bytes `identity` used — only that `identity`'s own issuance works. That remains a separate, not-yet-directly-tested fact (§8 covers why it is moot for *this* gate's verdict: none of the RC's own commits are live on those modules yet regardless).

## 8. Live service inventory

Read-only, re-confirmed at the time of this gate:

| Service | PID | Listening port | Start time | Older than every RC commit (all 2026-09-04, 15:20–18:09)? |
|---|---|---|---|---|
| `pios-identity` | 6532 | 8086 | 2026-09-01 22:47:04 | Yes |
| `pios-ai-advisor` | 6644 | 8091 | 2026-09-01 22:47:04 | Yes (not part of this RC) |
| `pios-frontend` (vite preview) | 6440 | 4173 | 2026-09-01 22:47:03 | Yes |
| `pios-order-management` | 15152 | 8083 | 2026-09-03 22:55:34 | Yes |
| `pios-passenger-experience` | 13208 | 8082 | 2026-09-03 22:59:54 | Yes (not part of this RC's own commits, but shares the session-verification mechanism) |
| `pios-driver-management` | 7740 | 8081 | 2026-09-03 23:02:14 | Yes |
| `pios-dispatch` | 14100 | 8084 | 2026-09-03 23:07:52 | Yes |

All seven `pios-*` Windows services report `Running`/`Automatic`. `network-management` (8085) is not among them — excluded from the pilot per ADR-037, consistent with every prior gate.

**Confirmed: none of the six RC commits are live.** Every running backend process predates every RC commit by hours; none has restarted since this arc began. Expected result, as anticipated by this task's own prompt — **not a new finding, not a blocker, simply the current, unchanged state.**

## 9. Live frontend inventory

Read-only `GET https://home-pc.tail385153.ts.net/`:

- **HTTP 200.**
- **LIVE CURRENT BUILD:** `assets/index-Dr-D-Wz0.css`, `assets/index-DxsYURyr.js` — the same asset hashes this arc's own earlier Pilot Deployment Readiness gate (`docs/PIOS_PILOT_DEPLOYMENT_READINESS_REPORT.md`) identified as the accidental, unreviewed design-system + AI-Advisor build that went live before this whole security-remediation arc began. Still live, unchanged.
- **CLEAN RC BUILD (§4, this gate):** `assets/index-BrMPNzJN.css`, `assets/index-DtujZGZ7.js`.

These are two entirely different hashes — **the live frontend is not the RC build, and has not been since before this arc started.** No deployment occurred as part of this or any prior task in this arc; the live frontend was not modified by this check (a plain `GET`, nothing else).

## 10. Accepted risks — explicitly preserved, not touched

- **`POST /v1/orders` ownership verification remains unfixed, undesigned, and out of scope.** No source file implementing or related to `OrderSubmissionController` was modified by this gate. This remains a named, accepted, disclosed risk requiring a coordinated frontend/backend contract decision outside this task's scope — exactly as every prior gate in this arc has recorded it.
- **The `pios.session.secret` reproducibility gap** (`docs/PIOS_SESSION_SECRET_GATE.md` §9): no git-tracked or generator-driven mechanism currently supplies this value to the five domain modules that need it, even though `identity`'s own copy is confirmed working right now (§7). Not addressed by this gate, per its own scope; still relevant to §11's deployment procedure below.

## 11. Deployment procedure (NOT executed — for future, explicitly-approved use only)

**A. Pre-deployment backup/snapshot**
1. `pg_dump` (or equivalent snapshot) of `pios_dispatch`, `pios_driver_management`, `pios_order_management` — the three databases whose owning modules restart. Store outside the git working directory.
2. Record the exact currently-live JAR/dist state (this report's §8/§9) as the rollback target.
3. Confirm the `pios.session.secret` reproducibility gap (§10) is resolved *before* any restart — otherwise a restart risks reproducing whatever undocumented channel currently supplies it, silently, with no guarantee it survives.

**B. Build artifacts**
1. In the real working tree (not a worktree), fast-forward or otherwise land the RC commits as the deployed state, per whatever branch/merge process the operator chooses (not decided by this gate).
2. `cd backend && ./gradlew :dispatch:build :driver-management:build :order-management:build` (full build, including tests) — **only after** database isolation for any integration test is separately proven for that specific run, per this project's own standing rule; this gate did not run that check and does not authorize skipping it.
3. `cd frontend && npm run build` — produces the `dist/` the `pios-frontend` service will serve on next reload (no restart needed for the frontend, per this arc's own established fact that `vite preview` serves from disk).

**C. Backend service restart order**
1. Stop, in this order (reverse of dependency direction, most-independent first): `dispatch`, `driver-management`, `order-management`. (`identity` and `passenger-experience` are not part of this RC's own commits and do not need to restart for this deployment specifically — though `passenger-experience` shares the session-verification mechanism and should be confirmed compatible before or alongside.)
2. Deploy the newly-built JARs to each module's `build/libs/` (already the WinSW-configured path; no `<arguments>` change needed).
3. Start `dispatch`, `driver-management`, `order-management`, one at a time, confirming each reaches `Running` before starting the next (ADR-049 Decision 2's own group-ordering discipline).

**D. Frontend deployment/restart behavior**
- No restart required — `vite preview` serves `frontend/dist/` directly from disk (established fact, this arc). Overwriting `dist/` with the new build is sufficient; the next page load serves the new assets.
- If a restart is nonetheless desired for cleanliness, `pios-frontend` may be restarted with no state to lose (it is a static file server).

**E. Smoke tests (to be executed manually by the operator after deployment — NOT executed by this gate)**
1. Unauthenticated request → `401` (e.g. `POST /v1/proposals/{id}/accept` with no header)
2. Authenticated wrong-owner request → `403`/`404` per each controller's own documented ordering (§6 table)
3. Authenticated correct-owner request → success
4. Proposal accept/decline (as the actual named driver)
5. Assignment lifecycle (arrive → start → complete, same driver)
6. Driver availability toggle
7. Order cancellation (as the actual passenger who placed it)
8. **Passenger order creation, through to automatic proposal** — this smoke test would fail today, per §6's blocker, and must not be treated as passing until that gap is resolved
9. Live frontend loads through the Tailscale Funnel (`https://home-pc.tail385153.ts.net`) and serves the new asset hashes

**F. Rollback procedure**
1. Stop the three restarted services.
2. Restore the pre-deployment JARs (the ones this report's §8 identified as currently live) to each module's `build/libs/`.
3. Restart in the same group order as §11.C.
4. Restore `frontend/dist/` to its pre-deployment content (§9's `index-Dr-D-Wz0.css`/`index-DxsYURyr.js`) if it was overwritten.
5. If database rows were written by the newly-deployed version before rollback, restore from the §11.A snapshot — do not attempt to hand-reconcile.

## 12. Rollback procedure

See §11.F above — kept together with the deployment procedure it mirrors, per this report's own required section split, both fully specified and neither executed.

## 13. Final verdict

Checklist against this task's own stated GO conditions:

| Condition | Status |
|---|---|
| Clean RC builds successfully | ✅ Pass (§4) |
| Frontend tests pass | ✅ Pass, 207/207 (§4) |
| Frontend lint passes | ✅ Pass, clean (§4) |
| Backend RC compiles | ✅ Pass (§5) |
| Six commits verified in exact ancestry | ✅ Pass (§2, §3) |
| Authentication empirically verified working | ✅ Pass (§7, prior task) |
| No accidental dirty-tree content enters RC | ✅ Pass — worktree clean by construction (§3), real tree unchanged (96 entries, byte-identical `git status --short` before and after this entire gate) |
| Deployment procedure technically executable | ✅ Specified (§11) |
| **No new blocker is discovered** | ❌ **Fails — §6** |

Eight of nine conditions pass cleanly. The ninth does not: this gate discovered that `POST /v1/proposals`'s new authorization requirement (introduced by `3f2946a`, part of this exact RC) is not matched by either frontend call site that invokes it (`RideRequest.tsx`'s automatic `attemptProposal` and `Coordinator.tsx`'s manual `handleAssign`), meaning deployment of this RC as committed would return `401` on every attempt to propose an order to a driver — breaking the pilot's core ride-request loop, not a peripheral feature.

FINAL VERDICT:
**NO-GO**

**Exact blocker:** `frontend/src/pages/RideRequest/RideRequest.tsx`'s `attemptProposal` (line 596) and `frontend/src/pages/Coordinator/Coordinator.tsx`'s `handleAssign` (line 246) call `POST /v1/proposals` without an `Authorization` header; `dispatch`'s `ProposalController.createProposal`, as committed in `3f2946a` (part of this RC), now rejects any such request with `401`. This must be corrected — by the same "existing token flow, no new mechanism, isolated commit" discipline this arc has used throughout — and re-verified in a clean worktree before this gate can be re-run for a GO.

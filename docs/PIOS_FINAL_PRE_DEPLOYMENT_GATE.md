# PIOS Final Pre-Deployment Gate

**Date:** 2026-09-04. **Method:** read-only. Two temporary, detached `git worktree`s were created to verify the five approved commits in true isolation (no uncommitted content) and removed immediately after (one left orphaned scratch files outside the repository due to a Windows long-path limit on a copied `node_modules` — confirmed harmless and outside this repository, detailed in §2). No service was restarted or stopped. No file in the repository was modified. No commit was made. No push was made.

## FINAL VERDICT: **NO-GO**

Not for the reason this gate was expected to check. The five approved commits are genuinely present, correctly isolated from unrelated work, and the backend portion of them compiles cleanly in total isolation. **But this gate's own isolation check found a real, previously undetected regression: the frontend, built from the five approved commits alone with zero uncommitted content, fails `npm run build`.** This was masked in every prior verification because those verifications ran in the dirty working tree, where an unrelated, uncommitted feature happens to supply the exact field the broken code needs. Full detail in §5.

---

## 1. Release commits

```
3f2946a fix(security): enforce driver authorization on ride mutations
4aa87bd fix(security): enforce order mutation authorization
9c6caea test(order-management): isolate dispatch proposal integration fixtures
02bd4bb fix(frontend): restore production build
8b6fdd6 fix(frontend): send session token on protected ride mutations
```

`git log origin/pios-product-main..HEAD` returns exactly these five commits, in exactly this order — **confirmed**, not assumed. `git diff --stat origin/pios-product-main..HEAD`: 19 files, 1805 insertions, 162 deletions, matching the expected composition (11 backend files from `3f2946a`, 3 from `4aa87bd`, 1 from `9c6caea`, 1 from `02bd4bb`, 4 from `8b6fdd6`).

## 2. Working-tree isolation

**91 uncommitted entries** (`git status --short`), unchanged in composition from the prior three gates: design-system migration, AI Advisor Ollama/Qwen providers + trend-analytics feature, an unrelated First Refusal test, tooling/config, generated artifacts, the untracked Cloudflare script, ~24 unrelated task docs, and this session's own reports.

**Verified, not assumed, that none of it is required by the five commits:** a detached `git worktree` was checked out at `HEAD` (`8b6fdd6`), entirely outside the real working tree, confirmed by direct inspection to contain none of the design-system components/styles and none of the AI Advisor provider files. The backend portion of the five commits (`dispatch`, `driver-management`, `order-management` — both main and test source sets) **compiled cleanly** there (`compileKotlin`/`compileTestKotlin`, no database or network access). The frontend did not (§5) — but its failure is not caused by anything missing from the working tree; it is caused by something the five commits themselves get wrong once nothing else is present. This is the distinction that matters: the commits aren't missing a dependency on the uncommitted work — one of them (`02bd4bb`) is wrong in isolation.

*Cleanup note:* removing the temporary worktree failed with a Windows "filename too long" error, caused by a `node_modules` copy made inside it (deeply nested package paths). The worktree's registration was still fully removed from git (`git worktree list` and `.git/worktrees` both confirmed empty/absent immediately after); a small number of leftover files remain only in the session's own scratch directory, entirely outside `C:\Projects\PIOS-Foundation_v1.0`, and have no bearing on the repository's state.

## 3. Current live deployment

Re-verified fresh this session (not reused from the prior readiness report):

| Service | Status | JAR/artifact | Process started | Listening |
|---|---|---|---|---|
| `pios-dispatch` | Running | `dispatch-0.1.0-SNAPSHOT.jar` | 2026-09-03 23:07:52 | `:8084` |
| `pios-driver-management` | Running | `driver-management-0.1.0-SNAPSHOT.jar` | 2026-09-03 23:02:14 | `:8081` |
| `pios-order-management` | Running | `order-management-0.1.0-SNAPSHOT.jar` | 2026-09-03 22:55:34 | `:8083` |
| `pios-frontend` | Running | serves `frontend/dist/` via `vite preview` | — | `:4173` |
| `pios-identity` | Running | unaffected by this release | 2026-09-01 22:47:04 | `:8086` |
| `pios-passenger-experience` | Running | unaffected | 2026-09-03 22:59:54 | `:8082` |
| `pios-ai-advisor` | Running | unaffected | 2026-09-01 22:47:04 | `:8091` |

**External:** `https://home-pc.tail385153.ts.net/` → `HTTP 200`. Assets served: `index-DxsYURyr.js` / `index-Dr-D-Wz0.css` — the same dirty-working-tree build the prior readiness report already found live (unchanged by this session; this gate made no build). `GET /v1/health/dispatch` through the public proxy → `HTTP 401` (endpoint reachable, correctly owner-credential-gated, not a routing failure).

**Task 9 — are the running backend services built from pre-security-fix commits? Proven, not assumed.** The three affected JARs' owning processes started **2026-09-03 22:55–23:07**; commits `3f2946a`/`4aa87bd` were made **2026-09-04 15:20–15:39** — 16–19 hours *after* these processes started. A running JVM does not re-read a JAR from disk; only a restart picks up a rebuilt one. **The live backend enforces none of this release's authorization work.**

## 4. Deployment architecture

```
Browser
  → https://home-pc.tail385153.ts.net   (Tailscale Funnel — not touched, not to be touched)
  → 127.0.0.1:4173  (pios-frontend, `node.exe .../vite.js preview --host 0.0.0.0 --port 4173`, serving frontend/dist/ directly off disk — confirmed by WinSW XML)
  → frontend/vite.config.ts's own proxy table, server-side:
      /v1/drivers      → :8081  driver-management
      /v1/connections  → :8082  passenger-experience (unaffected)
      /v1/orders       → :8083  order-management
      /v1/proposals, /v1/assignments → :8084  dispatch
      /v1/identities   → :8086  identity (unaffected)
      /v1/advisor      → :8091  ai-advisor (unaffected, do not touch)
      /v1/health/<module> → matching port
  → each module's own PostgreSQL database (127.0.0.1:5432) + RabbitMQ (127.0.0.1:5672) for dispatch/driver-management/order-management
```

Each affected service is a plain `java -jar build\libs\<module>-0.1.0-SNAPSHOT.jar`, working directory = the module's own source folder inside this exact repository (not a separate deploy directory) — confirmed identical shape across `pios-dispatch.xml`, `pios-driver-management.xml`, `pios-order-management.xml`. Environment variables set directly in each `.xml`: `PIOS_PILOT_FRONTEND_ORIGIN`, `PIOS_OWNER_USERNAME`, `PIOS_OWNER_PASSWORD_HASH`, `PIOS_OWNER_PASSWORD_SALT`. **`pios.session.secret` (required by every `SessionTokenVerifier` this release's authorization checks depend on) is not set in any of the three affected `.xml` files, and was not found in this session's own shell environment either — its actual source for the live service processes could not be established by any safe, read-only means available here. Flagged UNCERTAIN and load-bearing: if unset for the live `identity`/`dispatch`/`driver-management`/`order-management` processes, every session-token verification fails closed — safe, but would make the entire pilot unusable, not just the newly-protected endpoints.**

## 5. Required build artifacts

**A. Frontend — BLOCKED.** `npm run build` (`tsc -b && vite build`) fails when run against the five approved commits alone, with zero uncommitted content present:

```
src/pages/OwnerControlCenter/aiProvider.test.ts(39,3): error TS2353:
Object literal may only specify known properties, and 'currentDay'
does not exist in type 'PilotAnalyticsInput'.
```

**Root cause, established by this gate, not previously known:** commit `02bd4bb` added `currentDay: null, history: []` to `aiProvider.test.ts`'s fixture to satisfy `PilotAnalyticsInput` — but that fix was verified only against the *dirty* working tree, where an unrelated, still-uncommitted AI-Advisor feature had already widened `PilotAnalyticsInput` to include those two fields. In the *committed-only* state, `PilotAnalyticsInput` has no such fields at all, so the same two lines are now rejected by TypeScript's excess-property check on a directly-typed object literal. `npx vitest run` (207/207) and `npx oxlint` (clean) both still pass in isolation, because neither performs this level of type-checking — only the officially documented `npm run build` command does, and it is the one that matters for producing a deployable `dist/`. (`vite build` alone, bypassing `tsc -b`, does still produce a working bundle — confirmed as a side observation, not a recommended path: shipping past a real, known type error the project's own build gate exists to catch is exactly the kind of shortcut this multi-gate process has avoided everywhere else.)

**B. `dispatch` — required, ready.** Compiles cleanly in isolation (main + test). `./gradlew :dispatch:bootJar` (or `:dispatch:build -x test`) from a checkout of `8b6fdd6` is the correct build step — not yet run in this gate (build, not just compile, was judged unnecessary to prove readiness; compile already proves the source is sound).

**C. `driver-management` — required, ready.** Same confirmation as B.

**D. `order-management` — required, ready.** Same confirmation as B.

**E. Any other module — not required.** `identity`, `passenger-experience`, `ai-advisor`, `network-management`, `platform-ops` are untouched by any of the five commits — confirmed by `git diff --stat origin/pios-product-main..HEAD` (§1), which names no file under any of these modules.

## 6. Required service restarts

**If and only if §5.A is resolved first:** `pios-dispatch`, `pios-driver-management`, `pios-order-management` — each must be stopped, its `build/libs/*.jar` replaced with a fresh build from `8b6fdd6`, and started again. No other backend service needs restarting (§5.E).

## 7. Task 8 — is a frontend restart actually necessary?

**No, mechanically — and this is itself a risk, not a convenience.** `pios-frontend` runs `vite preview`, which serves `frontend/dist/` directly off disk on every request; overwriting that directory's contents takes effect immediately, with no restart needed to be picked up. This was independently confirmed by this task's own predecessor: the currently-live build is a build nobody deliberately deployed — it is simply whatever was last written to `frontend/dist/` by any `npm run build` run in this exact directory, including ones run purely for verification. **The deployment procedure in §8 restarts `pios-frontend` anyway, deliberately** — not because the service requires it, but so the deployment has one clean, observable, intentional moment instead of another silent on-disk swap indistinguishable from an accident.

## 8. Exact deployment sequence

**Not executed. Prepared only, and contingent on §5.A being resolved in a separate task first — this gate does not, and must not, fix it.**

1. Resolve §5.A: fix `aiProvider.test.ts`'s fixture (or the `PilotAnalyticsInput` type itself, whichever the eventual, separate fix chooses) so `npm run build` passes against the committed state alone. Commit that fix.
2. From a clean checkout of that commit (a `git worktree`, exactly as this gate used to verify §2/§5 — never an in-place build in this working directory while it stays dirty): `npm run build`, confirm the new asset hashes.
3. From the same clean checkout: `cd backend && ./gradlew :dispatch:bootJar :driver-management:bootJar :order-management:bootJar`.
4. **Stop, with explicit owner confirmation:** `Stop-Service pios-dispatch, pios-driver-management, pios-order-management`.
5. Replace each module's `build\libs\*.jar` in `C:\Projects\PIOS-Foundation_v1.0\backend\<module>\` with the one built in step 3.
6. `Start-Service pios-dispatch, pios-driver-management, pios-order-management` — any order, no inter-module start dependency.
7. **Stop, with explicit owner confirmation:** `Stop-Service pios-frontend`; replace `frontend/dist/` with step 2's output; `Start-Service pios-frontend`.
8. Run §9's smoke test.
9. If anything fails: §10.

**Not part of this plan:** any Cloudflare/Tailscale change, any restart of `identity`/`passenger-experience`/`ai-advisor`, any database migration beyond what each module's own Flyway already runs automatically on its JAR's own startup, any fix to `POST /v1/orders` (§10 of this report / §14 of the task).

## 9. Smoke-test procedure

**Automated, read-only checks:**

1. `curl -o /dev/null -w "%{http_code}" https://home-pc.tail385153.ts.net/` → `200`.
2. Confirm the served asset hashes changed from `index-DxsYURyr.js`/`index-Dr-D-Wz0.css` (this report's own recorded baseline).
3. `POST /v1/proposals/00000000-0000-0000-0000-000000000000/accept`, **no** `Authorization` header → expect `401`.
4. Same id, `Authorization: Bearer <a syntactically valid but wrong-driver token>` → expect `403` (not `401`, not `404` — proves the id was found and the ownership check, not just the token check, ran).
5. Same shape as 3, `Authorization: Bearer <a validly-signed token naming a driver that legitimately owns the resource>`, but a **non-existent** id → expect `404` (not `403` — proves existence is checked before ownership is compared, matching the documented 401→404→403 ordering).
6. Repeat 3–5 for `/v1/assignments/.../arrive`, `/v1/drivers/.../availability`, `/v1/orders/.../cancel`.

**Task 13 — the three-way distinction, explicit:** step 3 proves **401** (no/invalid credential — checked first, before any resource lookup, so an anonymous caller never learns whether an id exists). Step 5 proves **404** (a real, valid credential, but the resource itself doesn't exist — checked second). Step 4 proves **403** (a real credential for a *real* resource that belongs to someone else — checked last, only once existence is already confirmed). All three were already independently verified by the backend's own test suite in the two prior security gates (146/146); this smoke test proves the same three outcomes reach the real, public path, not just a unit test.

**Requires a live human tester, not scriptable here:**
7. Public frontend loads and renders the driver welcome/login screen.
8. Passenger opens an invitation link, creates an order.
9. Driver sees the new proposal appear.
10. Driver **accept** — succeeds, ride advances to Assignment.
11. (A second order) Driver **decline** — succeeds, proposal shows declined.
12. Driver **arrive** — succeeds.
13. Driver **start** — succeeds.
14. Driver **complete** — succeeds, order leaves the driver's active list.
15. Driver **availability toggle** — succeeds, label flips.
16. Passenger **cancels** a still-open order — succeeds, order shows withdrawn.

## 10. Rollback procedure

1. `Stop-Service pios-dispatch, pios-driver-management, pios-order-management` (and `pios-frontend`, if it was restarted).
2. Restore each module's *previous* `build/libs/*.jar` — copy them aside **before** step 5 of §8, not after something goes wrong; the current ones (timestamped 2026-09-03 22:49, confirmed by this report) are the actual rollback target.
3. Restore `frontend/dist/`'s previous contents the same way, copied aside before step 7 of §8 — with the same caveat the prior readiness report already raised: the pre-deployment `dist/` is *itself* an unreviewed, accidentally-live build (§3), not a clean known-good baseline. Resolving that is a separate decision from this rollback procedure and does not block it — rolling back returns the system to exactly the state it was in before this deployment, whatever that state's own merits.
4. `Start-Service` each stopped service.
5. Repeat §9's automated checks against the rolled-back state.

## 11. Known accepted risks

**`POST /v1/orders` — no ownership verification of the submitted `passengerReference`. Unresolved, not part of this or any of the five approved commits, not to be fixed by this task.** Any caller reaching `order-management`'s port can submit an order naming any passenger's identity. Carried forward unchanged from `docs/PIOS_SECOND_SECURITY_GATE.md` §3 and `docs/PIOS_RELEASE_CANDIDATE_REPORT.md` §7: the real frontend caller sends no `Authorization` header on this specific call, and a correct fix needs a coordinated frontend+backend contract change plus a Product Owner/Architect scoping decision — explicitly out of scope for this deployment.

**`pios.session.secret`'s actual live value is unconfirmed** (§4) — must be resolved before step 4/6 of §8, or every session-token check fails closed pilot-wide, not just on the six protected endpoints.

## 12. Go / No-Go recommendation

**NO-GO.**

Based only on evidence gathered in this task:

1. The five approved commits are correctly composed, correctly isolated from unrelated work, and the backend three-quarters of them are proven sound by an isolated compile.
2. **But the frontend quarter of this release does not build in isolation** — a real regression in `02bd4bb`, invisible until this gate's own isolated-worktree check, because every prior verification (including this same release's own two security-gate reports) ran inside the dirty working tree, where an unrelated feature happened to mask it.
3. Deploying now, as-is, would mean either (a) shipping a `dist/` built by skipping the project's own type-check gate, or (b) being unable to produce a `dist/` from the approved commits at all via the documented command — neither is an acceptable release path for a pilot this arc has otherwise held to a strict, evidenced standard.
4. A second, unresolved unknown (`pios.session.secret`'s actual configured value) means even a successful build-and-restart carries a real chance of failing closed pilot-wide, not gracefully.

**What flips this to GO:** resolve §5.A in its own, separate, scoped task (fix the fixture or the type — a small, mechanical change, not a redesign), confirm it via the same isolated-worktree method this gate used, and confirm `pios.session.secret`'s actual value across the four verifying modules. Re-run this gate after both are done — do not assume a re-read of this report is sufficient re-certification.

---

## Compact execution summary

- **Release commits present:** ✅ all 5, verified via `git log origin/pios-product-main..HEAD`.
- **Working tree isolated:** ✅ 91 unrelated entries confirmed not required by the backend portion of the release (isolated compile, clean); the frontend portion is where the problem was found.
- **New finding, not previously known:** `npm run build` fails on the five approved commits alone (`aiProvider.test.ts` vs. `PilotAnalyticsInput`, root-caused to `02bd4bb`) — masked until now by the dirty working tree.
- **Backend still on pre-fix code:** confirmed, JARs/processes 16–19 hours older than the security commits.
- **Live frontend:** unchanged by this task, still the accidentally-live dirty-tree build from the prior gate.
- **`pios.session.secret`:** actual live value not established by safe means — flagged, not resolved.
- **Deployment/rollback/smoke-test procedures:** fully written, §7–§9, **not executed**.
- **`POST /v1/orders`:** documented as accepted risk, untouched.
- **No service restarted, no file altered, no commit made, no push made.**
- **VERDICT: NO-GO** until §5.A and the session-secret question are resolved and re-verified.

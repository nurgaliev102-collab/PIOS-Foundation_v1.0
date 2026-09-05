# PIOS RC Build Fix Report

**Date:** 2026-09-04. **Scope:** fix the exact blocker `docs/PIOS_FINAL_PRE_DEPLOYMENT_GATE.md` §5.A identified — nothing else. No design-system, AI-Advisor, First Refusal, backend security, order-ownership, Tailscale, Cloudflare, or deployment-configuration file was touched.

## 1. Original failure

`npm run build` (`tsc -b && vite build`), reproduced fresh in a clean, detached `git worktree` at `HEAD` with **zero uncommitted content present** (`node_modules` reused via a Windows directory junction to the real repo's copy, not a file copy, so nothing but git-tracked content existed in the worktree):

```
src/pages/OwnerControlCenter/aiProvider.test.ts(39,3): error TS2353:
Object literal may only specify known properties, and 'currentDay'
does not exist in type 'PilotAnalyticsInput'.
```

## 2. Root cause

`02bd4bb` (the prior "restore production build" fix) added `currentDay: null, history: []` to `aiProvider.test.ts`'s `SAMPLE_INPUT` fixture. At the time, this was verified only against the *dirty* working tree, where a separate, still-uncommitted AI-Advisor trend-analytics feature had already widened `PilotAnalyticsInput` to include those two fields as required properties — so the fixture needed them there. **At the release candidate's own HEAD, with that uncommitted feature absent, `PilotAnalyticsInput` has no `currentDay`/`history` fields at all** — confirmed by reading the type definition directly inside the clean worktree (`frontend/src/pages/OwnerControlCenter/pilotAnalytics.ts`, 8 fields only: `generatedAt`, `periodLabel`, `orders`, `proposals`, `assignments`, `drivers`, `reactionTime`, `health`). TypeScript's excess-property check on a directly-typed object literal (`const SAMPLE_INPUT: PilotAnalyticsInput = { ... }`) correctly rejects the two extra fields once they no longer exist on the type. This defect was invisible to every prior verification (three build checks across two security gates and the release-candidate hygiene pass) because all of them ran inside the dirty working tree, where the mismatch does not occur.

## 3. Minimal fix

Removed the `currentDay: null, history: []` lines and their now-inapplicable explanatory comment from `SAMPLE_INPUT` in `frontend/src/pages/OwnerControlCenter/aiProvider.test.ts` — 13 lines deleted, nothing added, one file. The resulting fixture is **byte-identical** to its own pre-`02bd4bb` content (confirmed by git: the new blob hash, `e0380ee`, is exactly the blob hash this file carried before `02bd4bb` ever touched it). This is not a new invention — it is the correct, original contract, restored.

## 4. Why unrelated AI-Advisor work is not required

`PilotAnalyticsInput` itself was not modified — deliberately. Adding `currentDay?`/`history?` to it (the alternative rejected by this task's own instructions) would mean the release candidate's own type contract starts depending on concepts (`DailySnapshot`, day-bucketed trend data) that exist nowhere else in the committed codebase; the RC would be shipping a shape shaped by a feature it doesn't actually contain. The fixture, not the contract, was wrong — `SAMPLE_INPUT` only ever exists to prove `BackendAIProvider` forwards whatever `PilotAnalyticsInput` it's given unchanged (confirmed by reading the test immediately below it); it was never exercising `currentDay`/`history` specifically, so their absence changes no coverage.

## 5. Clean-worktree build result

Two independent clean-worktree checks were performed — one before committing (to determine and validate the fix), one after (the decisive, final verification against the actual commit):

| Check | Result |
|---|---|
| Clean worktree at pre-fix `HEAD` (`8b6fdd6`) | `npm run build` → **FAIL**, exact error reproduced (§1) |
| Same worktree, fix applied but uncommitted | `npm run build` → **PASS** |
| **Fresh, second clean worktree at the committed fix (`415ddf6`)** | `npm run build` → **PASS** — `tsc -b` clean, `vite build` completes, `dist/assets/index-BrMPNzJN.css` (29.55 kB) + `index-DtujZGZ7.js` (359.10 kB) generated, PWA precache built |

The second row is the decisive one: a brand-new worktree, checked out from the real commit history (not the same working directory the fix was developed in), with node_modules reachable only via a junction (no working-tree file copy of any kind) — proving the *committed* state builds, not merely the local edit.

## 6. Test result

`npm run test` in the same final clean worktree: **PASS** — 207/207 tests, 24/24 files. (207, not the dirty tree's 228 — expected and correct: the 21-test difference is entirely tests belonging to the uncommitted design-system/AI-Advisor work, which this worktree correctly does not contain.)

## 7. Lint result

`npm run lint` (oxlint) in the same final clean worktree: **PASS (clean)** — 0 errors. 11 pre-existing warnings (`no-unsafe-optional-chaining` × 10, one `react-hooks/exhaustive-deps`), all already present before this fix, none introduced by it.

## 8. Exact commit

```
415ddf6 fix(frontend): make release candidate build independently
```
1 file changed, 13 deletions(-) — `frontend/src/pages/OwnerControlCenter/aiProvider.test.ts` only. Not pushed.

```
git log --oneline -7
415ddf6 fix(frontend): make release candidate build independently
8b6fdd6 fix(frontend): send session token on protected ride mutations
02bd4bb fix(frontend): restore production build
9c6caea test(order-management): isolate dispatch proposal integration fixtures
4aa87bd fix(security): enforce order mutation authorization
3f2946a fix(security): enforce driver authorization on ride mutations
b701c6c feat(pios-taxi): complete first refusal runtime integration
```

## 9. Working-tree safety verification

`git diff --stat` for every file this task was explicitly forbidden to touch was checked before and after this fix, and found unchanged: `pilotAnalytics.ts` (+189/-0), `pilotAnalytics.test.ts` (+221), all seven `ai-advisor/**` files, `RideRequest.tsx` (452 lines), `PassengerLanding.tsx`/`.module.css`/`.test.tsx` — identical line counts before and after, confirming nothing else was modified, staged, or discarded. `git status --short` before this task and after this task's commit shows the same 91 unrelated entries, minus only the one file that is now committed instead of modified. No unrelated change was staged; only `frontend/src/pages/OwnerControlCenter/aiProvider.test.ts` was ever added to the index.

**Honest, explicit disclosure this section exists to make:** this fix intentionally means `npm run build` **will fail again if run inside the current dirty working tree**, exactly as it did before `02bd4bb` — because `pilotAnalytics.ts`'s own uncommitted `PilotAnalyticsInput` still requires `currentDay`/`history`, and this fixture no longer supplies them. This is not a regression; it is the direct, unavoidable, correct consequence of the release candidate no longer depending on that unrelated, uncommitted feature. The dirty tree's own build will be made to pass again naturally, in its own right, whenever the AI-Advisor trend-analytics work is itself reviewed and committed with its own necessary fixture update — not before, and not by this task.

## 10. Remaining blockers

- **`pios.session.secret`'s actual configured value for the live `identity`/`dispatch`/`driver-management`/`order-management` processes has not yet been safely established** — not resolvable by any read-only method available in this session; still requires the owner's own confirmation before any restart.
- **`POST /v1/orders` ownership verification remains an accepted, unresolved risk** — untouched by this or any prior task in this arc, per explicit, standing scope.
- **Deployment has NOT been performed.** No service was restarted or stopped. `frontend/dist/` in the real repository was not touched by this task (only the two temporary, fully-removed worktrees built their own, separate `dist/` output).
- **The unrelated dirty-tree changes remain exactly where they were** — design-system migration, the AI-Advisor feature itself, First Refusal, tooling/config, generated artifacts — none committed, none discarded, none altered.

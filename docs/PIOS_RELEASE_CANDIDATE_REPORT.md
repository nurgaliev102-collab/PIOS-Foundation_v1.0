# PIOS Release Candidate Report

**Date:** 2026-09-04.

## 1. Git state

| | |
|---|---|
| HEAD | `02bd4bb` — `fix(frontend): restore production build` |
| Branch | `pios-product-main` |
| Remote | `origin` → `https://github.com/nurgaliev102-collab/PIOS-Foundation_v1.0.git` |
| Ahead/behind | Ahead of `origin/pios-product-main` by **4** commits, 0 behind. **Not pushed.** |

## 2. Included commits

| Commit | Purpose | Verified |
|---|---|---|
| `3f2946a` | `fix(security): enforce driver authorization on ride mutations` — Proposal accept/decline, Assignment arrive/start/complete, Driver availability | ✅ 116/116 relevant tests (this + previous gate) |
| `4aa87bd` | `fix(security): enforce order mutation authorization` — Order cancellation, owner-passenger check | ✅ 13/13 relevant tests |
| `9c6caea` | `test(order-management): isolate dispatch proposal integration fixtures` — fixed-literal IDs → `UUID.randomUUID()`, no assertion changed | ✅ 15/15, run twice, stable |
| `02bd4bb` | `fix(frontend): restore production build` — one isolated fixture fix in `aiProvider.test.ts` only | ✅ `npm run build` PASS, `npm run test` 228/228, `npm run lint` clean |

## 3. Build

`npm run build`: **PASS**

```
tsc -b && vite build
✓ 166 modules transformed
dist/assets/index-DxsYURyr.js   363.44 kB │ gzip: 110.76 kB
dist/assets/index-Dr-D-Wz0.css   39.68 kB │ gzip:   7.14 kB
✓ built in 885ms
PWA v1.3.0 — precache 6 entries (394.42 KiB), sw.js generated
```

## 4. Frontend tests

`npm run test`: **PASS** — 228/228 tests, 24/24 files.
`npm run lint`: **PASS (clean)** — 0 errors; 13 pre-existing warnings, all confirmed unrelated to any commit in this release candidate (`no-unsafe-optional-chaining` × 12 in test files, one `react-hooks/exhaustive-deps` in `PassengerLanding.tsx` — none touched by `3f2946a`/`4aa87bd`/`9c6caea`/`02bd4bb`).

## 5. Security tests

**PASS** — 146/146 across `dispatch`, `driver-management`, `order-management`, in-memory and real (isolated) PostgreSQL:

`ProposalControllerTest` 63/63 · `AssignmentControllerTest` 25/25 · `AssignmentControllerPostgreSQLSecurityTest` 5/5 · `ProposalControllerPostgreSQLIntegrationTest` 15/15 · `DriverControllerTest` 22/22 · `DriverControllerPostgreSQLSecurityTest` 3/3 · `OrderCancellationControllerTest` 10/10 · `OrderCancellationControllerPostgreSQLSecurityTest` 3/3.

## 6. Database safety

Every Postgres-backed test in this release candidate's history ran against a hardcoded `_test`-suffixed database (`pios_dispatch_test`, `pios_driver_management_test`, `pios_order_management_test`), each confirmed distinct from its production-named counterpart, with no environment-variable or system-property override path in any `PostgreSQLTestDatabase.kt`. No full, unscoped `./gradlew build`/`test` was run at any point. No production database was connected to, queried, or modified.

## 7. Explicit accepted risk

**`POST /v1/orders` — passenger-ownership is NOT enforced. This is not fixed.**

Any caller who can reach `order-management`'s port can submit `POST /v1/orders` naming any `passengerReference`, creating an order that appears under a different person's own identity in every downstream screen. This is deliberately left as-is, for three reasons:

1. **The real, current frontend caller (`RideRequest.tsx`) does not send an `Authorization` header on this call at all** — adding a mandatory ownership check here today would break the one legitimate caller that exists, not merely close a gap.
2. **A correct fix requires a coordinated frontend + backend contract change** (the frontend would need to start sending its session token on order submission, and the backend would need to decide what "ownership" means at creation time, when no resource yet exists to own) — not a same-shape, drop-in fix like the six mutation endpoints already closed.
3. **The decision was consciously scoped out of both security gates and this release candidate**, matching the same, already-disclosed classification this gap carried in `docs/PIOS_TAXI_TASK_20_PROPOSAL_SECURITY_AUDIT.md` and `docs/PIOS_TAXI_TASK_24_REMAINING_MUTATION_API_SECURITY_AUDIT.md` before this work began — not newly discovered, not newly deferred.

## 8. Working tree

91 uncommitted entries remain (37 modified + 54 untracked), none of them part of this release candidate:

| Workstream | Representative paths | Why excluded |
|---|---|---|
| Design-system migration | `frontend/src/styles/`, `frontend/src/components/{Button,Card,...}/`, `PassengerLanding.*`, `RideRequest.*`, `docs/PIOS_DESIGN_*.md` | Explicitly out of scope — "не трогай дизайн" |
| AI Advisor / analytics feature | `ai-advisor/**` (Ollama/Qwen providers), `frontend/.../pilotAnalytics.ts`/`.test.ts` (currentDay/history trend context) | Explicitly out of scope — "не трогай AI Advisor architecture"; this gate deliberately built the build-fix (`02bd4bb`) *around* this file rather than inside it, specifically so it would stay untouched |
| Unrelated First Refusal test addition | `backend/dispatch/.../OrderSubmittedFirstRefusalConsumerIntegrationTest.kt` | A different, already-merged feature area (Task 17), not part of any security or build gate |
| Tooling/config | `.claude/CLAUDE.md`, `.claude/settings.json`, `.claude/skills/`, `CLAUDE.md`, `README.md` | Session/tooling configuration, not application code |
| Generated/backup artifacts | `.claude/settings.json.graphify-bak`, `graphify-out/`, `logs/` | Should never be committed at all (separate hygiene item, not this release candidate's concern) |
| Untracked infra script | `windows-services/cloudflared/run-tunnel.ps1` | Cloudflare-adjacent — explicitly "не трогай Cloudflare" |
| Unrelated task reports | `docs/PIOS_TAXI_TASK_17`–`44_*.md`, `docs/CLAUDE_CODE_ENVIRONMENT_AUDIT.md` | A separate task thread (production deployment / Cloudflare diagnostics), not this release candidate |
| Identity same-origin routing | `frontend/src/identity/BackendIdentityProvider.ts`/`.test.ts` | Pilot-infrastructure change from an earlier, separate task — not security, not build, not re-verified or re-scoped by this gate |

## 9. Deployment recommendation

**READY FOR PILOT DEPLOYMENT**

Scoped strictly to the four commits in §2: all driver- and passenger-owned mutation endpoints across `dispatch`, `driver-management`, and `order-management` are authorization-enforced (146/146 tests, including against real isolated databases), the frontend build is restored (verified clean, isolated fix), and no unrelated work was pulled in.

**Explicit accepted risk carried into deployment, restated per §7: `POST /v1/orders` does not verify that the caller owns the `passengerReference` they submit.** This is a known, disclosed, consciously-scoped-out gap — not silently accepted, not fixed, and not part of this release candidate's own claim of readiness. Deploying now means deploying with this specific gap still open.

Not performed by this report and still required before any of this reaches the live pilot: rebuilding the affected backend modules from this exact commit, and restarting the running Windows services — both need separate, explicit operator confirmation, per this project's standing production-safety discipline. `git push` was not run.

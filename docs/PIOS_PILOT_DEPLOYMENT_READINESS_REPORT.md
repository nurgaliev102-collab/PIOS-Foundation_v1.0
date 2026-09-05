# PIOS Pilot Deployment Readiness Report

**Date:** 2026-09-04. **Method:** read-only audit — no service restarted, no file deleted, no `git reset`/`restore`/`clean`, no `git push`, no business-logic change. Two things happened beyond pure inspection, both disclosed in full below because they materially affect the verdict: a temporary, non-destructive `git worktree` was created and removed to verify an isolated build of the release candidate; and three `npm run build` calls made during this and the two prior gates wrote to `frontend/dist/`, which — as this report discovers — the live `pios-frontend` service serves directly from disk with no restart required. Nothing was pushed.

## FINAL VERDICT: **NOT READY**

Not because the release candidate itself is broken — its four commits are exactly as reported. **Because deploying it as planned would break the ride flow it exists to fix**, and because an unreviewed, uncommitted frontend build is already live on the public pilot URL right now, as an unintended side effect of this session's own verification work. Both are explained in full below, neither was caused by a deliberate deployment action, and neither has been corrected — this report surfaces them for the owner, per this task's own explicit "не исправляй" instruction.

---

## 1. Git state

| | |
|---|---|
| Branch | `pios-product-main` |
| HEAD | `02bd4bb` — `fix(frontend): restore production build` |
| Ahead/behind `origin` | Ahead by 4, behind by 0. **Not pushed.** |
| Working tree | Dirty — 92 entries (37 modified, 55 untracked), unchanged in nature from the prior three gates: design-system migration, AI Advisor Ollama/Qwen providers + trend-analytics feature, an unrelated First Refusal test, tooling/config, generated artifacts, the untracked Cloudflare script, ~24 unrelated task docs, and this report itself. |

**All four required commits present and verified unchanged** (`git show -s` on each, hash-matched):
`3f2946a`, `4aa87bd`, `9c6caea`, `02bd4bb` — `git log 02bd4bb..HEAD` is empty, confirming HEAD is exactly `02bd4bb`, nothing committed after it.

**"Изменения после 02bd4bb" — precisely what they are, and the real risk they carry:** every one of the 92 dirty entries. The real risk is **not** "these might sneak into a git commit" (git itself prevents that) — it is that **any `npm run build` or `./gradlew build` run in-place, in this exact working directory, silently includes all 92 uncommitted files' content**, not just the four RC commits, because these build tools compile the filesystem as it stands, not `git log`. This was verified directly (§9) and is the report's central finding: it already happened, unintentionally, in this session's own build-verification calls.

## 2. Release Candidate integrity

To settle exactly what the four commits alone produce — separate from the question above — a temporary, detached `git worktree` was created at `02bd4bb` (outside the repository's own directory, in the session scratchpad), built there, and removed immediately after. This is the only way to answer "what does the RC alone contain" without touching the real working tree (no `stash`, no `reset`, no `clean`).

- `npm run build` (main working tree, dirty): **PASS** (unrelated to §2's own question — see §9).
- `npm run test`: **PASS**, 228/228, 24/24 files.
- `npm run lint`: **PASS (clean)**, 0 errors, 13 pre-existing warnings, none new.
- The four RC commits: **present, unchanged, verified by hash** (§1).
- **New finding from the isolated worktree, not available to the prior gate:** the RC's own frontend source (`DriverHome.tsx`, `RideRequest.tsx`, exactly as committed in `02bd4bb` — no later, uncommitted diff applied) sends **no `Authorization` header** on any of the six now-protected mutation calls (`respondToProposal`, `respondToAssignment`, `toggleAvailability`, `handleCancelOrder`). See §3/§7 for why this matters and §9 for the full evidence.

## 3. Backend deployment readiness

**Modules actually used by the pilot** (confirmed both by `windows-services/*.xml` and by live `Get-NetTCPConnection`): `driver-management` (8081), `passenger-experience` (8082), `order-management` (8083), `dispatch` (8084), `identity` (8086), `ai-advisor` (8091). `network-management` (8085) is **not** running — confirmed absent from `Get-NetTCPConnection`, consistent with ADR-037's own exclusion.

**Currently running services** (`Get-Service pios-*`): `pios-ai-advisor`, `pios-dispatch`, `pios-driver-management`, `pios-frontend`, `pios-identity`, `pios-order-management`, `pios-passenger-experience` — all `Running`/`Automatic`.

**The critical fact this audit establishes precisely** (`Get-CimInstance Win32_Process` process-start timestamps vs. `git show` commit timestamps):

| Backend JAR | Built | Process started | Security commit | Committed |
|---|---|---|---|---|
| `dispatch-0.1.0-SNAPSHOT.jar` | 2026-09-03 22:49 | 2026-09-03 23:07:52 | `3f2946a` | 2026-09-04 15:20:03 |
| `driver-management-0.1.0-SNAPSHOT.jar` | 2026-09-03 22:49 | 2026-09-03 23:02:14 | `3f2946a` | 2026-09-04 15:20:03 |
| `order-management-0.1.0-SNAPSHOT.jar` | 2026-09-03 22:49 | 2026-09-03 22:55:34 | `4aa87bd` | 2026-09-04 15:39:15 |

**The three running backend services that matter for this security work were built and started 16–17 hours before the security fixes were even committed.** The live backend today enforces **none** of the authorization added by `3f2946a`/`4aa87bd` — it is exactly the pre-fix state both security gates set out to close. The RC has not been deployed in any form yet.

**Environment variables** (per `windows-services/*/pios-*.xml`, read only — no value beyond what's already non-secret is repeated here): each backend `.xml` sets `PIOS_PILOT_FRONTEND_ORIGIN`, `PIOS_OWNER_USERNAME`, `PIOS_OWNER_PASSWORD_HASH`, `PIOS_OWNER_PASSWORD_SALT` directly in the service definition (not the OS environment) — confirmed present for `dispatch`, structurally identical for the other modules per the same file family. `pios.session.secret` (needed by every `SessionTokenVerifier` this security work relies on) was **not found set in any `.xml` inspected** — its actual source (OS environment, a module's own `application.yml`, or genuinely absent) was **not exhaustively traced module-by-module in this pass**; flagged as **UNCERTAIN**, and load-bearing: if unset in the live `identity`/`dispatch`/`driver-management`/`order-management` deployment, every session token verification fails closed (§ note below), which is safe but would also make the entire pilot unusable, not just the six newly-protected endpoints.

**Databases used** (per each module's own `application.yml`, unchanged from `PIOS_REALITY_AUDIT.md`'s own confirmed mapping, not re-derived here): `pios_driver_management`, `pios_passenger_experience`, `pios_order_management`, `pios_dispatch`, `pios_identity` — one PostgreSQL database per module, `postgres`/no-password, `127.0.0.1:5432`, the same shared local instance the `_test`-suffixed databases also live on.

**Safe Gradle tasks:** none were run against these production-named databases in this audit. Building a JAR (`./gradlew :module:bootJar` or `:module:build -x test`) does not connect to any database and is safe; `./gradlew :module:test` is **not** safe to run unscoped, per §4.

## 4. Database safety

**Not re-verified from scratch this pass — inherited, and re-confirmed still true by inspection, not by re-running anything destructive.** Every `PostgreSQLTestDatabase.kt` across `dispatch`/`driver-management`/`order-management`/`identity`/`passenger-experience`/`network-management` (checked in the two prior security gates, not reopened here) hardcodes a `_test`-suffixed database name with no override path — this is why those gates' own Postgres-backed tests were safe to run. **No test was run in this audit.** No SQL of any kind — read or write — was executed against any database, production or test, in this pass. `postgresql-x64-18` and `RabbitMQ` services are both confirmed `Running` (read-only `Get-Service` check).

## 5. Runtime

**Confirmed chain, end to end:**

```
Browser
  → https://home-pc.tail385153.ts.net  (Tailscale Funnel, HTTP 200, confirmed live this session)
  → 127.0.0.1:4173  (pios-frontend WinSW service, `vite preview`, serving frontend/dist/ directly off disk)
  → /v1/** requests proxied server-side, per frontend/vite.config.ts's own table, to:
      /v1/drivers      → 127.0.0.1:8081  (driver-management)
      /v1/connections  → 127.0.0.1:8082  (passenger-experience)
      /v1/orders       → 127.0.0.1:8083  (order-management)
      /v1/proposals, /v1/assignments → 127.0.0.1:8084  (dispatch)
      /v1/identities   → 127.0.0.1:8086  (identity)
      /v1/advisor      → 127.0.0.1:8091  (ai-advisor)
      /v1/health/<module> → the matching port above
  → each module's own PostgreSQL database (127.0.0.1:5432) and, for driver-management/order-management/dispatch, RabbitMQ (127.0.0.1:5672)
```

**Working:** every hop above, confirmed live (ports listening, services `Running`, external URL returns 200, a proxied, owner-gated endpoint returns 401 not a routing error — §6). `network-management` (8085) is correctly not part of this chain.

**Not working as intended:** the frontend currently being served at the top of this chain is not a reviewed build (§9) and the backend at the bottom of it does not yet contain the security work this whole readiness gate is about (§3).

## 6. External smoke-test readiness

All GET/read-only, no state changed:

| Check | Result |
|---|---|
| `GET https://home-pc.tail385153.ts.net/` | `HTTP 200` |
| Frontend assets referenced by that page | `assets/index-DxsYURyr.js`, `assets/index-Dr-D-Wz0.css` — both present and match what `frontend/dist/` currently holds (see §9 for why that's the finding, not just a pass) |
| `GET /v1/health/identity` (through the public proxy) | `HTTP 401` — endpoint reachable, correctly owner-credential-gated, not a routing failure |
| `GET /v1/drivers/health-check-nonexistent` (routing probe) | `HTTP 404` — confirms `/v1/drivers/*` is genuinely proxied to `driver-management`, not swallowed by the frontend's own router |

No 5xx, no obviously broken response, observed anywhere in this pass.

## 7. Known accepted risks

**Carried forward, unchanged, still not fixed (per this task's own explicit instruction not to touch it):**

`POST /v1/orders` — no ownership verification of the submitted `passengerReference`. **ACCEPTED RISK**, not addressed by any of the four RC commits, not addressed in this pass. Reasons unchanged from `docs/PIOS_SECOND_SECURITY_GATE.md` §3 and `docs/PIOS_RELEASE_CANDIDATE_REPORT.md` §7: the real frontend caller sends no `Authorization` header on this specific call, and a correct fix needs a coordinated frontend+backend contract change plus a Product Owner/Architect scoping decision.

**New in this pass, and more urgent — see §9 for full detail:**

- The true release-candidate frontend (built from `02bd4bb` alone) does not send `Authorization` on the six endpoints the RC backend now protects. Deploying RC-backend + RC-frontend together as currently committed would return `401` on every accept/decline/arrive/start/complete/availability-toggle/cancel action a real driver or passenger attempts.
- An uncommitted, unreviewed frontend build (design-system migration + AI-Advisor trend-analytics feature, mixed with the unrelated Authorization-header fix) is currently live on the public pilot URL, due to this and the prior two gates' own `npm run build` verification calls writing directly to `frontend/dist/`, which the running `pios-frontend` service serves from disk with no restart needed to pick up the change.

## 8. Deployment prerequisites

Before any deployment step in §9's plan is executed:

1. **Owner decision on the frontend Authorization-header gap (§7).** The needed fix already exists, uncommitted, in `DriverHome.tsx` (cleanly isolable — confirmed clean of any design-system content, mirrors the same pattern already used for the four RC commits) and in `RideRequest.tsx` (entangled with the design-system migration in that specific file — the same kind of hunk-inseparability problem `docs/PIOS_SECOND_SECURITY_GATE.md` §2 already solved once for `pilotAnalytics.ts`, by fixing the true point of breakage in an untouched file instead; the equivalent move here has not been attempted in this read-only pass).
2. **Owner decision on the already-live, unreviewed frontend build (§9).** Whether to knowingly keep it, revert `frontend/dist/` to a clean build of `02bd4bb` alone, or treat this as the moment the design-system/AI-Advisor work is deliberately reviewed and accepted — this is a product decision, not one this report makes.
3. **Confirmation of `pios.session.secret`'s actual configured value across all four verifying modules** (`identity`, `dispatch`, `driver-management`, `order-management`) before restarting anything — an unset or mismatched secret fails every session token verification closed, which would make the pilot broadly unusable, not just the six endpoints this work targets.
4. **Explicit owner go-ahead to restart live services** — not implied by this report, and not performed by it.

## 9. Exact deployment procedure

**Not executed. Prepared only.**

1. Resolve prerequisite #1 (§8): commit the frontend Authorization-header fix, isolated from unrelated design-system content, the same way `3f2946a`/`4aa87bd` isolated the backend fix.
2. Resolve prerequisite #2 (§8): rebuild `frontend/dist/` from a clean, reviewed source state (either the same commit used in step 1, or a `git worktree`-isolated build exactly as this audit used to verify `02bd4bb` in §2 — never an in-place `npm run build` in this working directory while it remains dirty, per this report's own central finding).
3. Build each affected backend module's JAR from the same reviewed commit, **not** in place against the current dirty tree: `cd backend && ./gradlew :dispatch:bootJar :driver-management:bootJar :order-management:bootJar` — or, more conservatively, from a dedicated `git worktree` checkout of the exact deployment commit, mirroring step 2.
4. **Stop** with explicit owner confirmation before this step: `pios-dispatch`, `pios-driver-management`, `pios-order-management` (`Stop-Service`), replace each module's `build/libs/*.jar` with the newly built one, `Start-Service` in any order (no inter-module start-order dependency, per `README.md`'s own statement).
5. **Stop** with explicit owner confirmation: `pios-frontend` (`Stop-Service`), confirm `frontend/dist/` holds the reviewed build from step 2, `Start-Service`. (In practice, per this report's own §9 finding, a frontend content swap does not strictly require stopping the service at all — but doing so anyway, deliberately and once, gives a clean, known restart point rather than another silent on-disk swap.)
6. Run the smoke tests in §10.
7. If any smoke test fails: follow §11's rollback.

**Explicitly not part of this plan and not authorized by this report:** `identity`, `passenger-experience`, `ai-advisor` restarts (untouched by the RC commits — no reason to restart them), any Cloudflare/Tailscale change, any database migration beyond what each module's own Flyway already runs automatically on its own JAR's startup.

## 10. Exact smoke-test procedure

After step 6 above, all read-only:

1. `curl -o /dev/null -w "%{http_code}" https://home-pc.tail385153.ts.net/` → expect `200`.
2. Confirm the served JS/CSS asset hashes changed from `index-DxsYURyr.js`/`index-Dr-D-Wz0.css` (this report's own recorded baseline) to the newly built ones.
3. `curl -o /dev/null -w "%{http_code}" -X POST https://home-pc.tail385153.ts.net/v1/proposals/00000000-0000-0000-0000-000000000000/accept` (no `Authorization` header, a syntactically valid but certainly-nonexistent id) → expect `401`, **not** `200`/`500` — proves the new backend authorization is actually active through the real public path, not just in a unit test.
4. Repeat the same shape for `/v1/assignments/.../arrive`, `/v1/drivers/.../availability`, `/v1/orders/.../cancel` → expect `401` on each.
5. A real, end-to-end driver+passenger walkthrough (registration → invite link → order → propose → accept → arrive → start → complete) — **requires a live human tester**, not a curl script; not performed by this report.

## 11. Rollback procedure

1. `Stop-Service pios-dispatch, pios-driver-management, pios-order-management` (and `pios-frontend` if it was restarted).
2. Restore each module's previous `build/libs/*.jar` (the ones currently in place, timestamped 2026-09-03 22:49, confirmed by this report — copy them aside *before* step 3/4 of §9, not after something goes wrong).
3. Restore `frontend/dist/`'s previous contents the same way, if it was touched — or accept that, per this report's own finding, the pre-deployment `dist/` was itself already an unreviewed build, not a known-good baseline to roll back to. **This is itself a reason to resolve prerequisite #2 (§8) before any forward deployment, so a rollback target actually exists.**
4. `Start-Service` each stopped service.
5. Repeat §10's smoke tests against the rolled-back state.

## 12. Requires owner confirmation

- Whether to commit the frontend Authorization-header fix now, and how to isolate it from `RideRequest.tsx`'s design-system content.
- What to do about the already-live, unreviewed frontend build (§7/§9) — revert, or knowingly accept.
- The actual, currently-configured value (not printed here) of `pios.session.secret` across all four verifying modules, and whether it is genuinely set and consistent.
- Explicit go-ahead for every `Stop-Service`/`Start-Service` step in §9 — none performed by this report.

---

## Summary

**1. Можно ли сейчас начинать deployment?** Нет. Не потому что сам release candidate плох (он именно такой, как заявлено), а потому что: (a) уже подтверждено, что RC-фронтенд без дополнительной, ещё не закоммиченной правки не отправляет `Authorization` на все шесть теперь защищённых мутаций — деплой в текущем виде вернёт 401 на каждое действие водителя и пассажира; (b) на публичном URL уже незапланированно раздаётся несогласованная, некоммиченная сборка (дизайн-система + AI Advisor фича) — побочный эффект собственных проверок `npm run build` в этой и двух предыдущих сессиях, без единого перезапуска сервиса.

**2. Что конкретно нужно сделать владельцу?** Принять решение по §8/§12 — в первую очередь про Authorization-заголовки на фронтенде и про уже вживую раздающуюся несогласованную сборку — прежде чем что-либо перезапускать.

**3. Какие команды нужно выполнить?** Ни одной — план в §9 подготовлен, но не выполнен, как и требовало задание.

**4. Что сознательно НЕ трогал:** дизайн-систему, First Refusal, AI Advisor, `POST /v1/orders`, любые другие незакоммиченные изменения (не удалены, не отредактированы); ни один `git reset`/`restore`/`clean`; ни один `git push`; ни один Windows-сервис не остановлен и не перезапущен; ни одна продовая или тестовая база не изменена — только `Get-Service`/`Get-NetTCPConnection`/`Get-CimInstance`/read-only `curl` и один временный, полностью удалённый `git worktree`.

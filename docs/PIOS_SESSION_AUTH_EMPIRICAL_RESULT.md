# PIOS Session Authentication Empirical Result

**Date:** 2026-09-04. **Scope:** read-only follow-up to one controlled diagnostic registration performed by the operator (Ильдар), through the live PIOS frontend, against the live `identity` service. This session performed no POST of its own in this task — the registration was executed by the operator directly; this session's role here was limited to read-only inspection of server-side evidence afterward. No file was edited, no service was restarted/stopped, no configuration or code was changed, no secret was rotated or generated, nothing was committed or pushed, no second POST was made, and the token was never printed, stored, decoded, or otherwise exposed by this session.

---

## 1. Test objective

Resolve the `AUTHENTICATION STATE UNKNOWN` verdict from `docs/PIOS_SESSION_AUTH_EMPIRICAL_CHECK.md` by determining, empirically, whether the specific, currently-running live `identity` process (PID 6532, listening on 8086 since 2026-09-01 22:47:04, unchanged throughout this entire investigation arc) can actually issue a session token — the one question no prior read-only method in this arc could answer, because the discriminating action (a real registration or a login with a correct password) was explicitly out of scope for every task before this one.

## 2. Test method

The operator performed exactly one registration attempt through the live PIOS frontend, using a clearly synthetic diagnostic phone number (`+15555550100`, from the NANP-reserved fictional-use range — not a real subscriber, and `identity`'s own `Phone` value object performs no reachability/carrier verification of any kind, so nothing was ever contacted) and a diagnostic password never shared with this session. The request went through the frontend's own `/v1/identities` proxy route (`vite.config.ts:51`, `'/v1/identities': 'http://localhost:8086'`) to the live `identity` service — the same path and request shape (`POST /v1/identities/register`, JSON body `{phone, password}`) `frontend/src/identity/BackendIdentityProvider.ts:57-65` sends for any real user's registration. This session's own part of the task was strictly read-only: inspecting `identity`'s log and process state after the fact, described below.

## 3. Synthetic identity

- Phone: `+15555550100` (NANP-reserved fictional-use range, chosen specifically so this test could not resolve to, notify, or affect any real person)
- Password: not recorded by this session, never requested, never seen
- Session token: not recorded by this session, never requested, never seen, never decoded

## 4. Registration result

Per the operator's direct report of the live outcome:

- **HTTP status: 201 Created**
- **Token issued: YES** — the response included a session token (value never seen or recorded by this session, per instruction)
- **Authenticated state: YES** — a `201` with a token, matching `IdentityController.register()`'s success path (`ResponseEntity.status(HttpStatus.CREATED).body(outcome.toResponse())`, `IdentityController.kt:81`), is only reachable if `RegisterIdentityApplicationService.handle()` completed all the way through `sessionTokenIssuer.issue(...)` (`RegisterIdentityApplicationService.kt:69`) without throwing — the exact call that throws `IllegalStateException` when `pios.session.secret` is blank (§6).

## 5. Identity service evidence

- `backend/logs/identity.log` was read again, in full, after the registration: **6,952 bytes, last modified 2026-09-01T23:03:12** — byte-for-byte identical to its state before this test. **No new log line was written for this request.**
- This silence is **not** evidence against success. It is the same behavior this arc's own prior report (`PIOS_SESSION_AUTH_EMPIRICAL_CHECK.md` §2) already established for this module: Spring Boot does not log ordinary 2xx/4xx responses here (no access-log valve, no request-logging filter configured) — only unhandled exceptions (`ERROR`) and framework warnings (`WARN`) are written. A clean, successful `201` is expected to leave **zero** trace in this file, exactly as observed.
- What the silence **does** positively establish: **the specific failure signature is absent.** The one thing that would unavoidably have appeared here had this exact registration attempt hit the secret-blank code path — the `ERROR`-level stack trace `IllegalStateException: pios.session.secret must be configured to issue a session token`, at `SessionTokenIssuer.issue → RegisterIdentityApplicationService.handle → IdentityController.register` (the exact shape captured historically in §6) — **did not appear.** Its absence, for the specific window this request occurred in, is a direct, verifiable, read-only fact, not an inference.
- Process identity, re-confirmed at the time of this check: **PID 6532**, `java.exe -jar identity-0.1.0-SNAPSHOT.jar`, CreationDate **2026-09-01 22:47:04**, unchanged from every prior check in this arc — and **the sole process bound to port 8086** (`Get-NetTCPConnection -LocalPort 8086` → `OwningProcess 6532`, single row). Because the frontend's proxy sends every `/v1/identities/*` request to `localhost:8086`, and nothing else is listening there, this specific, long-running process is the only thing that could have handled the operator's request — there is no ambiguity about which process is being evaluated.

## 6. Root cause

`SessionTokenIssuer.issue()` (`backend/identity/src/main/kotlin/com/pios/identity/application/SessionTokenIssuer.kt:44-45`) begins with `check(secretBase64.isNotBlank())`. This is the sole gate between "a registration completes with `201` + token" and "a registration throws `IllegalStateException` → uncaught in `IdentityController.register()` (`IllegalStateException` is not among the exceptions it catches) → Spring's default handler returns `500`, logged at `ERROR`." A `201` with a token is only reachable if this check passed — i.e., if `pios.session.secret` was non-blank, and internally consistent enough (§ ADR-055: base64-decodable) to actually compute an HMAC and encode a token, for this exact process, at the moment of this request.

**The historical failure this arc found on 2026-08-23** (`identity.log.2026-08-20.0.gz`, five occurrences of exactly this stack trace) was on a **different process** — Tomcat port `18086`, PIDs 2072 then 8996, both long gone. It proved the failure mode is real and reproducible in this codebase, but it was never proof about the process serving the pilot today. **This test replaces that inference with direct evidence from the actual live process (PID 6532, port 8086):** its own registration, right now, returned `201` and a token — the 2026-08-23 failure is confirmed **not representative of the current runtime**.

## 7. Confidence

| Question | Confidence | Basis |
|---|---|---|
| A registration through the live frontend, right now, returns `201` with a token | **VERIFIED** | Operator's direct, first-hand report of the live HTTP response |
| The current, long-running `identity` process (PID 6532) is what handled it | **VERIFIED** | Sole listener on port 8086 at the time of the request and at the time of this check; unrestarted throughout |
| No `pios.session.secret`-blank exception occurred for this request | **VERIFIED** | `identity.log` unchanged, byte-for-byte, after the request — the failure signature would have appeared here and did not |
| `pios.session.secret` is therefore configured and functional for this process, right now | **VERIFIED** | Only path to `201`+token per §6's code trace |
| The exact configuration channel supplying it | **NOT ESTABLISHED, and out of scope here** | This task's instructions forbid inspecting or exposing the secret value; §9 notes this as a closed, non-blocking question |

The previous `AUTHENTICATION STATE UNKNOWN` verdict is **resolved**. It was UNKNOWN specifically because no direct evidence existed for *this* process; that evidence now exists.

## 8. Required remediation

**None.** No secret rotation, generation, or configuration change was required to reach this result — the live `identity` process already had a working `pios.session.secret` before, during, and after this test; this session changed nothing. The residual, non-blocking item from `PIOS_SESSION_SECRET_GATE.md` §9 — that no git-tracked or generator-driven mechanism (`generate-service-xml.ps1`, the `.xml.template` files) currently accounts for how this value reaches the running processes — still stands as a **reproducibility/maintainability gap**, not a functional one: if `identity` is ever restarted without first locating and re-supplying whatever currently, successfully supplies this value, the working state confirmed here would not survive that restart. Locating and documenting that channel (without exposing its value) remains worth doing before any planned restart, but is no longer a blocking question for *today's* live authentication.

## 9. Deployment impact

This test only establishes that **`identity`'s registration/login path is live and functional today, on the currently-running process, unrestarted**. It does not, by itself, establish anything new about the other four RC-relevant findings already on record:
- The verifying modules (`dispatch`, `driver-management`, `order-management`, all restarted 2026-09-03 22:55–23:08, and `passenger-experience`) were confirmed, in the prior gate, to carry **none** of the six approved RC commits yet — that remains unchanged; this test did not touch them.
- Whether those four modules' own `SessionTokenVerifier` copies currently accept a token this same `identity` process just issued is a **separate, not-yet-tested** question (a verifier only needs the same secret bytes `identity` used, but "same" was never confirmed between modules — only that `identity`'s own copy works, right now, for itself).
- The `POST /v1/orders` ownership gap remains an accepted, untouched, undeployed risk, as throughout this arc.
- No restart, deployment, or push has occurred as a result of this test or this report.

---

FINAL STATE:
**AUTHENTICATION VERIFIED WORKING**

---

## Compact final execution summary

- **HTTP 201 Created** observed by the operator on a real registration through the live frontend, using a synthetic diagnostic phone (`+15555550100`) that touches no real person.
- **Session token was returned** — its value was never seen, requested, printed, stored, or decoded by this session.
- **The current, long-running live `identity` process (PID 6532, since 2026-09-01, sole listener on 8086, unrestarted) issued it** — confirmed by process/port inspection, not assumption.
- **No `pios.session.secret`-blank exception occurred**: `identity.log` was read in full before and after the test and is byte-for-byte unchanged — the one signature that failure would leave (the `IllegalStateException` `ERROR` stack trace) is absent.
- **The 2026-08-23 historical failure (a different, long-gone process on port 18086) is confirmed not representative of today's runtime.**
- **The prior `AUTHENTICATION STATE UNKNOWN` verdict is resolved** by this direct evidence.
- **No secret rotation, configuration change, restart, deployment, commit, or push occurred** — in this task or as a result of it.

Report written: `docs/PIOS_SESSION_AUTH_EMPIRICAL_RESULT.md`.

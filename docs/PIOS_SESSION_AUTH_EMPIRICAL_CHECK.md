# PIOS Session Authentication Empirical Check

**Date:** 2026-09-04. **Scope:** read-only, empirical follow-up to `docs/PIOS_SESSION_SECRET_GATE.md` (NO-GO). No file was edited, no service was installed/started/stopped/restarted, no secret was generated or rotated, no registration or login was performed, no database was queried or mutated, nothing was committed or pushed. No secret value appears anywhere below.

Legend: **VERIFIED** = established directly from a source read in this session · **UNKNOWN** = no read-only method available could establish it · **NOT APPLICABLE** = the question doesn't apply given what was found.

---

## 1. Current identity service

- Windows service `pios-identity`: `Running`, `Automatic` start type (`Get-Service`).
- Live process: `java.exe`, PID **6532**, command line `-jar "build\libs\identity-0.1.0-SNAPSHOT.jar"`, working directory `C:\Projects\PIOS-Foundation_v1.0\backend\identity`, **CreationDate 2026-09-01 22:47:04** (`Get-CimInstance Win32_Process`). This is the same PID and start time established in the prior gate — the process has not restarted since.
- Listening port: **8086** (`server.port` in `backend/identity/src/main/resources/application.yml:13`, confirmed live in §7).
- Log file: `backend/logs/identity.log` (Logback rolling file, `logging.file.name: ../logs/identity.log` relative to the module's working directory — `application.yml:24-25`), rotated daily/by-size into `identity.log.<date>.<n>.gz` in the same directory (`backend/logs/`).

## 2. Recent identity logs

`backend/logs/identity.log` (the live file for the **current, still-running** process) was read in full — 6,952 bytes, last modified **2026-09-01T23:03:12+05:00**, i.e. it has not grown or changed since roughly 15 minutes after the current process started, right through to now (2026-09-04). Its entire contents:

- Startup sequence, `22:47:23` → `22:48:20`: `IdentityApplicationKt` starts, "No active profile set, falling back to... default", Tomcat/Flyway/Hikari initialize cleanly, "Started IdentityApplicationKt in 64.277 seconds".
- `22:51:22`: `DispatcherServlet` lazily initializes on the **first** HTTP request this process received — Spring does not log the request's method, path, or outcome at this point (default Spring Boot behavior; no access-log valve or request-logging filter is configured in this module).
- `23:03:12`: one `Http11Processor` parse error — a request whose first bytes are binary/TLS-handshake-shaped (`0x16 0x03 0x03...`, a TLS `ClientHello` signature) arrived at the plain-HTTP port, rejected by Tomcat with `IllegalArgumentException: Invalid character found in method name`. This is a malformed/misdirected-protocol probe, unrelated to session-token issuance.
- Nothing else. No further log line of any kind exists after `23:03:12`.

**There is no log line, in the current process's entire lifetime, that names `/v1/identities/register`, `/v1/identities/login`, `SessionTokenIssuer`, `RegisterIdentityApplicationService`, or `LoginApplicationService`.** This is a hard limit on what this log can prove either way: Spring Boot does not log ordinary successful (2xx/4xx) responses by default, only unhandled exceptions (at `ERROR`, via the `DispatcherServlet`'s own logger) and framework-level `WARN`s (both of which this file does capture elsewhere — the Hikari `WARN`s and the parse-error `ERROR` above prove `WARN`/`ERROR` output is not being suppressed). So the silence since `23:03:12` means one of exactly two things, and this log cannot distinguish which: **(a)** no registration or login has been attempted against this process since then, or **(b)** one was attempted and *succeeded* (no exception → nothing logged). It positively rules out one thing: **no register/login attempt in this process's lifetime has thrown an uncaught exception** — if the secret-blank failure (§6) had fired even once, it would appear here as an `ERROR`-level stack trace, exactly as it does in the historical evidence below, and it does not.

## 3. Secret-related runtime evidence

Searched every identity log file, current and rotated (`backend/logs/identity.log*`, including `.gz`), for: `SessionTokenIssuer`, `pios.session.secret`, `IllegalStateException`, `secret`, `register`, `login`, `authentication`.

**Current process's log (`identity.log`, 2026-09-01 22:47 → present): ABSENT.** No match for any of these terms beyond the framework-startup and parse-error lines already described in §2.

**Historical logs (rotated `.gz` files): PRESENT — but on a different, earlier process.** `identity.log.2026-08-20.0.gz` contains five occurrences of exactly the failure the code predicts, on **2026-08-23** (Tomcat port **18086**, PIDs 2072 then 8996 — a different port than the live pilot's 8086, and a different process than the one running today):

```
2026-08-23T21:52:30.555+05:00 ERROR 2072 --- [identity] [http-nio-18086-exec-4] o.a.c.c.C.[.[.[/].[dispatcherServlet] :
  Servlet.service() for servlet [dispatcherServlet] in context with path [] threw exception
  [Request processing failed: java.lang.IllegalStateException: pios.session.secret must be
  configured to issue a session token] with root cause
java.lang.IllegalStateException: pios.session.secret must be configured to issue a session token
	at com.pios.identity.application.SessionTokenIssuer.issue(SessionTokenIssuer.kt:45)
	at com.pios.identity.application.RegisterIdentityApplicationService$handle$1.invoke(RegisterIdentityApplicationService.kt:69)
	at com.pios.identity.application.RegisterIdentityApplicationService$handle$1.invoke(RegisterIdentityApplicationService.kt:43)
	at com.pios.identity.application.RegisterIdentityApplicationService.handle(RegisterIdentityApplicationService.kt:43)
	at com.pios.identity.api.IdentityController.register(IdentityController.kt:78)
```

(Repeated at `21:53:05`, `21:54:37`, `21:55:29`, `21:56:53` — five real, distinct `POST /v1/identities/register` attempts, all failing identically.) No secret value appears in this trace or anywhere it was read from — only the class, message, and call stack.

**Interpretation, stated precisely:** this is **direct, empirical proof that this exact failure mode is real and has actually occurred** in this codebase, not merely a theoretical reading of the code. It is **not** proof that the *currently running* identity process (different PID, different port, started eight days later on 2026-09-01) is in the same state — that process's own log is silent on this question (§2). Task 1 verdict:

**Evidence of secret-related failure — PRESENT historically (2026-08-23, a prior/different process) · ABSENT in the current live process's own log (2026-09-01–present, no confirming or denying evidence either way).**

## 4. Process environment evidence

Attempted to determine whether PID 6532 (the live `identity` process) actually received a `PIOS_SESSION_SECRET` environment variable at creation. Windows provides no built-in, read-only mechanism to enumerate another process's environment block without either (a) administrative memory introspection (walking the target process's PEB via `ReadProcessMemory`/similar), which is an unsafe, invasive workaround explicitly out of scope here, or (b) third-party tooling (Process Explorer/Process Hacker) not present on this machine and not appropriate to install for a read-only gate. `Get-CimInstance Win32_Process` and `Get-Process` both expose the command line but not the environment block. `[System.Diagnostics.Process].StartInfo.EnvironmentVariables` is empty for any process this session did not itself start.

**UNKNOWN — running process environment inaccessible.** No unsafe workaround was attempted, per instruction.

## 5. Service startup configuration

Re-inspected, specifically looking for any injection path that would not appear in the previously-checked files:

- `windows-services/identity/pios-identity.xml` (the live, generated file) and `pios-identity.xml.template` (git-tracked source): both read in full. Neither contains a `PIOS_SESSION_SECRET` `<env>` entry or placeholder token (`__PIOS_SESSION_SECRET__`) anywhere. Only `PIOS_PILOT_FRONTEND_ORIGIN` (literal) and the three owner-credential placeholders are present. No `<prestart>`, `<poststart>`, or `<download>` WinSW elements exist that could set environment state dynamically.
- Repository-wide search of `windows-services/` for `%PIOS_SESSION_SECRET%` or any `%...%` WinSW-native expansion syntax: **no matches**. `generate-service-xml.ps1`'s own KDoc explicitly records *why* — WinSW's native `%VAR%` expansion was tried once and found unreliable for this exact purpose, so the project deliberately bakes literal values into `<env>` at generation time instead. That reasoning only covers the three owner-credential names; `pios.session.secret` was never added to it (confirmed again: `$commonSecretNames` in `generate-service-xml.ps1:32` lists only `PIOS_OWNER_USERNAME`, `PIOS_OWNER_PASSWORD_HASH`, `PIOS_OWNER_PASSWORD_SALT`).
- No `-D` JVM system property in any WinSW `<arguments>` element, in any of the five domain modules, live or template.
- No `config/` directory, no `application-<profile>.yml`, no `spring.profiles.active` anywhere (re-confirmed).

**VERIFIED: no mechanism exists in this repository's own tracked or generated configuration that could inject `pios.session.secret` at process startup without it appearing in one of the files already checked.** This does not rule out a manual, undocumented, one-off action taken outside of any file this session can read (§4's residual UNKNOWN covers exactly that case).

## 6. Authentication code path

Read `SessionTokenIssuer.kt`, `SessionTokenVerifier.kt` (identity's own copy), `RegisterIdentityApplicationService.kt`, `LoginApplicationService.kt`, and `IdentityController.kt` directly.

- `SessionTokenIssuer.issue()` (called by both register, unconditionally, and login, only after a correct password match) begins with `check(secretBase64.isNotBlank())` — throws `IllegalStateException("pios.session.secret must be configured to issue a session token")` immediately if blank, **before** doing anything else (`SessionTokenIssuer.kt:45`).
- `IdentityController.register()` catches only `PhoneAlreadyRegisteredException` (→409) and `IllegalArgumentException` (→400) — **`IllegalStateException` is not caught** and propagates out of the controller method uncaught (`IdentityController.kt:76-86`).
- `IdentityController.login()` does not wrap `loginApplicationService.handle(...)` in any try/catch at all — a *correct* password (the only path that reaches `issue()`) with a blank secret would also propagate the same uncaught exception (`IdentityController.kt:88-95`). A *wrong* password never reaches `issue()` and returns an ordinary 401, uninformative about the secret either way.
- An uncaught exception reaching Spring's `DispatcherServlet` is handled by its default resolver, which returns HTTP 500 and logs the full stack trace at `ERROR` — exactly the shape captured in §3's historical evidence.
- `SessionTokenVerifier.verify()` (guards `GET /v1/identities/{id}`, `GET /v1/identities/me`, `POST /v1/identities/{id}/driver`) behaves differently: a blank secret makes it return `null` **before** ever touching the secret's actual bytes, which the controller maps to a plain 401 — indistinguishable from "token present but invalid" or "no token at all." This is why §7's safe GET checks cannot resolve the question: verification fails closed *silently*, only issuance fails *loudly*.

**Net effect, precisely stated:** if `pios.session.secret` is blank for the live `identity` process, **every** registration attempt and **every** login attempt with a correct password returns **HTTP 500**, not 401 — a visibly broken response, not a quiet one. A login attempt with a wrong password still correctly returns 401 regardless of secret state.

## 7. Safe live checks

Read-only `GET` requests only, no credentials sent, no request body, no mutation:

| Request | Result |
|---|---|
| `GET http://127.0.0.1:8086/v1/health/identity` | `401 Unauthorized` — expected: `OwnerCredentialGate` correctly rejects a request with no `Authorization` header. Confirms the service is up and this endpoint is live; tells us nothing about `pios.session.secret` (different gate entirely, ADR-044). |
| `GET http://127.0.0.1:8086/` | `404 Not Found` — expected, no root mapping exists. |
| `GET http://127.0.0.1:8086/actuator/health` | `404 Not Found` — Spring Boot Actuator is not a dependency of this module (confirmed absent from `backend/identity/build.gradle.kts`); no generic health endpoint exists to query. |
| `GET http://127.0.0.1:8086/v1/identities/me` | `401 Unauthorized` — expected: no `Authorization` header means `SessionTokenVerifier.verify(null)` returns `null` immediately, **before** the secret is ever consulted (§6). This response would be identical whether the secret is configured or not — it is not evidence either way. |

**Established:** the service is reachable, listening on 8086, and every endpoint responds with exactly the HTTP status its code predicts for an unauthenticated `GET`. **Not established, and not establishable via GET/HEAD alone:** whether `pios.session.secret` is actually configured — per §6, only an *issuance* attempt (register, or login with a correct password) is discriminating, and both are explicitly forbidden by this task's rules.

## 8. Safe live checks — existing test account (Task 3)

No safe, already-existing authenticated session or non-production test identity was found that this session could exercise without violating the read-only/no-mutation rules:

- `pios_identity` is a live production-named database (not a `_test`-suffixed database) — per this project's own standing constraint (established across the whole security-remediation arc, root-caused in `PIOS_REALITY_AUDIT.md` §19), it was **not queried**, not even with a read-only `SELECT`, to look for an existing test row.
- `docs/MVR_PILOT_ACCEPTANCE_CRITERIA.md` (git history: last touched **2026-07-24**, well before ADR-055's session-token flow and this arc's frontend fixes) documents a **now-superseded** pilot mode: a hardcoded `CURRENT_DRIVER_ID` build-time constant in a file, `frontend/src/pages/DriverHome/currentDriver.ts`, that **no longer exists in the repository** (confirmed: not found). This describes an earlier pilot design that bypassed identity/session-token login entirely; it is stale documentation, not a currently-usable test path, and not something this gate should treat as still true.
- No browser-side `StoredIdentity`/session state was inspected — that would be a real user's private local data, out of scope regardless of the auth question.
- No Postman collection, seed script, or documented sandbox credential was found anywhere in `docs/`.

**Task 3 verdict: no safe existing authentication test is available.** The only discriminating test (an actual registration or login-with-correct-password attempt) is explicitly excluded by this task's own rules.

## 9. Evidence confidence

| Question | Confidence | Basis |
|---|---|---|
| The code, as written, throws HTTP 500 on any real issuance attempt when the secret is blank | **VERIFIED** | Direct source read, §6 |
| This failure has actually occurred in this codebase, empirically | **VERIFIED** | §3, five identical stack traces, 2026-08-23 |
| No config channel reachable by static inspection currently supplies the secret to any of the 5 domain modules | **VERIFIED** (prior gate, re-confirmed §5) | `application.yml`, live WinSW XML + templates, generator script, Machine/User registry |
| The *currently running* `identity` process (PID 6532, since 2026-09-01) has actually thrown this exception | **UNKNOWN** | §2 — its own log is silent on register/login entirely, in either direction |
| The *currently running* `identity` process's actual environment block contains the secret | **UNKNOWN** | §4 — not safely readable |
| A registration or login attempt made right now against the live service would succeed or fail | **UNKNOWN** | The one test that would answer this is explicitly forbidden by this task |

## 10. Required next action

The single action that would convert this from UNKNOWN to a definitive VERIFIED/BROKEN determination is an actual registration or login attempt against the live service — explicitly out of scope for this task. Recommended, in order of preference, entirely for the **operator** to choose from and execute:

1. **Lowest-risk:** the operator performs one real registration through the live frontend themselves (a normal pilot action, not a special test) and reports whether it succeeds (a token comes back) or fails with a 500. If it fails, `backend/logs/identity.log` will then show the same `IllegalStateException` stack trace as §3, at that timestamp — trivially confirmable read-only, after the fact.
2. **Equivalent, no user-facing action:** the operator inspects the actual `identity` process's environment directly (Task Manager → Details → right-click PID 6532 → "Open file location" doesn't show env, but a tool the operator already has admin rights to run, e.g. Process Explorer, does) to check name-presence of `PIOS_SESSION_SECRET` — resolving §4's UNKNOWN directly, still without ever disclosing the value to this session.
3. Only **if** either of the above confirms the secret is genuinely absent from the live process should a new secret be generated and wired in — per the standing instruction, this is not recommended here, because neither condition (confirmed broken, confirmed unconfigured) has been established by this task's own evidence.

No other action is required or recommended by this report.

---

FINAL STATE:
**AUTHENTICATION STATE UNKNOWN**

The historical evidence (§3) proves this exact failure is real and reproducible in this codebase; the configuration evidence (§5, and the prior gate) shows no currently-reachable channel supplies the secret to any live module. But the one log that could confirm or refute this for the **specific process serving the pilot right now** is silent in both directions (§2), its environment block cannot be read safely (§4), and the one test that would resolve it is explicitly excluded by this task's rules (§7, §8). Per instruction, this is reported as UNKNOWN rather than inferred as BROKEN from configuration absence alone — though the weight of evidence, taken together, points toward BROKEN, and the operator action in §10.1 is the fastest, lowest-risk way to convert this into a certainty.

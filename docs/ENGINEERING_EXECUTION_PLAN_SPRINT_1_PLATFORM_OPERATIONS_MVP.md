# Engineering Execution Plan: Sprint 1 — Platform Operations Center MVP

**Suspended by Product Owner decision, 2026-08-06, after T-1…T-4 Accepted.** Platform Operations Center is no longer treated as PIOS's critical path; T-5…T-12 below do not resume until the Product Owner explicitly reopens this sprint. This document's own Addendum ("Is T-5…T-12 required before a safe pilot with Artur and the first drivers?") had already found none of them were required for pilot safety — that finding is what this suspension acts on. See `docs/SPRINT_PILOT_BLOCKERS.md` for the work that replaced this sprint's engineering attention.

Status (pre-suspension, kept for record): **Stage 2 Preparation. Not an authorization to implement, and not a substitute for the ratification gate.** This document performs the consistency audit the Product Owner asked for, records the contradictions it actually found, proposes minimal document corrections **without applying any of them**, and lays out the dependency graph, engineering task breakdown, critical path and first task for Sprint 1.

Companion to: `IMPLEMENTATION_PLAN_SPRINT_1_PLATFORM_OPERATIONS_MVP.md` (scope) — this document is execution, that one is scope.
Sources audited: `PLATFORM_OPERATIONS_CENTER_VISION_V1.md`, `PRODUCT_DECISION_PLATFORM_OPERATIONS_CENTER.md`, `ADR-046`, `ADR-047`, `ADR-048`, `ADR-049`, `PIOS_SERVER_CONTROL_CENTER_DESIGN.md`, and — for the two claims that could only be settled by reading code — `HealthController.kt`, `OwnerCredentialGate.kt`, `frontend/vite.config.ts`.

**No document was edited by this task.** Every correction in Part 2 is a proposal.

---

## Part 0 — The three resolutions, as received

Recorded verbatim so that later readings do not drift from them.

> **O-1.** «Запустить PIOS» does NOT mean «start processes» — it means «bring the platform to its target working state.» The operation must be fully idempotent. If a service is already running — no action is taken. If it is not running — it starts. A Health check MUST then be performed. This becomes a mandatory architectural rule of Platform Operations Center.

> **O-2.** Sprint 1 operates local-network-only. Remote access via Cloudflare Tunnel, an owned domain, and external ingress are excluded from Sprint 1 and deferred to the next stage. Design nothing for external access right now.

> **O-3.** WinSW becomes the standard mechanism for managing Windows services. Platform Operations Center manages ONLY Windows Services. It never runs `gradlew`, `java -jar`, `npm`, or any other process directly.

These resolve O-1, O-2 and O-3 of `IMPLEMENTATION_PLAN_SPRINT_1_PLATFORM_OPERATIONS_MVP.md` Part 5. O-4 through O-9 of that Part remain open and are carried forward unchanged.

---

## Part 1 — Consistency audit

Nine findings. Four are real contradictions requiring a document act; three are corrections to my own Sprint 1 plan; two are confirmations that something is already consistent.

---

### F-1 — **REAL CONTRADICTION.** O-1's mandatory Health check versus ADR-046 Decisions 3 and 4

This is the most consequential finding in this document.

**The distinction offered as a possible reconciliation does not hold.** It was suggested that "health-check-as-internal-verification-step" and "health-as-a-displayed-status-fact" are different things and can coexist. They are indeed different, and ADR-046 **Decision 4** is only about the second one:

> The agent reports process-level facts obtained from the service manager and from files it owns. It does **not** proxy, mirror, cache or aggregate `GET /v1/health/<module>`. […] "Process is RUNNING" and "module is healthy" are **different facts**, shown side by side and never merged.

But ADR-046 **Decision 3** is about the first one, and it is categorical in its own heading:

> **3. The agent never asks a domain module anything.**

and in its body:

> The agent reports **only platform facts**: is this service running, since when, what the service manager says, what the last action did, what is in the log tail, what backups exist, which revision is deployed.

ADR-047 Decision 5 restates it as a structural property, not a policy:

> **No domain access.** […] ADR-046 Decision 3 makes this structural: **the agent has no client for those APIs at all.**

And `PIOS_SERVER_CONTROL_CENTER_DESIGN.md` §4.2 names the endpoint explicitly in its zero-calls row:

> Вызовы доменных API | **Нет ни одного.** Ни `/v1/orders`, ни `/v1/assignments`, **ни `/v1/health/<модуль>`**

A health check performed *by the agent*, at any point in an operation and whether or not its result is ever displayed, is the agent asking a domain module something. **Decision 3 forbids it. This is a conflict, not a nuance.**

#### F-1.1 — The credential fact, verified in code rather than inferred

`backend/dispatch/src/main/kotlin/com/pios/dispatch/api/HealthController.kt` line 41:

```kotlin
if (!ownerCredentialGate.verify(authorization)) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
}
```

`OwnerCredentialGate` (lines 58–60, 121–126) holds `pios.owner.username`, `pios.owner.password-hash` and `pios.owner.password-salt`, and verifies by deriving PBKDF2 over a **presented plaintext password**.

The consequence, stated plainly because it must not be buried in a task description:

> **For `platform-ops` to call `GET /v1/health/<module>`, the owner's observation password would have to be stored in plaintext in `platform-ops`'s own configuration.** The modules store only a hash and a salt; a caller must present the password itself.

That is a new architectural fact, not an implementation detail. It means the operations component would hold a credential for a surface ADR-047 Decision 1 deliberately separated from it, and it puts the owner's observation password on disk in a second place. ADR-011's Least Privilege applies directly.

#### F-1.2 — Three ways forward, with a recommendation

| Option | What it means | Document cost |
| --- | --- | --- |
| **A. Agent calls `/v1/health/<module>`** | The true check: it detects "process alive, database unreachable", which `HealthController` lines 45–61 already answer with `503` | Requires overriding ADR-046 D3 and D4, ADR-047 D5 and design §4.2, **and** accepting F-1.1's plaintext credential. Needs a new ADR that says so in those words |
| **B. Browser performs the check after the operation reports success** | Reuses the pattern ADR-046 D3 already blesses for the ride warning ("the **browser** renders any business-side warning") | No document change — but it cannot make the check *mandatory inside the operation*, because a browser-side check runs after `SUCCEEDED` is already reported, and a direct API caller skips it entirely. It does not deliver what O-1 asked for |
| **C. Agent performs a platform-level readiness verification** | After SCM reports `RUNNING`, the agent verifies the target's TCP port accepts a connection, within a bounded settle timeout, and converges to `READY` / `RUNNING_NOT_READY` / `FAILED` | No conflict with D3: a socket is a platform fact, not a question put to a domain module. **Discloses honestly that it does not detect "alive but broken"** — the state ADR-049 D1 already named as the most likely real-shift failure |

**Recommendation: C for Sprint 1**, with the limitation stated on screen and in the operation result, and with A available later as its own decision once the credential question in F-1.1 has been answered on its merits rather than as a side effect of a sprint.

**Explicitly rejected, because a developer will otherwise invent it:** calling `/v1/health/<module>` *without* credentials and treating the `401` as proof of liveness. It is still a call to a domain module (D3), and it launders a prohibited dependency into a status code.

#### F-1.3 — An ADR is required either way

O-1's own last sentence — *"This becomes a mandatory architectural rule of Platform Operations Center"* — is an instruction that a new architectural rule now exists. `CLAUDE.md` requires it to be recorded:

> **Never Change Architecture Without an ADR.** Any change to an architectural decision must be recorded as an Architecture Decision Record in `docs/ADR/` before or alongside the change.

**A new ADR-050 is not a change to any existing ADR**, so it does not violate the Product Owner's "no changes to any ADR" instruction. It is the mechanism that instruction's own repository requires. Proposed in Part 2 as C-1.

---

### F-2 — **REAL CONTRADICTION (mechanical).** O-1's idempotency versus design §3.4's `409`

`PIOS_SERVER_CONTROL_CENTER_DESIGN.md` §3.4, response-code table:

> `409` | Другое действие уже выполняется, **либо цель уже в запрошенном состоянии** | «Уже выполняется другое действие. Подождите»

O-1 says: *"If a service is already running — no action is taken"* — that is a **success**, not a `409`. The second clause of that row is now wrong. Proposed correction C-2.

---

### F-3 — **REAL CONTRADICTION.** O-2's local-network-only versus ADR-048 Decision 5

ADR-048 Decision 5 is not a recommendation:

> **Serving the operations hostname over `http://` is forbidden, not discouraged.**

and ADR-047's Consequences state the same as a precondition:

> **TLS is a precondition, not an enhancement.** […] If this surface is ever served over `http://`, everything in this ADR is worth zero.

O-2 removes the domain and the tunnel — which are exactly what made TLS free. On a home LAN with no domain, the options are plain HTTP (forbidden by the sentence above) or a self-signed certificate (a browser warning the owner must click through on the phone, every time).

**A sprint plan cannot waive a "forbidden".** This needs a document act.

**Recommendation:** a narrow, named, time-boxed exception — plain HTTP permitted **only** while the surface is bound to a private LAN interface and no external ingress exists, with the risk stated in the owner's own words: *anyone on the same Wi-Fi can read the operations password and stop the platform.* Preferred over a self-signed certificate because training the owner to click through certificate warnings is a worse long-term outcome than one disclosed, bounded risk. Proposed as part of C-1.

---

### F-4 — **CORRECTION TO MY OWN SPRINT 1 PLAN.** ADR-048 is not wholly deferred

`IMPLEMENTATION_PLAN_SPRINT_1_PLATFORM_OPERATIONS_MVP.md` Part 1.3 and Part 2 treat ADR-048 as load-bearing in full. After O-2 that is wrong — but "ADR-048 is now non-load-bearing for Sprint 1" is equally wrong. Precisely:

| ADR-048 | Sprint 1 status after O-2 |
| --- | --- |
| **Decision 1** — the control plane has its own ingress and does not traverse `vite preview` | **Still fully load-bearing.** On a LAN the phone reaches `platform-ops` directly on 8090. The failure mode D1 exists to prevent is unchanged: *"`vite preview` would become a dependency of the ability to restart `vite preview`."* See F-5 |
| **Decision 2** — zero rows added to `preview.proxy` | **Still fully load-bearing** and unchanged |
| **Decision 3** — external access gate | **Deferred by O-2** |
| **Decision 4** — stable hostname / named tunnel | **Deferred by O-2** |
| **Decision 5** — TLS mandatory | **In direct conflict — see F-3** |

Also: the Definition of Done's «с телефона» now means *a phone on the same local network*, and «без удалённого рабочего стола» is unaffected and still binding.

---

### F-5 — **REAL GAP, newly created by O-2.** Where the operations console UI is served from

My own Sprint 1 plan (Part 6.2) placed the `/owner/server` screens in `frontend/`, matching design §1.1. Under O-2 that becomes a trap:

- `frontend` is served by `vite preview`, which is itself a **controllable target** in the closed list.
- If the owner stops `frontend` from the phone, the console page already loaded keeps working (design §3.5 says exactly this) — but on any reload, refresh, tab switch or dropped connection, **the console is gone, and the only thing that could start `frontend` again was the console.**
- The Definition of Done then fails outright: the owner cannot control platform startup from the phone.

ADR-048 Decision 1 was what prevented this, by giving the control plane a hostname of its own. O-2 removes the hostname; the property must therefore be preserved by other means.

**Recommendation: `platform-ops` serves its own console assets on port 8090.** The UI may still be built inside the existing frontend project (reusing its tooling and styles), but its built output is deployed and served by `platform-ops`, not by `vite preview`. This keeps ADR-048 Decision 1's *property* true under LAN-only conditions, and it is what makes QA step 7 of the Sprint 1 plan ("the screen survives stopping `frontend`") passable rather than aspirational.

This is not gold-plating. Without it, one of the five capabilities is self-defeating.

---

### F-6 — **OPERATIONAL RISK, verified in the repository.** LAN reachability and interface binding

Three concrete facts, all read from files today:

1. **Design §3.0 states the base address as `http://127.0.0.1:8090` locally.** A service bound to `127.0.0.1` is unreachable from the phone. Under O-2, `platform-ops` must bind to **`0.0.0.0`** (or the machine's LAN IPv4 explicitly). This is a Sprint 1 configuration requirement, not a preference.
2. **Never rely on `localhost` resolution on this machine.** `frontend/vite.config.ts` lines 31–41 route every proxy row through `http://localhost:<port>`. On Windows 11 `localhost` resolves to `::1` before `127.0.0.1`, so a service bound only to IPv4 is reachable by one spelling and not the other. Whatever `platform-ops` binds to, and whatever any readiness check (F-1.2 option C) connects to, must be an explicit address — never the string `localhost`.
3. **`frontend/vite.config.ts` line 14 sets `preview.allowedHosts: ['.trycloudflare.com']`** — Vite rejects Host headers it does not recognise. A phone hitting `http://192.168.x.x:4173` presents an IP-literal Host, which Vite's DNS-rebinding protection normally exempts — **but this must be verified from an actual phone, not assumed.** If F-5 is adopted, this does not block the operations console; it still affects whether the pilot surface itself is usable from the LAN, which is outside Sprint 1's scope but will be noticed immediately.

Add to this the thing that silently breaks everything and appears in no design document: **Windows Firewall must permit inbound TCP 8090 on the Private profile.** A missing rule produces a phone that hangs, not an error message.

---

### F-7 — **CONFIRMED CONSISTENT.** O-3 versus ADR-046 and ADR-049 — with one distinction that must be named

O-3 is already the ADR package's position, verified rather than assumed.

ADR-046 Decision 2 already separates the two actors explicitly:

> Each controllable thing is registered once, **by an operator**, as a Windows service with a fixed name, working directory, command line and environment — **outside the agent, before the agent can do anything.** […] The agent then instructs the Service Control Manager for the resolved service name.

ADR-049's Alternatives already rejected the opposite by name:

> **Keeping `gradlew bootRun` and having the agent spawn processes directly.** Rejected: it requires the agent to compose command lines, which ADR-046 Decision 2 forbids by construction, and a spawned child dies with its parent.

**The distinction to state precisely, so it is never blurred:**

> **WinSW is an install-time actor. `platform-ops` is a runtime actor.** WinSW wraps `java -jar` (and `vite preview`, and `cloudflared`) into Windows services once, during operator setup, from configuration versioned in the repository. `platform-ops` at runtime never sees any of those command lines — it resolves a logical target through a closed build-time map to a Windows service name and instructs the SCM. Different actors, different times, no overlap.

No document change needed for Sprint 1.

**But name the future collision now.** O-3 as worded — *"never runs `gradlew`, `java -jar`, `npm`, or any other process directly"* — is inconsistent with **ADR-049 Decision 4** (backups run `pg_dump` "with a fixed executable path and fixed arguments") and **Decision 3** (updates run `git fetch`/`checkout` and `gradlew.bat`, per design §4.3). Neither is in Sprint 1, so nothing is blocked. But when Sprint 2 or 3 arrives, either O-3 gets a narrow named exception for fixed-path, fixed-argument tooling, or backups and updates need a different mechanism. Recorded now so it is not discovered as a surprise.

---

### F-8 — **CONFIRMED CONSISTENT, with a narrowing to state.** Vision versus Sprint 1 after O-2

`PLATFORM_OPERATIONS_CENTER_VISION_V1.md` §1 describes the owner as *«за рулём, дома, в другом городе, спит»*, and Scenario 1 («Ночью что-то остановилось») is the founding scenario of the whole product.

Under O-2, Sprint 1 covers the **«дома»** case only. From the road or another city, the owner can still see nothing and do nothing.

This is not a contradiction — a Vision describes v1 of the product, a sprint delivers a slice — but it must be said plainly so that **nobody declares Vision Scenario 1 complete at the end of Sprint 1.** The Vision's own success criterion 6.1.3 («владелец ни разу не был вынужден физически сесть за компьютер, чтобы восстановиться после ночного сбоя») is only partially reachable until external ingress lands.

Product Decision §6's *«Делать всё перечисленное удалённо, с телефона, не находясь рядом с компьютером»* is likewise half-met: not next to the computer, yes; genuinely remote, not yet. §11.2 is the open question O-2 has now answered as "deferred", which is a legitimate deferral and not a contradiction.

---

### F-9 — **UNCHANGED FROM THE PREVIOUS PLAN.** Citation drift

Carried forward, still not applied:

- `PIOS_SERVER_CONTROL_CENTER_DESIGN.md` cites the **superseded ADR-045** in §2.2, §3.0, §3.3, §3.4, §4.2, §17, §18.1 (step 2) and §19.
- `ADR-046` and `ADR-047` cite the Product Decision's **"Section 8"** for open questions that now live in **Section 11** (ADR-046 §8.5, §8.8; ADR-047 §8.1, §8.2). ADR-047 D5 cites "Section 5" for one-person-no-roles (actual: Section 8); ADR-047's Context cites "Section 10, point 3" for the accidental-stop criterion (actual: Section 13, point 3).

---

## Part 2 — Proposed document corrections — **NOT APPLIED**

Each is minimal. None has been made. All require the Product Owner's word.

| # | Document | Section | Proposed minimal change | Why |
| --- | --- | --- | --- | --- |
| **C-1** | **New: `docs/ADR/ADR-050-Operation-Convergence-and-Readiness-Verification.md`** | new file | One ADR recording three things: (a) an operation converges to a target state and is fully idempotent — a target already in the desired state is a no-op counted as success, not an error; (b) which readiness verification is performed after an action, and — critically — whether it is F-1.2 option **C** (platform-level, no domain call) or option **A** (calls `/v1/health/<module>`, which requires the plaintext owner credential in `platform-ops` and overrides ADR-046 D3/D4 and ADR-047 D5 by name); (c) the narrow LAN-only plain-HTTP exception to ADR-048 Decision 5 from F-3, bounded to "no external ingress exists" and stating the risk in the owner's own words | F-1, F-2, F-3. A **new** ADR, so it does not edit any existing one. `CLAUDE.md` requires a new architectural rule to be recorded before or alongside the change |
| **C-2** | `PIOS_SERVER_CONTROL_CENTER_DESIGN.md` | §3.4, response-code table | Delete «либо цель уже в запрошенном состоянии» from the `409` row. `409` retains only the concurrent-action meaning | F-2 |
| **C-3** | `PIOS_SERVER_CONTROL_CENTER_DESIGN.md` | §3.0, "Базовый адрес" row | Replace `http://127.0.0.1:8090` with a binding that is reachable on the LAN (`0.0.0.0:8090`), and add: never use the string `localhost` for any binding or readiness check on this machine | F-6 |
| **C-4** | `PIOS_SERVER_CONTROL_CENTER_DESIGN.md` | §1.1 | State that under LAN-only operation the console assets are served by `platform-ops` on 8090, not by `vite preview` | F-5 |
| **C-5** | `IMPLEMENTATION_PLAN_SPRINT_1_PLATFORM_OPERATIONS_MVP.md` | Part 1.3, Part 2 table, Part 5 (O-1/O-2/O-3 rows), Part 6.2 | Record the three resolutions; correct the ADR-048 mapping per F-4; move the console assets out of `frontend/` per F-5; close O-1/O-2/O-3 and carry O-4…O-9 forward | F-4, F-5, and the resolutions themselves |
| **C-6** | `PIOS_SERVER_CONTROL_CENTER_DESIGN.md`; `ADR-046`; `ADR-047` | as listed in F-9 | Re-point ADR-045 citations to 046–049; correct the Section 8/11 references | F-9 (hygiene, non-blocking) |

**C-1 is the only blocking one.** Tasks T-8 and T-9 below cannot start without it.

---

## Part 3 — Dependency graph

Every edge is grounded in a specific architectural fact, not in intuition.

```
                    T-0  GATE (ratification + ADR-050 + API contracts)
                     |
        +------------+------------------------------+
        |                                           |
      T-1 logs                                    T-3 platform-ops skeleton
        |                                          /   |        \
        |                                    T-4 LAN  T-6 journal  T-7 confirmations
      T-2 WinSW services                     reach     |            |
        |     \                                 |      +-----+------+
        |      \                                |            |
      T-5 SCM read                              |          T-8 convergence engine
        |          \                            |            |
        |           +---------------------------+------------+
        |                                       |
      T-10 errors (needs T-1)                 T-9 mutating endpoints
        \                                       /
         +----------------+--------------------+
                          |
                     T-11 console UI (served by platform-ops)
                          |
                     T-12 docs extension
```

| Edge | Grounded in |
| --- | --- |
| T-0 → everything | `CLAUDE.md` "No Implementation Before Documentation"; `.claude/CLAUDE.md` Stage 1 → Stage 2; ADR-006 (contracts before implementation) |
| T-1 → T-2 | **ADR-049 Decision 5**: once the modules become Windows services *"they have no console at all, so the logs stop existing entirely."* If T-2 runs first, T-2's own failures are undiagnosable |
| T-1 → T-10 | **ADR-049 Decision 5**: the errors endpoint parses log files. With no files, `GET /v1/ops/errors` can only return design §3.1's `503 LOG_FILE_NOT_CONFIGURED`, and cannot be tested |
| T-2 → T-5 | **ADR-046 Decision 2**: the agent reads state from the SCM. With no registered services there is no `state` to read |
| T-3 → T-4, T-6, T-7 | ADR-047 Decision 1: every endpoint, including read ones, is behind the ops credential. Nothing can be exercised before the gate exists |
| T-6 → T-8 | **ADR-047 Decision 4**: the journal is written *"on the attempt and on the result"* — it must exist before the first action, not after |
| T-7 → T-8 | **ADR-047 Decision 3**: every destructive operation is a two-step confirmation. Step two cannot exist before step one |
| T-5, T-7, T-6 → T-8 | ADR-049 Decision 2: convergence needs target resolution, ordering and per-step reporting |
| C-1 (ADR-050) → T-8 | F-1: the convergence engine's readiness step is undefined until C-1 chooses option A or C |
| T-8 → T-9 | design §3.4: the endpoints are thin wrappers over the engine; building them first would put the semantics in the controller |
| T-4, T-5, T-9, T-10 → T-11 | The console renders exactly these; F-5 requires it to be served by `platform-ops` |
| T-2, T-11 → T-12 | ADR-049 Consequences: `README.md` and `LAUNCH_CHECKLIST.md` stop describing reality once services replace `bootRun` |

---

## Part 4 — Engineering tasks

### T-0 — Clear the ratification gate *(Architect / Product Owner — not a developer task)*

- **Goal.** Make Stage 2 legitimate.
- **Result.** Explicit ratification of `PRODUCT_DECISION_PLATFORM_OPERATIONS_CENTER.md` and ADR-046…049; ADR-050 written and ratified (C-1); corrections C-2…C-5 applied; `API_SPECIFICATION.md` and `INTERFACE_CONTRACTS.md` entries for the eight Sprint 1 endpoints.
- **Dependencies.** None.
- **Definition of Done.** No document Sprint 1 depends on still reads "Черновик" / "Draft". ADR-050 states which readiness option (A or C) is chosen. The LAN plain-HTTP exception is recorded or rejected.

---

### T-1 — Rolling file logging in the five modules

- **Goal.** Make logs exist before the consoles that currently hold them disappear.
- **Result.** `logging.file.name` plus rolling-policy properties in five `application.yml` files, at a fixed log directory. No code, no dependency, no migration, no contract change.
- **Dependencies.** T-0.
- **Definition of Done.** Each of the five modules writes a rolling log file at the agreed path; retention matches the answer to O-8 (recommendation: 20 MB × 14 files, 500 MB cap); `./gradlew.bat :<module>:bootRun` still works unchanged for local development; `git diff` touches only five `application.yml` files.
- **Grounding.** ADR-049 Decision 5 (authorized there as a prerequisite, explicitly needing no ADR of its own).

### T-2 — WinSW service definitions for six targets

- **Goal.** Turn `bootRun` consoles into Windows services, per O-3.
- **Result.** `bootJar` artifacts for the five modules; a versioned WinSW XML per target (five modules, `frontend` as `vite preview`, plus `cloudflared` and `platform-ops` once T-3 exists) with fixed name, working directory, command line and environment — including `PIOS_PILOT_FRONTEND_ORIGIN`, read via `System.getenv` at context construction; SCM recovery actions per O-6.
- **Dependencies.** T-0, **T-1**.
- **Definition of Done.** Every target starts, stops and restarts from `services.msc` and `sc.exe`; every target survives a real reboot (verified by actually rebooting); every service's environment is complete — verified by loading the pilot surface from a phone and confirming no CORS failure; all WinSW XML is committed, none lives only on the machine.
- **Grounding.** ADR-046 Decision 2; ADR-049 Decision 1; O-3; design §4.4.

### T-3 — `platform-ops` skeleton and operations credential

- **Goal.** The deployable exists, is closed by default, and is reachable.
- **Result.** New top-level directory `platform-ops/` (sibling of `backend/` and `frontend/`); Spring Boot on port 8090 bound to `0.0.0.0`; an **independent copy** of the PBKDF2 credential check reading `pios.ops.*`; `GET /v1/ops/health`.
- **Dependencies.** T-0.
- **Definition of Done.** Missing or wrong credential → `401` with **no `WWW-Authenticate` header**; unconfigured `pios.ops.*` → `401` (fail closed); correct credential → `200`; the **owner** credential is rejected here, verified as an explicit negative test; no shared code with any module (`MODULE_STRUCTURE.md` §4); no database, no event, no domain dependency of any kind.
- **Grounding.** ADR-046 Decision 1; ADR-047 Decisions 1, 5; design §18.2.

### T-4 — LAN reachability and firewall

- **Goal.** Make «с телефона» true under O-2 before anything is built on top of it.
- **Result.** Explicit `0.0.0.0` binding verified; a Windows Firewall inbound rule for TCP 8090 on the Private profile; the machine's LAN address recorded in the operator documentation.
- **Dependencies.** T-3.
- **Definition of Done.** `GET /v1/ops/health` answers from a **real phone on the home Wi-Fi**, by IP address, with the laptop lid closed and nobody at the machine. No use of the string `localhost` anywhere in the binding or the verification. If plain HTTP is in use, the risk from F-3 is written down where the owner will read it.
- **Grounding.** O-2; F-3; F-6.

### T-5 — Closed target list and SCM read surface

- **Goal.** Report platform state from the service manager only.
- **Result.** Build-time `(logical name → Windows service name)` map; SCM query adapter; `GET /v1/ops/services` and `GET /v1/ops/services/{target}`.
- **Dependencies.** T-2, T-3.
- **Definition of Done.** Returns exactly the SCM's six states; a target outside the closed list is refused with `404` **before any other processing**; `platform-ops` is absent from its own list; `network-management` is absent; no string from any request reaches a command line, path, argument or environment variable; no `/v1/health` call and no JVM/CPU/memory field anywhere in the response.
- **Grounding.** ADR-046 Decisions 2, 4; ADR-049 Decision 2; design §3.1.

### T-6 — Append-only action journal

- **Goal.** PIOS's first audit trail, arriving with its first auditable action.
- **Result.** Append-only file writer (timestamp, action, target, outcome, source address, whether a token was consumed, `OWNER`/`SYSTEM` origin); `GET /v1/ops/audit`.
- **Dependencies.** T-3.
- **Definition of Done.** One line on the attempt and one on the result; refused attempts appear alongside successful ones; the credential and any token value never appear; **no API path deletes or edits it**.
- **Grounding.** ADR-047 Decision 4.

### T-7 — Confirmation mechanism

- **Goal.** An intent device, not an authentication device.
- **Result.** `POST /v1/ops/confirmations` returning a token bound to one `(action, target)` pair.
- **Dependencies.** T-3, T-6.
- **Definition of Done.** ≥128-bit value from a secure random source; memory only, never written or logged; single-use; 60-second default lifetime; at most one active globally, a new one invalidating the previous; replay, expiry, mismatched `(action, target)` and wrong phrase each produce `403`; an agent restart invalidates it.
- **Grounding.** ADR-047 Decision 3; design §3.3.

### T-8 — Idempotent convergence engine and readiness verification

- **Goal.** Implement O-1 as a mechanism, not as a controller behaviour.
- **Result.** Global single-flight executor; group ordering (start: PostgreSQL → RabbitMQ → five modules → `frontend` → `tunnel`; stop: reverse, **PostgreSQL and RabbitMQ excluded**); per-step state; convergence semantics; readiness verification per ADR-050; `GET /v1/ops/operations/{operationId}`.
- **Dependencies.** T-5, T-6, T-7, **and C-1 (ADR-050) ratified**.
- **Definition of Done.** Start against an already-running platform performs **no action** and reports success (F-2); a failed step halts the group and is reported as a partial result, never as success; restart is one operation and **does not start if the stop failed**; a second concurrent mutating request is refused with `409`; the readiness step behaves exactly as ADR-050 specifies and its limitation is reported in the result, not hidden.
- **Grounding.** O-1; ADR-049 Decision 2; ADR-047 Decision 3.

### T-9 — Mutating endpoints: start, restart, stop

- **Goal.** The three actions, built fix-before-break.
- **Result.** `POST /v1/ops/services/{target}/start`, then `/restart`, then `/stop` — in that order.
- **Dependencies.** T-8.
- **Definition of Done.** Each returns `202` with an `operationId`; the full response-code table of design §3.4 behaves as written, **minus `423`** (R-5, protection mode still open); every call produces before-and-after journal entries; the group stop leaves PostgreSQL and RabbitMQ running.
- **Grounding.** design §3.4; ADR-049 Decision 2.

### T-10 — Errors endpoint

- **Goal.** «увидеть ошибки».
- **Result.** `GET /v1/ops/errors?sinceHours=`, reading the same log files through a closed table, tail-first and bounded, grouping mechanically by (target, first line).
- **Dependencies.** T-1, T-3.
- **Definition of Done.** No importance judgement of any kind; never returns a file and has no download path; never accepts a path from a request; bounded read verified against a deliberately oversized log file; a module without a configured log file yields `503 {"reason": "LOG_FILE_NOT_CONFIGURED"}` rather than an empty list.
- **Grounding.** ADR-046 Decision 5; ADR-049 Decision 5; design §2.5, §3.1.

### T-11 — Operations console UI, served by `platform-ops`

- **Goal.** The five capabilities on a phone screen.
- **Result.** Login screen (with the mandatory "this is a different password" hint), status screen with all three states, errors screen, confirmation overlay with typed phrase and live countdown, minimal audit list — **built assets served by `platform-ops` on 8090**, per F-5.
- **Dependencies.** T-4, T-5, T-9, T-10.
- **Definition of Done.** QA steps 1–9 of `IMPLEMENTATION_PLAN_SPRINT_1_PLATFORM_OPERATIONS_MVP.md` Part 7 pass, including step 7 — **the console survives stopping `frontend` and can start it again after a full page reload**; the grey "cannot reach the agent" state disables the action buttons rather than erroring; not one file listed as untouched in that plan's Part 6.2 is modified; the only change to `/owner` is one navigation link that performs no action.
- **Grounding.** design §2.1, §2.2, §2.5, §2.9; ADR-046 Decision 6; F-5.

### T-12 — Extend the operator documentation

- **Goal.** Stop `README.md` and `LAUNCH_CHECKLIST.md` describing a machine that no longer exists.
- **Result.** Both documents **extended, not replaced**: service-based startup, WinSW, the LAN address, the firewall rule, and the fact that `bootRun` remains correct for local development.
- **Dependencies.** T-2, T-11.
- **Definition of Done.** Nothing deleted; the `bootRun` instructions retained and labelled as local development; a reader can perform the one-time setup from the document alone.
- **Grounding.** ADR-049 Consequences; `CLAUDE.md` "Never Delete Documentation".

---

## Part 5 — Critical path

Two paths tie at **seven nodes**:

- **P1 (declared critical):** `T-0 → T-1 → T-2 → T-5 → T-9 → T-11 → T-12`
- **P2 (co-critical):** `T-0 → T-3 → T-7 → T-8 → T-9 → T-11 → T-12`

**Critical path length: 7 nodes — 6 engineering tasks after the gate.**

**P1 is the one to protect**, even though the two are equal in node count, for reasons that are documented rather than intuitive:

- **T-2 is the largest single piece of work in the package.** ADR-046 Decision 2 calls it *"the largest operational prerequisite in this package"*, and it is the only task whose verification requires physically rebooting the machine.
- **T-2's risk is external.** It depends on O-3's WinSW behaving correctly with `java -jar`, on environment variables surviving into a service context, and on SCM recovery actions being configured correctly — none of which is provable by a unit test.
- **T-1 → T-2 is a one-way door.** Once the modules become services their consoles are gone (ADR-049 Decision 5). If T-1 is skipped or done badly, every subsequent task is debugged blind.

**Off the critical path, and therefore parallelizable:** T-4 (after T-3), T-6 (after T-3), T-10 (after T-1 and T-3). T-10 in particular can be built entirely in parallel with the whole mutating-endpoint chain.

**The one thing that can stall both paths at once is T-0 — specifically C-1 (ADR-050).** T-8 cannot begin without it, and T-8 sits on P2 while T-9 (which depends on T-8) sits on both.

---

## Part 6 — The first engineering task

> **T-1 — Rolling file logging in the five modules.**

Chosen for four reasons, all of them structural rather than preferential:

1. **It sits on the declared critical path**, and it is that path's first node after the gate.
2. **It is a hard prerequisite of two separate later tasks** — T-2 (ADR-049 Decision 5: services have no console) and T-10 (nothing to parse without files) — so it can never be deferred without cost.
3. **It depends on none of the unresolved questions.** It needs no ADR-050 decision, no credential, no network, no phone, no WinSW. Only O-8's retention numbers, and the recommendation there is already on the table.
4. **It is valuable on its own even if Sprint 1 stops immediately afterwards.** ADR-049's own Consequences say it: *"Logs begin to exist, which makes every future diagnosis cheaper — including diagnoses that have nothing to do with this console."*

It is also the smallest task in the sprint: five `application.yml` files, no code, no dependency, no migration, no contract.

**T-1 may not start until T-0 clears.** As of this document, it has not: every source document still reads Draft, and ADR-050 does not exist.

---

## Files Changed (this task)

`docs/ENGINEERING_EXECUTION_PLAN_SPRINT_1_PLATFORM_OPERATIONS_MVP.md` (new). **No other file created or modified.** No ADR, Product Decision, Vision, design document or implementation plan was edited — the six corrections in Part 2 are proposals awaiting the Product Owner's word. No source code, build file, migration, API or event contract was touched.

---

## Addendum (2026-08-06) — Roadmap update after T-1…T-4 Accepted

T-0's gate is closed (Product Decision + ADR-046…050 ratified). T-1, T-2,
T-3 and T-4 are each individually **Accepted** — see
`IMPLEMENTATION_PLAN_SPRINT_1_PLATFORM_OPERATIONS_MVP.md`'s own
Implementation Status table for dates, and
`ENGINEERING_JOURNAL_SPRINT_1_PLATFORM_OPERATIONS_MVP.md` J-1 for the false-404
investigation T-4 triggered. This addendum re-assesses T-5…T-12 with what
implementation actually taught, rather than what was estimated before any
code existed. Original Part 4 task definitions are unchanged; this adds
current status, not a rewrite.

### T-5 — Closed target list and SCM read surface

- **Goal.** `platform-ops` reports each target's `Running`/`Operational`/
  `Verified` state (ADR-050) by querying the Windows Service Control
  Manager only — never asking a module about itself.
- **Complexity: Medium** (revised up from what the original task read like
  on paper). The real reason: Kotlin/Java has no built-in binding to the
  Windows SCM. T-5 must resolve, not inherit, a choice between (a)
  shelling out to `sc.exe query` and parsing text output — zero new
  dependency, consistent with `platform-ops/build.gradle.kts`'s
  deliberately minimal dependency set (T-3) — or (b) adding JNA (Java
  Native Access) for a real Win32 API call — more robust, but the first
  non-Spring dependency this deployable would carry. **Not yet decided.**
- **Dependencies.** T-2 (Accepted — the six real service names,
  `pios-driver-management` through `pios-frontend`, are now known facts,
  not placeholders), T-3 (Accepted — the skeleton exists to add this to).
  **Fully unblocked.**
- **Remaining scope.** All of it — no code exists yet. The
  (logical name → Windows service name) map is now mechanical to write
  (T-2 already fixed every name); the query adapter and the two endpoints
  are the real work.

### T-6 — Append-only action journal

- **Goal.** Every attempted and completed action is written to an
  append-only file, readable via `GET /v1/ops/audit`.
- **Complexity: Low.** Straightforward file I/O against a well-specified
  record shape. No concurrency concern yet (T-8's single-flight executor
  isn't built; T-6 only needs to be safe for a single caller today).
- **Dependencies.** T-3 (Accepted). **Fully unblocked.**
- **Remaining scope.** All of it. One small open point worth naming before
  starting, same shape as T-1's own log-directory decision: where this
  file lives has not been chosen — it is not one of the T-1 rolling logs
  and needs its own short, documented answer, not an implicit default.

### T-7 — Confirmation mechanism

- **Goal.** A destructive action requires a prior confirmation step that
  produces a short-lived, single-use token bound to one `(action, target)`
  pair.
- **Complexity: Low-Medium.** In-memory store, secure-random token,
  single-active-globally invalidation — mechanical once its dependency
  exists.
- **Dependencies.** T-3 (Accepted), **T-6 (not started)** — the journal
  must exist first; ADR-047 Decision 4 requires the attempt logged before
  the action, and a confirmation is itself an attempt.
- **Remaining scope.** All of it; blocked on T-6 only.

### T-8 — Idempotent convergence engine and readiness verification

- **Goal.** Implement O-1's rule — idempotent convergence to a target
  state, with mandatory readiness verification — as the actual mechanism
  behind start/restart/stop.
- **Complexity: High**, and higher than the original plan scoped, for a
  concrete reason found only after ADR-050 existed: **ADR-050 Decisions 2
  and 3 require a new `GET /v1/internal/verification` endpoint on *each of
  the five PIOS backend modules*** for the `Verified` tier to be anything
  more than a stub — this is real, new work inside `backend/`, and it is
  not currently named as its own task anywhere in Part 4. T-8 cannot
  honestly claim ADR-050 compliance without it. **This is a gap in the
  plan, not a decided detail** — it needs either a new task number
  (before or alongside T-8) or an explicit, written expansion of T-8's own
  scope to include five small, identical backend changes. Left unresolved
  here on purpose — a planning gap should be named, not silently absorbed
  into an estimate.
- **Dependencies.** T-5, T-6, T-7 (none started), and the five-module
  verification endpoint above (not scoped anywhere yet). ADR-050's own
  ratification — **the one blocking dependency this task originally
  carried that is now cleared.**
- **Remaining scope.** All of it, plus the newly-surfaced backend-side
  prerequisite. The largest remaining task in the sprint by a clear
  margin.

### T-9 — Mutating endpoints: start, restart, stop

- **Goal.** Expose the three actions as `POST /v1/ops/services/{target}/
  {start|restart|stop}`.
- **Complexity: Medium.** The hard logic lives in T-8; T-9 itself is a
  comparatively thin controller layer — request validation, wiring to
  T-7's confirmation token, response shape per design §3.4.
- **Dependencies.** T-8 (not started). **Fully blocked** until T-8 lands.
- **Remaining scope.** All of it.

### T-10 — Errors endpoint

- **Goal.** «увидеть ошибки» — read recent errors out of the T-1 rolling
  log files.
- **Complexity: Low-Medium.** No new dependency, but real care required:
  bounded, tail-first reading against files that can reach 20 MB × 14
  (T-1's own retention config), mechanical grouping by `(target, first
  line)`, and the documented `503 LOG_FILE_NOT_CONFIGURED` case.
- **Dependencies.** T-1 (Accepted), T-3 (Accepted). **Fully unblocked —
  and independent of the T-5→T-9 chain**, exactly as the original
  critical-path analysis already noted (parallelizable).
- **Remaining scope.** All of it; zero blockers today.

### T-11 — Operations console UI, served by `platform-ops`

- **Goal.** The five Sprint-1 capabilities on a phone screen, served by
  `platform-ops` itself on 8090 — not `vite preview` (F-5: the console
  must not depend on the very process it might need to restart).
- **Complexity: High.** Five screens, plus a real, currently undecided
  architecture question this task must resolve, not inherit: does
  `platform-ops` get its own frontend build toolchain (React/Vite,
  mirroring `frontend/`), or a deliberately minimal, no-build-step static
  HTML/CSS/JS bundle served as Spring static resources? The second is more
  consistent with this deployable's demonstrated minimalism (T-3's bare
  dependency list) and with design's own "vводить нечего" instinct for the
  Owner-facing surfaces, but has not been decided anywhere.
- **Dependencies.** T-4 (Accepted), T-5, T-9, T-10 (none started).
  **Blocked on three tasks, none of which have begun.**
- **Remaining scope.** All of it.

### T-12 — Extend the operator documentation

- **Goal.** Bring `README.md` and `LAUNCH_CHECKLIST.md` up to date with the
  real, service-based machine — extended, not replaced (`bootRun`
  instructions stay, labelled as local development).
- **Complexity: Low.** Pure documentation, mechanical once T-11 exists to
  describe. Some of this groundwork already exists informally
  (`platform-ops/README.md`, this sprint's own journal and status table)
  but the *formal* task — editing the actual root `README.md` and
  `LAUNCH_CHECKLIST.md` — has not happened.
- **Dependencies.** T-2 (Accepted), T-11 (not started).
- **Remaining scope.** Low effort, but gated entirely on T-11.

### Updated critical path

Original: `T-0 → T-1 → T-2 → T-5 → T-9 → T-11 → T-12` (7 nodes) tied with
`T-0 → T-3 → T-7 → T-8 → T-9 → T-11 → T-12`. With T-0…T-3 (and T-4) done,
what remains is:

```
T-5 ┐
T-6 ┼→ T-8 → T-9 → T-11 → T-12
T-7 ┘
```

T-5, T-6 and T-7 are mutually parallelizable (each depends only on
already-Accepted T-2/T-3) and all three feed **T-8** — now the sprint's
single largest task, carrying the newly-found backend-verification-endpoint
gap above. **T-10 remains independently parallelizable at any point**, with
no effect on the critical path either way.

### The main question, answered separately below

Whether any of T-5…T-12 is a *required* precondition for a safe pilot with
Artur and the first drivers — as opposed to valuable operational tooling —
is answered in its own section, since the two questions have different
answers and should not be blended.

---

## Is T-5…T-12 required before a safe pilot with Artur and the first drivers?

**No — not one of them.** The mandatory minimum for pilot safety was
already satisfied by T-1 and T-2, both already Accepted. This is stated as
a finding, not an assumption — grounded in what these tasks actually are,
not in what would be convenient to build.

**Why T-5…T-12 sit outside pilot safety, structurally, not by choice.**
Everything from T-5 onward is Platform Operations Center surface — and
ADR-046 Decision 1 already settled that this tool is not part of PIOS: it
owns no capability, models no domain, holds no data, and (ADR-046 Decision
3) never asks a domain module anything. A driver accepting a proposal, an
order being submitted, a passenger seeing a working link — none of these
paths touch `platform-ops` in any way, whether or not it has a status
screen, an errors endpoint, or a start/stop button. Confirmed independently:
`docs/LAUNCH_CHECKLIST.md`, this project's own existing and authoritative
pilot-readiness document, does not mention Platform Operations Center,
`platform-ops`, or any T-number from this sprint anywhere in its text —
it was written, and would be satisfied or not, entirely independently of
this thread's work.

**What *was* the actual safety-relevant question, and it is already
closed.** The real risk this whole Sprint exists to close (§1 of
`PLATFORM_OPERATIONS_CENTER_VISION_V1.md`) was: *if something stops
overnight, does the platform recover without a human, or does it stay down
until someone manually intervenes?* That question is answered by T-1 (logs
exist, so a failure is diagnosable) and, decisively, by **T-2**: all six
services are registered `Automatic` with Service Control Manager recovery
actions, and this was verified — not assumed — by an actual, physical
reboot of the machine with nothing started by hand afterward (T-2's own
acceptance evidence). That property does not depend on `platform-ops`
existing, being reachable, or having a UI — ADR-049 Decision 1 deliberately
put recovery at the operating-system level for exactly this reason: *"it
works while the agent is dead."*

**What remains genuinely absent, named plainly, not smoothed over.**
Without T-5…T-12, the owner has no *remote, mobile* way to see a degraded
(not crashed) service, force a restart by hand, or read errors without
physically sitting at the machine. This is a real gap in operational
convenience and incident-response speed — it is not, on its own, a pilot
*safety* gap, because the platform's baseline behavior (serve traffic, or
autonomously recover if a process dies) does not depend on it. Naming this
distinction precisely, per the Product Owner's own instruction not to list
desirable improvements: T-5…T-12 are exactly that — desirable, not
mandatory — and are excluded from this answer on that basis, not omitted by
oversight.

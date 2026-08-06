# Implementation Plan: Sprint 1 — Platform Operations Center MVP

Status: **Suspended by Product Owner decision, 2026-08-06, after T-4.** Platform Operations Center is no longer treated as PIOS's critical path. T-1 through T-4 remain **Accepted** and are not reverted — the recovery/logging/reachability properties they established stand as-is and required no further work to be pilot-safe (see `ENGINEERING_EXECUTION_PLAN_SPRINT_1_PLATFORM_OPERATIONS_MVP.md`, "Is T-5…T-12 required before a safe pilot", which had already reached this same conclusion independently). T-5 onward do not resume until the Product Owner explicitly reopens this sprint. Engineering attention moves to Sprint "Pilot Blockers" (`docs/SPRINT_PILOT_BLOCKERS.md`) — real, first-pilot blockers found in PIOS's own core product, not in this tool.

Status before suspension: **Stage 2 in progress.** `PRODUCT_DECISION_PLATFORM_OPERATIONS_CENTER.md` and ADR-046…050 were ratified by the Product Owner on 2026-08-06, closing Part 0's gate. Implementation proceeded strictly task-by-task against `ENGINEERING_EXECUTION_PLAN_SPRINT_1_PLATFORM_OPERATIONS_MVP.md`, one task at a time, each requiring explicit Product Owner acceptance before the next began — see Implementation Status below. This document itself still designs nothing new: every endpoint, screen and constraint below remains pulled by reference from `PIOS_SERVER_CONTROL_CENTER_DESIGN.md` and the ADR-046…050 package.

Scoped from: the Product Owner's Sprint 1 instruction of 2026-08-06 (quoted verbatim in Part 1).
Grounded in: `PRODUCT_DECISION_PLATFORM_OPERATIONS_CENTER.md`, `ADR-046`, `ADR-047`, `ADR-048`, `ADR-049`, `ADR-050`, `PIOS_SERVER_CONTROL_CENTER_DESIGN.md` Sections 2–4, `PLATFORM_OPERATIONS_CENTER_VISION_V1.md`.

---

## Implementation Status

Tracked here as each task in `ENGINEERING_EXECUTION_PLAN_SPRINT_1_PLATFORM_OPERATIONS_MVP.md` Part 4 is accepted. Not a duplicate of that document's own task definitions — this table records outcome and date only.

| Task | Status | Date | Note |
| --- | --- | --- | --- |
| T-1 — Rolling file logging | **Accepted** | 2026-08-06 | Five modules write to `backend/logs/`; verified live |
| T-2 — WinSW services for six targets | **Accepted** | 2026-08-06 | All six services installed, `Automatic`, survived a real reboot |
| T-3 — `platform-ops` skeleton and ops credential | **Accepted** | 2026-08-06 | Independent Gradle project; auth verified (missing/wrong/correct/owner-credential cases) |
| T-4 — LAN reachability and firewall | **Accepted** | 2026-08-06 | See `ENGINEERING_JOURNAL_SPRINT_1_PLATFORM_OPERATIONS_MVP.md` for the false-404 investigation this task's phone verification triggered; root cause was a malformed request path, not network/Firewall/`platform-ops` |
| T-5 onward | **Suspended** | 2026-08-06 | Product Owner decision: Platform Operations Center is no longer PIOS's critical path. Not started, not scheduled; resumes only on explicit Product Owner instruction. |

---

## Method: Evidence Grading

Same convention as every preceding Product Decision and Implementation Plan in this repository:

- **[RATIFIED]** — observed directly in an approved document or in committed code. *Caveat specific to this sprint: for this package, "approved" is not yet true of anything — see Part 0. Where used below, [RATIFIED] means "written down and cited", not "ratified by the Product Owner".*
- **[DERIVED]** — a plan-level choice reasoning from that material.
- **[OPEN]** — a real fork this document does not resolve, flagged for a decision before code is written.

---

## Part 0 — THE GATE. Read this before anything else in this document.

**Nothing in Parts 1–8 may be handed to a Developer while the documents this sprint depends on are still marked Draft.**

`CLAUDE.md` (repository root) states two rules that bear directly on this sprint:

> **No Implementation Before Documentation.** Do not write backend code, frontend code, database schemas, or business logic until the relevant documentation exists and has been reviewed.

> **Never Change Architecture Without an ADR.** Any change to an architectural decision must be recorded as an Architecture Decision Record in `docs/ADR/` before or alongside the change.

`.claude/CLAUDE.md` states the workflow: Stage 1 (Architect) → Stage 2 (Developer) → Stage 3 (QA), where Stage 1 either approves the direction, requests an ADR, or blocks the change. This document is Stage 1 output. Stage 2 does not follow automatically from it.

### 0.1. Current status of every document Sprint 1 depends on

Verified by reading each file on 2026-08-06:

| Document | Status line, as written today |
| --- | --- |
| `PRODUCT_DECISION_PLATFORM_OPERATIONS_CENTER.md` | «Черновик — ожидает ратификации Product Owner.» |
| `ADR-046-Platform-Operations-Component-Boundary.md` | "Draft — awaiting Product Owner ratification." |
| `ADR-047-Destructive-Action-Authorization.md` | "Draft — awaiting Product Owner ratification." |
| `ADR-048-Operations-Control-Plane-Ingress.md` | "Draft — awaiting Product Owner ratification." |
| `ADR-049-Platform-Recovery-and-Change-Safety.md` | "Draft — awaiting Product Owner ratification." |

Every one of them is a draft. Each one says so in its own first lines.

### 0.2. What must be ratified before Stage 2 begins — this plan's judgment

**All five**, not four. Sprint 1 simultaneously touches the component boundary (046), the authorization of destructive actions (047), the remote ingress path (048) **and** the service-registration, group-ordering and log-file prerequisites that live in 049. Part 2 shows this capability by capability.

Additionally, per ADR-006 and `CLAUDE.md`, the endpoints in scope must be recorded in `API_SPECIFICATION.md` and `INTERFACE_CONTRACTS.md` **before** implementation. That is Architect work, and it happens after ratification, not before.

### 0.3. The distinction that must not be collapsed

**Receiving a sprint goal and a Definition of Done is not the same speech-act as ratifying the documents that authorize building it.**

The Product Owner's Sprint 1 instruction names an objective and a completion criterion. It does not say "ADR-046 through ADR-049 are ratified", and it does not answer the open questions those documents deliberately left to the Product Owner. `PRODUCT_DECISION_PLATFORM_OPERATIONS_CENTER.md` Section 0 already drew exactly this line for its own case:

> **Дано.** …Это разрешение **на существование инструмента** и на подготовку документов.
> **Не дано.** Ратификация текста этого документа и ответы на открытые вопросы Раздела 11. Разрешить компоненту существовать — более узкий акт, чем разрешить каждое его поведение.

The same reading applies here. A sprint goal authorizes *planning* this scope. It does not, by itself, discharge the ratification the four (five) documents each request in their own first paragraph.

**This document does not resolve that ambiguity, and a Developer-stage agent must not resolve it either.** It is the Product Owner's call, and it needs to be made explicitly — a sentence naming the documents as ratified, or naming which of them are. Until then, Part 8's handoff does not fire.

---

## Part 1 — Scope, anchored to the Product Owner's own words

### 1.1. The instruction, verbatim

> **Sprint 1 — Platform Operations Center MVP.**
> **Цель:** С телефона можно: увидеть статус, запустить PIOS, остановить PIOS, перезапустить PIOS, увидеть ошибки.
> **Критерий готовности:** После первого включения ноутбука ASUS владелец может полностью управлять запуском платформы со своего телефона без использования терминала, VS Code или удаленного рабочего стола.

Five capabilities. One Definition of Done. Nothing else is in this sprint.

### 1.2. Each capability as a testable acceptance criterion

**C1 — «увидеть статус».** From a phone, after authenticating with the operations credential, the owner sees one screen showing, for each target in the closed list, whether its **process** is running and since when: `driver-management`, `passenger-experience`, `order-management`, `dispatch`, `identity`, `frontend`, `postgresql`, `rabbitmq`, `tunnel` — displayed under the plain-language names of design §2.2 («Приём заказов», «Раздача заказов», …), not under their technical names.

- The source is the **Windows Service Control Manager**, not a query to each Spring Boot module. [RATIFIED — ADR-046 Decision 4; design §3.1] This is decided, not re-derived here: `state` is one of the SCM's own six values (`RUNNING`, `STOPPED`, `START_PENDING`, `STOP_PENDING`, `PAUSED`, `UNKNOWN`).
- "Process is RUNNING" and "module is healthy" are **different facts** and must not be merged on screen (ADR-046 Decision 4). Sprint 1 shows only the first; `GET /v1/health/<module>` stays where ADR-043 put it.
- The screen must distinguish three states, and this is a pass/fail criterion, not a nicety: **PIOS is stopped** / **PIOS is partly running** / **I cannot reach the agent** (design §2.2's green, red and grey cards). In the grey state the action buttons are disabled rather than producing an error.

**C2 — «запустить PIOS».** The group target `pios` starts, in the order defined by ADR-049 Decision 2, from the phone, behind a confirmation.

**C3 — «остановить PIOS».** The group target `pios` stops in reverse order — with PostgreSQL and RabbitMQ **not** stopped by the group action (ADR-049 Decision 2), behind a confirmation whose text states the consequence in plain language (design §2.9).

**C4 — «перезапустить PIOS».** Stop-then-start as **one** operation, and **if the stop fails the start is not attempted** and the operation is marked failed at the step that failed (ADR-049 Decision 2). This is an acceptance criterion, not an implementation detail: a restart that silently proceeds after a failed stop is a defect, not a variant.

For C2–C4, all three: a failed step halts a group sequence and is reported as a partial result, never as success; the agent executes **at most one mutating operation at a time, globally**, and a second concurrent request is refused with `409` (ADR-047 Decision 3).

**C5 — «увидеть ошибки».** From the phone, the owner sees errors from the last N hours, grouped mechanically by (service + first line of message), with a count, a first/last timestamp and a sample — and can copy the whole thing to the clipboard in one action to send onward (design §2.5, §3.1 `GET /v1/ops/errors`).

- **No interpretation.** The agent does not decide what is "important"; no such criterion exists and inventing one is forbidden (design §2.5, citing the same rule `PIOS_OWNER_CONTROL_CENTER_MVP_DESIGN.md` §5.3 already applied).
- This capability is **impossible without log files existing**, which they do not today. See Part 2, ADR-049 Decision 5.

### 1.3. The Definition of Done, unpacked

> «После первого включения ноутбука ASUS владелец может полностью управлять запуском платформы со своего телефона без использования терминала, VS Code или удаленного рабочего стола.»

Three requirements are packed into that sentence, and each is separately verifiable:

1. **Cold boot, nobody at the machine.** For the phone to reach anything at all after the laptop is powered on, `platform-ops` **and** the tunnel process must already be running without human action. That is ADR-049 Decision 1 (Automatic start type), and it makes ADR-049 load-bearing for this sprint (Part 2).
2. **From the phone.** Not from the local network, not from a browser on the machine. That makes ADR-048 load-bearing and non-deferrable, and it carries a price tag (Part 5, item O-2).
3. **Without terminal, VS Code or remote desktop.** This is the strongest clause in the whole instruction: it rules out "the owner RDPs in and clicks" as an acceptable pass. It also means the one-time service registration (ADR-046 Decision 2, design §4.4) must be **complete before the DoD is evaluated** — that setup is an operator task performed once at the machine, and it is not itself a violation of the DoD, which speaks about ongoing operation.

**[OPEN — O-1] The Automatic-start tension, which this plan will not resolve on its own.** ADR-049 Decision 1 registers *every* target, including the five modules and `frontend`, with start type **Automatic**. If that is done, then after a cold boot PIOS is already running and there is nothing for «запустить PIOS» to start — C2 becomes demonstrable only as stop-then-start. The alternative reading of the DoD — the five modules registered as **Manual**, so the owner genuinely starts the platform from the phone each time — contradicts ADR-049 Decision 1's own text and gives up unattended reboot survival, which `PLATFORM_OPERATIONS_CENTER_VISION_V1.md` Scenario 2 treats as a core outcome.

**Recommendation:** keep Automatic per ADR-049 Decision 1, and read the DoD as *"the capability to control startup exists and is exercisable from the phone"* rather than *"PIOS must be down after boot"*. Verification then is: cold boot → status visible from the phone → stop → start → restart, all from the phone. **This is an interpretation of the Product Owner's sentence and needs his word, not this plan's.**

---

## Part 2 — Which ADR governs which capability

Stated capability by capability, because a summary paragraph would hide the one correction below.

| Capability | ADR-046 | ADR-047 | ADR-048 | ADR-049 |
| --- | --- | --- | --- | --- |
| C1 status | D1 (component, port, no DB), D2 (closed target list), D4 (SCM as the source; no health proxying, no JVM metrics) | D1 (ops credential gates the read surface too), D5 (what the credential does not grant) | D1, D4, D5 (reachable from a phone at a stable hostname over TLS) | D1 (targets are services at all — otherwise there is no `state` to read) |
| C2 start | D2 (target resolved through a closed map; no string reaches a command line) | D1, D3 (confirmation), D4 (journal), and single-flight | D1, D4, D5 | **D1** (start type / recovery), **D2** (group start order) |
| C3 stop | D2 | D1, D3, D4, single-flight | D1, D4, D5 | **D2** (reverse order; PostgreSQL and RabbitMQ excluded from the group stop) |
| C4 restart | D2 | D1, D3, D4, single-flight | D1, D4, D5 | **D2** (stop-then-start as one operation; failed stop halts) |
| C5 errors | D5 (closed file table, tail-first, bounded, lines-not-files) | D1, D5 | D1, D4, D5 | **D5** (the log files must be made to exist first) |

### 2.1. Correction to the framing this plan was given

The scoping brief handed to this document stated that **ADR-049 is not load-bearing for Sprint 1**. Having read it, that is not correct, and recording it as correct would let a real prerequisite be skipped. The evidence:

- **ADR-049 Decision 1** is what makes `platform-ops` and `cloudflared` start on their own after a cold boot. Without it the Definition of Done's first clause — *«после первого включения ноутбука»* — cannot be met at all, because the phone would reach nothing.
- **ADR-049 Decision 2** is the *only* place in the package that defines group start order, group stop order (including the deliberate exclusion of PostgreSQL and RabbitMQ), restart-as-one-operation with fail-halt, and "the agent is not a target of itself". C2, C3 and C4 are literally that decision.
- **ADR-049 Decision 5** states the fact that makes C5 possible or impossible: *"No module writes a log file… once the modules become Windows services (ADR-046 Decision 2), they have **no console at all**, so the logs stop existing entirely rather than merely being inconvenient."* Design §3.1 even specifies the honest failure response for this state — `503 {"reason": "LOG_FILE_NOT_CONFIGURED"}`. Sprint 1 that skipped Decision 5 would ship an errors screen that is permanently empty, and would ship it having also removed the consoles that were the only place errors previously appeared.

**What *is* correctly out of scope in ADR-049 is Decisions 3 and 4** — updates, rollback, backups and restore. Those are not built in Sprint 1 (Part 3).

**Consequence for Part 0:** ADR-049 belongs in the ratification gate alongside the other four. An ADR is ratified as a whole; Sprint 1 depends on three of its five decisions.

---

## Part 3 — What Sprint 1 deliberately does not build

**[RATIFIED — reused from `PRODUCT_DECISION_PLATFORM_OPERATIONS_CENTER.md` §7, §10 and design §17; nothing below is newly excluded by this plan except where marked]**

- **Backups.** No `POST /v1/ops/backups`, no `GET /v1/ops/backups`, no design §2.6 screen. (ADR-049 Decision 4 — not built.)
- **Restore.** Does not exist and cannot be built: `PRODUCT_DECISION_PLATFORM_OPERATIONS_CENTER.md` §11.3 is open, and until it is answered *«этой возможности нет»*.
- **Updates and rollback.** No `GET /v1/ops/version`, no `POST /v1/ops/updates`, no `/rollback`, no design §2.7 screen. (ADR-049 Decision 3 — not built.)
- **Notifications of any kind.** ADR-046 Decision 7; `PRODUCT_DECISION_OWNER_CONTROL_CENTER.md` §9.
- **Anything domain-side.** No order, driver, passenger, assignment or money, in any direction. The agent has no client for those APIs at all (ADR-046 Decision 3).
- **JVM/CPU/memory metrics, database content, arbitrary file access, any generic command.** ADR-046 Decisions 2, 4, 5 — permanent, not deferred.
- **Any change to `/owner`, `HealthController.kt`, `OwnerCredentialGate.kt`, `vite.config.ts`, or any module's domain code, schema or events.** Design §18.2, transferred verbatim.

**[DERIVED — proposed reductions for this MVP cut, new to this document]**

- **R-1. Per-target start/stop/restart buttons are deferred; only the group target `pios` is actionable.** The five capabilities name «PIOS», not individual services. Status is still shown per target (C1 requires it, and the red card in design §2.2 names the specific broken service). Design §2.3's per-service action buttons are out.
  *Cost, stated:* the red-state flow «не работает раздача заказов → [Запустить заново]» becomes a group start rather than a targeted one. See O-3.
- **R-2. The ride-count warning («Сейчас выполняется 2 поездки») is deferred.** Design §2.2 and §2.9 show it, and ADR-046 Decision 3 requires it to be fetched *by the browser* from `dispatch` using the **observation** credential — which would put both credentials in one operations session and blur the separation ADR-047 Decision 1 exists to create. It is also, per `PRODUCT_DECISION_PLATFORM_OPERATIONS_CENTER.md` §11.5, a warning and not a block in any case. The confirmation dialog keeps its plain-language consequence text without the number.
- **R-3. The logs screen (design §2.4) and `GET /v1/ops/logs/{target}` are deferred.** `GET /v1/ops/errors` already returns a `sample` («первые 40 строк последнего вхождения») and the screen already has «Скопировать всё» — which is the whole value of C5's "hand it to someone who can read it" path. The full tail viewer, with its level filter, `since` parameter, line-count selector and auto-refresh toggle, is over-built for five checkboxes.
  *This is the one reduction I am least sure of* — if the Product Owner expects «увидеть ошибки» to include reading the log itself, put `GET /v1/ops/logs/{target}` back in with fixed parameters (200 lines, no level filter, no auto-refresh) rather than the full §2.4 design.
- **R-4. The action-journal screen (design §2.8) is reduced to a plain list.** **The journal itself is not reduced and is not optional** — ADR-047 Decision 4 requires append-only writing before and after every action, including refused attempts, and that is in scope in full. Only the presentation is minimal, and `GET /v1/ops/audit` is included because a refused or crashed action is otherwise invisible.
- **R-5. The `423` protection-mode response (design §3.4) is not implemented.** The protection-mode toggle is an open question (`PRODUCT_DECISION_PLATFORM_OPERATIONS_CENTER.md` §11.1, ADR-047 Decision 6). Per ADR-047 Decision 6's own rule — *"until answered, the conservative behaviour applies: the stricter option, or the feature does not exist"* — the feature does not exist in Sprint 1, and neither does its status code.

**Not reduced, deliberately, though it looks like MVP fat:** the asynchronous `202` + `GET /v1/ops/operations/{operationId}` polling of design §3.5. A group stop or start of eight services takes tens of seconds; a synchronous request through a tunnel would time out and leave the owner unable to tell a slow stop from a failed one — which violates C1's own three-state requirement. Keep it.

---

## Part 4 — What to reuse, by reference

Nothing below is re-specified here. The Developer implements what these sections already say.

| Sprint 1 element | Source, by section |
| --- | --- |
| Common rules for every endpoint (owner, base address, `Authorization: Basic`, no `WWW-Authenticate` on 401, `/v1/ops/` prefix, JSON/ISO-8601, no domain concept, no string into a command) | design **§3.0** |
| Closed target list and the group target `pios` | design **§3.0**; ADR-046 Decision 2 |
| `GET /v1/ops/services` — main screen query, with the full response shape | design **§3.1** |
| `GET /v1/ops/services/{target}` — one target, `404` if outside the closed list | design **§3.1** |
| `GET /v1/ops/errors?sinceHours=24` — grouped errors, mechanical grouping only | design **§3.1** |
| `GET /v1/ops/audit?limit=&offset=` | design **§3.1** |
| `GET /v1/ops/health` — distinguishes "agent alive, PIOS stopped" from "no agent" | design **§3.6** |
| `POST /v1/ops/confirmations` — token properties, all six binding | design **§3.3**; ADR-047 Decision 3 |
| `POST /v1/ops/services/{target}/start` \| `/stop` \| `/restart` — request body, `202` shape | design **§3.4** |
| Full response-code table (`202/400/401/403/404/409/500/503`), with the owner-facing text for each | design **§3.4** — minus `423`, per R-5 |
| Long-running operation polling | design **§3.5** |
| Login screen, with the mandatory "this is a different password" hint | design **§2.1** |
| Main screen, three states | design **§2.2** — minus the ride warning, per R-2 |
| Errors screen | design **§2.5** |
| Confirmation overlay, typed phrase, live countdown | design **§2.9** |
| One-time operator setup (bootJar, service registration, env vars, recovery actions, agent last) | design **§4.4**; ADR-049 Decision 1 |
| Group start order, stop order, restart semantics | ADR-049 Decision 2; design §5.2, §6.1, §7 |
| Rolling file logging in the five modules | ADR-049 Decision 5; design §8.1 |

### 4.1. Documentation drift found while scoping — for the Architect, not the Developer

Not blocking, but it should be corrected when these documents are ratified, and it should not be corrected silently by a Developer:

- `PIOS_SERVER_CONTROL_CENTER_DESIGN.md` still cites **ADR-045** throughout (§2.2, §3.0, §3.3, §3.4, §4.2, §17, §18.1 step 2, §19). ADR-045 is superseded by the 046–049 package. Its decision numbers do not map one-to-one onto the new ADRs, so a reader following those citations today lands on a superseded document.
- `ADR-046` and `ADR-047` cite `PRODUCT_DECISION_PLATFORM_OPERATIONS_CENTER.md` **"Section 8"** for the deferred open questions (e.g. ADR-046 §8.5, §8.8; ADR-047 §8.1, §8.2). In the current text of that Product Decision, the open questions are **Section 11**, and Section 8 is "who may use it". ADR-048 and ADR-049 already cite Section 11 correctly. ADR-047 Decision 5 similarly cites "Section 5" for one-person-no-roles (actual: Section 8), and its Context cites "Section 10, point 3" for the accidental-stop success criterion (actual: Section 13, point 3).

---

## Part 5 — Open questions that must be answered before code, not during it

Only the subset that Sprint 1 actually touches. The rest of design §16 stays open and untouched.

| # | Question | Why it blocks Sprint 1 | Recommendation, as a recommendation |
| --- | --- | --- | --- |
| **O-1** | Automatic vs Manual start type for the five modules and `frontend` (Part 1.3) | Determines whether «запустить PIOS» has anything to start after a cold boot, and how the DoD is verified | Keep Automatic per ADR-049 D1; verify the DoD as stop→start→restart from the phone |
| **O-2** | Named Cloudflare Tunnel (account + owned domain), and an external access gate in front of the operations hostname — design **§16.5**, `PRODUCT_DECISION_…` §11.2, ADR-048 D3/D4 | «С телефона» and «без удалённого рабочего стола» cannot be met without a stable hostname reachable from outside. Costs money and a domain — a Product Owner decision, not architecture's | Yes, **and before the first destructive endpoint exists**, not after (ADR-047 D2, ADR-048 D3) |
| **O-3** | Which tool makes a Windows service out of `java -jar` (WinSW / NSSM / other) — design **§16.1** | `sc.exe` alone cannot. Nothing in Sprint 1 works without this, and it introduces a third-party tool into PIOS operations | WinSW: one `.exe` + XML, no installer, configuration is versionable |
| **O-4** | Whether the operator must type a confirmation word or a tap on a dialog suffices — `PRODUCT_DECISION_…` §11.1, ADR-047 D6 | Determines the confirmation UI for C2–C4 | Type the word. ADR-047 D6's own fallback rule already makes typing the default until answered |
| **O-5** | Idle timeout and re-entry of the ops password before a destructive action — `PRODUCT_DECISION_…` §11.1, design §16.9 | Affects the login/session behaviour built in Sprint 1 | 15-minute idle timeout; re-entry before destructive actions |
| **O-6** | Restart aggressiveness for SCM recovery actions; what to do about "alive but broken" — design **§16.2** | The recovery actions are configured during Sprint 1's one-time setup | 30 s / 60 s / 120 s, counter reset after 24 h. Do **not** automate "alive but broken" — no rule for it exists (ADR-002) |
| **O-7** | What happens when a service does not stop within the timeout — design **§16.3** | C3 and C4 both hit this on their first bad day | Mark `FAILED` and show the truth. Do not force-kill |
| **O-8** | Log retention values — design **§16.4** | The rolling-file configuration that makes C5 possible needs numbers | 20 MB × 14 files, 500 MB cap per module |
| **O-9** | Whether personal data in logs is masked before the owner copies it — `PRODUCT_DECISION_…` §11.8, design §16.8 | C5's «Скопировать всё» sends passenger names and addresses to a developer in one tap | Do not mask in the files; warn on screen before copying |

**None of O-1…O-9 may be answered by a default a developer picks.** O-2 and O-3 are hard blockers: without them, Sprint 1 cannot start. The rest change behaviour that Sprint 1 builds and would have to be reworked if answered later.

---

## Part 6 — Repository and file impact

### 6.1. Where `platform-ops` physically lives — **[DERIVED, needs confirmation]**

ADR-046 Decision 1 settles what the component *is* (not a module, not a bounded context, not in `MODULE_STRUCTURE.md` §3) and forbids the name `operations`. It does **not** name a directory path, and ADR-008 requires a new top-level folder to be a deliberate decision.

**Proposal: a new top-level directory `platform-ops/`, a sibling of `backend/` and `frontend/`.** Reasoning: ADR-046 Decision 1.1 answers the boundary question as *"a platform component, not part of PIOS — and it nevertheless lives in the PIOS repository"*, with PostgreSQL and RabbitMQ as the literal analogy. A sibling directory expresses exactly that; `backend/platform-ops/` would read, within a year, as a sixth backend module, which is the misreading ADR-046 spends Decision 1 preventing.

*Alternative, and why it is not recommended:* placing it under `backend/` as a Gradle subproject is cheaper (it inherits the existing build configuration and toolchain). Rejected on ADR-046's own naming argument — the same argument that forbade `backend/operations/` applies to any path that puts it among the modules.

**This needs the Product Owner's or Architect's confirmation at ratification time**, because it is a repository-structure decision under ADR-008 and ADR-046 did not spell out the path.

### 6.2. What changes

| Area | New | Modified |
| --- | --- | --- |
| `platform-ops/` (new) | Spring Boot app on port 8090; ops credential check (**a new independent copy of the PBKDF2 mechanism, not shared code** — design §18.2, `MODULE_STRUCTURE.md` §4 forbids shared code between modules); closed target map; SCM adapter; log-file reader (closed table, tail-first, bounded); error grouper; confirmation store (in-memory); single-flight executor; append-only action journal writer; the eight endpoints of Part 4 | — |
| `frontend/` | `/owner/server` routes: login, main screen, errors screen, confirmation overlay, minimal audit list | `routes.tsx` (new routes), and **one navigation link** on `/owner` that performs no action (ADR-046 Decision 6) |
| `backend/` five modules | — | **`application.yml` only** — rolling file logging (ADR-049 D5). No code, no dependency, no migration, no contract change |
| `docs/` | `API_SPECIFICATION.md` and `INTERFACE_CONTRACTS.md` entries **before** implementation (ADR-006) | `README.md` and `LAUNCH_CHECKLIST.md` **extended, not replaced** — `bootRun` stays correct for local development; the pilot machine now starts via services (ADR-049 Consequences; `CLAUDE.md` "Never Delete Documentation") |
| Untouched, explicitly | — | `vite.config.ts` (**zero rows added to `preview.proxy`** — ADR-048 D2), `OwnerControlCenter.tsx`, `StatusCard.tsx`, `TodayCard.tsx`, `EventFeed.tsx`, `healthPoll.ts`, `statusEvaluation.ts`, `buildReport.ts`, `todayData.ts`, `HealthController.kt`, `OwnerCredentialGate.kt`, `network-management` |

No migration. No domain code. No event. No change to any existing endpoint.

---

## Part 7 — QA: what "done" means

Mapped straight onto the Definition of Done sentence. The scenario is run **from an actual phone**, on mobile data rather than the home network, with the laptop lid closed to nobody.

1. Power the ASUS laptop on from cold. Touch nothing on the machine afterwards.
2. From the phone, open the operations hostname. **The observation password is rejected**; the operations password is accepted. (This is a required negative test, not an afterthought — it is what proves the two surfaces are separate.)
3. The status screen shows every target with its state and uptime. Names are plain-language.
4. Stop PIOS: the confirmation appears with its consequence text and typed phrase; the countdown is live; the button is inert until the phrase matches. The stop reports per-step progress and finishes as a definite success or a definite failure.
5. Start PIOS. Restart PIOS.
6. Open the errors screen; copy everything in one tap.
7. **The screen survives stopping `frontend`** — this is the specific proof that ADR-048 Decision 1 was implemented and that control does not travel through `vite preview` (design §3.5's named special case).
8. Negative paths, all required: expired confirmation; re-used token; double-tap; two mutating requests at once (`409`); agent unreachable (grey card, buttons disabled); wrong ops password; laptop offline.
9. **At no point were a terminal, VS Code or remote desktop used.**

The sprint is complete when steps 1–9 pass and QA has listed any limitation that could not be verified (`.claude/CLAUDE.md`, Stage 3).

---

## Part 8 — Handoff to the Developer role

**This handoff does not fire until Part 0's gate clears.** When it does, the Developer receives:

- **ADR reference:** ADR-046 (component boundary and closed target list), ADR-047 (destructive-action authorization), ADR-048 (ingress), ADR-049 **Decisions 1, 2 and 5 only** — Decisions 3 and 4 are out of scope. Business authority: `PRODUCT_DECISION_PLATFORM_OPERATIONS_CENTER.md`. Narrative context, non-binding: `PLATFORM_OPERATIONS_CENTER_VISION_V1.md`.
- **Pattern to model on:** the existing Spring Boot module layout and the `OwnerCredentialGate` PBKDF2 check — **copied, not shared** (design §18.2).
- **Location:** `platform-ops/` at the repository root, pending Part 6.1's confirmation. **It is not a sixth PIOS module** and is not added to `MODULE_STRUCTURE.md` §3, `DOMAIN_MODEL.md`, `EVENT_CATALOG.md`, `DATABASE_DESIGN.md` or `INTERFACE_CONTRACTS.md` §3.
- **Endpoints:** the eight listed in Part 4, by reference to design §3.1, §3.3, §3.4, §3.5, §3.6 — already written into `API_SPECIFICATION.md` and `INTERFACE_CONTRACTS.md` by the Architect first (ADR-006).
- **Build order:** design §18.1's own sequence, restricted to this sprint — logs and Windows services first (useful even if nothing else ships), then the agent read-only, then external ingress, then confirmations, then `start` → `restart` → `stop` in that order (*fix before break*), then the screens.
- **The constraint the implementation must satisfy, stated as one sentence:** *after delivery it must still be true that the observation console cannot do anything, and that a stopped PIOS can be started from a phone by a person who typed a different password and confirmed a specific consequence.*

---

## Files Changed (this planning task)

`docs/IMPLEMENTATION_PLAN_SPRINT_1_PLATFORM_OPERATIONS_MVP.md` (new). No other file created or modified. No source code, build file, migration, API or event contract is touched by this planning task — nor by any subsequent task until Part 0's gate is explicitly cleared by the Product Owner.

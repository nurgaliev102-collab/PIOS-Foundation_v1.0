# ADR-046: Platform Operations Component Boundary — What the Operations Agent Is, and What It Is Structurally Incapable Of

## Status

**Draft — awaiting Product Owner ratification.**

Authorized in principle by the Product Owner's instruction of 2026-08-06
(*«…пакет ADR (046–049), который вводит новый компонент управления
платформой, не изменяя и не ослабляя ограничения Owner Control Center»*)
and by `docs/PRODUCT_DECISION_PLATFORM_OPERATIONS_CENTER.md`, which is
itself still a draft awaiting ratification. That instruction authorized the
**existence** of the component; it did not ratify this text, and it did not
answer the open questions that Product Decision's Section 8 defers. Neither
does this ADR.

**Package.** This is one of four ADRs that jointly replace the single,
bundled ADR-045:

| ADR | Question it answers |
| --- | --- |
| **046 (this)** | *What is this component, and what can it not do?* |
| 047 | *Who may make it act, and how is a destructive act authorized?* |
| 048 | *How does an instruction reach it from outside the machine?* |
| 049 | *How does the platform get back to a known-good state?* |

**Relationship to ADR-045.** ADR-045 (*Platform Operations Control
Boundary*) bundled all four questions into one document. It is superseded by
this package and retained, not deleted (`CLAUDE.md`, "Never Delete
Documentation"). The split is justified in "Why four ADRs and not one"
below.

**Relationship to ADR-043 and ADR-044.** This ADR is built **on top of**
both. It amends neither, contradicts neither, and requires no edit to
either. See Decision 6.

## Context

### The authority to write this at all

Control of the platform is forbidden — permanently, in their own words — by
`PRODUCT_DECISION_OWNER_CONTROL_CENTER.md` Sections 7 and 11 and by ADR-043
Decision 5. Both prescribed the same exit, in advance:

> ADR-043 Decision 5: *"A future capability to act from this screen is not a
> refinement of ADR-043; it is a different thing, requiring its own Product
> Decision (who may act, on whose behalf, with what authority) and its own
> ADR."*

> `PRODUCT_DECISION_OWNER_CONTROL_CENTER.md` Section 11: *«Если однажды
> понадобится управление — это новый инструмент с новым решением, не
> следующая версия этого.»*

`PRODUCT_DECISION_PLATFORM_OPERATIONS_CENTER.md` is that Product Decision.
This package is that ADR. Neither existing document is edited; the exit both
of them named is used exactly as they described it.

### What runs today, verified rather than remembered

Recorded in full in `PIOS_SERVER_CONTROL_CENTER_DESIGN.md` Section 0.3 and
not repeated here. The four facts this ADR actually rests on:

- **The five pilot modules are unsupervised.** Each is started by hand with
  `cd backend; ./gradlew.bat :<module>:bootRun` (`README.md` "Running
  Locally"; `LAUNCH_CHECKLIST.md` lines 17–23). No Windows service, no
  scheduled task, no supervisor. A module that dies at 03:00 stays dead.
- **The deployment target is one Windows 11 Pro laptop.** PostgreSQL 17
  (`postgresql-x64-17`) and RabbitMQ (`RabbitMQ`) already run as native
  Windows services with Automatic start; the JVM modules do not.
- **`GET /v1/health/<module>` reports four facts and no more** — module,
  `status`, `database`, `checkedAt`, plus `outbox` on three modules
  (`backend/dispatch/src/main/kotlin/com/pios/dispatch/api/HealthController.kt`
  lines 39–63). No uptime, no version, no pid, no memory.
- **No module writes a log file.** No `logging.file` property, no logback
  configuration, no file appender anywhere in `backend/`. Everything goes to
  the `bootRun` console's stdout and is lost with it.

### The structural fact that forces a new deployable

> **A stopped process cannot start itself.**

Requirement "start PIOS" cannot be satisfied by any endpoint hosted inside
the thing being started. Stop is self-hostable; start is not; restart is not,
because its second half is a start. Whatever answers *"start `dispatch`"*
must be a different process, must already be running, and must outlive what
it starts.

This closes the entire family of "replicate the check into the five modules,
like `OwnerCredentialGate`" designs before any preference is expressed —
even though that is precisely the shape this repository's own precedent
would otherwise suggest.

### Why four ADRs and not one

ADR-044 stated this repository's convention explicitly when it declined to
be part of ADR-043: *"This repository's own convention is one decision per
ADR. ADR-038 and ADR-039 were split despite arriving together; ADR-040 and
ADR-041 likewise."* ADR-045 broke that convention by carrying thirteen
decisions, and it broke it in the one place where it matters most — the four
questions have **different futures and different reviewers**:

- *What the component is* (this ADR) is meant to stand for as long as the
  component exists.
- *How a destructive act is authorized* (ADR-047) is expected to be revised
  the moment PIOS has real authentication, exactly as ADR-044 expects to be
  superseded.
- *How the instruction arrives* (ADR-048) depends on an infrastructure
  decision with a price tag, which the Product Owner may answer differently
  next year.
- *How the platform returns to a known-good state* (ADR-049) is the one part
  that binds a ratified ADR (ADR-014's reversibility requirement) and would
  outlive the console entirely.

Bundling them would force a future partial supersession — the failure mode
ADR-042 named as *"false ratified text is worse than no text"*.

## Problem

What kind of component may hold the authority to start, stop and restart
PIOS's processes, given that no module can start itself, that no component
may hold a joined view of five bounded contexts (ADR-005, ADR-009, ADR-043
Decision 1), and that no new domain module may be scaffolded without
ratifying a bounded context (`MODULE_STRUCTURE.md` Section 8, ADR-043
Decision 8)?

And how is that component prevented — structurally, not by careful coding —
from becoming a remote shell on the owner's laptop?

## Decision

### 1. A new deployable is created: the PIOS Operations Agent. It sits at the infrastructure tier, and it is not a ninth module.

The user-facing tool is named **Platform Operations Center**
(`PRODUCT_DECISION_PLATFORM_OPERATIONS_CENTER.md` Section 4, pending the
Product Owner's confirmation of the name). The technical artefact — the
deployable, its folder and its Windows service — is **`platform-ops`**.

**The technical artefact may not be named `operations`.** That name, along
with `observability`, `monitoring`, `reporting` and `analytics`, is reserved
by ADR-043 Decision 8, which forbids scaffolding a module under any of them
without ratifying a bounded context first. A folder called
`backend/operations/` would, within a year, be read by someone as evidence
that the gate had been lifted. It has not been.

Port 8090 — 8085 belongs to `network-management` and 8086 to `identity`.
Kotlin and Spring Boot, the same stack as every module (ADR-023); no new
technology enters the project.

Binding properties:

- **Owns no domain data.** No aggregate, no entity, no value object from
  `DOMAIN_MODEL.md`. It has no business invariant because it models no
  business.
- **Has no database.** Not PostgreSQL, not SQLite, not any store. Its only
  persistent artefacts are the append-only action journal (ADR-047 Decision
  4) and the backup files it produces (ADR-049). It therefore cannot appear
  in `DATABASE_DESIGN.md` Sections 4–5 and cannot violate ADR-005 by
  construction: there is nothing for it to own.
- **Publishes and consumes no event.** It is absent from the RabbitMQ
  topology. `EVENT_CATALOG.md` is unchanged.
- **Is not a module under `MODULE_STRUCTURE.md` Section 3.** It is not added
  to that document's list, it maps to no capability in ADR-018, and no
  future document may cite it as precedent for adding a domain module
  without ratifying a bounded context.

**Why a separate tier rather than a ninth module.** ADR-018's eight
capabilities all answer *"what does the business do"*. This component answers
*"what keeps the software running"* — the same category as
`postgresql-x64-17` and the `RabbitMQ` service, neither of which is a PIOS
module either. Calling it a module would require ratifying a bounded context
for something that has none, and would make `MODULE_STRUCTURE.md` Section 2's
Domain Alignment principle (*"every module aligns exactly with one of the
eight architectural components ratified in ADR-018"*) false the moment it
were added.

#### 1.1. The boundary question, answered plainly: is this *part of PIOS*, or infrastructure that merely operates PIOS?

The Product Owner asked this directly, so it is answered directly rather than
left to follow from the properties above. ADR-043 Decision 8 already
established that a question of this shape needs prior ratification, so it is
given the same weight here as the not-a-ninth-module question.

**Answer: it is a platform component, not part of PIOS — and it nevertheless
lives in the PIOS repository.** Those are two separable facts and conflating
them is what makes the answer sound evasive.

| Question | Answer |
| --- | --- |
| Is it a PIOS module (`MODULE_STRUCTURE.md` Section 3)? | **No** |
| Is it a bounded context (ADR-017, ADR-018)? | **No** |
| Does it own any of the ratified capabilities? | **No** |
| Does it appear in `DOMAIN_MODEL.md`, `EVENT_CATALOG.md`, `DATABASE_DESIGN.md`? | **No — there is nothing of it to record there** |
| Is it deployed independently, like a module (ADR-014, ADR-026)? | **Yes, and necessarily so** — see below |
| Does its source live in the PIOS repository, built by the same toolchain? | **Yes** |

*Why not part of PIOS.* "PIOS", used architecturally, names the set of
ratified capability modules. This component owns no capability, models no
domain, holds no data and answers no business question. The literal analogy
is already running on the same laptop: **PostgreSQL and RabbitMQ are both
indispensable to PIOS existing, and neither is part of PIOS.** This agent is
of that category — infrastructure that operates PIOS — with exactly one
difference: it is software this project writes, so it has to be versioned
somewhere.

*Why in the repository anyway.* The alternative — a separate repository, or
worse, a folder of scripts on the operator's desktop — was rejected on a
ground this project has already walked once.
`PIOS_PILOT_INFRASTRUCTURE_DECISION.md` Section 3 rejected an external proxy
for precisely this: it *«перенёс бы конфигурацию маршрутизации в
неверсионируемый файл вне репозитория, который нельзя ни проверить на
прогоне, ни воспроизвести»*. A component able to stop the platform and
restore its databases is the last thing that should live in unversioned
scripts. Its code, its closed target list and its configuration must pass
through the same review path as everything else.

Adding a folder for it is formally a repository-structure change, and ADR-008
requires that to be *"a deliberate decision before adding any new top-level
folder, rather than creating one ad hoc"*. This ADR is that decision.

*Why it is still independently deployable.* Not because ADR-026 makes it a
module — it does not — but because the opposite would be absurd: an agent
co-released with the modules it restarts could not restart them across its
own release. Independent deployability here is a physical necessity, not an
inherited property.

*What follows practically.* The component is **not** added to
`MODULE_STRUCTURE.md` Section 3's list; it is **not** added to
`INTERFACE_CONTRACTS.md` Section 3's cross-module relationships (it has
none); and when anyone enumerates "PIOS consists of…", it does not appear.
PIOS is the modules. This is what PIOS is switched on with.

**This does not discharge ADR-043 Decision 8's gate.** The Operations bounded
context — correlated cross-context diagnostics, incident history,
event-stream timelines, notification delivery with no browser open — remains
unbuilt and still requires its own ratification. Decision 3 below bars the
one mechanism by which this agent could grow into it.

### 2. Authority is delegated to the operating system's service manager, over a closed target list. No caller-supplied string ever reaches a command line.

This is the decision that determines whether the feature is a control panel
or a remote code execution service. The distinction is **not** "we validate
input carefully"; it is "there is no input to validate".

- Each controllable thing is registered once, by an operator, as a **Windows
  service** with a fixed name, working directory, command line and
  environment — outside the agent, before the agent can do anything.
- The agent holds a **closed, build-time set of targets**, each a
  `(logical-name → windows-service-name)` pair. A request names a logical
  target; the agent resolves it through that fixed map and refuses anything
  absent from it before any other processing.
- The agent then instructs the Service Control Manager for the resolved
  service name. **No string from a request ever becomes part of a command
  line, a shell invocation, a filesystem path, an argument, an environment
  variable, or a database name.** There is no string concatenation into a
  command anywhere in this component.
- **There is no generic action.** No `exec`, no `command` parameter, no
  `script`, no `path`, no "advanced mode" — not now, and not in any later
  iteration without an ADR that supersedes this sentence. A future request
  for *"just let me run one PowerShell line from my phone"* is a request to
  delete this decision and must be treated as one.

The closed target list: `driver-management`, `passenger-experience`,
`order-management`, `dispatch`, `identity`, `frontend`, `postgresql`,
`rabbitmq`, `tunnel`, and the group target `pios`.

**`platform-ops` is not a target of itself** (ADR-049 Decision 2), and
`network-management` is not a target at all (ADR-037: not started, not
proxied, absent from the owner console — `moduleBaseUrls.ts` lines 22–39).

**Consequence, chosen rather than discovered.** The five modules must stop
being `gradlew bootRun` consoles and become installed Windows services before
any of this works. That is the largest operational prerequisite in this
package, it changes how PIOS is started by hand on ordinary days, and it
requires `README.md` and `LAUNCH_CHECKLIST.md` to be **extended** — not
silently outgrown. Which service-wrapper tool makes a Windows service out of
`java -jar` (`sc.exe` alone cannot) is an open decision
(`PIOS_SERVER_CONTROL_CENTER_DESIGN.md` §16.1).

### 3. The agent never asks a domain module anything. Any correlation between platform state and business state happens in the browser.

The temptation this forecloses is concrete and will be felt on day one:
*"refuse to stop while a ride is in progress"* is an obviously good idea, and
implementing it requires the agent to ask `dispatch` about assignments. That
would make the agent a component that fans out over domain APIs — the exact
shape ADR-043 Decision 1 forbids by name (*"a backend-for-frontend, gateway,
or aggregation endpoint that fans out to other modules"*) and that
`PILOT_INFRASTRUCTURE_ROUTING_DECISION.md` re-rejected as Variant G.

Therefore:

- The agent reports **only platform facts**: is this service running, since
  when, what the service manager says, what the last action did, what is in
  the log tail, what backups exist, which revision is deployed.
- The **browser** renders any business-side warning ("сейчас выполняется 2
  поездки") from data the observation console already holds by ADR-043
  Decision 1's own mechanism. The agent neither knows nor asks.
- That warning is **a display of a fact, not a business rule.** Whether an
  in-progress ride *should* block a stop is a rule nobody has decided, and
  inventing it here would breach ADR-002 and `CLAUDE.md`. It is deferred in
  `PRODUCT_DECISION_PLATFORM_OPERATIONS_CENTER.md` §8.5.

**Cost, named rather than hidden:** because the guard lives in the browser, a
caller talking to the agent's API directly bypasses it. The API-level
protections are ADR-047's, and they do not depend on the browser at all.

### 4. The agent's own observation surface is narrow, and does not duplicate or replace `/v1/health`.

- The agent reports process-level facts obtained from the service manager
  and from files it owns. It does **not** proxy, mirror, cache or aggregate
  `GET /v1/health/<module>`.
- "Process is RUNNING" and "module is healthy" are **different facts**, shown
  side by side and never merged. A module with an unreachable database is a
  live process that can do nothing — `HealthController` already answers `503`
  with a body for exactly that case (lines 58–62), and that answer stays
  where ADR-043 put it.
- **No memory, CPU, thread, GC or JVM metrics.** No screen answers a question
  about them, and ADR-011's Least Privilege applies to what an endpoint
  returns, not only to who may call it.
- **No database content, ever.** Not one row.
- **No money, in any form** — ADR-042 R4.3 applies here as everywhere.

Uptime and version come from the service manager, deliberately, so that
**none of the five modules has to change**. If the Product Owner later wants
those facts reported by the modules themselves, that is separate, additive
work on five modules and is not authorized here.

### 5. File access is by closed table, tail-first, and never by path.

The agent reads log files (ADR-049 Decision 5 requires them to start
existing) and backup files. Constraints:

- The log directory and backup directory are **fixed in the agent's own
  configuration**. A filename is derived from the resolved target through a
  closed table. **No path is ever assembled from request content.**
- Reads are **tail-first and bounded** (`lines` capped). Reading a whole file
  would make diagnosis heaviest exactly when things are worst — the same
  mistake ADR-043 Decision 2 already caught in its own first draft over
  `findUnpublished()`, and it is not repeated here.
- The agent returns **lines in JSON**, never files. No download endpoint, no
  `Content-Disposition`, no arbitrary file retrieval.

**Disclosed:** module logs may contain passenger names and pickup addresses,
because `GET /v1/orders` returns those fields and any diagnostic line near
them will capture them. Whether to redact is deferred
(`PRODUCT_DECISION_PLATFORM_OPERATIONS_CENTER.md` §8.8); that the log screen
sits behind the *operations* credential and not the observation one is
decided here and in ADR-047.

### 6. ADR-043 and ADR-044 are built upon, not amended. The observation surface is untouched.

This decision implements, at the architectural level, the named principle of
`PRODUCT_DECISION_PLATFORM_OPERATIONS_CENTER.md` Section 2:

> **Observation and Operations are separate concerns.**
> Owner Control Center answers *«Что происходит?»* — *what is happening*.
> Operations Center answers *«Что сделать?»* — *what should be done*.

The two requirements behind that principle are genuinely opposed, which is
why one surface cannot satisfy both: an observation tool opened ten times a
day, sometimes in a hurry, must have **no** button capable of breaking
anything; a control tool must be **deliberately hard** to use by accident.
Optimising a single surface for both always ends with convenience winning.
Everything below is that principle expressed as constraints on files.

- **ADR-043 stays literally true.** Its Decision 5 says *the Control Center*
  is read-only; the Control Center remains read-only. No control button is
  added to `OwnerControlCenter.tsx`, `StatusCard.tsx`, `TodayCard.tsx`,
  `EventFeed.tsx`, `healthPoll.ts`, `statusEvaluation.ts`, `buildReport.ts`
  or `todayData.ts`. The only addition to `/owner` is a navigation link,
  which performs no action.
- **ADR-044 stays literally true and unmodified.** Its credential, its five
  `OwnerCredentialGate` copies and its `HealthController` gate are not
  edited. ADR-047 states precisely where it deliberately departs, and why,
  without changing ADR-044's own text.
- **`INTERFACE_CONTRACTS.md` Section 3 is unchanged.** No cross-module
  relationship is added: the agent references no module's data, holds no
  identifier belonging to any module, and calls none of them.
- **No module is aware this component exists.** Nothing is configured in any
  module to enable it.

The sentence that must remain sayable after delivery: *"the observation
console still cannot do anything; a different tool, with a different
password, can."*

### 7. What this ADR does not authorize.

- No business rule of any kind (ADR-002; `CLAUDE.md`).
- No change to any module's domain code, schema, events or existing
  endpoints.
- No Operations bounded context (ADR-043 Decision 8, unchanged).
- No notifications, push or otherwise
  (`PRODUCT_DECISION_OWNER_CONTROL_CENTER.md` Section 9). An agent that can
  see a service is down is one step from an agent that texts about it, and
  that step is a separate decision.
- No participant authentication and no closing of the existing public
  `/v1/**` endpoints (ADR-044 Decision 11, unchanged). **Nobody may describe
  this work as having made PIOS secure**: it adds a locked door to a new,
  dangerous room in a house whose front door is still open.
- **No Docker.** Docker Desktop and WSL2 work on this machine and the backend
  does not use them: no `Dockerfile`, no `docker-compose.yml`
  (`LAUNCH_CHECKLIST.md` line 56). Containerizing changes how every module is
  built, started, configured and networked — including PostgreSQL's `trust`
  authentication and RabbitMQ's `guest`/`guest`. ADR-026 places
  infrastructure topology outside its own scope and defers it to a future
  Infrastructure decision under ADR-022; choosing containers inside a
  control-panel design would settle that deferred question as a side effect
  of building a UI. If the Product Owner wants containers — a legitimate and
  good direction — it needs its own ADR, taken on its own merits, **before**
  this one is implemented, not alongside it.

## Alternatives Considered

- **Control endpoints replicated into the five modules, like
  `OwnerCredentialGate`.** Rejected structurally, not by preference: a
  stopped process cannot start itself, so this cannot satisfy "start" at all.
  It would also put a process-killing endpoint on the deployables that serve
  driver and passenger traffic.
- **Spring Boot Actuator with `management.endpoint.shutdown`.** Rejected on
  ADR-043 Decision 2's ground, only more so: a broad management surface in
  five live services, able to stop and never to start, on a platform whose
  other endpoints are unauthenticated.
- **A generic remote-command endpoint with an allow-list of command
  strings.** Rejected. An allow-list of strings is one refactor away from an
  allow-list with a parameter, which is remote code execution with extra
  steps. Decision 2's "no caller-supplied string reaches a command line" is
  chosen precisely because it is a property that cannot erode gradually.
- **A ninth domain module named `operations`.** Rejected — Decision 1, and
  forbidden by ADR-043 Decision 8 without a ratified bounded context there is
  no bounded context to ratify.
- **A local-only tool with no network surface, driven by files or Task
  Scheduler.** Genuinely safer, and rejected only because control from a
  phone cannot be met by it. Retained as the fallback if the Product Owner
  decides remote control is not worth its risk — which would be a defensible
  decision, not a retreat.
- **Extending the Owner Control Center and its credential.** Rejected: it
  contradicts `PRODUCT_DECISION_OWNER_CONTROL_CENTER.md` Sections 7 and 11
  and ADR-043 Decision 5 directly, and would mean one stolen read-only
  credential can stop production.

## Consequences

### Positive

- PIOS becomes recoverable by its owner without a terminal — the largest
  operational gap in Pilot Operation today.
- The five modules gain crash recovery and reboot survival as a by-product of
  Decision 2's service registration, whether or not the console is ever
  opened (ADR-049 Decision 1). That is arguably worth more than the console.
- The dangerous capability is quarantined by construction: separate
  deployable, no domain knowledge, no database, closed target list, no
  command composition.
- ADR-043 and ADR-044 remain literally true and unedited. The observation
  surface does not become "the thing that can also stop PIOS", which is
  exactly what `PRODUCT_DECISION_OWNER_CONTROL_CENTER.md` Section 11 was
  written to prevent.

### Negative, disclosed rather than argued away

- **This is the largest single increase in attack surface in the project's
  history.** Before it, the worst outcome of a compromised owner credential
  was disclosure of operational internals ADR-044 already called narrow.
  After it, the worst outcome is a stopped or data-reverted platform. Every
  mitigation in ADR-047 is real and none of them changes that sentence.
- **A ninth running process** must itself be kept alive, configured,
  credentialed and updated — one more thing that can be down, and if it is
  down when everything else is, the phone shows nothing.
- **The five modules must become Windows services**, changing the daily
  manual start procedure two repository documents currently describe.
- **The Windows Service Control Manager becomes an architectural
  dependency.** The design is deliberately not portable; a future move to
  Linux or containers rewrites Decision 2's implementation entirely. Accepted
  because the target is one specific laptop, and pretending otherwise would
  produce an abstraction serving a hypothetical.
- **The browser-side ride guard is bypassable** by direct API calls
  (Decision 3).
- **Nothing here helps if the laptop is off, asleep or offline.** A control
  plane hosted on the machine it controls cannot report that the machine is
  gone; the console must therefore distinguish "PIOS is stopped" from "I
  cannot reach the server", the same distinction
  `PIOS_OWNER_CONTROL_CENTER_MVP_DESIGN.md` Section 5.5 already established.

## Traceability

| Subject | Source |
| --- | --- |
| Authority to introduce control at all | `PRODUCT_DECISION_PLATFORM_OPERATIONS_CENTER.md`; Product Owner instruction 2026-08-06 |
| The exit both existing documents prescribed | ADR-043 Decision 5 (lines 281–293); `PRODUCT_DECISION_OWNER_CONTROL_CENTER.md` Section 11 (line 87) |
| One decision per ADR, and why bundling fails | ADR-044 "Why this is not part of ADR-043"; ADR-042 ("false ratified text is worse than no text") |
| No fan-out aggregator, no joined view | ADR-043 Decisions 1, 2; `PILOT_INFRASTRUCTURE_ROUTING_DECISION.md` Variant G; ADR-005, ADR-009 |
| Operations bounded context still ungranted | ADR-043 Decision 8; `MODULE_STRUCTURE.md` Section 8 (line 116) |
| Domain Alignment principle a ninth module would break | `MODULE_STRUCTURE.md` Section 2 (line 17), Section 3 |
| Modules unsupervised today | `README.md` "Running Locally"; `LAUNCH_CHECKLIST.md` lines 17–23 |
| Health endpoint's actual narrow shape | `HealthController.kt` lines 39–63 |
| `network-management` excluded | ADR-037; `moduleBaseUrls.ts` lines 22–39 |
| Heavy diagnosis under load is a known trap | ADR-043 Decision 2 (the `findUnpublished()` correction) |
| Least Privilege applies to responses, not only callers | ADR-011 lines 19–23 |
| No money anywhere | ADR-042 R4.3 |
| Infrastructure topology is a separate, later decision | ADR-026 Decision (second paragraph); ADR-022 |
| No Docker in the repository | `LAUNCH_CHECKLIST.md` line 56 |
| No business rule may be invented | ADR-002; `CLAUDE.md` |
| Sibling decisions | ADR-047 (authorization), ADR-048 (ingress), ADR-049 (recovery and change safety) |

## Files Changed

This ADR only. No source file is touched by this document.

Prerequisites before any Developer task starts under this package:
ratification of `PRODUCT_DECISION_PLATFORM_OPERATIONS_CENTER.md`, answers to
its Section 8, ratification of ADR-046 through ADR-049, and
`API_SPECIFICATION.md` / `INTERFACE_CONTRACTS.md` entries written **before**
implementation (ADR-006, `CLAUDE.md`).

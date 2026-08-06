# ADR-045: Platform Operations Control Boundary — PIOS's First Component With Authority Over Its Own Processes

## Status

**Superseded by ADR-046, ADR-047, ADR-048 and ADR-049 (2026-08-06). Retained,
not deleted (`CLAUDE.md`, "Never Delete Documentation"). Never ratified in
this form, and must not be implemented from.**

### Why the blocker is gone, and why this document is still superseded

This ADR was originally filed as *"Proposed. Not ratified. Blocked on a
Product Decision that does not exist yet."* **That blocker is resolved.** The
Product Owner, on 2026-08-06, gave the authorization that ADR-043 Decision 5
and `PRODUCT_DECISION_OWNER_CONTROL_CENTER.md` Section 11 both required in
advance, and `docs/PRODUCT_DECISION_PLATFORM_OPERATIONS_CENTER.md` now
exists as the Product Decision half of that route.

No document should be left asserting a blocker that has been lifted, which is
why this Status is rewritten rather than annotated.

The blocker being lifted is **not** why this ADR is superseded. It is
superseded for a reason internal to it: **it carried thirteen decisions in
one document**, in a repository whose own convention is one decision per ADR
— stated by ADR-044 itself when it declined to be part of ADR-043
(*"ADR-038 and ADR-039 were split despite arriving together; ADR-040 and
ADR-041 likewise"*). The four questions it bundled have different futures and
would have forced a future partial supersession, which ADR-042 named as the
failure mode to avoid: *"false ratified text is worse than no text."*

The Product Owner's instruction of 2026-08-06 asked for the package form
directly (*«пакет ADR (046–049)»*), which is the same conclusion reached from
the other direction.

### Where each of this ADR's decisions now lives

| ADR-045 Decision | Now in |
| --- | --- |
| 1 (new non-domain deployable, not a ninth module) | **ADR-046** Decision 1, expanded with the explicit *platform-component-vs-part-of-PIOS* answer (1.1) |
| 2 (service-manager delegation, closed target list, no command composition) | **ADR-046** Decision 2 |
| 3 (separate surface, separate route) | **ADR-046** Decision 6, under the named principle *Observation and Operations are separate concerns* |
| 4 (second credential, deliberate departure from ADR-044 Decision 6) | **ADR-047** Decision 1 |
| 5 (agent never calls a domain module) | **ADR-046** Decision 3 |
| 6 (two-step, single-use confirmation) | **ADR-047** Decision 3 |
| 7 (append-only action journal) | **ADR-047** Decision 4 |
| 8 (separate ingress, not through `vite preview`) | **ADR-048** Decisions 1, 2, 4 |
| 9 (auto-recovery belongs to the OS) | **ADR-049** Decisions 1, 2 |
| 10 (updates: operator-initiated, reversible, migration asymmetry) | **ADR-049** Decision 3 |
| 11 (backups and restore) | **ADR-049** Decision 4 |
| 12 (Docker not introduced) | **ADR-046** Decision 7 |
| 13 (what is not authorized) | **ADR-046** Decision 7; ADR-049 Decision 6 |

Nothing was dropped in the split, and two things were added that this
document did not contain: the explicit answer to *"is this part of PIOS or
infrastructure that operates PIOS"* (ADR-046 Decision 1.1) and the naming
analysis (`PRODUCT_DECISION_PLATFORM_OPERATIONS_CENTER.md` Section 4).

**Read the four ADRs, not this one.** The body below is preserved as the
record of the analysis that produced them, and its citations remain accurate.

Companion document: `docs/PIOS_SERVER_CONTROL_CENTER_DESIGN.md` — screens,
endpoint shapes, operational procedures. Design detail, not architectural
decision. That document also carries the package's open questions in the
Product Owner's own language.

## The ratified positions this contradicts

Cited verbatim, because a paraphrase would make them sound softer than they
are.

**ADR-043 Decision 5** (`ADR-043-Owner-Control-Center-Observation-Boundary.md`
lines 281–293), under the heading *"This surface is read-only, permanently"*:

> The Control Center calls no endpoint that changes state. It has no button
> that assigns, cancels, completes, proposes, changes availability,
> **restarts anything**, or edits anything.

and, in the same Decision:

> This is a decision about the surface's nature, not a description of its
> first version. A future capability to *act* from this screen is not a
> refinement of ADR-043; it is a different thing, requiring its own Product
> Decision (who may act, on whose behalf, with what authority) and its own
> ADR.

**`PRODUCT_DECISION_OWNER_CONTROL_CENTER.md` Section 7** (line 53):

> Нельзя остановить, перезапустить или иначе управлять работой платформы.

**`PRODUCT_DECISION_OWNER_CONTROL_CENTER.md` Section 11** (line 87):

> **Панель никогда не управляет.** Наблюдение — это не первый шаг к
> управлению, это весь смысл инструмента. Если однажды понадобится
> управление — это новый инструмент с новым решением, не следующая версия
> этого.

**ADR-043 Decision 2** (line 186) and **Decision 1** (lines 152–161) forbid,
by name, any component that "fans out to other modules", any
backend-for-frontend, gateway or aggregation endpoint, and any module
reporting on another module.

**ADR-043 Decision 8** (lines 332–346) forbids scaffolding an `operations`,
`observability`, `monitoring`, `reporting` or `analytics` module without
prior ratification of a new bounded context under `MODULE_STRUCTURE.md`
Section 8.

### What follows from those citations

Three things, and they are separable — the Product Owner may resolve them
differently from one another:

1. **The capability itself.** Starting, stopping, restarting, updating and
   restoring PIOS from a browser is control, and control is what Section 7
   forbids. Only the Product Owner can lift that, and only by writing a new
   Product Decision that says so in its own words. This ADR does not lift
   it and cannot.
2. **Where the capability lives on screen.** The request was framed as *"a
   new section inside the existing Owner Control Center"*. Section 11 says
   the opposite in advance: *"это новый инструмент с новым решением, не
   следующая версия этого"*. This ADR's Decision 3 recommends a way to
   honour both the request and the ratified sentence — a separate surface
   that reuses the same shell — but the sentence is the Product Owner's to
   keep or to amend, not this ADR's.
3. **The read-only Observation surface itself.** This ADR leaves ADR-043
   entirely intact. Nothing below adds a button to the observation console,
   changes `GET /v1/health/<module>`, or makes any of the five pilot
   modules aware that a control plane exists. That is deliberate: ADR-043
   survived its own critical review unchanged and there is no reason
   arising here to reopen it.

## Context

### What actually runs today, verified on the target machine

The target deployment is a single Windows 11 Pro laptop acting as the PIOS
home server. This is not discoverable from the repository, so it is recorded
here as the factual premise the decision rests on. If any of it is wrong,
the decision below is wrong with it.

- **JDK 21 (Temurin)**, `JAVA_HOME` set at user level. Gradle 8.10.2 through
  the committed wrapper (`backend/gradlew.bat`); no standalone Gradle.
- **PostgreSQL 17** runs as a native Windows service, `postgresql-x64-17`,
  start type Automatic, surviving reboot. Six databases exist. `pg_hba.conf`
  was set to `trust` for `127.0.0.1/::1` to match every module's
  `application.yml` (`username: postgres`, empty password — verified in
  `backend/dispatch/src/main/resources/application.yml` lines 4–7). That
  `pg_hba.conf` edit is a machine-level change no repository file manages.
- **RabbitMQ** runs as a native Windows service, `RabbitMQ`, Automatic,
  `guest`/`guest` on 5672. Only `driver-management`, `order-management` and
  `dispatch` use it; `identity` and `passenger-experience` declare no AMQP
  configuration at all.
- **The five pilot backend modules are not supervised by anything.** Each is
  started by hand with `cd backend; ./gradlew.bat :<module>:bootRun`
  (`README.md` "Running Locally"; `docs/LAUNCH_CHECKLIST.md` lines 18–22).
  There is no Windows service, no scheduled task, no supervisor, and no
  restart-on-crash behaviour of any kind. A module that dies at 03:00 stays
  dead until a human notices.
- **Docker Desktop and WSL2 are installed and working, and the backend does
  not use them.** No `Dockerfile` and no `docker-compose.yml` exists
  anywhere in the repository; `docs/LAUNCH_CHECKLIST.md` line 56 states this
  explicitly as a property of the checklist, not an omission.
- **No module writes a log file.** A search of `backend/` for `logging.file`,
  `logback` configuration or any file appender returns nothing; the only
  matches for "logging" are two `LoggingRejectAndDontRequeueRecoverer`
  classes. Every module logs to the standard output of the `gradlew bootRun`
  console that started it, and that output is lost when the console closes.
  **There is no log to retrieve today.** Requirement 8 of the request is
  therefore not "expose existing logs"; it is "cause logs to exist first".
- **`GET /v1/health/<module>` reports four facts and no more** — module name,
  `status`, `database`, `checkedAt`, plus `outbox` on the three modules that
  have one (`backend/dispatch/.../api/HealthController.kt` lines 48–57). It
  reports **no** process uptime, no memory, no start time, no version, no
  pid. Every one of those is new API surface if the control console needs
  it, not something already available.
- **Owner authentication is one replicated in-process check** —
  `OwnerCredentialGate` (dispatch copy, lines 56–153): PBKDF2-HMAC-SHA256
  against three `pios.owner.*` configuration values, constant-time compare,
  fixed failure delay, in-memory failure window, fails closed when
  unconfigured. There is no session, no token, no server-side state and no
  audit record of any kind (ADR-044 Decisions 3, 6, 7, 8).
- **The public path is one Cloudflare quick tunnel in front of
  `vite preview`**, which proxies eleven path prefixes to five local ports
  (`frontend/vite.config.ts` lines 30–42). The hostname changes on every
  tunnel restart (`PIOS_PILOT_INFRASTRUCTURE_DECISION.md` Section 4, "Если
  туннель оборвался"), and `PILOT_INFRASTRUCTURE_ROUTING_DECISION.md`
  Section 7 already names the absence of a stable address as an open,
  unresolved question made more acute by Pilot Operation.
- **A machine-specific defect affects any written start procedure.** This
  laptop's IPv6 loopback (`::1`) is broken system-wide, most plausibly by a
  third-party VPN TUN adapter unrelated to PIOS. Vite 8's dev server resolves
  `localhost` to `::1` first and therefore binds only to the broken address,
  reporting "ready" with no error and being unreachable. `npm run dev --
  --host 127.0.0.1` works. The Spring Boot modules are unaffected because
  they bind dual-stack. Any procedure this ADR's design document writes must
  carry that flag rather than repeat the failure.

### Why this cannot be built the way the observation surface was built

ADR-043 solved its problem without any new backend component by moving the
work into the browser. That answer is unavailable here, for one reason that
is structural rather than stylistic:

**A stopped process cannot start itself.** Requirement 5 of the request
("как запускать PIOS") cannot be satisfied by any endpoint hosted inside the
thing being started. Stop is self-hostable; start is not; restart is not
(the second half of it is a start). Whatever answers "start `dispatch`" must
be a different process from `dispatch`, must already be running, and must
outlive the thing it starts.

That single fact rules out the entire family of "add control endpoints to
the five modules" designs before any preference is expressed. It is worth
stating plainly, because "put it in each module, like `OwnerCredentialGate`"
is the shape this repository's own precedent would otherwise suggest.

### What kind of thing this is, and what it is not

The component this ADR ratifies has no domain, no aggregate, no invariant,
no database, no event, and no business rule. It does not know what a driver,
an order, a proposal or an assignment is, and it must never learn. It knows
Windows service names, file paths, a git repository, and a `pg_dump`
executable.

That makes it categorically different from every module ratified in ADR-018
and enumerated in `MODULE_STRUCTURE.md` Section 3. It is **not a ninth
capability**, it is **not a bounded context**, and this ADR is careful not to
let it be cited later as though it were.

## Problem

Where does the authority to start, stop, restart, update, back up and
restore PIOS live, given that:

- no module can start itself, so the authority cannot live in the five
  modules;
- no component may hold a joined view of five bounded contexts (ADR-005,
  ADR-009, ADR-043 Decision 1), so the authority may not be bolted onto a
  fan-out aggregator;
- no new *domain* module may be scaffolded without ratifying a new bounded
  context (`MODULE_STRUCTURE.md` Section 8, ADR-043 Decision 8);
- the resulting surface is, by construction, remote process control reachable
  from a phone — the most dangerous capability this project has ever
  contained;

and how is that authority constrained so that a single misclick, or a single
stolen browser session, cannot stop a platform a real driver is working on?

## Decision

### 1. A new deployable is created: the PIOS Operations Agent. It sits at the infrastructure tier, not the domain tier, and it is not a ninth module.

Working name `platform-ops` (final name is a naming choice, not an
architectural one). Properties, all of them binding:

- **Owns no domain data.** No aggregate, no entity, no value object drawn
  from `DOMAIN_MODEL.md`. It has no business invariant because it models no
  business.
- **Has no database.** Not PostgreSQL, not SQLite, not any store. Its only
  persistent artefact is an append-only audit file (Decision 7) and the
  backup files it produces. It therefore cannot participate in
  `DATABASE_DESIGN.md` Sections 4–5 and cannot violate ADR-005 by
  construction, because there is nothing for it to own.
- **Publishes and consumes no event.** It is not on the RabbitMQ topology at
  all. `EVENT_CATALOG.md` is unchanged by this ADR.
- **Calls no module's domain API, ever.** Not `/v1/orders`, not
  `/v1/assignments`, not `/v1/drivers`, not any of them. See Decision 5 for
  the one apparent exception and why it is not one.
- **Is not a module under `MODULE_STRUCTURE.md` Section 3.** It is not added
  to that document's list of eight, it does not map to a capability in
  ADR-018, and no future document may cite it as precedent for adding a
  domain module without an ADR that ratifies a bounded context.

**Why a separate tier rather than a ninth module.** ADR-018's eight
capabilities are all answers to "what does the business do". This component
is an answer to "what keeps the software running", which is the same
category as `postgresql-x64-17` and the `RabbitMQ` service — neither of which
is a PIOS module either. Calling it a module would require ratifying a
bounded context for something that has no bounded context to ratify, and
would make `MODULE_STRUCTURE.md` Section 2's "Domain Alignment" principle
("every module aligns exactly with one of the eight architectural components
ratified in ADR-018") false the moment it were added.

**Explicitly: this does not discharge ADR-043 Decision 8's gate.** The
Operations bounded context — correlated cross-context diagnostics, incident
history, event-stream-backed timelines, notification delivery while no
browser is open — remains unbuilt and still requires its own ratification.
The agent ratified here is forbidden from growing into it: Decision 5 bars
the mechanism (fan-out over domain APIs) by which it would have to.

### 2. The agent does not manage processes. It commands the Windows Service Control Manager, over a closed allow-list, and never composes a command line from anything a caller sent.

This is the decision that determines whether this feature is a control panel
or a remote code execution service. The distinction is not "we validate
input carefully"; it is "there is no input to validate".

- Each of the five pilot modules is registered as a **Windows service** with
  a fixed name, fixed working directory and fixed command line, established
  once at install time by an operator, outside the agent (Decision 9 names
  what the operator must do; the design document specifies it).
- The agent holds a **compile-time-closed set of controllable targets**. Each
  target is a `(logical-name, windows-service-name)` pair. A request names a
  logical target; the agent looks it up in that fixed map and refuses
  anything not found, before any other processing.
- The agent invokes the Service Control Manager for that resolved service
  name. **No caller-supplied string ever reaches a command line, a shell, a
  path, an argument, or an environment variable.** There is no string
  concatenation into a command anywhere in this component.
- **There is no generic action.** No `POST /v1/ops/exec`, no `command`
  parameter, no `script` parameter, no `path` parameter, no "advanced mode",
  not now and not in any later iteration without an ADR that supersedes this
  sentence. A future request for "just let me run one PowerShell line from
  the phone" is a request to delete this decision, and must be treated as
  one.

**Consequence, stated plainly so it is chosen rather than discovered:** the
five modules must stop being `gradlew bootRun` consoles and become installed
Windows services before any of this works. That is a real change to how PIOS
is operated on this machine, it is the largest operational prerequisite in
this ADR, and it is listed among the open decisions because it also changes
how the Product Owner starts PIOS by hand on days when nothing is wrong.

### 3. Control lives on its own surface, its own route and its own credential. The observation console is not modified.

- The observation console at `/owner` keeps exactly the behaviour ADR-043
  and ADR-044 gave it. No control button is added to `StatusCard`,
  `TodayCard`, `EventFeed` or `OwnerControlCenter.tsx`'s action row.
- The control surface is a **separate route** (`/owner/server` recommended)
  rendering a separate component tree, reachable only after a **second,
  separate credential** is presented (Decision 4).
- The two surfaces may share the visual shell (`Header`, `ActionButton`,
  the CSS module) exactly as any two pages in this application already do.
  Sharing a stylesheet is not sharing a boundary.

**How this relates to Section 11 of the Product Decision.** Section 11 says
control, if it ever comes, is *"новый инструмент с новым решением"* — a new
tool with a new decision. A separate route, separate component tree,
separate credential, separate backend and separate ADR is, on every
architectural measure, a new tool; that it is reachable from the same
browser tab is a navigation convenience, not an architectural merge. This
ADR recommends that reading. **It does not impose it** — if the Product
Owner reads Section 11 as requiring a physically separate application, that
is a legitimate reading of their own sentence, and the design document
carries it as an open decision rather than resolving it silently.

### 4. Destructive operations require a second credential. This is a deliberate, named deviation from ADR-044 Decision 6.

ADR-044 Decision 6 is explicit: *"One credential, one capability: read the
observation surface... no audit of who did what — because there is exactly
one subject and it performs no actions."* Both halves of that reasoning stop
being true here: the subject now performs actions, and the actions are
destructive.

Decision:

- The agent verifies its own credential — `pios.ops.username`,
  `pios.ops.password-hash`, `pios.ops.password-salt` — using the **same
  mechanism** as `OwnerCredentialGate`: PBKDF2-HMAC-SHA256, constant-time
  compare, no `WWW-Authenticate` header on 401, fail-closed when
  unconfigured, per-process failure delay and failure window. The mechanism
  is reused so that no second cryptographic idiom enters the project
  (ADR-044 Decision 2's own reasoning).
- The credential value is **different from `pios.owner.*`** and must be
  generated separately. A stolen observation credential must not be able to
  stop the platform, and today it would be, if one credential covered both.
- The agent holds **no session and issues no login artefact.** The credential
  is presented per request in `Authorization: Basic`, exactly as ADR-044
  Decision 3 established, so the property ADR-044's revision fought hardest
  for — immediate, total revocation by changing a configuration value — is
  preserved unchanged.

**What is deliberately *not* done here, and why.** No second factor (TOTP,
passkey, SMS) is specified inside the application. Reason: it would be the
first hand-rolled or newly-dependency-bearing authentication protocol in
PIOS, replicated nowhere else, protecting one component — precisely the
trade ADR-044's Alternatives section rejected when it declined Spring
Security for five modules. The stronger outer layer is specified where it
costs nothing to get right: **Cloudflare Access in front of the control
hostname** (Decision 8), which ADR-044 Decision 10 already named as *"the
recommended next step"* toward the Defense in Depth that ADR-011 requires
and ADR-044 admits it does not provide. Whether to require it is an open
decision for the Product Owner; the architecture's recommendation is yes,
and yes before the first destructive endpoint exists rather than after.

### 5. The agent never asks a domain module anything. Any correlation between platform state and business state happens in the browser.

The temptation this decision forecloses is concrete and will be felt on the
first day: *"refuse to stop the platform while a ride is in progress"* is an
obviously good idea, and implementing it requires the agent to ask `dispatch`
about assignments. That would make the agent a component that fans out over
domain APIs — the exact shape ADR-043 Decision 1 forbids by name and
`PILOT_INFRASTRUCTURE_ROUTING_DECISION.md` Variant G re-rejected on the same
grounds.

So:

- The agent reports **only platform facts**: is this service running, since
  when, what does the Service Control Manager say, what did the last action
  do, what is in the log tail, what backups exist.
- The **browser** — which, in the control console, is already logged in to
  the observation surface and already holds today's orders and assignments
  by ADR-043 Decision 1's own mechanism — is what renders "сейчас
  выполняется N поездок" beside the Stop button, and what refuses to enable
  the button until the operator has seen that number.
- That warning is **a display of a fact, not a business rule.** Whether an
  in-progress ride *should* block a stop, and for how long, and with what
  override, is a business rule this ADR does not invent and may not
  (`CLAUDE.md` "Never Invent Business Rules"; ADR-002). It is carried to the
  Product Owner as an open decision.

The honest cost of this decision is named rather than hidden: because the
guard lives in the browser, a caller who talks to the agent's API directly
bypasses it. The API-level protection against that is Decision 6, which does
not depend on the browser at all.

### 6. Every destructive operation is a two-step, single-use, expiring confirmation. Confirmation dialogs are the UI half; this is the other half.

A dialog protects against a misclick. It does not protect against a replayed
request, a double-submit on a flaky mobile connection, or a stolen session
issuing `POST /stop` directly. The API therefore requires:

1. **Step one — declare intent.** The console asks the agent to prepare the
   action. The agent returns a **confirmation token**: an opaque random
   value, held in the agent's own memory, bound to exactly one
   `(action, target)` pair, valid for a short fixed window (60 seconds
   recommended), usable exactly once, invalidated by use or by expiry, and
   discarded entirely if the agent restarts.
2. **Step two — execute.** The action endpoint refuses without a matching,
   unexpired, unused token for that exact action and target.

**Why this is not the token ADR-044 deleted, checked against that ADR's own
four objections** (`ADR-044` "Revision 2026-08-02"):

| ADR-044's objection to its own token | Applies here? |
| --- | --- |
| It created an inter-module trust relationship — module A issued, module B accepted | **No.** One component issues and the same component consumes. No module issues or accepts anything. No module is even aware this exists |
| It made revocation impossible for twelve hours | **No.** Lifetime is seconds, single-use, and it authenticates nothing — the credential is still checked independently on the execute call |
| It was the only hand-rolled cryptography in PIOS | **No.** It is not cryptography. It is an opaque random value compared for equality; there is no signature, no format, no claims, nothing to get subtly wrong |
| It required two endpoints that were unnecessary | **The two steps are the point here.** The second call is not a second way to authenticate; it is the mechanism that makes a destructive action non-replayable |

The token is an **idempotency and intent mechanism, not an authentication
mechanism**, and ADR-044's reasoning against session tokens is preserved
intact by that distinction.

Additionally: a destructive action carries an explicit
`confirmation` field whose value the operator typed (the target's own name),
so that a replay of a captured request body still fails against a
freshly-required token, and so that a request constructed by accident cannot
satisfy the shape.

### 7. Every action is recorded, before and after, in an append-only audit log the agent itself cannot rewrite.

ADR-044 Consequences state the debt directly: *"There is no audit trail...
it stops being tolerable the moment there is a second subject."* It also
stops being tolerable the moment the single subject can stop production,
which is now.

- One append-only file, written by the agent, one line per event, containing:
  timestamp, action, target, outcome, source IP, and whether a confirmation
  token was consumed. **Never the credential, never a token value.**
- Written for the *attempt* as well as the *result*, so a refused action and
  a crashed action are both visible.
- Exposed read-only through the agent's own API so the console can render it,
  and included in the diagnostic report.
- The agent has no endpoint that deletes or edits it. Rotation is an
  operator action on the filesystem, deliberately outside the API.

This is not a general observability solution and does not discharge ADR-012's
outstanding cross-module tracing debt, which ADR-043 already declined to
discharge and this ADR declines equally.

### 8. The control plane is not reachable through `vite preview`, and therefore not through today's tunnel arrangement unchanged.

This is the single most easily-missed failure in the whole design, so it is
a decision rather than a note.

Today, everything public reaches PIOS through one Cloudflare quick tunnel
pointed at `vite preview` on 4173, which proxies onward to the five modules
(`frontend/vite.config.ts` lines 30–42). If the control API were added as a
twelfth row in that proxy table, then **`vite preview` would be a dependency
of the ability to restart `vite preview`** — and the first time that process
died, the phone would show nothing and control nothing, in exactly the
situation the tool exists for. `PILOT_INFRASTRUCTURE_ROUTING_DECISION.md`
Section 3 rejected Variant C on a structurally identical argument: a
monitoring path that shares a failure mode with the thing it monitors
produces false diagnoses.

Decision: **the control plane gets its own ingress, terminated by
`cloudflared` itself and pointed directly at the agent's port**, not through
the Vite process. Two consequences follow, and both are open decisions for
the Product Owner because both cost something:

- A quick tunnel cannot do this cleanly and changes hostname on every
  restart. A **named Cloudflare Tunnel** (account, own domain, stable
  hostname, `cloudflared` running as a Windows service) is the recommended
  mechanism. `PILOT_INFRASTRUCTURE_ROUTING_DECISION.md` Section 7 already
  records the stable-address question as open and already notes the pilot has
  outgrown the "supervised session" scope the original infrastructure
  document was written for. This ADR does not resolve that; it makes it
  unavoidable.
- With a named tunnel, **Cloudflare Access on the control hostname** becomes
  a configuration checkbox rather than a project. Decision 4 recommends it.

`PIOS_PILOT_INFRASTRUCTURE_DECISION.md` and
`PILOT_INFRASTRUCTURE_ROUTING_DECISION.md` are **extended, not superseded**,
by whatever is chosen: their existing routing table and its
"no prefix is rewritten" condition stay literally true, because nothing is
added to that table by this ADR.

### 9. Auto-recovery is the operating system's job, not the agent's.

An agent that supervises the five modules is itself unsupervised, and the
question "what restarts the restarter" has no good answer inside the
application.

- **Crash recovery** for the five modules, for PostgreSQL, for RabbitMQ and
  for the agent itself is configured as **Windows Service Control Manager
  recovery actions** (restart on first, second and subsequent failure, with
  a reset period) plus start type Automatic so everything returns after a
  reboot. This is native, already in use for `postgresql-x64-17` and
  `RabbitMQ`, and survives the agent being dead.
- **The agent does not implement a supervision loop, a watchdog, or a
  periodic "is it up, if not start it" scheduler**, and must not acquire one
  as an implementation detail. Its role in recovery is to *report* what the
  Service Control Manager did and to let a human act when automation did not
  help.
- **The agent never restarts itself** through its own API. Its own recovery
  is the SCM's, exactly like every other target.

An explicit limitation, disclosed rather than papered over: SCM recovery
restarts a *dead process*. It does nothing for a process that is alive and
broken — a module whose database is unreachable, or whose outbox is stalled.
Those states are visible on the observation console (ADR-043 Decision 2) and
are a human decision, and this ADR does not automate them, because "when
should a degraded but running service be restarted" is an operational policy
nobody has decided.

### 10. Updates are operator-initiated, reversible, and never automatic.

- The agent can report the currently deployed revision and whether the
  configured source has a newer one. It does not apply anything on its own,
  on a schedule, or on a webhook.
- An update is a discrete, confirmed action under Decisions 6 and 7, and it
  records the revision it moved *from* before it moves.
- **Rollback is mandatory, not optional.** ADR-014 is binding here: *"A
  release is not considered acceptable unless it can be reversed; if a
  released change cannot be rolled back to the domain's prior accepted
  version, that change is not treated as ready."* An update path that cannot
  return to the recorded prior revision does not satisfy ADR-014 and may not
  ship.
- **Database migrations are the sharp edge and are not solved here.** Flyway
  migrations run forward on module start and PIOS has no down-migrations. A
  code rollback across a migration boundary is therefore *not* reversible by
  the mechanism above, and the agent must not pretend otherwise: an update
  that includes a migration is disclosed as such to the operator, and the
  reversal path for it is "restore the backup", not "roll back the code".
  How that is to be handled as policy is an open decision.
- Whether updates may be applied while a driver is working is a business and
  operational decision, not an architectural one. Open decision.

### 11. Backups are produced by PostgreSQL's own tooling, per database, and their destination and retention are not invented here.

- One `pg_dump` per database, six databases, invoked with a fixed executable
  path and fixed arguments (Decision 2: no caller-supplied strings).
- Restore is **the most destructive action in the entire system** — it
  discards live data — and is therefore subject to Decisions 6 and 7 without
  exception, plus a stricter confirmation than any other action, plus a
  requirement that the target service be stopped first.
- **Where backups are stored, how long they are kept, whether they are
  encrypted, whether they leave the laptop, and whether restore should be
  exposed through a browser at all** are policy questions with real
  consequences (a backup file of six databases contains every passenger name
  and address PIOS holds). This ADR does not answer any of them. They are
  open decisions, listed as such.

### 12. Docker is not introduced, and introducing it would be a separate decision of its own.

Docker Desktop and WSL2 work on this machine, and a containerized design
would make several parts of this ADR simpler. It is not chosen, for reasons
that are about honesty rather than preference:

- The repository contains no `Dockerfile` and no `docker-compose.yml`, and
  `docs/LAUNCH_CHECKLIST.md` line 56 records the native install as a stated
  property. Containerizing PIOS changes how every module is built, started,
  configured and networked — including PostgreSQL's `trust` authentication
  and RabbitMQ's `guest`/`guest`, neither of which survives a naive move.
- ADR-026 explicitly places infrastructure topology outside its own scope and
  defers it to a future Infrastructure decision under ADR-022. Choosing
  containers inside a control-panel design would be deciding that deferred
  question as a side effect of building a UI — the same anti-pattern
  `PILOT_INFRASTRUCTURE_ROUTING_DECISION.md` refused when it declined to
  settle the stable-address question inside a routing question.
- ADR-014 requires reversibility, and a container migration performed as an
  implementation detail of this feature would not be reversible in any
  practical sense.

If the Product Owner wants containers, that is a legitimate direction and a
good one — and it needs its own ADR, taken on its own merits, before this
one is implemented, not alongside it.

### 13. What this ADR does not authorize.

Named explicitly, so none of it arrives as an implementation detail:

- No business rule about when PIOS may or may not be stopped (ADR-002,
  `CLAUDE.md`).
- No change to any of the five modules' domain code, schema, events or
  existing endpoints. If the console needs richer facts than
  `GET /v1/health/<module>` returns today, that is new API surface requiring
  its own specification — see the design document — and it is additive and
  optional or it does not happen.
- No control over `network-management`. It is isolated (ADR-037), absent from
  the pilot proxy, absent from the observation console
  (`moduleBaseUrls.ts` lines 22–39), and it is not started today. It is not a
  target of this agent. If it is ever started, that is its own decision.
- No Operations bounded context (ADR-043 Decision 8, unchanged).
- No participant authentication, and no closing of the existing public
  `/v1/**` endpoints (ADR-044 Decision 11, unchanged). This ADR must not be
  described as having made PIOS secure; it adds a locked door to a new,
  dangerous room in a house whose front door is still open.
- No notifications, push or otherwise (`PRODUCT_DECISION_OWNER_CONTROL_CENTER.md`
  Section 9). An agent that can see a service is down is one small step from
  an agent that texts about it, and that step is a separate decision.

## Alternatives Considered

- **Control endpoints inside each of the five modules, replicated like
  `OwnerCredentialGate`.** Rejected on a structural ground, not a
  preference: a stopped process cannot start itself, so this design cannot
  satisfy the "start" requirement at all. It would also put a
  process-killing endpoint on the same deployable that serves passenger and
  driver traffic.
- **A Windows service that exposes no network surface, driven only by files
  on disk or by Task Scheduler.** Genuinely safer, and rejected only because
  requirement 13 (phone control) cannot be met by it. Retained as the
  fallback if the Product Owner decides remote control is not worth its
  risk — which would be a defensible decision, not a retreat.
- **Extending the existing Owner Control Center console and its credential
  to include control.** Rejected: it contradicts
  `PRODUCT_DECISION_OWNER_CONTROL_CENTER.md` Sections 7 and 11 and ADR-043
  Decision 5 in the most direct way available, and it would mean one stolen
  read-only credential can stop production.
- **Spring Boot Actuator with `management.endpoint.shutdown` enabled.**
  Rejected on the same ground ADR-043 Decision 2 rejected Actuator, only
  more so: it brings a broad management surface into five live services, it
  can only stop and never start, and enabling remote shutdown on the
  deployables that serve driver traffic is the opposite of Least Privilege.
- **A generic remote-command endpoint with an allow-list of command
  strings.** Rejected. An allow-list of strings is one refactor away from an
  allow-list with a parameter, which is remote code execution with extra
  steps. Decision 2's "no caller-supplied string reaches a command line" is
  chosen precisely because it is a property that cannot erode gradually.
- **Routing control through the existing `vite preview` proxy.** Rejected —
  Decision 8. It makes the process being controlled a dependency of the
  ability to control it.
- **Containerizing PIOS and controlling containers instead of services.**
  Not rejected on merit; deferred as requiring its own decision —
  Decision 12.
- **An agent that watchdogs the five modules and restarts them
  automatically.** Rejected — Decision 9. It moves the unsupervised-process
  problem rather than solving it, and the operating system already solves it.

## Consequences

### Positive

- PIOS becomes recoverable by its owner without a terminal, which is the
  single largest operational gap in Pilot Operation today: right now a module
  that dies overnight stays dead until someone notices and runs Gradle.
- The five modules gain crash recovery and reboot survival as a by-product
  of Decision 2's service registration, whether or not anyone ever opens the
  control console. That is arguably worth more than the console.
- Log files begin to exist (Context: they do not today), which makes every
  future diagnosis cheaper.
- The dangerous capability is quarantined: separate deployable, separate
  credential, separate route, separate ingress, closed target list, no
  command composition, two-step confirmation, audit log. No single one of
  those is sufficient; the set is Defense in Depth applied where ADR-011
  requires it and ADR-044 openly admitted it was absent.
- ADR-043 and ADR-044 remain literally true and unmodified. The observation
  surface does not become "the thing that can also stop PIOS", which is
  precisely what `PRODUCT_DECISION_OWNER_CONTROL_CENTER.md` Section 11 was
  written to prevent.

### Negative, disclosed rather than argued away

- **This is the largest single increase in attack surface in the project's
  history.** Before it, the worst outcome of a compromised owner credential
  was disclosure of operational internals that ADR-044 already characterised
  as narrow. After it, the worst outcome is a stopped, unrecoverable or
  data-reverted platform. Every mitigation above is real and none of them
  changes that sentence.
- **A ninth running process** must itself be kept alive, configured,
  credentialed and updated. It is one more thing that can be down — and if it
  is down when everything else is down, the phone shows nothing.
- **The five modules must become Windows services**, which changes the daily
  manual start procedure the Product Owner and the developer currently use
  and which `README.md` and `docs/LAUNCH_CHECKLIST.md` document. Those
  documents must be updated, not silently outgrown.
- **The Windows Service Control Manager is now an architectural dependency.**
  The design is deliberately not portable; a future move to Linux or to
  containers rewrites Decision 2's implementation entirely. This is accepted
  because the deployment target is one specific laptop and pretending
  otherwise would produce an abstraction serving a hypothetical.
- **The browser-side ride guard (Decision 5) is bypassable** by anyone
  calling the API directly. The API-level guarantees are the credential, the
  confirmation token and the audit log; the ride count is advisory.
- **Restore-from-backup is exposed to a browser.** Even with every control
  above, this is a button that destroys live data. The Product Owner may
  reasonably decide it should not exist on this surface at all, and the
  design carries that as an open decision rather than assuming.
- **Rollback across a Flyway migration is not solved** (Decision 10), so
  ADR-014's reversibility requirement is met for code and not met for
  schema. That gap is named, not closed.
- **Nothing here helps if the laptop is off, asleep, or off the internet.**
  A control plane hosted on the machine it controls cannot report that the
  machine is gone. The console must therefore distinguish "PIOS is stopped"
  from "I cannot reach the server", the same distinction
  `PIOS_OWNER_CONTROL_CENTER_MVP_DESIGN.md` Section 5.5 already established
  and for the same reason.

## Traceability

| Subject | Source |
| --- | --- |
| The prohibition this ADR asks to be lifted | ADR-043 Decision 5 (lines 281–293); `PRODUCT_DECISION_OWNER_CONTROL_CENTER.md` Sections 7 (line 53) and 11 (line 87) |
| The route out of that prohibition, prescribed in advance | ADR-043 Decision 5, final paragraph ("its own Product Decision... and its own ADR") |
| No fan-out aggregator, no component holding a joined view | ADR-043 Decisions 1 and 2; `PILOT_INFRASTRUCTURE_ROUTING_DECISION.md` Variant G; ADR-005, ADR-009 |
| Operations bounded context still ungranted | ADR-043 Decision 8; `MODULE_STRUCTURE.md` Section 8 (line 116) |
| Domain Alignment principle a ninth module would break | `MODULE_STRUCTURE.md` Section 2 (line 17), Section 3 |
| Credential mechanism reused rather than reinvented | ADR-044 Decisions 2, 3; `OwnerCredentialGate.kt` (dispatch copy) lines 56–153 |
| Why a confirmation token is not the token ADR-044 deleted | ADR-044 "Revision 2026-08-02", its own four objections |
| Audit-trail debt this closes for its own surface | ADR-044 Consequences ("There is no audit trail") |
| Defense in Depth, admitted unmet, and the recommended outer layer | ADR-011 (lines 19–23); ADR-044 Decision 10 |
| Reversibility of a release is mandatory | ADR-014 Decision (line 17) |
| Infrastructure topology is a separate, later decision | ADR-026 Decision (second paragraph); ADR-022 |
| Existing public ingress and its single-origin proxy table | `frontend/vite.config.ts` lines 30–42; `PIOS_PILOT_INFRASTRUCTURE_DECISION.md` Sections 4–5 |
| Stable public address already recorded as an open question | `PILOT_INFRASTRUCTURE_ROUTING_DECISION.md` Section 7 |
| Why a monitoring path must not share a failure mode with its target | `PILOT_INFRASTRUCTURE_ROUTING_DECISION.md` Section 3, Variant C, argument 3 |
| Health endpoint's actual, narrow response shape | `backend/dispatch/src/main/kotlin/com/pios/dispatch/api/HealthController.kt` lines 39–63 |
| No file logging exists today | Absence of any `logging.file`/logback configuration in `backend/**` |
| Modules started by hand, unsupervised | `README.md` "Running Locally"; `docs/LAUNCH_CHECKLIST.md` lines 17–23 |
| No Docker in the repository | `docs/LAUNCH_CHECKLIST.md` line 56 |
| `network-management` excluded | ADR-037; `frontend/src/pages/OwnerControlCenter/moduleBaseUrls.ts` lines 1–39 |
| No business rule may be invented | ADR-002; `CLAUDE.md` |

## Files Changed

This ADR only (`docs/ADR/ADR-045-Platform-Operations-Control-Boundary.md`,
new). No source file is touched by this document, and none may be until the
prerequisites below are met.

**Prerequisites before any Developer task starts under this ADR:**

1. A new Product Decision by the Product Owner — a separate document, not an
   amendment buried in the existing one — that (a) authorizes control at all,
   overriding `PRODUCT_DECISION_OWNER_CONTROL_CENTER.md` Section 7, (b)
   states how Section 11's *"новый инструмент, не следующая версия этого"* is
   to be honoured, and (c) answers the open decisions listed in
   `docs/PIOS_SERVER_CONTROL_CENTER_DESIGN.md` Section 16.
2. Ratification of this ADR by the Product Owner.
3. `API_SPECIFICATION.md` and `INTERFACE_CONTRACTS.md` entries for every
   endpoint in the design document, written **before** implementation
   (ADR-006, `CLAUDE.md`).
4. A decision on the ingress mechanism (Decision 8), because the answer
   determines whether the control surface is reachable at all.

`PRODUCT_DECISION_OWNER_CONTROL_CENTER.md` is **not edited** by this work.
Per `CLAUDE.md`'s "Never Delete Documentation", a superseding Product
Decision supersedes it in the open, leaving the original text intact and
findable.

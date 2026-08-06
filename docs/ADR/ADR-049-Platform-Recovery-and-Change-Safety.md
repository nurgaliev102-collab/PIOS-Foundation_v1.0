# ADR-049: Platform Recovery and Change Safety — Returning to a Known-Good State Is Owned Outside the Application

## Status

**Draft — awaiting Product Owner ratification.**

Authorized in principle by the Product Owner's instruction of 2026-08-06,
which names *«automatic recovery»*, *«backup»* and *«update»* among the
component's purposes, and by
`docs/PRODUCT_DECISION_PLATFORM_OPERATIONS_CENTER.md` (itself a draft).

**Package.** ADR-046 (what the component is), ADR-047 (who may make it act),
ADR-048 (how the instruction arrives), **049 (this — how the platform returns
to a known-good state)**. Together they supersede ADR-045.

**Relationship to ADR-014.** This is the only ADR in the package that is
bound by an existing ratified decision rather than merely consistent with
one. ADR-014 requires reversibility as a property of a release, and Decision
3 below both implements it and names exactly where it cannot be met.

## Context

### The gap this closes is the largest one in Pilot Operation

Verified, not assumed: the five pilot modules are started by hand with
`cd backend; ./gradlew.bat :<module>:bootRun` (`README.md` "Running
Locally"; `LAUNCH_CHECKLIST.md` lines 17–23). There is no Windows service, no
scheduled task, no supervisor and no restart-on-crash behaviour of any kind.

A module that dies at 03:00 stays dead until a human notices. PIOS is in
Pilot Operation — Артур works day to day with no operator present
(`PIOS_PILOT_OPERATION_PROTOCOL.md` Section 1) — so "until a human notices"
can be a whole working day.

By contrast, PostgreSQL (`postgresql-x64-17`) and RabbitMQ (`RabbitMQ`)
already run as native Windows services with Automatic start and already
survive reboot. The JVM modules are the anomaly, not the norm, on their own
machine.

### What already exists to build on

- **Flyway runs forward, on module start**, per module, from that module's
  own `db/migration` location (`application.yml` lines 8–10). **There are no
  down-migrations anywhere in this repository.**
- **The outbox pattern already protects events across a stop.** An
  unpublished row (`published_at IS NULL`) survives a shutdown and is
  published by that module's own relay after restart (ADR-031, ADR-032).
  Stopping the platform does not lose events, and that is worth saying out
  loud because the intuitive fear is the opposite.
- **Six databases exist** — `pios_driver_management`, `pios_order_management`,
  `pios_dispatch`, `pios_identity`, `pios_passenger_experience`,
  `pios_network_management` — reachable as `postgres` with an empty password
  over `trust` on `127.0.0.1` (`application.yml` lines 4–7; `pg_hba.conf` on
  the machine).
- **No module writes a log file.** No `logging.file` property, no logback
  configuration, no file appender in `backend/`. Everything goes to the
  `bootRun` console's stdout and dies with it.

That last point has a consequence that must be faced before anything else in
this ADR: once the modules become Windows services (ADR-046 Decision 2), they
have **no console at all**, so the logs stop existing entirely rather than
merely being inconvenient.

## Problem

How does PIOS return to a known-good state — after a crash, after a reboot,
after a bad release, and after data loss — given that a supervisor living
inside the application dies with the application, that ADR-014 requires every
release to be reversible, and that Flyway makes schema changes one-way?

## Decision

### 1. Crash recovery and reboot survival belong to the operating system's service manager, not to the agent.

An agent that supervises the five modules is itself unsupervised, and *"what
restarts the restarter"* has no good answer inside the application.

- Every target — the five modules, `frontend`, `cloudflared`, PostgreSQL,
  RabbitMQ **and the agent itself** — is registered as a Windows service with
  start type **Automatic** and with **Service Control Manager recovery
  actions** configured (restart on first, second and subsequent failure, with
  a reset period).
- This is native, already proven on this machine for two of those services,
  and **it works while the agent is dead**, which is the property no
  in-application design can have.
- The agent's role in recovery is to be **configured once at install time**,
  to **report** what the service manager did, and to let a human intervene
  when automation did not help.

**The agent does not implement a supervision loop, a watchdog, or a periodic
"is it up — if not, start it" scheduler, and must not acquire one as an
implementation detail.**

**How this reconciles with the Product Owner's own wording.** The instruction
of 2026-08-06 names *«automatic recovery»* as a purpose of the component, and
that purpose is met in full: the platform does come back by itself, after a
crash and after a reboot. What this decision fixes is *where the mechanism
lives* — the outcome is the component's responsibility, the mechanism is the
operating system's. `PRODUCT_DECISION_PLATFORM_OPERATIONS_CENTER.md` Section
9 states the same thing in the owner's own language.

**Disclosed limitation, not papered over.** The service manager restarts a
**dead process**. It does nothing for a process that is alive and broken — a
module whose database is unreachable, a stalled outbox, an exhausted
connection pool. Those states are visible on the observation console (ADR-043
Decision 2 already surfaces both) and remain a **human decision**. Automating
them would require a rule — *when should a degraded but running service be
restarted* — that nobody has decided, and inventing it here would breach
ADR-002 and `CLAUDE.md`. It is deferred in
`PRODUCT_DECISION_PLATFORM_OPERATIONS_CENTER.md` Section 11.7.

How aggressive the restart schedule should be is likewise the Product Owner's
(same Section). Architecture's recommendation, stated as a recommendation:
30 s / 60 s / 120 s, counter reset after 24 h.

### 2. The agent is not a target of itself, and start/stop/restart ordering is defined, deterministic and disclosed.

- **`platform-ops` cannot be started, stopped or restarted through its own
  API.** Otherwise one tap disables the ability to enable anything. Its own
  recovery is the service manager's, exactly like every other target.
- **Group start order** (`pios`): PostgreSQL, then RabbitMQ, then the five
  modules, then `frontend`, then `tunnel`.
- **Group stop order** is the reverse, with one deliberate exception:
  **PostgreSQL and RabbitMQ are not stopped by the group action.** Stopping
  the database to "stop PIOS" lengthens the outage and adds risk while
  gaining nothing — neither serves outside traffic. Both remain individually
  addressable targets.
- **Restart is stop-then-start as one operation, and if the stop fails the
  start is not attempted.** The operation is marked failed at the step that
  failed. This prevents the most common way a working system becomes a broken
  one: starting a second instance while the first still holds the port.
- **A failed step halts a group sequence.** Half a stop is reported as half a
  stop, never as a success.

**Named explicitly so it is never mistaken for a dependency:** the ordering
among the five modules is chosen for *predictability and legibility on
screen*, not because any module requires another to start. It does not —
ADR-009, ADR-026 and ADR-044 Decision 1 (*"No module depends on any other
module being up"*) all hold, and this ordering must never be cited as
evidence that they do not.

### 3. A release is reversible or it is not ready. Where that cannot be met, the gap is named, not hidden.

ADR-014 binds directly:

> A release is not considered acceptable unless it can be reversed; if a
> released change cannot be rolled back to the domain's prior accepted
> version, that change is not treated as ready.

Therefore:

- **Updates are operator-initiated, always.** No schedule, no webhook, no
  auto-apply. The agent may report that a newer revision exists; it applies
  nothing on its own.
- **The prior revision is recorded before anything moves**, and returning to
  it is a first-class action, not a manual recovery procedure.
- **A backup is taken before an update begins, and if the backup fails the
  update does not start.**
- **The target revision is not free-form.** An update request names a
  revision that must match the one the agent itself reported as available;
  an arbitrary revision is refused (ADR-046 Decision 2: nothing from a
  request becomes a command argument).

**The migration asymmetry, stated as a decision rather than discovered as a
surprise.** Flyway migrates forward only and this repository has no
down-migrations. So:

> **A code rollback across a migration boundary is not a rollback.**

Consequences, all binding:

- the agent reports whether a pending update **contains migrations** — a fact
  about files in `db/migration`, not a judgement — and the console warns
  **before** the action, not after;
- for such an update, the documented reversal path is **restore from backup**,
  not code rollback, and the interface must say so in those words;
- **the agent may never present a rollback as safe when it is not.**

This means ADR-014's requirement is **met for code and not met for schema**.
That gap is named here rather than closed, because closing it means either
down-migrations (a project-wide engineering decision nobody has taken) or
restore-from-backup as a routine operation (which depends on
`PRODUCT_DECISION_PLATFORM_OPERATIONS_CENTER.md` Section 11.3, still open).

Whether updates may be applied from a phone, during a working shift, or at
all when they contain migrations, are business and operational questions
deferred in that same Product Decision (Section 11.6).

### 4. Backups are produced by PostgreSQL's own tooling, per database, and restore is deliberately treated as the most dangerous action in the system.

- One `pg_dump` per database, **six databases**, with a fixed executable path
  and fixed arguments. `pios_network_management` is included even though the
  module is not running (ADR-037): the database exists, and a backup that
  silently skips one of six is a trap, not an economy.
- A backup is marked complete **only after all six succeed**. A partial
  backup stays visible but is marked, and cannot be restored from — restoring
  from a truncated dump loses data silently, which is worse than failing
  loudly.
- **Scheduled backups run from the operating system's scheduler, not from a
  timer inside the agent** — the same reasoning as Decision 1: a schedule
  living inside a process dies with the process.
- Application configuration is **not** in the backup: it lives in the
  repository and in the service definitions.

**Restore.** It destroys live data, and it is therefore the strictest path in
the entire package:

- the target must already be stopped; restoring into a running database is
  refused;
- it requires ADR-047's confirmation token and typed confirmation, with a
  stricter phrase than any other action;
- **a backup of the current state is taken before the restore**, so that an
  erroneous restore is itself reversible;
- it produces its own distinct entry in the action journal, naming which
  backup was applied.

**Whether restore should exist in a browser at all is an open decision**
(`PRODUCT_DECISION_PLATFORM_OPERATIONS_CENTER.md` Section 11.3). Architecture
recommends **removing it from the web surface**: its only use case is a
catastrophe in which the owner is already at the machine or on the phone with
a developer, and its presence means a stolen operations session can destroy
data rather than merely stop a service. **Until that question is answered,
the capability does not exist.**

Where backups are stored, how long they are kept, whether they are encrypted
and whether they leave the laptop are likewise deferred (Section 11.4) — a
dump of six databases contains every passenger name and address PIOS has ever
held, which makes this a data-protection policy question, not a technical
detail.

### 5. Diagnosis requires logs, and logs must be made to exist before they can be retrieved.

There is no log to retrieve today, and after ADR-046 Decision 2 there would
be none at all. So this is a prerequisite of the package, not a feature of
it.

- **Each of the five modules writes to a rolling log file**, configured
  through Spring Boot's own `logging.file.name` and rolling-policy
  properties. This is an additive configuration change: no new dependency, no
  new configuration file, no code, no contract, no migration. It creates no
  cross-module relationship and changes no ratified decision — the same test
  by which `PIOS_PILOT_INFRASTRUCTURE_DECISION.md` Section 0 concluded no ADR
  was required for its own change. **No separate ADR is needed for it**; it
  is authorized here as a prerequisite.
- **The agent reads those files by closed table, tail-first and bounded**
  (ADR-046 Decision 5). Never a whole file: diagnosis must not get heavier as
  things get worse — the trap ADR-043 Decision 2 already caught in its own
  first draft over `findUnpublished()`.
- **The agent returns lines, never files.** No download endpoint.
- Log retention values, and whether personal data in logs must be redacted
  before the owner copies them to a developer, are deferred
  (`PRODUCT_DECISION_PLATFORM_OPERATIONS_CENTER.md` Sections 11.8).
  Architecture's recommendation: 20 MB per file, 14 files, 500 MB cap per
  module; do not redact in the files, but warn on screen before copying.

### 6. What this ADR does not authorize.

- **No automatic update, ever**, on any trigger (Decision 3).
- **No automatic restore of data**, on any trigger (Decision 4).
- **No watchdog inside the agent** (Decision 1).
- **No self-restart of the agent through its own API** (Decision 2).
- **No rule about when the platform may be stopped or updated.** ADR-002 and
  `CLAUDE.md`; deferred in
  `PRODUCT_DECISION_PLATFORM_OPERATIONS_CENTER.md` Sections 11.5 and 11.6.
- **No notification when recovery happens.** The console shows it; nothing is
  sent anywhere (`PRODUCT_DECISION_OWNER_CONTROL_CENTER.md` Section 9;
  ADR-046 Decision 7).
- **No change to any module's domain code, schema, events or existing
  endpoints.** The only module-side change authorized anywhere in this
  package is the logging configuration in Decision 5.

## Alternatives Considered

- **A watchdog inside the agent that polls and restarts.** Rejected —
  Decision 1. It moves the unsupervised-process problem rather than solving
  it, and the operating system already solves it, for free, in a way that
  survives the agent.
- **Windows Task Scheduler "at startup" tasks instead of services.** Cheaper
  to set up and rejected: a scheduled task starts a process once and offers
  no restart-on-failure semantics, which is most of the value here.
- **Keeping `gradlew bootRun` and having the agent spawn processes
  directly.** Rejected: it requires the agent to compose command lines, which
  ADR-046 Decision 2 forbids by construction, and a spawned child dies with
  its parent — so the agent could never be restarted without killing the
  platform.
- **Writing down-migrations so code rollback becomes a true rollback.**
  Genuinely the correct long-term answer to Decision 3's gap, and out of
  scope here: it is a project-wide engineering discipline affecting every
  module's migration practice, and adopting it as a side effect of building a
  control panel would be exactly the evasion this package refuses elsewhere.
- **Filesystem or VM snapshots instead of `pg_dump`.** Rejected for now:
  snapshot restore returns the whole machine, including code and
  configuration, which makes "restore data only" impossible and couples data
  recovery to machine recovery. Not rejected on merit for the long term.
- **Backing up to the same disk only.** Not rejected here, because it is not
  this ADR's to decide — deferred with the rest of the retention question
  (Section 11.4). Named because "the default is whatever the developer picks"
  is the outcome to avoid.

## Consequences

### Positive

- **The largest operational gap in Pilot Operation closes**, and it closes
  whether or not anyone ever opens the console: after Decision 1, a module
  that dies at 03:00 is back within seconds, and the platform survives a
  reboot unattended.
- Recovery does not depend on the agent being alive, which is the one
  property an in-application supervisor could never have.
- ADR-014's reversibility requirement gets its first concrete implementation
  in this codebase, and the one place it cannot be met is named in writing
  rather than discovered during a bad release.
- Logs begin to exist, which makes every future diagnosis cheaper — including
  diagnoses that have nothing to do with this console.
- A backup is guaranteed to exist before any update, which is a stronger
  guarantee than the project has today (today there is none at all).

### Negative, disclosed rather than argued away

- **Schema changes remain one-way.** ADR-014 is satisfied for code and not
  for schema, and the fallback for a bad migration is restore-from-backup —
  which is itself gated on an open decision. This is the sharpest unresolved
  edge in the package.
- **Recovery is bound to the Windows Service Control Manager.** A future move
  to Linux or containers rewrites Decision 1 entirely. Accepted because the
  target is one specific laptop; recorded so nobody mistakes it for a
  portable design.
- **"Alive but broken" is not recovered automatically**, and that is the
  failure mode most likely to happen during a real shift (Decision 1).
- **A restart loop is possible.** A module that crashes on startup will be
  restarted repeatedly until the reset period intervenes. The service
  manager's own failure-count reset bounds it; nothing else does.
- **Backups are on the same machine as the data** until Section 11.4 is
  answered. A disk failure loses both.
- **The action journal and the logs are also on that machine.** They survive
  a process restart; they do not survive the disk.
- **Someone must perform the one-time service registration correctly**,
  including environment variables that are read at startup (notably
  `PIOS_PILOT_FRONTEND_ORIGIN`, read via `System.getenv` during context
  construction — `PIOS_PILOT_INFRASTRUCTURE_DECISION.md` fact 1.1). A service
  definition missing it fails as a CORS error in a phone browser, not as a
  clear startup message.
- **`README.md` and `LAUNCH_CHECKLIST.md` stop describing how the production
  machine is actually started** and must be extended, not silently outgrown
  (`CLAUDE.md`, "Never Delete Documentation"). The `bootRun` instructions
  remain correct for local development, the same way
  `PIOS_PILOT_INFRASTRUCTURE_DECISION.md` already distinguishes
  `npm run dev` from the pilot's `build` + `preview`.

## Traceability

| Subject | Source |
| --- | --- |
| Authorizing document; automatic recovery, backup, update as named purposes | `PRODUCT_DECISION_PLATFORM_OPERATIONS_CENTER.md` Sections 6, 9, 11; Product Owner instruction 2026-08-06 |
| Reversibility is mandatory for a release | ADR-014 Decision (line 17) |
| Modules unsupervised today; PostgreSQL and RabbitMQ already services | `README.md` "Running Locally"; `LAUNCH_CHECKLIST.md` lines 11–23; the machine |
| Operating without an observer present | `PIOS_PILOT_OPERATION_PROTOCOL.md` Section 1 |
| Flyway runs forward per module; no down-migrations exist | `application.yml` lines 8–10; absence of any down-migration in `backend/**` |
| A stop does not lose events | ADR-031, ADR-032 (outbox `published_at IS NULL` survives restart) |
| No module depends on another being up | ADR-009; ADR-026; ADR-044 Decision 1 |
| Degraded-but-running states are visible but not automatable | ADR-043 Decision 2; ADR-002; `CLAUDE.md` |
| Diagnosis must not get heavier as things get worse | ADR-043 Decision 2 (the `findUnpublished()` correction) |
| No file logging exists today | Absence of `logging.file`/logback configuration in `backend/**` |
| Additive configuration needs no ADR of its own | `PIOS_PILOT_INFRASTRUCTURE_DECISION.md` Section 0 (the same test) |
| `network-management` database exists though the module does not run | ADR-037; `LAUNCH_CHECKLIST.md` line 12 |
| Environment variables read at startup | `PIOS_PILOT_INFRASTRUCTURE_DECISION.md` fact 1.1 |
| No notifications | `PRODUCT_DECISION_OWNER_CONTROL_CENTER.md` Section 9; ADR-046 Decision 7 |
| Confirmation and journalling required for destructive actions | ADR-047 Decisions 3, 4 |
| Sibling decisions | ADR-046 (component), ADR-047 (authorization), ADR-048 (ingress) |

## Files Changed

This ADR only. No source file is touched by this document.

The one module-side change it authorizes as a prerequisite — rolling file
logging configuration in five `application.yml` files (Decision 5) — is
additive, code-free and contract-free, and is handed to the Developer role
only after the Product Decision and this package are ratified.

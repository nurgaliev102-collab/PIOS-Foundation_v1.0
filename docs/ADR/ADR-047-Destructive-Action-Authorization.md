# ADR-047: Destructive Action Authorization — A Second Credential, a Two-Step Confirmation, and an Action Journal

## Status

**Draft — awaiting Product Owner ratification.**

Authorized in principle by the Product Owner's instruction of 2026-08-06 and
by `docs/PRODUCT_DECISION_PLATFORM_OPERATIONS_CENTER.md` (itself a draft).
That instruction authorized the component's **existence**; it did not decide
how strong the protection around a destructive action must be. That question
is deferred to the Product Owner in that Product Decision's Section 8.1, and
this ADR decides only the parts that are architectural rather than a matter
of the owner's own tolerance. Which is which is stated explicitly in
Decision 6.

**Package.** ADR-046 (what the component is), **047 (this — who may make it
act)**, ADR-048 (how the instruction arrives), ADR-049 (how the platform
returns to a known-good state). Together they supersede ADR-045, which
bundled all four.

**Relationship to ADR-044.** This ADR is built on top of ADR-044 and
**departs from it deliberately in exactly two named places** (Decision 1 and
Decision 4). ADR-044's own text is not edited, and its mechanism for the
observation surface is not changed in any way. Like ADR-044, this ADR is
written to be **deleted** when PIOS acquires real authentication, not to be
built upon.

## Context

### What ADR-044 decided, and on what premise

ADR-044 Decision 6 is explicit:

> One credential, one capability: read the observation surface. No role
> model, no permission check beyond "is this credential correct", no second
> account, no account creation, no password-change endpoint, no account
> listing, **and no audit of who did what — because there is exactly one
> subject and it performs no actions.**

And in its Consequences:

> **There is no audit trail.** Nothing records that the owner logged in, or
> when, or from where. With one subject performing no actions this is
> tolerable; it stops being tolerable the moment there is a second subject.

Both halves of that reasoning rest on the same premise: **the subject
performs no actions.** ADR-046 makes that premise false. The subject now
performs actions, and the actions destroy availability and — in ADR-049's
restore path — data.

ADR-044 also removed a session token from its own first draft, on four
recorded objections (its "Revision 2026-08-02"). Any token-shaped thing
proposed here must be checked against those four objections explicitly, not
waved past them. Decision 3 does that line by line.

### What exists to reuse

- `OwnerCredentialGate` (dispatch copy, lines 56–153): PBKDF2-HMAC-SHA256
  over three `pios.owner.*` configuration values, constant-time comparison
  via `MessageDigest.isEqual`, a fixed delay after a failure
  (`failure-delay-ms`, default 500), a rolling failure window
  (`max-failures-per-window` 20 per `window-ms` 900000) held in memory, and
  **fail-closed when unconfigured** (`isConfigured()`, lines 77–78).
- The `401` produced by `HealthController` (lines 41–43) carries **no
  `WWW-Authenticate` header**, deliberately: otherwise the browser raises its
  own native credential dialog ahead of the product's login screen —
  ADR-044 Decision 3's named trap and a direct violation of
  `PRODUCT_DECISION_OWNER_CONTROL_CENTER.md` Section 11.
- `ownerCredential.ts` (lines 17–49) holds the credential in
  `sessionStorage` under `pios.owner.credential` and sends it per request as
  `Authorization: Basic`.
- **No audit facility of any kind exists** anywhere in the repository.

### The threat this actually has to survive

Two adversaries, and they need different answers:

1. **The owner's own thumb.** A phone in a pocket, a mis-tap, a double-tap on
   a flaky mobile connection, a back-button re-submit. This is the *likelier*
   adversary and the one ADR-046's Product Decision names as a success
   criterion (`PRODUCT_DECISION_PLATFORM_OPERATIONS_CENTER.md` Section 10,
   point 3).
2. **A stolen session.** The credential lives in `sessionStorage`; under a
   successful XSS it is equivalent to a stolen token, which ADR-044 already
   disclosed. Previously that bought an attacker a read-only console.
   Now it could buy a stopped platform.

A confirmation dialog answers the first adversary and does nothing at all
about the second. Both halves are needed, and they are different mechanisms.

## Problem

How is a destructive operation authorized, so that (a) a compromised
observation credential cannot stop the platform, (b) a replayed or
double-submitted request cannot execute twice, (c) a misclick cannot execute
once, and (d) after any incident it is knowable what happened — without
inventing a cryptographic protocol, without creating a user-management
capability, and without editing ADR-044?

## Decision

### 1. The operations surface has its own credential, separate from the owner's. This is a deliberate, named departure from ADR-044 Decision 6.

- The agent verifies `pios.ops.username`, `pios.ops.password-hash`,
  `pios.ops.password-salt` — the same three-value shape, the same `pios.*`
  Spring property convention, and the **same mechanism**: PBKDF2-HMAC-SHA256,
  constant-time comparison, fixed failure delay, in-memory rolling failure
  window, fail-closed when unconfigured. The mechanism is reused verbatim so
  that no second cryptographic idiom enters the project — ADR-044 Decision
  2's own reasoning, applied again.
- The value **must differ** from `pios.owner.*` and must be generated for the
  deployment, never chosen for memorability, never committed.
- The agent's `401` carries **no `WWW-Authenticate` header**, for exactly the
  reason ADR-044 Decision 3 gives.
- The credential is presented **per request**, in `Authorization: Basic`.
  There is no session, no login endpoint, and no server-side session state —
  ADR-044 Decision 3 and Decision 7 are preserved unchanged, and with them
  the property ADR-044 fought hardest for: **revocation is immediate and
  total** by changing one configuration value and restarting one process.

**Why this is a departure and not a contradiction.** ADR-044 Decision 6 says
"one credential, one capability". This ADR does not give one credential two
capabilities; it gives a **second capability its own credential**. The
principle survives; the count does not. The reason the count must change is
concrete rather than stylistic: with a single credential, the XSS exposure
ADR-044 already disclosed for a read-only console would become an exposure
that can stop production. Separating them means the observation console can
be compromised without the platform being stoppable.

**Honest limitation, carried over unchanged from ADR-044 Decision 8:** the
failure window is per-process and resets on restart. Here it is *one*
process rather than five, so the "attacker gets five times the budget"
weakness ADR-044 disclosed does not apply — this is the one place where this
design is stronger than ADR-044's, and it is stronger by accident of shape,
not by effort.

### 2. There is no second factor inside the application. The stronger outer layer is placed where it is free and correct.

Not because a second factor is unnecessary, but because implementing TOTP,
passkeys or SMS inside the agent would be the first hand-rolled — or first
newly-dependency-bearing — authentication protocol in PIOS, living in one
component, protecting one surface. That is precisely the trade ADR-044's
Alternatives rejected when it declined Spring Security for five modules.

The stronger layer belongs at the edge: **an external access gate in front of
the operations hostname** (ADR-048 Decision 3). ADR-044 Decision 10 already
named this as *"the recommended next step"* toward the Defense in Depth that
ADR-011 requires and that ADR-044 openly admits it does not provide.

**Architecture's recommendation, stated separately from the fact that it is
still open:** enable it **before** the first destructive endpoint exists, not
after. The decision is the Product Owner's
(`PRODUCT_DECISION_PLATFORM_OPERATIONS_CENTER.md` §8.1, §8.2), because it
costs an account and a domain.

### 3. Every destructive operation is a two-step, single-use, expiring confirmation. This is an intent mechanism, not an authentication mechanism.

Step one declares intent and receives a **confirmation token**; step two
executes and consumes it. Properties, all binding:

- an unpredictable value from a cryptographically secure random source
  (≥128 bits), opaque, carrying no claims and no structure;
- held **only in the agent's memory** — never written to disk, never in the
  action journal, never logged, gone if the agent restarts;
- bound to exactly one `(action, target)` pair;
- **single-use** — consumed on use;
- **short-lived** — 60 seconds by default, configurable;
- **at most one active at a time**, globally; issuing a new one invalidates
  any previous one;
- accompanied by a `confirmation` field whose value the operator typed, so
  that a replayed request body still fails for want of a fresh token and a
  request assembled by accident cannot satisfy the shape.

Additionally, and independently of the token: **the agent executes at most
one mutating operation at a time, globally** — a second concurrent request is
refused. This makes double-tap behaviour on a bad mobile connection
predictable, and makes "stop and update simultaneously" impossible.

**Checked line by line against ADR-044's own four objections to the token it
deleted:**

| ADR-044's objection to its own token | Applies here? |
| --- | --- |
| *"It created the only genuine inter-module trust relationship"* — module A issued, module B accepted | **No.** One component issues and the same component consumes. No module issues or accepts anything; no module knows this exists |
| *"It made revocation impossible"* — valid for twelve hours | **No.** Sixty seconds, single-use, and it authenticates nothing — the credential is still checked independently on the execute call |
| *"It was the only hand-rolled cryptography in PIOS"* | **No.** It is not cryptography: an opaque random value compared for equality. No signature, no format, no claims, nothing to get subtly wrong |
| *"It required two endpoints that turn out to be unnecessary"* | **The two steps are the point.** The second call is not a second way to authenticate; it is the mechanism that makes a destructive action non-replayable |

The token is an **idempotency-and-intent device**, and ADR-044's reasoning
against *session* tokens is preserved intact by that distinction.

### 4. Every action is journalled, before and after, append-only, and the agent has no way to rewrite it. This is the second deliberate departure from ADR-044.

ADR-044 tolerated the absence of an audit trail on a stated premise that is
now false. Therefore:

- **One append-only file**, one line per event, written by the agent:
  timestamp, action, target, outcome, source address, whether a confirmation
  token was consumed, and origin (`OWNER` for an operator action, `SYSTEM`
  for something the service manager did on its own).
- **Written on the attempt and on the result**, so a refused action and a
  crashed action are as visible as a successful one. A rejected attempt is
  the signal that someone tried something.
- **Never contains the credential and never contains a token value.**
- Exposed **read-only** through the agent's API so the console can render it,
  and included in any diagnostic report.
- **The agent has no endpoint that deletes or edits it.** Rotation is an
  operator action on the filesystem, deliberately outside the API.

This closes ADR-044's disclosed audit debt **for this surface only**. It does
not close it for the observation console, and it does not discharge ADR-012's
outstanding cross-module tracing requirement, which ADR-043 declined to
discharge and which this ADR declines equally.

### 5. What the credential does not grant.

Least Privilege (ADR-011) applied at the moment of creation, not asserted
afterwards:

- **No domain access.** The operations credential opens no domain endpoint.
  It cannot read `/v1/orders`, cannot create anything, cannot change any
  driver, order, proposal or assignment. ADR-046 Decision 3 makes this
  structural: the agent has no client for those APIs at all.
- **No database access.** No SQL, no row, no query.
- **No arbitrary file access** (ADR-046 Decision 5).
- **No role, no second account, no account management, no password-change
  endpoint, no self-service anything** — `PRODUCT_DECISION_OWNER_CONTROL_CENTER.md`
  Section 4's "one person, no roles" constraint is honoured here too, and
  `PRODUCT_DECISION_PLATFORM_OPERATIONS_CENTER.md` Section 5 restates it for
  this tool. A second operator is not a configuration change; it is the point
  at which a real user model has become necessary, and it needs its own
  decision.
- **No ability to disable the journal, the confirmation requirement, or its
  own credential check** through the API.

### 6. What this ADR decides, and what it deliberately leaves to the Product Owner.

Stated as a table because conflating the two would be the failure mode.

**Decided here (architectural — a wrong answer is an architectural defect):**

| Decision | Why it is not the Product Owner's to choose |
| --- | --- |
| A separate credential for operations | Otherwise "the observation panel never controls" stops being true in effect, whatever the documents say |
| Same PBKDF2 mechanism, no new crypto | Introducing a second cryptographic idiom is a project-wide cost with no product-visible benefit |
| No `WWW-Authenticate` on 401 | A browser-native dialog would speak in the browser's words — already forbidden |
| Per-request credential, no session, no issuance | Preserves immediate revocation; avoids re-creating what ADR-044 deleted |
| Two-step, single-use, expiring confirmation | The only protection against replay and double-submit that does not depend on the browser |
| Single-flight execution | Concurrency safety, not preference |
| Append-only journal with no delete path | An audit trail the actor can erase is not an audit trail |
| No domain, database or file access from this credential | ADR-011 Least Privilege; ADR-046 Decision 3 |

**Left open (the owner's tolerance, not architecture) —
`PRODUCT_DECISION_PLATFORM_OPERATIONS_CENTER.md` §8.1:**

| Open question | Architecture's recommendation, stated as a recommendation |
| --- | --- |
| External access gate (e.g. Cloudflare Access) in front of the operations hostname | Yes, and before the first destructive endpoint exists |
| Must the operator type a confirmation word, or is a tap on a dialog enough | Type the word. A tap can happen in a pocket; typing cannot |
| Idle timeout on the operations credential, and re-entry before each destructive action | 15-minute idle timeout; re-entry before destructive actions |
| A "protection mode" toggle that must be lifted before anything can be stopped | Recommended, default on — but it makes an urgent action two steps longer during a real outage, which is a real cost |
| Confirmation token lifetime (60 s default) | 60 s |

None of the open items may be implemented as a default chosen by a
developer. Until answered, the conservative behaviour applies: the stricter
option, or the feature does not exist.

## Alternatives Considered

- **One credential for both observation and operations.** Rejected —
  Decision 1. It would mean a compromised read-only console can stop
  production, and it would make
  `PRODUCT_DECISION_OWNER_CONTROL_CENTER.md` Section 11 false in effect while
  remaining true on paper.
- **A signed session token (JWT/HMAC) for the operations surface.** Rejected
  for the four reasons ADR-044 recorded when it deleted its own: it would
  reintroduce hand-rolled cryptography, delay revocation, and add a format
  that must be right. The confirmation token in Decision 3 is deliberately
  *not* this: it authenticates nothing.
- **Spring Security on the agent.** Rejected on ADR-044's own ground: a
  framework-wide filter chain and a new dependency tree to protect one
  component's endpoints, on a platform that has never had one. The
  hand-written check is smaller than the configuration Spring Security would
  need and its entire behaviour fits in one file — and here it is *one* copy,
  not five.
- **A credential table in a new database.** Rejected: the agent has no
  database (ADR-046 Decision 1), and ADR-044 Decision 2's aggregate test
  (ADR-035 Part 2) returns no on every criterion here exactly as it did
  there.
- **Confirmation by UI dialog only.** Rejected: it protects against a thumb
  and not at all against a replay or a direct API call.
- **TOTP or passkeys implemented in the agent.** Rejected — Decision 2. Not
  on merit, but on where the mechanism belongs.
- **Journalling into a database or an external log service.** Rejected: the
  agent has no database by construction, and an external service is a new
  runtime dependency for the tool that must work when things are broken.

## Consequences

### Positive

- A compromised observation credential cannot stop, update or alter the
  platform. The two surfaces fail independently.
- Revocation stays immediate and total — one configuration value, one
  restart — because nothing is ever issued that outlives a request.
- Replay, double-submit and blind `POST` from a stolen session all fail
  without a fresh, single-use, matching token.
- PIOS acquires its first audit trail, at the moment it acquires its first
  action worth auditing, rather than after an incident.
- No new cryptography, no new dependency, no new configuration idiom: three
  `pios.*` values and the same PBKDF2 call already in the codebase.
- The mechanism remains as deletable as ADR-044's: nothing persisted, no wire
  format any future system must stay compatible with, and a future mechanism
  that also uses `Authorization` replaces it without the console's
  request-building code changing shape.

### Negative, disclosed rather than argued away

- **A second credential is a second thing to generate, store, rotate and
  lose.** Rotating it is one configuration change plus one restart — cheaper
  than ADR-044's five-place rotation, but it is still operational debt and is
  recorded as such.
- **The credential is resident in `sessionStorage`** for the life of the tab,
  with the same XSS equivalence ADR-044 disclosed. The recommended idle
  timeout and re-entry (Decision 6) reduce the window and do not close it.
- **Brute-force resistance is in-memory and resets on restart** (inherited
  from ADR-044 Decision 8).
- **Defense in Depth is met only if the open item in Decision 2 is
  answered yes.** As decided here alone, this is still a single application
  boundary, and ADR-011's third principle remains partially unmet — knowingly,
  and stated rather than implied.
- **The journal is a file on the same laptop that the actions affect.** It
  survives the agent restarting; it does not survive the disk. Off-machine
  retention is not decided here and is adjacent to
  `PRODUCT_DECISION_PLATFORM_OPERATIONS_CENTER.md` §8.4.
- **The confirmation token dies with the agent.** If the agent restarts
  between the two steps, the operator must start over. This is correct and
  is disclosed so it is not filed later as a bug.
- **TLS is a precondition, not an enhancement.** A credential in an
  `Authorization` header over plain HTTP is protected by nothing (ADR-044
  Decision 9). If this surface is ever served over `http://`, everything in
  this ADR is worth zero.

## Traceability

| Subject | Source |
| --- | --- |
| Authorizing document | `PRODUCT_DECISION_PLATFORM_OPERATIONS_CENTER.md`; Product Owner instruction 2026-08-06 |
| The premise that has become false | ADR-044 Decision 6; ADR-044 Consequences ("There is no audit trail") |
| The four objections any token must survive | ADR-044 "Revision 2026-08-02" |
| Mechanism reused verbatim | `OwnerCredentialGate.kt` (dispatch copy) lines 56–153 |
| No `WWW-Authenticate` on 401, and why | ADR-044 Decision 3; `HealthController.kt` lines 41–43; `PRODUCT_DECISION_OWNER_CONTROL_CENTER.md` Section 11 |
| Credential storage in the browser and its XSS exposure | `ownerCredential.ts` lines 17–49; ADR-044 Consequences |
| Immediate revocation preserved | ADR-044 Decision 7 |
| Brute-force limits and their honest bounds | ADR-044 Decision 8 |
| Recommended outer access layer | ADR-044 Decision 10; ADR-011 lines 19–23; ADR-048 Decision 3 |
| Aggregate test applied to the credential | ADR-035 Part 2, as applied in ADR-044 Decision 2 |
| One user, no roles | `PRODUCT_DECISION_OWNER_CONTROL_CENTER.md` Section 4; `PRODUCT_DECISION_PLATFORM_OPERATIONS_CENTER.md` Section 5 |
| Open questions belong to the Product Owner | `PRODUCT_DECISION_PLATFORM_OPERATIONS_CENTER.md` Section 8.1; `PIOS_SERVER_CONTROL_CENTER_DESIGN.md` §16.5, §16.9, §16.10 |
| Tracing debt not discharged | ADR-012; ADR-043 Consequences |
| Sibling decisions | ADR-046 (component), ADR-048 (ingress), ADR-049 (recovery and change safety) |

## Files Changed

This ADR only. No source file is touched by this document.
`API_SPECIFICATION.md` and `INTERFACE_CONTRACTS.md` must record the
confirmation endpoint and every gated operations endpoint **before**
implementation (ADR-006).

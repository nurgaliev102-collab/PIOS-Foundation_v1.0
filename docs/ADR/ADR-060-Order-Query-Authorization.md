# ADR-060: Order Query Authorization — Who May Read an Order, and Where That Is Enforced

## Status

**Accepted — Product Owner ruling, 2026-08-15.** The two questions this ADR
raised as the Product Owner's rather than the architect's were both put to them
and both answered; the rulings are recorded in Decisions 6 and 7, which are now
settled and carry no open marker. Nothing in this ADR is pending.

Both rulings accepted the recommendation as written: the Coordinator screen's
breakage is accepted (Decision 6), and the driver-side retention gap is accepted
as a disclosed limitation with its closure explicitly deferred (Decision 7). The
rest of this ADR is ratified architecture applied to a P0 vulnerability and
required no product ruling.

This ADR consumes the gate ADR-055 left open in its own "Explicitly out of scope"
(line 116): *"No Dispatch, Order Management, or Driver Management endpoint is
locked down by this ADR — those remain open and are the **named next step**, not
something this ADR quietly covers."* This is that next step, taken for Order
Management only.

**Evidence-gate note, recorded rather than glossed.** `PIOS_PRODUCT_EVIDENCE.md`
holds no `E-NNN` entry motivating this work, and no `docs/PRODUCT_DECISION_*.md`
requires it. It proceeds on Product Owner authority as remediation of a
confirmed vulnerability — the same basis and the same disclosure ADR-055 line 20
recorded for itself. It is not a product-scope item and must never later be read
as evidence-backed. No evidence-analyst pass is required for a vulnerability
remediation that removes disclosure and adds no capability; one *would* be
required before any of the "Deliberately not done here" items below.

## Context

### The vulnerability

`backend/order-management/src/main/kotlin/com/pios/ordermanagement/api/OrderQueryController.kt`
lines 34–50: `listOrders()` takes no parameters and no `Authorization` header. Its
own KDoc, lines 24–26, states the behaviour plainly — *"Returns every order,
unfiltered and unordered — Sprint FR-003 explicitly excludes filtering, search,
and pagination."* Every caller, authenticated or not, receives every order ever
placed, each carrying `origin`, `destination`, `passengerName`, `createdAt`,
`pickupAddress`, `requestedPickupAt`.

This was correct for the sprint that wrote it and is not correct for a pilot with
real people's data.

### Order enumeration is not hard today, and this is what decides the ruling below

The originating audit proposed relying on order identifiers being unguessable
random UUIDs. **They are not, in practice, unguessable — they are enumerable
without any credential at all**, through two endpoints that were verified in this
repository for this ADR:

- `backend/driver-management/.../api/DriverController.kt` lines 96–101:
  `listDrivers()` — no parameters, no `Authorization` header. Returns every
  driver id.
- `backend/dispatch/.../api/ProposalController.kt` lines 175–190: `listProposals`
  takes `orderId`/`driverId` and **no `Authorization` header**. `?driverId=X`
  returns every proposal for any X the caller names, each carrying its
  `orderId`.

So `GET /v1/drivers` → `GET /v1/proposals?driverId=` yields the complete set of
live order identifiers to an anonymous caller. ADR-059 Blocker 3 already recorded
the same fact from the other direction (*"`GET /v1/orders`, `GET /v1/proposals?orderId=`
and `GET /v1/proposals?driverId=` are **unauthenticated**"*).

Registration is open (`POST /v1/identities/register`, Auth: none — ADR-055
Decision 3 table), so "hold a token" and "hold a driver-linked token" are both
self-service. A rule of the form *authenticated callers may fetch any order by
id* is therefore not a mitigation at all today: it is the same full-table dump
behind two extra HTTP calls.

### Order Management cannot answer "is this driver entitled to this order?" itself

- `Order` (`domain/Order.kt` lines 92–101) has no driver field, and none of its
  KDoc's seven documented additions ever contemplated one.
- `application/OrderAssignmentRecognitionHandler.kt` line 19 is explicit that
  this is deliberate: the module *"does not transition the Order aggregate, which
  has no corresponding state to transition to, and it **retains neither
  reference as its own information**."* The `driverReference` that Dispatch's
  `AssignmentAccepted` already delivers into this module
  (`application/AssignmentAcceptedUpdateCommand.kt` lines 14–18) is validated and
  discarded on purpose.
- Even if that were reversed, it would not solve the problem: `AssignmentAccepted`
  fires on **acceptance**, and the driver needs the order's address and passenger
  name *before* accepting, while the proposal is still open
  (`DriverHome.tsx` KDoc lines 293–305 — destination and `pickupAddress` are
  rendered on the proposal card).

The driver↔order relationship lives only in Dispatch's `Proposal`/`Assignment`.

### The passenger side is answerable, on a convention this ADR now makes binding

`OrderOrigin`'s own KDoc (lines 19–22) hedges: a value equal to Passenger
Experience's `PassengerReference.passengerId` *"may be used to construct one, but
the two remain distinct types."* The one real writer does exactly that —
`RideRequest.tsx` line 497 `const passengerId = identity.identityId`, line 559
`passengerReference: passengerId` — and `OrderSubmissionRequestHandler` passes it
into `OrderOrigin` with only a non-blank check.

ADR-055 Decision 5 already ratified the general rule this depends on: *"a
`passengerReference` written by any client MUST equal the authenticated
`identityId`."* Decision 2 below extends that rule to `Order.origin`, which is
where it was being relied on in fact but never stated.

### There are four callers of this endpoint, not two

The originating audit named two. Verified: there are four, and two of them have
no session token and never will.

| Caller | Line | Principal | Needs |
| --- | --- | --- | --- |
| `frontend/src/pages/RideRequest/RideRequest.tsx` | 457 | passenger | one own order's `requestedPickupAt` |
| `frontend/src/pages/DriverHome/DriverHome.tsx` | 425 | driver | detail for orders it already has proposals for |
| `frontend/src/pages/Coordinator/Coordinator.tsx` | 172 | none — no credential of any kind | the whole list |
| `frontend/src/pages/OwnerControlCenter/todayData.ts` | 103 | owner (holds a Basic credential already, does not send it here) | the whole list |

`Coordinator.tsx`'s own KDoc lines 128–131 says so: *"no authentication — this
page is reachable by anyone who navigates to it."* `PILOT_OPERATION_PLAN.md`
0.1.3 records the same. Any fix that ignores these two callers breaks the
operator's only two observation screens, and `todayData.ts` line 103's
`.catch(() => [])` means the Owner Control Center would break **silently**,
showing zeroed counters rather than an error — the worst possible failure for a
monitoring surface.

### The mechanism to use already exists and is ratified

- ADR-055 Decision 1: a stateless HMAC-SHA256 Bearer token, verified **locally**
  by a `SessionTokenVerifier` **replicated per module**, never centralized,
  because `MODULE_STRUCTURE.md` Section 4 forbids shared business code between
  modules. `identity` is the sole issuer; every other module is a verifier only.
- `MODULE_STRUCTURE.md` Section 4 line 95: *"Every module enforces its own
  authentication and authorization at its own boundary, consistent with ADR-011;
  no module is exempt, and **no module enforces this on another's behalf**."*
- The enforcement shape to copy is `passenger-experience`'s `ConnectionController.kt`
  lines 119–155: verify first, 401 on any verification failure, then compare the
  named parameter to `sub` (passenger) or `drv` (driver), 403 on mismatch.
- Order Management already replicates security components this way:
  `api/OwnerCredentialGate.kt` is its own copy, used by `HealthController` line 41.

## Decision

### 1. `GET /v1/orders` gains three mutually exclusive, principal-scoped modes. There is no unauthenticated mode.

Mode selection is by query parameter, following `ProposalController.listProposals`'s
own already-established "exactly one of two, else 400" convention (lines 162–190).

| Mode | Parameter | Principal | Returns |
| --- | --- | --- | --- |
| Own orders | `?passengerReference=<identityId>` | Bearer, `param == token.sub` | that passenger's orders |
| Named orders | `?ids=<uuid>,<uuid>,…` | Bearer, `token.drv != null` | the subset of those ids that exist |
| Full list | *(no parameter)* | `Authorization: Basic`, `OwnerCredentialGate` | every order |

`Authorization` scheme is dispatched by prefix — `Basic` to `OwnerCredentialGate`,
`Bearer` to `SessionTokenVerifier` — exactly as ADR-055 Decision 1 line 48 already
specifies for the two gates coexisting on one header.

### 2. `Order.origin` is, from this ADR forward, the authenticated passenger's `identityId`.

`OrderOrigin`'s KDoc hedge ("may be used") becomes a stated requirement, extending
ADR-055 Decision 5 from `passengerReference` to `Order.origin`. Without this, the
`origin.reference == token.sub` comparison in Mode 1 is an implementation
coincidence rather than a rule.

**No type change, no validation change, no migration.** `OrderOrigin` stays a
`String`-carrying value class, and `POST /v1/orders` keeps accepting whatever
non-blank reference it is given — Order Management still does not own identities
and still cannot verify one (the reference-not-ownership rule, ADR-005/ADR-009/
ADR-019/ADR-037). What changes is only that an origin which is not the caller's
`identityId` now makes its own order unreadable by its own author. That is a
consequence, disclosed here, not a validation.

**Disclosed consequence:** any order row written before ADR-055 whose `origin` is
a client-generated UUID becomes unreadable by its author, exactly as ADR-055
Decision 5 already disclosed for `connections` rows. No migration can fix this;
nothing ever proved who those values belonged to.

### 3. The driver's entitlement is **not** verified per order id, and Order Management does **not** call Dispatch.

Mode 2 authenticates the caller and requires a driver-linked token; it does not
ask Dispatch whether this driver actually holds a proposal for each requested id.

**Why not.** ADR-055 Decision 1 rejected a per-request cross-module authorization
call on its merits, with a real precedent available, because *"it would put
`identity`'s uptime in front of every authenticated read in every module — exactly
the temporal coupling ADR-003 rejected and ADR-027 line 22 declines to
reintroduce."* An Order Management → Dispatch call per order id, on a screen that
polls, is that same shape: it would place Dispatch's uptime in front of Order
Management's reads and close a dependency cycle between two bounded contexts that
today have exactly one edge, asynchronous and one-directional (Dispatch →
`AssignmentAccepted` → Order Management, `MODULE_STRUCTURE.md` Section 5).

**What makes Mode 2 sound is Decision 4, not the shape of a UUID.** The capability
argument for opaque identifiers is worthless while identifiers are enumerable
anonymously (Context above). It becomes real only once they are not.

**What Mode 2 does not achieve, stated plainly.** The Product Owner's requirement
(3) was *"a driver gets only orders they're entitled to see."* Mode 2 delivers *a
driver gets only orders whose identifier it holds* — a superset meaning "was ever
entitled." Concretely: a driver who was proposed an order and **declined** it, or
whose proposal **lapsed**, keeps the identifier and can keep reading that order's
passenger name and pickup address indefinitely. This is a retention gap in data
that driver already saw legitimately, not a disclosure to a stranger. It is the
one requirement this ADR does not fully meet.

**This gap was put to the Product Owner as an explicit choice and knowingly
accepted (ruling 2026-08-15).** It is a disclosed limitation this fix carries,
not an oversight and not a defect to be quietly patched later by an
implementation task. Decision 7 records both what closing it would cost and the
prerequisite that must exist first.

### 4. Dispatch's `GET /v1/proposals?driverId=` is locked to the token's own driver. This is not optional; it is what makes Decision 3 defensible.

`ProposalController.listProposals` (lines 175–190) gains a `Bearer` requirement on
the `?driverId=` branch only: 401 without a valid token, 403 when
`driverId != token.drv`. This is `ConnectionController.getConnectionsForDriver`
(lines 119–136) copied exactly, and it is the "Dispatch becomes a token verifier"
step ADR-059 S2 already ratified as the required shape (*"the replicated
`SessionTokenVerifier` shape ADR-055 Decision 1 already ratified"*).

**The owner keeps its access to this endpoint, by the same dual-scheme rule as
Mode 3.** `todayData.ts` line 108 fans out `GET /v1/proposals?driverId=` across
every driver from the owner's browser, and the owner holds no Bearer token and
never will — so a `drv`-only rule would silently strip the proposal-derived
entries out of the Owner Control Center's event feed. `?driverId=` therefore
accepts **either** a Bearer whose `drv` equals the named driver, **or** a valid
`Authorization: Basic` owner credential naming any driver. Dispatch already
carries its own `api/OwnerCredentialGate.kt` copy, so this needs no new
component.

This is the architect's call, of the same class as Decision 5 and not a Product
Owner question: it grants the owner nothing it does not already read today
unauthenticated, adds no principal, no role model and no capability, and keeps
the surface read-only. It extends the ADR-044 Decision 5 amendment from
`GET /v1/orders` to `GET /v1/proposals?driverId=` on the identical reasoning.

`?orderId=` is **left open** by this ADR. Locking it requires a
`passengerReference` on `Proposal` — ADR-059 S3 names this as a breaking contract
change that *"must be named in whatever ADR authorizes it, never slipped in as an
implementation detail."* It is not authorized here. It is safe to leave open only
because it takes an order identifier the caller must already possess; it is an
enumeration *sink*, not a *source*.

With Decision 1 and this decision in force, no unauthenticated endpoint in PIOS
returns an order identifier. The developer must verify that claim exhaustively
before implementation is called complete (see "What implementation must verify").

> **Amendment pointer (2026-09-18, per `ADR-015`).** D-08 (Handoff Observation
> Foundation, `docs/PIOS_D08_HANDOFF_OBSERVATION_IMPLEMENTATION_SPEC.md` §9)
> extends this Decision's own pattern — a Dispatch-owned business query endpoint
> gated by Dispatch's own already-existing `api/OwnerCredentialGate.kt`, the
> identical component this Decision already reuses, needing no new component —
> to one further endpoint: `GET /v1/handoffs/observation?driverId=`. Unlike this
> Decision's own `?driverId=` rule for `GET /v1/proposals` (which accepts
> **either** a matching `Bearer` token **or** the owner's `Basic` credential),
> the new endpoint accepts **`Authorization: Basic` only** — no `Bearer` branch
> exists for it at all, including for the named driver's own token. It is
> narrower than this Decision, not an extension of its dual-scheme shape.
>
> **What this is not.** Unlike this Decision's and Decision 5's own "the owner
> gains nothing new" framing, this is new read capability: an aggregate,
> read-only view of a committing driver's own Handoff usage (proposal/
> acceptance/consent/refusal/withdrawal counts and a computed rate over a
> trailing window) that no endpoint exposed to the owner before. It introduces
> no new authentication mechanism, no new principal, and no role model —
> `ADR-044` Decision 6 ("no roles, no permissions, no user management") remains
> preserved, the owner still holds exactly one credential with exactly one
> shape of capability, read-only. It returns no score, rank, tier, or
> cross-driver comparison of any kind (`docs/PIOS_D08_HANDOFF_OBSERVATION_IMPLEMENTATION_SPEC.md`
> §5's own boundary). Nothing else in this ADR — Decisions 1–3, 5–7, either
> access matrix, or any other endpoint — is touched.

### 5. Owner access is preserved by extending `OwnerCredentialGate` to this one endpoint. This amends ADR-044 Decision 5.

ADR-044 Decision 5 lists *"`GET/POST /v1/orders` … — not gated, not changed, not
touched."* Mode 3 amends that list for `GET /v1/orders` only. The reason it was
written that way is stated in the same decision (line 329): the existing endpoints
*"are called by unauthenticated driver and passenger browsers, and authenticating
them means participant authentication (Decision 11)."* ADR-055 performed that
participant authentication; the premise of the exclusion has expired. `POST
/v1/orders` and everything else on ADR-044's list is untouched.

The owner gains nothing new: read-only observation of the same rows it already
receives, now with the credential it already holds
(`OwnerControlCenter/ownerCredential.ts` line 61 `toBasicAuthorizationHeader`,
already used by `healthPoll.ts` line 64).

**`todayData.ts` must be changed, and this is not the same case as the Coordinator
screen.** The two are easy to bundle together as "operator screens" and must not
be: the Coordinator has no credential and is accepted as broken (Decision 6),
while the Owner Control Center **holds a valid credential and simply does not
send it on this one call**. `todayData.ts` line 103 fetches `/v1/orders` with no
`Authorization` header, and `loadTodaySnapshot()` takes no arguments
(`todayData.ts` line 100) even though `OwnerControlCenter.tsx` line 44 holds the
credential in state and calls it at line 60 without passing it. The credential
must be threaded through and sent, exactly as `healthPoll.ts` line 64 already
does.

Leaving `todayData.ts` unmodified is **not** an available option under this
Decision: line 103's `.catch(() => [])` would swallow the new 401 and render
zeroed counters and a truncated event feed, with no error shown — a monitoring
surface silently reporting "nothing happened today" during a live pilot. That
failure mode is the reason Mode 3 exists at all; shipping Mode 3 with no caller
would deliver the cost of the change and none of its benefit.

ADR-043 Decision 1 (browser-side
aggregation, no aggregating backend), Decision 3 (the client fans out), and
Decision 5 (permanently read-only) are all preserved. ADR-044 Decision 6 ("no
roles, no permissions, no user management") is preserved: there is still exactly
one owner credential with exactly one capability.

### 6. The Coordinator screen loses its order list. Accepted.

**Product Owner ruling, 2026-08-15: accepted as recommended.** The breakage is a
known, deliberate consequence of this fix, not a regression to be filed. No
owner-login work is added to this screen now; doing so remains a separate future
task if it is ever wanted.

`Coordinator.tsx` line 172 holds no credential and has no login screen. After
Decision 1 it renders its own "Could not load orders." state, and its Propose
action — which requires selecting an order row — becomes unusable. `Coordinator.tsx`
is therefore **left unmodified by this fix**: there is no code change that would
help it, and pretending otherwise would only hide the consequence.

The basis for accepting this: the pilot's real path does not go through the
coordinator. `PILOT_OPERATION_PLAN.md` 0.3.4 records that submission proposes the
ride directly to the inviting driver and *«Координатор в этом пути не
участвует»*; 0.4.9 records the screen can do almost nothing else.

The alternative that was weighed and not taken — giving `/coordinator` the owner
login screen — was plausible because the coordinator and the owner are the same
human in this pilot (Артур, per `PILOT_OPERATION_PLAN.md` Section "Что происходит,
когда Артуру всё-таки нужно посмотреть"). It was declined as frontend work
outside a P0 security fix. Inventing a *separate* coordinator credential remains
forbidden without a new decision, by ADR-044 Decision 6.

**Consequence for the pilot, so it is not discovered in use:** after this fix,
Артур has no screen showing the full order list except the Owner Control Center,
which shows counters and an event feed rather than the list itself, and no screen
at all from which to propose a ride manually. `PILOT_OPERATION_PLAN.md` should be
corrected to reflect this — see "Follow-up this ADR creates".

### 7. Closing the retention gap in Decision 3 is deferred, and this is the only sound way to do it when it happens.

**Product Owner ruling, 2026-08-15: deferred, not built now.** The gap stands as
the disclosed limitation Decision 3 states. What follows is recorded so that a
future task has a specified target and cannot be improvised (ADR-059's own
convention for an option that is named but not taken):

- Order Management verifies each requested id against Dispatch at read time —
  `GET /v1/proposals?orderId={id}`, checking that some returned proposal or
  assignment names `token.drv`, and that its status still entitles the driver.
- Cost: the first Order Management → Dispatch synchronous call; a bidirectional
  dependency between two bounded contexts; Dispatch's uptime in front of Order
  Management's reads; fan-out multiplied by `DriverHome`'s poll interval; and a
  precedent that a module calls another to authorize a read, which will be reused
  everywhere once set.
- Requires deciding what "still entitled" means — whether a declined or completed
  ride ends the driver's access, and when. **That is a data-retention rule, not
  an architectural one**, and neither `CLAUDE.md` ("Never Invent Business Rules")
  nor ADR-002 permits this ADR to invent it. It needs the same personal-data
  Product Decision ADR-059 Blocker 1 already records as outstanding.

**The prerequisite is therefore a hard gate, not a preference.** No implementation
task may close this gap until a data-retention Product Decision exists —
recommended filename `docs/PRODUCT_DECISION_PARTICIPANT_DATA_RETENTION.md`,
answering at minimum: how long a driver retains access to an order's passenger
data, whether a declined or lapsed proposal ends that access immediately, and
whether a completed ride does. Building the verification call without that
decision would mean inventing the rule in code, which is exactly what the
deferral exists to prevent. This ADR is not amended or rewritten when that
happens; the closure gets its own ADR citing this Decision 7 (`CLAUDE.md`: "Never
Delete Documentation").

## Access matrix — `GET /v1/orders`

`Bearer*` = a token that passes `SessionTokenVerifier.verify`. A token that fails
for any reason — absent, wrong scheme, malformed, bad signature, expired — is one
indistinguishable 401, per `SessionTokenVerifier`'s own KDoc lines 37–46.

| Caller | No parameter | `?passengerReference=X` | `?ids=a,b,c` |
| --- | --- | --- | --- |
| No `Authorization` header | **401** | **401** | **401** |
| Malformed / expired / bad-signature Bearer | **401** | **401** | **401** |
| Bad `Basic` credential | **401** | **401** | **401** |
| Bearer*, passenger (`drv == null`) | **403** | `X == sub` → **200**, own rows only · else **403** | **403** |
| Bearer*, driver (`drv != null`) | **403** | `X == sub` → **200**, own rows only · else **403** | **200**, matching rows only |
| `Basic`, valid owner credential | **200**, every row | **400** | **400** |

Additional shape rules, all 400 and all checked *after* authentication so that an
unauthenticated caller learns nothing about the contract:

- `passengerReference` and `ids` both present → 400.
- `ids` empty, blank, or containing a blank element → 400.
- `ids` containing more than **100** elements → 400 (a shape guard, not a
  business rule; no pagination is introduced).
- An id in `ids` that matches no order is simply absent from the response. Never
  404 — a list endpoint returns what matches, and 404 would confirm to a caller
  which identifiers exist, the same reasoning ADR-055 Decision 4 applied to
  `connectionId`.

## Access matrix — `GET /v1/proposals` (Decision 4)

| Caller | `?driverId=X` | `?orderId=Y` | neither / both |
| --- | --- | --- | --- |
| No `Authorization` header | **401** | **200** (unchanged, open) | **400** (unchanged) |
| Malformed / expired / bad-signature Bearer | **401** | **200** (unchanged) | **400** |
| Bearer*, `X == drv` | **200** | **200** | **400** |
| Bearer*, `X != drv` (including `drv == null`) | **403** | **200** | **400** |
| `Basic`, valid owner credential | **200**, any `X` | **200** | **400** |
| `Basic`, bad credential | **401** | **200** | **400** |

Only the `?driverId=` column changes. `?orderId=` and the existing
neither/both → 400 rule (`ProposalController.kt` lines 181–187) are untouched,
and every proposal action endpoint (`accept`, `decline`, `lapse`,
`GET /v1/proposals/{id}`) stays exactly as unauthenticated as it is today.

A driver holds one token carrying both `sub` and `drv`, so the driver row's
`?passengerReference=` cell is not a special case: a driver reading *their own*
orders as a passenger is the ordinary Mode 1.

## Answers to the four implementation questions

**1. Query contract.** Decision 1 plus the matrix above. Parameter names
`passengerReference` (matching `ConnectionController`'s own parameter name, not a
new synonym) and `ids`. Both optional individually, mutually exclusive, with
"neither" meaning the owner mode. Check order per request: resolve scheme → gate →
401; then parameter shape → 400; then subject comparison → 403; then query.

**2. `SessionTokenVerifier`.** Copy verbatim into
`backend/order-management/src/main/kotlin/com/pios/ordermanagement/api/SessionTokenVerifier.kt`,
package line the only difference, becoming the third copy alongside `identity`'s
and `passenger-experience`'s. Decision 4 requires a **fourth** copy at
`backend/dispatch/src/main/kotlin/com/pios/dispatch/api/SessionTokenVerifier.kt`
on the same terms, plus the same commented `pios.session.secret` block in
`backend/dispatch/src/main/resources/application.yml`. Dispatch's own
`api/OwnerCredentialGate.kt` already exists and is reused unchanged for Decision
4's owner branch. This is ADR-055 Decision 1's ratified replication
and `MODULE_STRUCTURE.md` Section 4's own rule; this module already replicates
`OwnerCredentialGate.kt` the same way. Do not extract a shared module, do not add
Spring Security, do not introduce a filter or interceptor — every existing gate in
this codebase is a `@Component` checked in the controller method, and consistency
is worth more here than elegance.

**3. `OrderResponse`.** **No change — row-level filtering only.** All eight fields
stay, in order. Justification, so this is not merely asserted:

- Each mode already returns only rows the caller is entitled to, which makes
  field-level redaction redundant.
- The driver already legitimately receives `passengerName` and `pickupAddress`
  (`DriverHome.tsx` KDoc lines 293–305, Sprint 3B and Sprint H5), and already
  receives passenger `identityId` values from an authenticated endpoint —
  `ConnectionController.kt` line 131 returns `passengerReference` to the driver.
  `origin` therefore discloses nothing to a driver that a ratified endpoint does
  not already disclose.
- ADR-059 S1/S6 remain in force and are untouched: no phone number appears in
  `OrderResponse`, and none may be added.
- Changing the shape would break all four callers' parsers for no security gain.

**4. Configuration.** Add to
`backend/order-management/src/main/resources/application.yml`, commented out,
copying `passenger-experience`'s own block (lines 54–64) including its rationale
comment. `pios.session.secret` must be byte-identical to `identity`'s — that is
correct and expected, and its cost is already a disclosed Consequence of ADR-055
(line 123: *"The HMAC secret is shared between modules. Leaking it in one module
forges tokens for all"*). Unset ⇒ every Bearer verification returns `null` ⇒ 401
— fail-closed, matching `pios.owner.*`'s existing posture in the same file.
`pios.session.ttl-seconds` is **issuer-only** and must not be added: a verifier
reads `exp` from the payload and never computes one. `pios.owner.*` already
exists in this module for `HealthController` and needs no change.

**Deployment ordering is a correctness requirement, not an ops detail.** The
secret must be present in Order Management's runtime configuration *before* the
gated build starts, or fail-closed means 401 for every participant. Backend and
frontend must ship together — the passenger's `requestedPickupAt` fetch
(`RideRequest.tsx` line 457) and the driver's `loadOrderDetails`
(`DriverHome.tsx` line 425) both currently send no header, and both hold
`identity.token` at the call site already.

## What implementation must verify

- **`DriverHome.loadOrderDetails` must be re-sequenced.** It currently runs
  independently of `loadProposals` on the same poll tick (lines 401–422) and has
  no order ids at call time. Mode 2 requires it to run *after* proposals resolve
  and pass those ids. Its existing best-effort failure tolerance (lines 432–435)
  should be preserved unchanged.
- **`DriverHome.loadProposals` must now send the driver's Bearer token** —
  Decision 4 gates the `GET /v1/proposals?driverId=` it calls. This is the
  driver's *own* proposal list, the core of their screen, so unlike
  `loadOrderDetails` its failure is not cosmetic. The token is already at the
  call site (`loadConnections`, lines 439–443, sends it to a different module on
  the same tick). ADR-055's own addendum records the trap to avoid here: a
  first-session driver holds a `drv: null` token until `attachDriver` re-mints
  it, and every `driverId`-scoped call made before that returns 403 silently.
  Test the register → create profile → go online → see proposals sequence in one
  session, not just a returning driver.
- **No unauthenticated endpoint may return an order identifier.** Decision 4
  closes `GET /v1/proposals?driverId=`; the developer must sweep the remaining
  surface — including `platform-ops`, which this ADR did not audit — and report
  the result rather than assume it.
- **IDOR/BOLA negative tests**, at minimum: no header → 401 on all three modes;
  expired and tampered-signature tokens → 401; passenger A requesting passenger
  B's `passengerReference` → 403; passenger token (`drv == null`) using `?ids=`
  → 403; any Bearer with no parameter → 403; `Basic` with parameters → 400; both
  parameters → 400; 101 ids → 400; a valid driver receiving only the subset of
  requested ids that exist. On Dispatch: `?driverId=` with no header → 401;
  `?driverId=` for another driver → 403; `?driverId=` with a passenger-only token
  (`drv == null`) → 403; `?driverId=` with a valid owner `Basic` credential
  naming any driver → 200; `?orderId=` with no header → 200, proving it stayed
  open deliberately.
- **`todayData.ts` must send the owner credential** (Decision 5) — thread it from
  `OwnerControlCenter.tsx` line 44 through `loadTodaySnapshot()` and onto the
  `/v1/orders` fetch at line 103. Verify against a *live* 401 that the Owner
  Control Center shows real counters and not a silent zero; the existing
  `.catch(() => [])` makes this the one change whose failure is invisible in the
  UI. The **same credential must also be sent on the `/v1/proposals?driverId=`
  fan-out at line 108** (Decision 4's owner branch); with `.catch(() => [])` there
  too, forgetting it silently empties the event feed rather than showing an
  error. `/v1/drivers` and `/v1/assignments?orderId=` are not gated by this ADR
  and must keep working with no credential — verify they still do.
- **`Coordinator.tsx` must be left unmodified** (Decision 6). Its broken order
  list is the accepted outcome, not a defect to work around, and no partial
  mitigation may be added to it in this fix.
- **Regression, unchanged by this ADR and to be proven so:** `POST /v1/orders`,
  `POST /v1/orders/{id}/cancel`, proposal create/accept/decline/lapse,
  `statedPrice`, `statedEtaMinutes`, `requestedPickupAt`, and the assignment
  lifecycle. None of them is touched.

## Deliberately not done here

`POST /v1/orders` is left unauthenticated — its one server-to-server caller is
`passenger-experience`'s `RestClientOrderSubmissionClient`, and authenticating a
service-to-service write is a separate decision. `POST /v1/orders/{id}/cancel`,
`GET /v1/drivers`, `POST /v1/drivers/{id}/availability`, every `/v1/assignments`
endpoint, `GET /v1/proposals?orderId=`, and every proposal action endpoint remain
unauthenticated. `network-management` stays isolated and untouched. No rate
limiting, no audit log, no data-retention rule, no RBAC, no role model, no
pagination, and no field-level redaction is introduced. Each of those is a
separate decision, and several need evidence or a Product Decision first.

**Naming them here is the point.** This ADR closes one hole completely; it does
not make PIOS authenticated, and nobody should read it as having done so.

## Follow-up this ADR creates

Neither item is part of the implementation task this ADR authorizes, and neither
is a defect. Both are consequences of accepted rulings, recorded so they are not
rediscovered as surprises.

1. **`docs/PILOT_OPERATION_PLAN.md` needs correcting for Decision 6.** Section
   0.4 describes a Coordinator screen that lists orders and proposes rides;
   after this fix it lists nothing and can propose nothing. 0.1.3's observation
   that `/coordinator` *«открыт любому, кто знает адрес»* stays true of the route
   itself, but its order list is no longer the disclosure it was. That document
   is a living record of what the pilot actually is, so it should be updated
   rather than left describing a screen that no longer works. This is a
   documentation task, not part of the security fix, and it must not be done by
   deleting the superseded text (`CLAUDE.md`: "Never Delete Documentation").
2. **The data-retention Product Decision named in Decision 7 remains
   outstanding**, and now gates two things rather than one: closing the driver
   retention gap here, and any future ride-contact disclosure (ADR-059's own
   Blocker 1, still unresolved). Whoever writes it should know it serves both.

## Consequences

- The demonstrated vulnerability — anonymous retrieval of every passenger's name,
  pickup address, destination and schedule — is closed, and so is the anonymous
  enumeration path that fed it.
- Order Management becomes the second module to authenticate participants, using
  the replicated verifier ADR-055 ratified, gaining no build dependency on
  `identity` and no call to it. Dispatch becomes the third, for one branch of one
  endpoint, and now carries both gates — `SessionTokenVerifier` for participants
  and its pre-existing `OwnerCredentialGate` for the owner.
- **A driver's proposal list now requires their token** (Decision 4). This is the
  first time a Dispatch endpoint can refuse a participant, and it puts the
  ADR-055 stale-`drv`-token trap on the critical path of a driver's main screen
  rather than on a secondary counter. Its addendum is required reading before
  implementation.
- **Requirement (3) is met in a weaker form than requested**, per Decision 3, and
  the Product Owner accepted that knowingly on 2026-08-15. PIOS carries a stated
  retention gap from this point forward: a driver's read access to an order's
  passenger data does not end when their proposal does. Anyone who later reads
  this ADR should understand that as a decision with a reason and a named
  prerequisite (Decision 7), not as an unnoticed hole.
- **`GET /v1/orders` acquires a required header for the first time**, reversing
  ADR-044 Decision 5's promise that *"the pilot flow for Артур and for passengers
  remains exactly as unauthenticated after this ADR as it is before it."* That
  promise was already partially spent by ADR-055; it is spent further here, and
  deliberately.
- The Coordinator screen degrades and stays degraded (Decision 6, accepted
  2026-08-15). The operator loses the only screen listing every order and the
  only screen from which a ride can be proposed manually.
- A misconfigured deployment fails closed: every participant sees 401 and the
  pilot stops. This is the correct failure direction and it is stated so nobody
  is surprised by it at 2am.
- `Order.origin` acquires a meaning it did not formally have (Decision 2), and
  pre-ADR-055 rows become unreadable by their authors.

## Traceability

| Source | Relationship |
| --- | --- |
| `ADR-055` line 116 | Names Order Management lockdown as the next step; this ADR is it |
| `ADR-055` Decisions 1, 4, 5 | Token format, local per-module verification, the enforcement shape copied, `passengerReference == identityId` extended here to `Order.origin` |
| `ADR-044` Decision 5 | Amended for `GET /v1/orders` (Decision 5) and `GET /v1/proposals?driverId=` (Decision 4); Decisions 6 and 11 preserved |
| `ADR-043` Decisions 1, 3, 5 | Owner surface stays browser-side, fan-out, read-only |
| `ADR-059` S2, S3, S6 | Dispatch as verifier (used); `passengerReference` on `Proposal` (explicitly *not* used); no phone in any response (unchanged) |
| `ADR-011` | Least Privilege, Defense in Depth, Security by Design |
| `MODULE_STRUCTURE.md` Sections 4, 5 | Per-module enforcement, no shared security code, allowed dependencies |
| `ADR-005`, `ADR-009`, `ADR-019`, `ADR-037` | Reference-not-ownership — unamended; no foreign key and no copied data is introduced |
| `ADR-003`, `ADR-027` | Why Order Management does not call Dispatch to authorize a read |
| `ADR-002`, `CLAUDE.md` | Why the retention rule in Decision 7 is not invented here |
| `PILOT_OPERATION_PLAN.md` 0.1.3, 0.3.4, 0.4.9 | Coordinator screen's unauthenticated status and its absence from the real pilot path |

# ADR-044: Owner Authentication — PIOS's First Authentication Mechanism

## Status

Accepted. Ratified by Product Owner 2026-08-02, after the Self Architecture
Review below and its three fixes (token mechanism removed in favor of
per-request Basic Authorization; outbox health query corrected; CORS
class count corrected). The Product Owner separately confirmed support for
each architectural consequence of that review: no session/token mechanism,
no shared auth service, no new tables, no server-side state, services
remain independently deployable.

Expected to be **superseded** by the eventual participant-authentication
decision (see Decision 11). It is written to be removable, not to be built
on.

## Revision 2026-08-02 (pre-ratification critical review)

The first draft of this ADR specified a session token: login exchanged the
password for an HMAC-SHA256-signed bearer token, issued by any of the five
modules and verified locally by all of them, with a twelve-hour lifetime and
a shared `PIOS_OWNER_SESSION_SECRET`.

**That token is removed.** A critical self-review requested by the Product
Owner asked whether the design was genuinely minimal or quietly committing
the project to something painful later. It was the latter, in four specific
ways, and all four are removed by deleting the token rather than by
mitigating it:

1. **It created the only genuine inter-module trust relationship in the
   design.** Module A issued a credential that module B accepted. That is a
   materially stronger coupling than five modules independently checking a
   credential presented by an external client, and it was the one part of
   the design that made the ADR-026 question non-trivial.
2. **It made revocation impossible.** The first draft had to admit, in its
   own Consequences, that a leaked token stayed valid for up to twelve hours
   with no way to withdraw it short of rotating a secret and restarting five
   processes. Checking the credential directly makes revocation immediate:
   change the password.
3. **It was the only hand-rolled cryptography in PIOS.** A token format,
   however simple, is code that must be right, replicated five times, in a
   project that had no cryptographic code at all.
4. **It required two endpoints that turn out to be unnecessary.**
   `POST /v1/owner/session` and `GET /v1/owner/session` are deleted; the
   login screen establishes a session by calling `GET /v1/health` and
   reading 200 against 401. The new API surface of the entire MVP is now
   exactly five health endpoints and nothing else.

What the token bought — the password not being resident in the browser for
the session — is smaller than it appears: both artefacts grant identical
access to the same console, the password is deployment-generated and
therefore reused nowhere (Decision 8), and the token was the *unrevocable*
one of the two.

The superseded design is described here rather than deleted, per
`CLAUDE.md`'s "Never Delete Documentation"; the body below describes only
the design being proposed for ratification.

## Why this is not part of ADR-043

Both decisions arrive in the same work, so bundling them was a live option.
Rejected, for three reasons:

1. **This repository's own convention is one decision per ADR.** ADR-038
   (Identity module foundation) and ADR-039 (Identity–Driver association)
   were split despite arriving together; ADR-040 (Assignment ride lifecycle)
   and ADR-041 (Order lifecycle synchronisation) likewise.
2. **The two decisions have opposite futures.** ADR-043's observation
   boundary is meant to stand indefinitely. This decision is meant to be
   replaced the moment PIOS has real participant authentication. Bundling
   them would force a future partial supersession, which ADR-042 identified
   as the failure mode to avoid: *"false ratified text is worse than no
   text"* (its own reasoning for amending ADR-034 in form).
3. **This is the larger architectural moment of the two.** PIOS has had no
   authentication of any kind since its first commit. A dashboard is a new
   screen; the first credential the platform has ever checked is a new
   category of thing in the system. It deserves to be findable under its own
   number rather than as a subsection of a dashboard ADR.

## Context

### PIOS has no authentication today, by design

Verified against the code on 2026-08-02:

- No module declares `spring-boot-starter-security`. A case-insensitive
  search of `backend/` for `SecurityFilterChain`, `@PreAuthorize`,
  `Authorization`, `Bearer` and `token` returns exactly one file:
  `backend/identity/src/main/kotlin/com/pios/identity/domain/Session.kt`, a
  domain type with `issuedAt`/`expiresAt` that **no controller, application
  service or repository references**.
- There is no `HandlerInterceptor`, no servlet `Filter`, and no
  `WebMvcConfigurer` anywhere in `backend/` other than the six
  `WebCorsConfiguration` classes. There is no existing request-interception
  seam to reuse; this ADR's check is the first one in the platform.
- `IdentityController`'s own KDoc (lines 19–31) states it plainly: *"No
  other endpoint exists on this controller — no verification, no login, no
  session issuance (ADR-038's own explicit scope boundary, unchanged by
  ADR-039)."*
- `Coordinator.tsx`'s own KDoc (lines 80–85) states it for the frontend:
  *"no authentication — this page is reachable by anyone who navigates to
  it, same as every other page in this project today."*
- ADR-038 line 52 sets an explicit gate: *"A future ADR is required before
  `identity` gains a second real capability (issuing an actual verified
  `Credential`)."*

So the absence is deliberate and documented, not an oversight. Introducing
authentication is a change to a ratified position and requires an ADR before
the change, per `CLAUDE.md` and `MODULE_STRUCTURE.md` Section 8.

### What the ratified security principles already require

ADR-011 (lines 19–23) binds every boundary to Security by Design, Least
Privilege and Defense in Depth, and states that *"This ADR does not define
any authentication protocol, identity provider, encryption method, or other
implementation mechanism."* This ADR supplies one mechanism, for one
boundary, and is measured against those three principles in Decision 10 —
including where it falls short.

### Configuration idioms already in use

Three exist today, and a fourth must not be invented:
`System.getenv("PIOS_PILOT_FRONTEND_ORIGIN")` in the six
`WebCorsConfiguration` classes; `@Value("${pios.order-management.base-url}")`
in `passenger-experience`'s `OrderManagementRestClientConfiguration` line 30;
and `@Scheduled(fixedDelayString = "${pios.outbox.relay.fixed-delay-ms:2000}")`
in the three `OutboxRelayScheduler` classes. The `pios.*` Spring property
namespace is the established convention and is what Decision 2 uses —
environment-variable overridable through Spring's own relaxed binding,
without a second idiom.

### What is actually being protected, and what is not

ADR-043 introduces exactly two new things: the five `GET /v1/health`
endpoints, and the console screen. The health endpoints expose operational
internals — datasource reachability, unpublished outbox depth, age of the
oldest unpublished record — that are not public today and have no non-owner
consumer.

Everything else the console reads is **already fully public and stays that
way**: `GET /v1/orders` today returns every order with passenger names,
pickup addresses and destinations to anyone who knows the pilot URL. That
exposure predates this work by several sprints and is not created, widened
or worsened by it. Closing it means authenticating drivers and passengers,
which is Decision 11's scope.

The honest description of what this ADR buys is therefore narrow: it puts a
password in front of the owner's console and the operational internals
behind it. It does not make PIOS an authenticated system.

### The Product Owner's constraints

From `PRODUCT_DECISION_OWNER_CONTROL_CENTER.md` Section 4: one person, the
platform owner; *"Панель не предполагает нескольких пользователей с разными
правами — это сознательное ограничение, а не временное упрощение"*.
Section 7: nothing may be changed through the panel, including settings,
roles and access rights. Section 9: no notifications in this MVP.
Section 11: the panel never speaks technically to its only user.

From the Product Owner's decision of 2026-08-02: a login screen with
username and password, built as part of this MVP rather than deferred, and
explicitly no role system. The earlier proposal to rely on an unguessable
URL was rejected.

## Problem

How does PIOS authenticate exactly one operator, for a read-only console and
five self-reporting health endpoints, without creating a user-management
capability, without a new module, without any module depending on any other,
and without changing the existing unauthenticated driver and passenger
surface?

## Decision

### 1. Owner authentication is an edge concern. No bounded context owns it, and no module issues anything.

The check is implemented identically in each of the five pilot modules
(`driver-management`, `passenger-experience`, `order-management`, `dispatch`,
`identity`) and applied only to that module's own `GET /v1/health`. Each
module verifies a credential presented by the browser, against its own
configuration, and answers for itself. **No module produces a credential
that another module consumes.**

This is the same shape, and the same justification, as
`WebCorsConfiguration`, which exists six times over with a KDoc (lines
14–23) recording that the replication is *"a minimal operational necessity,
not an architectural decision"*. Shared code between modules is forbidden
(`MODULE_STRUCTURE.md` Section 4: the root build file shares build tooling
*"never shared business code or domain logic"*), so replication is the
available and precedented answer, not a compromise.

**Explicitly: nothing here lives in `identity`.** Considered seriously and
rejected on two grounds.

- *Conceptual.* ADR-038's own self-critique (item 1) draws a careful line
  between authentication identity (`Identity`) and relationship identity
  (`Person`). The platform's operator is neither: no phone anchor, no
  device, no verification challenge, no participation in the marketplace.
- *Functional.* If one module were the sole issuer, its outage would lock
  the owner out of the tool whose entire purpose is telling him what is
  broken. With no issuer at all, this failure mode does not exist.

Consequently **this ADR does not trigger, discharge or consume ADR-038 line
52's gate.** That gate remains in force, unconsumed, for the day `identity`
gains a real verified `Credential`. Nothing here is an `Identity`, a
`Credential`, a `Device`, a `VerificationChallenge`, or ADR-038's `Session`.
`IdentityController`'s own KDoc remains literally true after this change and
**must not be edited**: no endpoint is added to it, and no login, session or
verification capability is added to that module's domain.

### 2. The credential lives in deployment configuration, not in a database.

Each of the five modules reads three values, through the existing `pios.*`
Spring property convention:

- `pios.owner.username`
- `pios.owner.password-hash` — PBKDF2-HMAC-SHA256, base64
- `pios.owner.password-salt` — base64, generated once per deployment

No table, no migration, no aggregate, no repository, and — with the token
removed — no signing secret anywhere. Rationale:

- There is exactly one owner, with no self-service registration, no
  password-reset flow, no second user, and no lifecycle. Re-running
  ADR-035 Part 2's six-criteria aggregate test returns no on every
  criterion: no invariant of its own, no lifecycle precondition, no
  independent query need, no second actor, no cardinality mismatch, no
  in-module precedent.
- A credential table is the first component of a user-management system,
  which `PRODUCT_DECISION_OWNER_CONTROL_CENTER.md` Sections 4 and 7 exclude.
  Configuration cannot accidentally grow a second row through a UI.
- It touches no module's ratified schema, so no bounded context absorbs a
  concern outside its ownership.

PBKDF2 is chosen because `javax.crypto.SecretKeyFactory` provides it in the
JDK — no new dependency in five modules. The iteration count is a
configuration value with a sane default: high enough to be meaningful,
low enough that one verification per module per poll interval is negligible.
Comparison of the derived key is constant-time.

Consequence, disclosed: changing the password is a configuration change plus
a restart of the five processes. For one operator in a pilot this is
acceptable, and the friction is itself a guard against this becoming a user
directory.

### 3. The credential is presented on each request, in the `Authorization` header. There is no session token and no session state.

`Authorization: Basic base64(username:password)` on `GET /v1/health`. The
module derives a key from the presented password using its configured salt
and iteration count, compares it in constant time against
`pios.owner.password-hash`, and answers 200 or 401.

Three implementation constraints follow, each easy to get wrong and each
binding on the Developer task:

- **A 401 from this endpoint must not carry a `WWW-Authenticate` header.**
  If it does, the browser raises its own native credential dialog, which
  would pre-empt the login screen this MVP specifies and would speak in the
  browser's words rather than the product's — a direct violation of
  `PRODUCT_DECISION_OWNER_CONTROL_CENTER.md` Section 11.
- The username must not contain a colon, since the scheme's own encoding
  uses one as the separator.
- The credential must never be logged, and a failed attempt must not
  distinguish "unknown username" from "wrong password".

No CORS change is required: `allowedHeaders("*")` (line 39) already permits
`Authorization`, so this approach touches **none** of the six
`WebCorsConfiguration` files, all of which sit on the live driver and
passenger request path. A cookie would have required `allowCredentials(true)`
plus an explicit header allowlist in those same files, which is why it is
rejected.

The browser holds the credential in `sessionStorage` for the life of the
tab, and the console clears it on explicit logout or on a client-side idle
timeout. Both are frontend behaviours; the server holds no session state at
all.

### 4. Login is `GET /v1/health`. No dedicated authentication endpoint exists.

The login screen submits the credential by calling `GET /v1/health`. A 200
from any pilot module proves the credential correct and the console opens. A
401 from a module that answered proves it incorrect. No answer at all from
any module is a third case, distinguishable in the browser — `apiClient.ts`
lines 61–74 already separate `ApiError`, which carries a status, from a
network failure, which does not — and shown as "cannot check the password
right now" rather than as a wrong password.

This is why the previously-specified `POST /v1/owner/session` and
`GET /v1/owner/session` are deleted: they would have been a second way to
express a check the gated endpoint already performs. **The new API surface
of this entire MVP is five `GET /v1/health` endpoints and nothing else.**

### 5. What is gated: the five `/v1/health` endpoints and the console route. Nothing else.

**Gated (401 without a valid credential):**

- `GET /v1/health` on all five pilot modules;
- the Owner Control Center frontend route, which renders the login screen
  instead of the console until a credential is held.

**Not gated, not changed, not touched:**

- `GET/POST /v1/orders`, `GET/POST /v1/drivers`,
  `POST /v1/drivers/{id}/availability`, `GET/POST /v1/proposals` and its
  accept/decline/lapse actions, `GET/POST /v1/assignments` and its
  arrive/start/complete actions, `GET/POST /v1/connections`,
  `GET/POST /v1/identities` and `POST /v1/identities/{id}/driver`;
- `frontend/src/pages/RideRequest/RideRequest.tsx`,
  `frontend/src/pages/DriverHome/DriverHome.tsx`,
  `frontend/src/pages/PassengerLanding/PassengerLanding.tsx`,
  `frontend/src/pages/Coordinator/Coordinator.tsx`,
  `frontend/src/pages/NetworkTest/NetworkTest.tsx`;
- all six `WebCorsConfiguration` classes;
- `network-management` in its entirety.

**The pilot flow for Артур and for passengers remains exactly as
unauthenticated after this ADR as it is before it.** No driver and no
passenger is asked for a credential, and no existing request acquires a new
required header.

Gating health specifically is the one judgement call: it is new, has no
consumer other than the owner, and reveals internal state of no use to a
legitimate anonymous caller and obvious use to a hostile one. Gating it costs
nothing in compatibility because nothing exists that calls it. That is Least
Privilege applied at the moment of creation, which is ADR-011's Security by
Design — *"a property considered when a domain's boundary is designed, not
added afterward"*.

Not extending the gate to the existing endpoints is equally deliberate: they
are called by unauthenticated driver and passenger browsers, and
authenticating them means participant authentication (Decision 11).

### 6. No roles, no permissions, no user management.

One credential, one capability: read the observation surface. No role model,
no permission check beyond "is this credential correct", no second account,
no account creation, no password-change endpoint, no account listing, and no
audit of who did what — because there is exactly one subject and it performs
no actions. This implements
`PRODUCT_DECISION_OWNER_CONTROL_CENTER.md` Sections 4 and 7 directly.

If a second operator is ever needed, that is not a configuration change
under this ADR; it is the point at which a real user model has become
necessary, and it requires its own decision — which Section 4 of the Product
Decision already anticipates.

### 7. Logout and revocation.

Logout discards the credential from `sessionStorage`. Because nothing is
issued, nothing has to be revoked: **a compromised credential is withdrawn
by changing `pios.owner.password-hash` and restarting**, and the withdrawal
is immediate and total. There is no window during which a previously-issued
artefact remains valid.

### 8. Brute-force resistance is deliberate but limited, and the limits are named.

Each module applies to its own `GET /v1/health`: a fixed delay after a failed
verification, and a cap on failed attempts per time window, held in memory in
that process. Successful verifications are not counted, since the console
polls continuously with a correct credential.

Honest limitations: the counter resets when a process restarts, and because
the check is per-process across five modules, an attacker distributing
attempts across them gets five times the budget. For a single operator with a
strong, deployment-generated password over TLS this is adequate; it is not a
substitute for rate limiting at the edge, and it is not claimed to be.

The password must be generated for the deployment, not chosen for
memorability, and never committed to the repository.

### 9. TLS is a precondition of this mechanism, not an enhancement of it.

A credential in an `Authorization` header travels in the clear over plain
HTTP, and is sent on every poll rather than once. The pilot is served through
a Cloudflare Tunnel, which terminates TLS
(`PIOS_PILOT_INFRASTRUCTURE_DECISION.md`; `frontend/vite.config.ts` lines
7–14). **If this surface is ever served over plain HTTP, the mechanism
provides no protection at all.** This is an operational precondition to be
stated in the deployment documentation, not an assumption left implicit.

### 10. Measured against ADR-011 — including where it falls short.

- **Security by Design — satisfied.** The health endpoints are gated at the
  moment they are created, not retrofitted.
- **Least Privilege — satisfied for what is introduced.** The credential
  grants read-only observation and nothing else; the health endpoint exposes
  four named facts and nothing else; Actuator's broad surface was rejected
  on exactly this ground (ADR-043 Decision 2).
- **Defense in Depth — not satisfied, and not claimed.** This is a single
  boundary. Behind it the existing `/v1/**` endpoints remain open, so an
  attacker who ignores the console reaches the same order and passenger data
  directly. ADR-011's third principle is *partially unmet by this ADR*,
  knowingly. It is unmet today as well; this ADR neither worsens nor repairs
  it. Repairing it is participant authentication (Decision 11). Putting
  Cloudflare Access or equivalent in front of the deployment remains
  available as a cheap outer layer and is the recommended next step toward
  it.

### 11. Participant authentication remains out of scope and gated — and this mechanism is built to be deleted.

Driver login, passenger login, phone verification, OTP, passkeys, OAuth,
sessions for participants, and any closing of the existing public endpoints
are **not** authorised by this ADR. They remain where ADR-038 put them:
behind a future ADR.

**What superseding this actually costs, walked through rather than
asserted.** Nothing survives its removal: no table, no migration, no
persisted row, no issued artefact, no wire format any future system must
stay compatible with, no client-side storage beyond one value in
`sessionStorage`, and no module that has come to depend on another. Deleting
this mechanism is: remove one class from five modules, remove three property
values from five deployments, remove one route guard from the frontend.
Because the console sends a standard `Authorization` header, a future
mechanism that also uses `Authorization` replaces it without the console's
request-building code changing shape at all.

This is the specific property the first draft's token design did not have,
and it is the main reason that draft was rejected on review.

## Alternatives Considered

- **A session token issued by any module and verified by all** — the first
  draft of this ADR. Rejected on review; see "Revision 2026-08-02" above.
- **Unguessable URL only, no authentication.** The original recommendation
  for a 3–8 person pilot, on the reasoning that the underlying APIs are
  already public so the console adds an index rather than an exposure.
  Rejected by the Product Owner on 2026-08-02, and the rejection is right
  for a reason beyond preference: the health endpoints are genuinely *new*
  exposure, and "the data is already public" is an argument that weakens
  every sprint rather than strengthening.
- **Cloudflare Access or Basic auth at the tunnel.** Zero application code
  and a real gate. Rejected as the *primary* mechanism because it protects
  only what passes through that specific deployment path; the health
  endpoints would be unprotected in any other deployment, including local
  development against pilot data. Retained as the recommended additional
  outer layer (Decision 10).
- **Hosting the check in `identity`.** Rejected — Decision 1, on both
  conceptual and availability grounds.
- **A dedicated auth service or gateway.** Rejected: a new deployable
  requires ratification of a new bounded context
  (`MODULE_STRUCTURE.md` Section 8) and makes five modules depend on one
  more thing that can be down, to protect one endpoint each.
- **Spring Security in five modules.** Rejected: five new dependency trees
  and a framework-wide filter chain introduced into five live services to
  protect one endpoint each, on a platform that has never had one. The
  hand-written check is smaller than the configuration Spring Security would
  need, and its entire behaviour is visible in one file per module.
- **Storing the credential in a new table.** Rejected — Decision 2.

## Consequences

### Positive

- PIOS acquires its first authentication boundary at the moment it acquires
  its first genuinely sensitive surface, rather than afterwards.
- The console cannot be opened by anyone who merely learns the pilot URL.
- **No module depends on any other module being up** in order to
  authenticate or to serve its own health endpoint. Domain isolation
  (ADR-009) and independent deployability (ADR-026) are preserved exactly.
- Revocation is immediate and complete (Decision 7).
- No cryptographic protocol is invented; the only cryptography used is a
  standard JDK key-derivation function applied in the ordinary way.
- The driver and passenger experience is untouched — no new field, no new
  header, no new failure mode on any path Артур or a passenger uses.
- `identity`'s ratified scope is untouched and ADR-038's gate stays
  unconsumed.
- The mechanism is small enough to delete when real authentication arrives,
  and Decision 11 states exactly what deleting it costs.

### Negative, disclosed — including the debt this leaves

- **The credential is replicated across five deployments.** Rotation is a
  five-place operation followed by five restarts, and the cost grows
  linearly: a sixth pilot module would be a sixth place. This is genuine
  operational debt and is recorded as such.

  It is *not*, however, a violation of ADR-026, which defines independent
  deployability as a property of the release process — *"each module can be
  built, versioned, and released without requiring any other module to be
  rebuilt or redeployed alongside it"* — and that remains true: no module's
  build, version or release depends on another's, and no module's artefact
  contains anything belonging to another. What is coupled is a configuration
  value, not an artefact, and the coupling is between five deployments and
  one human operator, not between two bounded contexts.

  ADR-014's related requirement — that a change to one domain not require
  redeploying unrelated domains — is under mild tension, and it is named
  rather than argued away: rotating the owner password does mean restarting
  five services. Since the trigger is a credential change rather than a
  domain change, this is judged acceptable; a future reader who challenges
  that judgement is raising a fair point, not misreading the ADR.
- **There is no audit trail.** Nothing records that the owner logged in, or
  when, or from where. With one subject performing no actions this is
  tolerable; it stops being tolerable the moment there is a second subject,
  which is precisely the point at which Decision 6 says a real user model is
  required.
- **Brute-force protection is per-process and resets on restart**
  (Decision 8).
- **The credential is sent on every poll**, not once per session — more
  transmissions than a token design would need, all over the same TLS to the
  same five services, and therefore a wider window only if TLS is absent,
  which Decision 9 forbids.
- **The password is resident in `sessionStorage`** for the life of the tab.
  Under a successful XSS this is equivalent in effect to a stolen token, and
  worse in that it does not expire on its own — but it is revocable, which
  the token was not. The console renders no HTML from data and React escapes
  interpolated values by default.
- **ADR-011's Defense in Depth remains unmet** (Decision 10).
- **The existing public endpoints stay public.** Anyone stating that "the
  dashboard is now secured" must not be understood to mean that PIOS is.

## Traceability

| Subject | Source |
| --- | --- |
| No authentication exists today | `IdentityController` KDoc lines 19–31; `Coordinator.tsx` KDoc lines 80–85; absence of `spring-boot-starter-security`, and of any `HandlerInterceptor`/`Filter`/`WebMvcConfigurer` other than the six CORS classes, across all module sources and build files |
| Security principles this is measured against | ADR-011 lines 19–23 |
| `identity`'s scope boundary, left intact | ADR-038 (scope, lines 33–37; gate, line 52), ADR-039 |
| Replicated edge configuration precedent | `WebCorsConfiguration` KDoc lines 14–23, replicated in six modules |
| Configuration idiom reused, not invented | `pios.outbox.relay.fixed-delay-ms` (`OutboxRelayScheduler` lines 46–47); `pios.order-management.base-url` (`OrderManagementRestClientConfiguration` line 30) |
| No shared code between modules | `MODULE_STRUCTURE.md` Section 4 |
| Independent deployability, and its exact definition | ADR-026 (Decision, second paragraph); ADR-014 |
| No module becomes a runtime dependency of another | ADR-026, ADR-027 |
| New bounded context would require ratification | `MODULE_STRUCTURE.md` Section 8 (line 116) |
| Aggregate test applied to the credential | ADR-035 Part 2 |
| CORS constraints shaping the credential transport | `WebCorsConfiguration` lines 36–39 |
| Browser distinguishes 401 from unreachable | `frontend/src/api/apiClient.ts` lines 61–74 |
| TLS precondition | `PIOS_PILOT_INFRASTRUCTURE_DECISION.md`; `vite.config.ts` lines 7–14 |
| One user, no roles, no delegation | `PRODUCT_DECISION_OWNER_CONTROL_CENTER.md` Sections 4, 7 |
| Panel never speaks technically to its user | `PRODUCT_DECISION_OWNER_CONTROL_CENTER.md` Section 11 |
| What this surface is for | ADR-043 |

## Files Changed

This ADR only (`docs/ADR/ADR-044-Owner-Authentication-Mechanism.md`).
No source file is touched by this document.

`API_SPECIFICATION.md` and `INTERFACE_CONTRACTS.md` must record the gated
`GET /v1/health` before it is implemented, per ADR-006. With the session
endpoints removed, that is the only entry either document needs from this
ADR.

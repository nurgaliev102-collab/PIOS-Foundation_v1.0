# ADR-079: Test-Data Credential for Synthetic (`isTest`) Driver Creation

## Status

**Accepted — 2026-09-17. Author: Architect role. Ratification of the *mechanism*: taken here as ordinary engineering. Ratification of the *production configuration*: an owner act, materialized by whether the owner sets the key (see Decision 6 — unset means disabled).**

Why this is classified as ordinary engineering rather than a Product Owner question, stated so the classification can be challenged rather than assumed:

- It **reverses nothing that was ratified.** No ADR in this repository ratified "creating an `isTest` driver requires the owner credential." That rule appeared in code in `8206ff3` (the security hardening pass) and is documented only in `DriverController`'s own KDoc. `ADR-076` — the ADR that pass is usually attributed to — says nothing about driver-creation authentication at all; its only `isTest` content is the *dispatch-side* fail-closed gate (`ADR-076` line 109–113) and an explicit refusal to derive `isTest` from anything (line 117), neither of which this ADR touches.
- It **decides no business rule.** No pricing, commission, matching, eligibility or regulatory rule is created or implied (`ADR-002`, `CLAUDE.md` "Never Invent Business Rules").
- It **introduces a new authentication mechanism**, which is exactly the kind of change `ADR-011` and `ADR-044`'s own precedent say must be recorded in an ADR before it is built. Hence this document, written *before* the code, not after it.

**What this ADR does not settle, and deliberately leaves to the owner:** whether the credential is configured in production at all. Decision 6 makes "unconfigured" the default and "unconfigured" mean "every request 401" — so the recommendation in Part 5 can be declined simply by never setting the value, with no code change and no ADR amendment.

**One honest disclosure, because it bears on how much weight this ADR's Context carries.** Two ratified ADRs still describe `POST /v1/drivers` as ungated, and both were made false by `8206ff3` without an ADR:

- `ADR-073` line 118: *"`POST /v1/drivers` is deliberately unauthenticated."*
- `ADR-044` Decision 5, line 301–307: lists `GET/POST /v1/drivers` under *"Not gated, not changed, not touched."*

This ADR does **not** reverse the `8206ff3` gate — the gate is a genuine improvement and removing it would be a real security regression. It records the drift and recommends the `ADR-015` amendment pointers as a follow-up (Part 7). Recording it is not optional: pretending the current code matches `ADR-073`/`ADR-044` would make every later reader's file:line reasoning wrong.

---

## Context

### What the code does today (read at `HEAD` = `71535cd`, branch `pios-product-main`)

`backend/driver-management/src/main/kotlin/com/pios/drivermanagement/api/DriverController.kt` lines 156–184, `createDriver`:

```kotlin
val ownerRequest = authorization?.startsWith("Basic ") == true
if (ownerRequest) {
    if (!ownerCredentialGate.verify(authorization)) { return 401 }
} else {
    val verified = sessionTokenVerifier.verify(authorization) ?: return 401
    if (verified.guest) { return 403 }
    if (verified.sub != request.driverId || (verified.drv != null && verified.drv != request.driverId)) { return 403 }
    if (request.isTest) { return 403 }          // line 176–178
}
persistDriver(request)
```

So: an owner `Basic` credential may create anything; a self-naming `Bearer` session may create a real driver only; **`isTest: true` is reachable through exactly one credential — the owner's.**

`OwnerCredentialGate` has exactly three consumers in this module, verified by search, and this matters for Decision 4's blast-radius claim:

- `DriverController.kt:164` — `createDriver`;
- `DriverController.kt:201` — `listDrivers` (full driver roster, 401 without it);
- `HealthController.kt:41` — `GET /v1/health` (`ADR-044`'s original and only intended subject).

### Why that is a problem, and whose problem it is

The owner credential is the **real human owner's production admin login** (`PIOS_OWNER_USERNAME=ildar`, per `docs/PIOS_AI_HANDOFF.md` KNOWN RISKS #1, line 68). Only its PBKDF2 hash is readable from `/etc/pios/driver-management.env`. It is not recoverable, and it should not be recoverable: an automated E2E script authenticating as the human owner would mean every synthetic driver row in production was written under the owner's own identity, with `listDrivers` and health access attached to the same secret. That is a Least Privilege failure (`ADR-011`), not merely an inconvenience.

The consequence is recorded in two live trackers, not inferred here:

- `docs/PIOS_AI_HANDOFF.md` KNOWN RISKS #1 (line 68) and #2 (line 69): the driver-side golden path has not been re-verified since `8206ff3`.
- `docs/PIOS_TAXI_COMMERCIAL_EXECUTION.md` line 90: `ADR-078`'s decline/lapse recovery **has never been exercised against a live decline**, explicitly because a second `isTest` driver cannot be created on production.

### What production `isTest` E2E has actually caught

This is the load-bearing part of the argument for Part 5, and it is evidence from this repository, not a general claim about testing:

- **The `countInvitedBy` defect.** `backend/driver-management/src/main/kotlin/com/pios/drivermanagement/persistence/PostgreSQLDriverRepository.kt` lines 78–91 carries its own correction note: *"(correction, 2026-09-15, found via live production E2E: the original `is_test = FALSE` filter made this permanently unverifiable through this project's own isTest E2E convention)."*
- **`ADR-069`** — test/real segregation in Fallback Dispatch — is written, in its own line 18, as a response to *"a confirmed live production defect."*

Both defects live **in the test/real segregation machinery itself**. A defect in the rule that separates test data from real data is, by construction, hard to find anywhere that has no real data to be separated from — and PIOS has **no staging environment**: one VPS, seven systemd services, no second deployment described anywhere in `docs/`.

### The gap this ADR closes

There is no credential in this system that is (a) scriptable, (b) not the owner's, and (c) capable of writing a row marked `is_test = true`. That is the whole gap. Nothing else about the endpoint needs to change.

---

## Decision

### 1. A new, single-purpose gate class in `driver-management` only

`TestDataCredentialGate`, a new `@Component` in `com.pios.drivermanagement.api`, alongside `OwnerCredentialGate` and `SessionTokenVerifier`.

**Named `TestDataCredentialGate`, not `TestCredentialGate`** (the working proposal's name): a `src/main` class whose name begins with `Test` reads as test infrastructure in a Gradle/JUnit repository and will be misfiled by the next reader. It gates *test data*, not tests.

**No replica in any other module.** `MODULE_STRUCTURE.md` Section 4 forbids shared business code, and `ADR-055` Decision 1's "replicate, don't share" convention is what produced five copies of `SessionTokenVerifier`. That convention says *replicate when a second module genuinely needs it* — no other module has a `isTest` write path that is owner-gated, so no other module needs it. Any future replica requires its own ADR paragraph, not a copy-paste.

### 2. Its own `Authorization` scheme: `PiosTest <token>`

Not `Basic`, not `Bearer`, and deliberately **not** the bare `Test` of the working proposal: `Test` is short enough to collide with something later and is not vendor-distinguishable, while `PiosTest` can never be confused with an IANA-registered scheme and is trivially greppable across backend, frontend and scripts.

**Check order in `createDriver` is part of this decision, not an implementation detail:**

1. `PiosTest ` prefix → `TestDataCredentialGate.verify`;
2. `Basic ` prefix → `OwnerCredentialGate.verify` (**unchanged**);
3. otherwise → `SessionTokenVerifier` path (**unchanged**, including the line 176–178 `isTest` 403).

A `PiosTest` header **must never reach `OwnerCredentialGate.verify`**. That is a real requirement, not tidiness: `OwnerCredentialGate` records a failure and sleeps on every non-matching credential (lines 95–101) and locks out after 20 failures in 15 minutes (lines 62–64, 104–108). Routing test-credential traffic through it would let a wrong or abused test token **lock the real owner console out of production**. The new gate keeps its own independent failure window.

### 3. Credential weight: SHA-256 of a high-entropy token, not PBKDF2 — with reasons

The working proposal mirrored `OwnerCredentialGate`'s PBKDF2-HMAC-SHA256 + salt + 210 000 iterations. **Rejected as over-engineering, for a stated reason rather than a preference:**

- PBKDF2's iteration count exists to make *offline* brute force expensive against a **human-chosen, low-entropy** secret. That is exactly `pios.owner.password-*`'s situation.
- This credential is **machine-generated and never typed by a human**: a ≥256-bit token from a CSPRNG, base64. Offline brute force against SHA-256 of a 256-bit random preimage is not a threat that iteration count improves; it is infeasible by entropy alone.
- The **salt** likewise buys nothing: salts defeat rainbow tables and cross-target correlation for low-entropy secrets. One high-entropy secret with no siblings has neither exposure.
- What *does* matter for this credential is online guessing — and that is answered by entropy plus the same throttling `ADR-044` Decision 8 already established.

**So:** store `pios.test-data.credential-hash` = base64(SHA-256(token)). Compare with `MessageDigest.isEqual` (constant time — the same primitive `OwnerCredentialGate.matchesConfiguredPassword` already uses, line 125). Keep the `failureDelayMillis` / `maxFailuresPerWindow` / `windowMillis` in-memory throttle, with the same disclosed limitation `ADR-044` Decision 8 discloses (per-process, resets on restart).

Hashing rather than storing the token in plaintext is retained even though a 256-bit token makes preimage recovery infeasible: a config leak then yields an unusable digest rather than a live credential, and it costs one function call. Under-engineering would be plaintext comparison with `==`; over-engineering would be PBKDF2. This sits where the threat model actually is.

### 4. Capability: one endpoint, one branch, nothing else

The `PiosTest` credential authorizes **exactly** `POST /v1/drivers` where `request.isTest == true`.

- Valid `PiosTest` credential **and** `isTest == false` → **403**. It can never create a real driver.
- It is accepted on **no** other endpoint: not `listDrivers` (`DriverController.kt:197`), not `getDriver`, not `getMilestones`, not `declareAvailability`, not `updateVehicle`, not `updateLongDistancePreference`, not `HealthController` (`HealthController.kt:41`), not any endpoint in any other module.
- It grants **no read access of any kind**.
- It must not be plumbed through the internal `createDriver(request)` seam at `DriverController.kt:154` — that seam bypasses all authentication by design and is not an HTTP endpoint; it stays as it is.

### 5. `invitedByDriverId` under this credential is confined to the test lane

When the caller is authenticated by `PiosTest`, a supplied `invitedByDriverId` naming a driver that is **not** itself `isTest` degrades to `null`.

Reasoning, kept narrow so it is not mistaken for a business rule: `countInvitedBy` (`PostgreSQLDriverRepository.kt:86–91`) deliberately has **no** `is_test` filter, so a test-lane row would otherwise increment a **real** driver's private `invitedDriversCount`. That contradicts `ADR-069`'s segregation invariant for no gain — a referral-loop E2E is fully verifiable with a test inviter *and* a test invitee. Degrading to `null` rather than rejecting preserves `ADR-073` Part 2's own ratified semantics (`CreateDriverApplicationService.kt:96–109`: a bad inviter must never fail a registration). This applies to the `PiosTest` branch only; the owner and `Bearer` branches are untouched.

### 6. Fail-closed default; configuration

Unset `pios.test-data.credential-hash` ⇒ `isConfigured()` is `false` ⇒ `verify()` returns `false` for every request ⇒ **401**. Identical posture to `pios.owner.*` and `pios.session.secret` in `backend/driver-management/src/main/resources/application.yml` lines 45–73, and documented in that file the same commented-out way.

```
pios:
  test-data:
    credential-hash: ""          # base64(SHA-256(token)); token is >=256 bits from a CSPRNG
    auth:
      failure-delay-ms: 500
      max-failures-per-window: 20
      window-ms: 900000
```

No table, no migration, no session, no token issuance, no rotation state — the same "no new persistent surface" property `ADR-044` Decision 2 established. Rotation is: generate a new token, replace one line in `/etc/pios/driver-management.env`, restart one service.

### 7. What this ADR does not authorize

No change to the owner `Basic` branch or the `Bearer` branch of `createDriver`. No new endpoint. No test-data *deletion* or cleanup endpoint. No replica of this gate in `dispatch`, `order-management`, `passenger-experience`, `identity`, `billing`, `network-management` or `core`. No change to how `isTest` arrives on an **order** — `ADR-076` line 113's disclosed gap (order `isTest` is still a client-supplied flag) is neither closed nor widened here. Nothing touching `ADR-054`, delegation, Handoff, `Connection` lifecycle, or any Product Decision reserved to the owner.

---

## Blast radius, verified rather than asserted

**If the token leaks, the holder can create driver rows with `is_test = true`, and nothing else.** Checked against the full `DriverController`, the identity registration path, and the dispatch selection queries:

1. **It reaches no other capability.** `OwnerCredentialGate`'s three consumers are `createDriver`, `listDrivers` and `HealthController` (search-verified); the new gate is wired into one branch of one of them. Every other endpoint on this controller requires a `Bearer` token whose `drv` equals the path driver id (`DriverController.kt` lines 233, 257, 294, 324) — creating a driver row mints no session and no token, so a leaked test token cannot bring its own driver on line, accept a proposal, or read milestones.

2. **It cannot overwrite or take over any existing driver.** `CreateDriverApplicationService.handle` checks `findById` first and throws `DriverAlreadyExistsException` → 409 (`CreateDriverApplicationService.kt:71–73`), specifically because the PostgreSQL `save` is an upsert. Real drivers cannot be clobbered.

3. **The created row is excluded from every real-order selection path.** Registration emits `DriverAvailabilityChanged` carrying `isTest` into Dispatch's projection (`CreateDriverApplicationService.kt:87–92`), and both selection queries use strict, fail-closed equality: `PostgreSQLDriverAvailabilityRepository.findLongestIdleAvailable` — `available = true AND is_test IS NOT NULL AND is_test = ?` (Tier 3) — and `PostgreSQLTrustedDriverRepository.findLongestIdleTrustedAvailable` — `a.is_test IS NOT NULL AND a.is_test = ?` (Tier 1), both per `ADR-069` Part 3/5. `ADR-076` line 111 adds the same `record.isTest == command.isTest` check to the direct and First-Refusal paths. A test driver is never offered a real order.

4. **The capability is strictly weaker than what the public internet already has.** `POST /v1/identities/register` is unauthenticated (`IdentityController.kt:127`), `POST /v1/identities/{id}/driver` needs only the caller's own fresh session (`IdentityController.kt:214`), and `createDriver`'s `Bearer` branch then accepts a self-naming caller. **Any anonymous caller can already create an unlimited number of `is_test = false` drivers — rows that *do* enter real dispatch.** The `PiosTest` credential adds exactly one thing on top of that: the ability to set the flag that *removes* the row from real dispatch. Measured against the real baseline, it is a de-escalation, not an escalation.

**Residual effects, stated rather than waved away:**

- **Row volume.** A leaked token allows bulk writes to production `drivers`. Already true anonymously (point 4), and the throttle bounds failures, not successes — so this is a pre-existing, unclosed property of open registration, not something this ADR creates. It is not closed here.
- **Owner-console noise.** Test rows appear in `listDrivers`; `DriverResponse` carries `isTest` (`DriverController.kt:276`) so they remain distinguishable. Unchanged from today's convention.
- **`countInvitedBy` inflation.** Achievable today anonymously with real rows, since the `is_test` filter was deliberately removed (`PostgreSQLDriverRepository.kt:78–91`). Decision 5 keeps the new credential *out* of that vector rather than adding to it.
- **Driver-id squatting.** Ids are caller-generated UUIDs; 409 protects existing rows (point 2); pre-creating an unguessable UUID someone else will later pick is not a realistic attack.

---

## Part 5 — Production or non-production only: recommendation

**Recommendation: configure it in production, under the named conditions below. Also configure it in local/QA, with a different token.**

The tradeoff, stated plainly in both directions:

**Cost of production-only-never (non-prod by convention).** PIOS has no staging environment. Production `isTest` E2E is not a habit this session invented — it is the repository's own established verification method, and it is what found the two defects cited in Context, both of which lived *inside* the test/real segregation logic and therefore could not have been found in an environment with no real data. Declining production configuration converts `PIOS_AI_HANDOFF.md` KNOWN RISKS #2 from "temporarily blocked" into "permanent," leaves `ADR-078` (already deployed to production) without any path to live verification (`PIOS_TAXI_COMMERCIAL_EXECUTION.md` line 90), and makes every future golden-path claim an inference from unit tests. The honest alternative — build a staging environment with production-like data — is a far larger decision, with its own personal-data implications, and is not proposed here.

**Cost of configuring it in production.** One more secret on the VPS; synthetic rows continue to accumulate in the production database; a leak enables bulk synthetic-row creation. The first is routine, the second is the status quo of the existing convention, and the third is bounded by the blast-radius analysis above — specifically point 4, which shows the leak grants nothing an anonymous caller lacks.

The asymmetry is decisive: the *cost of having it* is bounded by a capability the public already holds, while the *cost of not having it* is the loss of the only verification method that has actually caught production defects in this system.

**Conditions attached to the recommendation:**

1. A **freshly generated, production-only** token, distinct from every non-production token, generated by the owner, never committed, never pasted into a document, living only in `/etc/pios/driver-management.env`.
2. **Different token per environment.** A dev/QA token must never authenticate against production.
3. **Rotate on any durable exposure** — if it is ever pasted into a chat transcript, CI log, or issue. Rotation is one config line plus one service restart (Decision 6); treat it as cheap and do it rather than reasoning about whether the exposure mattered.
4. Record it in `docs/PIOS_DEPLOYMENT_SECRETS.md` the same way the existing secrets are recorded — **name and purpose, never value**.
5. If the owner declines: set nothing. Decision 6's fail-closed default means production then behaves exactly as it does today, and this ADR needs no amendment — only a dated note here saying the recommendation was declined.

---

## Consequences

**Positive**
- The `isTest` E2E convention is restored for driver creation without the owner credential being shared, extracted, or reused in any form.
- `ADR-078`'s decline/lapse recovery becomes live-verifiable; `KNOWN RISKS #1` becomes closable; `#2` becomes re-verifiable.
- Real (non-test) driver creation is **not** weakened by a single line: the owner and `Bearer` branches, including the line 176–178 `isTest` 403 for self-registering callers, are untouched.
- The owner console gains protection it lacks today: test traffic can no longer consume `OwnerCredentialGate`'s shared failure budget (Decision 2).

**Negative**
- A third credential mechanism in one module (`OwnerCredentialGate`, `SessionTokenVerifier`, `TestDataCredentialGate`). Justified only because the first two cannot express "may write test data and nothing else."
- A deliberate divergence from `ADR-044`'s PBKDF2 shape (Decision 3). Divergence is a maintenance cost; it is taken knowingly, with the threat-model reason written down, rather than copied for symmetry.
- Synthetic rows keep accruing in production. Unchanged from the existing convention; no cleanup mechanism is authorized here.

**Neutral**
- No schema change, no migration, no Flyway version, no event, no new endpoint, no frontend change, no cross-module contract. `driver-management` alone rebuilds and redeploys.

---

## Follow-ups this ADR requires (not performed by this document)

1. **`ADR-015` amendment pointers**, dated, original text preserved: `ADR-073` line 118 and `ADR-044` Decision 5 (line 303) both still assert `POST /v1/drivers` is ungated, which `8206ff3` made false. Each should gain a pointer to `8206ff3` and to this ADR.
2. `DriverController`'s class KDoc (lines 71–85) must describe the third accepted credential once implemented.
3. `application.yml` gains the commented `pios.test-data.*` block, in the style of lines 45–73.
4. Tests: a `TestDataCredentialGateTest` mirroring `OwnerCredentialGateTest`, plus controller cases for — valid `PiosTest` + `isTest: true` → 201; valid `PiosTest` + `isTest: false` → 403; invalid/absent token → 401; unconfigured gate → 401; `PiosTest` rejected on `listDrivers`; a `PiosTest`-created driver naming a real inviter stores `null` (Decision 5).
5. `docs/PIOS_AI_HANDOFF.md` KNOWN RISKS #1 updated once deployed.

## Handoff to the developer role

**In scope (driver-management only):**
- `backend/driver-management/src/main/kotlin/com/pios/drivermanagement/api/TestDataCredentialGate.kt` (new)
- `backend/driver-management/src/main/kotlin/com/pios/drivermanagement/api/DriverController.kt` — `createDriver` branch ordering and KDoc only
- `backend/driver-management/src/main/resources/application.yml` — commented config block
- corresponding tests under `backend/driver-management/src/test/...`

**Out of scope:** every other module; `OwnerCredentialGate`; `SessionTokenVerifier`; `HealthController`; `listDrivers`; `CreateDriverApplicationService` (Decision 5 is enforced in the controller branch, using the already-injected `driverRepository`, so the application service keeps its caller-agnostic shape); any schema or migration; any frontend file; `ADR-054` and anything delegation-adjacent.

**Constraints the implementation must satisfy:** Decisions 2 (check order, independent failure window, never reaches `OwnerCredentialGate`), 3 (SHA-256 + `MessageDigest.isEqual` + throttle), 4 (one endpoint, one branch, 403 when `isTest == false`, no read access), 5 (`invitedByDriverId` degrades to `null` unless the inviter is itself `isTest`), 6 (fail closed when unconfigured).

## References

- `docs/ADR/ADR-044-Owner-Authentication-Mechanism.md` — Decisions 2, 3, 5, 8; the pattern this one deliberately diverges from and why.
- `docs/ADR/ADR-055-Session-Authentication-and-Password-Credential.md` — Decision 1, "replicate, don't share."
- `docs/ADR/ADR-069-Test-Real-Segregation-In-Fallback-Driver-Selection.md` — Parts 2, 3, 5; the segregation invariant Decision 5 preserves.
- `docs/ADR/ADR-073-Driver-To-Driver-Referral-Single-Hop-Origin-Fact.md` — Parts 2 and 4; line 118's now-stale ungated claim.
- `docs/ADR/ADR-076-Server-Authorized-Named-Driver-Offer.md` — lines 109–117; dispatch-side `isTest` gate, unchanged here.
- `docs/ADR/ADR-011-Security-Principles.md`, `docs/ADR/ADR-002-Dispatch-Engine.md` (scope limits), `docs/MODULE_STRUCTURE.md` Section 4.
- `docs/PIOS_AI_HANDOFF.md` KNOWN RISKS #1/#2; `docs/PIOS_TAXI_COMMERCIAL_EXECUTION.md` line 90.
- Code read at `HEAD` `71535cd`: `DriverController.kt`, `OwnerCredentialGate.kt`, `HealthController.kt`, `CreateDriverApplicationService.kt`, `PostgreSQLDriverRepository.kt`, `IdentityController.kt`, `PostgreSQLDriverAvailabilityRepository.kt`, `PostgreSQLTrustedDriverRepository.kt`, `application.yml`.

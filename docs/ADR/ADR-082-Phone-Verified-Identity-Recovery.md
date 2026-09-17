# ADR-082: Phone-Verified Identity Recovery (D-03)

## Status

**Accepted by the Product Owner, 2026-09-17 (D-03), as an architectural and design contract — implemented the same day.** This ADR records the ratified decisions D-03.1–D-03.8, the design built to satisfy them, and (§Implementation Record below) what was actually built and verified. Bound by everything this ADR states as a constraint, and by the one point (§8) this ADR flags as a real, disclosed limitation rather than a fully solved problem.

## Context

`ADR-038` placed `Identity.phone`, `Credential`, and `VerificationChallenge`/`VerificationMethod` in the domain specifically so a future recovery flow would not need a schema migration against live data. `ADR-055` built phone+password authentication but explicitly excluded password reset, SMS/OTP, and server-side session revocation. `ADR-075` built a guest lifecycle and explicitly declared guests unrecoverable by construction. The D-03 reconciliation (this conversation, 2026-09-17, read-only, no code changed) confirmed: no recovery path exists today; `identity_credentials` is insert-only (a second write for the same `identity_id` throws, by design); every `Identity.phone` in the database today is an **unverified claim** — nothing was ever proven; and retrofitting SMS-only recovery onto that unverified data would let whoever currently holds a phone number claim any account that happens to list it, including a driver's entire business record reachable through that account.

This ADR is the design response to that reconciliation, built to the Product Owner's own D-03.1–D-03.8 ratification, reproduced and satisfied below.

## Decision

### Part 1 — Scope (D-03.1)

Phone-verified recovery, via SMS OTP, is authorized for **registered identities only**. Guest identities (`ADR-075`) remain unrecoverable by construction — a guest has no `phone`, so it is structurally excluded from every eligibility check below, not special-cased around.

### Part 2 — Legacy identities and the enrolment rule (D-03.2)

Every `Identity.phone` written before this ADR, and every one written after it until explicitly proven, is **UNVERIFIED**. Verification is a new, explicit fact (`phoneVerifiedAt`), never inferred from the phone's mere presence, age, or format validity.

A legacy (currently-UNVERIFIED) identity becomes recovery-eligible only by completing **legacy enrolment**: proving both (a) an existing, currently-valid authenticated session for that identity, and (b) SMS OTP proof of the phone already on file. Enrolment sets `phoneVerifiedAt`; it does not, by itself, grant or imply anything else (Part 9, invariant 8).

**A legacy user who has already lost their authenticated session before this ADR exists gets no automatic recovery path.** This ADR deliberately builds no fallback for that case. It is named here as explicitly out of scope, left to a separate Product/Security decision, per direct instruction.

### Part 3 — Session invalidation: a generation counter, not a session table (D-03.3)

`Identity` gains `sessionGeneration: Int`, defaulting to `0` for every existing row. A successful recovery increments it by exactly one, in the same transaction as the credential replacement, and a fresh token is minted carrying the new value. `ADR-055`'s stateless, no-session-table architecture is **not** revisited — this is an additive claim on the existing token shape, following the exact precedent `ADR-075`'s `gst` claim already established (additive, absence reads as the conservative default).

**A real, disclosed limitation, surfaced here rather than glossed over (see §8 for the full reasoning):** `ADR-055` Decision 1 requires every module to verify a token **locally**, with no per-request call to `identity`. `identity`'s own endpoints already read the live `Identity` row on most calls and can therefore compare a token's `sgen` claim against the live value with no new coupling — so generation-based invalidation is **fully, immediately effective for `identity`'s own endpoints** (`/me`, `/{id}`, `/{id}/driver`, `/me/register`, and the new endpoints this ADR adds). The five other modules' replicated verifiers have no local access to `identity`'s database and **cannot** check a live generation value without either a session table or a per-request cross-module call — both of which this decision (D-03.3) explicitly declines to introduce. A token minted before a recovery event therefore **continues to verify successfully in those five modules until its own natural `exp`** — the same "a stolen token is valid until it expires" trade `ADR-055` already discloses and accepts for every other scenario, now also disclosed as applying here. This is not a new gap invented by this ADR; it is the existing, accepted trade-off, named explicitly rather than silently inherited.

### Part 4 — Login rate limiting: unchanged (D-03.4)

`LoginRateLimiter` is not modified by this ADR. The targeted-lockout / IP-distribution gap the D-03 reconciliation found (§F.2 of that report) is registered here as an open, separate security follow-up — not fixed, not silently left undocumented either.

### Part 5 — OTP scope: `SMS_OTP` only, provider undecided (D-03.5)

Only `VerificationMethod.SMS_OTP` is implemented. `SILENT_NETWORK_AUTH` and `TELEGRAM` remain exactly what `ADR-038` already left them: named discriminator values, nothing built. No SMS provider is chosen by this ADR. Part 10 defines the seam a provider attaches to later, without provider-specific code ever entering the domain model — directly reusing `ADR-074` Part 3's already-proven "a seam, not a provider SDK" shape.

### Part 6 — Recovery invariants (D-03.6)

Binding on the design in Parts 7–8 and on implementation:

1. Recovery never creates a new `Identity` for an existing registered user.
2. Recovery always preserves the same `Identity.id`.
3. Recovery never changes an existing `driverId`.
4. Recovery never lets the requester choose or supply a `driverId`.
5. Recovery never transfers Driver Business data between identities.
6. If an `Identity` already has a `driverId`, that `driverId` is unchanged after recovery.
7. A guest identity cannot use recovery (structural — see Part 1).
8. Phone verification never creates a driver association as a side effect.
9. Knowing a phone number alone never recovers an identity — OTP proof of ownership is required, and for a legacy identity, the Part 2 enrolment rule must be satisfied first.

### Part 7 — OTP security properties (D-03.7)

Binding on the OTP lifecycle (Part 9):

- The code is stored only as a salted hash; plaintext is never persisted.
- Single-use, atomically consumed.
- Expiration is mandatory and checked on every verification.
- A bounded attempt count is enforced per challenge, durably (a database column, not an in-memory counter, so it survives a restart).
- Verification uses constant-time comparison.
- Requests are rate-limited by phone and by IP/client-key.
- A duplicate or replayed request is rejected — requesting a new challenge supersedes any prior live one; a consumed or superseded challenge can never succeed again.
- The recovery-request response is generic regardless of whether the phone resolves to a registered, eligible identity — it never discloses registration status.
- No OTP code and no full phone number is ever logged.

### Part 8 — Provider cost (D-03.8)

The SMS provider is PIOS's first external runtime dependency and first genuine per-user variable cost, accepted here as the necessary cost of recovery. The specific provider and tariff are a separate technical/procurement decision, made after Part 10's seam exists — not before, and not by this ADR.

---

## Design

### 1. Final state machine

```
Identity lifecycle (ADR-075, unchanged by this ADR):

  guest (phone=null, no credential, driverId=null)
     │  POST /v1/identities/me/register
     ▼
  registered (phone set, credential written, driverId still possibly null)
     │  POST /v1/identities/{id}/driver
     ▼
  registered + driver-associated

Phone verification sub-state (new, D-03, orthogonal to the above,
tracked by identities.phone_verified_at):

  UNVERIFIED  (default — every legacy row, every freshly registered row)
     │  legacy enrolment (Bearer session + SMS OTP, purpose=LEGACY_ENROLLMENT)
     │  — or, identically, a first-time "verify my phone" action by any
     │    already-registered identity, since the mechanism is the same act
     │    regardless of when the identity itself was created
     ▼
  VERIFIED  (terminal — no "un-verify" operation exists or is authorized)

Recovery eligibility = registered AND phone != null AND phone_verified_at != null.

Recovery attempt (transient, per OTP challenge row, purpose=RECOVERY):

  (request) → OTP_LIVE → (correct code, within attempts, before expiry) → CONSUMED
                  │
                  ├─ (new request for same identity+purpose) → SUPERSEDED
                  ├─ (expires_at passed) → EXPIRED (checked at verify time, not a written state)
                  └─ (max_attempts reached) → dead (checked at verify time, not a written state)
```

### 2. Identity aggregate changes

`Identity` (`backend/identity/.../domain/Identity.kt`) gains two fields:

```kotlin
class Identity(
    val id: IdentityId,
    val phone: Phone?,
    val driverId: String?,
    val createdAt: Instant,
    val phoneVerifiedAt: Instant? = null,   // new — null means UNVERIFIED
    val sessionGeneration: Int = 0          // new — bumped by exactly one on each successful recovery
) {
    fun withDriverId(driverId: String): Identity = /* unchanged */
    fun withPhone(phone: Phone): Identity = /* unchanged */
    fun withPhoneVerified(verifiedAt: Instant): Identity =
        Identity(id, phone, driverId, createdAt, verifiedAt, sessionGeneration)
        // Callers must enforce phoneVerifiedAt == null beforehand — this is a
        // one-way transition, mirroring UpgradeGuestIdentityApplicationService's
        // own guest→registered check() precondition style.
    fun withSessionGenerationBumped(): Identity =
        Identity(id, phone, driverId, createdAt, phoneVerifiedAt, sessionGeneration + 1)
}
```

No change to `withDriverId`'s existing single-assignment guard (`AssociateDriverApplicationService.kt:42`) — it already structurally satisfies Part 6 invariants 3, 4, 6. No new field is added for `driverId` mutability; none is needed.

### 3. DB model

New migration `V5__phone_verification_and_recovery.sql` (next free version after `V4`; no existing migration edited):

```sql
-- D-03 Part 2: every existing row is UNVERIFIED by construction. No backfill,
-- no inference from any other column.
ALTER TABLE identities ADD COLUMN phone_verified_at TIMESTAMPTZ;

-- D-03 Part 3: session-generation counter, additive token claim.
ALTER TABLE identities ADD COLUMN session_generation INTEGER NOT NULL DEFAULT 0;

-- D-03 Part 7: durable, hashed, single-use, expiring, attempt-bounded OTP
-- challenges. Shared by RECOVERY and LEGACY_ENROLLMENT purposes so both
-- flows get the same security properties from one mechanism, not two.
CREATE TABLE phone_verification_challenges (
    id             TEXT PRIMARY KEY,
    identity_id    TEXT NOT NULL REFERENCES identities (id),
    phone          TEXT NOT NULL,
    purpose        TEXT NOT NULL CHECK (purpose IN ('RECOVERY', 'LEGACY_ENROLLMENT')),
    code_hash      TEXT NOT NULL,   -- base64, PBKDF2-HMAC-SHA256, never plaintext
    code_salt      TEXT NOT NULL,   -- base64, generated per challenge
    iterations     INTEGER NOT NULL,
    attempt_count  INTEGER NOT NULL DEFAULT 0,
    max_attempts   INTEGER NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    expires_at     TIMESTAMPTZ NOT NULL,
    consumed_at    TIMESTAMPTZ,     -- non-null once successfully verified; single-use
    superseded_at  TIMESTAMPTZ      -- non-null once a newer live challenge replaced it
);

-- At most one currently-live challenge per (identity, purpose) — the DB-level
-- backstop for "duplicate/replay rejected", mirroring ADR-080's own
-- structural-guarantee-over-application-discipline precedent.
CREATE UNIQUE INDEX ux_phone_verification_challenges_live
    ON phone_verification_challenges (identity_id, purpose)
    WHERE consumed_at IS NULL AND superseded_at IS NULL;
```

`identity_credentials` (`V3`) needs one new repository operation (not a schema change): a credential-replace path, since its current `save()` is a plain `INSERT` with no `ON CONFLICT` by deliberate design (D reconciliation finding). The replace is an `UPDATE ... WHERE identity_id = ?` (or `INSERT ... ON CONFLICT (identity_id) DO UPDATE`), added as a new `CredentialRepository.replace(...)` method alongside the existing `save`/`findByIdentityId` — `save` keeps its current insert-only contract for first-time registration; `replace` is new and used only by recovery.

### 4. OTP lifecycle

Shared by both purposes (`RECOVERY`, `LEGACY_ENROLLMENT`):

1. **Request** — `PhoneVerificationChallengeApplicationService.request(identityId, phone, purpose)`:
   - Rate-limit check by phone (new `PhoneOtpRequestRateLimiter`, same shape as `LoginRateLimiter`) and by IP/client-key (reusing `GuestIdentityRateLimiter`'s exact, already-hardened client-key derivation — loopback-only proxy header trust).
   - Inside one transaction: mark any existing live challenge for `(identityId, purpose)` `superseded_at = now()`; generate a random numeric code; hash it (`PasswordHasher`'s own PBKDF2 shape, fresh salt); insert the new row with `expires_at = now() + TTL`.
   - Send the code via `OutboundSmsPort` (Part 10) — never logged.
   - Response: always the same generic acknowledgement.

2. **Verify** — `PhoneVerificationChallengeApplicationService.verify(identityId, purpose, code)`:
   - Lock the live challenge row (`SELECT ... FOR UPDATE`, the same per-row lock discipline `ADR-080`'s `OrderGuard` already proved) so a concurrent double-verify cannot both succeed.
   - No live row, or `expires_at` passed, or `attempt_count >= max_attempts` → generic failure (never distinguish which).
   - Increment `attempt_count` and persist, inside the same transaction as the comparison — "atomic consume."
   - Re-hash the presented code with the stored salt/iterations, compare via `MessageDigest.isEqual`.
   - Match → set `consumed_at = now()`, return success. Mismatch → generic failure, the incremented `attempt_count` already persisted.

### 5. Recovery lifecycle

```
POST /v1/identities/recovery/request {phone}
  → always the same generic response (D-03.7), regardless of match
  → internally: resolve identity by phone; if it is registered AND
    phone_verified_at != null, issue a RECOVERY challenge (Section 4) and
    send it; otherwise do nothing further — the response is identical either
    way, and the same rate-limit bookkeeping runs either way to avoid a
    timing-based enumeration side-channel

POST /v1/identities/recovery/confirm {phone, code, newPassword}
  → one transaction:
      1. verify OTP (Section 4, purpose=RECOVERY) — generic failure on any
         non-success reason
      2. validate newPassword length (reuse RegisterIdentityApplicationService's
         MIN/MAX_PASSWORD_LENGTH)
      3. credentialRepository.replace(...) — new hash/salt/iterations
      4. identity = identity.withSessionGenerationBumped(); save
      5. issue a fresh token carrying the new sessionGeneration
  → 200 AuthResponse {identityId, driverId, token, expiresAt} — driverId is
    whatever it already was; the request body has no driverId field at all,
    so Part 6 invariants 3/4/6 are enforced by the DTO shape itself, not
    only by a runtime check
```

`IdentityId` is never created or changed — the whole flow mutates the existing row found by phone, exactly as `UpgradeGuestIdentityApplicationService` already mutates a guest row in place.

**Legacy enrolment** (distinct entry point, Bearer-gated, D-03 Part 2):

```
POST /v1/identities/me/phone/verify/request   (Bearer, sub==self, non-guest)
  → issues a LEGACY_ENROLLMENT challenge for the caller's own phone already
    on file — this path never accepts a new phone number, only proves the
    existing one
POST /v1/identities/me/phone/verify/confirm {code}   (Bearer, sub==self, non-guest)
  → verify OTP (purpose=LEGACY_ENROLLMENT) → identity.withPhoneVerified(now());
    save. No credential change, no sessionGeneration bump — this is a proof
    event on an already-trusted session, not a recovery event.
```

### 6. API contracts

| Method | Path | Auth | Request | Response |
|---|---|---|---|---|
| POST | `/v1/identities/recovery/request` | none | `{phone}` | 202 `{}` — always, generic |
| POST | `/v1/identities/recovery/confirm` | none | `{phone, code, newPassword}` | 200 `AuthResponse` · 401 generic failure |
| POST | `/v1/identities/me/phone/verify/request` | Bearer, `sub==self`, non-guest | — | 202 `{}` · 401 · 403 (guest) · 409 (already verified) |
| POST | `/v1/identities/me/phone/verify/confirm` | Bearer, `sub==self`, non-guest | `{code}` | 200 `IdentityResponse` · 401 generic failure |

All existing endpoints (`register`, `login`, `me`, `{id}`, `{id}/driver`, `guest`, `me/register`) are unchanged.

### 7. Transaction boundaries

Every new write path runs inside one `transactionRunner.run { }` boundary, matching this module's existing, unbroken convention:
- OTP request: supersede-prior + insert-new, one transaction.
- OTP verify: row-lock + attempt-increment + compare + consume, one transaction.
- Recovery confirm: verify + credential-replace + generation-bump + token-issue, one transaction — an uncaught exception rolls back the OTP consumption too, so a genuinely failed recovery is never mistaken for an already-consumed challenge on retry.
- Legacy enrolment confirm: verify + `phoneVerifiedAt` set, one transaction.

### 8. Session token changes

Payload gains one additive claim: `"sgen": <int>`, mirroring exactly how `ADR-075` introduced `gst` — absent or unparseable reads as `0`, so every token minted before this ADR keeps verifying, correctly, at generation `0`.

**Scoping disclosed, not hidden (see Part 3 above):** full, immediate, cross-module revocation-by-generation would require either a session table or a per-request call to `identity` from the other five modules — both excluded by D-03.3. This design gives `sessionGeneration` **full effect within `identity`'s own endpoints** (already reading the live `Identity` row; comparing `sgen` there is a small addition, not a new access pattern) and **no immediate effect in the five other modules**, where an old token remains valid until its own `exp` — identical to the trade `ADR-055` already accepts for every other token-theft scenario. This is named here explicitly so it is never later assumed to be a complete revocation guarantee it is not.

### 9. Security / rate-limit model

| D-03.7 requirement | Mechanism |
|---|---|
| Hash only, no plaintext | PBKDF2-HMAC-SHA256, per-challenge salt (`PasswordHasher` shape reused) |
| Single-use | `consumed_at` + the partial unique live-index |
| Expiration | `expires_at`, checked on every verify |
| Bounded attempts | `attempt_count`/`max_attempts` columns — durable, survives a restart |
| Atomic consume | Row lock (`SELECT ... FOR UPDATE`) + single transaction |
| Constant-time verification | `MessageDigest.isEqual`, same as `PasswordHasher`/`SessionTokenVerifier` |
| Rate limit by phone | New `PhoneOtpRequestRateLimiter`, `LoginRateLimiter`'s shape |
| Rate limit by IP/client-key | Reuses `GuestIdentityRateLimiter`'s exact, already-hardened derivation |
| Duplicate/replay rejected | New request supersedes prior live challenge; consumed/superseded rows can never succeed |
| Generic recovery-request response | Identical response regardless of match, Section 5 |
| No phone-existence leak | Same generic-response principle for unknown, unverified-legacy, and guest phones alike |
| No OTP/full-phone logging | Mirrors `IdentityController`'s existing PII-conscious logging discipline |
| Login rate limiting | **Unchanged** (D-03.4) — targeted-lockout/IP-distribution gap registered as a separate, open follow-up, not fixed here |

### 10. Provider adapter seam

Directly reuses `ADR-074` Part 3's proven shape, not a new pattern:

```kotlin
// application layer — no SMS-provider-specific type anywhere in domain/
interface OutboundSmsPort {
    fun sendVerificationCode(phone: Phone, code: String)
}
```

Called only from the OTP-issuing application service. `Phone`, `Identity`, `VerificationChallenge`/`VerificationMethod` remain exactly as provider-agnostic as `ADR-038` already made them. No provider is chosen or implemented by this ADR (D-03.5/D-03.8) — a concrete adapter, when a provider is chosen in its own separate technical/procurement decision, is one new class implementing `OutboundSmsPort` with its own configuration keys, with zero change to any application service, domain type, or other module — the same "no rewrite" guarantee `ADR-074` Part 3 item 3 already proved for billing's own webhook seam.

### 11. Migration plan

One new file, `backend/identity/src/main/resources/db/migration/identity/V5__phone_verification_and_recovery.sql` (Section 3, in full above). No existing migration is edited. No backfill. Not applied to production by this ADR or by this stage — implementation and any migration run remain future, separately-executed steps, explicitly outside this document's own authorization.

### 12. Test matrix

- **Domain:** `withPhoneVerified`/`withSessionGenerationBumped` — one-way transition, monotonic increment.
- **OTP issuance:** hash never equals plaintext; salt unique per challenge; correct `expires_at`; a new request supersedes the prior live row and never leaves two simultaneously live.
- **OTP verification:** correct code succeeds exactly once; a second attempt against a consumed challenge fails generically; wrong code increments `attempt_count` and fails generically; exceeding `max_attempts` fails permanently for that challenge; an expired challenge fails; a real two-thread PostgreSQL race (mirroring `ADR-080`'s proven pattern) confirms exactly one of two concurrent verify attempts against the same challenge succeeds.
- **Recovery request:** identical response shape/status for a non-existent phone, a guest's (nonexistent) phone, an unverified-legacy phone, and a verified-eligible phone — the enumeration-safety test.
- **Recovery confirm:** `identityId` unchanged; `driverId` unchanged and never requester-settable (the request DTO has no such field — a compile-time guarantee, plus a runtime test that an unexpected extra field is ignored, not honored); `sessionGeneration` incremented by exactly one; the pre-recovery token is rejected by `identity`'s own endpoints afterward; the new token is accepted; the old password no longer authenticates; the new one does.
- **Legacy enrolment:** guest token rejected (403); a non-self token rejected (403); an already-verified phone rejected or no-op (409, exact status decided in implementation); `phoneVerifiedAt` set only after a correct OTP; no change to `driverId` or credential.
- **Cross-module token behavior (documented, and asserted where practical):** a token minted before a recovery event continues to verify successfully in the five non-`identity` modules until its natural `exp` — the disclosed trade-off from Section 8, made explicit in a test or a comment at the point it would otherwise look like an oversight.
- **Security regression:** the recovery-confirm request schema is checked to carry no `driverId` field at all — defense-in-depth alongside the pre-existing, structural `ux_identities_driver_id` DB guarantee.

---

## Consequences

**Positive:** every required change is additive to `identity` alone — no other module, contract, or ratified boundary changes. The domain shapes `ADR-038` placed years-early (`VerificationChallenge`, `VerificationMethod`) are finally used for their stated purpose. The legacy-phone takeover risk the D-03 reconciliation found is closed structurally (enrolment required before eligibility), not by policy alone. Guests remain honestly unrecoverable, exactly as `ADR-075` already committed to.

**Negative, disclosed:** session-generation invalidation is real but partial — full only within `identity`'s own endpoints, not the five other modules, until their own token's natural expiry (§8, §3). Login rate limiting's targeted-lockout gap remains open (Part 4). A legacy user who already lost their session before this ADR has no path back, by explicit instruction, until a separate decision addresses that case.

## What This ADR Does Not Authorize

Any code, migration, or configuration change (none exists yet — this is a design and decision record only); any SMS provider selection or integration; `SILENT_NETWORK_AUTH` or `TELEGRAM` implementation; any change to `LoginRateLimiter`; any session table or reworking of `ADR-055`'s stateless model; any automatic recovery path for a legacy user who already lost their session before this ADR; any way for a recovery requester to supply or influence `driverId`; any backfill of `phone_verified_at`; any change to `driver-management`, `dispatch`, `order-management`, `passenger-experience`, `billing`, or `network-management`; any production database access or migration run.

## Related ADRs

- [ADR-038](ADR-038-Identity-Module-Bounded-Context-Foundation.md) — `Phone`, `Credential`, `VerificationChallenge`/`VerificationMethod`, placed for exactly this.
- [ADR-039](ADR-039-Identity-Driver-Association.md) — the one-to-one, single-assignment `driverId` invariant this ADR's Part 6 relies on and does not alter.
- [ADR-055](ADR-055-Session-Authentication-and-Password-Credential.md) — stateless token model; Decision 1's local-only verification is the exact constraint Part 3/§8 works within, not around.
- [ADR-075](ADR-075-Guest-First-Passenger-Identity-And-In-Place-Upgrade.md) — guest exclusion (Part 1); the additive-claim/conservative-default precedent (`gst`) Part 3's `sgen` claim follows.
- [ADR-074](ADR-074-Subscription-Billing-Bounded-Context-Foundation.md) — Part 3's "seam, not SDK" precedent, reused directly in Part 5/§10.
- [ADR-080](ADR-080-Commitment-Termination-and-Order-Cancellation-Handshake.md) — the per-row-lock, structural-guarantee-over-application-discipline pattern reused in §3/§4/§9.
- [ADR-015](ADR-015-Evolution-Strategy.md) — supersession discipline for any future change to this decision.

## References

- The D-03 reconciliation report produced 2026-09-17 (this conversation) — evidentiary basis for Context.
- `docs/ATLAS_IMPLEMENTATION_RECONCILIATION.md` §7 — independently arrived at the same legacy-bootstrap finding this ADR's Part 2 resolves.
- `docs/PIOS_FINAL_PRODUCT_OWNER_DECISION_BRIEF.md` §6 Decision 9, §17 D-03.
- `backend/identity/src/main/kotlin/com/pios/identity/domain/Identity.kt`, `Phone.kt`, `Credential.kt`, `VerificationChallenge.kt` — read at design time, `Identity.kt` since modified (§Implementation Record).
- `backend/identity/src/main/kotlin/com/pios/identity/application/*`, `api/*`, `persistence/*` — read at design time, since extended (§Implementation Record); `V1`–`V4` migrations remain unmodified.
- `backend/identity/src/main/resources/db/migration/identity/V1`–`V4` — read, not modified. `V5` — named here at design time, created and applied to the isolated test database only (§Implementation Record).

## Implementation Record, 2026-09-17/18

Implemented the same day this ADR's design was accepted, per the Product Owner's own closing instruction to proceed without a further decision request. Full detail (files, test evidence, defects found and fixed, the concurrency proof) is in the D-03 implementation final report delivered in that conversation; this section is the durable, in-repository summary.

**What was built, exactly as designed above:** `Identity.phoneVerifiedAt`/`sessionGeneration` and their two new methods (§2); the new `PhoneVerificationChallenge`/`PhoneVerificationPurpose` domain type and its repository (§3–4); `V5__phone_verification_and_recovery.sql`, applied only to the isolated `pios_identity_test` database — never to production (§3, §11); `RequestRecoveryApplicationService`/`ConfirmRecoveryApplicationService` and `RequestPhoneVerificationApplicationService`/`ConfirmPhoneVerificationApplicationService` (§5); the four new endpoints (§6); the additive `sgen` token claim, checked only within `identity`'s own protected endpoints (§8, unchanged from design — the other five modules' `SessionTokenVerifier` copies were not touched); `OutboundSmsPort`/`NoOpOutboundSmsPort` (§10) — no provider chosen or wired.

**One real design gap found only during implementation, closed:** the design's OTP-issuance step (supersede-then-insert) has no lock preceding it. A real two-thread PostgreSQL test reproduced a `DuplicateKeyException` on two concurrent first-ever requests for the same `(identity, purpose)` — both found nothing to supersede and both attempted to insert. Closed with `PhoneVerificationChallengeGuard`/`PostgreSQLPhoneVerificationChallengeGuard`, a new `phone_verification_challenge_guards` table added to the same `V5` migration (not yet applied anywhere when this was found, so no second migration was needed) — the identical per-key lock-row pattern `ADR-080`'s own `OrderGuard` already established, taken first inside the issuer's transaction. Verified via 5 repeated real-Postgres race iterations with zero further failures.

**Verification, real and repeated, not asserted:** `identity` module: 116 tests, 0 failures, including a real two-thread PostgreSQL race (5 repetitions each) for concurrent recovery confirms, concurrent legacy-enrolment confirms, and concurrent OTP requests, plus a "reopen with fresh repository instances" test mirroring `ADR-080`'s own proof pattern. Full backend regression: `dispatch` 645/645 in isolation (two pre-existing, already-documented flaky scheduler tests, `OutboxRelaySchedulerTest`/`ProposalLapseSchedulerTest`, confirmed via repeated isolated runs as environmental, unrelated to D-03 — `dispatch` was not touched by this work); `driver-management`, `order-management`, `billing`, `network-management`, `passenger-experience` clean; `core`'s 9 failures are a pre-existing local environment-configuration gap (`-Dpios.core.qa.postgres.*` unset), unrelated to D-03 and present before this work began. Frontend: 377/377 (12 new — `Recovery.test.tsx` plus new `DriverHome.test.tsx` cases), `tsc -b` clean, production build clean, lint clean (pre-existing warning-only baseline, no new errors).

**A second real defect, found by a frontend test, fixed in the component:** the "verify your phone" card on `DriverHome`'s Profile tab was originally gated only on `identity.phoneVerified === false`; since a successful verification flips that value to `true`, the card — including its own "Номер подтверждён." success message — unmounted itself the instant verification succeeded, so a driver would never actually see the confirmation. Fixed by also keeping the card mounted through its own local `phoneVerifyStep === 'done'` state.

**Frontend architecture note:** `StoredIdentity` gained `phoneVerified` directly (populated by `restoreIdentity()`'s already-existing `/me` call and by `confirmPhoneVerification()`'s own response) rather than a separate `getIdentitySummary()` fetch — the first implementation attempt added a second, redundant `GET /v1/identities/me` call, which broke 22 `DriverHome.test.tsx` tests by shifting every subsequent queued mock response; the fix removed the duplicate call entirely rather than patching each test.

**Not done, per this ADR's own scope:** no SMS provider chosen or integrated; `SILENT_NETWORK_AUTH`/`TELEGRAM` unbuilt; `LoginRateLimiter` unchanged (its targeted-lockout/IP-distribution gap remains a separate, open follow-up); no automatic recovery path for a legacy user who already lost their session before this ADR.

Not committed, not pushed, not deployed by this record — see the conversation's own final report for git status at the time of writing.

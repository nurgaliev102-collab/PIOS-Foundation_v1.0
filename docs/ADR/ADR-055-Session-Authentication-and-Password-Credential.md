# ADR-055: Session Authentication — Password Credential and Signed Session Token

## Status

Proposed. This is the "future ADR" ADR-038 Consequences line 52 requires *before `identity` gains a second real capability*, and the "authorization model" ADR-054 Evolution Path item 5 names for its own Part 6 limitation. Both gates are used here deliberately; neither was bypassed.

## Context

PIOS today authenticates nobody except its owner. Verified in this repository, not paraphrased:

- `backend/identity/.../api/IdentityController.kt` line 49: `GET /v1/identities/{id}` returns an identity to whoever presents the id. No credential is checked, because none exists. Line 60's `POST /v1/identities/{id}/driver` associates a driver reference on the same terms.
- `backend/identity/.../domain/Credential.kt` line 18: `sealed interface Credential` with zero subtypes — an extension point, stated as such in its own KDoc.
- `backend/identity/.../domain/Session.kt` lines 5–14: explicitly leaves open *"whether a real implementation would persist sessions in a table like this one, or issue stateless signed tokens instead — that choice belongs to whichever future ADR actually implements login."* This ADR is that ADR, and answers: stateless signed tokens, no table.
- `backend/identity/src/main/resources/db/migration/identity/V1__initial_schema.sql`: `identities(id, phone, created_at)` only. `phone` is present and unused, placed there by ADR-038 precisely so the login shape would not need a migration against live data later.
- `backend/passenger-experience/.../api/ConnectionController.kt` lines 68–122: all five Circle-of-Trust endpoints trust the `driverId` / `passengerReference` / `connectionId` strings the client sends. ADR-054 Part 6 discloses this and Consequences line 164 records *"Only the passenger changes it is unenforced at the API boundary."*
- `frontend/src/identity/BackendIdentityProvider.ts` lines 81–106 restore identity by re-`GET`ting `/v1/identities/{id}` with no credential; `frontend/src/persistence/localPassengerIdentity.ts` line 40 mints a client-side UUID with no backend involvement at all.

A pilot with real people cannot run on this: any party who learns another party's identifier is that party.

**Evidence-gate note, recorded rather than glossed.** `PIOS_PRODUCT_EVIDENCE.md` holds no `E-NNN` entry motivating authentication, and `PRODUCT_DECISION_ENTREPRENEUR_MODEL.md` Section 4 restates the gate in force (*«элемент объёма, не обеспеченный ни `E-NNN`, ни зарегистрированной гипотезой, в объём не входит»*). This Sprint proceeds on Product Owner authority as a pilot **prerequisite**, not as a product-scope item claiming evidence it does not have. That distinction is the Product Owner's to hold, and is written down here so it is never later read as evidence-backed.

## Decision

### 1. Mechanism: a self-verifying signed session token, verified independently by each module

Each module that needs to know who is calling verifies the token **locally**, using a pre-shared HMAC secret supplied by configuration. No module calls `identity` to check a token.

`passenger-experience` does already make a real synchronous server-to-server REST call (`persistence/RestClientOrderSubmissionClient.kt` → Order Management `POST /v1/orders`, sanctioned by ADR-028's "REST for direct request-driven interaction"), so a per-request call to `identity` was genuinely available and is rejected on its merits, not for lack of precedent: it would put `identity`'s uptime in front of every authenticated read in every module — exactly the temporal coupling ADR-003 rejected and ADR-027 line 22 declines to reintroduce — for a check that needs no shared state.

The chosen shape follows this codebase's own established answer for cross-module security logic: `OwnerCredentialGate.kt` (ADR-044) is replicated identically in five modules rather than centralized, because `MODULE_STRUCTURE.md` Section 4 forbids shared business code between modules. A `SessionTokenVerifier` is replicated the same way, with the same justification. `identity` is the sole **issuer**; every other module is a **verifier only**.

This preserves reference-not-ownership (ADR-005/ADR-009/ADR-019): a verifying module learns an `identityId` string out of a token it checked arithmetically. It stores no identity data, holds no foreign key, and gains no build dependency on `identity`.

**Token format** (`Authorization: Bearer <token>`):

```
<base64url(payload)>.<base64url(HMAC-SHA256(secret, base64url(payload)))>
payload = {"sub":"<identityId>","drv":"<driverId>"|null,"exp":<epochSeconds>}
```

Verification = recompute the HMAC, constant-time compare (`MessageDigest.isEqual`, as `OwnerCredentialGate` already does), then check `exp`. Configuration, mirroring the `pios.owner.*` convention:

- `pios.session.secret` — base64, identical across issuing and verifying modules
- `pios.session.ttl-seconds` — default `2592000` (30 days); an operational parameter, not a business rule

Unset secret ⇒ every verification returns false. Closed by default, same posture as `OwnerCredentialGate.isConfigured()`.

`Basic` (owner) and `Bearer` (session) are distinguished by prefix on the same header, so the two gates coexist without ambiguity.

### 2. Credential: phone + password, PBKDF2-HMAC-SHA256, stored only in `identity`'s own database

Confirmed as proposed. `phone` is the login handle — the field ADR-038 already placed on `Identity` for exactly this. Hashing parameters are `OwnerCredentialGate`'s (PBKDF2WithHmacSHA256, 210 000 iterations default), with a **per-credential** salt rather than a per-deployment one.

`identity` migration `V2__create_identity_credentials.sql`:

```sql
CREATE UNIQUE INDEX ux_identities_phone ON identities (phone) WHERE phone IS NOT NULL;

CREATE TABLE identity_credentials (
    identity_id    TEXT PRIMARY KEY REFERENCES identities (id),
    password_hash  TEXT NOT NULL,        -- base64, PBKDF2-HMAC-SHA256
    password_salt  TEXT NOT NULL,        -- base64, generated per credential
    iterations     INTEGER NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL
);
```

Phone is **not** duplicated into the credential table — it stays the existing `identities.phone` column, now uniquely indexed, so there is one source of truth. `Credential.kt`'s sealed interface gains its first subtype, `PasswordCredential`. **No session table**: Session.kt's open question resolves to stateless.

### 3. `identity` API

| Method | Path | Auth | Request | Response |
| --- | --- | --- | --- | --- |
| POST | `/v1/identities/register` | none | `{phone, password}` | 201 `{identityId, driverId, token, expiresAt}` · 409 phone taken · 400 malformed |
| POST | `/v1/identities/login` | none | `{phone, password}` | 200 `{identityId, driverId, token, expiresAt}` · 401 otherwise |
| GET | `/v1/identities/me` | Bearer | — | 200 `{id, phone, driverId}` · 401 |
| GET | `/v1/identities/{id}` | Bearer, `sub == {id}` | — | 200 `IdentityResponse` · 401 · 403 |
| POST | `/v1/identities/{id}/driver` | Bearer, `sub == {id}` | `{driverId}` | 200 · 401 · 403 |

`POST /v1/identities` (create with no credential) is **removed**, superseded by `register`. Leaving it would let anyone mint a credential-less identity and attach a driver reference to it — the exact hole this ADR closes. This is a deliberate breaking change to `BackendIdentityProvider.ts`, in scope for this Sprint.

Login failure never distinguishes unknown phone from wrong password, and never logs the presented credential (ADR-044 Decision 3, reused). Failure-rate limiting reuses `OwnerCredentialGate`'s in-memory window, with its already-disclosed limits.

### 4. `passenger-experience` Circle of Trust — all five endpoints

All require `Authorization: Bearer`; a missing/invalid/expired token is 401 everywhere.

| Endpoint | Additional check | On failure |
| --- | --- | --- |
| `POST /v1/connections` | `body.passengerReference == token.sub` | 403 |
| `GET /v1/connections?passengerReference=` | param `== token.sub` | 403 |
| `GET /v1/connections?driverId=` | param `== token.drv` | 403 |
| `POST /v1/connections/{connectionId}/primary` | stored `passenger_reference == token.sub` | 404 |
| `DELETE /v1/connections/{connectionId}` | stored `passenger_reference == token.sub` | 204, nothing deleted |

The last two do not return 403, deliberately: a `connectionId` is an opaque handle, and answering "403" to a non-owner confirms the id exists. DELETE keeps returning 204 unconditionally so ADR-054's idempotent-DELETE contract survives unchanged; the honest cost is that a non-owner's delete looks successful and is not. Recorded, not hidden.

`driverId` on `POST /v1/connections` stays an **unverified** reference. `passenger-experience` does not own drivers (ADR-054, `ConnectionController` KDoc lines 38–42) and a passenger cannot prove the driver's identifier — verifying it would require the cross-module call rejected in Decision 1.

### 5. `passengerReference` becomes the authenticated `identityId`

The check in Decision 4 is only meaningful if the two are the same string. From this ADR forward, a `passengerReference` written by any client MUST equal the authenticated `identityId`; `localPassengerIdentity.ts`'s client-generated UUID (line 40) stops being an identifier the backend accepts, and its `name` field remains a device-local display value, unchanged and out of scope. **Consequence, disclosed:** `connections` rows written before this ADR carry client-generated UUIDs and become unreachable by their original device. No data migration can fix this — nothing ever proved who those UUIDs belonged to. The Product Owner must accept this before implementation; it is cheap now and irreversible after a real pilot writes real rows.

### 6. Drivers get the same treatment, in this Sprint

A driver logs in through the same `register`/`login` endpoints; their token carries `drv`, populated from the ADR-039 association already stored on `Identity`. `GET /v1/connections?driverId=` is thereby locked to the driver it names, and `BackendIdentityProvider.ts`'s credential-less restore is replaced by token-backed restore.

## Explicitly out of scope

OAuth/SSO, social or Telegram login, 2FA, SMS/OTP verification (`phone` remains unverified — a password proves control of the account, not of the number), passkeys/WebAuthn, RBAC or any role/permission model, refresh tokens, server-side session revocation, password reset, enterprise identity, and `Device`/`VerificationChallenge` (still unpersisted). `network-management` stays isolated; its `Person` is untouched. No Dispatch, Order Management, or Driver Management endpoint is locked down by this ADR — those remain open and are the **named next step**, not something this ADR quietly covers.

## Consequences

- `identity` gains its second real capability, filling ADR-038's own extension point rather than standing up a parallel auth mechanism beside it. `Credential` gets its first subtype; `Session` becomes a resolved question rather than an open one.
- ADR-054 Part 6's disclosed limitation is closed for the passenger side.
- **A stolen token is valid until it expires.** Statelessness buys zero runtime coupling and pays for it in revocation. Mitigated only by TTL. If revocation is ever needed, it needs its own decision — a session table, or a shorter TTL plus refresh.
- **The HMAC secret is shared between modules.** Leaking it in one module forges tokens for all. This is the same trade `OwnerCredentialGate` already makes with its shared owner credential, and is the price of not calling `identity` per request.
- Every frontend call to a protected endpoint must now carry the header; unauthenticated callers break by design.

## Related

ADR-038 (extension point used), ADR-039 (`driverId` association reused as the `drv` claim), ADR-044 (hashing, constant-time compare, rate limiting, per-module replication), ADR-054 (Part 6 closed; contracts otherwise unchanged), ADR-003/ADR-026/ADR-027/ADR-028 (why verification is local, not a call), ADR-005/ADR-009/ADR-019 (reference-not-ownership, unamended).

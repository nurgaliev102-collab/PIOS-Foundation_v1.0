# ADR-075: Guest-First Passenger Identity and In-Place Upgrade

## Status

**Accepted — documenting already-built, uncommitted code (2026-09-16).**

This ADR is **retroactive**. The code it describes already exists in the working tree at the time of writing, was implemented without a Stage 1 Architect review (`.claude/CLAUDE.md`, "Development Workflow", Stage 1 — required when *"domain model changes"*), and is **not committed and not deployed**. This ADR records what was actually built rather than re-designing it, and names the conditions that must hold before it is committed, pushed, or deployed. It is not a claim that the work was authorized in advance; it was not.

**Author:** Architect role. **Ratification:** a human act (`ADR-007`). Nothing here is ratified by this document alone.

**Evidence basis: absent.** Unlike `ADR-068`–`ADR-074`, no hypothesis was registered before this work started. `docs/PIOS_PRODUCT_HYPOTHESES.md` ends at **H12**; there is no H13. `docs/PIOS_PRODUCT_EVIDENCE.md`'s gate rule (*«Перед началом любого нового Sprint… Если ответа нет — не начинаем»*) was therefore not satisfied for this Sprint. Registering a hypothesis is a **Product Owner act** (the precedent set by H7–H12, each registered by the Product Owner, never by an agent) and is deliberately **not performed here**. See "Blocking Prerequisites".

---

## Context

### What existed before (read from source)

`ADR-055` (Session Authentication and Password Credential), Decision 3, **removed** the credential-less `POST /v1/identities` entirely. `IdentityController`'s own KDoc still states the reason verbatim:

> *"ADR-055 Decision 3 removes the old credential-less `POST /v1/identities` entirely — leaving it would let anyone mint a credential-less identity and attach a driver reference to it, the exact hole that ADR closes."*

`Identity` (`backend/identity/src/main/kotlin/com/pios/identity/domain/Identity.kt`) already carried `phone: Phone?` as **nullable**, with its own KDoc explaining that the field exists "only so the shape an eventual verification flow would update already exists". Nothing in the ratified record described an identity that is *intended* to exist without a phone for a whole product flow.

`ADR-039` (Identity–Driver Association) established `Identity.driverId` as a reference, not ownership. It did **not** state that the association is one-to-one, and no database constraint enforced it.

### What was actually built

| Artifact | File |
|---|---|
| `POST /v1/identities/guest` | `backend/identity/src/main/kotlin/com/pios/identity/api/IdentityController.kt` lines 87–107 |
| Guest creation service | `.../application/CreateGuestIdentityApplicationService.kt` |
| In-place upgrade (`POST /v1/identities/me/register`) | `IdentityController.kt` lines 151–176, `.../application/UpgradeGuestIdentityApplicationService.kt` |
| `gst` token claim | `.../application/SessionTokenIssuer.kt` lines 48–66 |
| `gst` verification | `.../api/SessionTokenVerifier.kt` line 82; `driver-management/.../api/SessionTokenVerifier.kt` line 86 |
| Per-client creation limiter | `.../application/GuestIdentityRateLimiter.kt` |
| One-to-one Identity↔Driver index + backfill | `identity/src/main/resources/db/migration/identity/V4__unique_driver_association.sql` |
| Guest token refused for driver creation | `driver-management/.../api/DriverController.kt` lines 158–162 |
| Guest token refused for driver association | `IdentityController.kt` lines 225–227 |

---

## Decision

### 1. An Identity may exist in a **guest** state: no phone, no credential, no driver

`CreateGuestIdentityApplicationService.handle()` persists an `Identity` with `phone = null`, `driverId = null`, and writes **no** `PasswordCredential`. This is a genuine new lifecycle state on a ratified aggregate, and it is recorded here as such:

```
guest  (phone = null, no credential, driverId = null)
   │  POST /v1/identities/me/register  — in place, same IdentityId
   ▼
registered  (phone set, credential written, driverId still null)
   │  POST /v1/identities/{id}/driver  (ADR-039, guest tokens refused)
   ▼
registered + driver-associated
```

There is no transition back. `UpgradeGuestIdentityApplicationService` refuses any identity that already has a phone **or** a credential (`check(identity.phone == null && credentialRepository.findByIdentityId(identityId) == null)`), so "upgrade" is strictly guest → registered, once.

### 2. Upgrade is **in place** — the `IdentityId` never changes

This is the load-bearing property and the reason a second aggregate was not introduced. Every fact already pointing at the guest identity — `Order.origin`, `Proposal.passengerReference`, `primary_connections`, `connections` — keeps pointing at the same opaque string after registration. No cross-module migration, no re-keying, no backfill, and no module other than `identity` is aware that the transition happened. This preserves the reference-not-ownership rule (`ADR-005`, `ADR-019`, `ADR-037`) exactly: other modules hold a plain string and never learn what state it is in.

### 3. `gst` is an **additive** token claim; absence means `false`

`SessionTokenIssuer` adds `"gst": <boolean>` to the ADR-055 payload alongside `sub`/`drv`/`exp`. Verifiers read `payloadNode.get("gst")?.takeIf { it.isBoolean }?.asBoolean() ?: false`, so:

- every token minted before this change keeps verifying, and reads as **not a guest** — the correct, conservative default (a pre-existing token always belonged to a registered identity);
- modules that do not read the claim (`order-management`, `dispatch`, `passenger-experience` copies of `SessionTokenVerifier`) are unaffected and were deliberately left unchanged. Confirmed by search: only `identity` and `driver-management` parse `gst` today.

Guest tokens use a **shorter TTL** (`pios.session.guest-ttl-seconds`, default 604 800 = 7 days) than registered tokens (2 592 000 = 30 days, `ADR-055`).

### 4. A guest may **not** become a driver, and may not associate one

`ADR-055` Decision 3's stated reason for deleting the credential-less endpoint was that it *"would let anyone mint a credential-less identity and attach a driver reference to it."* This ADR reintroduces credential-less identity creation, so that exact hole is closed explicitly at both doors rather than by the endpoint's absence:

- `DriverController` (driver creation) returns **403** for a token with `guest = true`;
- `IdentityController.associateDriver` returns **403** for a token with `guest = true`.

A guest is a *passenger-shaped* identity only. `ADR-055` Decision 3 is therefore **narrowed, not reversed**: credential-less creation is permitted again, but the capability it was deleted to protect remains unreachable from a credential-less token.

### 5. The Identity↔Driver association is one-to-one, enforced in the database

`V4__unique_driver_association.sql` creates `ux_identities_driver_id` — a partial unique index on `identities(driver_id) WHERE driver_id IS NOT NULL`. This makes explicit an invariant `ADR-039` implied but never stated. `NULL`s remain unconstrained, so guests and passenger-only identities are unaffected.

**The migration is destructive by design and this is disclosed, not hidden.** Its own preamble states: *"Historical builds allowed more than one identity to claim the same driver. Keep the oldest claim and revoke every ambiguous later claim before enforcing the invariant."* Any later duplicate claim has its `driver_id` set to `NULL`. The chosen failure mode — revoke rather than reassign — is the safer one, but for a real human it means **losing access to their driver profile at deploy time, silently**. See "Blocking Prerequisites", item 3.

### 6. Anti-abuse: socket address, not caller-supplied headers

`GuestIdentityRateLimiter` bounds guest creations per client key (default 10 per hour, configurable). The client key is derived in `IdentityController.createGuestFromAddress`:

- normally, `request.remoteAddr` — the socket peer, which a caller cannot forge;
- **only** when the socket peer is a loopback address does the `X-PIOS-Client-IP` header override it, and only if it parses as a literal IP.

`CF-Connecting-IP` and `X-Forwarded-For` are **not** trusted at the Identity service, because a public caller can set them itself. The frontend proxy is expected to *replace* `X-PIOS-Client-IP` rather than forward a caller-supplied one.

### 7. What this ADR does **not** claim

Stated plainly, because a credential-less identity invites exactly these assumptions:

- **Phone ownership is not verified**, at guest creation, at upgrade, or at registration. `Identity`'s own KDoc already said this; nothing here changes it.
- **The limiter is in-memory and per-instance** (`ConcurrentHashMap`). It is not distributed anti-abuse. A second `identity` instance, or a restart, resets every window. It is a database-spam bound, not a security control.
- **There is no identity recovery.** A guest whose device or token is lost is unrecoverable by construction — there is no credential to recover with. This is the honest cost of the model and is not solved here.
- **No sybil resistance.** Nothing prevents one human from holding many guest identities.

## What this ADR does not authorize

Phone/SMS verification; any identity recovery mechanism; distributed or persistent rate limiting; trusting `CF-Connecting-IP`/`X-Forwarded-For` anywhere; a guest acquiring any driver-side capability; any use of the `gst` claim as an authorization input in `dispatch`, `order-management`, or `passenger-experience` without a further decision; any read from or write to `network-management` (`ADR-064`); any linkage between `Identity` and `network-management`'s `Person` (`ADR-038` self-critique keeps them separate).

---

## Consequences

### Positive

- A stranger passenger can submit an order without first inventing a password — the entry point `ADR-070`'s Channel 1 discovery flow assumes exists.
- Because upgrade is in place, `ADR-070`'s post-ride "save driver" relationship and every prior order survive registration with no cross-module work at all.
- `ADR-039`'s association becomes an enforced invariant instead of an assumed one.
- `ADR-055`'s deletion rationale is answered directly at the two capabilities it protected, rather than relied on implicitly.

### Negative

- PIOS now persists identities that can never be recovered and can never be attributed to a verified human. Guest rows accumulate; nothing prunes them and no retention rule is decided here.
- The `identity` table's growth is bounded only by an in-memory limiter that resets on restart.
- `V4` can silently revoke a real driver's profile access (see Blocking Prerequisites item 3).
- One more security-relevant behavior now depends on correct proxy configuration (`X-PIOS-Client-IP` must be replaced, not forwarded). A misconfigured proxy degrades the limiter to "one bucket for all traffic", which fails *closed* (over-limiting), not open — but it is still a new operational dependency.

---

## Blocking Prerequisites

1. **Hypothesis registration (Product Owner).** No H13 exists. Every ADR from `ADR-042` onward that relied on the 2026-08-01 addendum registered its hypothesis *before* the Sprint. This Sprint did not. Either the Product Owner registers one retroactively, explicitly labelled as retroactive, or this work is accepted on a different, explicitly stated basis. **Not performed by this ADR.**
2. **`ADR-055` amendment (Architect).** `ADR-055` Decision 3's text and `IdentityController`'s own KDoc both still say credential-less identity creation was removed. That is now false. A narrow amendment pointer must be added to `ADR-055` per `ADR-015`, and the controller KDoc corrected by the developer role (docs are amended by this session; the KDoc is code and is not).
3. **Production duplicate-claim count, before `V4` is applied.** Nobody has checked how many rows `V4` would revoke in production. Required check, run read-only against production **before** migrating:
   ```sql
   SELECT driver_id, count(*) FROM identities
   WHERE driver_id IS NOT NULL GROUP BY driver_id HAVING count(*) > 1;
   ```
   If this returns any row, a human must decide per-driver which identity keeps the profile, before `V4` decides it by `created_at`. `ADR-047` (Destructive Action Authorization) is the governing precedent for making that a human decision rather than a migration's.

---

## Related ADRs

- [ADR-038](ADR-038-Identity-Module-Bounded-Context-Foundation.md) — the Identity aggregate and its separation from `network-management`'s `Person`.
- [ADR-039](ADR-039-Identity-Driver-Association.md) — the association this ADR makes one-to-one and enforces.
- [ADR-055](ADR-055-Session-Authentication-and-Password-Credential.md) — the decision that deleted credential-less creation; narrowed, not reversed, by Decision 4 above.
- [ADR-070](ADR-070-Channel-1-Discovery-Matching-and-Post-Ride-Relationship-Formation.md) — the stranger-passenger flow this identity model serves; its Option B relationship formation is unchanged by this ADR.
- [ADR-047](ADR-047-Destructive-Action-Authorization.md) — applied to `V4`'s revocation step.
- [ADR-005](ADR-005-Data-Ownership.md) / [ADR-019](ADR-019-Conceptual-Data-Ownership.md) / [ADR-037](ADR-037-Network-Management-Module-Bounded-Context-Extension.md) — reference-not-ownership, preserved by in-place upgrade.

## References

Read for this ADR (working tree, 2026-09-16, uncommitted):

- `backend/identity/src/main/kotlin/com/pios/identity/api/IdentityController.kt`
- `backend/identity/src/main/kotlin/com/pios/identity/application/CreateGuestIdentityApplicationService.kt`
- `backend/identity/src/main/kotlin/com/pios/identity/application/UpgradeGuestIdentityApplicationService.kt`
- `backend/identity/src/main/kotlin/com/pios/identity/application/GuestIdentityRateLimiter.kt`
- `backend/identity/src/main/kotlin/com/pios/identity/application/SessionTokenIssuer.kt`
- `backend/identity/src/main/kotlin/com/pios/identity/api/SessionTokenVerifier.kt`
- `backend/identity/src/main/kotlin/com/pios/identity/domain/Identity.kt`
- `backend/identity/src/main/resources/db/migration/identity/V4__unique_driver_association.sql`
- `backend/driver-management/src/main/kotlin/com/pios/drivermanagement/api/DriverController.kt`
- `backend/driver-management/src/main/kotlin/com/pios/drivermanagement/api/SessionTokenVerifier.kt`
- `docs/PIOS_PRODUCT_HYPOTHESES.md` (H1–H12; no H13)

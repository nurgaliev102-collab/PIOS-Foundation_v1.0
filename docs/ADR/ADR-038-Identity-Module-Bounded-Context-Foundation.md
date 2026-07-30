# ADR-038: Identity Module — Bounded Context Foundation

## Status

Proposed

## Note on sequencing

This ADR is written *before* the `identity` module is scaffolded, deliberately — `MODULE_STRUCTURE.md` Section 8's Extension Strategy states a new module requires an ADR "before that module may be scaffolded," and `ADR-037` (§"This ADR is the missing authorization... before that module may be scaffolded") is the direct precedent this ADR follows. The task that produced this ADR originally sequenced documentation last (Этап 7); this ADR moved itself to first, since implementing five stages of a new bounded context and only then writing the ADR justifying it inverts `ADR-006` (Documentation-Driven Development) and the project's own established order. Nothing below was implemented before this document existed.

## Context

`docs/PIOS_IDENTITY_ARCHITECTURE_RESEARCH.md` (this same working session) researched modern registration/auth/contact-discovery approaches and recommended treating identity as a new, dedicated concept rather than folding it into an existing module. Separately, that same research identified `CURRENT_DRIVER_ID` (`frontend/src/pages/DriverHome/currentDriver.ts`) — a hardcoded constant — as a structural blocker: the application cannot represent more than one real driver, which is not a polish issue but a ceiling on the pilot itself growing past a single driver.

This ADR authorizes the minimum architectural foundation to remove that ceiling and to give the previously-researched identity model a real home — without implementing any of the user-facing mechanisms (SMS, OTP, passkeys, OAuth, Contact Picker) that research catalogued. Those remain explicitly out of scope here, per the task's own instruction.

## Self-critique of the prior research report

The task instructing this ADR explicitly required critical re-evaluation of `PIOS_IDENTITY_ARCHITECTURE_RESEARCH.md`, not treating it as settled. Two points revised here, one confirmed:

1. **Revised — Identity vs. Person is a sharper distinction than the research report drew.** The report described `Identity → Person → Profile` as a layered stack without fully explaining *why* they're not the same concept. Having now read `network-management`'s actual code (not just its ADR), `Person` already carries an optional `phone: String?` field (`domain/Person.kt`) — so the naive reading of the research report ("Identity is the thing that owns a phone number") would duplicate exactly what `Person` already has. The distinction that actually holds up: `Person` (ADR-037) is about *network/relationship* identity — who someone is to other people in the graph. `Identity` (this ADR) is about *authentication* — how a session proves it's acting as a given human, across devices, over time. `Person.phone` and a future `Identity.phone` are not the same field duplicated by accident — one is a network-facing attribute (may be unverified, may be someone else's number entered on their behalf, `network-management`'s own Sprint 7A scope never required verification), the other is a verified credential anchor with security properties `Person` was never meant to carry. Keeping them separate, with `Person` referencing `Identity` by id once that wiring is ever authorized, respects Domain Isolation (`ADR-009`) better than merging them would — authentication concerns (devices, sessions, credential revocation) have no reason to live inside a module whose entire declared purpose is Connection/Invitation.
2. **Revised — the research report implicitly assumed SMS OTP's request/response shape when describing `Verification`.** Building the actual schema surfaced a cleaner requirement: `VerificationMethod` needs to be a first-class discriminator (`SMS_OTP`, `SILENT_NETWORK_AUTH`, `TELEGRAM`, …) from day one, not retrofitted later, since the report itself recommended more than one method as realistic depending on market. Reflected in the domain shapes below — as unused, unpersisted types, since no verification is implemented.
3. **Confirmed, not revised — new bounded context, not an extension.** Re-examined whether `Identity` could instead live inside `network-management` (same rationale ADR-037 itself weighed for `Connection`). Rejected for the same reason ADR-037 rejected folding `Person` into `Passenger Experience`: `network-management`'s declared scope (`MODULE_STRUCTURE.md`) is Person/Profile/Connection/Invitation, not credentials or sessions — adding those would grow that module's responsibility into a domain it was never scoped for, the same "one module spans more than one domain" problem ADR-037 itself flags as forbidden.

## Problem

Does PIOS's already-ratified module structure permit adding a tenth module, `identity`, owning a phone-anchored `Identity` and the *shape* of future credentials/devices/sessions/verification — without altering any of the nine already-ratified modules' own ownership, and without implementing any authentication mechanism itself?

## Decision

`identity` is added as a new module, independently deployable per `ADR-023`'s per-module service style, following the same "additive only, zero integration" precedent `ADR-037` already established for `network-management`. It persists in its own database (`pios_identity`), consistent with `ADR-005`/`ADR-009`.

**Scope, explicitly bounded:**

- Owns exactly one persisted capability today: Create/Retrieve `Identity` — an id and an optional, unverified phone string. No verification, no proof of ownership, no SMS is sent or checked. This mirrors `Person`'s own current honesty about scope: `network-management`'s `Person.phone` is optional and unverified today too, and its own KDoc says so directly ("requiring it here would invent a validation rule this sprint was not asked to enforce").
- Documents, as compiled Kotlin types with no application service and no controller wiring, the future shape of `Credential`, `Device`, `VerificationMethod`, `VerificationChallenge`, and `Session` — present in the domain package so the eventual implementation has a real target to extend, absent from persistence (no Flyway table) so nothing is speculatively committed to schema before it's needed.
- No new production dependency from any existing module onto `identity`, and none from `identity` onto any existing module — the same "zero integration" boundary `ADR-037` drew for `network-management`'s Sprint 7A.

**Frontend counterpart, in the same spirit:** the hardcoded `CURRENT_DRIVER_ID` is replaced by a locally-persisted driver identity (`localDriverIdentity.ts`, mirroring the already-existing `localPassengerIdentity.ts` pattern) accessed through a new `IdentityProvider` interface — today backed entirely by browser storage, not by the new backend module, since the backend module has no verification to actually authenticate anyone yet. This is the explicit "adapter, not full integration" the originating task asked for when full integration isn't yet possible.

## Constraints

- No change to any of the nine already-ratified modules' owned capabilities, boundaries, or persisted schema.
- No new cross-module dependency, synchronous or event-based — `identity` is additive only, same as `network-management` was.
- No SMS, OTP, passkey, WebAuthn, OAuth (Apple/Google/Telegram login), or Contact Picker implementation — explicitly excluded by the task this ADR responds to, not merely deferred by omission.
- No existing API changed. `driver-management`'s `GET /v1/drivers/:id` contract is untouched; only how the frontend decides *which* `:id` to call changes.

## Consequences

- A tenth entry is added to `MODULE_STRUCTURE.md` Section 3 and to `settings.gradle.kts` — mechanical and additive, not a rewrite.
- `CURRENT_DRIVER_ID`'s hardcoding is removed. A device without a locally-saved driver identity now sees a one-time "введите код водителя" prompt before reaching Driver Home — a real, visible change from today's zero-prompt experience, disclosed here rather than claimed away. Once saved, behavior is identical to today.
- Real phone-based registration, login, and cross-device session recovery remain entirely unimplemented after this ADR — this document authorizes only the architectural seam, not the feature. A future ADR is required before `identity` gains a second real capability (issuing an actual verified `Credential`), the same gate `ADR-037` itself named for `network-management`'s next step.
- `Person` and `Identity` will eventually need to reference each other. That wiring is **not authorized here** — same posture `ADR-037` took toward `network-management` → routing integration: reserved for its own future ADR, once there's a concrete reason (a real login flow) to need it.

## Traceability

| Source | Relationship |
| --- | --- |
| `docs/PIOS_IDENTITY_ARCHITECTURE_RESEARCH.md` | Motivates this ADR; two of its claims revised above, one confirmed |
| `docs/ADR/ADR-037-Network-Management-Module-Bounded-Context-Extension.md` | Direct structural precedent — zero-integration new module, ADR-before-scaffolding sequencing |
| `docs/MODULE_STRUCTURE.md` Section 8 | Requires this ADR before the module may be scaffolded |
| `ADR-009` (Domain Isolation) | Basis for keeping `Identity` (authentication) and `Person` (network relationship) as distinct concerns |
| `ADR-005`, `ADR-023`, `ADR-026` | Database ownership, independent-service style — applied, not modified |
| `frontend/src/persistence/localPassengerIdentity.ts` | Existing pattern this ADR's frontend counterpart (`localDriverIdentity.ts`) mirrors |

## Files Changed

This ADR (`docs/ADR/ADR-038-Identity-Module-Bounded-Context-Foundation.md`, new). Implementation (new `backend/identity` module, `settings.gradle.kts` entry, frontend `IdentityProvider`/`localDriverIdentity.ts`, `DriverHome.tsx` refactor) follows in the same work session, per this ADR — listed in full in the accompanying implementation report, not repeated here. `docs/MODULE_STRUCTURE.md` Section 3 should receive a corresponding "Identity Module" entry as a follow-up documentation update, per the same convention `ADR-037` itself used (not performed automatically by this ADR).

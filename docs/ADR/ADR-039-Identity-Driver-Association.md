# ADR-039: Identity-Driver Association — First Real User Lifecycle

## Status

Proposed

## Note on sequencing

Written before implementation, for the same reason `ADR-038` was: `ADR-038`'s own Consequences section already named this exact moment — *"A future ADR is required before `identity` gains a second real capability... the same gate `ADR-037` itself named for `network-management`'s next step."* Associating a `Driver` id with an `Identity` is that second capability. Nothing below was implemented before this document existed.

## Context

`ADR-038` removed `CURRENT_DRIVER_ID` and gave the app a real, multi-device-capable `Identity` — but replaced the hardcoded constant with a screen asking a person to type a driver code (`ILDAR001`). That is an honest admission of what existed at the time, not a product experience: a real user should never see or type an internal identifier. This ADR authorizes the next step — a first real user lifecycle (create an identity, create a driver profile under a human name, never surface an id) — while still explicitly not touching phone verification or any external provider, per the originating task's own scope.

## Decision

**`Identity` gains one new field and one new capability: an optional `driverId` reference and an endpoint to set it.** Modeled exactly like `phone` already is — a plain, unverified string reference, not a foreign key, not ownership. `driver-management`'s own schema, domain model, and API contract are **not changed at all**: it has no idea `identity` exists, the same one-directional "reference, not ownership" pattern `ADR-037` already established for `network-management`'s references to `Driver`/`Passenger` ids.

**The reference is orchestrated by the frontend, not by a server-to-server call.** `identity` does not call `driver-management` over HTTP, and `driver-management` does not call `identity`. The frontend, which already calls multiple backend modules directly (the same pattern `RideRequest.tsx` already uses for Order Management + Dispatch), creates the `Driver` via the already-existing `POST /v1/drivers`, then tells `identity` about the resulting id via the new endpoint. Neither backend module gains a new production dependency on the other — only the frontend gains a new orchestration step. This keeps `ADR-038`'s "zero integration" posture intact at the backend level while still delivering the product behavior this task asks for.

**The driver id itself is generated, not typed.** Where `ADR-038`'s interim screen asked a person to type `ILDAR001`, this ADR generates a `driverId` client-side (a UUID, the same generation approach `localPassengerIdentity.ts` already uses) and never shows it. A person now provides only a display name; the internal id becomes exactly what its own name says — internal.

**Passenger side is explicitly out of scope.** The originating task frames this as "создание профиля водителя" — the passenger's own local identity (`localPassengerIdentity.ts`) is untouched, remains local-only, and is not linked to `identity` here. Whether a passenger should also get a real `Identity` is a real future question, not decided or assumed by this ADR.

**Phone remains genuinely unset**, exactly as `ADR-038` left it. The interface gains a visible, honest "next step" signal (a disabled/upcoming phone-confirmation affordance) so the product *shows* where it's going without *building* it — no SMS, no OTP, no verification logic of any kind.

## Constraints

- No change to `driver-management`'s persisted schema, domain model, or API — `POST /v1/drivers`, `GET /v1/drivers/:id` are called exactly as they already exist.
- No SMS, OTP, passkeys, OAuth (Apple/Google/Telegram), Contact Picker, or push notification — same exclusion list `ADR-038` already stated, unchanged.
- No new production (non-orchestration) dependency between `identity` and `driver-management` in either direction.
- `network-management`'s `Person`/`Connection`/`Invitation` remain exactly as isolated as `ADR-037` and `ADR-038` already left them — this ADR does not touch that module or wire it to anything.

## Consequences

- The "введите код водителя" screen is retired. First run becomes: welcome → identity created (silently, no code shown) → "как вас зовут?" → driver profile created and linked → main app. Reopening the app restores the same identity+driver automatically from the device's own local pointer to a real, backend-issued `Identity` id — not from a fabricated, honor-system driver code.
- `persistence/localDriverOnboarding.ts` (the separate "has this driver seen the welcome screen" flag) is retired — redundant now that "does a stored identity+driver exist" already answers the same question, and keeping both would let them drift out of sync.
- `identity`'s own schema gains one nullable column (`driver_id`) — additive, no migration touches existing rows destructively.
- **Still not implemented after this ADR:** phone verification, any way to recover an identity on a second device (today's identity is strictly per-device — losing the device loses the identity, with no recovery path yet), and any linkage between a passenger's local identity and the `identity` module. Each remains its own future decision.

## Traceability

| Source | Relationship |
| --- | --- |
| `docs/ADR/ADR-038-Identity-Module-Bounded-Context-Foundation.md` | Predicted this exact next step and its own gating requirement |
| `docs/ADR/ADR-037-Network-Management-Module-Bounded-Context-Extension.md` | "Reference, not ownership" pattern, applied identically here |
| `frontend/src/persistence/localPassengerIdentity.ts` | Precedent for client-generated ids and local-only device identity |

## Files Changed

This ADR (new). Implementation (extending `backend/identity`'s domain/application/api/persistence, new Flyway migration, and the `DriverHome.tsx` onboarding rewrite) follows in the same session, listed in full in the accompanying report — not repeated here.

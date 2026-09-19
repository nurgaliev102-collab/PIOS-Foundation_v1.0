# ADR-085: Driver Vehicle Plate Visibility (D-11.C1 / D-11.C2)

## Status

**Accepted, 2026-09-19, by the Product Owner.** Architecture-only ratification — **no code has been written for this ADR.** This ADR covers only D-11.C1 (remove plate from the unauthenticated driver read) and D-11.C2 (no new passenger-facing plate path). D-11.C3 (vehicle make/model/colour on the assigned-ride screen) is a **separate** Product Owner decision, recorded in `docs/PIOS_D11_DECISION_LOCK.md`, and deliberately not authorized or scoped by this ADR — see Part 4.

**Proposed Date:** 2026-09-19. **Ratified Date:** 2026-09-19. **Author:** Architect role.

### The Product Owner decisions this ADR records

- **D-11.C1 — GO.** Remove `vehiclePlateNumber` from `GET /v1/drivers/{driverId}` when read unauthenticated/publicly. The driver's own authenticated self-view must remain capable of reading and editing the stored plate, unchanged.
- **D-11.C2 — NO-GO, for now.** No new passenger-facing plate endpoint or new plate-visibility model is introduced. No future rule for when a passenger may see the plate is inferred by this ADR.

---

## Context

### What already exists, verified

Vehicle fields (`vehicleMake`, `vehicleModel`, `vehicleColor`, `vehiclePlateNumber`, `vehicleSeatCount`) already exist on `Driver` (`backend/driver-management/src/main/kotlin/com/pios/drivermanagement/domain/Driver.kt`), backed by migration `V9__driver_vehicle.sql`. This corrects a stale premise recorded in `docs/DRIVER_IDENTITY_DESIGN_DECISION.md` (lines stating vehicle data "does not exist anywhere in the data model") — that statement predates `V9` and is factually false today. `DRIVER_IDENTITY_DESIGN_DECISION.md` §4's separate ban on numeric trust signals (rating, ride count, connection count) is **not** stale and is **not** touched by this ADR.

**The plate is exposed today, unauthenticated, on `GET /v1/drivers/{driverId}`** (`DriverController.kt`, `toResponse`) — the only endpoint of the five vehicle-touching driver endpoints with no `Authorization` requirement at all. This is not theoretical: the public invite-preview screen (`frontend/src/pages/PassengerLanding/invitationSource.ts`, `PassengerLanding.tsx`) fetches this endpoint with no credential and renders the plate to anyone who has ever received a driver's own `/i/{driverId}` share link.

`ADR-060` ("Order Query Authorization") is cited as the repository's own precedent that field-level redaction is a named, separate decision (`ADR-060`: *"no field-level redaction is introduced. Each of those is a separate decision..."*) — but `ADR-060` itself is scoped to `GET /v1/orders`/`GET /v1/proposals` and explicitly leaves `GET /v1/drivers` alone. `ADR-060` is **precedent for how this repository treats redaction decisions, not the governing authority for `DriverResponse`.** This ADR is that separate, explicit decision for the `Driver` aggregate's own response shape.

### A defect this ADR's implementation must not introduce

The driver's own vehicle-edit screen (`frontend/src/pages/DriverHome/DriverHome.tsx`) reads the driver's current vehicle fields from the **same unauthenticated** `GET /v1/drivers/{driverId}` endpoint, populates its edit form from that response, and on save sends all five vehicle fields together. `Driver.updateVehicle(...)` replaces all five fields unconditionally on every call. **If the plate is removed from that response without also ensuring the driver's own authenticated read still receives it, the driver's own stored plate will be silently erased the next time they edit any vehicle field** — the edit form's plate input would load empty, and the unconditional five-field save would persist that empty value. This is stated here as a **binding data-integrity requirement**, not an implementation nicety (Part 3).

---

## Decision

### Part 1 — D-11.C1: remove the plate from the unauthenticated read only

`DriverResponse`'s construction for the unauthenticated `GET /v1/drivers/{driverId}` path no longer includes `vehiclePlateNumber`. The plate remains present, unchanged, on:
- the owner-`Basic`-gated `GET /v1/drivers` list endpoint;
- the `Bearer`-gated write-response paths (`POST /v1/drivers/{id}/vehicle` and any other endpoint that requires `drv == driverId`).

Concretely, the response-mapping function becomes caller-aware (aware of whether the caller is the driver themselves, an owner, or an anonymous reader) rather than a single shared mapper producing one shape for everyone.

### Part 2 — D-11.C2: no new passenger-facing plate path, and no inferred future rule

No new endpoint, field, or visibility rule is introduced for a passenger to see a plate under any circumstance (invite-preview, assigned-ride, or otherwise). This ADR does not decide, imply, or reserve a position on whether a passenger may someday see a plate once a ride is assigned — that remains a fully open question for a future, separate Product Decision, and no default is assumed in its absence.

### Part 3 — Binding data-integrity acceptance criterion (D-11.C1)

**Implementation of D-11.C1 must satisfy, and be tested against:** a driver's own authenticated read of their own vehicle data (however it is served — either by the existing endpoint recognizing the caller's own valid `Bearer` token and including the plate for that caller, or by a dedicated authenticated self-read) continues to return the plate unchanged; and a driver who edits any single vehicle field (make, model, colour, seat count) after this change has shipped does not have their previously-stored plate silently cleared. This is an explicit future implementation acceptance criterion, not merely a recommendation.

### Part 4 — D-11.C3 is out of scope for this ADR

Showing vehicle make/model/colour on the assigned-ride passenger screen (D-11.C3) is a **separate** Product Owner decision. It does not require any change to this ADR's scope, since the backend already returns make/model/colour on paths this ADR does not touch. **D-11.C1 and D-11.C3 must not be implemented in the same commit** — C1 is disclosure remediation; C3 is a new passenger-facing product surface; keeping them in separate commits keeps any future plate-visibility incident unambiguously attributable to one change or the other. This non-bundling rule is recorded in `docs/PIOS_D11_DECISION_LOCK.md` and repeated here so an implementer reading only this ADR still sees it.

### Part 5 — What this ADR does not authorize

Any new `Vehicle` aggregate, `vehicles` table, or change to how vehicle data is stored — this is a visibility question only, not a data-model change. Any new vehicle field (VIN, photo, year, verification status). Any rating, score, count, tenure, or other numeric trust signal (`DRIVER_IDENTITY_DESIGN_DECISION.md` §4 remains fully in force, untouched). Any change to phone-disclosure rules (`ADR-059` Option C — PIOS discloses no participant's phone number to any other participant, on any surface — is not reopened). Any redesign of driver identity generally. Any general authentication requirement added to `GET /v1/drivers/{driverId}` itself — the invite-preview flow depends on this endpoint remaining reachable without credentials; only the plate field is removed from its unauthenticated shape. Any retroactive scrubbing of plates already disclosed prior to this change — nothing here can undo that.

---

## Alternatives Considered

- **Redesigning `Driver` into a separate `Vehicle` aggregate as part of this change.** Rejected — out of scope; this is a visibility/redaction decision, not a data-model change, and bundling the two would make a simple, urgent remediation into a much larger, slower change.
- **Authenticating `GET /v1/drivers/{driverId}` generally, instead of redacting one field.** Rejected — the invite-preview flow (`invitationSource.ts`) structurally depends on this endpoint being reachable before any identity exists; a blanket auth requirement would break that flow, which is unrelated to the plate-disclosure problem.
- **Deciding D-11.C2 now (e.g. "plate visible only once a ride is assigned").** Rejected for this ADR — the reconciliation found no authenticated, assignment-scoped driver-detail endpoint exists today; answering C2 as anything other than NO-GO would require building new infrastructure this Product Owner decision does not yet authorize. Left explicitly open rather than answered by default.

---

## Consequences

### Positive

- Closes a live, credential-free disclosure of a real person's vehicle registration number to anyone who has ever received their invite link.
- No data-model change, no migration, no new aggregate — the smallest change that closes the disclosure.
- The binding data-integrity criterion (Part 3) prevents the change from silently corrupting a driver's own stored data.

### Negative

- Plates already disclosed before this change cannot be un-disclosed — this ADR closes the ongoing exposure, not the historical one.
- `DriverResponse`'s mapping function becomes caller-aware, adding a small amount of conditional complexity to a previously uniform mapper.
- D-11.C2 remains genuinely unanswered — a passenger with a real, practical need to identify a car by its plate (as opposed to make/model/colour, covered separately by D-11.C3) has no path today, and this ADR does not create one.

---

## Related ADRs

- [ADR-060: Order Query Authorization](ADR-060-Order-Query-Authorization.md) — cited as precedent for field-level redaction as a named, separate decision; not the governing authority for `DriverResponse`, which this ADR provides.
- [ADR-059: Ride Contact Disclosure](ADR-059-Ride-Contact-Disclosure.md) — phone-disclosure Option C, unrelated and untouched.
- `docs/DRIVER_IDENTITY_DESIGN_DECISION.md` §4 — the numeric-trust-signal ban, untouched.

## References

- `docs/PIOS_D11_DECISION_LOCK.md` — the ratified D-11 decision this ADR implements.
- `backend/driver-management/src/main/kotlin/com/pios/drivermanagement/domain/Driver.kt`
- `backend/driver-management/src/main/kotlin/com/pios/drivermanagement/api/DriverController.kt`
- `backend/driver-management/src/main/kotlin/com/pios/drivermanagement/api/DriverResponse.kt`
- `backend/driver-management/src/main/resources/db/migration/drivermanagement/V9__driver_vehicle.sql`
- `frontend/src/pages/PassengerLanding/invitationSource.ts`, `PassengerLanding.tsx`
- `frontend/src/pages/DriverHome/DriverHome.tsx`

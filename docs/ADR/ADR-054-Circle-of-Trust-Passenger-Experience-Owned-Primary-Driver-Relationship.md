# ADR-054: Circle of Trust — Passenger-Experience-Owned Primary Driver Relationship

## Status

**Accepted for implementation.**

**Decision Date:** 2026-08-08

**Basis.** Sprint "My Business + Circle of Trust", started on **hypothesis H7** (`PIOS_PRODUCT_HYPOTHESES.md` §H7, registered 2026-08-08 with «Как проверим» and «Критерий успеха» filled), under the second admissible Sprint basis ratified in `PIOS_PRODUCT_EVIDENCE.md`, "Дополнение 2026-08-01", and recorded there as the **fourth application** (line 149). The evidence log is still empty on this date; this Sprint **produces** evidence, it does not consume any. No `E-NNN` entry is claimed.

**Product authority.** `PRODUCT_DECISION_CIRCLE_OF_TRUST.md` (Product Owner, 2026-08-08) resolves four of the eight open questions in `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 8. This ADR is the architectural half of that decision and invents no business rule of its own (ADR-002; `CLAUDE.md`, "Never Invent Business Rules").

Amends no existing ADR. Creates no cross-module contract, so `INTERFACE_CONTRACTS.md` §5's five justified relationships are unchanged.

## Context

### Why this is an ADR and not an additive field

This repository's own precedent for treating a change as additive rather than architectural is `Order.destination` (Sprint 3B; `Order.kt` lines 38–45), reused by `passengerName`, `pickupAddress` and `Proposal.statedPrice`: an optional attribute with no competing claimant, no new invariant, and no new cross-module relationship. That precedent **does not** cover this change, for three specific reasons:

1. It introduces a genuinely new **invariant** — at most one primary per passenger — where the concept previously had none.
2. It gives `Connection` **mutable state**, which its own KDoc currently and deliberately denies it: *"Deliberately minimal: no status, no type, nothing beyond the pairing and when it was recorded"* (`Connection.kt` lines 9–11).
3. It resolves, at the architectural level, questions `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 8 explicitly reserved for a Product Owner decision, and Part 3 of that document records that Personal Client Relationship has **no ratified lifecycle at all** ("Creation — no command, event, or description of how one comes to exist"). Adding a passenger-changeable designation is the first lifecycle-bearing act ever performed on this concept.

`MODULE_STRUCTURE.md` §8 therefore requires this document to exist before the change, not after it.

### What exists in the repository today (read, not remembered)

**Passenger Experience already owns a passenger↔driver pairing, and already owns it exclusively.**

- `backend/passenger-experience/src/main/resources/db/migration/passengerexperience/V1__create_connections.sql` lines 10–16:
  ```
  CREATE TABLE connections (
      id TEXT PRIMARY KEY,
      driver_id TEXT NOT NULL,
      passenger_reference TEXT NOT NULL,
      created_at TIMESTAMPTZ NOT NULL,
      UNIQUE (driver_id, passenger_reference)
  );
  ```
  `UNIQUE (driver_id, passenger_reference)` is a *pair* uniqueness constraint, not a cap: nothing today limits how many drivers one `passenger_reference` may appear against. **A many-to-many circle is already the schema's shape.** The migration's own comment states why the constraint exists — idempotent re-opening of an invitation link "at the storage level, not just in application code."
- `ConnectionRepository.kt` lines 13–17 exposes exactly three operations: `save`, `findByDriverAndPassenger`, `findByDriver`. **There is no passenger-side query**, and `RetrieveConnectionsForDriverHandler` has no passenger-side counterpart.
- `ConnectionController.kt` lines 42–65 serves `POST /v1/connections` (201 first time, 200 on repeat — idempotent by explicit requirement) and `GET /v1/connections?driverId=`. Nothing else.
- `DriverReference.kt` lines 3–17 records the reference-not-ownership discipline in the code itself: *"Passenger Experience does not own driver data -- this value carries no availability, standing, or other Driver Management-owned information, only the plain id."*
- `INTERFACE_CONTRACTS.md` lines 50–51 already assign this module *"Passenger and corporate customer representation, **including Personal Client relationships**"* and *"its own side of Personal Client Relationship information."* No new ownership is claimed by this ADR; existing ownership is exercised.

**Creation is already passenger-initiated, in fact as well as in principle.** `frontend/src/pages/PassengerLanding/PassengerLanding.tsx` lines 136–145 issues the `POST /v1/connections` only after the passenger has confirmed their own identity on that driver's invitation link — matching `PRODUCT_DECISION_CIRCLE_OF_TRUST.md` Decision 3 ("opening a driver's invitation link and confirming"). Nothing anywhere creates a Connection from a completed ride: `DispatchAssignmentApplicationService` and `ProposalApplicationService` have no relationship-writing side effect of any kind, and `Order` carries no driver reference at all.

**The other `Connection` is a different concept in a different module, and is already fenced off.** `network-management` owns a Person↔Person referral graph (ADR-037) also served at `/v1/connections`. `frontend/vite.config.ts` lines 19–22 records the separation explicitly: *"`network-management`'s own `/v1/connections` (port 8085) is intentionally not routed here; it is excluded from the pilot flow (ADR-037), so there is no collision with passenger-experience's `/v1/connections` below."* This ADR touches `network-management` in no way, and does not merge, bridge, or relate the two concepts.

**Intra-module foreign keys have precedent in this repository.** `network-management/.../V1__initial_schema.sql` lines 19, 26–27 and 34 use `REFERENCES persons (id)` within that module's own database. The reference-not-ownership rule (ADR-005, ADR-019) prohibits a foreign key **across** module boundaries; it has never prohibited one inside a module's own schema, and ADR-037's own schema is the standing example.

### The conflicting evidence that had to be checked, and what it actually says

- `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 2 records **[RATIFIED absence]** of any Dispatch role in this relationship, and Part 4 records **[RATIFIED absence]** of any Dispatch right to override or act on it. `ADR-034` line 32 lists *"The Passenger or Corporate Customer aggregate or representation (Passenger Experience — no contract to Dispatch exists in INTERFACE_CONTRACTS.md Section 5)"* under "Dispatch does NOT own"; line 59 lists *"Passenger or Corporate Customer profile data"* under **forbidden inputs** to the assignment decision; line 49 classifies Personal Client Relationship only as a *future candidate input*, "not ratified by any approved document."
- Counterweight, disclosed rather than omitted: `PRODUCT_DECISION_PERSONAL_NETWORK_MVP_TRANSITION.md` line 25 does authorize, at product level, *"Order routing that checks for an existing personal relationship before offering the order to the general queue — a single, explicit, transparent rule."* `Connection.kt` line 8 anticipates the same thing in its KDoc ("lets Ride Request later offer a new order to this driver first, before any general queue"). **Neither is implemented, and neither is implemented here.** Today `RideRequest.tsx` proposes to exactly the one `driverId` from the `/i/:driverCode/request` route param — a client-side choice, never relationship data reaching Dispatch. Turning line 25 into a Dispatch-side rule would require ratifying ADR-034 line 49's candidate input *and* creating the Passenger Experience → Dispatch contract that `INTERFACE_CONTRACTS.md` §5 does not contain. This ADR does neither and forecloses neither (Part 5).

## Problem

Where does a passenger's *circle of trust* — several recognized drivers, at most one of them marked **primary**, changeable only by the passenger's own explicit action — live; how is the single-primary invariant enforced; and how is Dispatch's assignment decision kept demonstrably free of it?

## Decision

### Part 1: Placement — Passenger Experience, exclusively, extending the existing `Connection` concept

The circle of trust is Passenger Experience's own information, stored in `pios_passenger_experience`, and is modeled as an extension of the **existing** `Connection` concept rather than as a new one.

- **No new module and no new bounded context.** `INTERFACE_CONTRACTS.md` lines 50–51 already assign this module Personal Client relationship ownership; a new context would fragment ownership the ratified documents already place in one module.
- **No `network-management` involvement.** That module's `Connection` is a Person↔Person referral edge (ADR-037), deals in abstract Persons rather than Driver/Passenger identifiers, and is excluded from the pilot flow (`vite.config.ts` lines 19–22). It remains isolated: nothing calls it and it calls nothing.
- **No new service and no new queue.** No RabbitMQ topology, no event, no outbox record, no consumer, and no new process are created. `EVENT_CATALOG.md`'s catalogued events are untouched and every existing payload stays at its current version.
- **`driver-management` is untouched.** `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 5 records the relationship as *jointly* owned, and Part 5's own "documentation asymmetry" note records that Driver Management's side is never restated outside the data-model documents. This ADR builds **only Passenger Experience's own side**, which is the side every document restates by name. Driver Management's side remains exactly as unbuilt as it is today, and this ADR neither builds it nor denies it.
- **Reference-not-ownership is preserved unchanged.** `driver_id` remains a plain, unverified string identifier (`DriverReference.kt` lines 3–17). No driver data is copied, no foreign key crosses a module boundary, and no server-to-server call is added to any write path (ADR-005, ADR-019, ADR-027).

### Part 2: Persistence shape — a separate `primary_connections` table, not a column on `connections`

**Decision: a new table.** Migration `V2__create_primary_connections.sql` (V1 is the highest existing version in this module):

```sql
ALTER TABLE connections
    ADD CONSTRAINT connections_passenger_reference_id_key UNIQUE (passenger_reference, id);

CREATE TABLE primary_connections (
    passenger_reference TEXT PRIMARY KEY,
    connection_id       TEXT NOT NULL,
    designated_at       TIMESTAMPTZ NOT NULL,
    CONSTRAINT primary_connections_connection_fkey
        FOREIGN KEY (passenger_reference, connection_id)
        REFERENCES connections (passenger_reference, id)
        ON DELETE CASCADE
);
```

Four properties are load-bearing, and each is the reason this shape was chosen over a flag column:

1. **The single-primary invariant becomes a primary key.** "At most one primary per passenger" is *literally* the definition of `PRIMARY KEY (passenger_reference)`. The column alternative (`is_primary BOOLEAN` plus `CREATE UNIQUE INDEX ... ON connections (passenger_reference) WHERE is_primary`) expresses the same invariant less directly, and carries a concrete PostgreSQL hazard: a partial unique **index** cannot be declared `DEFERRABLE`, so a single-statement primary swap can raise a spurious unique violation depending on row update order, forcing a clear-then-set two-statement sequence to be correct. With this table, setting a primary is one `INSERT ... ON CONFLICT (passenger_reference) DO UPDATE` and there is nothing to swap. This follows the discipline `V1__create_connections.sql`'s own comment already states for this exact table — enforce it *"at the storage level, not just in application code."*
2. **The composite foreign key makes "the primary must be a driver in *this* passenger's own circle" structural.** Because it references `(passenger_reference, id)` rather than `id` alone, the database itself rejects a designation pointing at another passenger's connection. A boolean column expresses this weakly (the flag simply sits on some row) and a single-column FK expresses it not at all. This is the same structural-over-procedural preference ADR-042 R2 applied to the no-pay-to-win guarantee.
3. **`ON DELETE CASCADE` makes removal correct by construction.** `PRODUCT_DECISION_CIRCLE_OF_TRUST.md` ratifies that a passenger may remove *any* driver, primary or not. Deleting the `connections` row removes the designation automatically; no application-layer cleanup step exists to be forgotten. Intra-module FK precedent: `network-management/.../V1__initial_schema.sql` lines 19, 26–27, 34.
4. **The added `UNIQUE (passenger_reference, id)` does double duty and is not gratuitous.** It is required as the composite FK's target, *and* it is the index that makes the new `findByPassenger` query efficient. The existing `UNIQUE (driver_id, passenger_reference)` cannot serve a `WHERE passenger_reference = ?` lookup, because `driver_id` leads it. No separate single-column index is needed.

**The separation of lifecycles is the deeper reason.** A `connections` row records an immutable historical fact — *this passenger reached PIOS through this driver's link*, written once, never updated (`PostgreSQLConnectionRepository.kt` lines 24–32 contain an `INSERT` and no `UPDATE` at all). A primary designation is a mutable, passenger-changeable preference with its own `designated_at`. Putting a mutable preference column on a table whose every existing row is immutable-by-construction mixes two lifecycles in one place; keeping them apart lets `Connection` keep the "nothing beyond the pairing and when it was recorded" property its own KDoc claims for it (`Connection.kt` lines 9–11), which would otherwise have to be edited into an untruth.

**`connections` itself is otherwise unchanged.** No column is added to it, no column is dropped, no existing constraint is altered, and every row written before this migration satisfies the new schema without a backfill or a default.

### Part 3: Dispatch is untouched, and the neutrality is structural rather than procedural

`ADR-034` Part 1 (line 32) and Part 2's forbidden-inputs list (line 59) stand **unamended and reinforced**. Concretely, and checkable in review:

- **No new cross-module contract.** `INTERFACE_CONTRACTS.md` §5's five justified relationships are unchanged. There is still no Passenger Experience → Dispatch contract of any kind, in either direction.
- **Dispatch's schema, code, API and payloads are untouched.** `dispatch`'s highest migration stays `V9`. No `Proposal`, `Assignment`, `AssignmentStatus`, availability-projection, outbox or event change is authorized by this ADR.
- **Nothing about primacy or circle membership ever crosses into Dispatch.** `POST /v1/proposals` continues to receive exactly what it receives today: a single, plain `driverId`. At the Dispatch boundary that identifier is **indistinguishable** from today's URL-param-sourced one; only the frontend's own source for it changes (a passenger-chosen member of their circle instead of only the route param). Dispatch cannot tell, and must not be told, whether the driver is primary, non-primary, or in any circle at all.
- **Assignment Policy (ADR-034 Part 3) gains no input.** The port still does not exist; when it does, nothing in this ADR is reachable from it, because no Dispatch-side field, projection, or contract carries the relationship.
- **Fair Opportunity Policy is unaffected.** `PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` §2 scopes that mechanism to NETWORK-scoped requests, and this Sprint's flow is DIRECT throughout — one passenger proposing to one named driver. No shared candidate pool is consulted, filtered, ranked, or reordered.

### Part 4: The passenger-side read and write surface

Within `passenger-experience` only, and additive throughout:

- **A passenger-side query.** `ConnectionRepository` gains a `findByPassenger`-shaped operation, the counterpart `RetrieveConnectionsForDriverHandler` never had. The existing three operations keep their current signatures.
- **The list of a passenger's own circle, with the primary flagged**, plus **set primary** and **remove a connection**. `POST /v1/connections/{connectionId}/primary` and `DELETE /v1/connections/{connectionId}` follow this codebase's existing action-on-resource convention (`POST /v1/proposals/{proposalId}/accept`); the passenger-side list is served as a sibling filter on the existing collection (`GET /v1/connections?passengerReference=...`). Exact path and payload shapes are the developer's to finalize *within* these constraints, not to re-derive.
- **Backward compatibility is mandatory, not best-effort.** `POST /v1/connections`'s request and its 201/200 idempotency semantics, and `GET /v1/connections?driverId=`'s response shape, are unchanged. `PassengerLanding.tsx` lines 136–145 and `DriverHome.tsx` lines 366–380 must keep working unmodified; under ADR-026 callers deploy independently, and `SubmitOrderRequest.kt` lines 10–15 record that this exact class of mistake has already broken this exact kind of caller once.
- **The driver-facing response gains nothing.** `GET /v1/connections?driverId=` must **not** expose whether the driver is anyone's primary. Whether a driver may ever learn this is a product question no ratified document answers, and inventing an answer here is exactly what `CLAUDE.md` forbids. It is recorded in Part 6 as open.
- **Setting a primary is a designation, never a creation.** The set-primary operation may only designate an existing member of the passenger's own circle; it may never create a `Connection` as a side effect. The composite FK enforces this at the storage level, so the rule survives an application-layer mistake.

### Part 5: What this ADR does not decide

- **No Team, Fleet, crew, or delegation mechanism of any kind.** `PRODUCT_DECISION_CIRCLE_OF_TRUST.md` Decision 4 explicitly declines to resolve `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 8's delegation question, and that document itself notes Fleet *"has no ratified domain owner yet."* `PRODUCT_DECISION_ENTREPRENEUR_MODEL.md`'s framing of each driver as an independent, isolated entrepreneur makes this a real future product decision, not an implementation detail. No fallback-to-another-driver behavior, no group, no shared client list.
- **No change to Dispatch's assignment decision**, no Assignment Policy, no ranking, no scoring, no priority, no queue reordering (Part 3).
- **No new service and no new queue** (Part 1).
- **No relationship-based order routing.** `PRODUCT_DECISION_PERSONAL_NETWORK_MVP_TRANSITION.md` line 25's "relationship first, else general queue" rule is neither implemented nor foreclosed here; implementing it inside Dispatch would first require ratifying `ADR-034` line 49's candidate input and creating a contract `INTERFACE_CONTRACTS.md` §5 does not contain.
- **No lifecycle beyond creation, designation and removal.** No confirmation by the driver, no suspension, no expiry from inactivity — `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 3 leaves all of these `[REQUIRES VALIDATION]` and `PRODUCT_DECISION_CIRCLE_OF_TRUST.md` deliberately leaves them open.
- **No cap on circle size.** `PRODUCT_DECISION_CIRCLE_OF_TRUST.md` Decision 1 states "No cap is set for the pilot"; inventing one would be inventing a business rule.
- **No `driver-management` counterpart**, no aggregate promotion of `Connection`, and no change to `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Parts 1–7.

### Part 6: Known limitation, recorded rather than glossed

**PIOS has no authorization layer, so "only the passenger's own explicit action" is a client-side convention, not a backend-enforced guarantee.** `ConnectionController.kt` has no credential check of any kind; `OwnerCredentialGate` (ADR-044) guards `GET /v1/health` and nothing else. Any caller who knows a `passengerReference` can today create a Connection or, after this change, set a primary. This does not block the pilot — the same limitation already governs every endpoint in the system, and ADR-042 R8 recorded it in the same terms — but it must not be described to anyone as an enforced rule. `PRODUCT_DECISION_CIRCLE_OF_TRUST.md`'s "a driver cannot make themselves anyone's primary" is honored by *which client calls the endpoint*, and by nothing else, until an authorization decision exists. Naming a real one is a separate, unstarted decision; this ADR does not name it or reserve a number for it.

Relatedly, and disclosed for the same reason: `PassengerLanding.tsx` line 131 marks the connection call *"deliberately not blocking or surfaced to the passenger on failure."* Circle membership can therefore silently fail to be recorded. Acceptable for onboarding bookkeeping; worth re-examining once the circle is a screen the passenger actually reads.

## Alternatives Considered

- **A boolean `is_primary` column on `connections`, with a partial unique index.** Rejected on the four grounds in Part 2 — chiefly that it expresses "at most one primary per passenger" as a constraint plus discipline rather than as a key, cannot express "primary must belong to *this* passenger's circle" at all, and carries the non-deferrable-partial-index swap hazard. It is the smaller diff; it is the weaker guarantee.
- **A `primary_driver_id TEXT` column on a new passenger row.** Rejected: Passenger Experience has no passenger table (`PassengerReference` is a bare value; there is no `passengers` schema), so this would require inventing a passenger aggregate to hold one nullable field — a strictly larger change with a new aggregate nobody asked for, failing `ADR-035` Part 2's own test on every criterion.
- **Placing the circle in `network-management`.** Rejected: that module's `Connection` is a Person↔Person referral edge (ADR-037) in abstract Persons, not Driver/Passenger identifiers; it is deliberately excluded from the pilot flow (`vite.config.ts` lines 19–22); and `INTERFACE_CONTRACTS.md` lines 50–51 already place Personal Client relationships in Passenger Experience. Using it would require a new cross-module contract to obtain a capability the owning module can provide with none.
- **Placing "primary" in `driver-management`, as the joint owner's other side.** Rejected for this Sprint: the designation is made *by the passenger, about their own circle*, and every ratified document that restates this ownership by name restates Passenger Experience's side (`PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 5). A Driver Management side may still be built later; nothing here forecloses it.
- **Deriving "primary" from ride history — most recent, or most frequent, completed ride.** Rejected outright, and this is the rejection that matters most: `PRODUCT_DECISION_CIRCLE_OF_TRUST.md` ratifies that completing a ride with a non-primary trusted driver *never* changes who is primary and never creates membership, and `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 6 already distinguishes the relationship from ride history (*"not defined as, or derived from, an accumulated count or log of past rides"*). Part 2's stored, explicitly-designated row is what makes the prohibition structural: there is no derivation path to accidentally introduce, because there is a stored fact instead.
- **A Dispatch-side "prefer the primary driver" rule.** Rejected as out of scope and unratified — `ADR-034` line 49 classifies this input as a *candidate*, never ratified, and line 59 currently forbids Passenger Experience data as an assignment input outright. It would need its own ADR, its own `INTERFACE_CONTRACTS.md` contract, and a Product Decision resolving `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 8's last open question.
- **Treating this as an additive field under the `destination` precedent, with no ADR.** Rejected for the three reasons in Context: a new invariant, first mutable state on `Connection`, and the resolution of an explicitly-reserved Part 8 question.

## Consequences

### Positive

- A passenger can hold and manage several trusted drivers with exactly one primary, with **no new module, no new service, no new queue, no new event, no new cross-module contract, and no change to Dispatch or Order Management**.
- The single-primary invariant and the "primary must be a member of this passenger's own circle" invariant are both enforced by the database, not by application discipline — they survive a future refactor that forgets them.
- "Completing a ride never changes who is primary" is guaranteed structurally: Dispatch has no write path into `pios_passenger_experience` and no contract through which to acquire one, and no derivation-from-history code path exists to be introduced by accident.
- `ADR-034` Parts 1 and 2, `ADR-037`, `ADR-040`, `ADR-041`, `ADR-051`, `ADR-052` and `ADR-053` are all left unmodified. `network-management` and `identity` remain isolated.
- Backward compatibility by construction: every existing row, caller, endpoint, response shape and event payload is unaffected.

### Negative

- Passenger Experience gains its second table and its first intra-module foreign key, so a circle listing now reads two tables instead of one. Deliberate — the cost is one join, in exchange for the invariant being a key.
- `Connection` acquires an associated mutable designation while `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 1 still classifies Personal Client Relationship as a conceptual **entity**, not a ratified aggregate. This ADR does not promote it, but the gap between "an entity with no lifecycle" (Part 3) and a stored, changeable designation is real and is recorded here rather than left implicit.
- "Only the passenger changes it" is unenforced at the API boundary (Part 6). Honest limitation, not a claim.
- The circle is not visible to the driver, and the question of whether it should be is left open (Part 4) — a driver may reasonably ask, and there is no ratified answer to give them yet.

## Evolution Path

None of the following is resolved here; each requires its own Product Decision, its own ADR, or both:

1. **Team / Fleet delegation and driver-side fallback** — `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 8's delegation question, explicitly left open by `PRODUCT_DECISION_CIRCLE_OF_TRUST.md` Decision 4.
2. **Whether the relationship ever influences Dispatch's decision**, and how much relative to fair access — `ADR-034` line 49, `PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` §6, Part 8's final question.
3. **Driver-side visibility of primacy**, and Driver Management's own side of the joint ownership.
4. **Relationship lifecycle beyond creation/designation/removal** — mutual confirmation, suspension, expiry from inactivity.
5. **An authorization model** that would make Part 6's limitation an enforced rule.

Any change to Part 1 (placement), Part 2 (persistence shape) or Part 3 (Dispatch neutrality) requires a decision that explicitly supersedes this one, consistent with ADR-015.

## What This ADR Does Not Authorize

Any change to `dispatch`, `order-management`, `driver-management`, `network-management` or `identity`; any new module, service, process, queue, event, event version, outbox record, or cross-module contract; any Assignment Policy, ranking, scoring, priority, or queue reordering; any Team/Fleet/crew concept or delegation or fallback mechanism; any derivation of circle membership or primacy from ride history, completion, or frequency; any driver-initiated write to a passenger's circle; any change to `POST /v1/connections`'s existing request shape or its 201/200 idempotency semantics; any change to `GET /v1/connections?driverId=`'s response shape; and any cap, expiry, or confirmation rule on the relationship. Each is excluded by a ratified document cited above, by `PRODUCT_DECISION_CIRCLE_OF_TRUST.md`'s own stated scope, or by both. None may be introduced as an implementation detail of this ADR.

## Related ADRs

- [ADR-005: Data Ownership](ADR-005-Data-Ownership.md) / [ADR-009: Domain Isolation](ADR-009-Domain-Isolation.md) / [ADR-019: Conceptual Data Ownership](ADR-019-Conceptual-Data-Ownership.md) — the exclusive-ownership and reference-not-ownership rules this ADR applies without modification.
- [ADR-034: Assignment Policy and Dispatch Decision Architecture](ADR-034-Assignment-Policy-and-Dispatch-Decision-Architecture.md) — Part 1 line 32, Part 2 lines 49 and 59; unamended and reinforced by Part 3 above.
- [ADR-037: Network Management Module Bounded Context Extension](ADR-037-Network-Management-Module-Bounded-Context-Extension.md) — the *other* `Connection`, kept separate; also the intra-module foreign key precedent.
- [ADR-042: Stated Ride Price — Minimal MVP Model](ADR-042-Stated-Ride-Price-Minimal-Model.md) — R2's structural-over-procedural placement reasoning, reused in Part 2; R8's record that PIOS has no authorization layer, reused in Part 6.
- [ADR-027: MVP Integration Mechanism](ADR-027-MVP-Integration-Mechanism.md) / [ADR-026: Deployment Strategy](ADR-026-Deployment-Strategy.md) — no server-to-server call on a write path; independent caller deployment.
- [ADR-044: Owner Authentication Mechanism](ADR-044-Owner-Authentication-Mechanism.md) — the only credential check in the system, and its scope, per Part 6.

## References

- `docs/PRODUCT_DECISION_CIRCLE_OF_TRUST.md` — the Product Owner decision this ADR implements architecturally.
- `docs/PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` — Parts 1–3, 5, 7, 8.
- `docs/PRODUCT_DECISION_PERSONAL_NETWORK_MVP_TRANSITION.md` — line 25 (relationship-first routing, not implemented here).
- `docs/PRODUCT_DECISION_ENTREPRENEUR_MODEL.md` — the independent-entrepreneur framing behind Part 5's Fleet exclusion.
- `docs/PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` — §2 (NETWORK-scope applicability), §6, §8.
- `docs/INTERFACE_CONTRACTS.md` — §4 lines 50–51 (Passenger Experience ownership), §5 (five justified contracts, unchanged).
- `docs/PIOS_PRODUCT_HYPOTHESES.md` §H7; `docs/PIOS_PRODUCT_EVIDENCE.md` line 149 (fourth application of the hypothesis basis).
- `backend/passenger-experience/src/main/resources/db/migration/passengerexperience/V1__create_connections.sql` — read, not modified.
- `backend/passenger-experience/src/main/kotlin/com/pios/passengerexperience/domain/Connection.kt`, `domain/DriverReference.kt`, `application/ConnectionRepository.kt`, `application/CreateConnectionApplicationService.kt`, `persistence/PostgreSQLConnectionRepository.kt`, `api/ConnectionController.kt` — all read, none modified by this document.
- `frontend/src/pages/PassengerLanding/PassengerLanding.tsx` lines 128–145; `frontend/src/pages/DriverHome/DriverHome.tsx` lines 366–380; `frontend/vite.config.ts` lines 19–22, 32 — read, not modified.

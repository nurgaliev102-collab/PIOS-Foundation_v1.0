# ADR-086: Network-Management Identity Binding Security Addendum

## Status

Ratified for C-3 implementation by the Product Owner instruction of 2026-09-23.
No deployment or product activation is authorized.

## Context and decision

Eight dormant network-management routes lack authentication and object-level
authorization. [D-12](../PIOS_D12_NETWORK_IDENTITY_DECISION.md) is the precise
decision and endpoint matrix incorporated by this addendum. This is a narrow
security repair, not a new referral, dispatch, discovery, or network product
flow.

Identity remains the authentication owner. Its signed token's verified `sub`
is the `IdentityId`. Network-management persists that plain reference as an
immutable, unique `persons.identity_id` with `identity_bound_at`. It generates
and owns the separate `PersonId`; Identity stores no `PersonId`. Every existing
network-management route requires the user's Bearer token. A single local
resolver maps the verified server-side `sub` to the bound Person. Path, body,
query, phone, name, and invitation code cannot establish the caller. Legacy
unbound Persons remain quarantined, without heuristic backfill.

Guests may own their own Person under exactly the same D-12 self-only and
participant rules. In-place guest upgrade, login, driver association and phone
recovery retain the same IdentityId and hence the same Person. No normal API
can rebind a Person; account merge/transfer is not authorized. Identity
deletion/disablement does not currently exist and is not invented here.

## Exact historical exceptions

- **ADR-037:** Its Sprint 7A zero-integration statement remains historical.
  D-12 now permits the module-local plain IdentityId reference and local
  verification of Identity-issued tokens solely for C-3 security. It does not
  introduce a synchronous Identity dependency or routing integration.
- **ADR-038:** The Consequences clause saying Person/Identity wiring is “not
  authorized here” is superseded for this one binding. The aggregates and IDs
  remain separate; `Person.phone` is not an authentication credential or
  ownership proof. Nothing else in ADR-038 is reopened.
- **ADR-064:** Decision Part 2 and “What This ADR Does Not Authorize” prohibit
  any new network-management read/write/dependency. Those prohibitions are
  superseded only for C-3 hardening of its existing routes, the local mapping
  migration and tests. ADR-064's product placement and ban on new runtime
  feature consumers remain in force.
- **ADR-075:** Its “What this ADR does not authorize” prohibition on guest
  network-management use and Identity↔Person linkage is superseded only for a
  guest accessing its own bound Person under D-12. No guest driver-side
  capability, Dispatch/Order/Passenger Experience `gst` authorization change,
  or change to guest TTL/recovery risk is authorized.
- **ADR-059:** Unchanged. No network-management response may disclose phone;
  phone is never used to link Identity and Person.
- **ADR-082:** Unchanged. A non-Identity service verifies token signature and
  expiry locally but cannot compare `sgen` to live Identity state. The
  existing possible pre-recovery-token validity until `exp` is inherited, not
  solved by a new service dependency.

## Authorization and data boundaries

The complete eight-route self-only/participant matrix and status rules are in
D-12 §5–6. The local `persons.identity_id` uniqueness constraint ensures one
Person per Identity, the Person primary key ensures one row per Person, and an
immutability trigger forbids normal updates of the binding pair. Existing
null-bound rows are preserved but cannot be directly read, selected as a
Connection target, or used as an Invitation creator by the protected API.

The module remains dormant: no public routing, deployment, production schema
change, service credential, operator Basic bypass, or cross-module caller is
authorized by this addendum. Deployment is a separate gate.

# ADR-069: Test/Real Segregation in Fallback Driver Selection — `is_test` on Dispatch's Availability Projection

## Status

**Accepted, 2026-09-15, by the Product Owner, in-session (ratified the same day it was proposed, given the confirmed live production defect).**

Ratification resolves the blocking questions:

- **Q-A — accepted as specified: strict equality, both directions.** A real order never reaches a test driver (the defect fix); a test order never reaches a real driver, yielding `NoAvailableDriver` instead. The "prefer real, fall back to test" alternative is rejected for the reasons Part 3 already states (implicit ranking; re-admits the defect through a side door).
- **Q-B — accepted as recommended by the architect role.** (i) Extending the same predicate to `ProposalApplicationService.handle`'s atomic availability check is deferred to its own, later decision, once the column is `NOT NULL` and backfill is verified complete — not authorized by this ADR. (ii) The manual Coordinator path (`POST /v1/proposals`) is not touched — also not authorized here.
- **Q-C — assigned as a required, non-blocking implementation task**, not skipped: the developer implementing this ADR must also update `docs/INTERFACE_CONTRACTS.md` §5/§7, `docs/EVENT_CATALOG.md`'s `DriverAvailabilityChanged` entry, and add the `ADR-069` row to `docs/README.md`'s ADR index (same narrow-scope precedent used for ADR-062/063/067/068).
- **Q-D, Q-E** — left to implementation design, as scoped.

**Sequencing in Part 4.3 is binding, not advisory** — the migration, publish, backfill, and verification gate must land before the selection predicate ships, in that order. **Part 5 is binding**: `ADR-068`'s Tier 1 query and this ADR's Tier 3 fix ship together, in one change, since `ADR-068`'s implementation is not yet merged.

**No code may be written before this status section; it now may.**

This ADR is written in response to a **confirmed live production defect**, not a feature request. It is filed as its own ADR rather than as an amendment to `ADR-068` (ratified earlier the same day); the reasoning for that choice is Part 0, and it is stated first because it is itself an architectural decision.

**Immediate containment is available without any code change and without this ADR being ratified:** declare `e2e-lifecycle-driver-86947813` UNAVAILABLE through Driver Management's own Declare Availability command (`DriverController`, the ordinary API path — *not* a direct `UPDATE` against `pios_dispatch.driver_availability`, which would desynchronise Dispatch's projection from its owner). That removes exactly one row from the `available = true` candidate set. It does not fix the defect class, and it must not be recorded as a fix.

This ADR invents no business rule (`CLAUDE.md`, "Never Invent Business Rules"; `ADR-002`). It does not decide pricing, commission, matching quality, or any regulatory rule. It extends an **already-ratified data-separation concept** — Owner Control Center test/production data separation, 2026-08-17, the same instruction cited in `V5__add_driver_is_test.sql` lines 1–13 and `V11__add_proposal_and_assignment_is_test.sql` — to a selection path that was built without it. One aspect of the rule (the second direction: may a *test* order be offered to a *real* driver?) is a genuine product judgement; it is stated explicitly as a decision below **and** raised as a blocking question (Q-A) rather than assumed settled.

---

## Context

### The defect, as observed

Confirmed 2026-09-15 by direct read-only query against production `pios_dispatch`:

1. **`pios_dispatch.driver_availability` has no `is_test` column at all.** `V2__driver_availability.sql` lines 11–15 create exactly three columns:

   ```sql
   CREATE TABLE driver_availability (
       driver_reference TEXT PRIMARY KEY,
       available BOOLEAN NOT NULL,
       updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
   );
   ```

2. **The fallback selection query has no notion of test data.** `backend/dispatch/src/main/kotlin/com/pios/dispatch/persistence/PostgreSQLDriverAvailabilityRepository.kt` lines 70–80:

   ```sql
   SELECT driver_reference FROM driver_availability
   WHERE available = true [AND driver_reference NOT IN (?, …)]
   ORDER BY updated_at ASC LIMIT 1
   ```

3. **`isTest` reaches the service and is then ignored for selection.** `FallbackDispatchApplicationService.attempt` (`application/FallbackDispatchApplicationService.kt`) takes `isTest: Boolean = false` at line 92, and at lines 95–96 calls `driverAvailabilityRepository.findLongestIdleAvailable(excludeDrivers)` — no `isTest` argument exists on that method. The parameter is used only at line 103, to stamp the resulting `ProposeDriverCommand`. So the flag correctly labels the Proposal that gets created, and has **zero** influence on who it is created for.

4. **Production impact, observed:** driver `e2e-lifecycle-driver-86947813` (`is_test = true` in `pios_driver_management.drivers`) currently holds the oldest `available = true` row in `driver_availability` (since 2026-08-17) — older than every currently-available real driver. Under `ORDER BY updated_at ASC LIMIT 1`, that row is the **first** thing Fallback Dispatch returns. A real passenger's real order would be offered to a test fixture.

### Why the flag is missing: the source event never carried it

This is not a consumer that drops a field it was given. The field is absent at the source.

- **Driver Management does hold the fact, authoritatively.** `V5__add_driver_is_test.sql` line 14 adds `drivers.is_test BOOLEAN NOT NULL DEFAULT FALSE`; `Driver.kt` line 65 carries `isTest`; `PostgreSQLDriverRepository.kt` lines 35/40/55/82 persist and read it.
- **The published event does not carry it.** `DriverAvailabilityApplicationService.envelopeFor` (lines 97–109) builds the payload as exactly:

  ```kotlin
  "payload" to mapOf(
      "driverId" to event.driverId.value,
      "availability" to event.availability.name
  )
  ```

  and the domain event itself (`domain/DriverAvailabilityChanged.kt` lines 13–17) has only `driverId`, `availability`, `occurredAt`.
- **The consumer therefore cannot project it.** `DriverAvailabilityChangedListener.onMessage` lines 70–86 extracts `payload.driverId` and `payload.availability` and nothing else — correctly, since nothing else is offered. Its own KDoc lines 28–33 state that limit as deliberate.

**Conclusion: `is_test` is genuinely absent from the Driver Management → Dispatch contract, not merely unused by Dispatch.** Fixing this requires the owning module to publish the fact; Dispatch may not invent, infer, or fetch it (`ADR-030` line 26: a consumer *"never defines its own copy of, or private extension to, another domain's event schema"*; `ADR-005`/`ADR-019`, reference-not-ownership).

### What `ADR-068` ratified hours earlier, and where it collides

`ADR-068` Part 2, property 3 (line 147) states, as a ratified property of the selection algorithm:

> **"Availability remains the sole eligibility gate at selection time**, sourced only from Dispatch's own `driver_availability` projection (`ADR-034` Part 2, "current ratified inputs"). The trusted projection narrows the candidate set; it never overrides availability, and it carries no eligibility meaning of its own."

This ADR adds a **second** eligibility gate at selection time. That is a direct narrowing of a sentence the Product Owner ratified today, and it cannot be done silently (`CLAUDE.md`, "Never Change Architecture Without an ADR"; `ADR-015`).

`ADR-068`'s implementation is **not merged into `pios-product-main`**. A repository-wide search for `TrustedDriver` in the working tree returns only `ADR-068` itself; the implementation lives on the agent worktree `.claude/worktrees/agent-af0ceb8b77d9fe9ef` (`application/TrustedDriverRepository.kt`, `persistence/PostgreSQLTrustedDriverRepository.kt`, `resources/db/migration/dispatch/V17__trusted_driver.sql`, and a modified `FallbackDispatchApplicationService.kt`). That branch's Tier 1 query (`PostgreSQLTrustedDriverRepository.findLongestIdleTrustedAvailable`, lines 67–92) joins `trusted_driver_records` to `driver_availability` on `a.available = true` — **it inherits the same defect verbatim**, because it inherits the same eligibility predicate.

---

## Part 0 — Decision: a new ADR, not an amendment to `ADR-068`

**Decided: this is `ADR-069`, a new record, with a narrow amendment pointer added into `ADR-068` Part 2 property 3 (the same in-place-pointer mechanism `ADR-068`'s own Q9 applied to `ADR-054`, per `ADR-015`).**

Three reasons, each sufficient on its own:

1. **It changes what `ADR-068` ratified, rather than elaborating it.** "Availability remains the sole eligibility gate" is a positive, load-bearing statement. Editing that sentence's meaning inside the document the Product Owner ratified this morning would make the ratified text no longer be what was ratified. `ADR-015`'s discipline is to record the narrowing in a new document and point at it, not to rewrite history.
2. **Different module pair, different contract.** `ADR-068` is entirely a Passenger Experience → Dispatch decision and explicitly touches no Driver Management contract (`ADR-068` Part 4: *"Order Management, Driver Management, Identity, `network-management`, `core` — untouched"*). This ADR changes the **Driver Management → Dispatch** event contract (`INTERFACE_CONTRACTS.md` §5) and Dispatch's own `driver_availability` schema. Folding a second module's contract change into `ADR-068` would make that ADR's own "untouched" list false.
3. **The defect predates `ADR-068` and exists without it.** Tier 3 has behaved this way since FR-003A shipped. This decision must stand even if `ADR-068` were never merged — an amendment to `ADR-068` would tie a live production fix to an unmerged feature branch's fate.

---

## Decision

### Part 1 — Driver Management publishes `isTest` on `DriverAvailabilityChanged` (additive, no version bump)

- `DriverAvailabilityChanged`'s payload gains **one** additional field, `isTest: Boolean`, sourced from `Driver.isTest` — the owning module's own authoritative field, published by the owning module, on its own event. `ADR-030` line 25 (*"only Driver Management may add, change, or version a field on DriverAvailabilityChanged"*) is satisfied by construction, and line 26's prohibition on consumer-side extension is why this is the only admissible mechanism.
- **This is a non-breaking, additive change: `eventVersion` stays `1`** (`ADR-030` lines 33–36 and 58: adding an optional field does not increment the version; tolerant readers absorb it). `DriverAvailabilityChangedListener`'s strict `eventVersion == 1` check (lines 59–62) therefore continues to pass unmodified, and every other consumer is unaffected.
- **No other field is added.** No name, phone, rating, reputation, vehicle, or `createdAt`. `ADR-034` Part 2 line 61's prohibition on driver *profile* data crossing into the dispatch decision is not touched: `is_test` is not a profile attribute of a person, it is a **classification of the record itself** — the same thing `proposals.is_test` and `assignments.is_test` already are inside Dispatch (`V11`), carried so that test data and production data do not mix. It expresses no quality, preference, rank, or standing, and is never compared between two drivers.

### Part 2 — Dispatch projects it into `driver_availability` as a **three-valued** column

- One new column on `driver_availability`: `is_test BOOLEAN NULL` — **nullable, with no default.** `NULL` means *"Dispatch has not yet been told this driver's classification."* It is not a third business state; it is an explicit "unknown", and it exists only until backfill completes (Part 4).
- `DriverAvailabilityChangedListener` reads `payload.isTest` **tolerantly**: present → project `true`/`false`; **absent or non-boolean → project `NULL`** (never `false`). This is the whole safety property of the design: an event published by a not-yet-upgraded Driver Management instance must produce "unknown", never "real".
- The `upsert` path must carry the value through on every event, including when it is `NULL`, so a driver's classification is refreshed on every availability change rather than being written once and trusted forever.
- Dispatch remains a non-authoritative projection. Driver Management stays sole owner of `is_test`. Dispatch never writes back, never exposes a query over it, and never reads `pios_driver_management` (no shared schema, `PERSISTENCE_ARCHITECTURE.md` §5).

### Part 3 — The selection rule, stated exactly (no ambiguity left)

Let `orderIsTest` be the `isTest` value already flowing into `FallbackDispatchApplicationService.attempt` — from `OrderSubmitted`'s `payload.isTest` on the submission path (`OrderSubmittedFirstRefusalListener.kt` lines 136/158), and from `proposal.isTest` on the decline and lapse paths (`ProposalController.kt` line 437, `ProposalLapseApplicationService.kt` line 155). **No new source of this flag is introduced.**

A driver is an eligible fallback candidate if and only if **all** of the following hold:

```
  driver_availability.available = true                 (unchanged, ADR-068 property 3)
AND driver_reference ∉ excludeDrivers                    (unchanged, ADR-068 property 2)
AND driver_availability.is_test IS NOT NULL              (fail-closed on unknown)
AND driver_availability.is_test = :orderIsTest           (strict equality, both directions)
```

Tie-break inside the resulting candidate set is **unchanged**: `ORDER BY updated_at ASC LIMIT 1`.

Consequences of that rule, spelled out so nothing is left to interpretation:

| Order | Eligible drivers |
|---|---|
| Real (`isTest = false`) | Only drivers with `is_test = false`. **Never** a test driver — this is the defect being fixed. |
| Test (`isTest = true`) | Only drivers with `is_test = true`. **Never** a real driver — a test order must not put a fake job in a live driver's app. |
| Either | **Never** a driver with `is_test IS NULL` (classification unknown). |

- **Strict equality, not preference.** A test order does **not** fall back to real drivers when no test driver is available; it returns `FallbackDispatchOutcome.NoAvailableDriver`, an already-existing, non-exceptional outcome (`ADR-068` Part 4). Segregation is a *partition*, not a *ranking* — which is also what keeps this on the MATCHING RULES side of `PIOS_TAXI_PRODUCT_DECISIONS.md` §10 and consistent with `ADR-068` Part 2 property 1: a binary filter, no score, no weight, no cross-driver comparison. A "prefer real, fall back to test" rule would be exactly the implicit ranking §10 forbids, and would also re-admit the defect through the back door.
- **Fail-closed on `NULL` is deliberate.** The alternative (treat unknown as real) is the current broken behavior. See Part 4 for why this does not mean "fallback goes dark".
- **This rule applies identically to Tier 1 and Tier 3.** Not as a follow-up — as one change. See Part 5.
- **Tier 2 is untouched**: still not defined, still structurally empty (`ADR-068` Part 3).

### Part 4 — Retroactive-data safety: `NULL`, not `DEFAULT FALSE`, plus an owner-side replay backfill

**The migration must NOT use `BOOLEAN NOT NULL DEFAULT FALSE`.** This is the single most important line in this ADR.

`V5__add_driver_is_test.sql` (lines 6–13) chose `DEFAULT FALSE` and explained why it was safe there: `drivers` is the **authoritative** table, every insert path sets the value explicitly going forward, and a mislabelled historical row is a reporting inaccuracy. `V11` chose it for `proposals`/`assignments` for the same reason — those are **historical records** of things that already happened.

None of that transfers here. In `driver_availability`, `is_test = false` is not metadata about the past; it is a **live predicate meaning "eligible to receive real passengers' orders."** Applying `DEFAULT FALSE` would write `false` onto every existing row — including `e2e-lifecycle-driver-86947813`'s row, the exact row that caused this defect — and the migration would **ratify the defect in the schema** while appearing to fix it. That is strictly worse than today, because the query would then look correct.

Therefore:

1. **Migration:** `ALTER TABLE driver_availability ADD COLUMN is_test BOOLEAN NULL;` — no default, no backfill inside the migration. Every pre-existing row becomes explicitly *unknown*, and by Part 3 an unknown driver is ineligible for **every** order, real or test. The known-bad row is excluded from the first moment the predicate ships.
2. **Backfill = owner-side replay, not a cross-module read.** Driver Management re-publishes `DriverAvailabilityChanged` (carrying `isTest`) once for every driver it holds, through its existing outbox/relay (`ADR-032`), as an operator-triggered one-off. Dispatch's ordinary consumer path fills the column with no new ingestion route, no new endpoint, and no Dispatch read of `pios_driver_management`. This is the same "replay from the owning module" backfill shape `ADR-068` Part 1 named for its own projection.
   - **Implementation constraint, not a detail:** replayed events must carry **fresh `eventId`s**, or `driver_availability_processed_events`'s primary-key ledger (`PostgreSQLDriverAvailabilityRepository.markProcessed`) will correctly discard every one of them as a redelivery and the backfill will silently do nothing. Whoever implements this must verify that explicitly.
   - A replay must not fabricate an availability *change*: it republishes each driver's **current** `availability` value, so no driver's `available` flag flips as a side effect of the backfill. `updated_at` will be refreshed by `upsert` (line 42, `updated_at = now()`), which resets the longest-idle ordering platform-wide — **an accepted, stated side effect**, not a silent one. It affects tie-break order only, never eligibility.
3. **Deployment ordering is part of this decision, because fail-closed has an availability cost if sequenced wrongly.** Required order:
   1. Migration (nullable column) + Driver Management publishing `isTest` + Dispatch projecting it. **Selection predicate not yet changed.** From this moment the column self-heals for any driver who toggles availability.
   2. Operator-run replay backfill (2, above).
   3. **Verification gate, to be checked before enabling the new predicate:** `SELECT count(*) FROM driver_availability WHERE available = true AND is_test IS NULL` returns `0`.
   4. Selection predicate change (Part 3) ships.

   If steps 1–3 are skipped or fail, the fail-closed rule means Fallback Dispatch returns `NoAvailableDriver` rather than mis-offering an order. That is the correct failure direction, but it is a real, visible degradation — it must be monitored, not discovered. Whether steps 1–4 ship as one release with the predicate behind the verification gate, or as two releases, is an implementation-design choice for the developer; the *ordering* is not.
4. **A later, separate migration may tighten the column to `NOT NULL`** once the gate in 3(iii) has held over a stated period. Not authorized here, and not required for correctness — fail-closed already covers it.

**Explicitly rejected backfill alternatives:**

| Alternative | Why rejected |
|---|---|
| `DEFAULT FALSE` on the new column | Writes "real" onto the known-bad row; ratifies the defect in schema. See above. |
| Dispatch reads `pios_driver_management.drivers` (direct or via a one-off script) | Cross-schema read; `PERSISTENCE_ARCHITECTURE.md` §5 Forbidden Ownership, `ADR-005`/`ADR-009`/`ADR-019`. Not admissible even as a one-off. |
| Dispatch calls Driver Management's HTTP API to classify drivers | A server-to-server call on (or beside) the write path; the exact shape `ADR-062` and `ADR-068` Part 1 both rejected for this module pair. |
| Infer test drivers from the identifier string (e.g. an `e2e-` prefix) | Inventing a business rule from a naming accident (`CLAUDE.md`). `e2e-lifecycle-driver-86947813` happens to be legible; nothing guarantees the next one is. |
| Manual operator `UPDATE` against `pios_dispatch.driver_availability` | Writes a fact into a derived projection from outside its owner's event stream; it would be silently overwritten on the driver's next availability change, and it breaks the projection's one-writer invariant. |

### Part 5 — `ADR-068`'s Tier 1 query is in scope of this same change, not a follow-up

`PostgreSQLTrustedDriverRepository.findLongestIdleTrustedAvailable` (worktree, lines 67–92) selects on `a.available = true` with no test/real predicate, and `FallbackDispatchApplicationService.attempt` on that branch (lines 130–133) tries Tier 1 **before** Tier 3. If only Tier 3 were fixed, a real passenger with a test driver in their trusted circle would hit the defect *first and preferentially* — the fix would make the common case safe and the relationship case worse.

**Decided: both queries carry the identical predicate, in one change.** Two supporting facts make this cheap rather than burdensome:

- `ADR-068`'s implementation is **not merged** (search evidence in Context). The predicate can be added on that branch before it lands, so no fix-then-refix sequence exists.
- The predicate is the same four-line `WHERE` clause in both places, over the same `driver_availability` columns.

If `ADR-068`'s branch merges first, this change touches both repositories' queries. If this change merges first, `ADR-068`'s branch must rebase onto it and include the predicate in its Tier 1 query as a **merge precondition** — not a follow-up ticket.

### Part 6 — What this ADR does NOT change

- **`FirstRefusalApplicationService` / the primary-driver path.** A primary driver is a passenger's own explicit designation, not a platform selection; applying a segregation predicate there is a different question with different reasoning. Untouched. See Q-B.
- **`ProposalApplicationService.handle`'s availability check** (lines 126–132) — still `record?.available == true`, still the sole atomic authority on availability (`ADR-068` Part 2, property 4). Adding a test/real check there as defence-in-depth is deliberately **not** bundled here; see Q-B.
- **`ProposeDriverCommand.isTest`, `Proposal.isTest`, `Assignment.isTest`, `Trip.isTest`** and every existing `is_test` column — meaning, defaults, and propagation all unchanged.
- **`FallbackDispatchOutcome`'s three cases**, the three trigger call sites, `excludeDrivers` semantics, lapse semantics, the one-open-proposal-per-order invariants, and "no retry on decline/lapse" — all unchanged (`ADR-068` Part 4, preserved in full).
- **`eventVersion`** stays `1`; no migration window, no dual-publish (Part 1).
- **Order Management, Passenger Experience, Identity, `network-management`, `core`** — untouched. Order Management already carries `isTest` on `OrderSubmitted`; nothing there changes.

### Part 7 — What this ADR does not authorize

Any implementation before ratification; any `DEFAULT FALSE` on the new column; any cross-schema or synchronous cross-module read to obtain `is_test`; any inference of test status from identifiers, naming conventions, or heuristics; any additional field on `DriverAvailabilityChanged` beyond `isTest`; any "prefer real, fall back to test" or any other ordering/weighting between the two classes; any change to `eventVersion`; any extension of this predicate to First Refusal, to `ProposalApplicationService.handle`, or to any manual Coordinator proposal path (`POST /v1/proposals`); any Tier 2 definition; any change to `ADR-068`'s tier ordering or tie-break; any new endpoint, event, or state.

---

## Open Questions

**Blocking — Product Owner:**

- **Q-A.** Part 3 fixes the rule as **strict equality in both directions**. Direction one (a real order must never reach a test driver) is a defect fix and is not in question. Direction two — **a test order must never reach a real driver, and instead yields `NoAvailableDriver`** — is a product judgement this ADR states rather than assumes. The alternative ("a test order may use a real driver if no test driver is available") is rejected here as an implicit ranking (`PIOS_TAXI_PRODUCT_DECISIONS.md` §10) and as an intrusion of fake work into a live driver's app, but confirmation is required, not inferred.

**Blocking — Architect (this role), before implementation handoff:**

- **Q-B.** Should the same predicate be applied to (i) `ProposalApplicationService.handle`'s availability check, as the atomic last line of defence, and (ii) the manual Coordinator path `POST /v1/proposals`? Recommendation: **(i) yes, as a separate, subsequent decision once backfill is complete and the column is `NOT NULL`** — bundling a fail-closed check into the single atomic gate that every proposal path passes through, while the column is still three-valued, risks blocking legitimate proposals platform-wide. **(ii) no** — an explicit human Coordinator action is not a platform selection. Neither is authorized by this ADR.
- **Q-C.** `docs/INTERFACE_CONTRACTS.md` §5 ("Contract: Driver Management → Dispatch", lines 94–99) and §7's event table (line 160), plus `docs/EVENT_CATALOG.md`'s `DriverAvailabilityChanged` entry, need the new payload field recorded. **Not performed by this ADR** (this task's scope was restricted to `docs/ADR/`). `docs/README.md`'s ADR index likewise needs an `ADR-069` row.

**Non-blocking, implementation design:**

- **Q-D.** Monitoring for the fail-closed condition: an alert on `count(*) WHERE available = true AND is_test IS NULL > 0`, and on a sustained rise in `NoAvailableDriver` outcomes after the predicate ships. Without it, a failed backfill is invisible until a passenger complains.
- **Q-E.** Whether the replay backfill (Part 4.2) is a one-off operator command, a scheduled job, or a manual runbook step. Not decided here; the *ordering* constraint in Part 4.3 is.

---

## Consequences

### Positive

- The observed production defect is closed at its root — the selection predicate — rather than by removing one bad row.
- The fix is the ratified pattern end to end: owner publishes, consumer projects, projection is queried locally. No new module, no new contract shape, no synchronous cross-module call, no shared schema, no foreign key.
- Fail-closed means every failure mode of the fix (unpublished field, failed backfill, lagging consumer) degrades to "no driver offered" rather than "wrong driver offered".
- `ADR-068`'s Tier 1 is corrected before it ever reaches production, not after.
- The rule stays a binary filter with the existing tie-break, so no ranking mechanism is introduced implicitly (`PIOS_TAXI_PRODUCT_DECISIONS.md` §10; `ADR-068` Part 2 property 1 preserved).

### Negative

- `ADR-068` Part 2 property 3's "availability is the sole eligibility gate" is narrowed the day after it was ratified. Recorded here and pointed at from `ADR-068` itself, not glossed.
- Dispatch's availability projection gains a nullable column and a three-valued read, which is more state to reason about until the column is tightened to `NOT NULL`.
- The backfill replay resets `updated_at` platform-wide, which perturbs the longest-idle tie-break once. Stated, bounded, eligibility-neutral — but real.
- Between the migration and a completed backfill, fallback eligibility is strictly smaller than today. Sequencing (Part 4.3) is what keeps that window closed; skipping it turns a correctness fix into an availability incident.
- A test order with no available test driver now produces `NoAvailableDriver` where it previously produced a proposal to a real driver. This is intended, and it will look like a regression to anyone running E2E flows against production without a test driver online.

---

## Related ADRs

- [ADR-068: Relationship-Ordered Fallback Dispatch](ADR-068-Relationship-Ordered-Fallback-Dispatch.md) — Part 2 property 3 is narrowed by this ADR; its Tier 1 query is in scope of the same change (Part 5).
- [ADR-030: Event Schema Versioning Strategy](ADR-030-Event-Schema-Versioning-Strategy.md) — additive-field rule (lines 33–36, 58) and owner-only schema authority (lines 25–26), both applied unmodified.
- [ADR-031](ADR-031-Event-Infrastructure-Foundation-Architecture.md) / [ADR-032](ADR-032-Reliable-Event-Publication-Strategy.md) — the outbox/consumer/idempotency machinery reused for both the field and its replay backfill; no new technology.
- [ADR-034: Assignment Policy and Dispatch Decision Architecture](ADR-034-Assignment-Policy-and-Dispatch-Decision-Architecture.md) — Part 2's forbidden-input list; Part 1 argues `is_test` is a record classification, not profile data, and never a ranking input.
- [ADR-005](ADR-005-Data-Ownership.md) / [ADR-009](ADR-009-Domain-Isolation.md) / [ADR-019](ADR-019-Conceptual-Data-Ownership.md) — reference-not-ownership; why the backfill is an owner-side replay and never a cross-schema read.
- [ADR-015: Evolution Strategy](ADR-015-Evolution-Strategy.md) — why this is a new record plus a pointer, not an in-place rewrite of `ADR-068`.
- [ADR-002: Dispatch Engine](ADR-002-Dispatch-Engine.md) — pricing/commission/matching/regulatory rules out of architectural scope; Q-A is raised rather than assumed.
- [ADR-007](ADR-007-Decision-Authority.md) — acceptance is a human act; this ADR is Proposed.

## References

Read for this ADR on 2026-09-15, none modified except the pointer added to `ADR-068`:

- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/FallbackDispatchApplicationService.kt` (lines 89–96, 103)
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/DriverAvailabilityRepository.kt` (lines 35–62)
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/persistence/PostgreSQLDriverAvailabilityRepository.kt` (lines 37–47, 70–80)
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/persistence/DriverAvailabilityChangedListener.kt` (lines 51–92)
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/ProposalApplicationService.kt` (lines 119–143)
- `backend/dispatch/src/main/resources/db/migration/dispatch/V2__driver_availability.sql`, `V11__add_proposal_and_assignment_is_test.sql`
- `backend/driver-management/src/main/kotlin/com/pios/drivermanagement/application/DriverAvailabilityApplicationService.kt` (lines 75–109)
- `backend/driver-management/src/main/kotlin/com/pios/drivermanagement/domain/DriverAvailabilityChanged.kt`, `domain/Driver.kt` (line 65)
- `backend/driver-management/src/main/resources/db/migration/drivermanagement/V5__add_driver_is_test.sql`
- `.claude/worktrees/agent-af0ceb8b77d9fe9ef/backend/dispatch/src/main/kotlin/com/pios/dispatch/persistence/PostgreSQLTrustedDriverRepository.kt` (lines 67–92) — unmerged `ADR-068` implementation
- `.claude/worktrees/agent-af0ceb8b77d9fe9ef/backend/dispatch/src/main/resources/db/migration/dispatch/V17__trusted_driver.sql` — unmerged; migration-number collision noted for the developer
- `docs/ADR/ADR-068-Relationship-Ordered-Fallback-Dispatch.md` (Part 2 property 3, line 147)
- `docs/INTERFACE_CONTRACTS.md` §5 (lines 94–99), §7 (line 160)
- Production read-only query against `pios_dispatch.driver_availability`, 2026-09-15 — reported to this role, not executed by it.

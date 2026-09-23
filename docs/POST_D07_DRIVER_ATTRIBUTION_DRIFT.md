# Post-D-07 Driver Attribution Drift — `repeatClientsCount` / `driver_client_rides`

**This is a forensic record, not a fix, not an ADR, and not a D-08 decision.** It exists so the drift found during D-08's own analysis is not lost or silently folded into an unrelated initiative. No code, schema, migration, ADR, or test is changed by this document.

> **Closure record, 2026-09-23 (append-only).** The current-behavior evidence
> below is preserved as the historical state in which the drift was found.
> Product readiness remediation selected the already-ratified Path A: the
> version-1 `AssignmentCompleted` payload keeps `driverId` as the committing
> driver and adds optional `executingDriverId`. Driver Management falls back to
> `driverId` for old messages; completed-ride/earnings facts use the executor,
> while client-relationship/repeat facts use the committer. Focused tests cover
> the split and old-envelope compatibility. The drift recorded here is closed.

---

## 1. Exact Current Behavior

`backend/driver-management/src/main/kotlin/com/pios/drivermanagement/application/AssignmentCompletedApplicationService.kt`, `handle()` (re-read directly, not assumed):

```kotlin
fun handle(command: AssignmentCompletedUpdateCommand) = transactionRunner.run {
    val isNewEvent = assignmentCompletedRepository.markProcessed(command.eventId)
    if (isNewEvent) {
        val driverId = DriverId(command.driverId)
        val statedPriceParsed = PriceParser.parse(command.statedPrice)
        driverMilestonesRepository.recordCompletedRide(driverId, command.occurredAt, statedPriceParsed)
        driverRideStatedPricesRepository.record(command.eventId, driverId, command.statedPrice, statedPriceParsed)

        val passengerReference = orderPassengerRepository.findPassengerReference(command.orderId)
        if (passengerReference != null) {
            val becameRepeatClient =
                driverClientsRepository.recordRideForClient(driverId, passengerReference, command.occurredAt)
            if (becameRepeatClient) {
                driverMilestonesRepository.incrementRepeatClientsCount(driverId)
            }
        }
        // ...
    }
}
```

**There is exactly one `driverId` derived per event (`command.driverId`, i.e. `AssignmentCompleted.driverId`), and every one of `completedRidesCount`, `totalStatedEarnings`, `driver_client_rides.ride_count`, and `repeatClientsCount` is computed from that same single value.** `Assignment.driver`/`Trip.driver` (the committing driver) is never read by this class — there is no code path by which it *could* route anything to a different identity than `command.driverId`.

**What `command.driverId` equals, confirmed directly from `Trip.kt`** (re-read): `Trip.arrive()`, `Trip.start()`, `Trip.complete()` each report `driverId = executingDriver` (`Trip.kt:150, 161, 172`), which defaults to `driver` (the committing driver) but becomes the substitute the moment `Trip.assignExecutingDriver()` is called — the one and only effect of `Handoff.consent()`'s own successful `commit()` (`HandoffApplicationService.kt:154-170`). `AssignmentCompleted`'s own payload shape is unchanged by D-07 (still one `driverId` field, same `eventVersion`) — this was a deliberate D-07 design choice (§4 below), not an oversight in the event contract itself.

**Net effect**: once a Handoff has been consented for a given ride, `completedRidesCount`, `totalStatedEarnings`, `driver_client_rides.ride_count`, and `repeatClientsCount` **all** credit the executing (substitute) driver for that ride. The committing driver's own copies of these four figures do not increment for that ride at all.

---

## 2. Expected Semantics from OQ-3

`docs/PIOS_TAXI_HANDOFF_OPEN_QUESTIONS.md:31-39`, OQ-3 ("After a consented handoff, whose ride count and earnings increment?"), quoted in full for the two rows that matter here:

> | **A — Executing driver only** | Whoever actually drove gets the ride-count/earnings credit... The committing driver — who held the client relationship and arranged the substitute — gets no credit at all for facilitating the ride, even in "Мой бизнес" stats. |
> | **C — Split by purpose** | **Relationship-facing facts (repeat-client flag, "Мой бизнес" stats) credit the committing driver; ride-count/earnings credit the executing driver.** Most consistent with both stated principles simultaneously. **Requires the most implementation work: `AssignmentCompleted` needs an additive `executingDriverId` field... and two different attribution rules replace today's single one.** |

**Option C is the option D-08(b) recommended and D-07's own spec reports as locked** (`docs/PIOS_FINAL_PRODUCT_OWNER_DECISION_BRIEF.md:1767`: "Attribution splits by kind (OQ-3 option C): relationship-facing facts credit the driver who committed; ride-count and earnings credit the driver who drove"). OQ-3's own text **names the repeat-client flag specifically**, by name, as a relationship-facing fact belonging to the committing driver — not a general or ambiguous statement, a precise one.

---

## 3. Why D-07's Implementation Created This Drift

OQ-3's own row for Option C, quoted above, already anticipated the exact mechanism this split would require: **an additive `executingDriverId` field on `AssignmentCompleted`, carrying the executor's identity alongside the event's existing (committer-sourced, pre-D-07) `driverId`, so two different attribution rules could read two different fields.**

D-07's own implementation spec considered this and explicitly chose a simpler path instead. From the summary of this engagement's own prior session (restated here for completeness, consistent with what the code shows): *"Resolved the `AssignmentCompleted` attribution question left open in the prior spec draft (which had recommended an additive new field) in favor of the simpler approach... reuse the existing `driverId` field, sourced from `Trip.executingDriver` instead of `Trip.driver`, with zero shape/eventVersion change and zero Driver Management code change needed at all."*

This simplification is **correct and sufficient for the ride-count/earnings half of OQ-3 Option C** — that half only ever needed the executor's identity, and reusing the single existing field delivers exactly that, with no new field, no `eventVersion` bump, and no change to `driver-management` at all (a genuine engineering win, not a mistake in itself).

**It is not sufficient for the relationship-facing half.** By construction, `driver-management` now receives exactly one driver identity per `AssignmentCompleted` event — the executor's — and has no way to learn the committing driver from this event at all. `AssignmentCompletedApplicationService` was never given, and could not be given without the additive field OQ-3 itself already named, any way to route `repeatClientsCount`/`driver_client_rides` to a different identity than `completedRidesCount`/`totalStatedEarnings`.

**The precise point of drift**: `docs/PIOS_D07_HANDOFF_IMPLEMENTATION_SPEC.md` §10 states: *"The original driver retains the historical/origination fact regardless (Invariant #22...) — matching D-08(b)'s own 'relationship-facing facts credit the committing driver' half... fully matching D-08(b)'s own recommended split, on both halves."* This sentence is true of Invariant #22 in isolation — the `Handoff` aggregate's own `original_driver_reference` field is permanent and always queryable (`GET /v1/handoffs?assignmentId=`), so *a* record of who originated the ride does survive. But that is a different fact from *the `repeatClientsCount`/`driver_client_rides` metric crediting the committing driver*, which OQ-3 Option C names separately and specifically, and which the shipped code does not do. The spec's own summary appears to have treated "the origination fact survives somewhere" as equivalent to "the repeat-client metric follows OQ-3 Option C," and those are not the same claim.

---

## 4. Affected Metrics

| Metric | Affected? | Current attribution | OQ-3 Option C's stated attribution |
|---|---|---|---|
| `completedRidesCount` | No drift | Executing driver | Executing driver — **matches** |
| `totalStatedEarnings` | No drift | Executing driver | Executing driver — **matches** |
| `driver_client_rides` (`ride_count`, `last_ride_at`) | **Drift** | Executing driver | Committing driver — **does not match** |
| `repeatClientsCount` | **Drift** | Executing driver (derived from `driver_client_rides` reaching threshold 2) | Committing driver — **does not match** |
| `invitedDriversCount` | Not applicable | Independent of `AssignmentCompleted`/Handoff entirely (`drivers.invited_by_driver_id`, set once at driver creation, `ADR-073`) | Not addressed by OQ-3, has no committer/executor axis to begin with |

---

## 5. Affected Code Path

- `backend/driver-management/src/main/kotlin/com/pios/drivermanagement/application/AssignmentCompletedApplicationService.kt` — `handle()`, the single method responsible; specifically the calls to `driverClientsRepository.recordRideForClient(driverId, ...)` and `driverMilestonesRepository.incrementRepeatClientsCount(driverId)`, both keyed by `command.driverId`.
- `backend/driver-management/src/main/kotlin/com/pios/drivermanagement/application/AssignmentCompletedUpdateCommand.kt` — carries only one `driverId` field, sourced from the event's own single `driverId`; there is no second field to carry a committer identity even if `AssignmentCompletedApplicationService` wanted to use one.
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/domain/Trip.kt` — `arrive()`/`start()`/`complete()`, the upstream source: reports `driverId = executingDriver` in each returned event, which is what ultimately becomes `AssignmentCompleted.driverId`.
- The `AssignmentCompleted` event contract itself (Dispatch's own outbox/event publishing, unchanged shape, unchanged `eventVersion`) — the layer at which OQ-3's own anticipated `executingDriverId` additive field would need to be added, if that remediation path is chosen (§7).

---

## 6. Why This Is Outside D-08

D-08 (`docs/PIOS_D08_HANDOFF_OBSERVATION_IMPLEMENTATION_SPEC.md`) defines a **new, forward-looking observation mechanism for Handoff *frequency*** — built entirely from `handoffs` and `assignments`, and explicitly, by construction, never reading `repeatClientsCount`, `driver_client_rides`, or any other `driver-management`-owned metric (`PIOS_D08_HANDOFF_OBSERVATION_IMPLEMENTATION_SPEC.md` §11).

This drift concerns something categorically different: **whether an already-shipped D-07 feature (the D-08(b) attribution split) was built correctly against an already-locked decision.** Resolving it does not touch observation windows, evidence gates, or caps — it touches whether `AssignmentCompleted` gains a new field, or whether `driver-management`'s consumer changes, or whether OQ-3's own text is instead amended to match what was actually built. None of those three things has anything to do with D-08's own subject matter. Bundling this into a D-08 ADR would conflate two unrelated architectural questions the way the task explicitly warned against.

---

## 7. Proposed Remediation Boundary (Not a Resolution)

Two legitimate resolution paths exist. Neither is chosen here — this section only bounds the shape each would take, so a future, separate decision has a starting point rather than a blank page.

**Path A — Make the code match OQ-3 Option C's literal text.**
- Add the additive field OQ-3 itself already anticipated: `AssignmentCompleted` gains a new, additive `executingDriverId` field (or symmetrically, a `committingDriverId` field), alongside its existing `driverId`, following this exact codebase's own established precedent for additive event fields (`ADR-065`'s own earlier precedent, cited by D-07's spec itself, §9).
- `AssignmentCompletedUpdateCommand` and `AssignmentCompletedApplicationService.handle()` gain a second identity, and `driverClientsRepository.recordRideForClient`/`incrementRepeatClientsCount` are re-pointed to the committer's identity specifically, while `driverMilestonesRepository.recordCompletedRide`/earnings remain on the executor's, per OQ-3's own split.
- Cost: a new event field (`eventVersion` bump or additive-only per existing convention), a `driver-management` code change, and new tests distinguishing the two identities — nontrivial but bounded and well-precedented.

**Path B — Amend OQ-3 / the D-07 spec's own text to match what was actually built.**
- Formally record that `repeatClientsCount`/`driver_client_rides` are, and will remain, executor-attributed — the same rule as `completedRidesCount`/`totalStatedEarnings` — and that OQ-3 Option C's own "repeat-client flag" clause is superseded by this later, practical decision.
- Cost: a documentation-only change (a dated, narrow amendment to the relevant open-questions/spec text, in the same style already used throughout this engagement for ADR amendments) — no code change at all.

**What this document does not do**: it does not recommend Path A over Path B, does not estimate which better serves the underlying "relationship stays with whoever created it" principle, and does not touch any code, schema, event contract, or ADR. It is closed only by a future, explicit Product Owner decision — outside D-08, and outside this document.

---

## Verification

- No production code, schema, migration, ADR, or test was modified to produce this document.
- No commit, no push.

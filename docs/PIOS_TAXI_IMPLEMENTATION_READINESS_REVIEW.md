# PIOS Taxi Implementation Readiness Review

Status: **Verification pass over `ADR-062` and `ADR-063` (both Status: Proposed), prepared ahead of an implementation task ("Task 11").** No code, schema, migration, RabbitMQ, or configuration is touched by this document. This is the fifth check `docs/PIOS_TAXI_ARCHITECTURE_CONTRACT.md` Section 21's own gate (item 2, "all contract conflicts explicitly resolved") requires before implementation — it does not itself satisfy that gate (only a documentation-reconciliation pass and ADR acceptance can), but it verifies the ADRs are actually sound enough to be reconciled and accepted.

**Tagging convention** (unchanged from the rest of this decision chain): **FACT** / **VERIFIED** (checked directly against source/ADR text for this review, not assumed) / **RISK** / **RECOMMENDATION**.

---

## 1. Consistency Verification — ADR-062

**Re-read in full for this review.** Checked against every document it claims to interact with, not merely against its own internal logic.

### 1.1 Against `ADR-034` Part 2 (Assignment Decision Boundary) — the most important check

`ADR-034` Part 2 draws three tiers, re-read directly for this review:

- **Forbidden inputs**: *"Passenger or Corporate Customer profile data (Passenger Experience — Dispatch has no contract with it at all)."*
- **Future candidate inputs**: *"Personal Client Relationship — ... No document attributes this relationship a role in the assignment decision specifically."*

These are **two different lines about two different things**, and this distinction is load-bearing: `ADR-062`'s own `PrimaryDriverRecord` carries exactly two opaque identifiers (`passengerReference`, `primaryDriverId`) and explicitly forbids passenger name, phone, or any other profile field (Architecture Contract Section 7). It therefore falls in the **Future candidate** tier (Personal Client Relationship), never the **Forbidden** tier (profile data) — **VERIFIED, not merely asserted**: had `ADR-062` proposed sending passenger name or phone to Dispatch, it would have directly violated the Forbidden list; because the data contract was deliberately minimized before this check was performed, it does not.

The Forbidden item's own parenthetical — *"Dispatch has no contract with it at all"* — is the specific, stated reason for that entry's exclusion, not an intrinsic prohibition (unlike, say, "Payment or pricing information," which is forbidden on ownership grounds regardless of any contract). `ADR-034` Part 2 itself describes exactly the promotion path a Future-candidate item follows: *"would require both a new INTERFACE_CONTRACTS.md contract and a [module]-side decision to expose it."* `ADR-062` is precisely that new contract and that decision, for the Personal Client Relationship item specifically — **no contradiction, this is the anticipated mechanism, exercised**.

**One real correction this review makes to `ADR-062`'s own Consequences section**: it already says `ADR-034` Part 2's classification is "partially superseded" — re-reading `ADR-034` confirms this is accurate but should be stated more precisely: `ADR-062` does not touch the **Forbidden** list at all (still forbidden: payment, full profile data, driver standing/ranking, notification history, analytics, external systems); it promotes exactly one line of the **Future candidate** list, for exactly one narrow purpose (a binary first-refusal trigger, not a ranking input — restated from `ADR-062`'s own Constraints). This is consistent with what `ADR-062` already says; this review only confirms it holds up against the source text rather than against a paraphrase of it.

### 1.2 Against `MODULE_STRUCTURE.md` Section 5 (Dependency Rules) and `ADR-005`/`ADR-009`/`ADR-019`

*"A module may depend on another module's declared interaction (ADR-004) or published events (ADR-003)"* — `ADR-062`'s chosen mechanism (Passenger Experience publishes, Dispatch consumes) is exactly this, structurally identical to the already-ratified `Driver Management → Dispatch` (`DriverAvailabilityChanged`) and `Order Management → Dispatch` (`OrderCancelled`, `ADR-053`) relationships. **VERIFIED — no new dependency shape is introduced**, only a new instance of an already-sanctioned one.

*"No module shares physical storage with another"* (`ADR-005`) — `PrimaryDriverRecord` is Dispatch's own table, in Dispatch's own database, written only by Dispatch's own consumer, exactly like `DriverAvailabilityRecord`. **VERIFIED.**

### 1.3 Against `ADR-054` (Circle of Trust) and `ADR-055` (Session Authentication)

`ADR-054` Part 4 (quoted in the existing `ConnectionController.kt` KDoc, re-read for this review): *"the driver-facing response gains nothing"* — `isPrimary` is deliberately passenger-facing-only at the API level. `ADR-062`'s own projection does not change this: Dispatch's copy is never exposed through any query endpoint (Architecture Contract Section 5, "who may read"), so the driver-facing response contract `ADR-054` Part 4 protects is untouched. **VERIFIED.**

`ADR-055`'s session-authentication model governs `GET /v1/connections` (passenger-session-only, 403 on mismatch) — `ADR-062` does not call this endpoint at all (it rejected Option A specifically to avoid needing a new service-to-service credential for it, Architecture Contract Section 6). **VERIFIED — no interaction, hence no conflict.**

### 1.4 One overlooked implementation-detail risk this review surfaces (not a document contradiction, a code-level nuance)

`ADR-062`'s target flow (Architecture Contract Section 3) has Dispatch consult its projection "before creating its first Proposal." Re-reading `ProposalController.createProposal` (`POST /v1/proposals`) for this review confirms it takes `driverId` directly from the request body, supplied by the caller, with **no authentication of any kind** on that endpoint (Section 4 below has the full picture). This is not a contradiction with `ADR-062`'s own text — `ADR-062` already names this exact endpoint's missing auth as a pre-existing condition it does not fix — but it means implementing First Refusal's own decision logic **cannot simply modify `createProposal`'s existing signature/behavior** without first deciding whether the caller stops supplying `driverId` explicitly (so Dispatch's own logic can choose it) or continues to (so the caller must itself consult the projection, partially reopening the client-orchestration concern `ADR-062` argued against, Section 6). **This is a real, concrete implementation question `ADR-062` itself flags as out of scope ("the exact command/endpoint shape... is implementation detail, out of this ADR's own architectural scope") — re-confirmed here as genuinely unresolved, not overlooked.**

## 2. Consistency Verification — ADR-063

### 2.1 Against `ADR-040` (Assignment Ride Lifecycle) — re-read in full for this review

`ADR-040`'s own Context section states its actual Decision precisely: *"ride-progress state lives on **Assignment** (`dispatch`), not `Order`."* Read literally out of context, this could seem to permanently bind ride-progress to the `Assignment` class specifically. **Read in its own Context** (the paragraph immediately preceding it), the decision being resolved was a **placement conflict between two modules** — the sprint's own proposal wanted these states on `Order` (Order Management); `ADR-040` rejected that and kept them in Dispatch. The invariant `ADR-040` actually protects is **module-level**: ride-progress belongs to Dispatch, never to Order Management. It says nothing about, and takes no position on, whether a future Dispatch-internal refinement could move these states to a sibling aggregate within the same module.

**VERIFIED: `ADR-063` does not contradict `ADR-040`.** It keeps ride-progress inside Dispatch (satisfying `ADR-040`'s actual, module-level holding) and only refines which aggregate *within* Dispatch owns it — a narrower, additive refinement `ADR-040` neither authorizes nor forbids, because it was never asked that question.

### 2.2 A genuine implementation-detail risk this review surfaces, found by re-reading `ADR-040` Decision item 2 directly

*"`arrive()` accepts either `CREATED` or `ACCEPTED` as its precondition — not `ACCEPTED` alone... Every Assignment reaching a driver's ride-progress buttons today is therefore `CREATED`, not `ACCEPTED`"* — `ADR-040` documents a real, still-live implementation gap: `ProposalAssignmentOrchestrationService.acceptProposal` creates an Assignment and (per this quote) never separately calls `Assignment.accept()`, so `Assignment.status` in production is `CREATED`, not `ACCEPTED`, at the moment ride-progress begins, even though `AssignmentAccepted` (the event) has already been published.

**This directly affects `ADR-063`'s own stated Trip-creation trigger**, which currently reads: *"Trip is created at the moment an Assignment reaches `ACCEPTED`."* Per the finding above, `Assignment.status` may never actually observably reach `ACCEPTED` in the one real path that creates it today. **RISK, flagged for correction when `ADR-063` is next revised (not performed by this review, which does not edit existing ADRs): the trigger should be restated as "when `AssignmentAccepted` is produced" (the event/outcome), not "when `Assignment.status == ACCEPTED`" (the field) — these are not currently the same moment in the live code**, and `ADR-063` as written conflates them. This is exactly the kind of gap this task asked to be found by actually checking against source, not by re-reading the ADRs against each other alone.

### 2.3 Against `ADR-041` (Order Lifecycle Synchronization with Assignment Completion) and `ADR-043` (Owner Control Center — Observation Boundary)

`ADR-041` Decision item 4 (*"Order Management binds only `assignment.completed`... not `assignment.arrived` or `assignment.started`"*) is unaffected in substance by `ADR-063` — the successor events (`TripCompleted`/`TripArrived`/`TripStarted`) are designed to preserve exactly this same binding pattern (Architecture Contract Section 9). **VERIFIED, no contradiction**, contingent on the dual-publish migration window `ADR-063` itself specifies actually being implemented as described (not verified further here, since no implementation exists yet to check).

`ADR-043`'s own justification for `arrivedAt`/`startedAt`/`completedAt` (operational observability) is reused, not superseded, by `ADR-063` — the three timestamps move location but keep their original purpose. **VERIFIED.**

### 2.4 Against `MODULE_STRUCTURE.md`

Re-read Section 3's Dispatch entry directly for this review: it describes Dispatch's responsibility ("the assignment decision") and owned capabilities without enumerating Dispatch's own internal aggregates exhaustively. **VERIFIED — no update to `MODULE_STRUCTURE.md` Section 3 is required for `ADR-063`**, confirming the Architecture Contract's own Section 19 finding, now checked against the actual document text rather than assumed.

## 3. Authentication / Authorization Boundary — Dedicated Investigation

**FACT, established by direct source inspection for this review, not carried forward from any prior task's summary:**

### 3.1 There is no service-to-service (backend-to-backend synchronous) authentication mechanism anywhere in this codebase today

An exhaustive search for inter-module `RestClient`/`RestTemplate` configuration found **exactly one**: `passenger-experience`'s `OrderManagementRestClientConfiguration.kt` — and it belongs to the dead, unwired Tranche-2 order-submission path (Architecture Contract Section 2). It sends no `Authorization` header, no API key, no mutual TLS, no shared secret — a plain, timeout-bounded `RestClient`. **No other backend-to-backend REST call exists in the codebase at all.** This means today, "service-to-service auth" is not a partially-built thing `ADR-062` needs to extend — it is **entirely absent**, and `ADR-062`'s own choice to avoid needing one (by rejecting Option A) sidesteps having to invent this from scratch, not merely a convenience.

### 3.2 The three real auth mechanisms that do exist, and exactly what each protects

| Mechanism | Protects | Used by |
|---|---|---|
| `SessionTokenVerifier` (`ADR-055`) | End-user (passenger/driver) identity, `Bearer` token, `sub`/`drv` claims | Identity's own endpoints; Passenger Experience's `ConnectionController` (all 5 endpoints); Dispatch's `listProposals(?driverId=)` only |
| `OwnerCredentialGate` (`ADR-044`, replicated per module) | A single shared operator/admin credential, `Authorization: Basic` | Coordinator, Owner Control Center, AI Advisor; Dispatch's `listProposals(?driverId=)` as an alternate path (ADR-060) |
| RabbitMQ broker credentials (`spring.rabbitmq.*`) | Which process can connect to the broker at all | Every event-publishing/consuming module |

### 3.3 `ProposalController`'s own auth surface, mapped completely (re-read in full for this review, not sampled)

| Endpoint | Auth |
|---|---|
| `POST /v1/proposals` (create) | **None** |
| `POST /v1/proposals/{id}/accept` | **None** |
| `POST /v1/proposals/{id}/decline` | **None** |
| `POST /v1/proposals/{id}/lapse` | **None** |
| `GET /v1/proposals/{id}` | **None** |
| `GET /v1/proposals?orderId=` | **None** — deliberately, per `ADR-060` Decision 4 ("an enumeration *sink*, not a *source*") |
| `GET /v1/proposals?driverId=` | **Session token (`drv` match) OR owner credential** — the one authenticated path, closed specifically to fix an enumeration vulnerability (`ADR-060`) |

**RISK, stated plainly**: every state-changing Proposal endpoint is unauthenticated. This is a pre-existing condition, confirmed (not newly discovered — the Architecture Contract already named `createProposal` specifically; this review confirms the same is true of accept/decline/lapse, which the Architecture Contract had not individually checked). `ADR-062`/`ADR-063` do not worsen this — First Refusal's own decision logic runs *inside* Dispatch, upstream of whichever endpoint ultimately calls `createProposal`'s own application-service layer — but neither ADR's implementation should be mistaken for closing this gap, and this review recommends the same thing the Architecture Contract already did: treat it as a precondition to consider before or alongside First Refusal shipping, not a blocker to the architecture documents themselves.

### 3.4 RabbitMQ-level trust model, checked directly against production configuration

`dispatch/src/main/resources/application.yml` (and, by the same pattern, every other RabbitMQ-using module): `username: guest`, `password: guest`, default vhost `/` — **the same shared credential for every production module**, no per-module RabbitMQ user separation (unlike the dedicated `pios_test`/`pios-test` isolation this session's own Task 2 built for tests). **RISK, pre-existing, not introduced by `ADR-062`**: nothing at the broker level distinguishes "Passenger Experience is the legitimate publisher of `PrimaryConnectionDesignated`" from any other process that can reach the broker and knows the exchange name — trust is enforced entirely by *which code is actually deployed*, identical to how `DriverAvailabilityChanged`'s own trust already works today. `ADR-062` inherits this exact, already-accepted risk model; it does not create a new one or a weaker one than what `DriverAvailabilityChanged` already operates under.

### 3.5 Conclusion for Section 3

Neither `ADR-062` nor `ADR-063` requires a new authentication mechanism, and neither can retroactively fix the two real, pre-existing gaps found (unauthenticated Proposal mutations; shared RabbitMQ credential). **RECOMMENDATION**: the exact command/endpoint-shape question `ADR-062` leaves open (Section 1.4 above) is the natural, and possibly only, point where closing the `createProposal` auth gap could be bundled into the same implementation work at low incremental cost — worth deciding explicitly when that shape is designed, not as a separate, later initiative.

## 4. Documents Requiring Old → New State Transition

Precise, section-level (not merely document-level), consolidating and sharpening the Architecture Contract's own Section 19:

| Document | Exact current statement | Exact required change | Performed by this review? |
|---|---|---|---|
| `INTERFACE_CONTRACTS.md` Section 5 | Lists five contracts, no Passenger Experience → Dispatch entry | Add a sixth "Contract: Passenger Experience → Dispatch" entry, mirroring the existing entries' own format | No |
| `INTERFACE_CONTRACTS.md` Section 7 | Nine-row event table, no `PrimaryConnectionDesignated`/`Cleared` rows | Add two rows | No |
| `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 2 | *"Dispatch. No ratified role at all"* | Add a note: role is now ratified narrowly, for First Refusal's binary trigger only, per `ADR-062` — not a general grant | No |
| `ADR-034` Part 2 | Personal Client Relationship listed under "Future candidate inputs" only | Move (or annotate) it as ratified for the narrow First-Refusal purpose, explicitly distinct from any future ranking use, per `ADR-062` | No |
| `ADR-063` itself | Trip-creation trigger stated as "`Assignment` reaches `ACCEPTED`" | Correct to "when `AssignmentAccepted` is produced" (event, not field state) — Section 2.2 above | No — this review found it, does not edit the ADR |
| `EVENT_CATALOG.md` Section 6 | `AssignmentArrived`/`Started`/`Completed` under Dispatch Domain Events | Add `TripArrived`/`Started`/`Completed` alongside, with the dual-publish note, once implemented | No |
| `docs/README.md` | ADR table ends at `ADR-061` | Add rows for `ADR-062`, `ADR-063` | No |
| `MODULE_STRUCTURE.md` Section 3 | No change needed (Section 2.4 above) | None | N/A |

**None of these were modified by this review**, consistent with the instruction that this is a verification pass, not a reconciliation pass.

## 5. Exact File List — What "Task 11" May Modify

**RECOMMENDATION, scoped narrowly.** The guiding principle: Task 11 should build the **contract infrastructure only** — new aggregates, events, projections, migrations — **without wiring it into any existing endpoint's live behavior**, since the trigger/endpoint shape (Section 1.4/3.5 above) and the response-window value both remain genuinely OPEN. This keeps Task 11 purely additive and reversible, and defers the one behavior-changing step (actually making `createProposal` or its caller consult the projection) to a separate, later task once those open questions are answered.

### 5.1 ADR-062 track (Passenger Experience → Dispatch)

**New files, `passenger-experience`:**
- `src/main/kotlin/com/pios/passengerexperience/domain/PrimaryConnectionDesignated.kt`
- `src/main/kotlin/com/pios/passengerexperience/domain/PrimaryConnectionCleared.kt`
- Corresponding test files under `src/test/kotlin/...`

**Modified files, `passenger-experience`:**
- `application/SetPrimaryConnectionApplicationService.kt` — emit `PrimaryConnectionDesignated` alongside its existing behavior (additive)
- `application/RemoveConnectionApplicationService.kt` — emit `PrimaryConnectionCleared` when the removed connection was primary (additive)
- Whichever outbox-relay wiring file already exists for this module's own event publication (mirror `order-management`'s/`dispatch`'s own `OutboxRelay`/`OutboxRelayScheduler` pattern — passenger-experience does not currently have one, since it has never published an event; this is new infrastructure for this module specifically, not a modification)
- Corresponding test files

**New files, `dispatch`:**
- `application/PrimaryDriverRecord.kt` (mirrors `DriverAvailabilityRecord.kt` exactly)
- `persistence/PrimaryDriverRepository.kt` / `PostgreSQLPrimaryDriverRepository.kt`
- `persistence/RabbitMQPassengerExperienceTopologyConfiguration.kt` (mirrors the existing `RabbitMQOrderManagementTopologyConfiguration.kt` shape — queue, DLQ, binding)
- `persistence/PrimaryConnectionDesignatedListener.kt` / `PrimaryConnectionClearedListener.kt`
- `src/main/resources/db/migration/dispatch/V12__primary_driver_projection.sql` (next available version number in that module — confirm against the actual latest file at implementation time, not assumed here)
- Corresponding test files

**Explicitly NOT modified in this track:** `ProposalController.kt`, `ProposalApplicationService.kt`, `ProposalAssignmentOrchestrationService.kt`, any frontend file, `Coordinator.tsx`, `RideRequest.tsx` — the projection exists and is fed, but nothing yet reads it to change routing behavior.

### 5.2 ADR-063 track (Trip aggregate)

**New files, `dispatch`:**
- `domain/Trip.kt`, `domain/TripStatus.kt`, `domain/TripArrived.kt`, `domain/TripStarted.kt`, `domain/TripCompleted.kt`
- `persistence/TripRepository.kt` / `PostgreSQLTripRepository.kt`
- `src/main/resources/db/migration/dispatch/V13__trips.sql` (or the next number after the ADR-062 track's own migration, if sequenced after it — see Section 6)
- Corresponding test files

**Modified files, `dispatch`:**
- `domain/Assignment.kt` — remove `arrive()`/`start()`/`complete()` and the three now-relocated timestamp fields (a real behavior-preserving refactor, not additive — this is the one place this track is not purely additive, and should be reviewed accordingly)
- `api/AssignmentController.kt` — the `arrive`/`start`/`complete` REST surface moves to reference `Trip` internally; **endpoint paths and payloads should not change** unless a separate decision authorizes that (not decided here)
- `persistence/RabbitMQEventPublisher.kt` (Dispatch's own) — publish `TripArrived`/`Started`/`Completed` **alongside** `AssignmentArrived`/`Started`/`Completed` during the dual-publish window (`ADR-063` Consequences) — the old events must **not** be removed in this same task
- Corresponding test files

**Modified files, `order-management` — deferred, not in Task 11's scope**: switching the existing `AssignmentCompleted` consumer to `TripCompleted` is explicitly the step that happens *after* the dual-publish window, per `ADR-063` itself — **should not be part of the same task that introduces the rename**, to keep the migration genuinely safe.

**Explicitly NOT modified in this track:** `order-management` (until the later switch-over task), any frontend file, `Coordinator.tsx`.

### 5.3 Documentation track (can run in parallel, low risk)

- `INTERFACE_CONTRACTS.md`, `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 2, `ADR-034` Part 2 (annotation, not rewrite — per `CLAUDE.md`'s "Never Delete Documentation"), `EVENT_CATALOG.md`, `docs/README.md` — per Section 4 above.
- `ADR-062`, `ADR-063` themselves — Status flip from Proposed to Accepted (a governance action, not a content rewrite) once reviewed; `ADR-063`'s own trigger-wording correction (Section 2.2 above) should be applied at this point too, as an amendment, not a silent edit — following this repository's own precedent (`ADR-040`'s own "Editorial note added after ratification" pattern, which keeps the original text and appends a dated correction rather than rewriting history).

### 5.4 Files that must NOT be touched by Task 11, stated explicitly

`frontend/**` (all of it — no trigger mechanism is decided, Section 1.4), `Coordinator.tsx`, any existing migration file (only new, additive migrations), `ProposalController.kt`'s existing endpoint signatures, `AssignmentController.kt`'s existing endpoint signatures beyond internal relocation, any RabbitMQ vhost/user/permission configuration (production or test), any `OwnerCredentialGate`/`SessionTokenVerifier` file.

## 6. Implementation Dependency Graph

```
                        ┌─────────────────────────────┐
                        │  Documentation reconciliation │
                        │  (Section 4/5.3) — can start   │
                        │  immediately, blocks nothing    │
                        │  downstream, but Architecture   │
                        │  Contract Section 21's gate      │
                        │  needs it done before either      │
                        │  track below is considered        │
                        │  "implementation-ready"           │
                        └──────────────┬───────────────────┘
                                       │ (gate, not a build dependency)
        ┌──────────────────────────────┼──────────────────────────────┐
        │                              │                              │
        ▼                              │                              ▼
┌───────────────────┐                  │                  ┌───────────────────────┐
│  ADR-062 TRACK      │                 │                  │  ADR-063 TRACK          │
│  (independent of     │                │                  │  (independent of         │
│   ADR-063 track)      │                │                  │   ADR-062 track)          │
└─────────┬─────────────┘                │                  └───────────┬─────────────┘
          │                              │                              │
          ▼                              │                              ▼
1. PE: PrimaryConnectionDesignated/       │              1. Dispatch: Trip/TripStatus/
   Cleared event classes + outbox         │                 TripArrived/Started/Completed
   wiring (PE has none today — new         │                 domain classes
   infrastructure for this module)          │                          │
          │                                  │                          ▼
          ▼                                  │              2. Dispatch: trips migration +
2. PE: emit events from                       │                 TripRepository
   SetPrimaryConnectionApplicationService/     │                          │
   RemoveConnectionApplicationService            │                          ▼
   (additive)                                     │              3. Dispatch: relocate arrive()/
          │                                        │                 start()/complete() from
          ▼                                         │                 Assignment to Trip
3. Dispatch: PrimaryDriverRecord +                   │                 (behavior-preserving
   migration + repository                             │                 refactor — needs its own
          │                                            │                 focused test coverage)
          ▼                                             │                          │
4. Dispatch: RabbitMQ topology config +                  │                          ▼
   listener(s), consuming PE's new events                 │              4. Dispatch: dual-publish
          │                                                │                 TripArrived/Started/
          ▼                                                │                 Completed alongside the
5. [STOP — Task 11's own boundary,                          │                 existing Assignment*
   Section 5.4] Projection exists                            │                 events (both published,
   and is fed; nothing reads it yet                            │                 neither removed)
                                                                  │                          │
                                                                  │                          ▼
                                                                  │              5. [STOP — Task 11's own
                                                                  │                 boundary] Trip exists,
                                                                  │                 both event names live;
                                                                  │                 Order Management's own
                                                                  │                 consumer is NOT switched
                                                                  │                 yet
                                                                  │
        ┌──────────────────────────────────────────────────────┴───────────────┐
        │                                                                        │
        ▼                                                                        ▼
FUTURE, separate task (not Task 11):                          FUTURE, separate task (not Task 11):
- Decide the trigger/endpoint shape                            - Order Management switches its
  (Section 1.4/3.5)                                               consumer to TripCompleted
- Decide the response-window value                              - Old AssignmentArrived/Started/
- Wire Dispatch's routing logic to                                Completed retired after the
  actually consult PrimaryDriverRecord                            deprecation window
- Consider closing the createProposal
  auth gap (Section 3.5) at the same time
```

**Cross-track note**: the two tracks share no files and no runtime dependency on each other — they may be implemented in either order, or in parallel by different engineers, without coordination beyond the shared migration-numbering convention within `dispatch` (Section 5.1/5.2's own note that the exact next `V__` number for each track must be confirmed against the real latest file at the time each is actually built, not pre-assigned here).

## 7. Gate Status After This Review

Restating `docs/PIOS_TAXI_ARCHITECTURE_CONTRACT.md` Section 21's own eight-item gate:

| # | Item | Status after this review |
|---|---|---|
| 1 | Required ADRs exist | Unchanged — still Proposed, not Accepted |
| 2 | Contract conflicts explicitly resolved | Unchanged — resolved *architecturally* (Sections 1–2 above confirm no real contradiction survives scrutiny), but the source documents (Section 4) still carry the superseded language |
| 3 | Primary Driver ownership unambiguous | Unchanged, confirmed still true |
| 4 | First Refusal contract defined | Unchanged, confirmed true, with one open implementation-detail question sharpened (Section 1.4) rather than newly created |
| 5 | Failure behavior defined | Unchanged |
| 6 | Order/Assignment/Trip boundaries defined | Unchanged, confirmed true, with one wording correction identified for `ADR-063` (Section 2.2) — not yet applied |
| 7 | Network Management boundary defined | Unchanged |
| 8 | No existing ratified decision silently contradicted | **Strengthened by this review** — Sections 1–2 now show, by direct re-reading of the source ADRs rather than by restating the earlier conclusion, that no genuine contradiction exists; the remaining work is purely the documentation-reconciliation pass (Section 4), not a substantive fix |

**Overall: still NOT YET SATISFIED**, for the same reason the Architecture Contract already named — items 1 and 8's own reconciliation pass have not happened. This review adds confidence that when that pass happens, it will be a *transcription* of already-sound reasoning (Sections 1–2), not a discovery of a real problem requiring either ADR to be redesigned.

---

## Final Report

**File created:**
- `docs/PIOS_TAXI_IMPLEMENTATION_READINESS_REVIEW.md`

**Files modified:** none.

**Files deleted:** none.

**Tests/checks executed:** none — documentation/verification-only, no code run, no code written.

**Production databases touched:** NO
**RabbitMQ touched:** NO
**Production services restarted:** NO

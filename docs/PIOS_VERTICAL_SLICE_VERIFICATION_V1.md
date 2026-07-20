# PIOS Vertical Slice Verification v1.0

Status: Final Technical Confidence Checkpoint — End-to-End Architecture Verification Only. This document is **not** a Product Decision, **not** an ADR, and **not** an implementation plan. It verifies, against actual committed code, whether the complete technical path resulting from Tranche 1 (`41b9b24`), Tranche 2 (`182520e`), and the outbox relay hardening (`e01451d`) works as one coherent system, ahead of market validation (the Network Pilot). No production code, test, migration, API, event contract, ADR, or Product Decision is created or modified by this document.

---

## Method: Evidence Grading

- **[RATIFIED]** — stated in an approved document or directly observed in committed code, cited inline.
- **[DERIVED]** — a conclusion reasoned from already-ratified material.
- **[OPEN]** — a genuine gap surfaced, not resolved.

**Inspection basis.** `docs/PIOS_HARDENING_REVIEW_V1.md`, `docs/PIOS_CONSOLIDATION_IMPLEMENTATION_READINESS_REVIEW.md`, `docs/IMPLEMENTATION_PLAN_TRANCHE_1_DISPATCH_EVENT_PUBLISHING.md`, `docs/IMPLEMENTATION_PLAN_TRANCHE_2_PASSENGER_REST_TRANSPORT.md`, `docs/IMPLEMENTATION_PLAN_OUTBOX_RELAY_PRODUCTION_TRIGGER.md`; and actual, currently committed code and tests in all four `backend/` modules, re-read directly for this document rather than assumed from prior review text.

---

## Part 1 — The Vertical Scenario, As The Repository Actually Defines It

Adjusted to current, ratified architecture — no automated Order→Dispatch trigger and no OrderSubmitted consumer exist or are ratified anywhere (EVENT_CATALOG.md Section 8/9; ADR-002/034); this document does not invent either:

1. A passenger's Submit Order request reaches Order Management over real HTTP (`POST /v1/orders`), via Passenger Experience's own REST client (ADR-004/028).
2. Order Management creates the `Order` (`Order.submit()`), persisted to its own PostgreSQL database.
3. The `OrderSubmitted` outbox record is written in the same transaction and automatically relayed (`OutboxRelayScheduler`) to Order Management's own exchange (`order-management.events`, routing key `order.submitted`). **No consumer exists for this event anywhere** — this is the chain's own, already-ratified stopping point for this specific event, not a gap this document invents.
4. Separately, Dispatch receives an order reference and a driver reference through `AssignOrderCommand`, supplied by an external caller — there is no automated trigger connecting step 3 to this step; none is ratified by any document (ADR-002, ADR-034: the assignment trigger and criteria are deliberately undefined pending a future Assignment Policy).
5. Dispatch creates the `Assignment` (`Assignment.create()`), persisted to its own PostgreSQL database.
6. The `OrderAssigned` outbox record is written in the same transaction and automatically relayed to Dispatch's own exchange (`dispatch.events`, routing key `order.assigned`).
7. Driver acceptance occurs (`Assignment.accept()`), persisted in the same transaction as the resulting outbox record.
8. The `AssignmentAccepted` outbox record is automatically relayed to `dispatch.events`, routing key `assignment.accepted`.
9. Order Management's `AssignmentAcceptedListener`, bound to its own queue (`order-management.from-dispatch`), consumes the message, records the `eventId` idempotently, and invokes `OrderAssignmentRecognitionHandler` — which validates the references but does not transition `Order`'s own status (no ratified Order state exists for this yet, by its own design, confirmed unchanged since Tranche 1).

---

## Part 2 — Existing Test Coverage Verification

**Does one existing test already prove the complete chain?** No. Re-verified directly: no single test spans Part 1's steps 1 through 9 using only real transports end-to-end. Two distinct kinds of composition already exist, and neither is what this question asks for:

- `MvpVerticalSliceScenarioTest` (order-management) composes the **entire business sequence** (submit → assign → accept → complete) in one test, across all four modules — but entirely through direct, in-process method calls on in-memory repositories. **Zero real transport** (no HTTP, no RabbitMQ, no scheduler) is used anywhere in it, by its own explicit design (a verification scenario per ADR-027's own precedent).
- Every individual segment below is separately proven against **real PostgreSQL and real RabbitMQ** — but no test chains them together starting from a real HTTP request and ending at a real consumer side-effect.

| Segment | Classification | Evidence |
| --- | --- | --- |
| REST entry | **FULLY VERIFIED** | `OrderSubmissionControllerTest`, `RestClientOrderSubmissionClientTest`, `PassengerOrderSubmissionEndToEndTest` — real HTTP, real controller/client. **Caveat:** these use an in-memory `OrderRepository`, not real PostgreSQL, and no outbox/transaction runner is wired in (`NoOpOutboxRepository`/`NoOpTransactionRunner` defaults apply) — deliberately, to avoid the full-application-boot collision risk Tranche 2's own plan identified. |
| Order creation | **FULLY VERIFIED** | `OrderTest`, `OrderLifecycleApplicationServiceTest`, `PostgreSQLOrderLifecycleTest` — real PostgreSQL, proven independently of the REST leg above. |
| Outbox persistence | **FULLY VERIFIED** | `OrderOutboxTransactionTest` (Order Management), `AssignmentOutboxTransactionTest` (Dispatch) — real PostgreSQL, same-transaction guarantee proven for both producer modules. |
| Automatic relay trigger | **FULLY VERIFIED** | `OutboxRelaySchedulerTest` in all three producer modules (new, `e01451d`) — real PostgreSQL, real RabbitMQ, a genuine, unmocked `@Scheduled` firing with no manual `.relay()` call. **Caveat:** each proves the trigger against a manually-inserted outbox record, not one produced by an actual business call in the same test. |
| RabbitMQ publication | **FULLY VERIFIED** | `RabbitMQEventPublisherTest`, `OrderSubmittedPublicationTest`, `AssignmentEventEnvelopeTest`, plus the scheduler tests above — all against a real broker with publisher confirms. |
| Dispatch processing | **FULLY VERIFIED** | `DispatchAssignmentApplicationServiceTest`, `AssignmentTest` — proven as a direct capability, consistent with Part 1 step 4's own finding that no automated trigger exists to test. |
| Assignment creation | **FULLY VERIFIED** | Same tests, plus `PostgreSQLAssignmentLifecycleTest`, real PostgreSQL. |
| Assignment acceptance | **FULLY VERIFIED** | Same suite; `Assignment.accept()` proven both in isolation and transactionally with its outbox record. |
| Consumer processing | **FULLY VERIFIED, with one named substitution** | `AssignmentAcceptedConsumerIntegrationTest`, `AssignmentAcceptedIdempotencyIntegrationTest`, `AssignmentAcceptedDeadLetterIntegrationTest`, `AssignmentAcceptedProjectionTransactionTest` — real broker, real PostgreSQL idempotency ledger. **Substitution, by deliberate design, not a gap:** the producer side is `AssignmentAcceptedMessagePublisher`, a test-only simulator that publishes exactly Dispatch's own envelope shape — its own KDoc states this is deliberate, so Order Management's test suite never depends on Dispatch's code, mirroring the same module-isolation constraint already established for Dispatch's own `DriverAvailabilityConsumerIntegrationTest`. |

**Overall.** Every individual segment is genuinely, independently proven against real infrastructure. **No test proves the composed whole** — an HTTP-originated order, actually persisted for real, actually auto-relayed, feeding into Dispatch's own real `Assignment.create()`/`accept()` calls, whose own outbox records are actually auto-relayed and actually consumed by Order Management's real listener — as a single, real-transport, real-database chain.

---

## Part 3 — Gap Analysis

**Smallest remaining gap:** the composition described above — connecting the already-separately-proven real-transport segments into one single scenario — does not exist as a test.

**Classification: B — one narrow integration test would close it, but it is not required now.**

Reasoning against the three possible outcomes:

- **Not A.** Existing tests do not, strictly, "already prove enough" to claim the composed whole was verified — they prove every part, never the assembly.
- **B, with an important qualification.** A single cross-module test (mirroring `MvpVerticalSliceScenarioTest`'s own already-established test-scoped cross-module dependency pattern, but replacing every in-process call with the real HTTP/Postgres/RabbitMQ/scheduler path) would close this gap directly. **This document does not add it** (per this task's own instruction) and does not urge it as blocking: every failure mode the composed test would exercise — REST failure handling, outbox transactional consistency, automatic relay, broker delivery, consumer idempotency, retry, dead-lettering — is already independently covered by an existing, real-infrastructure test (Part 2). The composed test's own incremental value is **confidence and documentation of the assembly**, not coverage of a failure mode nothing else catches.
- **Not C.** No architecture problem was found. The gap is a test-composition gap, not a structural defect — every segment already works correctly against real infrastructure; only their combination into one artifact is unproven.

---

## Part 4 — Real Transport Verification

| Concern | Real | Mocked/Stubbed |
| --- | --- | --- |
| HTTP (Submit Order) | **Yes** — `RestClient`/embedded Tomcat, real sockets, real serialization (`OrderSubmissionControllerTest`, `RestClientOrderSubmissionClientTest`, `PassengerOrderSubmissionEndToEndTest`) | — |
| PostgreSQL | **Yes**, for order/assignment/outbox/idempotency persistence tests (`PostgreSQLTestDatabase`, real Flyway-migrated schema) | The REST-leg tests specifically use an **in-memory** `OrderRepository`/`NoOpOutboxRepository` (a deliberate substitution, not a mock framework — plain, hand-written classes already used throughout this codebase) |
| RabbitMQ | **Yes** — every relay/publisher/consumer/DLQ/idempotency test runs against a real, locally-running broker (`RabbitMQTestConnection`), with real publisher confirms | — |
| Scheduler trigger | **Yes** — `OutboxRelaySchedulerTest` uses a real Spring `ApplicationContext` with `@EnableScheduling` genuinely active; the annotation is never faked or invoked directly | — |
| Consumers | **Yes** — `AssignmentAcceptedListener`/`DriverAvailabilityChangedListener` run as real `@RabbitListener` beans inside a real container, with real retry/DLQ | The **producer** side of these specific consumer tests is a hand-built message simulator (`AssignmentAcceptedMessagePublisher`), not Dispatch's own running application — a deliberate module-isolation choice (Part 2), not a stand-in for a broken real path |

**No component anywhere in this chain is verified only through a mock framework** (Mockito, MockK, or similar) — this codebase uses none; every substitution found is either a real, hand-written in-memory implementation of an already-abstracted port (`InMemoryOrderRepository`, `NoOpOutboxRepository`) or a deliberate, documented module-isolation boundary (the message simulator above), never a framework-generated fake standing in for untested real behavior.

---

## Part 5 — Domain Safety Check

Re-confirmed by direct inspection of every file touched by all three engineering milestones (`41b9b24`, `182520e`, `e01451d`): **zero occurrence** of Opportunity, Eligibility, Fair Opportunity Policy, Assignment Policy, Reciprocity, Reputation, or payment/pricing logic anywhere in any diff.

- **Order remains demand representation.** `Order.submit()` is unmodified since before Tranche 1; the REST controller and client added in Tranche 2 are thin wrappers around already-existing, already-tested handlers.
- **Assignment remains commitment representation.** `Assignment.create()`/`Assignment.accept()` are unmodified; Tranche 1 added only a transactional outbox write alongside each. The CREATED/ACCEPTED split (Opportunity/Acceptance/Commitment Product Decision's own finding) is untouched.
- **Dispatch remains decision infrastructure.** No selection or ranking logic was added anywhere; the outbox relay trigger (`e01451d`) is pure transport plumbing, identical in kind across all three producer modules.

**Conclusion: no domain-boundary violation found anywhere across the three milestones.**

---

## Part 6 — Production Confidence Assessment

| Module | Classification | Reasoning |
| --- | --- | --- |
| **Order Management** | **READY FOR MARKET VALIDATION** | Order lifecycle, REST endpoint, and AssignmentAccepted consumption are each solid and now automatically delivered end-to-end (relay gap closed). Remaining, named, non-blocking item: Order's own status still does not transition on AssignmentAccepted (a deliberate, unresolved-by-design gap, not a defect) — genuine future engineering work, not a pilot blocker. |
| **Dispatch** | **READY FOR MARKET VALIDATION** | The most thoroughly tested module in the repository; the relay-scheduling gap that previously affected it is closed. No assignment trigger/criteria exist — deliberately, per ADR-002/034, awaiting pilot evidence and a future Product Decision, not a software defect. |
| **Passenger Experience** | **READY FOR MARKET VALIDATION** | REST client solid and tested; still has no real inbound caller in production (an explicit Tranche 2 non-goal), so it remains reachable only by test code today — genuine future engineering work, not a pilot blocker, since the pilot itself is entirely manual (Part 7). |
| **Driver Management** | **READY FOR MARKET VALIDATION** | Untouched by Tranche 1/2; the relay-scheduling gap it shared with the other two producer modules is now closed identically. |
| **Event infrastructure** | **READY FOR MARKET VALIDATION** | Outbox, publisher-confirm, per-consumer queue + DLQ, bounded retry, idempotency, and now automatic relay triggering are all proven against real infrastructure (Parts 2, 4). The one remaining, explicitly out-of-scope item (multi-instance relay safety, `IMPLEMENTATION_PLAN_OUTBOX_RELAY_PRODUCTION_TRIGGER.md` Part 4) does not apply under the current single-instance-per-module deployment model (ADR-026). |

No module is classified **BLOCKED** or **NEEDS TECHNICAL WORK** relative to the market-validation gate specifically (Part 7 explains why); genuine, named engineering items remain (composed end-to-end test, Order status transition, Passenger Experience's own inbound caller) and are recorded as future work, not gates.

---

## Part 7 — Market Validation Gate

**YES.**

**The decisive reason, stated plainly rather than assumed:** the Network Pilot's own ratified mechanism (`PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md`; the four Network Pilot Product Experiment documents) is **entirely manual** — a human coordinator, driver-to-driver phone/text contact, and a manual case log. It requires **zero software** to run, a fact those documents themselves state directly ("the entire pilot can run on already-existing code... plus 100% manual process"). The technical vertical slice this document verifies and the Network Pilot are, by the Consolidation Review's own already-established framework (`PIOS_CONSOLIDATION_IMPLEMENTATION_READINESS_REVIEW.md` Part 10, "Engineering Track" vs. "Validation Track"), two parallel tracks that converge only at a future evidence gate — the pilot was never gated on this software chain's completeness, and this verification does not newly discover that it should be. The engineering work verified here (Tranches 1–2, hardening) is valuable independent of the pilot's outcome, exactly as that Review's own Part 14 self-challenge already concluded.

**What must NOT be built before pilot evidence exists** (restating, not inventing, the Consolidation Review's own Part 7/Part 13 findings, still current):

- Opportunity (a true multi-candidate structure) — **BLOCKED BY VALIDATION**.
- Eligibility (a formal criterion beyond `Availability`) — **BLOCKED BY VALIDATION**.
- Fair Opportunity Policy (any of the seven candidate mechanisms) — **BLOCKED BY VALIDATION + BLOCKED BY PRODUCT DECISION**.
- Fulfillment Authority's automated mechanism — **BLOCKED BY VALIDATION + BLOCKED BY PRODUCT DECISION**.
- Personal Client Relationship's lifecycle/aggregate — **BLOCKED BY PRODUCT DECISION** (creation, confirmation, exclusivity all unresolved).
- Any automated Order→Dispatch trigger implementing a real assignment criterion (as opposed to the externally-supplied driver this system already correctly supports) — **BLOCKED BY PRODUCT DECISION** (ADR-034's Assignment Policy port remains intentionally unbuilt).

---

## Files Changed (This Verification Task)

`docs/PIOS_VERTICAL_SLICE_VERIFICATION_V1.md` (new) is the only content file this task creates. `docs/README.md` receives one minimal traceability pointer. No production code, test, migration, API, event contract, ADR, or Product Decision is created or modified by this task.

---

Where this document finds no evidence for a claim, it states so explicitly rather than filling the gap. This verification does not authorize implementation, and does not itself constitute a Product Decision or ADR.

# Implementation Plan: Production Outbox Relay Trigger v1.0

Status: Implementation Plan — Architecture Analysis and Planning Only. This document is not a Product Decision, not an ADR, and **not an authorization to write code**. It designs the smallest fix for the gap `docs/PIOS_HARDENING_REVIEW_V1.md` identified: `OutboxRelay.relay()` exists, is fully implemented and tested, and is invoked only by test code — no production trigger exists in `backend/dispatch`, `backend/order-management`, or `backend/driver-management`. This is not a product change; it introduces no Opportunity, Eligibility, Fair Opportunity Policy, Assignment Policy, pricing, or new Order/Assignment semantics. Coding requires separate, explicit approval.

Every claim below is drawn from **actually re-inspected, currently committed code**, not from the Hardening Review's own prior wording assumed still true.

---

## Method: Evidence Grading

- **[RATIFIED]** — stated in an approved document or directly observed in committed code, cited inline.
- **[DERIVED]** — a design choice reasoned from already-ratified/already-proven patterns.
- **[HYPOTHESIS]** — a candidate detail (a property name, a default value) consistent with observed convention, not yet verified by writing the file.
- **[OPEN]** — a genuine implementation choice this plan leaves to the implementer.

---

## Problem Re-Verification (Mandatory, Before Any Design)

Re-run against current `HEAD` (after `eaadf6a`), not assumed from the Hardening Review's prior text:

```
grep -rn "Scheduled\|TaskScheduler\|SchedulingConfigurer\|@EnableScheduling" backend/{dispatch,order-management,driver-management,passenger-experience}/src/main
  -> zero matches
grep -rn "\.relay(" backend/{dispatch,order-management,driver-management}/src/main
  -> zero matches
```

**Finding confirmed, unchanged.** `OutboxRelay` (`@Component`) exists in all three producer modules' `application` package, byte-for-byte structurally identical across modules except package names, exchange names, and KDoc wording (diffed directly: `dispatch`/`order-management`/`driver-management` `RabbitMQEventPublisher.kt` differ only in `EXCHANGE_NAME` and doc comments; `PostgreSQLOutboxRepository.kt` differ only in table name). Each module's `relay()` is invoked exclusively from test code (`OutboxRelayTest`, `OrderSubmittedPublicationTest`/`DriverAvailabilityChangedPublicationTest`/`AssignmentEventEnvelopeTest`-equivalents). No `*Application.kt` (`DispatchApplication`, `OrderManagementApplication`, `DriverManagementApplication`) declares `@EnableScheduling`. **The finding is correct; proceeding to design, not stopping.**

---

## Part 1 — As-Is Analysis

| # | Question | Dispatch | Order Management | Driver Management |
| --- | --- | --- | --- | --- |
| 1 | Where `OutboxRelay` lives | `com.pios.dispatch.application.OutboxRelay` | `com.pios.ordermanagement.application.OutboxRelay` | `com.pios.drivermanagement.application.OutboxRelay` |
| 2 | How `relay()` works | Reads all unpublished records, publishes each via `EventPublisher.publish(routingKey, payload)`, marks published only on success | Identical | Identical |
| 3 | How pending records are selected | `outboxRepository.findUnpublished()` — plain `SELECT ... WHERE published_at IS NULL ORDER BY id ASC`, **unbounded, no `LIMIT`** | Identical query shape, own table (`order_management_outbox`) | Identical query shape, own table (`driver_management_outbox`) |
| 4 | How successful publication is recorded | `outboxRepository.markPublished(id)` — `UPDATE ... SET published_at = now() WHERE id = ?`, called only after `eventPublisher.publish` returns without throwing | Identical | Identical |
| 5 | How failures behave | Per-record `try/catch`: logs a warning, leaves the record unpublished, continues to the next record — one failure never aborts the batch | Identical | Identical |
| 6 | Concurrent relay execution safety (same instance) | Not inherently thread-safe by construction, but nothing in this module currently calls `relay()` concurrently from two threads — the class holds no mutable shared state beyond its constructor-injected collaborators | Identical reasoning | Identical reasoning |
| 7 | Could multiple app instances process the same record | **Yes, unmitigated.** `findUnpublished()` performs no row claiming; two instances polling concurrently would both read the same pending row before either commits `markPublished` | Identical | Identical |
| 8 | Repository locking/claiming | **None exists in any of the three modules.** No `SELECT ... FOR UPDATE [SKIP LOCKED]`, no claimed-by/lease column, no optimistic version column | None | None |
| 9 | Publisher confirms | **Yes, all three.** `RabbitMQEventPublisher.publish` uses `RabbitTemplate.invoke { convertAndSend(...); waitForConfirmsOrDie(5000L) }` — identical 5000ms timeout constant in all three, confirmed by direct diff | Same | Same |
| 10 | Ordering guarantees | **Per-record commit order preserved** within one `relay()` call (`ORDER BY id ASC`); **no guarantee across concurrent instances** or across interleaved publish attempts if one record's publish is slow while a later one's succeeds first — the loop is sequential per call, but nothing prevents two overlapping calls from processing overlapping windows out of relative order | Identical | Identical |

**Cross-module comparison, summary.** All three modules are **structurally identical, module-local copies** — confirmed by direct `diff` of `RabbitMQEventPublisher.kt` (differs only in `EXCHANGE_NAME` and doc comments) and of `PostgreSQLOutboxRepository.kt` (differs only in table name). No shared library exists or is referenced; this is consistent with MODULE_STRUCTURE.md's no-shared-module rule and ADR-032's own "no shared outbox table or relay service spanning multiple domains" (Alternatives Considered). The trigger gap is identical in kind and severity across all three.

---

## Part 2 — Determine the Smallest Correct Trigger

**Candidates evaluated:**

- **Spring `@Scheduled`.** **[SELECTED]** Already available with zero new dependency: `spring-boot-starter-web` (present in all three modules' `build.gradle.kts`) transitively includes `spring-context`, which provides `@Scheduled`/`@EnableScheduling`/the default `TaskScheduler`. No new Gradle dependency required in any module.
- **`SchedulingConfigurer`/custom `TaskScheduler` bean.** Considered and rejected for this task specifically: this exists to customize thread-pool sizing, clock source, or trigger logic beyond a fixed/cron expression — no requirement here justifies that complexity. The default single-thread `TaskScheduler` Spring provides for `@Scheduled` methods is sufficient for one relay tick per module.
- **Quartz, Kafka, a workflow engine.** Explicitly excluded by this task's own instruction and unjustified by any existing authority — no approved document names any of these, and introducing one would be a disproportionate new infrastructure dependency for triggering a single periodic method call, contradicting No Premature Optimization (ENGINEERING_GUIDELINES.md Section 3, cited throughout ADR-030/031/032).

**Where the trigger belongs.** **[DERIVED]** A new, thin class per module — `OutboxRelayScheduler` (module-local, `com.pios.<module>.application` package, mirroring `OutboxRelay`'s own package) — holding a single `@Scheduled` method that calls the already-existing, already-tested `outboxRelay.relay()`. **Not** added as an annotation directly on `OutboxRelay.relay()` itself: keeping the trigger as a separate, thin wrapper leaves the already-proven `OutboxRelay` class (and every existing test constructing it directly, outside a Spring context) completely untouched — zero risk of the annotation processing interfering with existing direct-construction tests, and a cleaner separation between "what to do" (`OutboxRelay`) and "when to do it" (`OutboxRelayScheduler`). **[OPEN]** the implementer may instead annotate `relay()` directly if a future review finds the extra class unjustified; this plan recommends the separate class as the safer default.

**Does each producer module own its own trigger?** **[DERIVED, yes.]** Consistent with ADR-026's independent-deployability guarantee and ADR-031/032's per-domain exclusive-ownership principle already extended to outbox tables and relays — a shared, cross-module scheduler would recreate exactly the shared-infrastructure pattern ADR-031/032 already reject for exchanges, queues, and outbox tables. Three separate, module-local `OutboxRelayScheduler` classes, one per producer module.

**Independently configurable?** **[DERIVED, yes.]** Each module's own `application.yml` gains its own `pios.outbox.relay.*` property block (Part 5), independently overridable per module in production — no shared configuration file, consistent with each module's already-independent `application.yml`.

**Default interval strategy.** **[HYPOTHESIS, a configuration default, not a product invariant]** `fixedDelay`, not `fixedRate` — `fixedDelay` waits for one execution to complete before scheduling the next from that completion time, which cannot overlap with itself even if a given tick runs long (e.g., a slow broker). A default of **2000ms** is proposed: the same order of magnitude already used elsewhere in this codebase for a related concern (`RabbitMQEventPublisher`'s own 5000ms confirm timeout), but explicitly a tunable default, not fixed here as a requirement — the implementer or operator may override it per module without any code change.

**Startup behavior.** **[DERIVED]** Spring's own default for `@Scheduled(fixedDelay = ...)`: the first execution occurs once the `ApplicationContext` has fully refreshed (all beans, including `DataSource`/Flyway migration and `RabbitTemplate`, are already initialized by then) — no artificial `initialDelay` is required for correctness. An explicit `initialDelay` is left as a future, optional tuning knob (Part 5), not a requirement.

**Failure isolation.** **[RATIFIED, inherited unchanged]** `OutboxRelay.relay()`'s own existing per-record `try/catch` already isolates one record's publish failure from the rest of the batch (Part 1, row 5) — the scheduler adds no new failure-isolation logic of its own; it only needs to ensure one tick's own uncaught exception (if `relay()` itself ever threw, which it currently does not — every exception inside its loop is already caught) does not deregister the `@Scheduled` method from future execution. Spring's own `@Scheduled` infrastructure already guarantees this: an uncaught exception from one invocation is logged by Spring and the next scheduled invocation still fires — no additional code is needed to satisfy this, but it is named here as an explicit acceptance criterion (Part 9) rather than assumed silently.

---

## Part 3 — Do Not Over-Abstract

**Evaluated: A (module-local) vs. B (shared library).**

**Evidence for A (module-local), decisive:**

- ADR-027 explicitly rejects "a shared 'integration' or 'contracts' Gradle subproject holding common event or DTO types" for this exact class of problem, calling it out by name as a rejected alternative.
- ADR-031/032 both explicitly reject a shared exchange, shared queue, or shared outbox table/relay service spanning multiple domains, in their own Alternatives Considered sections, for the identical reasoning: exclusive per-domain ownership (ADR-005, ADR-009, ADR-025) would otherwise be contradicted.
- MODULE_STRUCTURE.md Section 2 (Low Coupling) and the repository's own `settings.gradle.kts` comment ("No shared module is declared here; each module depends on no other") are both direct, current, structural evidence — not merely historical — that this repository has never introduced a shared library for any cross-cutting infrastructure concern, including outbox/relay code that is already, today, three near-identical module-local copies.
- The three `OutboxRelay`/`PostgreSQLOutboxRepository`/`RabbitMQEventPublisher` classes are already near-identical **and this was already true before this task**, deliberately, per the same reasoning — this task does not newly discover a DRY opportunity; it inherits an already-deliberate repetition.

**Conclusion: A — module-local.** No concrete architectural need for extraction exists now: no module is about to diverge in its relay behavior, no operational cost has been demonstrated from the duplication, and the four-module skeleton is still small enough that "three near-identical classes" is a proportionate cost, not a maintenance burden. Recommending extraction here would be speculative DRY refactoring, exactly what this task's own Part 3 instructs against.

---

## Part 4 — Concurrency and Delivery Safety (Mandatory)

**Single instance:**

- **Overlapping scheduled executions.** Prevented by using `fixedDelay` (Part 2) rather than `fixedRate` — Spring's own `ScheduledAnnotationBeanPostProcessor`, backed by the default single-threaded `TaskScheduler`, does not begin the next `fixedDelay` execution until the current one returns. A slow RabbitMQ publish within one tick simply delays the next tick; it does not cause two ticks to run at once.
- **Slow RabbitMQ publishing.** Each `publish()` call blocks on `waitForConfirmsOrDie(5000L)`; a slow or degraded broker lengthens one tick's duration but — because of the `fixedDelay` guarantee above — cannot cause concurrent ticks within the same instance.
- **Failure during a batch.** Already handled by `OutboxRelay`'s own existing per-record `try/catch` (Part 1, row 5), unchanged by this plan: one record's failure is logged, that record remains unpublished for the next tick, and the loop continues to the remaining records in the same tick.

**Multiple instances (a genuine, distinct concern from single-instance safety):**

- **Two instances reading the same pending outbox row.** **Confirmed possible, unmitigated at the repository level** (Part 1, rows 7–8: no `SELECT ... FOR UPDATE`, no claim/lease column, no optimistic lock). If module X is ever horizontally scaled to more than one running instance, two instances' own `OutboxRelayScheduler` ticks could both call `findUnpublished()`, both see the same unpublished row, and both attempt to publish it before either instance's `markPublished` commits.
- **Duplicate publication.** A direct consequence of the above: the same event (same `eventId`, since `eventId` is generated once at outbox-write time — Part 3 of the Tranche 1 plan already established this — not regenerated per publish attempt) could be delivered to the broker more than once.
- **Marking publication state.** `markPublished(id)` is a plain, idempotent `UPDATE ... SET published_at = now() WHERE id = ?` — safe to execute more than once for the same id (the second execution is a harmless no-op write), so this step itself introduces no corruption, only redundant work.
- **Crash after broker publish but before local mark-as-published.** Already an accepted, designed-for case, unchanged by this plan: `OutboxRelay`'s own KDoc states the record "is simply retried on the next call to `relay`" — this is precisely the at-least-once guarantee ADR-029/031/032 already select and require. Adding a scheduler does not change this; it only makes the already-designed-for retry path actually exercise automatically instead of only via a manually-invoked test call.

**Delivery guarantee this design can actually provide, precisely stated:**

**At-least-once delivery to the broker, with effectively-once business effect only because every current consumer is required to be idempotent** (ADR-031: "Consumers must be idempotent... a direct, required consequence of choosing at-least-once delivery, not an optional recommendation"; proven in code by every consumer's own `markProcessed(eventId)` idempotency ledger). **This plan does not claim, and cannot truthfully claim, exactly-once delivery** — no row-claiming mechanism exists to prevent a second instance from re-attempting a record another instance already published, and the broker-and-database pair here has no distributed transaction spanning both.

**Does safe multi-instance operation require a larger change than merely adding a scheduler? Yes — flagged, not silently expanded into this task's scope.**

Concretely, safe multi-instance relay execution (avoiding *routine* duplicate broker publication under horizontal scaling, as opposed to the already-accepted *rare* crash-window duplicate) would require row-claiming — for example, `SELECT ... FOR UPDATE SKIP LOCKED` in `findUnpublished()`, or a claimed-by/lease-expiry column — a change to the `OutboxRepository` contract and its PostgreSQL adapter in all three modules, materially larger than adding a scheduler. **This plan explicitly does not include that change.** It is named here as a distinct, future hardening item, justified precisely: ADR-026 itself states independent scaling "enables independent scaling as a capability, it does not require exercising it for every module on day one" — no approved document currently requires more than one running instance per module, so this gap does not block the narrow trigger this plan designs, but it must not be silently assumed solved by it either.

---

## Part 5 — Configuration

**Required now:**

- **`pios.outbox.relay.fixed-delay-ms`** — the interval, in milliseconds, between the end of one relay tick and the start of the next. **[HYPOTHESIS default: `2000`]**, overridable per module via `application.yml` or an environment variable, following the same `@Value("\${...}")` convention already used in this codebase (`OrderManagementRestClientConfiguration`'s own `pios.order-management.base-url`, Tranche 2). Required because `@Scheduled(fixedDelayString = "...")` needs a concrete value; a literal, unconfigurable constant would make the interval untunable without a code change and a redeploy, which is unnecessary given Spring's own externalized-configuration mechanism is already the established pattern in this codebase.

**Not required now — future operational tuning, named so it is not silently forgotten nor prematurely built:**

- **Enabled/disabled flag** (`pios.outbox.relay.enabled`). Not included in this plan's required scope: the same effect (stopping delivery) can already be achieved by not deploying this change, or by setting an impractically large delay; no demonstrated operational need for a live toggle exists yet.
- **Batch size / `LIMIT` on `findUnpublished()`.** Not included: `findUnpublished()` remains unbounded, exactly as it is today and as every existing test already exercises it — introducing a `LIMIT` would require changing the `OutboxRepository` interface's contract in all three modules, a larger change than this task's own narrow scope, and no approved document or observed data volume currently justifies it (No Premature Optimization).
- **Explicit `initialDelay`.** Not included: Spring's own default (first tick after context refresh, Part 2) is already correct; an explicit initial delay is a future tuning knob only, relevant if a thundering-herd concern is ever demonstrated across multiple instances of the same module (itself gated on the multi-instance question already flagged as out of scope, Part 4).

---

## Part 6 — File-Level Implementation Plan

**Files likely created (one per producer module, three total):**

- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/OutboxRelayScheduler.kt`
- `backend/order-management/src/main/kotlin/com/pios/ordermanagement/application/OutboxRelayScheduler.kt`
- `backend/driver-management/src/main/kotlin/com/pios/drivermanagement/application/OutboxRelayScheduler.kt`

Each: a `@Component` with a single `@Scheduled(fixedDelayString = "\${pios.outbox.relay.fixed-delay-ms:2000}")` method calling `outboxRelay.relay()`, discarding the returned list (already logged internally by `OutboxRelay` itself, Part 1 row 5) — no new logic, a pure trigger.

**Files likely modified:**

- `backend/dispatch/src/main/kotlin/com/pios/dispatch/DispatchApplication.kt`
- `backend/order-management/src/main/kotlin/com/pios/ordermanagement/OrderManagementApplication.kt`
- `backend/driver-management/src/main/kotlin/com/pios/drivermanagement/DriverManagementApplication.kt`

Each gains `@EnableScheduling` alongside its existing `@SpringBootApplication` annotation — the minimal, standard Spring Boot idiom for activating `@Scheduled` processing; no other change to any of the three files.

- `backend/dispatch/src/main/resources/application.yml`
- `backend/order-management/src/main/resources/application.yml`
- `backend/driver-management/src/main/resources/application.yml`

Each gains one new `pios.outbox.relay.fixed-delay-ms` property (Part 5), mirroring the existing `pios.*` convention Tranche 2 already introduced for Passenger Experience.

**Files intentionally NOT modified — and why:**

- `OutboxRelay.kt` (all three modules) — already correct and already tested; this plan adds a caller, never changes its behavior (Part 2's own reasoning for keeping the trigger a separate class).
- `OutboxRepository.kt`/`PostgreSQLOutboxRepository.kt` (all three) — no row-claiming change is in this plan's scope (Part 4's explicit flag, not a silent inclusion).
- `EventPublisher.kt`/`RabbitMQEventPublisher.kt` (all three) — publisher-confirm behavior is already correct and unrelated to triggering.
- `passenger-experience` — has no outbox or `OutboxRelay` of its own (it owns no event, EVENT_CATALOG.md Section 8); entirely untouched by this plan.
- Any domain class (`Order`, `Assignment`, `Driver`), any event, any migration, any ADR, any Product Decision — no change of any kind.
- Any existing test — this plan adds new tests (Part 7) but modifies none of the 182 already passing.

---

## Part 7 — Test Plan

**Constraint acknowledged explicitly.** Every other component in this codebase is tested by direct construction outside a Spring container (ADR-013's own established convention, reused verbatim in the Hardening Review's own Part 5 findings). `@Scheduled` is a deliberate, narrow exception to that convention: whether an annotated method is actually invoked periodically is governed entirely by Spring's own `ScheduledAnnotationBeanPostProcessor`, which only activates inside a real `ApplicationContext` with `@EnableScheduling` — direct construction of `OutboxRelayScheduler` cannot exercise this behavior at all. Proving the trigger genuinely fires therefore requires at least one real, running (if narrow) Spring context — named here so it is not silently smuggled in as "business as usual," and not avoided by weakening the proof.

**Tests to add, evaluated against the task's own eight minimum items:**

1. **Scheduled trigger invokes `relay()` automatically.** Required — the entire point of this task. Design: a narrow, test-only `@Configuration` class (mirroring Tranche 2's own `TestOrderSubmissionWebConfiguration` precedent — a minimal context registering only what is needed, not the full `*Application`) registering `@EnableScheduling`, the real `OutboxRelayScheduler`, the real `OutboxRelay`, a real `PostgreSQLOutboxRepository` (real `PostgreSQLTestDatabase`), and a real `RabbitMQEventPublisher` (real `RabbitMQTestConnection`) with a short `fixed-delay-ms` (for example, 200ms, test-only) so the test does not wait long. **Deliberately narrow, not the full `*Application`**, for the same reason Tranche 2 avoided it: booting the complete `OrderManagementApplication` from within `order-management`'s own test suite would also start the real `AssignmentAcceptedListener`, a genuine competing consumer against the same real queue `AssignmentAcceptedConsumerIntegrationTest`/`AssignmentAcceptedIdempotencyIntegrationTest`/`AssignmentAcceptedDeadLetterIntegrationTest` already manipulate directly in the same test JVM (JUnit 5 runs test classes sequentially by default, confirmed — no `junit-platform.properties` enabling parallel execution exists in this repository — but a `@RabbitListener` container's own background consumer thread, once started, keeps running independently of which test method is currently executing, for the remainder of the test JVM's life). Scoping this new context to Order Management's own **producer** side only (which has no competing consumer for `order-management.events` today, Hardening Review Part 1) avoids this collision entirely.
2. **Pending outbox record reaches the publisher without the test manually calling `relay()`.** Combined with item 1: the test writes an outbox record (or, better, drives it through a real business call — for example, `OrderLifecycleApplicationService.submitOrder`, mirroring `OrderSubmittedPublicationTest`'s own existing setup exactly) and then polls (using the already-established `awaitUntilNotNull` helper from `Polling.kt`) for the record to become published and for a real `RabbitMQTestObserverQueue` to receive it — **never calling `.relay()` itself**, the one change from `OrderSubmittedPublicationTest`'s existing pattern that actually proves this task's own value.
3. **Successful publication marks the record correctly.** Covered by the same test: assert `outboxRepository.findUnpublished()` no longer contains the relayed record's id, mirroring `OutboxRelayTest`'s own existing assertion style.
4. **Publisher failure leaves the event recoverable.** Already proven at the unit level by `OutboxRelay`'s own existing behavior (Part 1 row 5) and not dependent on the scheduler; **no new test required** — re-asserting this would duplicate existing coverage (`OutboxRelayTest` and its cross-module equivalents) without proving anything new about the trigger itself.
5. **Subsequent trigger retries a pending event.** Worth one small addition: using the same narrow scheduling context, write a record while the real `RabbitMQEventPublisher`'s target queue is temporarily unavailable (or more simply, assert against the already-proven fact that `relay()`'s own retry-next-poll behavior is unit-tested, and add only a timing assertion that a *second* scheduled tick — not the first — is what eventually relays a record inserted just after the first tick already started) — **[OPEN]**, left to the implementer to decide the simplest deterministic way to prove "the second tick, not just the first, still fires," without inventing artificial broker outages that duplicate existing failure-path coverage.
6. **Trigger failure does not permanently stop future executions.** Spring's own guarantee (Part 2); the most direct proof without fighting the framework is to confirm — by code inspection, not a bespoke test — that no code path introduced by this plan wraps `relay()` in anything that could rethrow past `@Scheduled`'s own boundary in a way Spring does not already handle. **[OPEN]**, a targeted test here would mostly test Spring itself rather than this plan's own code; the implementer should judge whether a minimal test (a scheduler tick that throws, followed by a second successful tick) earns its cost.
7. **Duplicate/concurrency behavior matches documented delivery semantics.** Already fully proven at the consumer/idempotency level for the two real cross-module event flows (`AssignmentAcceptedIdempotencyIntegrationTest` and its `DriverAvailabilityChanged` equivalent); this plan does not add a new multi-instance-relay test, since multi-instance operation is explicitly out of scope (Part 4) — asserting behavior for a scenario this plan does not implement would be misleading, not thorough.
8. **Existing 182-test baseline remains green.** Mandatory, full `./gradlew build` re-run after implementation, exactly as Tranche 1 and Tranche 2 both already did.

**One real integration test proving the full chain, as the task requires.** Yes — item 2 above, precisely: real (narrow) Spring context → real `PostgreSQLTestDatabase` → a real business call producing a durable outbox record → the real, unmocked, annotation-driven scheduler fires on its own → the real `RabbitMQEventPublisher` delivers to the real broker → a real `RabbitMQTestObserverQueue` observes it. **The scheduler itself is never faked, stubbed, or invoked directly by the test** — satisfying this task's own explicit instruction.

---

## Part 8 — Scope Boundary

This plan explicitly introduces none of: Opportunity, Eligibility, Fair Opportunity Policy, Assignment Policy, Reciprocity, Reputation, pricing/payment logic, or any new Order/Assignment semantics — confirmed by Part 6's own file list, which touches only a new trigger class, an `@EnableScheduling` annotation, and one configuration property per module.

This plan does not redesign the messaging platform. RabbitMQ, the topic-exchange-per-domain topology, publisher confirms, retry/DLQ, and the idempotency ledger (ADR-029 through ADR-032) are all reused completely unchanged. The only new capability is: something now actually calls the already-existing `relay()` method periodically, in production, without a human or a test doing so manually.

---

## Part 9 — Acceptance Criteria

- All three producer modules (Dispatch, Order Management, Driver Management) have a production mechanism (`@Scheduled`, activated via `@EnableScheduling`) that automatically triggers pending outbox publication, with no test or manual invocation required.
- The trigger's interval is externally configurable per module (`pios.outbox.relay.fixed-delay-ms`), with a documented default.
- A failure during one relay tick (a single record's publish failure, or a tick-level exception) does not permanently stop future scheduled executions — Spring's own guarantee, explicitly verified rather than assumed (Part 7, item 6).
- Failures remain retryable according to the already-established semantics: an unpublished or unconfirmed record is retried on a subsequent tick, exactly as `OutboxRelay`'s own existing, unmodified behavior already guarantees.
- The precise delivery guarantee actually provided — **at-least-once to the broker, effectively-once business effect via mandatory idempotent consumers, not exactly-once** — is stated in the implementation's own documentation (KDoc on `OutboxRelayScheduler`), not left implicit.
- Multi-instance safety is explicitly named as unaddressed by this specific change (Part 4), not silently implied as solved.
- No existing event contract, envelope shape, routing key, exchange name, or consumer behavior changes.
- No domain behavior (`Order`, `Assignment`, `Driver`) changes.
- The full test suite (182 existing tests plus the new scheduler-proof tests, Part 7) passes, run against real PostgreSQL and RabbitMQ, consistent with this project's established testing philosophy.
- `passenger-experience` remains entirely untouched (it owns no outbox).

---

## Part 10 — Go / No-Go

**GO — safe to implement as a narrow hardening tranche.**

**Exact implementation boundary:** exactly the files listed in Part 6 — one new `OutboxRelayScheduler.kt` per producer module, one `@EnableScheduling` addition per module's `*Application.kt`, one new configuration property per module's `application.yml`, plus the new tests in Part 7. Nothing in `OutboxRelay.kt`, `OutboxRepository.kt`, `EventPublisher.kt`, any domain class, any migration, any event contract, any ADR, or `passenger-experience` is touched.

**No deeper architectural issue blocks this.** The one genuine, larger concern this review surfaced — safe multi-instance relay execution requiring row-claiming (Part 4) — is real, but does not block a single-instance-per-module production deployment, which is the only deployment model any currently approved document (ADR-026, explicitly: "does not require exercising [independent scaling] for every module on day one") requires or assumes today. It is recorded here as a named, future, distinct hardening item, not folded into this task's scope and not silently ignored.

---

## Files Changed (This Planning Task)

`docs/IMPLEMENTATION_PLAN_OUTBOX_RELAY_PRODUCTION_TRIGGER.md` (new) is the only content file this task creates. `docs/README.md` receives one minimal traceability pointer. No production code, test, migration, API, event contract, ADR, or Product Decision is created or modified by this task.

---

Where this plan states a **[HYPOTHESIS]**-graded detail (the default interval, the exact property name) or an **[OPEN]** item, it is a recommendation or a deliberately deferred implementation choice, not a ratified fact — the implementer confirms or adjusts it before writing code. Acting on this plan requires separate, explicit implementation approval.

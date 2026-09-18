package com.pios.dispatch.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.dispatch.domain.Assignment
import com.pios.dispatch.domain.AssignmentAccepted
import com.pios.dispatch.domain.AssignmentArrived
import com.pios.dispatch.domain.AssignmentCompleted
import com.pios.dispatch.domain.AssignmentCreated
import com.pios.dispatch.domain.AssignmentId
import com.pios.dispatch.domain.AssignmentStarted
import com.pios.dispatch.domain.OrderAssigned
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.domain.Trip
import com.pios.dispatch.domain.TripArrived
import com.pios.dispatch.domain.TripCompleted
import com.pios.dispatch.domain.TripStarted
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Application-layer coordination for the Assign Order and Accept
 * Assignment commands (APPLICATION_ARCHITECTURE.md Section 6). This
 * service sequences each command into the Assignment aggregate's own
 * behavior; it does not decide the assignment or its acceptance itself
 * (APPLICATION_ARCHITECTURE.md Section 2, "Domain Decides Business
 * Meaning"). After each transition, the service persists the affected
 * [Assignment] *and* a corresponding [OutboxRecord] together, inside one
 * [transactionRunner] boundary (ADR-032, Tranche 1: Dispatch Event
 * Publishing Completion) — so the two either both commit or both roll
 * back, closing for Dispatch's own events the same dual-write gap already
 * closed for Order Management's and Driver Management's own events. The
 * [Assignment] aggregate itself remains entirely unaware that a
 * repository or an outbox exists.
 *
 * [outboxRepository] and [transactionRunner] default to no-ops so that
 * existing tests exercising only Assignment lifecycle behavior (not the
 * outbox) continue to work unchanged — mirroring exactly the same default
 * already proven for Order Management's own
 * `OrderLifecycleApplicationService` and Driver Management's own
 * `DriverAvailabilityApplicationService`; a real, Spring-wired instance
 * of this service always receives the real
 * [com.pios.dispatch.persistence.PostgreSQLOutboxRepository] and
 * [com.pios.dispatch.persistence.SpringTransactionRunner] beans instead,
 * since Spring's constructor injection always supplies an argument for a
 * parameter it can resolve a bean for, regardless of a Kotlin default
 * being present.
 *
 * [existingAssignments] is still supplied by the caller, since
 * [AssignmentRepository] offers lookup by id but no query capability
 * beyond that. [acceptAssignment] is overloaded: one form still operates
 * on an [Assignment] instance supplied by the caller, the other restores
 * the [Assignment] from [assignmentRepository] by id first, proving the
 * aggregate can be saved, loaded back, and continue its own domain
 * operation exactly as it would if it had never left memory.
 *
 * ## Trip creation (ADR-063; Task 11, Trip Domain Foundation)
 *
 * [handle] and [handleWithinCallerTransaction] each create the connected
 * [Trip] immediately after the [Assignment] itself, inside the same
 * [transactionRunner] boundary — the authoritative, corrected (Task 11A)
 * trigger for Trip's own creation is `OrderAssigned`, i.e. the moment
 * [Assignment.create] succeeds, not [AssignmentAccepted] (never actually
 * published by any live production path — see `ADR-063`'s own Status
 * section) and never a read of [Assignment.status]. Both real production
 * paths that create an Assignment — [AssignmentController.assignOrder]
 * (via the self-fetching [handle] overload) and
 * [ProposalAssignmentOrchestrationService.acceptProposal] (via
 * [handleWithinCallerTransaction]) — therefore also create a Trip,
 * without either of those two call sites needing any change themselves.
 * [tripRepository] defaults to a no-op for the same reason
 * [outboxRepository]/[transactionRunner] do — existing tests exercising
 * only Assignment behavior continue to work unmodified.
 *
 * ## Ride-progress convergence onto Trip (ADR-063; Task 12, Trip Ride-Progress Convergence)
 *
 * [arriveAssignment], [startAssignment] and [completeAssignment] no longer
 * transition [Assignment] itself — they transition the connected [Trip]
 * instead, via [tripFor], which is now the sole validator of these three
 * moves ([Trip.arrive]/[Trip.start]/[Trip.complete]'s own `check()`
 * preconditions). [Assignment]'s own `arrive`/`start`/`complete` methods,
 * its `arrivedAt`/`startedAt`/`completedAt` fields, and its `ARRIVED`/
 * `IN_PROGRESS`/`COMPLETED` [AssignmentStatus] values are **not deleted**
 * — ADR-063's Decision section calls for their eventual removal, but Task
 * 12's own Phase 3 instruction is more specific and takes precedence for
 * this implementation: "Do NOT delete existing Assignment fields ...
 * Only remove old behavior after proving that no active consumer depends
 * on it." A live consumer does: `AssignmentController.arrive/start/complete`
 * are called today by `DriverHome.tsx`'s own `respondToAssignment`, and
 * `AssignmentStatus.ARRIVED/IN_PROGRESS/COMPLETED` rows already exist in
 * the pilot's own PostgreSQL data from before this change. Deleting the
 * fields now would silently strand that historical data. They are
 * therefore kept, verbatim, simply no longer written to by this service —
 * a frozen, read-only remnant of pre-Trip history, not a second live
 * source of truth. `AssignmentTest.kt`'s own direct unit tests of those
 * methods continue to pass unmodified, since `Assignment.kt` itself is not
 * touched by this convergence at all.
 *
 * [tripFor] self-heals: an [Assignment] saved before Trip existed (or
 * through any path that predates this wiring) has no connected [Trip] yet
 * — [tripFor] creates one on first ride-progress action, exactly as
 * [Trip.create] would have at Assignment-creation time, so a legacy
 * Assignment can still be carried through its ride lifecycle without
 * requiring a backfill migration.
 *
 * Each transition still also publishes the *original* `Assignment*` outbox
 * event (`AssignmentArrived`/`AssignmentStarted`/`AssignmentCompleted`,
 * unchanged `eventType`/routing key), alongside the new `Trip*` one — the
 * dual-publish migration window ADR-063's own Consequences section
 * requires, so Order Management's real, already-wired
 * `AssignmentCompletedListener` (bound to routing key
 * `assignment.completed`) keeps receiving exactly what it always has,
 * unmodified, per this task's own Order Management Rule.
 *
 * ## Agreed amount capture and propagation (D-06, Settlement as Evidence;
 * supersedes ADR-065 Decision items 2-3 -- see the dated amendment to
 * that ADR)
 *
 * [completeAssignment] no longer looks up any
 * [com.pios.dispatch.domain.Proposal] at completion time. The amount an
 * `AssignmentCompleted` event carries is read from the completing
 * [Trip]'s own [com.pios.dispatch.domain.Trip.agreedAmount] -- captured
 * once, at Trip creation ([createTripFor], from [AssignOrderCommand.agreedAmount],
 * itself sourced by [ProposalAssignmentOrchestrationService] from the
 * just-accepted/-confirmed Proposal inside the same shared transaction --
 * never re-derived later. `null` when the originating Trip has none:
 * the direct `POST /v1/assignments` manual path (no Proposal at all), a
 * self-healed Trip ([tripFor]'s own fallback, which also supplies no
 * Proposal), or a Trip created before this field existed (no backfill,
 * D-06 Decision item 7). Still forwarded, verbatim and unparsed, as
 * `payload.statedPrice` -- the field name is unchanged for backward
 * compatibility (D-06 Decision item 12); only its source changed. Never
 * published on `TripCompleted` (unchanged from ADR-065 Decision item 2).
 * `eventVersion` stays 1: both Order Management's and Driver Management's
 * own consumers hard-require it, and this field remains additive and
 * optional.
 */
@Service
class DispatchAssignmentApplicationService(
    private val assignmentRepository: AssignmentRepository,
    private val outboxRepository: OutboxRepository = NoOpOutboxRepository,
    private val transactionRunner: TransactionRunner = NoOpTransactionRunner,
    private val objectMapper: ObjectMapper = ObjectMapper(),
    private val tripRepository: TripRepository = NoOpTripRepository,
    private val orderGuard: OrderGuard = NoOpOrderGuard,
    private val dispatchRequestRepository: DispatchRequestRepository? = null
) {

    fun handle(
        command: AssignOrderCommand,
        existingAssignments: Collection<Assignment> = emptyList()
    ): AssignmentCreated = transactionRunner.run {
        orderGuard.lock(command.order)
        checkOrderIsAssignable(command.order)
        val created = Assignment.create(
            order = command.order,
            driver = command.driver,
            existingAssignments = existingAssignments
        )
        assignmentRepository.save(created.assignment)
        outboxRepository.save(outboxRecordFor(created.assignment.id, created.event))
        createTripFor(created.assignment, command.agreedAmount)
        created
    }

    /**
     * Handles [command] by first restoring the order's own existing
     * assignments through [assignmentRepository] itself, inside the same
     * [transactionRunner] boundary as the rest of this method (Milestone
     * 14A) — instead of requiring the caller to read them beforehand and
     * pass the result in, as the other [handle] overload still does for
     * callers that already have that collection on hand (for example,
     * [com.pios.dispatch.api.AssignmentController.assignOrder]). Closes
     * the window in which a caller's own pre-fetched read could become
     * stale before this method's write.
     */
    fun handle(command: AssignOrderCommand): AssignmentCreated = transactionRunner.run {
        handleWithinCallerTransaction(command)
    }

    /**
     * Identical to [handle] (the single-argument, self-fetching overload)
     * except that it does not open its own [transactionRunner] boundary —
     * for [ProposalAssignmentOrchestrationService] to call from inside the
     * one shared transaction it now owns for Accept Proposal (ADR-036).
     * Calling this from outside an already-open transaction would leave
     * the existing-assignments read, the [Assignment] write, and the
     * outbox write uncommitted-atomic only by accident; every other caller
     * must keep using [handle] instead.
     */
    fun handleWithinCallerTransaction(command: AssignOrderCommand): AssignmentCreated {
        orderGuard.lock(command.order)
        checkOrderIsAssignable(command.order)
        val existingAssignments = assignmentRepository.findByOrder(command.order)
        val created = Assignment.create(
            order = command.order,
            driver = command.driver,
            existingAssignments = existingAssignments,
            isTest = command.isTest
        )
        assignmentRepository.save(created.assignment)
        outboxRepository.save(outboxRecordFor(created.assignment.id, created.event))
        createTripFor(created.assignment, command.agreedAmount)
        return created
    }

    /**
     * Creates and persists the [Trip] connected to [assignment], per the
     * corrected (Task 11A) `OrderAssigned` trigger described in this
     * class's own KDoc. Idempotent: checks [tripRepository] for an
     * already-existing Trip for this Assignment first — mirroring
     * [Assignment.create]'s own `existingAssignments` check — so a
     * duplicate invocation for the same Assignment (however it might
     * arise) never creates a second Trip; `trips.assignment_id`'s own
     * database-level `UNIQUE` constraint (V12 migration) is the
     * persistent backstop this in-memory check alone cannot be.
     *
     * [agreedAmount] (D-06) is captured onto the new Trip exactly as
     * supplied by the caller — this method does not look it up, does not
     * read a Proposal, and does not compute it. See this class's own
     * "Agreed amount capture and propagation" KDoc.
     */
    private fun createTripFor(assignment: Assignment, agreedAmount: String? = null) {
        val existingTrip = tripRepository.findByAssignmentId(assignment.id)
        val tripCreated = Trip.create(assignment, agreedAmount, existingTrip)
        tripRepository.save(tripCreated.trip)
    }

    private fun checkOrderIsAssignable(order: OrderReference) {
        val state = dispatchRequestRepository?.findForUpdate(order.orderId)?.state
        check(state != DispatchRequestState.CANCELLED && state != DispatchRequestState.UNFULFILLED) {
            "Order ${order.orderId} is no longer assignable"
        }
    }

    /**
     * Confirms the given [assignment], per the Accept Assignment command.
     * [assignment] must be the one referenced by [command] — the caller
     * is responsible for finding it, since [AssignmentRepository] offers
     * lookup by id but no query capability beyond that.
     */
    fun acceptAssignment(
        assignment: Assignment,
        command: AcceptAssignmentCommand
    ): AssignmentAccepted = transactionRunner.run {
        orderGuard.lock(assignment.order)
        require(assignment.id == command.assignmentId) {
            "Assignment ${assignment.id.value} does not match command target ${command.assignmentId.value}"
        }
        val event = assignment.accept()
        assignmentRepository.save(assignment)
        outboxRepository.save(outboxRecordFor(assignment.id, event))
        event
    }

    /**
     * Confirms the assignment referenced by [command] by first restoring
     * it through [assignmentRepository]. Throws
     * [AssignmentNotFoundException] — an application-layer error, never a
     * persistence or domain one (see that class's own KDoc) — if no
     * Assignment identified by [AcceptAssignmentCommand.assignmentId] has
     * been saved.
     */
    fun acceptAssignment(command: AcceptAssignmentCommand): AssignmentAccepted = transactionRunner.run {
        val assignment = assignmentRepository.findById(command.assignmentId)
            ?: throw AssignmentNotFoundException(command.assignmentId)
        orderGuard.lock(assignment.order)
        acceptAssignment(assignment, command)
    }

    /**
     * Records that the driver has reached the passenger, by first
     * restoring the targeted Assignment through [assignmentRepository] —
     * purely to confirm it exists and to seed [tripFor]'s own self-heal
     * path, since [Trip] (not [Assignment]) now validates and persists
     * this transition (see this class's own "Ride-progress convergence"
     * KDoc). Throws [AssignmentNotFoundException] if none is saved under
     * [ArriveAssignmentCommand.assignmentId]. Mirrors [acceptAssignment]'s
     * own self-fetching shape exactly; no separate
     * caller-supplies-the-instance overload exists for this or the two
     * transitions below, since — unlike acceptance, which
     * [ProposalAssignmentOrchestrationService] calls from inside its own
     * shared transaction — nothing in this sprint's scope needs one.
     *
     * Returns [AssignmentArrived] (not [TripArrived]) — the pre-existing
     * compatibility event type, so every current caller and test
     * (`event.orderId`/`event.driverId`) keeps compiling and passing
     * unmodified; the new [TripArrived] is still published to the outbox
     * (dual-publish), just not returned from this method.
     */
    fun arriveAssignment(command: ArriveAssignmentCommand): AssignmentArrived = transactionRunner.run {
        val assignment = assignmentRepository.findById(command.assignmentId)
            ?: throw AssignmentNotFoundException(command.assignmentId)
        orderGuard.lock(assignment.order)
        val trip = tripFor(assignment)
        val tripEvent = trip.arrive()
        tripRepository.save(trip)
        outboxRepository.save(outboxRecordFor(assignment.id, tripEvent))
        val compatEvent = AssignmentArrived(orderId = tripEvent.orderId, driverId = tripEvent.driverId, occurredAt = tripEvent.occurredAt)
        outboxRepository.save(outboxRecordFor(assignment.id, compatEvent))
        compatEvent
    }

    /** Records that the ride itself has begun. See [arriveAssignment]'s own KDoc for shape and rationale. */
    fun startAssignment(command: StartAssignmentCommand): AssignmentStarted = transactionRunner.run {
        val assignment = assignmentRepository.findById(command.assignmentId)
            ?: throw AssignmentNotFoundException(command.assignmentId)
        orderGuard.lock(assignment.order)
        val trip = tripFor(assignment)
        val tripEvent = trip.start()
        tripRepository.save(trip)
        outboxRepository.save(outboxRecordFor(assignment.id, tripEvent))
        val compatEvent = AssignmentStarted(orderId = tripEvent.orderId, driverId = tripEvent.driverId, occurredAt = tripEvent.occurredAt)
        outboxRepository.save(outboxRecordFor(assignment.id, compatEvent))
        compatEvent
    }

    /**
     * Records that the ride has finished. See [arriveAssignment]'s own KDoc
     * for shape and rationale. The completing [Trip]'s own
     * [com.pios.dispatch.domain.Trip.agreedAmount] — captured once, at
     * Trip creation, never re-derived here — is what the
     * `AssignmentCompleted` outbox record below forwards; see this
     * class's own "Agreed amount capture and propagation" KDoc. This
     * method performs no Proposal lookup of any kind.
     */
    fun completeAssignment(command: CompleteAssignmentCommand): AssignmentCompleted = transactionRunner.run {
        val assignment = assignmentRepository.findById(command.assignmentId)
            ?: throw AssignmentNotFoundException(command.assignmentId)
        orderGuard.lock(assignment.order)
        val trip = tripFor(assignment)
        val tripEvent = trip.complete()
        tripRepository.save(trip)
        outboxRepository.save(outboxRecordFor(assignment.id, tripEvent))
        val compatEvent = AssignmentCompleted(orderId = tripEvent.orderId, driverId = tripEvent.driverId, occurredAt = tripEvent.occurredAt)
        outboxRepository.save(outboxRecordFor(assignment.id, compatEvent, trip.agreedAmount))
        compatEvent
    }

    /**
     * Returns the [Trip] connected to [assignment], self-healing by
     * creating one on the spot if none exists yet — see this class's own
     * "Ride-progress convergence" KDoc for why a pre-existing Assignment
     * might reach a ride-progress transition with no Trip already
     * connected. Idempotent the same way [createTripFor] is: looks up
     * before creating, never produces a second Trip for an Assignment that
     * already has one.
     */
    private fun tripFor(assignment: Assignment): Trip =
        tripRepository.findByAssignmentId(assignment.id)
            ?: Trip.create(assignment).trip.also { tripRepository.save(it) }

    /**
     * The *new* half of the dual-publish migration window (ADR-063
     * Consequences; Task 12). `eventType`/routing key are `Trip*`, not
     * `Assignment*` — a genuinely new contract, additive alongside the
     * unchanged legacy one below, not a replacement for it. No consumer
     * binds to `trip.arrived`/`trip.started`/`trip.completed` yet; adding
     * one (in Order Management or elsewhere) is explicitly out of this
     * task's own scope.
     */
    private fun outboxRecordFor(assignmentId: AssignmentId, event: TripArrived): OutboxRecord = OutboxRecord(
        aggregateId = assignmentId.value,
        eventType = "TripArrived",
        routingKey = "trip.arrived",
        payload = envelopeFor(
            eventType = "TripArrived",
            occurredAt = event.occurredAt.toString(),
            payload = mapOf("orderId" to event.orderId.orderId, "driverId" to event.driverId.driverId)
        )
    )

    private fun outboxRecordFor(assignmentId: AssignmentId, event: TripStarted): OutboxRecord = OutboxRecord(
        aggregateId = assignmentId.value,
        eventType = "TripStarted",
        routingKey = "trip.started",
        payload = envelopeFor(
            eventType = "TripStarted",
            occurredAt = event.occurredAt.toString(),
            payload = mapOf("orderId" to event.orderId.orderId, "driverId" to event.driverId.driverId)
        )
    )

    private fun outboxRecordFor(assignmentId: AssignmentId, event: TripCompleted): OutboxRecord = OutboxRecord(
        aggregateId = assignmentId.value,
        eventType = "TripCompleted",
        routingKey = "trip.completed",
        payload = envelopeFor(
            eventType = "TripCompleted",
            occurredAt = event.occurredAt.toString(),
            payload = mapOf("orderId" to event.orderId.orderId, "driverId" to event.driverId.driverId)
        )
    )

    /**
     * The *legacy* half of the dual-publish window: identical shape to
     * what this service has always published for these three transitions
     * (`eventType`/routing key unchanged) — kept so Order Management's
     * real `AssignmentCompletedListener` (routing key
     * `assignment.completed`) keeps working unmodified. See this class's
     * own "Ride-progress convergence" KDoc.
     */
    private fun outboxRecordFor(assignmentId: AssignmentId, event: AssignmentArrived): OutboxRecord = OutboxRecord(
        aggregateId = assignmentId.value,
        eventType = "AssignmentArrived",
        routingKey = "assignment.arrived",
        payload = envelopeFor(
            eventType = "AssignmentArrived",
            occurredAt = event.occurredAt.toString(),
            payload = mapOf("orderId" to event.orderId.orderId, "driverId" to event.driverId.driverId)
        )
    )

    private fun outboxRecordFor(assignmentId: AssignmentId, event: AssignmentStarted): OutboxRecord = OutboxRecord(
        aggregateId = assignmentId.value,
        eventType = "AssignmentStarted",
        routingKey = "assignment.started",
        payload = envelopeFor(
            eventType = "AssignmentStarted",
            occurredAt = event.occurredAt.toString(),
            payload = mapOf("orderId" to event.orderId.orderId, "driverId" to event.driverId.driverId)
        )
    )

    /**
     * [statedPrice] (D-06; field name unchanged for backward compatibility
     * — see this class's own "Agreed amount capture and propagation"
     * KDoc) is the completing Trip's own `agreedAmount`, forwarded
     * verbatim and nullable — `null` when the Trip has none. Not
     * published on any other event ([TripCompleted] included).
     */
    private fun outboxRecordFor(assignmentId: AssignmentId, event: AssignmentCompleted, statedPrice: String?): OutboxRecord = OutboxRecord(
        aggregateId = assignmentId.value,
        eventType = "AssignmentCompleted",
        routingKey = "assignment.completed",
        payload = envelopeFor(
            eventType = "AssignmentCompleted",
            occurredAt = event.occurredAt.toString(),
            payload = mapOf("orderId" to event.orderId.orderId, "driverId" to event.driverId.driverId, "statedPrice" to statedPrice)
        )
    )

    private fun outboxRecordFor(assignmentId: AssignmentId, event: OrderAssigned): OutboxRecord = OutboxRecord(
        aggregateId = assignmentId.value,
        eventType = "OrderAssigned",
        routingKey = "order.assigned",
        payload = envelopeFor(
            eventType = "OrderAssigned",
            occurredAt = event.occurredAt.toString(),
            payload = mapOf("orderId" to event.orderId.orderId, "driverId" to event.driverId.driverId)
        )
    )

    private fun outboxRecordFor(assignmentId: AssignmentId, event: AssignmentAccepted): OutboxRecord = OutboxRecord(
        aggregateId = assignmentId.value,
        eventType = "AssignmentAccepted",
        routingKey = "assignment.accepted",
        payload = envelopeFor(
            eventType = "AssignmentAccepted",
            occurredAt = event.occurredAt.toString(),
            payload = mapOf("orderId" to event.orderId.orderId, "driverId" to event.driverId.driverId)
        )
    )

    /**
     * Builds the same minimum transport envelope shape RabbitMQ Event
     * Publishing Foundation v1.0 already established for Order Management
     * and Driver Management: a stable [eventId] identifying this one
     * occurrence of the event (generated exactly once, here, at event
     * creation — never regenerated by the relay or publisher, so a
     * retried publish of the same persisted outbox record always carries
     * the same eventId); an explicit `eventVersion` (ADR-030: version is
     * a property of the event itself); the domain's own business
     * [occurredAt]; and only the event-specific data the ratified
     * Dispatch -> Order Management contract (INTERFACE_CONTRACTS.md
     * Section 5) already justifies, nested under `payload`. [payload]'s
     * value type is nullable (rather than `String`) purely so
     * `AssignmentCompleted`'s own `statedPrice` field (ADR-065) can be
     * `null` — every other caller still passes only non-null values.
     */
    private fun envelopeFor(eventType: String, occurredAt: String, payload: Map<String, String?>): String =
        objectMapper.writeValueAsString(
            mapOf(
                "eventId" to UUID.randomUUID().toString(),
                "eventType" to eventType,
                "eventVersion" to 1,
                "occurredAt" to occurredAt,
                "payload" to payload
            )
        )
}

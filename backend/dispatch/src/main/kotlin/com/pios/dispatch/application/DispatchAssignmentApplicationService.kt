package com.pios.dispatch.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.dispatch.domain.Assignment
import com.pios.dispatch.domain.AssignmentAccepted
import com.pios.dispatch.domain.AssignmentCreated
import com.pios.dispatch.domain.AssignmentId
import com.pios.dispatch.domain.OrderAssigned
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
 */
@Service
class DispatchAssignmentApplicationService(
    private val assignmentRepository: AssignmentRepository,
    private val outboxRepository: OutboxRepository = NoOpOutboxRepository,
    private val transactionRunner: TransactionRunner = NoOpTransactionRunner,
    private val objectMapper: ObjectMapper = ObjectMapper()
) {

    fun handle(
        command: AssignOrderCommand,
        existingAssignments: Collection<Assignment> = emptyList()
    ): AssignmentCreated = transactionRunner.run {
        val created = Assignment.create(
            order = command.order,
            driver = command.driver,
            existingAssignments = existingAssignments
        )
        assignmentRepository.save(created.assignment)
        outboxRepository.save(outboxRecordFor(created.assignment.id, created.event))
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
        val existingAssignments = assignmentRepository.findByOrder(command.order)
        val created = Assignment.create(
            order = command.order,
            driver = command.driver,
            existingAssignments = existingAssignments
        )
        assignmentRepository.save(created.assignment)
        outboxRepository.save(outboxRecordFor(created.assignment.id, created.event))
        return created
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
        acceptAssignment(assignment, command)
    }

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
     * Section 5) already justifies, nested under `payload`.
     */
    private fun envelopeFor(eventType: String, occurredAt: String, payload: Map<String, String>): String =
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

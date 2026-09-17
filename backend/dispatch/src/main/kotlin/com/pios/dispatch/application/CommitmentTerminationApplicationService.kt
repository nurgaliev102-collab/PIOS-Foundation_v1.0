package com.pios.dispatch.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.dispatch.domain.Assignment
import com.pios.dispatch.domain.AssignmentId
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.domain.ProposalStatus
import com.pios.dispatch.domain.Termination
import com.pios.dispatch.domain.TerminationInitiator
import com.pios.dispatch.domain.TerminationReasonCode
import com.pios.dispatch.domain.Trip
import com.pios.dispatch.domain.TripStatus
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.UUID

data class TerminateCommitmentCommand(
    val requestId: String,
    val orderId: String,
    val initiator: TerminationInitiator,
    val reasonCode: TerminationReasonCode?,
    val note: String? = null,
    val assignmentId: String? = null
)

@Service
class CommitmentTerminationApplicationService(
    private val assignments: AssignmentRepository,
    private val trips: TripRepository,
    private val proposals: ProposalRepository,
    private val proposalService: ProposalApplicationService,
    private val dispatchRequests: DispatchRequestRepository,
    private val requests: TerminationRequestRepository,
    private val orderGuard: OrderGuard,
    private val outbox: OutboxRepository,
    private val transactions: TransactionRunner,
    private val mapper: ObjectMapper,
    private val clock: Clock = Clock.systemUTC()
) {
    fun terminate(command: TerminateCommitmentCommand): TerminationRequestRecord = transactions.run {
        require(command.requestId.isNotBlank() && command.orderId.isNotBlank())
        require(command.note == null || command.note.length <= 2000)
        if (command.reasonCode != null) {
            Termination(command.requestId, command.initiator, command.reasonCode, clock.instant(), command.note)
        } else {
            require(command.initiator == TerminationInitiator.PASSENGER && command.note == null) {
                "A termination reason is required"
            }
        }
        val order = OrderReference(command.orderId)
        orderGuard.lock(order)
        val existing = requests.findById(command.requestId)
        if (existing != null) {
            check(existing.orderId == command.orderId && existing.initiator == command.initiator &&
                existing.reasonCode == command.reasonCode && existing.note == command.note &&
                (command.assignmentId == null || existing.assignmentId == command.assignmentId)) {
                "The requestId is already bound to another termination command"
            }
            return@run existing
        }
        val matchingAssignments = assignments.findByOrder(order)
        check(matchingAssignments.size <= 1) { "Multiple assignments for order ${order.orderId} require reconciliation" }
        val assignment = matchingAssignments.firstOrNull()
        if (command.assignmentId != null) {
            check(assignment?.id?.value == command.assignmentId) { "Assignment does not match order" }
        }
        when {
            assignment == null -> resolveWithoutCommitment(command, order)
            else -> resolveWithAssignment(command, assignment)
        }
    }

    private fun resolveWithoutCommitment(command: TerminateCommitmentCommand, order: OrderReference): TerminationRequestRecord {
        check(command.initiator == TerminationInitiator.PASSENGER) { "Only passenger may cancel without an assignment" }
        dispatchRequests.markCancelled(order.orderId)
        proposals.findByOrder(order).firstOrNull {
            it.status == ProposalStatus.OPEN || it.status == ProposalStatus.PRICE_PROPOSED
        }?.let {
            proposalService.withdrawProposal(it, WithdrawProposalCommand(it.id))
        }
        val result = record(command, null, TerminationOutcome.NO_COMMITMENT)
        requests.save(result)
        publishResolution(result)
        return result
    }

    private fun resolveWithAssignment(command: TerminateCommitmentCommand, assignment: Assignment): TerminationRequestRecord {
        val trip = trips.findByAssignmentId(assignment.id) ?: Trip.create(assignment).trip.also(trips::save)
        val outcome = when {
            trip.status == TripStatus.COMPLETED -> TerminationOutcome.ALREADY_COMPLETED
            trip.status == TripStatus.TERMINATED -> TerminationOutcome.ALREADY_TERMINATED
            command.reasonCode == null -> TerminationOutcome.REASON_REQUIRED
            else -> TerminationOutcome.TERMINATED
        }
        val result = record(command, assignment.id.value, outcome)
        if (outcome == TerminationOutcome.TERMINATED) {
            val fact = Termination(
                requestId = command.requestId,
                initiator = command.initiator,
                reasonCode = requireNotNull(command.reasonCode),
                terminatedAt = clock.instant().truncatedTo(ChronoUnit.MICROS),
                note = command.note
            )
            trip.terminate(fact)
            assignment.terminate(fact.terminatedAt)
            trips.save(trip)
            assignments.save(assignment)
            dispatchRequests.markCancelled(assignment.order.orderId)
            requests.save(result)
            publishTermination(assignment, trip, fact)
        } else {
            requests.save(result)
            publishResolution(result)
        }
        return result
    }

    private fun record(
        command: TerminateCommitmentCommand,
        assignmentId: String?,
        outcome: TerminationOutcome
    ): TerminationRequestRecord = TerminationRequestRecord(
        command.requestId,
        command.orderId,
        assignmentId,
        command.initiator,
        command.reasonCode,
        command.note,
        outcome
    )

    private fun publishResolution(result: TerminationRequestRecord) {
        outbox.save(event(
            result.orderId,
            "OrderCancellationResolved",
            "order.cancellation-resolved",
            mapOf(
                "requestId" to result.requestId,
                "orderId" to result.orderId,
                "outcome" to result.outcome.name
            )
        ))
    }

    private fun publishTermination(assignment: Assignment, trip: Trip, fact: Termination) {
        outbox.save(event(
            assignment.order.orderId,
            "CommitmentTerminated",
            "commitment.terminated",
            mapOf(
                "requestId" to fact.requestId,
                "orderId" to assignment.order.orderId,
                "assignmentId" to assignment.id.value,
                "tripId" to trip.id.value,
                "driverId" to assignment.driver.driverId,
                "initiator" to fact.initiator.name,
                "reasonCode" to fact.reasonCode.name,
                "note" to fact.note,
                "terminatedAt" to fact.terminatedAt.toString()
            )
        ))
    }

    private fun event(orderId: String, type: String, key: String, payload: Map<String, String?>): OutboxRecord =
        OutboxRecord(
            aggregateId = orderId,
            eventType = type,
            routingKey = key,
            payload = mapper.writeValueAsString(
                mapOf(
                    "eventId" to UUID.randomUUID().toString(),
                    "eventType" to type,
                    "eventVersion" to 1,
                    "occurredAt" to clock.instant().toString(),
                    "payload" to payload
                )
            )
        )
}

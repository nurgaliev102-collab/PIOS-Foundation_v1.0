package com.pios.dispatch.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.dispatch.application.ArriveAssignmentCommand
import com.pios.dispatch.application.AssignOrderCommand
import com.pios.dispatch.application.CommitmentTerminationApplicationService
import com.pios.dispatch.application.CompleteAssignmentCommand
import com.pios.dispatch.application.ConfirmPriceCommand
import com.pios.dispatch.application.DispatchAssignmentApplicationService
import com.pios.dispatch.application.ProposalApplicationService
import com.pios.dispatch.application.ProposalAssignmentOrchestrationService
import com.pios.dispatch.application.ProposeDriverCommand
import com.pios.dispatch.application.ProposePriceCommand
import com.pios.dispatch.application.TerminateCommitmentCommand
import com.pios.dispatch.application.TerminationOutcome
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.domain.ProposalStatus
import com.pios.dispatch.domain.TerminationInitiator
import com.pios.dispatch.domain.TerminationReasonCode
import com.pios.dispatch.domain.TripStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CommitmentTerminationPostgreSQLTest {
    private val dataSource = PostgreSQLTestDatabase.dataSource
    private val jdbc = JdbcTemplate(dataSource)
    private val transactions = SpringTransactionRunner(TransactionTemplate(DataSourceTransactionManager(dataSource)))
    private val guard = PostgreSQLOrderGuard(jdbc)
    private val assignments = PostgreSQLAssignmentRepository(jdbc)
    private val trips = PostgreSQLTripRepository(jdbc)
    private val proposals = PostgreSQLProposalRepository(jdbc)
    private val dispatchRequests = PostgreSQLDispatchRequestRepository(jdbc)
    private val requests = PostgreSQLTerminationRequestRepository(jdbc)
    private val outbox = PostgreSQLOutboxRepository(jdbc)
    private val mapper = ObjectMapper()
    private val proposalService = ProposalApplicationService(
        proposals, transactions, dispatchRequestRepository = dispatchRequests, orderGuard = guard
    )
    private val assignmentService = DispatchAssignmentApplicationService(
        assignments, outbox, transactions, mapper, trips, proposals, guard, dispatchRequests
    )
    private val orchestration = ProposalAssignmentOrchestrationService(
        proposals, proposalService, assignmentService, transactions, guard
    )
    private val termination = CommitmentTerminationApplicationService(
        assignments, trips, proposals, proposalService, dispatchRequests, requests,
        guard, outbox, transactions, mapper
    )

    private fun order(): OrderReference = OrderReference(UUID.randomUUID().toString())
    private fun driver(): DriverReference = DriverReference("driver-${UUID.randomUUID()}")
    private fun createManual(order: OrderReference = order()): String =
        assignmentService.handle(AssignOrderCommand(order, driver())).assignment.id.value

    private fun passengerCommand(order: OrderReference, reason: TerminationReasonCode? = TerminationReasonCode.PLANS_CHANGED) =
        TerminateCommitmentCommand(UUID.randomUUID().toString(), order.orderId, TerminationInitiator.PASSENGER, reason)

    private fun <A, B> race(first: () -> A, second: () -> B): Pair<Result<A>, Result<B>> {
        val gate = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        return try {
            val a = pool.submit(Callable { gate.await(); runCatching(first) })
            val b = pool.submit(Callable { gate.await(); runCatching(second) })
            gate.countDown()
            a.get(20, TimeUnit.SECONDS) to b.get(20, TimeUnit.SECONDS)
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `concurrent completion and termination have one terminal winner and no false completion event`() {
        val order = order()
        val assignmentId = createManual(order)
        val assignment = assignments.findByOrder(order).single()
        assignmentService.arriveAssignment(ArriveAssignmentCommand(assignment.id))
        assignmentService.startAssignment(com.pios.dispatch.application.StartAssignmentCommand(assignment.id))
        val (completion, cancellation) = race(
            { assignmentService.completeAssignment(CompleteAssignmentCommand(assignment.id)) },
            { termination.terminate(passengerCommand(order)) }
        )
        val trip = assertNotNull(trips.findByAssignmentId(assignment.id))
        val completions = jdbc.queryForObject(
            "SELECT count(*) FROM dispatch_outbox WHERE aggregate_id = ? AND event_type = 'AssignmentCompleted'",
            Long::class.java, assignmentId
        ) ?: 0L
        when (trip.status) {
            TripStatus.COMPLETED -> {
                assertTrue(completion.isSuccess)
                assertEquals(TerminationOutcome.ALREADY_COMPLETED, cancellation.getOrThrow().outcome)
                assertEquals(1L, completions)
            }
            TripStatus.TERMINATED -> {
                assertTrue(completion.isFailure)
                assertEquals(TerminationOutcome.TERMINATED, cancellation.getOrThrow().outcome)
                assertEquals(0L, completions)
            }
            else -> error("Trip did not reach a terminal state: ${trip.status}")
        }
    }

    @Test
    fun `concurrent arrival and termination always leave Trip terminal`() {
        val order = order()
        val assignmentId = createManual(order)
        val assignment = assignments.findByOrder(order).single()
        race(
            { assignmentService.arriveAssignment(ArriveAssignmentCommand(assignment.id)) },
            { termination.terminate(passengerCommand(order)) }
        )
        assertEquals(TripStatus.TERMINATED, trips.findByAssignmentId(assignment.id)?.status)
        assertEquals("TERMINATED", assignments.findById(assignment.id)?.status?.name)
        assertFailsWith<IllegalStateException> {
            assignmentService.completeAssignment(CompleteAssignmentCommand(assignment.id))
        }
        assertTrue(assignmentId.isNotBlank())
    }

    @Test
    fun `concurrent start and termination -- termination always wins eventually, and a terminated Trip can never complete`() {
        // Unlike complete-vs-terminate (Decision test above), start's own
        // target state (IN_PROGRESS) does not block termination -- Trip.terminate()
        // accepts CREATED/ARRIVED/IN_PROGRESS alike. So termination can
        // never lose this race: if start wins the lock first, it moves
        // ARRIVED -> IN_PROGRESS and terminate then still succeeds from
        // there (IN_PROGRESS -> TERMINATED); if terminate wins first,
        // start's own ARRIVED-only guard fails. Either ordering is a
        // legitimate, correct outcome -- this test deliberately does not
        // assert which one "won" the lock, only the two invariants that
        // must hold regardless: the Trip always converges on TERMINATED,
        // and a terminated Trip can never subsequently be completed.
        val order = order()
        val assignmentId = createManual(order)
        val assignment = assignments.findByOrder(order).single()
        assignmentService.arriveAssignment(ArriveAssignmentCommand(assignment.id))
        race(
            { assignmentService.startAssignment(com.pios.dispatch.application.StartAssignmentCommand(assignment.id)) },
            { termination.terminate(passengerCommand(order)) }
        )
        assertEquals(TripStatus.TERMINATED, trips.findByAssignmentId(assignment.id)?.status)
        assertEquals("TERMINATED", assignments.findById(assignment.id)?.status?.name)
        assertFailsWith<IllegalStateException> {
            assignmentService.completeAssignment(CompleteAssignmentCommand(assignment.id))
        }
        assertTrue(assignmentId.isNotBlank())
    }

    @Test
    fun `two concurrent termination requests from different initiators have exactly one winner and exactly one CommitmentTerminated event`() {
        val order = order()
        createManual(order)
        val assignment = assignments.findByOrder(order).single()
        val passengerRequest = TerminateCommitmentCommand(
            UUID.randomUUID().toString(), order.orderId, TerminationInitiator.PASSENGER,
            TerminationReasonCode.PLANS_CHANGED
        )
        val driverRequest = TerminateCommitmentCommand(
            UUID.randomUUID().toString(), order.orderId, TerminationInitiator.DRIVER,
            TerminationReasonCode.CANNOT_FULFILL, assignmentId = assignment.id.value
        )
        val (fromPassenger, fromDriver) = race(
            { termination.terminate(passengerRequest) },
            { termination.terminate(driverRequest) }
        )
        val outcomes = listOf(fromPassenger.getOrThrow().outcome, fromDriver.getOrThrow().outcome)
        assertEquals(1, outcomes.count { it == TerminationOutcome.TERMINATED }, "exactly one request must actually terminate the commitment")
        assertEquals(1, outcomes.count { it == TerminationOutcome.ALREADY_TERMINATED }, "the loser must see ALREADY_TERMINATED, not a second TERMINATED")
        assertEquals(TripStatus.TERMINATED, trips.findByAssignmentId(assignment.id)?.status)
        val terminatedEvents = jdbc.queryForObject(
            "SELECT count(*) FROM dispatch_outbox WHERE aggregate_id = ? AND event_type = 'CommitmentTerminated'",
            Long::class.java, order.orderId
        )
        assertEquals(1L, terminatedEvents, "only the winning request may publish CommitmentTerminated")
        // Both request rows persist independently -- each initiator's own
        // request is itself idempotent and auditable, even though only one
        // of them actually changed Trip/Assignment state.
        assertNotNull(requests.findById(passengerRequest.requestId))
        assertNotNull(requests.findById(driverRequest.requestId))
    }

    @Test
    fun `accept versus cancel is serialized and accepted proposal is historical`() {
        val order = order()
        val proposal = proposalService.handle(ProposeDriverCommand(order, driver())).proposal
        proposalService.proposePrice(ProposePriceCommand(proposal.id, "300"))
        val (acceptance, cancellation) = race(
            { orchestration.confirmPrice(ConfirmPriceCommand(proposal.id)) },
            { termination.terminate(passengerCommand(order)) }
        )
        val result = cancellation.getOrThrow()
        val currentProposal = assertNotNull(proposals.findById(proposal.id))
        if (acceptance.isSuccess) {
            assertEquals(TerminationOutcome.TERMINATED, result.outcome)
            assertEquals(ProposalStatus.ACCEPTED, currentProposal.status)
            assertEquals(TripStatus.TERMINATED, trips.findByAssignmentId(assignments.findByOrder(order).single().id)?.status)
        } else {
            assertEquals(TerminationOutcome.NO_COMMITMENT, result.outcome)
            assertEquals(ProposalStatus.WITHDRAWN, currentProposal.status)
            assertTrue(assignments.findByOrder(order).isEmpty())
        }
    }

    @Test
    fun `manual assignment can terminate without claiming driver consent and duplicate request is stable`() {
        val order = order()
        createManual(order)
        val assignment = assignments.findByOrder(order).single()
        assertEquals("CREATED", assignment.status.name)
        val command = TerminateCommitmentCommand(
            UUID.randomUUID().toString(), order.orderId, TerminationInitiator.DRIVER,
            TerminationReasonCode.CANNOT_FULFILL, assignmentId = assignment.id.value
        )
        val first = termination.terminate(command)
        val replayed = CommitmentTerminationApplicationService(
            assignments, trips, proposals, proposalService, dispatchRequests, requests,
            guard, outbox, transactions, mapper
        ).terminate(command)
        assertEquals(first, replayed)
        assertEquals(TripStatus.TERMINATED, trips.findByAssignmentId(assignment.id)?.status)
        assertFailsWith<IllegalStateException> {
            termination.terminate(command.copy(reasonCode = TerminationReasonCode.OTHER))
        }
        val eventCount = jdbc.queryForObject(
            "SELECT count(*) FROM dispatch_outbox WHERE aggregate_id = ? AND event_type = 'CommitmentTerminated'",
            Long::class.java, order.orderId
        )
        assertEquals(1L, eventCount)
    }
}

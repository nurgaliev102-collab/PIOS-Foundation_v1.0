package com.pios.dispatch.persistence

import com.pios.dispatch.application.AssignOrderCommand
import com.pios.dispatch.application.DispatchAssignmentApplicationService
import com.pios.dispatch.domain.AssignmentId
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.domain.Trip
import com.pios.dispatch.domain.TripStatus
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * Proves Task 11's own core Trip-creation guarantees against a real
 * PostgreSQL database (the isolated `pios_dispatch_test` database,
 * `docs/TEST_DATABASE_ISOLATION.md`) — mirroring
 * [AssignmentOutboxTransactionTest]'s own proof style exactly, extended
 * to cover Trip's own idempotency requirement: exactly one Trip per
 * Assignment, surviving process restart, never solved merely with an
 * in-memory flag.
 */
class TripCreationTransactionTest {

    private val dataSource = PostgreSQLTestDatabase.dataSource
    private val assignmentRepository = PostgreSQLAssignmentRepository(JdbcTemplate(dataSource))
    private val outboxRepository = PostgreSQLOutboxRepository(JdbcTemplate(dataSource))
    private val tripRepository = PostgreSQLTripRepository(JdbcTemplate(dataSource))
    private val transactionRunner = SpringTransactionRunner(TransactionTemplate(DataSourceTransactionManager(dataSource)))
    private val service = DispatchAssignmentApplicationService(
        assignmentRepository = assignmentRepository,
        outboxRepository = outboxRepository,
        transactionRunner = transactionRunner,
        tripRepository = tripRepository
    )

    @Test
    fun `creating an assignment persists the assignment, its outbox record, and its trip together, in one transaction`() {
        val order = OrderReference("trip-tx-order-${UUID.randomUUID()}")
        val driver = DriverReference("trip-tx-driver-1")

        val created = service.handle(AssignOrderCommand(order, driver))

        assertNotNull(assignmentRepository.findById(created.assignment.id))
        assertEquals(1, outboxRepository.findUnpublished().count { it.aggregateId == created.assignment.id.value && it.eventType == "OrderAssigned" })
        val trip = tripRepository.findByAssignmentId(created.assignment.id)
        assertNotNull(trip)
        assertEquals(TripStatus.CREATED, trip.status)
    }

    @Test
    fun `a trip created through the shared transaction survives a fresh read through a brand new repository instance -- proving real persistence, not an in-memory artifact`() {
        val order = OrderReference("trip-tx-order-${UUID.randomUUID()}")
        val driver = DriverReference("trip-tx-driver-2")
        val created = service.handle(AssignOrderCommand(order, driver))

        // A brand new PostgreSQLTripRepository instance, sharing nothing in
        // process memory with the one `service` used above -- the same proof
        // shape this codebase already uses elsewhere to show persistence
        // survives beyond any one object's own lifetime, standing in for
        // "survives process restart" within a single test process.
        val freshRepository = PostgreSQLTripRepository(JdbcTemplate(dataSource))

        val reloaded = freshRepository.findByAssignmentId(created.assignment.id)
        assertNotNull(reloaded)
        assertEquals(created.assignment.id, reloaded.assignmentId)
    }

    @Test
    fun `the database itself refuses a second trip for the same assignment -- the persistent half of the idempotency guarantee`() {
        val order = OrderReference("trip-tx-order-${UUID.randomUUID()}")
        val driver = DriverReference("trip-tx-driver-3")
        val created = service.handle(AssignOrderCommand(order, driver))

        // Bypasses the application-level existingTrip check entirely (Trip.kt's
        // own in-memory half of this guarantee) to prove the second,
        // independent, persistent half: trips.assignment_id's own UNIQUE
        // constraint (V12 migration) rejects a second row for this assignment
        // even if application logic somehow tried to insert one directly.
        val duplicateTrip = Trip.create(created.assignment).trip

        assertFailsWith<DataIntegrityViolationException> {
            tripRepository.save(duplicateTrip)
        }
        // Still exactly one row for this assignment.
        val remaining = JdbcTemplate(dataSource).queryForObject(
            "SELECT COUNT(*) FROM trips WHERE assignment_id = ?",
            Int::class.java,
            created.assignment.id.value
        )
        assertEquals(1, remaining)
    }

    @Test
    fun `if the outbox save fails, the assignment's trip is rolled back too -- Trip creation shares Assignment's own atomicity`() {
        val order = OrderReference("trip-tx-order-${UUID.randomUUID()}")
        val driver = DriverReference("trip-tx-driver-4")

        val failingOutboxRepository = object : com.pios.dispatch.application.OutboxRepository {
            override fun save(record: com.pios.dispatch.application.OutboxRecord): com.pios.dispatch.application.OutboxRecord =
                throw RuntimeException("simulated outbox failure")
            override fun findUnpublished(): List<com.pios.dispatch.application.OutboxRecord> = emptyList()
            override fun markPublished(id: Long) = Unit
            override fun countUnpublished(): com.pios.dispatch.application.OutboxBacklog =
                com.pios.dispatch.application.OutboxBacklog(pending = 0, oldestPendingCreatedAt = null)
        }
        val failingService = DispatchAssignmentApplicationService(
            assignmentRepository = assignmentRepository,
            outboxRepository = failingOutboxRepository,
            transactionRunner = transactionRunner,
            tripRepository = tripRepository
        )

        assertFailsWith<RuntimeException> {
            failingService.handle(AssignOrderCommand(order, driver))
        }

        // The Assignment itself is never findable afterward either -- the
        // whole transaction (Assignment + outbox + Trip) rolled back
        // together, so there is no orphaned Trip for a never-really-created
        // Assignment.
        assertEquals(emptyList(), assignmentRepository.findByOrder(order))
        val orphanedTrips = JdbcTemplate(dataSource).queryForObject(
            "SELECT COUNT(*) FROM trips WHERE order_reference = ?",
            Int::class.java,
            order.orderId
        )
        assertEquals(0, orphanedTrips)
    }

    @Test
    fun `accepting a proposal end to end -- the real production path -- also creates exactly one trip`() {
        val proposalRepository = PostgreSQLProposalRepository(JdbcTemplate(dataSource))
        val proposalApplicationService = com.pios.dispatch.application.ProposalApplicationService(proposalRepository)
        val orchestrationService = com.pios.dispatch.application.ProposalAssignmentOrchestrationService(
            proposalRepository,
            proposalApplicationService,
            service,
            transactionRunner
        )
        val order = OrderReference("trip-tx-order-${UUID.randomUUID()}")
        val driver = DriverReference("trip-tx-driver-5")
        val proposal = proposalApplicationService.handle(com.pios.dispatch.application.ProposeDriverCommand(order, driver)).proposal

        val outcome = orchestrationService.acceptProposal(com.pios.dispatch.application.AcceptProposalCommand(proposal.id))

        val trip = tripRepository.findByAssignmentId(outcome.assignmentCreated.assignment.id)
        assertNotNull(trip)
        assertEquals(order, trip.order)
        assertEquals(driver, trip.driver)
        assertEquals(TripStatus.CREATED, trip.status)
    }
}

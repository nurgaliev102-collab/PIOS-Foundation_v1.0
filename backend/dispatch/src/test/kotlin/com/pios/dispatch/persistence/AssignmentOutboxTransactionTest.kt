package com.pios.dispatch.persistence

import com.pios.dispatch.application.AcceptAssignmentCommand
import com.pios.dispatch.application.AssignOrderCommand
import com.pios.dispatch.application.DispatchAssignmentApplicationService
import com.pios.dispatch.application.OutboxRecord
import com.pios.dispatch.application.OutboxRepository
import com.pios.dispatch.domain.AssignmentStatus
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Proves the core guarantee Tranche 1: Dispatch Event Publishing
 * Completion exists to establish for Dispatch's own events (ADR-032): an
 * Assignment's own state change and its corresponding outbox record are
 * saved in one atomic PostgreSQL transaction, against a real database —
 * mirroring Order Management's own equivalent proof exactly.
 */
class AssignmentOutboxTransactionTest {

    private val dataSource = PostgreSQLTestDatabase.dataSource
    private val assignmentRepository = PostgreSQLAssignmentRepository(JdbcTemplate(dataSource))
    private val outboxRepository = PostgreSQLOutboxRepository(JdbcTemplate(dataSource))
    private val transactionRunner = SpringTransactionRunner(TransactionTemplate(DataSourceTransactionManager(dataSource)))
    private val service = DispatchAssignmentApplicationService(assignmentRepository, outboxRepository, transactionRunner)

    @Test
    fun `creating an assignment persists both the assignment and a matching outbox record together`() {
        val order = OrderReference("outbox-tx-order-${UUID.randomUUID()}")
        val driver = DriverReference("outbox-tx-driver-1")

        val created = service.handle(AssignOrderCommand(order, driver))

        assertEquals(AssignmentStatus.CREATED, assignmentRepository.findById(created.assignment.id)?.status)
        val records = outboxRepository.findUnpublished().filter { it.aggregateId == created.assignment.id.value }
        assertEquals(1, records.size)
        assertEquals("OrderAssigned", records.single().eventType)
        assertEquals("order.assigned", records.single().routingKey)
    }

    @Test
    fun `accepting an assignment persists both the updated assignment and a matching outbox record together`() {
        val order = OrderReference("outbox-tx-order-${UUID.randomUUID()}")
        val driver = DriverReference("outbox-tx-driver-2")
        val created = service.handle(AssignOrderCommand(order, driver))

        service.acceptAssignment(AcceptAssignmentCommand(created.assignment.id))

        assertEquals(AssignmentStatus.ACCEPTED, assignmentRepository.findById(created.assignment.id)?.status)
        val records = outboxRepository.findUnpublished()
            .filter { it.aggregateId == created.assignment.id.value && it.eventType == "AssignmentAccepted" }
        assertEquals(1, records.size)
        assertEquals("assignment.accepted", records.single().routingKey)
    }

    @Test
    fun `if the outbox save fails, the assignment's own state change is rolled back too`() {
        val order = OrderReference("outbox-tx-order-${UUID.randomUUID()}")
        val driver = DriverReference("outbox-tx-driver-3")
        val created = service.handle(AssignOrderCommand(order, driver))

        val failingOutboxRepository = object : OutboxRepository {
            override fun save(record: OutboxRecord): OutboxRecord = throw RuntimeException("simulated outbox failure")
            override fun findUnpublished(): List<OutboxRecord> = emptyList()
            override fun markPublished(id: Long) = Unit
            override fun countUnpublished(): com.pios.dispatch.application.OutboxBacklog =
                com.pios.dispatch.application.OutboxBacklog(pending = 0, oldestPendingCreatedAt = null)
        }
        val failingService = DispatchAssignmentApplicationService(assignmentRepository, failingOutboxRepository, transactionRunner)

        assertFailsWith<RuntimeException> {
            failingService.acceptAssignment(AcceptAssignmentCommand(created.assignment.id))
        }

        // The assignment's status must still be CREATED in the database --
        // the failed outbox save must have rolled back the assignment's
        // own status update within the same transaction, not just its own.
        assertEquals(AssignmentStatus.CREATED, assignmentRepository.findById(created.assignment.id)?.status)
        assertTrue(
            outboxRepository.findUnpublished()
                .none { it.aggregateId == created.assignment.id.value && it.eventType == "AssignmentAccepted" }
        )
    }
}

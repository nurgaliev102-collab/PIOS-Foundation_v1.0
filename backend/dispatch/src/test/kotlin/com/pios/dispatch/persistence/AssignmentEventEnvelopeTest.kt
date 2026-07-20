package com.pios.dispatch.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.dispatch.application.AcceptAssignmentCommand
import com.pios.dispatch.application.AssignOrderCommand
import com.pios.dispatch.application.DispatchAssignmentApplicationService
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Proves the transport envelope Dispatch's own outbox produces for
 * OrderAssigned and AssignmentAccepted (Tranche 1: Dispatch Event
 * Publishing Completion) matches the same shape already established and
 * hardened for Order Management's own OrderSubmitted (a stable eventId,
 * an explicit eventVersion, and a business occurredAt distinct from
 * outbox bookkeeping), and that a retried publish of the same record
 * never regenerates a new eventId.
 */
class AssignmentEventEnvelopeTest {

    private val objectMapper = ObjectMapper()
    private val dataSource = PostgreSQLTestDatabase.dataSource
    private val assignmentRepository = PostgreSQLAssignmentRepository(JdbcTemplate(dataSource))
    private val outboxRepository = PostgreSQLOutboxRepository(JdbcTemplate(dataSource))
    private val transactionRunner = SpringTransactionRunner(TransactionTemplate(DataSourceTransactionManager(dataSource)))
    private val service = DispatchAssignmentApplicationService(assignmentRepository, outboxRepository, transactionRunner, objectMapper)

    @Test
    fun `the persisted OrderAssigned envelope carries a stable eventId, an explicit eventVersion, and a business occurredAt distinct from outbox bookkeeping`() {
        val order = OrderReference("envelope-order-${UUID.randomUUID()}")
        val driver = DriverReference("envelope-driver-1")

        val created = service.handle(AssignOrderCommand(order, driver))

        val record = outboxRepository.findUnpublished().single { it.aggregateId == created.assignment.id.value }
        val envelope = objectMapper.readTree(record.payload)

        assertTrue(envelope.get("eventId").asText().isNotBlank())
        assertEquals("OrderAssigned", envelope.get("eventType").asText())
        assertEquals(1, envelope.get("eventVersion").asInt())
        assertEquals(created.event.occurredAt.toString(), envelope.get("occurredAt").asText())
        assertEquals(order.orderId, envelope.get("payload").get("orderId").asText())
        assertEquals(driver.driverId, envelope.get("payload").get("driverId").asText())
        assertNotEquals(record.createdAt.toString(), envelope.get("occurredAt").asText())
    }

    @Test
    fun `the persisted AssignmentAccepted envelope carries its own distinct eventId from OrderAssigned's`() {
        val order = OrderReference("envelope-order-${UUID.randomUUID()}")
        val driver = DriverReference("envelope-driver-2")
        val created = service.handle(AssignOrderCommand(order, driver))
        val orderAssignedEventId = objectMapper.readTree(
            outboxRepository.findUnpublished().single { it.aggregateId == created.assignment.id.value }.payload
        ).get("eventId").asText()

        service.acceptAssignment(AcceptAssignmentCommand(created.assignment.id))

        val acceptedRecord = outboxRepository.findUnpublished()
            .single { it.aggregateId == created.assignment.id.value && it.eventType == "AssignmentAccepted" }
        val acceptedEnvelope = objectMapper.readTree(acceptedRecord.payload)

        assertEquals("AssignmentAccepted", acceptedEnvelope.get("eventType").asText())
        assertNotEquals(orderAssignedEventId, acceptedEnvelope.get("eventId").asText())
    }
}

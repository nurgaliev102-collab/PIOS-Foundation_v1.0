package com.pios.ordermanagement.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.ordermanagement.application.AssignmentAcceptedProjectionApplicationService
import com.pios.ordermanagement.application.OrderAssignmentRecognitionHandler
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Proves Tranche 1: Dispatch Event Publishing Completion's end-to-end
 * goal for Order Management's own consumer: a real AssignmentAccepted-
 * shaped message, published to the real `dispatch.events` exchange
 * exactly as Dispatch's own publisher would, is consumed by Order
 * Management's real listener and results in the event's eventId being
 * recorded as processed. Mirrors Dispatch's own already-proven
 * `DriverAvailabilityConsumerIntegrationTest` exactly, adapted to this
 * flow's own observable effect (the idempotency ledger, since
 * [OrderAssignmentRecognitionHandler] itself has no local state to
 * transition, by its own design).
 */
class AssignmentAcceptedConsumerIntegrationTest {

    private val dataSource = PostgreSQLTestDatabase.dataSource
    private val repository = PostgreSQLAssignmentAcceptedRepository(JdbcTemplate(dataSource))
    private val transactionRunner = SpringTransactionRunner(TransactionTemplate(DataSourceTransactionManager(dataSource)))
    private val applicationService = AssignmentAcceptedProjectionApplicationService(
        repository,
        OrderAssignmentRecognitionHandler(),
        transactionRunner
    )
    private val listener = AssignmentAcceptedListener(applicationService, ObjectMapper())
    private val publisher = AssignmentAcceptedMessagePublisher(RabbitMQTestConnection.connectionFactory)
    private val harness = OrderManagementTestListenerHarness(RabbitMQTestConnection.connectionFactory, listener)

    @AfterTest
    fun stopHarness() {
        harness.stop()
    }

    @Test
    fun `a valid AssignmentAccepted message is consumed and its eventId recorded as processed`() {
        val eventId = UUID.randomUUID().toString()

        publisher.publishAssignmentAccepted(
            orderReference = "consumer-order-${UUID.randomUUID()}",
            driverReference = "consumer-driver-${UUID.randomUUID()}",
            eventId = eventId
        )

        val processed = awaitUntilNotNull { repository.isProcessed(eventId).takeIf { it } }

        assertEquals(true, processed)
    }

    @Test
    fun `two distinct AssignmentAccepted messages are both recorded as processed`() {
        val firstEventId = UUID.randomUUID().toString()
        val secondEventId = UUID.randomUUID().toString()

        publisher.publishAssignmentAccepted(
            orderReference = "consumer-order-${UUID.randomUUID()}",
            driverReference = "consumer-driver-${UUID.randomUUID()}",
            eventId = firstEventId
        )
        publisher.publishAssignmentAccepted(
            orderReference = "consumer-order-${UUID.randomUUID()}",
            driverReference = "consumer-driver-${UUID.randomUUID()}",
            eventId = secondEventId
        )

        assertTrue(awaitUntilNotNull { repository.isProcessed(firstEventId).takeIf { it } } == true)
        assertTrue(awaitUntilNotNull { repository.isProcessed(secondEventId).takeIf { it } } == true)
    }
}

package com.pios.ordermanagement.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.ordermanagement.application.AssignmentAcceptedProjectionApplicationService
import com.pios.ordermanagement.application.OrderAssignmentRecognitionHandler
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Proves a deterministically failing or unsupported message is retried a
 * bounded number of times
 * ([RabbitMQListenerContainerConfiguration.MAX_ATTEMPTS]) and, once
 * exhausted, reaches Order Management's own dead-letter queue -- never
 * silently discarded, and never applied as if it were the supported
 * version. Mirrors Dispatch's own already-proven
 * `DriverAvailabilityDeadLetterIntegrationTest` exactly.
 */
class AssignmentAcceptedDeadLetterIntegrationTest {

    private val dataSource = PostgreSQLTestDatabase.dataSource
    private val repository = PostgreSQLAssignmentAcceptedRepository(JdbcTemplate(dataSource))
    private val transactionRunner = SpringTransactionRunner(TransactionTemplate(DataSourceTransactionManager(dataSource)))
    private val applicationService = AssignmentAcceptedProjectionApplicationService(
        repository,
        OrderAssignmentRecognitionHandler(),
        transactionRunner
    )
    private val listener = AssignmentAcceptedListener(applicationService, ObjectMapper())
    private val objectMapper = ObjectMapper()
    private val publisher = AssignmentAcceptedMessagePublisher(RabbitMQTestConnection.connectionFactory)
    private val deadLetterObserver = RabbitMQTestDeadLetterObserver(RabbitMQTestConnection.connectionFactory)
    private val harness = OrderManagementTestListenerHarness(RabbitMQTestConnection.connectionFactory, listener)

    @AfterTest
    fun stopHarness() {
        harness.stop()
    }

    @Test
    fun `a message that deterministically fails processing exhausts bounded retry and reaches the dead-letter queue`() {
        val marker = "dlq-marker-${UUID.randomUUID()}"
        // Structurally valid envelope (correct eventType/eventVersion) but
        // missing payload.orderId entirely -- the listener's own
        // validation throws on every single attempt, deterministically,
        // never succeeding no matter how many times it is retried.
        val malformedEnvelope = objectMapper.writeValueAsString(
            mapOf(
                "eventId" to marker,
                "eventType" to "AssignmentAccepted",
                "eventVersion" to 1,
                "occurredAt" to Instant.now().toString(),
                "payload" to mapOf("driverId" to "dlq-driver")
            )
        )

        publisher.publishRaw(malformedEnvelope)

        val deadLettered = deadLetterObserver.receiveMessageContaining(marker)

        assertNotNull(deadLettered, "A deterministically failing message must reach the dead-letter queue after bounded retry is exhausted")
    }

    @Test
    fun `an unsupported eventVersion is not processed as version 1 and reaches the dead-letter queue`() {
        val eventId = "dlq-version-${UUID.randomUUID()}"

        publisher.publishAssignmentAccepted(
            orderReference = "dlq-version-order",
            driverReference = "dlq-version-driver",
            eventId = eventId,
            eventVersion = 2
        )

        val deadLettered = deadLetterObserver.receiveMessageContaining(eventId)
        assertNotNull(deadLettered, "An unsupported eventVersion must reach the dead-letter queue, not be silently discarded")

        // And, decisively, it must never have been applied as if it were
        // version 1: no processed record for this eventId exists.
        assertEquals(false, repository.isProcessed(eventId))
    }
}

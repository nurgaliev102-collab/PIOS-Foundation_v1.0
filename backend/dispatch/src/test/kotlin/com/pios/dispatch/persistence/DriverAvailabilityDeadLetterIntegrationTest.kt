package com.pios.dispatch.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.dispatch.application.DriverAvailabilityProjectionApplicationService
import com.pios.dispatch.domain.DriverReference
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Proves Test E and Test F: a deterministically failing or unsupported
 * message is retried a bounded number of times
 * ([RabbitMQListenerContainerConfiguration.MAX_ATTEMPTS]) and, once
 * exhausted, reaches Dispatch's own dead-letter queue -- never silently
 * discarded, and never applied as if it were the supported version.
 */
class DriverAvailabilityDeadLetterIntegrationTest {

    private val dataSource = PostgreSQLTestDatabase.dataSource
    private val repository = PostgreSQLDriverAvailabilityRepository(JdbcTemplate(dataSource))
    private val transactionRunner = SpringTransactionRunner(TransactionTemplate(DataSourceTransactionManager(dataSource)))
    private val applicationService = DriverAvailabilityProjectionApplicationService(repository, transactionRunner)
    private val listener = DriverAvailabilityChangedListener(applicationService, ObjectMapper())
    private val objectMapper = ObjectMapper()
    private val publisher = DriverAvailabilityChangedMessagePublisher(RabbitMQTestConnection.connectionFactory)
    private val deadLetterObserver = RabbitMQTestDeadLetterObserver(RabbitMQTestConnection.connectionFactory)
    private val harness = DispatchTestListenerHarness(RabbitMQTestConnection.connectionFactory, listener)

    @AfterTest
    fun stopHarness() {
        harness.stop()
    }

    @Test
    fun `a message that deterministically fails processing exhausts bounded retry and reaches the dead-letter queue`() {
        val marker = "dlq-driver-${UUID.randomUUID()}"
        // Structurally valid envelope (correct eventType/eventVersion) but
        // missing payload.driverId entirely -- the listener's own
        // validation throws on every single attempt, deterministically,
        // never succeeding no matter how many times it is retried.
        val malformedEnvelope = objectMapper.writeValueAsString(
            mapOf(
                "eventId" to marker,
                "eventType" to "DriverAvailabilityChanged",
                "eventVersion" to 1,
                "occurredAt" to Instant.now().toString(),
                "payload" to mapOf("availability" to "AVAILABLE")
            )
        )

        publisher.publishRaw(malformedEnvelope)

        val deadLettered = deadLetterObserver.receiveMessageContaining(marker)

        assertNotNull(deadLettered, "A deterministically failing message must reach the dead-letter queue after bounded retry is exhausted")
    }

    @Test
    fun `an unsupported eventVersion is not processed as version 1 and reaches the dead-letter queue`() {
        val driverReference = "dlq-version-driver-${UUID.randomUUID()}"

        publisher.publishDriverAvailabilityChanged(
            driverReference = driverReference,
            available = true,
            eventVersion = 2
        )

        val deadLettered = deadLetterObserver.receiveMessageContaining(driverReference)
        assertNotNull(deadLettered, "An unsupported eventVersion must reach the dead-letter queue, not be silently discarded")

        // And, decisively, it must never have been applied as if it were
        // version 1: no local availability record for this driver exists.
        assertNull(repository.findByDriverReference(DriverReference(driverReference)))
    }
}

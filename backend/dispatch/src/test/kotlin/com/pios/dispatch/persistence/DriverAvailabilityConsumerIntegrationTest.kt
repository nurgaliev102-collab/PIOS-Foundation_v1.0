package com.pios.dispatch.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.dispatch.application.DriverAvailabilityProjectionApplicationService
import com.pios.dispatch.domain.DriverReference
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Proves Dispatch Consumer Foundation v1.0's end-to-end goal (Test A/B):
 * a real DriverAvailabilityChanged-shaped message, published to the real
 * `driver-management.events` exchange exactly as Driver Management's own
 * publisher would, is consumed by Dispatch's real listener and results in
 * Dispatch's own local availability record reflecting the change -- for
 * both an AVAILABLE and an UNAVAILABLE transition.
 */
class DriverAvailabilityConsumerIntegrationTest {

    private val dataSource = PostgreSQLTestDatabase.dataSource
    private val repository = PostgreSQLDriverAvailabilityRepository(JdbcTemplate(dataSource))
    private val transactionRunner = SpringTransactionRunner(TransactionTemplate(DataSourceTransactionManager(dataSource)))
    private val applicationService = DriverAvailabilityProjectionApplicationService(repository, transactionRunner)
    private val listener = DriverAvailabilityChangedListener(applicationService, ObjectMapper())
    private val publisher = DriverAvailabilityChangedMessagePublisher(RabbitMQTestConnection.connectionFactory)
    private val harness = DispatchTestListenerHarness(RabbitMQTestConnection.connectionFactory, listener)

    @AfterTest
    fun stopHarness() {
        harness.stop()
    }

    @Test
    fun `an AVAILABLE DriverAvailabilityChanged message updates Dispatch's local state to available`() {
        val driverReference = "consumer-driver-${UUID.randomUUID()}"

        publisher.publishDriverAvailabilityChanged(driverReference = driverReference, available = true)

        val record = awaitUntilNotNull { repository.findByDriverReference(DriverReference(driverReference)) }

        assertNotNull(record)
        assertEquals(true, record.available)
    }

    @Test
    fun `an UNAVAILABLE DriverAvailabilityChanged message updates Dispatch's local state to unavailable`() {
        val driverReference = "consumer-driver-${UUID.randomUUID()}"
        // Establish an initial AVAILABLE state first, so the UNAVAILABLE
        // transition is an actual, observable change, not just an initial
        // insert that happens to be false.
        publisher.publishDriverAvailabilityChanged(driverReference = driverReference, available = true)
        awaitUntilNotNull { repository.findByDriverReference(DriverReference(driverReference)) }

        publisher.publishDriverAvailabilityChanged(driverReference = driverReference, available = false)

        val record = awaitUntilNotNull {
            repository.findByDriverReference(DriverReference(driverReference))?.takeIf { !it.available }
        }

        assertNotNull(record)
        assertEquals(false, record.available)
    }

    // --- ADR-069: is_test projection, real broker + real DB ---

    @Test
    fun `a message with payload isTest omitted projects a null (unknown) classification`() {
        val driverReference = "consumer-driver-${UUID.randomUUID()}"

        publisher.publishDriverAvailabilityChanged(driverReference = driverReference, available = true)

        val record = awaitUntilNotNull { repository.findByDriverReference(DriverReference(driverReference)) }

        assertNotNull(record)
        assertEquals(null, record.isTest)
    }

    @Test
    fun `a message with payload isTest true projects true`() {
        val driverReference = "consumer-driver-${UUID.randomUUID()}"

        publisher.publishDriverAvailabilityChanged(driverReference = driverReference, available = true, isTest = true)

        val record = awaitUntilNotNull {
            repository.findByDriverReference(DriverReference(driverReference))?.takeIf { it.isTest == true }
        }

        assertNotNull(record)
        assertEquals(true, record.isTest)
    }

    @Test
    fun `a message with payload isTest false projects false`() {
        val driverReference = "consumer-driver-${UUID.randomUUID()}"

        publisher.publishDriverAvailabilityChanged(driverReference = driverReference, available = true, isTest = false)

        val record = awaitUntilNotNull {
            repository.findByDriverReference(DriverReference(driverReference))?.takeIf { it.isTest == false }
        }

        assertNotNull(record)
        assertEquals(false, record.isTest)
    }
}

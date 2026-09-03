package com.pios.dispatch.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.dispatch.application.PrimaryDriverProjectionApplicationService
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.PassengerReference
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Task 14 (First Refusal Foundation), Tests C and D: a real
 * `PrimaryConnectionDesignated`/`PrimaryConnectionCleared`-shaped message,
 * published to the real `passenger-experience.events` exchange exactly as
 * Passenger Experience's own publisher would, is consumed by Dispatch's
 * real listener and results in Dispatch's own local `PrimaryDriverRecord`
 * projection reflecting the change. Mirrors
 * [DriverAvailabilityConsumerIntegrationTest]'s own exact shape.
 */
class PrimaryConnectionConsumerIntegrationTest {

    private val dataSource = PostgreSQLTestDatabase.dataSource
    private val repository = PostgreSQLPrimaryDriverRepository(JdbcTemplate(dataSource))
    private val transactionRunner = SpringTransactionRunner(TransactionTemplate(DataSourceTransactionManager(dataSource)))
    private val applicationService = PrimaryDriverProjectionApplicationService(repository, transactionRunner)
    private val listener = PrimaryConnectionEventListener(applicationService, ObjectMapper())
    private val publisher = PrimaryConnectionMessagePublisher(RabbitMQTestConnection.connectionFactory)
    private val harness = PrimaryConnectionTestListenerHarness(RabbitMQTestConnection.connectionFactory, listener)

    @AfterTest
    fun stopHarness() {
        harness.stop()
    }

    @Test
    fun `Test C -- a PrimaryConnectionDesignated message updates Dispatch's local projection`() {
        val passengerReference = "consumer-passenger-${UUID.randomUUID()}"
        val driverId = "consumer-driver-${UUID.randomUUID()}"

        publisher.publishDesignated(passengerReference = passengerReference, driverId = driverId)

        val record = awaitUntilNotNull { repository.findByPassenger(PassengerReference(passengerReference)) }

        assertNotNull(record)
        assertEquals(DriverReference(driverId), record.primaryDriverId)
    }

    @Test
    fun `Test D -- a PrimaryConnectionCleared message removes Dispatch's local projection entry`() {
        val passengerReference = "consumer-passenger-${UUID.randomUUID()}"
        val driverId = "consumer-driver-${UUID.randomUUID()}"
        publisher.publishDesignated(passengerReference = passengerReference, driverId = driverId)
        awaitUntilNotNull { repository.findByPassenger(PassengerReference(passengerReference)) }

        publisher.publishCleared(passengerReference = passengerReference)

        awaitUntilNotNull {
            if (repository.findByPassenger(PassengerReference(passengerReference)) == null) Unit else null
        }
        assertNull(repository.findByPassenger(PassengerReference(passengerReference)))
    }

    @Test
    fun `re-designating a different driver updates the projection to the new one, not a second entry`() {
        val passengerReference = "consumer-passenger-${UUID.randomUUID()}"
        val firstDriver = "consumer-driver-first-${UUID.randomUUID()}"
        val secondDriver = "consumer-driver-second-${UUID.randomUUID()}"
        publisher.publishDesignated(passengerReference = passengerReference, driverId = firstDriver)
        awaitUntilNotNull { repository.findByPassenger(PassengerReference(passengerReference)) }

        publisher.publishDesignated(passengerReference = passengerReference, driverId = secondDriver)

        val record = awaitUntilNotNull {
            repository.findByPassenger(PassengerReference(passengerReference))
                ?.takeIf { it.primaryDriverId == DriverReference(secondDriver) }
        }
        assertNotNull(record)
        assertEquals(DriverReference(secondDriver), record.primaryDriverId)
    }
}

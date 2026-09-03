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

/**
 * Task 14 (First Refusal Foundation), Test E: the projection is
 * idempotent. RabbitMQ is an at-least-once broker: the same eventId may
 * be delivered more than once. Delivers the identical eventId twice, the
 * second time naming a *different* driver: if idempotency genuinely
 * works, the second (duplicate-eventId) delivery is ignored entirely and
 * Dispatch's local state still reflects only the first delivery's effect.
 * Mirrors [DriverAvailabilityIdempotencyIntegrationTest]'s own exact
 * construction and reasoning.
 */
class PrimaryConnectionIdempotencyIntegrationTest {

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
    fun `redelivering the same eventId does not re-apply a PrimaryConnectionDesignated a second time`() {
        val passengerReference = "idempotency-passenger-${UUID.randomUUID()}"
        val firstDriver = "idempotency-driver-first-${UUID.randomUUID()}"
        val secondDriver = "idempotency-driver-second-${UUID.randomUUID()}"
        val eventId = UUID.randomUUID().toString()

        publisher.publishDesignated(passengerReference = passengerReference, driverId = firstDriver, eventId = eventId)
        val firstProcessed = awaitUntilNotNull { repository.findByPassenger(PassengerReference(passengerReference)) }
        assertNotNull(firstProcessed)
        assertEquals(DriverReference(firstDriver), firstProcessed.primaryDriverId)

        // Same eventId, a different driver -- a genuine duplicate would
        // never actually change the projection, but this makes it
        // possible to observe whether the duplicate was ignored at all.
        publisher.publishDesignated(passengerReference = passengerReference, driverId = secondDriver, eventId = eventId)

        // Give the duplicate delivery time to reach the listener before
        // asserting the state never changed.
        Thread.sleep(2000L)

        val finalState = repository.findByPassenger(PassengerReference(passengerReference))
        assertNotNull(finalState)
        assertEquals(
            DriverReference(firstDriver),
            finalState.primaryDriverId,
            "A redelivered eventId must not re-apply its effect"
        )
    }

    @Test
    fun `redelivering the same eventId does not re-apply a PrimaryConnectionCleared a second time`() {
        val passengerReference = "idempotency-passenger-clear-${UUID.randomUUID()}"
        val driverId = "idempotency-driver-${UUID.randomUUID()}"
        publisher.publishDesignated(passengerReference = passengerReference, driverId = driverId)
        awaitUntilNotNull { repository.findByPassenger(PassengerReference(passengerReference)) }

        val clearEventId = UUID.randomUUID().toString()
        publisher.publishCleared(passengerReference = passengerReference, eventId = clearEventId)
        awaitUntilNotNull {
            if (repository.findByPassenger(PassengerReference(passengerReference)) == null) Unit else null
        }

        // Re-designate the same passenger, then redeliver the OLD clear
        // eventId -- if the ledger is genuinely keyed by eventId (not by
        // passenger+effect), this redelivery must be ignored, leaving the
        // fresh designation intact.
        publisher.publishDesignated(passengerReference = passengerReference, driverId = driverId)
        awaitUntilNotNull { repository.findByPassenger(PassengerReference(passengerReference)) }

        publisher.publishCleared(passengerReference = passengerReference, eventId = clearEventId)
        Thread.sleep(2000L)

        val finalState = repository.findByPassenger(PassengerReference(passengerReference))
        assertNotNull(finalState, "A redelivered clear eventId must not re-apply its effect")
        assertEquals(DriverReference(driverId), finalState.primaryDriverId)
    }
}

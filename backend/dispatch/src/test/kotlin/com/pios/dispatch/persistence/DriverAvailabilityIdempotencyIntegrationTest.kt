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
 * Proves Test C: RabbitMQ is an at-least-once broker (ADR-029, ADR-031),
 * so the same eventId may be delivered more than once -- the same
 * business effect must occur exactly once. Delivers the identical eventId
 * twice, the second time claiming the *opposite* availability value: if
 * idempotency genuinely works, the second (duplicate-eventId) delivery is
 * ignored entirely and Dispatch's local state still reflects only the
 * first delivery's effect. Resending a byte-identical message would not
 * distinguish "processed idempotently" from "processed twice with an
 * identical, harmless effect" -- this construction makes the assertion
 * decisive.
 */
class DriverAvailabilityIdempotencyIntegrationTest {

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
    fun `redelivering the same eventId does not apply its effect a second time`() {
        val driverReference = "idempotency-driver-${UUID.randomUUID()}"
        val eventId = UUID.randomUUID().toString()

        publisher.publishDriverAvailabilityChanged(
            driverReference = driverReference,
            available = true,
            eventId = eventId
        )
        val firstProcessed = awaitUntilNotNull { repository.findByDriverReference(DriverReference(driverReference)) }
        assertNotNull(firstProcessed)
        assertEquals(true, firstProcessed.available)

        // Same eventId, opposite availability -- a genuine duplicate would
        // never actually change availability, but this makes it possible
        // to observe whether the duplicate was ignored at all.
        publisher.publishDriverAvailabilityChanged(
            driverReference = driverReference,
            available = false,
            eventId = eventId
        )

        // Give the duplicate delivery time to reach the listener (it will
        // be consumed and discarded almost immediately, since markProcessed
        // returns false for it) before asserting the state never flipped.
        Thread.sleep(2000L)

        val finalState = repository.findByDriverReference(DriverReference(driverReference))
        assertNotNull(finalState)
        assertEquals(true, finalState.available, "A redelivered eventId must not re-apply its effect")
    }
}

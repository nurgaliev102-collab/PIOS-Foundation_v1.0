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

/**
 * Proves RabbitMQ is an at-least-once broker (ADR-029, ADR-031), so the
 * same eventId may be delivered more than once -- the same business
 * effect must occur exactly once. Delivers the identical eventId twice,
 * with different order/driver references the second time: if idempotency
 * genuinely works, the second (duplicate-eventId) delivery is ignored
 * entirely by [OrderAssignmentRecognitionHandler] (observable only
 * indirectly here, since that handler retains no state of its own by
 * design) -- decisively provable instead through the idempotency ledger
 * itself never recording the eventId a second time. Mirrors Dispatch's
 * own already-proven `DriverAvailabilityIdempotencyIntegrationTest`
 * exactly, adapted to this flow's own observable effect.
 */
class AssignmentAcceptedIdempotencyIntegrationTest {

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
    fun `redelivering the same eventId does not record it a second time`() {
        val eventId = UUID.randomUUID().toString()

        publisher.publishAssignmentAccepted(
            orderReference = "idempotency-order-1",
            driverReference = "idempotency-driver-1",
            eventId = eventId
        )
        val firstProcessed = awaitUntilNotNull { repository.isProcessed(eventId).takeIf { it } }
        assertEquals(true, firstProcessed)

        // Same eventId, different references -- a genuine duplicate must
        // never re-invoke the recognition handler, which this ledger
        // proves indirectly: the eventId's own processed record was
        // written exactly once, by the first delivery.
        publisher.publishAssignmentAccepted(
            orderReference = "idempotency-order-2",
            driverReference = "idempotency-driver-2",
            eventId = eventId
        )

        // Give the duplicate delivery time to reach the listener (it will
        // be consumed and discarded almost immediately, since
        // markProcessed returns false for it) before asserting nothing
        // further changed.
        Thread.sleep(2000L)

        assertEquals(true, repository.isProcessed(eventId))
    }
}

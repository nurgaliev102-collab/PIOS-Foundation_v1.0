package com.pios.dispatch.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.dispatch.application.OrderCancelledApplicationService
import com.pios.dispatch.application.ProposalApplicationService
import com.pios.dispatch.application.ProposeDriverCommand
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.domain.ProposalStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Proves ADR-053's (Proposal Resolution on Order Cancellation — Accepted)
 * end-to-end goal: a real OrderCancelled-shaped message, published to the
 * real `order-management.events` exchange exactly as Order Management's
 * own publisher would, is consumed by Dispatch's real listener and
 * results in the cancelled order's own `OPEN` Proposal reaching
 * `WITHDRAWN` — never calling the sweep/service directly. Mirrors
 * [DriverAvailabilityConsumerIntegrationTest] exactly.
 */
class OrderCancelledConsumerIntegrationTest {

    private val dataSource = PostgreSQLTestDatabase.dataSource
    private val proposalRepository = PostgreSQLProposalRepository(JdbcTemplate(dataSource))
    private val orderCancelledRepository = PostgreSQLOrderCancelledRepository(JdbcTemplate(dataSource))
    private val transactionRunner = SpringTransactionRunner(TransactionTemplate(DataSourceTransactionManager(dataSource)))
    private val proposalApplicationService = ProposalApplicationService(proposalRepository, transactionRunner)
    private val applicationService = OrderCancelledApplicationService(
        orderCancelledRepository,
        proposalRepository,
        proposalApplicationService,
        transactionRunner
    )
    private val listener = OrderCancelledListener(applicationService, ObjectMapper())
    private val publisher = OrderCancelledMessagePublisher(RabbitMQTestConnection.connectionFactory)
    private val harness = OrderCancelledTestListenerHarness(RabbitMQTestConnection.connectionFactory, listener)

    @AfterTest
    fun stopHarness() {
        harness.stop()
    }

    @Test
    fun `an OrderCancelled message withdraws the order's own OPEN proposal`() {
        val orderReference = "consumer-order-${UUID.randomUUID()}"
        val order = OrderReference(orderReference)
        val proposal = proposalApplicationService.handle(
            ProposeDriverCommand(order, DriverReference("consumer-driver-${UUID.randomUUID()}"))
        ).proposal

        publisher.publishOrderCancelled(orderReference = orderReference)

        val withdrawn = awaitUntilNotNull {
            proposalRepository.findById(proposal.id)?.takeIf { it.status == ProposalStatus.WITHDRAWN }
        }

        assertEquals(ProposalStatus.WITHDRAWN, withdrawn?.status)
    }

    @Test
    fun `an OrderCancelled message for an order with no proposal produces no error and no orphan effect`() {
        val orderReference = "consumer-order-no-proposal-${UUID.randomUUID()}"
        val eventId = UUID.randomUUID().toString()

        publisher.publishOrderCancelled(orderReference = orderReference, eventId = eventId)

        // No Proposal exists for this order, before or after -- the
        // absence of anything to withdraw is not an error (ADR-053 Part
        // 1). Awaits the event actually being processed (idempotency
        // ledger row appears) rather than asserting immediately, so this
        // test does not merely pass by finishing before the listener runs.
        val processed = awaitUntilNotNull {
            JdbcTemplate(dataSource).queryForList(
                "SELECT event_id FROM order_cancelled_processed_events WHERE event_id = ?",
                String::class.java,
                eventId
            ).firstOrNull()
        }
        kotlin.test.assertNotNull(processed)
    }
}

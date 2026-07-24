package com.pios.ordermanagement.persistence

import com.pios.ordermanagement.application.OrderLifecycleApplicationService
import com.pios.ordermanagement.application.OutboxRelay
import com.pios.ordermanagement.application.SubmitOrderCommand
import com.pios.ordermanagement.domain.OrderOrigin
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Proves RabbitMQ Event Publishing Foundation v1.0's end-to-end goal:
 * submitting an order eventually produces a real, observable
 * OrderSubmitted message on Order Management's own RabbitMQ exchange —
 * not merely that the outbox, relay, and publisher each work in
 * isolation.
 */
class OrderSubmittedPublicationTest {

    private val dataSource = PostgreSQLTestDatabase.dataSource
    private val orderRepository = PostgreSQLOrderRepository(JdbcTemplate(dataSource))
    private val outboxRepository = PostgreSQLOutboxRepository(JdbcTemplate(dataSource))
    private val transactionRunner = SpringTransactionRunner(TransactionTemplate(DataSourceTransactionManager(dataSource)))
    private val eventPublisher = RabbitMQEventPublisher(RabbitTemplate(RabbitMQTestConnection.connectionFactory))
    private val service = OrderLifecycleApplicationService(orderRepository, outboxRepository, transactionRunner)
    private val relay = OutboxRelay(outboxRepository, eventPublisher)
    private val origin = OrderOrigin("origin-1")

    @Test
    fun `submitting an order eventually publishes OrderSubmitted to Order Management's own exchange`() {
        val observer = RabbitMQTestObserverQueue(RabbitMQTestConnection.connectionFactory)

        val submitted = service.submitOrder(SubmitOrderCommand(origin))
        relay.relay()

        val received = observer.receiveMessageContaining(submitted.order.id.value)

        assertTrue(received != null)
        assertTrue(outboxRepository.findUnpublished().none { it.aggregateId == submitted.order.id.value })
    }
}

package com.pios.dispatch.persistence

import org.springframework.amqp.core.MessageListener
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerEndpoint
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitAdmin
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer

/**
 * Test-only harness that runs Dispatch's real, production
 * [OrderCancelledListener] against the real, locally-running RabbitMQ
 * broker, without booting a Spring [org.springframework.context.ApplicationContext]
 * -- mirrors [DispatchTestListenerHarness] exactly, for the new consumer
 * ADR-053 (Proposal Resolution on Order Cancellation — Accepted) adds.
 *
 * Reuses the actual production [RabbitMQOrderManagementTopologyConfiguration]
 * and [RabbitMQListenerContainerConfiguration] classes directly, so the
 * topology declared and the retry/recoverer behavior exercised here are
 * identical to what a real Spring Boot application would use.
 */
internal class OrderCancelledTestListenerHarness(
    connectionFactory: ConnectionFactory,
    listener: OrderCancelledListener
) {
    private val topology = RabbitMQOrderManagementTopologyConfiguration()
    private val container: SimpleMessageListenerContainer

    init {
        val exchange = topology.orderManagementEventsExchange()
        val queue = topology.dispatchFromOrderManagementQueue()
        val deadLetterQueue = topology.dispatchFromOrderManagementDeadLetterQueue()
        val binding = topology.dispatchFromOrderManagementBinding(queue, exchange)

        val admin = RabbitAdmin(connectionFactory)
        admin.declareExchange(exchange)
        admin.declareQueue(deadLetterQueue)
        admin.declareQueue(queue)
        admin.declareBinding(binding)
        // Every test run starts from an empty queue and DLQ -- mirrors
        // DispatchTestListenerHarness's own reasoning exactly.
        admin.purgeQueue(RabbitMQOrderManagementTopologyConfiguration.QUEUE_NAME)
        admin.purgeQueue(RabbitMQOrderManagementTopologyConfiguration.DEAD_LETTER_QUEUE_NAME)

        val factory = RabbitMQListenerContainerConfiguration().rabbitListenerContainerFactory(connectionFactory)
        val endpoint = SimpleRabbitListenerEndpoint()
        endpoint.setQueueNames(RabbitMQOrderManagementTopologyConfiguration.QUEUE_NAME)
        endpoint.messageListener = MessageListener { message ->
            listener.onMessage(String(message.body, Charsets.UTF_8))
        }
        container = factory.createListenerContainer(endpoint)
        container.start()
    }

    fun stop() {
        container.stop()
    }
}

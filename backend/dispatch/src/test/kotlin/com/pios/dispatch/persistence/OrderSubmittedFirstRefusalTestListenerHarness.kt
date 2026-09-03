package com.pios.dispatch.persistence

import org.springframework.amqp.core.MessageListener
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerEndpoint
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitAdmin
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer

/**
 * Test-only harness that runs Dispatch's real, production
 * [OrderSubmittedFirstRefusalListener] against the real, locally-running
 * RabbitMQ broker, without booting a Spring
 * [org.springframework.context.ApplicationContext] -- mirrors
 * [DispatchTestListenerHarness]/[PrimaryConnectionTestListenerHarness]'s
 * own exact structure, wired to the queue Task 16 added to
 * [RabbitMQOrderManagementTopologyConfiguration] (distinct from
 * [DispatchTestListenerHarness]'s own `OrderCancelled` queue on the same
 * topology class — the two are never conflated).
 */
internal class OrderSubmittedFirstRefusalTestListenerHarness(
    connectionFactory: ConnectionFactory,
    listener: OrderSubmittedFirstRefusalListener
) {
    private val topology = RabbitMQOrderManagementTopologyConfiguration()
    private val container: SimpleMessageListenerContainer

    init {
        val exchange = topology.orderManagementEventsExchange()
        val queue = topology.dispatchFromOrderManagementOrderSubmittedQueue()
        val deadLetterQueue = topology.dispatchFromOrderManagementOrderSubmittedDeadLetterQueue()
        val binding = topology.dispatchFromOrderManagementOrderSubmittedBinding(queue, exchange)

        val admin = RabbitAdmin(connectionFactory)
        admin.declareExchange(exchange)
        admin.declareQueue(deadLetterQueue)
        admin.declareQueue(queue)
        admin.declareBinding(binding)
        // Every test run starts from an empty queue and DLQ -- mirrors
        // every other harness's own identical reasoning.
        admin.purgeQueue(RabbitMQOrderManagementTopologyConfiguration.ORDER_SUBMITTED_QUEUE_NAME)
        admin.purgeQueue(RabbitMQOrderManagementTopologyConfiguration.ORDER_SUBMITTED_DEAD_LETTER_QUEUE_NAME)

        val factory = RabbitMQListenerContainerConfiguration().rabbitListenerContainerFactory(connectionFactory)
        val endpoint = SimpleRabbitListenerEndpoint()
        endpoint.setQueueNames(RabbitMQOrderManagementTopologyConfiguration.ORDER_SUBMITTED_QUEUE_NAME)
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

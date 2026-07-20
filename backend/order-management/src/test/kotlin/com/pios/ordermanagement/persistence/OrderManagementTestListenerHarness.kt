package com.pios.ordermanagement.persistence

import org.springframework.amqp.core.MessageListener
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerEndpoint
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitAdmin
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer

/**
 * Test-only harness that runs Order Management's real, production
 * [AssignmentAcceptedListener] against the real, locally-running RabbitMQ
 * broker, without booting a Spring [org.springframework.context.ApplicationContext]
 * -- consistent with this project's established testing convention of
 * constructing collaborators directly rather than through a full Spring
 * container. Mirrors Dispatch's own already-proven
 * `DispatchTestListenerHarness` exactly (Tranche 1: Dispatch Event
 * Publishing Completion).
 *
 * Reuses the actual production [RabbitMQConsumerTopologyConfiguration]
 * and [RabbitMQListenerContainerConfiguration] classes directly (their
 * `@Bean` methods are plain functions when called outside a container),
 * so the topology declared and the retry/recoverer behavior exercised
 * here are identical to what a real Spring Boot application would use --
 * not a parallel, hand-duplicated test version that could drift from it.
 */
internal class OrderManagementTestListenerHarness(
    connectionFactory: ConnectionFactory,
    listener: AssignmentAcceptedListener
) {
    private val topology = RabbitMQConsumerTopologyConfiguration()
    private val container: SimpleMessageListenerContainer

    init {
        val exchange = topology.dispatchEventsExchange()
        val queue = topology.orderManagementFromDispatchQueue()
        val deadLetterQueue = topology.orderManagementFromDispatchDeadLetterQueue()
        val binding = topology.orderManagementFromDispatchBinding(queue, exchange)

        val admin = RabbitAdmin(connectionFactory)
        admin.declareExchange(exchange)
        admin.declareQueue(deadLetterQueue)
        admin.declareQueue(queue)
        admin.declareBinding(binding)
        // Every test run starts from an empty queue and DLQ -- this
        // module's own broker resources persist across test runs (same
        // reasoning already established for the producer-side observer
        // queues in this and every other module's own tests).
        admin.purgeQueue(RabbitMQConsumerTopologyConfiguration.QUEUE_NAME)
        admin.purgeQueue(RabbitMQConsumerTopologyConfiguration.DEAD_LETTER_QUEUE_NAME)

        val factory = RabbitMQListenerContainerConfiguration().rabbitListenerContainerFactory(connectionFactory)
        val endpoint = SimpleRabbitListenerEndpoint()
        endpoint.setQueueNames(RabbitMQConsumerTopologyConfiguration.QUEUE_NAME)
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

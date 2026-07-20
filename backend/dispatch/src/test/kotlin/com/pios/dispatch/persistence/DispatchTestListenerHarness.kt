package com.pios.dispatch.persistence

import org.springframework.amqp.core.MessageListener
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerEndpoint
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitAdmin
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer

/**
 * Test-only harness that runs Dispatch's real, production
 * [DriverAvailabilityChangedListener] against the real, locally-running
 * RabbitMQ broker, without booting a Spring [org.springframework.context.ApplicationContext]
 * -- consistent with this project's established testing convention of
 * constructing collaborators directly rather than through a full Spring
 * container.
 *
 * Reuses the actual production [RabbitMQTopologyConfiguration] and
 * [RabbitMQListenerContainerConfiguration] classes directly (their
 * `@Bean` methods are plain functions when called outside a container),
 * so the topology declared and the retry/recoverer behavior exercised
 * here are identical to what a real Spring Boot application would use --
 * not a parallel, hand-duplicated test version that could drift from it.
 */
internal class DispatchTestListenerHarness(
    connectionFactory: ConnectionFactory,
    listener: DriverAvailabilityChangedListener
) {
    private val topology = RabbitMQTopologyConfiguration()
    private val container: SimpleMessageListenerContainer

    init {
        val exchange = topology.driverManagementEventsExchange()
        val queue = topology.dispatchFromDriverManagementQueue()
        val deadLetterQueue = topology.dispatchFromDriverManagementDeadLetterQueue()
        val binding = topology.dispatchFromDriverManagementBinding(queue, exchange)

        val admin = RabbitAdmin(connectionFactory)
        admin.declareExchange(exchange)
        admin.declareQueue(deadLetterQueue)
        admin.declareQueue(queue)
        admin.declareBinding(binding)
        // Every test run starts from an empty queue and DLQ -- this
        // module's own broker resources persist across test runs (same
        // reasoning already established for the producer-side observer
        // queues in Order/Driver Management's own tests).
        admin.purgeQueue(RabbitMQTopologyConfiguration.QUEUE_NAME)
        admin.purgeQueue(RabbitMQTopologyConfiguration.DEAD_LETTER_QUEUE_NAME)

        val factory = RabbitMQListenerContainerConfiguration().rabbitListenerContainerFactory(connectionFactory)
        val endpoint = SimpleRabbitListenerEndpoint()
        endpoint.setQueueNames(RabbitMQTopologyConfiguration.QUEUE_NAME)
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

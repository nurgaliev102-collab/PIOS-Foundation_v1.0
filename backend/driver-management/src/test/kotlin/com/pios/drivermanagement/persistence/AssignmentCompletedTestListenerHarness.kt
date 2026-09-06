package com.pios.drivermanagement.persistence

import org.springframework.amqp.core.MessageListener
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerEndpoint
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitAdmin
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer

/**
 * Growth Loops TZ v1, Phase 2 (docs/PIOS_GROWTH_LOOPS_TZ_V1.md Section 2):
 * test-only harness that runs Driver Management's real, production
 * [AssignmentCompletedListener] against the real (test-vhost) RabbitMQ
 * broker, without booting a Spring [org.springframework.context.ApplicationContext]
 * -- mirrors Order Management's own `AssignmentCompletedTestListenerHarness`
 * exactly. Reuses the actual production [RabbitMQConsumerTopologyConfiguration]
 * and [RabbitMQListenerContainerConfiguration] classes directly, so the
 * topology and retry/recoverer behavior exercised here are identical to a
 * real Spring Boot application's.
 */
internal class AssignmentCompletedTestListenerHarness(
    connectionFactory: ConnectionFactory,
    listener: AssignmentCompletedListener
) {
    private val topology = RabbitMQConsumerTopologyConfiguration()
    private val container: SimpleMessageListenerContainer

    init {
        val exchange = topology.dispatchEventsExchange()
        val queue = topology.driverManagementFromDispatchQueue()
        val deadLetterQueue = topology.driverManagementFromDispatchDeadLetterQueue()
        val binding = topology.driverManagementFromDispatchBinding(queue, exchange)

        val admin = RabbitAdmin(connectionFactory)
        admin.declareExchange(exchange)
        admin.declareQueue(deadLetterQueue)
        admin.declareQueue(queue)
        admin.declareBinding(binding)
        // Every test run starts from an empty queue and DLQ -- this
        // module's own test-vhost broker resources persist across test runs.
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

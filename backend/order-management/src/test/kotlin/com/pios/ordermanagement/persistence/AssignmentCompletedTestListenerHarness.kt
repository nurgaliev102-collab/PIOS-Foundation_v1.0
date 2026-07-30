package com.pios.ordermanagement.persistence

import org.springframework.amqp.core.MessageListener
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerEndpoint
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitAdmin
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer

/**
 * Test-only harness that runs Order Management's real, production
 * [AssignmentCompletedListener] against the real, locally-running RabbitMQ
 * broker, without booting a Spring [org.springframework.context.ApplicationContext].
 * Mirrors [OrderManagementTestListenerHarness] exactly, adapted to
 * ADR-041's own, separate queue/DLQ/binding
 * ([RabbitMQConsumerTopologyConfiguration.ASSIGNMENT_COMPLETED_QUEUE_NAME]).
 *
 * Reuses the actual production [RabbitMQConsumerTopologyConfiguration] and
 * [RabbitMQListenerContainerConfiguration] classes directly, so the
 * topology declared and the retry/recoverer behavior exercised here are
 * identical to what a real Spring Boot application would use.
 */
internal class AssignmentCompletedTestListenerHarness(
    connectionFactory: ConnectionFactory,
    listener: AssignmentCompletedListener
) {
    private val topology = RabbitMQConsumerTopologyConfiguration()
    private val container: SimpleMessageListenerContainer

    init {
        val exchange = topology.dispatchEventsExchange()
        val queue = topology.assignmentCompletedQueue()
        val deadLetterQueue = topology.assignmentCompletedDeadLetterQueue()
        val binding = topology.assignmentCompletedBinding(queue, exchange)

        val admin = RabbitAdmin(connectionFactory)
        admin.declareExchange(exchange)
        admin.declareQueue(deadLetterQueue)
        admin.declareQueue(queue)
        admin.declareBinding(binding)
        // Every test run starts from an empty queue and DLQ -- this
        // module's own broker resources persist across test runs (same
        // reasoning already established for [OrderManagementTestListenerHarness]).
        admin.purgeQueue(RabbitMQConsumerTopologyConfiguration.ASSIGNMENT_COMPLETED_QUEUE_NAME)
        admin.purgeQueue(RabbitMQConsumerTopologyConfiguration.ASSIGNMENT_COMPLETED_DEAD_LETTER_QUEUE_NAME)

        val factory = RabbitMQListenerContainerConfiguration().rabbitListenerContainerFactory(connectionFactory)
        val endpoint = SimpleRabbitListenerEndpoint()
        endpoint.setQueueNames(RabbitMQConsumerTopologyConfiguration.ASSIGNMENT_COMPLETED_QUEUE_NAME)
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

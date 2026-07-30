package com.pios.ordermanagement.persistence

import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate

/**
 * Test-only helper that observes Order Management's own dead-letter queue
 * ([RabbitMQConsumerTopologyConfiguration.DEAD_LETTER_QUEUE_NAME] by
 * default), used only to prove the bounded retry-then-DLQ policy
 * (ADR-031) actually routes an exhausted message there. The queue itself
 * is declared by [OrderManagementTestListenerHarness] (mirroring
 * production topology); this class only reads from it. Mirrors Dispatch's
 * own already-proven observer exactly (Tranche 1: Dispatch Event
 * Publishing Completion).
 *
 * [queueName] defaults to the AssignmentAccepted consumer's own
 * dead-letter queue but accepts any queue name, so ADR-041's own,
 * separate AssignmentCompleted dead-letter queue
 * ([RabbitMQConsumerTopologyConfiguration.ASSIGNMENT_COMPLETED_DEAD_LETTER_QUEUE_NAME],
 * declared by [AssignmentCompletedTestListenerHarness]) can reuse this
 * same observer rather than a duplicated class.
 */
internal class RabbitMQTestDeadLetterObserver(
    connectionFactory: ConnectionFactory,
    private val queueName: String = RabbitMQConsumerTopologyConfiguration.DEAD_LETTER_QUEUE_NAME
) {
    private val rabbitTemplate = RabbitTemplate(connectionFactory)

    fun receiveMessageContaining(
        expectedSubstring: String,
        maxAttempts: Int = 40,
        perAttemptTimeoutMillis: Long = 250L
    ): String? {
        repeat(maxAttempts) {
            val message = rabbitTemplate.receiveAndConvert(
                queueName,
                perAttemptTimeoutMillis
            ) as String?
            if (message != null && message.contains(expectedSubstring)) {
                return message
            }
        }
        return null
    }
}

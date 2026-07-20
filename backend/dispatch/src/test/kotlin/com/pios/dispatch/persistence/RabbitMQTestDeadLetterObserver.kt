package com.pios.dispatch.persistence

import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate

/**
 * Test-only helper that observes Dispatch's own dead-letter queue
 * ([RabbitMQTopologyConfiguration.DEAD_LETTER_QUEUE_NAME]), used only to
 * prove the bounded retry-then-DLQ policy (ADR-031) actually routes an
 * exhausted message there. The queue itself is declared by
 * [DispatchTestListenerHarness] (mirroring production topology); this
 * class only reads from it.
 */
internal class RabbitMQTestDeadLetterObserver(connectionFactory: ConnectionFactory) {
    private val rabbitTemplate = RabbitTemplate(connectionFactory)

    fun receiveMessageContaining(
        expectedSubstring: String,
        maxAttempts: Int = 40,
        perAttemptTimeoutMillis: Long = 250L
    ): String? {
        repeat(maxAttempts) {
            val message = rabbitTemplate.receiveAndConvert(
                RabbitMQTopologyConfiguration.DEAD_LETTER_QUEUE_NAME,
                perAttemptTimeoutMillis
            ) as String?
            if (message != null && message.contains(expectedSubstring)) {
                return message
            }
        }
        return null
    }
}

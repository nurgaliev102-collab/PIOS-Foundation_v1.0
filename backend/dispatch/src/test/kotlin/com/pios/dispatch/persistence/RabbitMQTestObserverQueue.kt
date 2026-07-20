package com.pios.dispatch.persistence

import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.TopicExchange
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitAdmin
import org.springframework.amqp.rabbit.core.RabbitTemplate

/**
 * Test-only helper that declares a stable, durable queue bound to
 * Dispatch's own producer exchange with a wildcard routing pattern,
 * purely to observe what [RabbitMQEventPublisher] actually publishes
 * (Tranche 1: Dispatch Event Publishing Completion). Mirrors Order
 * Management's own equivalent test helper exactly — this is test-only
 * verification infrastructure, never a consumer implementation (no
 * production module declares or depends on this).
 */
internal class RabbitMQTestObserverQueue(connectionFactory: ConnectionFactory) {
    private val rabbitTemplate = RabbitTemplate(connectionFactory)
    private val queueName = "dispatch.test-observer"

    init {
        val admin = RabbitAdmin(connectionFactory)
        val exchange = TopicExchange(RabbitMQEventPublisher.EXCHANGE_NAME, true, false)
        admin.declareExchange(exchange)
        val queue = Queue(queueName, true, false, false)
        admin.declareQueue(queue)
        admin.declareBinding(BindingBuilder.bind(queue).to(exchange).with("#"))
        admin.purgeQueue(queueName)
    }

    /**
     * Repeatedly receives messages (up to [maxAttempts], waiting
     * [perAttemptTimeoutMillis] each time) until one containing
     * [expectedSubstring] arrives, ignoring any other message the shared
     * test exchange happens to also carry. Returns null if none matched
     * within the attempt budget.
     */
    fun receiveMessageContaining(
        expectedSubstring: String,
        maxAttempts: Int = 20,
        perAttemptTimeoutMillis: Long = 500L
    ): String? {
        repeat(maxAttempts) {
            val message = rabbitTemplate.receiveAndConvert(queueName, perAttemptTimeoutMillis) as String?
            if (message != null && message.contains(expectedSubstring)) {
                return message
            }
        }
        return null
    }
}

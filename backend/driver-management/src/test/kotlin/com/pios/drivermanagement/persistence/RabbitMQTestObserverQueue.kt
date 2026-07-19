package com.pios.drivermanagement.persistence

import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.TopicExchange
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitAdmin
import org.springframework.amqp.rabbit.core.RabbitTemplate

/**
 * Test-only helper that declares a stable, durable queue bound to Driver
 * Management's own exchange with a wildcard routing pattern, purely to
 * observe what [RabbitMQEventPublisher] actually publishes. Driver
 * Management Event Publishing v1.0's explicit scope is the producer side
 * only -- this is test-only verification infrastructure, never a
 * consumer implementation (no production module declares or depends on
 * this).
 */
internal class RabbitMQTestObserverQueue(connectionFactory: ConnectionFactory) {
    private val rabbitTemplate = RabbitTemplate(connectionFactory)
    private val queueName = "driver-management.test-observer"

    init {
        val admin = RabbitAdmin(connectionFactory)
        val exchange = TopicExchange(RabbitMQEventPublisher.EXCHANGE_NAME, true, false)
        admin.declareExchange(exchange)
        // A plain, durable, non-exclusive, non-auto-delete queue, not an
        // anonymous exclusive one: an exclusive queue's lifecycle is tied
        // to its declaring connection, which proved fragile across the
        // separate RabbitAdmin/RabbitTemplate instances test classes
        // construct here. Durable is required here too -- RabbitMQ 4.x
        // deprecated non-durable, non-exclusive ("transient non-exclusive")
        // queues outright. Re-declaring this same, fixed name with
        // identical arguments across test classes is idempotent and harmless.
        val queue = Queue(queueName, true, false, false)
        admin.declareQueue(queue)
        admin.declareBinding(BindingBuilder.bind(queue).to(exchange).with("#"))
        // Purge on construction: this durable queue persists across test
        // runs, so without purging, unrelated messages left behind by an
        // earlier run (or an earlier test class in the same run) could
        // exhaust receiveMessageContaining's attempt budget before ever
        // reaching the message this specific test is looking for.
        admin.purgeQueue(queueName)
    }

    /**
     * Repeatedly receives messages (up to [maxAttempts], waiting
     * [perAttemptTimeoutMillis] each time) until one containing
     * [expectedSubstring] arrives, ignoring any other message the shared
     * test exchange happens to also carry (for example, outbox records
     * other test classes left unpublished). Returns null if none matched
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

package com.pios.core.persistence

import org.springframework.amqp.core.MessageListener
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerEndpoint
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitAdmin
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer

/**
 * Test-only harness that runs Core's real, production [OrderEventListener]
 * against a real RabbitMQ broker (the isolated `pios-test` vhost),
 * without booting a Spring `ApplicationContext` — mirrors dispatch's own
 * `OrderCancelledTestListenerHarness` exactly.
 *
 * Reuses the actual production [RabbitMQOrderManagementTopologyConfiguration]
 * and [RabbitMQListenerContainerConfiguration] classes directly, so the
 * topology declared and the retry/recoverer behaviour exercised here are
 * identical to what a real Spring Boot application would use.
 *
 * Reached only via [RabbitMQTestConnection] → the fail-closed
 * [com.pios.core.qa.CoreQaSafetyGate] (ADR-067 QA / Production Gate item 4).
 */
internal class CoreOrderEventTestListenerHarness(
    connectionFactory: ConnectionFactory,
    listener: OrderEventListener
) {
    private val topology = RabbitMQOrderManagementTopologyConfiguration()
    private val container: SimpleMessageListenerContainer

    init {
        val exchange = topology.coreOrderManagementEventsExchange()
        val queue = topology.coreFromOrderManagementQueue()
        val deadLetterQueue = topology.coreFromOrderManagementDeadLetterQueue()
        val bindings = listOf(
            topology.coreFromOrderManagementSubmittedBinding(queue, exchange),
            topology.coreFromOrderManagementCompletedBinding(queue, exchange),
            topology.coreFromOrderManagementCancelledBinding(queue, exchange)
        )

        val admin = RabbitAdmin(connectionFactory)
        admin.declareExchange(exchange)
        admin.declareQueue(deadLetterQueue)
        admin.declareQueue(queue)
        bindings.forEach { admin.declareBinding(it) }
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

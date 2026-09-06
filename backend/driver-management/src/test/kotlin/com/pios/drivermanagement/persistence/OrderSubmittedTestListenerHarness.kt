package com.pios.drivermanagement.persistence

import org.springframework.amqp.core.MessageListener
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerEndpoint
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitAdmin
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer

/**
 * Growth Loops TZ v1, Phase 2 extension (docs/PIOS_GROWTH_LOOPS_TZ_V1.md
 * Section 2.1's own disclosed gap, now closed): test-only harness that
 * runs Driver Management's real, production [OrderSubmittedListener]
 * against the real (test-vhost) RabbitMQ broker, mirroring
 * [AssignmentCompletedTestListenerHarness] exactly, adapted to
 * [RabbitMQOrderManagementTopologyConfiguration]'s own topology.
 */
internal class OrderSubmittedTestListenerHarness(
    connectionFactory: ConnectionFactory,
    listener: OrderSubmittedListener
) {
    private val topology = RabbitMQOrderManagementTopologyConfiguration()
    private val container: SimpleMessageListenerContainer

    init {
        val exchange = topology.orderManagementEventsExchange()
        val queue = topology.driverManagementFromOrderManagementQueue()
        val deadLetterQueue = topology.driverManagementFromOrderManagementDeadLetterQueue()
        val binding = topology.driverManagementFromOrderManagementBinding(queue, exchange)

        val admin = RabbitAdmin(connectionFactory)
        admin.declareExchange(exchange)
        admin.declareQueue(deadLetterQueue)
        admin.declareQueue(queue)
        admin.declareBinding(binding)
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

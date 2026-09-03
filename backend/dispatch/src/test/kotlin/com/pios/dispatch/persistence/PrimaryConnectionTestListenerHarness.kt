package com.pios.dispatch.persistence

import org.springframework.amqp.core.MessageListener
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerEndpoint
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitAdmin
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer

/**
 * Test-only harness that runs Dispatch's real, production
 * [PrimaryConnectionEventListener] against the real, locally-running
 * RabbitMQ broker, without booting a Spring
 * [org.springframework.context.ApplicationContext] -- mirrors
 * [DispatchTestListenerHarness]'s own exact structure, extended for two
 * bindings (designated + cleared) instead of one.
 *
 * Reuses the actual production
 * [RabbitMQPassengerExperienceTopologyConfiguration] and
 * [RabbitMQListenerContainerConfiguration] classes directly, so the
 * topology declared and the retry/recoverer behavior exercised here are
 * identical to what a real Spring Boot application would use.
 */
internal class PrimaryConnectionTestListenerHarness(
    connectionFactory: ConnectionFactory,
    listener: PrimaryConnectionEventListener
) {
    private val topology = RabbitMQPassengerExperienceTopologyConfiguration()
    private val container: SimpleMessageListenerContainer

    init {
        val exchange = topology.passengerExperienceEventsExchange()
        val queue = topology.dispatchFromPassengerExperienceQueue()
        val deadLetterQueue = topology.dispatchFromPassengerExperienceDeadLetterQueue()
        val designatedBinding = topology.dispatchFromPassengerExperienceDesignatedBinding(queue, exchange)
        val clearedBinding = topology.dispatchFromPassengerExperienceClearedBinding(queue, exchange)

        val admin = RabbitAdmin(connectionFactory)
        admin.declareExchange(exchange)
        admin.declareQueue(deadLetterQueue)
        admin.declareQueue(queue)
        admin.declareBinding(designatedBinding)
        admin.declareBinding(clearedBinding)
        // Every test run starts from an empty queue and DLQ -- mirrors
        // DispatchTestListenerHarness's own identical reasoning.
        admin.purgeQueue(RabbitMQPassengerExperienceTopologyConfiguration.QUEUE_NAME)
        admin.purgeQueue(RabbitMQPassengerExperienceTopologyConfiguration.DEAD_LETTER_QUEUE_NAME)

        val factory = RabbitMQListenerContainerConfiguration().rabbitListenerContainerFactory(connectionFactory)
        val endpoint = SimpleRabbitListenerEndpoint()
        endpoint.setQueueNames(RabbitMQPassengerExperienceTopologyConfiguration.QUEUE_NAME)
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

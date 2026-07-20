package com.pios.ordermanagement.persistence

import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.core.TopicExchange
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Order Management's own consumer-owned RabbitMQ topology (ADR-031;
 * Tranche 1: Dispatch Event Publishing Completion): a queue and
 * dead-letter queue Order Management alone declares, administers, and
 * binds -- never a queue or binding declared by, or shared with,
 * Dispatch. Mirrors Dispatch's own already-proven consumer topology
 * (`RabbitMQTopologyConfiguration`, consuming Driver Management's own
 * events) exactly.
 *
 * Named distinctly from the existing [RabbitMQTopologyConfiguration] --
 * which declares Order Management's own *producer*-side exchange -- so
 * that this module's own new consumer role and its already-existing
 * producer role remain two separate, independently-readable
 * configuration classes rather than one class conflating both.
 *
 * [dispatchEventsExchange] re-declares Dispatch's own producer-owned
 * exchange by name and identical parameters (durable topic exchange),
 * purely so this module's own queue binding succeeds regardless of which
 * of the two modules' Spring contexts starts first -- re-declaring an
 * already-existing exchange with identical parameters is an idempotent
 * no-op in RabbitMQ, the same technique Dispatch's own consumer topology
 * already uses for Driver Management's exchange. This does not modify
 * Dispatch's topology: Order Management never publishes to this
 * exchange, never redeclares it with different parameters, and never
 * administers it beyond its own binding.
 */
@Configuration
class RabbitMQConsumerTopologyConfiguration {

    @Bean
    fun dispatchEventsExchange(): TopicExchange =
        TopicExchange(PRODUCER_EXCHANGE_NAME, true, false)

    @Bean
    fun orderManagementFromDispatchDeadLetterQueue(): Queue =
        QueueBuilder.durable(DEAD_LETTER_QUEUE_NAME).build()

    /**
     * The main consumer queue. `x-dead-letter-exchange` set to the default
     * exchange (`""`) with `x-dead-letter-routing-key` equal to
     * [DEAD_LETTER_QUEUE_NAME] routes an exhausted-retry message straight
     * into [orderManagementFromDispatchDeadLetterQueue] -- every queue is
     * implicitly bound to the default exchange under its own name, so no
     * separate DLQ binding bean is needed.
     */
    @Bean
    fun orderManagementFromDispatchQueue(): Queue =
        QueueBuilder.durable(QUEUE_NAME)
            .withArgument("x-dead-letter-exchange", "")
            .withArgument("x-dead-letter-routing-key", DEAD_LETTER_QUEUE_NAME)
            .build()

    /**
     * Binds only the already-ratified routing key for AssignmentAccepted
     * (INTERFACE_CONTRACTS.md Section 5) -- never a wildcard, since this
     * module is a specific, named consumer, not a generic one (unlike
     * Notifications/Analytics, see ADR-033).
     */
    @Bean
    fun orderManagementFromDispatchBinding(
        orderManagementFromDispatchQueue: Queue,
        dispatchEventsExchange: TopicExchange
    ): Binding =
        BindingBuilder.bind(orderManagementFromDispatchQueue)
            .to(dispatchEventsExchange)
            .with(ASSIGNMENT_ACCEPTED_ROUTING_KEY)

    companion object {
        const val PRODUCER_EXCHANGE_NAME = "dispatch.events"
        const val QUEUE_NAME = "order-management.from-dispatch"
        const val DEAD_LETTER_QUEUE_NAME = "order-management.from-dispatch.dlq"
        const val ASSIGNMENT_ACCEPTED_ROUTING_KEY = "assignment.accepted"
    }
}

package com.pios.drivermanagement.persistence

import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.core.TopicExchange
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Growth Loops TZ v1, Phase 2 (docs/PIOS_GROWTH_LOOPS_TZ_V1.md Section 2):
 * Driver Management's own consumer-owned RabbitMQ topology for Dispatch's
 * `AssignmentCompleted` — mirrors Order Management's own
 * `RabbitMQConsumerTopologyConfiguration` exactly (same exchange
 * re-declaration technique, same dead-letter wiring shape), a second,
 * independent consumer of an event Order Management already consumes.
 * This does not modify Dispatch's own topology in any way: Driver
 * Management never publishes to `dispatch.events`, never redeclares it
 * with different parameters, and owns only its own queue/binding/DLQ, none
 * of it shared with Order Management's own identically-named-in-spirit but
 * distinctly-owned queue.
 *
 * [dispatchEventsExchange] re-declares Dispatch's own producer-owned
 * exchange by name and identical parameters (durable topic exchange) --
 * an idempotent no-op in RabbitMQ regardless of which module's Spring
 * context starts first, the same technique already established twice in
 * this codebase (Dispatch's own consumer of Driver Management's events;
 * Order Management's own consumer of this exact exchange).
 */
@Configuration
class RabbitMQConsumerTopologyConfiguration {

    @Bean
    fun dispatchEventsExchange(): TopicExchange =
        TopicExchange(PRODUCER_EXCHANGE_NAME, true, false)

    @Bean
    fun driverManagementFromDispatchDeadLetterQueue(): Queue =
        QueueBuilder.durable(DEAD_LETTER_QUEUE_NAME).build()

    @Bean
    fun driverManagementFromDispatchQueue(): Queue =
        QueueBuilder.durable(QUEUE_NAME)
            .withArgument("x-dead-letter-exchange", "")
            .withArgument("x-dead-letter-routing-key", DEAD_LETTER_QUEUE_NAME)
            .build()

    /** Binds only the already-ratified routing key for AssignmentCompleted -- never a wildcard. */
    @Bean
    fun driverManagementFromDispatchBinding(
        driverManagementFromDispatchQueue: Queue,
        dispatchEventsExchange: TopicExchange
    ): Binding =
        BindingBuilder.bind(driverManagementFromDispatchQueue)
            .to(dispatchEventsExchange)
            .with(ASSIGNMENT_COMPLETED_ROUTING_KEY)

    companion object {
        const val PRODUCER_EXCHANGE_NAME = "dispatch.events"
        const val QUEUE_NAME = "driver-management.from-dispatch.assignment-completed"
        const val DEAD_LETTER_QUEUE_NAME = "driver-management.from-dispatch.assignment-completed.dlq"
        const val ASSIGNMENT_COMPLETED_ROUTING_KEY = "assignment.completed"
    }
}

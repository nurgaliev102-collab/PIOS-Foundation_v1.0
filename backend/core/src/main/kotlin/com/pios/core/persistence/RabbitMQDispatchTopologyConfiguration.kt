package com.pios.core.persistence

import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.core.TopicExchange
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Core's own consumer-owned RabbitMQ topology for dispatch's
 * `AssignmentCompleted` (ADR-031 / ADR-067): a queue and dead-letter
 * queue Core alone declares, administers, and binds — never a queue or
 * binding declared by, or shared with, dispatch.
 *
 * **Binds the legacy `assignment.completed` routing key, not `trip.*`**
 * (ADR-067 Input Events binding notes: dispatch's `ADR-063` dual-publish
 * window emits both; Core binds the legacy, battle-tested key. When
 * `assignment.*` is retired, Core's binding moves with it in a future
 * slice — not now, and not as a reason to touch anything in dispatch
 * today).
 *
 * Named distinctly from [RabbitMQOrderManagementTopologyConfiguration]
 * and [RabbitMQPassengerExperienceTopologyConfiguration] — one class per
 * producer Core consumes from, the convention dispatch's own consumer
 * topologies establish.
 *
 * [coreDispatchEventsExchange] re-declares dispatch's own producer-owned
 * exchange by name and identical parameters (durable topic exchange),
 * idempotently, purely so Core's queue binding succeeds regardless of
 * context start order. Core never publishes to it, never redeclares it
 * differently, never administers it beyond its own binding (ADR-067).
 */
@Configuration
class RabbitMQDispatchTopologyConfiguration {

    @Bean
    fun coreDispatchEventsExchange(): TopicExchange =
        TopicExchange(PRODUCER_EXCHANGE_NAME, true, false)

    @Bean
    fun coreFromDispatchDeadLetterQueue(): Queue =
        QueueBuilder.durable(DEAD_LETTER_QUEUE_NAME).build()

    @Bean
    fun coreFromDispatchQueue(): Queue =
        QueueBuilder.durable(QUEUE_NAME)
            .withArgument("x-dead-letter-exchange", "")
            .withArgument("x-dead-letter-routing-key", DEAD_LETTER_QUEUE_NAME)
            .build()

    @Bean
    fun coreFromDispatchAssignmentCompletedBinding(
        coreFromDispatchQueue: Queue,
        coreDispatchEventsExchange: TopicExchange
    ): Binding =
        BindingBuilder.bind(coreFromDispatchQueue)
            .to(coreDispatchEventsExchange)
            .with(ASSIGNMENT_COMPLETED_ROUTING_KEY)

    companion object {
        const val PRODUCER_EXCHANGE_NAME = "dispatch.events"
        const val QUEUE_NAME = "core.from-dispatch"
        const val DEAD_LETTER_QUEUE_NAME = "core.from-dispatch.dlq"

        // Verified against dispatch's actual committed publisher,
        // DispatchAssignmentApplicationService.outboxRecordFor(...,
        // AssignmentCompleted, ...): routing key "assignment.completed".
        const val ASSIGNMENT_COMPLETED_ROUTING_KEY = "assignment.completed"
    }
}

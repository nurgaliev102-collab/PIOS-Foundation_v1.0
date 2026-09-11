package com.pios.core.persistence

import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.core.TopicExchange
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Core's own consumer-owned RabbitMQ topology for order-management's
 * events (ADR-031 / ADR-067): a single queue and dead-letter queue Core
 * alone declares, administers, and binds — never a queue or binding
 * declared by, or shared with, order-management.
 *
 * **One queue bound to all three order routing keys.** Unlike dispatch's
 * own reasoning for a dedicated queue per *event kind*
 * (`RabbitMQOrderManagementTopologyConfiguration` there), Core's
 * [OrderEventListener] is a single listener that branches on `eventType`
 * across `OrderSubmitted` / `OrderCompleted` / `OrderCancelled` — exactly
 * the pattern dispatch's own `RabbitMQPassengerExperienceTopologyConfiguration`
 * already establishes for `PrimaryConnectionDesignated` /
 * `PrimaryConnectionCleared` on one queue with one branching listener. A
 * message of any of the three kinds is understood by the one listener, so
 * no message is ever dead-lettered merely for arriving at the "wrong"
 * handler.
 *
 * [orderManagementEventsExchange] re-declares order-management's own
 * producer-owned exchange by name and identical parameters (durable topic
 * exchange), purely so Core's queue binding succeeds regardless of which
 * module's Spring context starts first — re-declaring an already-existing
 * exchange with identical parameters is an idempotent no-op in RabbitMQ,
 * the same technique dispatch's own consumer topologies use. This does
 * not modify order-management's topology: Core never publishes to this
 * exchange, never redeclares it with different parameters, and never
 * administers it beyond its own binding (ADR-067 Write Boundary / Event
 * Boundary).
 */
@Configuration
class RabbitMQOrderManagementTopologyConfiguration {

    @Bean
    fun coreOrderManagementEventsExchange(): TopicExchange =
        TopicExchange(PRODUCER_EXCHANGE_NAME, true, false)

    @Bean
    fun coreFromOrderManagementDeadLetterQueue(): Queue =
        QueueBuilder.durable(DEAD_LETTER_QUEUE_NAME).build()

    /**
     * The main consumer queue. `x-dead-letter-exchange` set to the default
     * exchange (`""`) with `x-dead-letter-routing-key` equal to
     * [DEAD_LETTER_QUEUE_NAME] routes an exhausted-retry message straight
     * into [coreFromOrderManagementDeadLetterQueue] — every queue is
     * implicitly bound to the default exchange under its own name, so no
     * separate DLQ binding bean is needed.
     */
    @Bean
    fun coreFromOrderManagementQueue(): Queue =
        QueueBuilder.durable(QUEUE_NAME)
            .withArgument("x-dead-letter-exchange", "")
            .withArgument("x-dead-letter-routing-key", DEAD_LETTER_QUEUE_NAME)
            .build()

    @Bean
    fun coreFromOrderManagementSubmittedBinding(
        coreFromOrderManagementQueue: Queue,
        coreOrderManagementEventsExchange: TopicExchange
    ): Binding =
        BindingBuilder.bind(coreFromOrderManagementQueue)
            .to(coreOrderManagementEventsExchange)
            .with(ORDER_SUBMITTED_ROUTING_KEY)

    @Bean
    fun coreFromOrderManagementCompletedBinding(
        coreFromOrderManagementQueue: Queue,
        coreOrderManagementEventsExchange: TopicExchange
    ): Binding =
        BindingBuilder.bind(coreFromOrderManagementQueue)
            .to(coreOrderManagementEventsExchange)
            .with(ORDER_COMPLETED_ROUTING_KEY)

    @Bean
    fun coreFromOrderManagementCancelledBinding(
        coreFromOrderManagementQueue: Queue,
        coreOrderManagementEventsExchange: TopicExchange
    ): Binding =
        BindingBuilder.bind(coreFromOrderManagementQueue)
            .to(coreOrderManagementEventsExchange)
            .with(ORDER_CANCELLED_ROUTING_KEY)

    companion object {
        const val PRODUCER_EXCHANGE_NAME = "order-management.events"
        const val QUEUE_NAME = "core.from-order-management"
        const val DEAD_LETTER_QUEUE_NAME = "core.from-order-management.dlq"

        // Verified against order-management's actual committed publisher,
        // OrderLifecycleApplicationService.outboxRecordFor(...): routing
        // keys "order.submitted" / "order.completed" / "order.cancelled".
        // Bound explicitly, never a wildcard — Core is a specific, named
        // consumer of exactly these three (ADR-067 Current Authorized
        // Inputs).
        const val ORDER_SUBMITTED_ROUTING_KEY = "order.submitted"
        const val ORDER_COMPLETED_ROUTING_KEY = "order.completed"
        const val ORDER_CANCELLED_ROUTING_KEY = "order.cancelled"
    }
}

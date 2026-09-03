package com.pios.dispatch.persistence

import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.core.TopicExchange
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Dispatch's own consumer-owned RabbitMQ topology for Passenger
 * Experience's `PrimaryConnectionDesignated`/`PrimaryConnectionCleared`
 * (ADR-031; ADR-062, Primary Driver / First Refusal — Passenger
 * Experience → Dispatch Contract, Accepted; Task 14, First Refusal
 * Foundation): a queue and dead-letter queue Dispatch alone declares,
 * administers, and binds — never a queue or binding declared by, or
 * shared with, Passenger Experience.
 *
 * Named distinctly from [RabbitMQTopologyConfiguration] (Driver
 * Management) and [RabbitMQOrderManagementTopologyConfiguration] (Order
 * Management) — one class per producer this module consumes from, never
 * one class conflating unrelated exchanges, exactly the convention those
 * two already establish.
 *
 * A single queue bound to **both** routing keys: unlike
 * [RabbitMQOrderManagementTopologyConfiguration]'s own reasoning for a
 * dedicated queue per *module*, `PrimaryConnectionDesignated` and
 * `PrimaryConnectionCleared` are two outcomes of the same underlying
 * relationship signal, both consumed by the identical projection logic
 * ([com.pios.dispatch.application.PrimaryDriverProjectionApplicationService]),
 * and [PrimaryConnectionEventListener] itself dispatches on `eventType`
 * — mirroring how a single queue already safely carries more than one
 * event type only when one listener already branches on it (as opposed
 * to ADR-053 Part 4's own concern, which was two *unrelated* event kinds
 * needing two *different* handlers).
 *
 * [passengerExperienceEventsExchange] re-declares Passenger Experience's
 * own producer-owned exchange by name and identical parameters (durable
 * topic exchange), purely so this module's own queue binding succeeds
 * regardless of which of the two modules' Spring contexts starts first —
 * the same idempotent-redeclaration technique
 * [RabbitMQTopologyConfiguration.driverManagementEventsExchange] already
 * uses. This does not modify Passenger Experience's topology: Dispatch
 * never publishes to this exchange, never redeclares it with different
 * parameters, and never administers it beyond its own binding.
 */
@Configuration
class RabbitMQPassengerExperienceTopologyConfiguration {

    @Bean
    fun passengerExperienceEventsExchange(): TopicExchange =
        TopicExchange(PRODUCER_EXCHANGE_NAME, true, false)

    @Bean
    fun dispatchFromPassengerExperienceDeadLetterQueue(): Queue =
        QueueBuilder.durable(DEAD_LETTER_QUEUE_NAME).build()

    /**
     * The main consumer queue. `x-dead-letter-exchange` set to the default
     * exchange (`""`) with `x-dead-letter-routing-key` equal to
     * [DEAD_LETTER_QUEUE_NAME] routes an exhausted-retry message straight
     * into [dispatchFromPassengerExperienceDeadLetterQueue] — every queue
     * is implicitly bound to the default exchange under its own name, so
     * no separate DLQ binding bean is needed.
     */
    @Bean
    fun dispatchFromPassengerExperienceQueue(): Queue =
        QueueBuilder.durable(QUEUE_NAME)
            .withArgument("x-dead-letter-exchange", "")
            .withArgument("x-dead-letter-routing-key", DEAD_LETTER_QUEUE_NAME)
            .build()

    @Bean
    fun dispatchFromPassengerExperienceDesignatedBinding(
        dispatchFromPassengerExperienceQueue: Queue,
        passengerExperienceEventsExchange: TopicExchange
    ): Binding =
        BindingBuilder.bind(dispatchFromPassengerExperienceQueue)
            .to(passengerExperienceEventsExchange)
            .with(PRIMARY_CONNECTION_DESIGNATED_ROUTING_KEY)

    @Bean
    fun dispatchFromPassengerExperienceClearedBinding(
        dispatchFromPassengerExperienceQueue: Queue,
        passengerExperienceEventsExchange: TopicExchange
    ): Binding =
        BindingBuilder.bind(dispatchFromPassengerExperienceQueue)
            .to(passengerExperienceEventsExchange)
            .with(PRIMARY_CONNECTION_CLEARED_ROUTING_KEY)

    companion object {
        const val PRODUCER_EXCHANGE_NAME = "passenger-experience.events"
        const val QUEUE_NAME = "dispatch.from-passenger-experience"
        const val DEAD_LETTER_QUEUE_NAME = "dispatch.from-passenger-experience.dlq"
        const val PRIMARY_CONNECTION_DESIGNATED_ROUTING_KEY = "primary.connection.designated"
        const val PRIMARY_CONNECTION_CLEARED_ROUTING_KEY = "primary.connection.cleared"
    }
}

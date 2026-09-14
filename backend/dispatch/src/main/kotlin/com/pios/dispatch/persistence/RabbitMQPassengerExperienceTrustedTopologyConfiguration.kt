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
 * Experience's `ConnectionEstablished`/`ConnectionRemoved` (ADR-031;
 * ADR-068, Relationship-Ordered Fallback Dispatch — Trusted → Network →
 * Open Marketplace, Part 1): a queue and dead-letter queue Dispatch alone
 * declares, administers, and binds — never a queue or binding declared
 * by, or shared with, Passenger Experience.
 *
 * A distinct queue/DLQ pair from
 * [RabbitMQPassengerExperienceTopologyConfiguration]'s own
 * `PrimaryConnectionDesignated`/`PrimaryConnectionCleared` queue — ADR-068
 * Part 1 calls for "its own per-consumer DLQ and its own
 * `markProcessed(eventId)` idempotency ledger", so this event pair gets
 * its own queue, its own DLQ, its own listener, and its own idempotency
 * ledger table ([PostgreSQLTrustedDriverRepository]), never sharing any of
 * [PrimaryDriverRecord]'s own consumer infrastructure. This mirrors, not
 * contradicts, [RabbitMQPassengerExperienceTopologyConfiguration]'s own
 * "single queue bound to both routing keys of the same underlying signal"
 * reasoning: `ConnectionEstablished`/`ConnectionRemoved` are two outcomes
 * of one signal (trusted-circle membership), consumed by one projection
 * ([com.pios.dispatch.application.TrustedDriverProjectionApplicationService]),
 * so they still share *this* queue -- it is only kept separate from the
 * *other* signal (primary designation), per ADR-068's own explicit
 * instruction.
 *
 * [passengerExperienceEventsExchange] re-declares Passenger Experience's
 * own producer-owned exchange by name and identical parameters, the same
 * idempotent-redeclaration technique
 * [RabbitMQPassengerExperienceTopologyConfiguration.passengerExperienceEventsExchange]
 * already uses -- declaring the identical exchange from a second
 * `@Configuration` class in this module is safe because RabbitMQ's own
 * exchange declaration is itself idempotent for identical parameters, and
 * this class never redeclares it with different ones. This does not
 * modify Passenger Experience's topology: Dispatch never publishes to
 * this exchange, never redeclares it with different parameters, and never
 * administers it beyond its own binding.
 */
@Configuration
class RabbitMQPassengerExperienceTrustedTopologyConfiguration {

    @Bean
    fun passengerExperienceTrustedEventsExchange(): TopicExchange =
        TopicExchange(RabbitMQPassengerExperienceTopologyConfiguration.PRODUCER_EXCHANGE_NAME, true, false)

    @Bean
    fun dispatchFromPassengerExperienceTrustedDeadLetterQueue(): Queue =
        QueueBuilder.durable(DEAD_LETTER_QUEUE_NAME).build()

    /**
     * The main consumer queue. `x-dead-letter-exchange` set to the default
     * exchange (`""`) with `x-dead-letter-routing-key` equal to
     * [DEAD_LETTER_QUEUE_NAME] routes an exhausted-retry message straight
     * into [dispatchFromPassengerExperienceTrustedDeadLetterQueue].
     */
    @Bean
    fun dispatchFromPassengerExperienceTrustedQueue(): Queue =
        QueueBuilder.durable(QUEUE_NAME)
            .withArgument("x-dead-letter-exchange", "")
            .withArgument("x-dead-letter-routing-key", DEAD_LETTER_QUEUE_NAME)
            .build()

    @Bean
    fun dispatchFromPassengerExperienceConnectionEstablishedBinding(
        dispatchFromPassengerExperienceTrustedQueue: Queue,
        passengerExperienceTrustedEventsExchange: TopicExchange
    ): Binding =
        BindingBuilder.bind(dispatchFromPassengerExperienceTrustedQueue)
            .to(passengerExperienceTrustedEventsExchange)
            .with(CONNECTION_ESTABLISHED_ROUTING_KEY)

    @Bean
    fun dispatchFromPassengerExperienceConnectionRemovedBinding(
        dispatchFromPassengerExperienceTrustedQueue: Queue,
        passengerExperienceTrustedEventsExchange: TopicExchange
    ): Binding =
        BindingBuilder.bind(dispatchFromPassengerExperienceTrustedQueue)
            .to(passengerExperienceTrustedEventsExchange)
            .with(CONNECTION_REMOVED_ROUTING_KEY)

    companion object {
        const val QUEUE_NAME = "dispatch.from-passenger-experience.trusted"
        const val DEAD_LETTER_QUEUE_NAME = "dispatch.from-passenger-experience.trusted.dlq"
        const val CONNECTION_ESTABLISHED_ROUTING_KEY = "connection.established"
        const val CONNECTION_REMOVED_ROUTING_KEY = "connection.removed"
    }
}

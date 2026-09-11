package com.pios.core.persistence

import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.core.TopicExchange
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Core's own consumer-owned RabbitMQ topology for passenger-experience's
 * `PrimaryConnectionDesignated` (ADR-031 / ADR-067): a queue and
 * dead-letter queue Core alone declares, administers, and binds.
 *
 * **Binds only `primary.connection.designated`.**
 * `PrimaryConnectionCleared` (`primary.connection.cleared`) is a Reserved
 * Future Input in ADR-067 — this ADR grants no permission to add a
 * listener or binding for it now, so unlike dispatch's own
 * `RabbitMQPassengerExperienceTopologyConfiguration` (which binds both),
 * Core binds the designated key alone.
 *
 * [corePassengerExperienceEventsExchange] re-declares
 * passenger-experience's own producer-owned exchange by name and
 * identical parameters (durable topic exchange), idempotently. Core never
 * publishes to it, never redeclares it differently, never administers it
 * beyond its own binding (ADR-067).
 */
@Configuration
class RabbitMQPassengerExperienceTopologyConfiguration {

    @Bean
    fun corePassengerExperienceEventsExchange(): TopicExchange =
        TopicExchange(PRODUCER_EXCHANGE_NAME, true, false)

    @Bean
    fun coreFromPassengerExperienceDeadLetterQueue(): Queue =
        QueueBuilder.durable(DEAD_LETTER_QUEUE_NAME).build()

    @Bean
    fun coreFromPassengerExperienceQueue(): Queue =
        QueueBuilder.durable(QUEUE_NAME)
            .withArgument("x-dead-letter-exchange", "")
            .withArgument("x-dead-letter-routing-key", DEAD_LETTER_QUEUE_NAME)
            .build()

    @Bean
    fun coreFromPassengerExperienceDesignatedBinding(
        coreFromPassengerExperienceQueue: Queue,
        corePassengerExperienceEventsExchange: TopicExchange
    ): Binding =
        BindingBuilder.bind(coreFromPassengerExperienceQueue)
            .to(corePassengerExperienceEventsExchange)
            .with(PRIMARY_CONNECTION_DESIGNATED_ROUTING_KEY)

    companion object {
        const val PRODUCER_EXCHANGE_NAME = "passenger-experience.events"
        const val QUEUE_NAME = "core.from-passenger-experience"
        const val DEAD_LETTER_QUEUE_NAME = "core.from-passenger-experience.dlq"

        // Verified against passenger-experience's actual committed
        // publisher, SetPrimaryConnectionApplicationService.outboxRecordFor(
        // PrimaryConnectionDesignated): routing key
        // "primary.connection.designated".
        const val PRIMARY_CONNECTION_DESIGNATED_ROUTING_KEY = "primary.connection.designated"
    }
}

package com.pios.dispatch.persistence

import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.core.TopicExchange
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Dispatch's own consumer-owned RabbitMQ topology (ADR-031; Dispatch
 * Consumer Foundation v1.0): a queue and dead-letter queue Dispatch alone
 * declares, administers, and binds -- never a queue or binding declared
 * by, or shared with, Driver Management.
 *
 * [driverManagementEventsExchange] re-declares Driver Management's own
 * producer-owned exchange by name and identical parameters (durable topic
 * exchange), purely so this module's own queue binding succeeds
 * regardless of which of the two modules' Spring contexts starts first --
 * re-declaring an already-existing exchange with identical parameters is
 * an idempotent no-op in RabbitMQ (the same technique already used by
 * this project's own test-only observer-queue helpers). This does not
 * modify Driver Management's topology: Dispatch never publishes to this
 * exchange, never redeclares it with different parameters, and never
 * administers it beyond its own binding.
 */
@Configuration
class RabbitMQTopologyConfiguration {

    @Bean
    fun driverManagementEventsExchange(): TopicExchange =
        TopicExchange(PRODUCER_EXCHANGE_NAME, true, false)

    @Bean
    fun dispatchFromDriverManagementDeadLetterQueue(): Queue =
        QueueBuilder.durable(DEAD_LETTER_QUEUE_NAME).build()

    /**
     * The main consumer queue. `x-dead-letter-exchange` set to the default
     * exchange (`""`) with `x-dead-letter-routing-key` equal to
     * [DEAD_LETTER_QUEUE_NAME] routes an exhausted-retry message straight
     * into [dispatchFromDriverManagementDeadLetterQueue] -- every queue is
     * implicitly bound to the default exchange under its own name, so no
     * separate DLQ binding bean is needed.
     */
    @Bean
    fun dispatchFromDriverManagementQueue(): Queue =
        QueueBuilder.durable(QUEUE_NAME)
            .withArgument("x-dead-letter-exchange", "")
            .withArgument("x-dead-letter-routing-key", DEAD_LETTER_QUEUE_NAME)
            .build()

    /**
     * Binds only the already-ratified routing key for DriverAvailabilityChanged
     * (verified against Driver Management's actual committed publisher,
     * `RabbitMQEventPublisher`/`DriverAvailabilityApplicationService` at
     * commit 9d7cea9) -- never a wildcard, since this module is a
     * specific, named consumer (INTERFACE_CONTRACTS.md Section 5), not a
     * generic one (unlike Notifications/Analytics, see ADR-033).
     */
    @Bean
    fun dispatchFromDriverManagementBinding(
        dispatchFromDriverManagementQueue: Queue,
        driverManagementEventsExchange: TopicExchange
    ): Binding =
        BindingBuilder.bind(dispatchFromDriverManagementQueue)
            .to(driverManagementEventsExchange)
            .with(DRIVER_AVAILABILITY_CHANGED_ROUTING_KEY)

    companion object {
        const val PRODUCER_EXCHANGE_NAME = "driver-management.events"
        const val QUEUE_NAME = "dispatch.from-driver-management"
        const val DEAD_LETTER_QUEUE_NAME = "dispatch.from-driver-management.dlq"
        const val DRIVER_AVAILABILITY_CHANGED_ROUTING_KEY = "driver.availability.changed"
    }
}

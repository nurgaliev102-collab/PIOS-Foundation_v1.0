package com.pios.drivermanagement.persistence

import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.core.TopicExchange
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Growth Loops TZ v1, Phase 2 extension (docs/PIOS_GROWTH_LOOPS_TZ_V1.md
 * Section 2.1's own disclosed gap, now closed): Driver Management's own
 * consumer-owned RabbitMQ topology for Order Management's `OrderSubmitted`
 * — mirrors Dispatch's own `RabbitMQOrderManagementTopologyConfiguration`
 * exactly (same exchange re-declaration technique, same dead-letter wiring
 * shape), a second, independent consumer of an event Dispatch already
 * consumes for its own, unrelated purpose (First Refusal). This does not
 * modify Order Management's own topology in any way, and shares no queue
 * or binding with Dispatch's own consumer of the same exchange.
 *
 * Named distinctly from [RabbitMQConsumerTopologyConfiguration] (this
 * module's own consumer topology for Dispatch's `dispatch.events`) — one
 * class per producer this module consumes from, the same convention
 * already established across this codebase.
 */
@Configuration
class RabbitMQOrderManagementTopologyConfiguration {

    @Bean
    fun orderManagementEventsExchange(): TopicExchange =
        TopicExchange(PRODUCER_EXCHANGE_NAME, true, false)

    @Bean
    fun driverManagementFromOrderManagementDeadLetterQueue(): Queue =
        QueueBuilder.durable(DEAD_LETTER_QUEUE_NAME).build()

    @Bean
    fun driverManagementFromOrderManagementQueue(): Queue =
        QueueBuilder.durable(QUEUE_NAME)
            .withArgument("x-dead-letter-exchange", "")
            .withArgument("x-dead-letter-routing-key", DEAD_LETTER_QUEUE_NAME)
            .build()

    /** Binds only the already-ratified routing key for OrderSubmitted -- never a wildcard. */
    @Bean
    fun driverManagementFromOrderManagementBinding(
        driverManagementFromOrderManagementQueue: Queue,
        orderManagementEventsExchange: TopicExchange
    ): Binding =
        BindingBuilder.bind(driverManagementFromOrderManagementQueue)
            .to(orderManagementEventsExchange)
            .with(ORDER_SUBMITTED_ROUTING_KEY)

    companion object {
        const val PRODUCER_EXCHANGE_NAME = "order-management.events"
        const val QUEUE_NAME = "driver-management.from-order-management.order-submitted"
        const val DEAD_LETTER_QUEUE_NAME = "driver-management.from-order-management.order-submitted.dlq"
        const val ORDER_SUBMITTED_ROUTING_KEY = "order.submitted"
    }
}

package com.pios.dispatch.persistence

import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.core.TopicExchange
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Dispatch's own consumer-owned RabbitMQ topology for Order Management's
 * events: originally `OrderCancelled` alone (ADR-031; ADR-053, Proposal
 * Resolution on Order Cancellation — Accepted), extended by Task 16
 * (First Refusal Runtime Integration) to also declare a second, dedicated
 * queue for `OrderSubmitted` (ADR-062). Every queue and dead-letter queue
 * this class declares is administered by Dispatch alone — never a queue
 * or binding declared by, or shared with, Order Management.
 *
 * Named distinctly from [RabbitMQTopologyConfiguration] — which declares
 * Dispatch's own consumer topology for Driver Management's events — for
 * the identical reason Order Management's own
 * `RabbitMQConsumerTopologyConfiguration` is named distinctly from its
 * own producer-side `RabbitMQTopologyConfiguration`: one class per
 * producer this module consumes from, never one class conflating two
 * unrelated exchanges.
 *
 * A dedicated queue, not a second binding on [RabbitMQTopologyConfiguration]'s
 * existing queue — ADR-053 Part 4 requires this explicitly, citing
 * ADR-041 Decision item 5's own reasoning: a listener hard-requiring one
 * `eventType` on a shared queue would dead-letter every message of the
 * other kind.
 *
 * [orderManagementEventsExchange] re-declares Order Management's own
 * producer-owned exchange by name and identical parameters (durable topic
 * exchange), purely so this module's own queue binding succeeds
 * regardless of which of the two modules' Spring contexts starts first —
 * the same idempotent-redeclaration technique
 * [RabbitMQTopologyConfiguration.driverManagementEventsExchange] already
 * uses. This does not modify Order Management's topology: Dispatch never
 * publishes to this exchange, never redeclares it with different
 * parameters, and never administers it beyond its own binding.
 */
@Configuration
class RabbitMQOrderManagementTopologyConfiguration {

    @Bean
    fun orderManagementEventsExchange(): TopicExchange =
        TopicExchange(PRODUCER_EXCHANGE_NAME, true, false)

    @Bean
    fun dispatchFromOrderManagementDeadLetterQueue(): Queue =
        QueueBuilder.durable(DEAD_LETTER_QUEUE_NAME).build()

    /**
     * The main consumer queue. `x-dead-letter-exchange` set to the default
     * exchange (`""`) with `x-dead-letter-routing-key` equal to
     * [DEAD_LETTER_QUEUE_NAME] routes an exhausted-retry message straight
     * into [dispatchFromOrderManagementDeadLetterQueue] — every queue is
     * implicitly bound to the default exchange under its own name, so no
     * separate DLQ binding bean is needed.
     */
    @Bean
    fun dispatchFromOrderManagementQueue(): Queue =
        QueueBuilder.durable(QUEUE_NAME)
            .withArgument("x-dead-letter-exchange", "")
            .withArgument("x-dead-letter-routing-key", DEAD_LETTER_QUEUE_NAME)
            .build()

    /**
     * Binds only the already-ratified routing key for OrderCancelled
     * (verified against Order Management's actual committed publisher,
     * `OrderLifecycleApplicationService.outboxRecordFor(OrderCancelled)`:
     * routing key `"order.cancelled"`) — never a wildcard, since this
     * module is a specific, named consumer (ADR-053 Part 4), not a
     * generic one (unlike Notifications/Analytics, see ADR-033).
     */
    @Bean
    fun dispatchFromOrderManagementBinding(
        dispatchFromOrderManagementQueue: Queue,
        orderManagementEventsExchange: TopicExchange
    ): Binding =
        BindingBuilder.bind(dispatchFromOrderManagementQueue)
            .to(orderManagementEventsExchange)
            .with(ORDER_CANCELLED_ROUTING_KEY)

    /**
     * Task 16 (First Refusal Runtime Integration): a **second**, dedicated
     * queue and dead-letter queue for `OrderSubmitted` — not a second
     * binding on [dispatchFromOrderManagementQueue], for the identical
     * reason that queue is itself already dedicated to `OrderCancelled`
     * alone (ADR-053 Part 4's own rule, restated in this class's own KDoc
     * above): [OrderSubmittedFirstRefusalListener] hard-requires
     * `eventType == "OrderSubmitted"`, so sharing a queue with
     * [OrderCancelledListener]'s own hard-required `"OrderCancelled"`
     * would dead-letter every message of whichever kind arrived at the
     * "wrong" listener.
     */
    @Bean
    fun dispatchFromOrderManagementOrderSubmittedDeadLetterQueue(): Queue =
        QueueBuilder.durable(ORDER_SUBMITTED_DEAD_LETTER_QUEUE_NAME).build()

    @Bean
    fun dispatchFromOrderManagementOrderSubmittedQueue(): Queue =
        QueueBuilder.durable(ORDER_SUBMITTED_QUEUE_NAME)
            .withArgument("x-dead-letter-exchange", "")
            .withArgument("x-dead-letter-routing-key", ORDER_SUBMITTED_DEAD_LETTER_QUEUE_NAME)
            .build()

    /**
     * Binds only the already-ratified routing key for OrderSubmitted
     * (verified against Order Management's actual committed publisher,
     * `OrderLifecycleApplicationService.outboxRecordFor(OrderSubmitted)`:
     * routing key `"order.submitted"`, unchanged by Task 15C/16's own
     * additive payload changes) — never a wildcard, for the identical
     * reason [dispatchFromOrderManagementBinding] above is not one.
     */
    @Bean
    fun dispatchFromOrderManagementOrderSubmittedBinding(
        dispatchFromOrderManagementOrderSubmittedQueue: Queue,
        orderManagementEventsExchange: TopicExchange
    ): Binding =
        BindingBuilder.bind(dispatchFromOrderManagementOrderSubmittedQueue)
            .to(orderManagementEventsExchange)
            .with(ORDER_SUBMITTED_ROUTING_KEY)

    companion object {
        const val PRODUCER_EXCHANGE_NAME = "order-management.events"
        const val QUEUE_NAME = "dispatch.from-order-management"
        const val DEAD_LETTER_QUEUE_NAME = "dispatch.from-order-management.dlq"
        const val ORDER_CANCELLED_ROUTING_KEY = "order.cancelled"
        const val ORDER_SUBMITTED_QUEUE_NAME = "dispatch.from-order-management.order-submitted"
        const val ORDER_SUBMITTED_DEAD_LETTER_QUEUE_NAME = "dispatch.from-order-management.order-submitted.dlq"
        const val ORDER_SUBMITTED_ROUTING_KEY = "order.submitted"
    }
}

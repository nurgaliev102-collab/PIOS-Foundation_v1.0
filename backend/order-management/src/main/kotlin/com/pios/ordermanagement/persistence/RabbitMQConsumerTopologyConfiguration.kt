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
 *
 * ADR-041 (Order Lifecycle Synchronization with Assignment Completion)
 * adds a *second*, entirely independent queue/DLQ/binding for
 * `assignment.completed` -- [assignmentCompletedQueue],
 * [assignmentCompletedDeadLetterQueue], [assignmentCompletedBinding] --
 * rather than a second binding on the existing
 * [orderManagementFromDispatchQueue]. ADR-041 Decision item 5 explains
 * why: [AssignmentAcceptedListener.onMessage] hard-requires
 * `eventType == "AssignmentAccepted"` and throws on anything else, so
 * sharing the queue would dead-letter every AssignmentCompleted message
 * until that listener were rewritten into a type dispatcher -- a larger,
 * unrelated change to a working, already-verified path. The two
 * subscriptions stay independently observable and independently
 * drainable as a result.
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

    /** ADR-041's own dead-letter queue for AssignmentCompleted -- see this class's own KDoc. */
    @Bean
    fun assignmentCompletedDeadLetterQueue(): Queue =
        QueueBuilder.durable(ASSIGNMENT_COMPLETED_DEAD_LETTER_QUEUE_NAME).build()

    /**
     * ADR-041's own consumer queue for AssignmentCompleted, independent of
     * [orderManagementFromDispatchQueue] -- see this class's own KDoc for
     * why. Same dead-letter wiring shape as the existing queue.
     */
    @Bean
    fun assignmentCompletedQueue(): Queue =
        QueueBuilder.durable(ASSIGNMENT_COMPLETED_QUEUE_NAME)
            .withArgument("x-dead-letter-exchange", "")
            .withArgument("x-dead-letter-routing-key", ASSIGNMENT_COMPLETED_DEAD_LETTER_QUEUE_NAME)
            .build()

    /** Binds only the already-ratified routing key for AssignmentCompleted (ADR-041) -- never a wildcard. */
    @Bean
    fun assignmentCompletedBinding(
        assignmentCompletedQueue: Queue,
        dispatchEventsExchange: TopicExchange
    ): Binding =
        BindingBuilder.bind(assignmentCompletedQueue)
            .to(dispatchEventsExchange)
            .with(ASSIGNMENT_COMPLETED_ROUTING_KEY)

    @Bean
    fun dispatchExhaustedDeadLetterQueue(): Queue =
        QueueBuilder.durable(DISPATCH_EXHAUSTED_DEAD_LETTER_QUEUE_NAME).build()

    @Bean
    fun dispatchExhaustedQueue(): Queue =
        QueueBuilder.durable(DISPATCH_EXHAUSTED_QUEUE_NAME)
            .withArgument("x-dead-letter-exchange", "")
            .withArgument("x-dead-letter-routing-key", DISPATCH_EXHAUSTED_DEAD_LETTER_QUEUE_NAME)
            .build()

    @Bean
    fun dispatchExhaustedBinding(
        dispatchExhaustedQueue: Queue,
        dispatchEventsExchange: TopicExchange
    ): Binding = BindingBuilder.bind(dispatchExhaustedQueue)
        .to(dispatchEventsExchange)
        .with(DISPATCH_EXHAUSTED_ROUTING_KEY)

    @Bean
    fun cancellationResolvedDeadLetterQueue(): Queue =
        QueueBuilder.durable(CANCELLATION_RESOLVED_DEAD_LETTER_QUEUE_NAME).build()

    @Bean
    fun cancellationResolvedQueue(): Queue = QueueBuilder.durable(CANCELLATION_RESOLVED_QUEUE_NAME)
        .withArgument("x-dead-letter-exchange", "")
        .withArgument("x-dead-letter-routing-key", CANCELLATION_RESOLVED_DEAD_LETTER_QUEUE_NAME)
        .build()

    @Bean
    fun cancellationResolvedBinding(cancellationResolvedQueue: Queue, dispatchEventsExchange: TopicExchange): Binding =
        BindingBuilder.bind(cancellationResolvedQueue).to(dispatchEventsExchange)
            .with(CANCELLATION_RESOLVED_ROUTING_KEY)

    @Bean
    fun commitmentTerminatedDeadLetterQueue(): Queue =
        QueueBuilder.durable(COMMITMENT_TERMINATED_DEAD_LETTER_QUEUE_NAME).build()

    @Bean
    fun commitmentTerminatedQueue(): Queue = QueueBuilder.durable(COMMITMENT_TERMINATED_QUEUE_NAME)
        .withArgument("x-dead-letter-exchange", "")
        .withArgument("x-dead-letter-routing-key", COMMITMENT_TERMINATED_DEAD_LETTER_QUEUE_NAME)
        .build()

    @Bean
    fun commitmentTerminatedBinding(commitmentTerminatedQueue: Queue, dispatchEventsExchange: TopicExchange): Binding =
        BindingBuilder.bind(commitmentTerminatedQueue).to(dispatchEventsExchange)
            .with(COMMITMENT_TERMINATED_ROUTING_KEY)

    companion object {
        const val PRODUCER_EXCHANGE_NAME = "dispatch.events"
        const val QUEUE_NAME = "order-management.from-dispatch"
        const val DEAD_LETTER_QUEUE_NAME = "order-management.from-dispatch.dlq"
        const val ASSIGNMENT_ACCEPTED_ROUTING_KEY = "assignment.accepted"
        const val ASSIGNMENT_COMPLETED_QUEUE_NAME = "order-management.from-dispatch.assignment-completed"
        const val ASSIGNMENT_COMPLETED_DEAD_LETTER_QUEUE_NAME = "order-management.from-dispatch.assignment-completed.dlq"
        const val ASSIGNMENT_COMPLETED_ROUTING_KEY = "assignment.completed"
        const val DISPATCH_EXHAUSTED_QUEUE_NAME = "order-management.from-dispatch.exhausted"
        const val DISPATCH_EXHAUSTED_DEAD_LETTER_QUEUE_NAME = "order-management.from-dispatch.exhausted.dlq"
        const val DISPATCH_EXHAUSTED_ROUTING_KEY = "dispatch.exhausted"
        const val CANCELLATION_RESOLVED_QUEUE_NAME = "order-management.from-dispatch.cancellation-resolved"
        const val CANCELLATION_RESOLVED_DEAD_LETTER_QUEUE_NAME = "order-management.from-dispatch.cancellation-resolved.dlq"
        const val CANCELLATION_RESOLVED_ROUTING_KEY = "order.cancellation-resolved"
        const val COMMITMENT_TERMINATED_QUEUE_NAME = "order-management.from-dispatch.commitment-terminated"
        const val COMMITMENT_TERMINATED_DEAD_LETTER_QUEUE_NAME = "order-management.from-dispatch.commitment-terminated.dlq"
        const val COMMITMENT_TERMINATED_ROUTING_KEY = "commitment.terminated"
    }
}

package com.pios.dispatch.persistence

import org.springframework.amqp.core.TopicExchange
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Declares Dispatch's own producer-owned exchange on startup (ADR-031;
 * Tranche 1: Dispatch Event Publishing Completion): one durable topic
 * exchange, owned and administered exclusively by this module — never a
 * shared or platform-wide exchange. Mirrors Order Management's and Driver
 * Management's own already-proven producer topology exactly.
 *
 * Named distinctly from the existing [RabbitMQTopologyConfiguration] —
 * which declares Dispatch's own *consumer*-side topology (its queue,
 * dead-letter queue, and binding to Driver Management's exchange) — so
 * that this module's own new producer role and its already-existing
 * consumer role remain two separate, independently-readable
 * configuration classes rather than one class conflating both.
 *
 * No queue is declared here. Per ADR-031, queues belong to consumers:
 * Order Management declares and binds its own queue to this exchange
 * independently, out of this module's scope.
 */
@Configuration
class RabbitMQProducerTopologyConfiguration {

    @Bean
    fun dispatchEventsExchange(): TopicExchange =
        TopicExchange(RabbitMQEventPublisher.EXCHANGE_NAME, true, false)
}

package com.pios.ordermanagement.persistence

import org.springframework.amqp.core.TopicExchange
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Declares Order Management's own exchange on startup (ADR-031): one
 * durable topic exchange, owned and administered exclusively by this
 * module — never a shared or platform-wide exchange. Spring Boot's
 * autoconfigured `RabbitAdmin` declares any [TopicExchange] bean found in
 * the context automatically.
 *
 * No queue is declared here. Per ADR-031, queues belong to consumers:
 * each consuming module declares and binds its own queue to this
 * exchange independently — out of this task's explicit scope (RabbitMQ
 * Event Publishing Foundation v1.0 implements the producer side only).
 */
@Configuration
class RabbitMQTopologyConfiguration {

    @Bean
    fun orderManagementEventsExchange(): TopicExchange =
        TopicExchange(RabbitMQEventPublisher.EXCHANGE_NAME, true, false)
}

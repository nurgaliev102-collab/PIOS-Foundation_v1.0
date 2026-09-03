package com.pios.passengerexperience.persistence

import org.springframework.amqp.core.TopicExchange
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Declares Passenger Experience's own producer-owned exchange on startup
 * (Task 14, First Refusal Foundation; ADR-062): one durable topic
 * exchange, owned and administered exclusively by this module — never a
 * shared or platform-wide exchange. Mirrors Dispatch's, Order
 * Management's, and Driver Management's own already-proven producer
 * topology exactly.
 *
 * No queue is declared here. Per this codebase's own established
 * convention (ADR-031), queues belong to consumers: Dispatch declares and
 * binds its own queue to this exchange independently
 * ([com.pios.dispatch.persistence.RabbitMQPassengerExperienceTopologyConfiguration]),
 * out of this module's scope.
 */
@Configuration
class RabbitMQProducerTopologyConfiguration {

    @Bean
    fun passengerExperienceEventsExchange(): TopicExchange =
        TopicExchange(RabbitMQEventPublisher.EXCHANGE_NAME, true, false)
}

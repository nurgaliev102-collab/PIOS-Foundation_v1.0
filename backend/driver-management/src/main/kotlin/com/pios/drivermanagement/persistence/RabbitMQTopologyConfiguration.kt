package com.pios.drivermanagement.persistence

import org.springframework.amqp.core.TopicExchange
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Declares only Driver Management's own producer-owned exchange (ADR-031):
 * a durable topic exchange named [RabbitMQEventPublisher.EXCHANGE_NAME].
 * No queue, binding, or DLQ is declared here — queue ownership belongs
 * exclusively to consuming modules (ADR-031, Queue Ownership), none of
 * which is implemented by this task. Consumers (for example, a future
 * Dispatch consumer of DriverAvailabilityChanged) declare and own their
 * own queue bound to this exchange, entirely outside this module.
 */
@Configuration
class RabbitMQTopologyConfiguration {

    @Bean
    fun driverManagementEventsExchange(): TopicExchange =
        TopicExchange(RabbitMQEventPublisher.EXCHANGE_NAME, true, false)
}

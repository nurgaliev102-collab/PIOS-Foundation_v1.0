package com.pios.drivermanagement.persistence

import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Growth Loops TZ v1, Phase 2 (docs/PIOS_GROWTH_LOOPS_TZ_V1.md Section 2):
 * bounded retry-then-dead-letter policy (ADR-031) for this module's own new
 * consumer, mirroring Order Management's own
 * `RabbitMQListenerContainerConfiguration` exactly -- same attempt count
 * and backoff, same recoverer. Overrides Spring Boot's auto-configured
 * `rabbitListenerContainerFactory` bean by declaring one under the same
 * name.
 */
@Configuration
class RabbitMQListenerContainerConfiguration {

    @Bean
    fun rabbitListenerContainerFactory(connectionFactory: ConnectionFactory): SimpleRabbitListenerContainerFactory {
        val factory = SimpleRabbitListenerContainerFactory()
        factory.setConnectionFactory(connectionFactory)
        factory.setAdviceChain(
            RetryInterceptorBuilder.stateless()
                .maxAttempts(MAX_ATTEMPTS)
                .backOffOptions(INITIAL_BACKOFF_MILLIS, BACKOFF_MULTIPLIER, MAX_BACKOFF_MILLIS)
                .recoverer(LoggingRejectAndDontRequeueRecoverer())
                .build()
        )
        return factory
    }

    companion object {
        const val MAX_ATTEMPTS = 3
        const val INITIAL_BACKOFF_MILLIS = 200L
        const val BACKOFF_MULTIPLIER = 2.0
        const val MAX_BACKOFF_MILLIS = 2000L
    }
}

package com.pios.dispatch.persistence

import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Bounded retry-then-dead-letter policy (ADR-031) for this module's own
 * RabbitMQ listener container, using Spring AMQP's own
 * [RetryInterceptorBuilder] -- not a custom-built retry mechanism.
 * [MAX_ATTEMPTS] attempts, with exponential backoff between
 * [INITIAL_BACKOFF_MILLIS] and [MAX_BACKOFF_MILLIS]; once exhausted,
 * [LoggingRejectAndDontRequeueRecoverer] rejects the message without
 * requeue, which -- given [RabbitMQTopologyConfiguration]'s own
 * dead-letter arguments on the main queue -- routes it to Dispatch's own
 * dead-letter queue rather than discarding or retrying it forever.
 *
 * Overrides Spring Boot's auto-configured `rabbitListenerContainerFactory`
 * bean by declaring one under the same name, exactly the mechanism Spring
 * Boot itself expects for this kind of customization.
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

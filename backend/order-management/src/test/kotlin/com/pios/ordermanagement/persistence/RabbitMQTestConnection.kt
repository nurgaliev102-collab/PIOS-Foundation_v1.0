package com.pios.ordermanagement.persistence

import org.springframework.amqp.rabbit.connection.CachingConnectionFactory
import org.springframework.amqp.rabbit.connection.ConnectionFactory

/**
 * Test-only access to the real, locally-running RabbitMQ broker (RabbitMQ
 * Event Publishing Foundation v1.0), matching `application.yml`'s own
 * connection details. Built once per test JVM ([by lazy]).
 */
internal object RabbitMQTestConnection {
    val connectionFactory: ConnectionFactory by lazy {
        CachingConnectionFactory("127.0.0.1", 5672).apply {
            username = "guest"
            setPassword("guest")
            // Matches application.yml's spring.rabbitmq.publisher-confirm-type,
            // which Spring Boot's autoconfiguration applies automatically in a
            // real app; this test-only connection factory is built by hand, so
            // it must be set explicitly for RabbitMQEventPublisher's
            // waitForConfirmsOrDie to have confirms to wait for at all.
            setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED)
        }
    }
}

package com.pios.dispatch.persistence

import org.springframework.amqp.rabbit.connection.CachingConnectionFactory
import org.springframework.amqp.rabbit.connection.ConnectionFactory

/**
 * Test-only access to the real, locally-running RabbitMQ broker (Dispatch
 * Consumer Foundation v1.0), matching `application.yml`'s own connection
 * details. Built once per test JVM ([by lazy]).
 */
internal object RabbitMQTestConnection {
    val connectionFactory: ConnectionFactory by lazy {
        CachingConnectionFactory("127.0.0.1", 5672).apply {
            username = "guest"
            setPassword("guest")
            setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED)
        }
    }
}

package com.pios.passengerexperience.persistence

import org.springframework.amqp.rabbit.connection.CachingConnectionFactory
import org.springframework.amqp.rabbit.connection.ConnectionFactory

/**
 * Test-only access to a dedicated, isolated RabbitMQ **test** virtual host
 * (`pios-test`) on the same broker instance — deliberately never the
 * default vhost (`/`) that `application.yml`'s own `spring.rabbitmq.*`
 * connects to, since `/` is production's own vhost. Mirrors
 * `com.pios.dispatch.persistence.RabbitMQTestConnection`'s own KDoc and
 * reasoning exactly (Task 14, First Refusal Foundation — this module's
 * first-ever RabbitMQ-using test). See `docs/RABBITMQ_TEST_ISOLATION.md`
 * for the convention every RabbitMQ-using module follows. Host, port,
 * vhost, username, and password are all hardcoded here, deliberately not
 * configurable via any environment variable or system property, so there
 * is no override path back to production.
 */
internal object RabbitMQTestConnection {
    private const val TEST_VIRTUAL_HOST = "pios-test"

    val connectionFactory: ConnectionFactory by lazy {
        check(TEST_VIRTUAL_HOST.isNotBlank() && TEST_VIRTUAL_HOST != "/") {
            "Refusing to build a RabbitMQ test connection: TEST_VIRTUAL_HOST " +
                "('$TEST_VIRTUAL_HOST') is not a valid, explicitly-isolated test " +
                "vhost. This must never resolve to '/' (production's own vhost)."
        }
        CachingConnectionFactory("127.0.0.1", 5672).apply {
            virtualHost = TEST_VIRTUAL_HOST
            username = "pios_test"
            setPassword("u2cZAscL4EP4dCwAFHSeDeP2elucyROf")
            setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED)
        }
    }
}

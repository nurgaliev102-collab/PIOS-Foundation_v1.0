package com.pios.drivermanagement.persistence

import org.springframework.amqp.rabbit.connection.CachingConnectionFactory
import org.springframework.amqp.rabbit.connection.ConnectionFactory

/**
 * Test-only access to a dedicated, isolated RabbitMQ **test** virtual host
 * (`pios-test`) on the same broker instance — deliberately never the
 * default vhost (`/`) that `application.yml`'s own `spring.rabbitmq.*`
 * connects to, since `/` is production's own vhost, shared by three live
 * WinSW backend services. Built once per test JVM ([by lazy]).
 *
 * Test/production RabbitMQ isolation incident (2026-09-02,
 * `docs/RABBITMQ_TEST_ISOLATION.md`): this object used to connect with the
 * exact same host/port/username/password as production
 * (`127.0.0.1:5672`, `guest`/`guest`, implicitly the default vhost `/`),
 * and every test topology class re-declared production's own exchange
 * names by importing their constants directly. A real message published by
 * `order-management`'s own integration tests onto the real, shared
 * `dispatch.events` exchange raced production's own live consumer for it,
 * causing observable test failures. This connects instead to a distinct
 * RabbitMQ vhost — a broker-enforced namespace boundary, not a naming
 * convention: a connection authenticates to exactly one vhost, and
 * declaring, binding, publishing to, purging, or consuming from any
 * exchange/queue is scoped to that vhost only, so identically-named test
 * topology (kept identical deliberately, so tests exercise the real
 * production topology shape) can never collide with production's own
 * `/`-vhost objects. The dedicated `pios_test` user has no permissions
 * granted on `/` at all — RabbitMQ refuses the connection outright rather
 * than falling back to it. Host/port/vhost/username/password are all
 * hardcoded here, exactly as `PostgreSQLTestDatabase`'s own fix does for
 * the equivalent PostgreSQL incident — deliberately not configurable via
 * any environment variable or system property, so there is no override
 * path back to production. See `docs/RABBITMQ_TEST_ISOLATION.md` for the
 * convention every RabbitMQ-using module follows, and for how to
 * provision `pios-test`/`pios_test` on a new machine.
 */
internal object RabbitMQTestConnection {
    private const val TEST_VIRTUAL_HOST = "pios-test"

    val connectionFactory: ConnectionFactory by lazy {
        // Fail closed: this assertion only ever fires if a future edit
        // changes [TEST_VIRTUAL_HOST] back to "/" (or blank) by mistake --
        // the one silent-fallback shape this fix exists to make
        // structurally impossible. Not a runtime broker check (the broker
        // itself already refuses `pios_test` on any vhost but
        // `pios-test`) -- this is the code-level half of that guarantee.
        check(TEST_VIRTUAL_HOST.isNotBlank() && TEST_VIRTUAL_HOST != "/") {
            "Refusing to build a RabbitMQ test connection: TEST_VIRTUAL_HOST " +
                "('$TEST_VIRTUAL_HOST') is not a valid, explicitly-isolated test " +
                "vhost. This must never resolve to '/' (production's own vhost)."
        }
        CachingConnectionFactory("127.0.0.1", 5672).apply {
            virtualHost = TEST_VIRTUAL_HOST
            username = "pios_test"
            setPassword("u2cZAscL4EP4dCwAFHSeDeP2elucyROf")
            // Matches application.yml's spring.rabbitmq.publisher-confirm-type,
            // which Spring Boot's autoconfiguration applies automatically in a
            // real app; this test-only connection factory is built by hand, so
            // it must be set explicitly for RabbitMQEventPublisher's
            // waitForConfirmsOrDie to have confirms to wait for at all.
            setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED)
        }
    }
}

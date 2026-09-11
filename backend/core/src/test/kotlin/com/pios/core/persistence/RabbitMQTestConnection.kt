package com.pios.core.persistence

import com.pios.core.qa.CoreQaEndpoints
import org.springframework.amqp.rabbit.connection.ConnectionFactory

/**
 * Test-only access to Core's QA RabbitMQ namespace.
 *
 * The connection target comes from [CoreQaEndpoints.rabbitmqConnectionFactory],
 * which runs the fail-closed [com.pios.core.qa.CoreQaSafetyGate] first:
 * building this factory throws [com.pios.core.qa.QaSafetyViolation] if the
 * resolved vhost is `/` (or blank), the user is `guest`, or the host is a
 * production host — before any connection is attempted (ADR-067 QA /
 * Production Gate item 4).
 *
 * The **default** QA namespace is the ratified `pios-test` vhost +
 * `pios_test` user (`docs/RABBITMQ_TEST_ISOLATION.md`). `pios_test` has
 * **no permission grant on `/`**, so a connection as this user to `/` is
 * `ACCESS_REFUSED` by the broker itself — the isolation is
 * broker-enforced, not a naming convention. Override per
 * `-Dpios.core.qa.rabbitmq.*` / `PIOS_CORE_QA_RABBITMQ_*` if a different
 * QA broker/vhost is used.
 */
internal object RabbitMQTestConnection {
    val connectionFactory: ConnectionFactory by lazy { CoreQaEndpoints.rabbitmqConnectionFactory() }
}

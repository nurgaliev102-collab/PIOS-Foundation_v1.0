package com.pios.core.qa

import org.springframework.amqp.rabbit.connection.CachingConnectionFactory
import org.springframework.amqp.rabbit.connection.ConnectionFactory

/**
 * The single source of the QA PostgreSQL / QA RabbitMQ endpoints every
 * Core integration test uses. Every accessor runs [CoreQaSafetyGate]
 * **before returning** — so no integration test can obtain a connection
 * target that could reach production (ADR-067 QA / Production Gate item
 * 4). A missing, ambiguous, or production-valued configuration throws
 * [QaSafetyViolation] here, before any socket is opened.
 *
 * Configuration is test-only, supplied through **system properties**
 * (`-Dpios.core.qa.*`, forwarded to the test JVM by `build.gradle.kts`)
 * or the matching `PIOS_CORE_QA_*` environment variables. It is **not**
 * read from `application.yml` or `application-test.yml` — the isolation
 * conventions (`docs/TEST_DATABASE_ISOLATION.md`,
 * `docs/RABBITMQ_TEST_ISOLATION.md`) forbid a YAML override precisely
 * because it reintroduces a silent path to production; the Safety Gate
 * answers that concern directly by making every production value invalid.
 *
 * **PostgreSQL has no safe default** — there is no isolated QA PostgreSQL
 * instance until the operator provisions one (a distinct cluster on a
 * distinct port; see `docs/PIOS_CORE_SLICE_01_QA_ENVIRONMENT_PLAN.md`).
 * Absent configuration = FAIL.
 *
 * **RabbitMQ has a safe default** — the ratified `pios-test` vhost +
 * `pios_test` user (`docs/RABBITMQ_TEST_ISOLATION.md`), whose user holds
 * zero permission on `/` and is therefore broker-refused from production.
 * The default is still run through the Gate.
 */
object CoreQaEndpoints {

    data class Postgres(val url: String, val username: String, val password: String)

    data class RabbitMq(
        val host: String,
        val port: Int,
        val virtualHost: String,
        val username: String,
        val password: String
    )

    private fun configValue(systemProperty: String, envVar: String): String? =
        System.getProperty(systemProperty)?.takeIf { it.isNotBlank() }
            ?: System.getenv(envVar)?.takeIf { it.isNotBlank() }

    /** QA PostgreSQL. No production-valid default; absent configuration fails at the Gate. */
    fun postgres(): Postgres {
        val url = configValue("pios.core.qa.postgres.url", "PIOS_CORE_QA_POSTGRES_URL")
        val username = configValue("pios.core.qa.postgres.username", "PIOS_CORE_QA_POSTGRES_USERNAME")
        // password read without the isNotBlank filter — an empty value must
        // still reach the Gate, which rejects it explicitly.
        val password = System.getProperty("pios.core.qa.postgres.password")
            ?: System.getenv("PIOS_CORE_QA_POSTGRES_PASSWORD")

        CoreQaSafetyGate.validatePostgres(url, username, password)
        return Postgres(url!!, username!!, password!!)
    }

    /**
     * QA RabbitMQ. Defaults to the ratified `pios-test` / `pios_test`
     * namespace (broker-refused from `/`); overridable per system
     * property / env var. The Gate runs on whatever is resolved.
     */
    fun rabbitmq(): RabbitMq {
        val host = configValue("pios.core.qa.rabbitmq.host", "PIOS_CORE_QA_RABBITMQ_HOST") ?: DEFAULT_RABBITMQ_HOST
        val port = (configValue("pios.core.qa.rabbitmq.port", "PIOS_CORE_QA_RABBITMQ_PORT") ?: DEFAULT_RABBITMQ_PORT.toString()).toIntOrNull()
        val virtualHost = configValue("pios.core.qa.rabbitmq.vhost", "PIOS_CORE_QA_RABBITMQ_VHOST") ?: DEFAULT_RABBITMQ_VHOST
        val username = configValue("pios.core.qa.rabbitmq.username", "PIOS_CORE_QA_RABBITMQ_USERNAME") ?: DEFAULT_RABBITMQ_USER
        val password = System.getProperty("pios.core.qa.rabbitmq.password")
            ?: System.getenv("PIOS_CORE_QA_RABBITMQ_PASSWORD")
            ?: DEFAULT_RABBITMQ_PASSWORD

        CoreQaSafetyGate.validateRabbitMq(host, port, virtualHost, username)
        return RabbitMq(host, port!!, virtualHost, username, password)
    }

    /** Convenience: a validated [ConnectionFactory] for the QA RabbitMQ namespace. */
    fun rabbitmqConnectionFactory(): ConnectionFactory {
        val qa = rabbitmq()
        return CachingConnectionFactory(qa.host, qa.port).apply {
            virtualHost = qa.virtualHost
            username = qa.username
            setPassword(qa.password)
            setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED)
        }
    }

    // The ratified QA RabbitMQ namespace — docs/RABBITMQ_TEST_ISOLATION.md.
    // `pios_test` has no permission grant on `/` (verified), so a connection
    // as this user to `/` is ACCESS_REFUSED by the broker itself.
    private const val DEFAULT_RABBITMQ_HOST = "127.0.0.1"
    private const val DEFAULT_RABBITMQ_PORT = 5672
    private const val DEFAULT_RABBITMQ_VHOST = "pios-test"
    private const val DEFAULT_RABBITMQ_USER = "pios_test"
    private const val DEFAULT_RABBITMQ_PASSWORD = "u2cZAscL4EP4dCwAFHSeDeP2elucyROf"
}

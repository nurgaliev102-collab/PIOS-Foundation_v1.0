package com.pios.core

import com.pios.core.persistence.PostgreSQLTestDatabase
import com.pios.core.qa.CoreQaEndpoints
import org.springframework.boot.SpringApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Module startup / context test. Boots the real [CoreApplication] Spring
 * context against the QA PostgreSQL database and QA RabbitMQ namespace,
 * and asserts every Slice 01 bean is wired — the three listeners, the
 * projection service, both repositories, the transaction runner, both
 * controllers.
 *
 * The Spring property overrides come from [CoreQaEndpoints], so this test
 * fails closed exactly like every other integration test: without a valid
 * QA configuration it throws [com.pios.core.qa.QaSafetyViolation] at
 * `CoreQaEndpoints.postgres()` / `.rabbitmq()`, before the context boots
 * and before any connection is opened (ADR-067 QA / Production Gate item
 * 4). It never resolves to `pios_core`, a production database name, the
 * `/` RabbitMQ vhost, or `postgres`/`guest`.
 *
 * The overrides are applied as JVM system properties, not via
 * `SpringApplicationBuilder.properties(...)`. Spring Boot's own property
 * source precedence puts `SpringApplicationBuilder.properties()` (its
 * `defaultProperties` source) BELOW `application.yml` — so that call would
 * silently lose to `application.yml`'s production-shaped
 * `spring.datasource.url: jdbc:postgresql://127.0.0.1:5432/pios_core`
 * instead of overriding it. System properties outrank `application.yml`,
 * so this is the only mechanism that actually lets the Gate-validated
 * values win.
 */
class CoreApplicationContextTest {

    @Test
    fun `the Core context starts and every Slice 01 bean is present`() {
        // fail-closed Safety Gate runs here (both accessors) before anything connects
        val pg = CoreQaEndpoints.postgres()
        val mq = CoreQaEndpoints.rabbitmq()

        // Force Flyway to have run the schema on the QA test DB first.
        PostgreSQLTestDatabase.dataSource

        val overrides = mapOf(
            "spring.datasource.url" to pg.url,
            "spring.datasource.username" to pg.username,
            "spring.datasource.password" to pg.password,
            "spring.flyway.locations" to "classpath:db/migration/core",
            "spring.rabbitmq.host" to mq.host,
            "spring.rabbitmq.port" to mq.port.toString(),
            "spring.rabbitmq.virtual-host" to mq.virtualHost,
            "spring.rabbitmq.username" to mq.username,
            "spring.rabbitmq.password" to mq.password,
            "server.port" to "0"
        )
        val previous = overrides.keys.associateWith { System.getProperty(it) }
        overrides.forEach { (key, value) -> System.setProperty(key, value) }

        val context = try {
            SpringApplicationBuilder(CoreApplication::class.java).run()
        } finally {
            previous.forEach { (key, value) ->
                if (value == null) System.clearProperty(key) else System.setProperty(key, value)
            }
        }

        try {
            listOf(
                "com.pios.core.persistence.OrderEventListener",
                "com.pios.core.persistence.AssignmentCompletedListener",
                "com.pios.core.persistence.PrimaryConnectionDesignatedListener",
                "com.pios.core.application.ParticipantHistoryProjectionApplicationService",
                "com.pios.core.application.ParticipantHistoryQueryService",
                "com.pios.core.persistence.PostgreSQLParticipantHistoryRepository",
                "com.pios.core.persistence.PostgreSQLProcessedEventRepository",
                "com.pios.core.persistence.SpringTransactionRunner",
                "com.pios.core.api.ParticipantHistoryController",
                "com.pios.core.api.HealthController"
            ).forEach { beanClass ->
                assertTrue(
                    context.getBeansOfType(Class.forName(beanClass)).isNotEmpty(),
                    "expected a $beanClass bean in the Core context"
                )
            }
        } finally {
            SpringApplication.exit(context)
        }
    }
}

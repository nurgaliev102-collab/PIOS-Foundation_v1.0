package com.pios.core.persistence

import com.pios.core.qa.CoreQaEndpoints
import org.flywaydb.core.Flyway
import org.springframework.jdbc.datasource.DriverManagerDataSource
import javax.sql.DataSource

/**
 * Test-only access to Core's QA PostgreSQL database (`pios_core_test`).
 *
 * The connection target is **not hard-coded** and **not read from
 * `application-test.yml`**. It comes from [CoreQaEndpoints.postgres],
 * which runs the fail-closed [com.pios.core.qa.CoreQaSafetyGate] first:
 * the initializer below throws [com.pios.core.qa.QaSafetyViolation]
 * before any connection is attempted if the QA configuration is absent,
 * ambiguous, or resolves to a production PostgreSQL endpoint, a
 * production database name, the `postgres` superuser, or an empty
 * password (ADR-067 QA / Production Gate item 4).
 *
 * Schema is applied by the same Flyway migrations the application runs on
 * startup (`classpath:db/migration/core`).
 *
 * To run these tests, define the QA PostgreSQL endpoint (a distinct
 * cluster on a distinct port, never `127.0.0.1:5432`):
 *
 * ```
 * ./gradlew :core:test \
 *   -Dpios.core.qa.postgres.url=jdbc:postgresql://127.0.0.1:5433/pios_core_test \
 *   -Dpios.core.qa.postgres.username=pios_core_qa \
 *   -Dpios.core.qa.postgres.password=<qa-role-password>
 * ```
 *
 * See `docs/PIOS_CORE_SLICE_01_QA_ENVIRONMENT_PLAN.md` for provisioning.
 */
internal object PostgreSQLTestDatabase {
    val dataSource: DataSource by lazy {
        val qa = CoreQaEndpoints.postgres() // fail-closed Safety Gate runs here, before any connection
        val dataSource = DriverManagerDataSource().apply {
            setDriverClassName("org.postgresql.Driver")
            url = qa.url
            username = qa.username
            password = qa.password
        }
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration/core")
            .load()
            .migrate()
        dataSource
    }
}

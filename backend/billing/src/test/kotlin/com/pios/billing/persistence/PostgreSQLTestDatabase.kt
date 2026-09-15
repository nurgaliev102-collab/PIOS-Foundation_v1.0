package com.pios.billing.persistence

import org.flywaydb.core.Flyway
import org.springframework.jdbc.datasource.DriverManagerDataSource
import javax.sql.DataSource

/**
 * Test-only access to Billing's own PostgreSQL **test** database
 * (`pios_billing_test`) — deliberately never `pios_billing` itself (that
 * name is production's own database on this shared local Postgres
 * instance). Schema-managed exclusively through the same Flyway
 * migrations the application itself runs on startup
 * (`src/main/resources/db/migration`) — never a manually-applied SQL
 * file, so the schema tests run against is reproducible from a clean
 * database in one deterministic step, an exact structural match for
 * production without ever being the same database.
 *
 * Follows `docs/TEST_DATABASE_ISOLATION.md`'s own convention for any new
 * module exactly: this file is a copy of
 * `com.pios.drivermanagement.persistence.PostgreSQLTestDatabase`, package
 * line and database/migration names only changed. The URL is hardcoded,
 * deliberately not configurable via any environment variable or system
 * property, precisely so there is no override path back to a
 * production-named database (2026-09-02 incident, `PIOS_REALITY_AUDIT.md`
 * Section 19).
 *
 * [dataSource] is built once per test JVM ([by lazy]) and migrated
 * immediately; Flyway's own applied-migrations tracking makes repeated
 * calls across test runs a safe no-op.
 *
 * Flyway's `locations` is pinned to this module's own migration
 * subdirectory (matching `spring.flyway.locations` in application.yml),
 * not the shared `classpath:db/migration` default.
 */
internal object PostgreSQLTestDatabase {
    val dataSource: DataSource by lazy {
        val dataSource = DriverManagerDataSource().apply {
            setDriverClassName("org.postgresql.Driver")
            url = "jdbc:postgresql://127.0.0.1:5432/pios_billing_test"
            username = "postgres"
            password = ""
        }
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration/billing")
            .load()
            .migrate()
        dataSource
    }
}

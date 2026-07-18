package com.pios.drivermanagement.persistence

import org.flywaydb.core.Flyway
import org.springframework.jdbc.datasource.DriverManagerDataSource
import javax.sql.DataSource

/**
 * Test-only access to Driver Management's real PostgreSQL database
 * (`pios_driver_management`), schema-managed exclusively through the
 * same Flyway migrations the application itself runs on startup
 * (`src/main/resources/db/migration`) — never a manually-applied SQL
 * file, so the schema tests run against is reproducible from a clean
 * database in one deterministic step, identical to production.
 *
 * [dataSource] is built once per test JVM ([by lazy]) and migrated
 * immediately; Flyway's own applied-migrations tracking makes repeated
 * calls across test runs a safe no-op.
 *
 * Flyway's `locations` is pinned to this module's own migration
 * subdirectory (matching `spring.flyway.locations` in application.yml),
 * not the shared `classpath:db/migration` default. Other modules'
 * test-scoped dependencies on this one (e.g. driver-management ->
 * dispatch contract tests) can put this module's migration file on
 * another module's test classpath too; without pinning, Flyway would see
 * more than one module's "V1__initial_schema.sql" at the same logical
 * path and refuse to proceed.
 */
internal object PostgreSQLTestDatabase {
    val dataSource: DataSource by lazy {
        val dataSource = DriverManagerDataSource().apply {
            setDriverClassName("org.postgresql.Driver")
            url = "jdbc:postgresql://127.0.0.1:5432/pios_driver_management"
            username = "postgres"
            password = ""
        }
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration/drivermanagement")
            .load()
            .migrate()
        dataSource
    }
}

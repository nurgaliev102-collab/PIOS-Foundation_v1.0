package com.pios.identity.persistence

import org.flywaydb.core.Flyway
import org.springframework.jdbc.datasource.DriverManagerDataSource
import javax.sql.DataSource

/**
 * Test-only access to Identity's real PostgreSQL database (`pios_identity`),
 * schema-managed exclusively through the same Flyway migrations the
 * application itself runs on startup (`src/main/resources/db/migration/identity`)
 * — never a manually-applied SQL file, so the schema tests run against is
 * reproducible from a clean database in one deterministic step, identical
 * to production. Mirrors `com.pios.dispatch.persistence.PostgreSQLTestDatabase`'s
 * own shape exactly.
 *
 * [dataSource] is built once per test JVM ([by lazy]) and migrated
 * immediately; Flyway's own applied-migrations tracking makes repeated
 * calls across test runs a safe no-op.
 */
internal object PostgreSQLTestDatabase {
    val dataSource: DataSource by lazy {
        val dataSource = DriverManagerDataSource().apply {
            setDriverClassName("org.postgresql.Driver")
            url = "jdbc:postgresql://127.0.0.1:5432/pios_identity"
            username = "postgres"
            password = ""
        }
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration/identity")
            .load()
            .migrate()
        dataSource
    }
}

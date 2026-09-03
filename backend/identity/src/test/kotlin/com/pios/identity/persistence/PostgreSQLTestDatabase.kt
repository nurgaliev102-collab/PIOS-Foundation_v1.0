package com.pios.identity.persistence

import org.flywaydb.core.Flyway
import org.springframework.jdbc.datasource.DriverManagerDataSource
import javax.sql.DataSource

/**
 * Test-only access to Identity's own PostgreSQL **test** database
 * (`pios_identity_test`) — deliberately never `pios_identity` itself
 * (that name is production's own database on this shared local Postgres
 * instance). Schema-managed exclusively through the same Flyway
 * migrations the application itself runs on startup
 * (`src/main/resources/db/migration/identity`) — never a manually-applied
 * SQL file, so the schema tests run against is reproducible from a clean
 * database in one deterministic step, an exact structural match for
 * production without ever being the same database. Mirrors
 * `com.pios.dispatch.persistence.PostgreSQLTestDatabase`'s own shape
 * exactly.
 *
 * Test/production database isolation incident (2026-09-02,
 * `PIOS_REALITY_AUDIT.md` Section 19): this object used to point at
 * `pios_identity` directly. A routine `./gradlew build` during a
 * read-only audit wrote real rows into production as a result. This name
 * is fixed, not configurable via any environment variable or system
 * property, precisely so there is no override path back to a
 * production-named database — see `docs/TEST_DATABASE_ISOLATION.md` for
 * the convention every module follows.
 *
 * [dataSource] is built once per test JVM ([by lazy]) and migrated
 * immediately; Flyway's own applied-migrations tracking makes repeated
 * calls across test runs a safe no-op.
 */
internal object PostgreSQLTestDatabase {
    val dataSource: DataSource by lazy {
        val dataSource = DriverManagerDataSource().apply {
            setDriverClassName("org.postgresql.Driver")
            url = "jdbc:postgresql://127.0.0.1:5432/pios_identity_test"
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

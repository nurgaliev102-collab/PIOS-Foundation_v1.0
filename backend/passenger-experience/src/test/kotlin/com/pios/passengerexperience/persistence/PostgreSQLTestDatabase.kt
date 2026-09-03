package com.pios.passengerexperience.persistence

import org.flywaydb.core.Flyway
import org.springframework.jdbc.datasource.DriverManagerDataSource
import javax.sql.DataSource

/**
 * Test-only access to Passenger Experience's own PostgreSQL **test**
 * database (`pios_passenger_experience_test`) — deliberately never
 * `pios_passenger_experience` itself (that name is production's own
 * database on this shared local Postgres instance), schema-managed
 * exclusively through the same Flyway migrations the application itself
 * runs on startup, mirroring
 * `com.pios.drivermanagement.persistence.PostgreSQLTestDatabase`'s own
 * KDoc and reasoning exactly.
 *
 * Test/production database isolation incident (2026-09-02,
 * `PIOS_REALITY_AUDIT.md` Section 19): this object used to point at
 * `pios_passenger_experience` directly. A routine `./gradlew build`
 * during a read-only audit wrote real rows into production as a result.
 * This name is fixed, not configurable via any environment variable or
 * system property, precisely so there is no override path back to a
 * production-named database — see `docs/TEST_DATABASE_ISOLATION.md` for
 * the convention every module follows.
 */
internal object PostgreSQLTestDatabase {
    val dataSource: DataSource by lazy {
        val dataSource = DriverManagerDataSource().apply {
            setDriverClassName("org.postgresql.Driver")
            url = "jdbc:postgresql://127.0.0.1:5432/pios_passenger_experience_test"
            username = "postgres"
            password = ""
        }
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration/passengerexperience")
            .load()
            .migrate()
        dataSource
    }
}

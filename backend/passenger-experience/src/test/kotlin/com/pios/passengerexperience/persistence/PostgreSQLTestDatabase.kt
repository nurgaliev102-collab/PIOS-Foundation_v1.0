package com.pios.passengerexperience.persistence

import org.flywaydb.core.Flyway
import org.springframework.jdbc.datasource.DriverManagerDataSource
import javax.sql.DataSource

/**
 * Test-only access to Passenger Experience's real PostgreSQL database
 * (`pios_passenger_experience`), schema-managed exclusively through the
 * same Flyway migrations the application itself runs on startup,
 * mirroring `com.pios.drivermanagement.persistence.PostgreSQLTestDatabase`'s
 * own KDoc and reasoning exactly.
 */
internal object PostgreSQLTestDatabase {
    val dataSource: DataSource by lazy {
        val dataSource = DriverManagerDataSource().apply {
            setDriverClassName("org.postgresql.Driver")
            url = "jdbc:postgresql://127.0.0.1:5432/pios_passenger_experience"
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

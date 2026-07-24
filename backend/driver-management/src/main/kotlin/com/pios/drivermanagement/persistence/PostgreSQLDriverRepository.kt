package com.pios.drivermanagement.persistence

import com.pios.drivermanagement.application.DriverRepository
import com.pios.drivermanagement.domain.Availability
import com.pios.drivermanagement.domain.Driver
import com.pios.drivermanagement.domain.DriverId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

/**
 * The PostgreSQL-backed adapter for [DriverRepository] (ADR-025;
 * PERSISTENCE_ARCHITECTURE.md Section 3, "Driver Management"). Persists
 * exactly the Driver logical entity this module owns, in its own
 * database (`pios_driver_management`) — no other module's information,
 * no shared schema. Schema is version-controlled and applied exclusively
 * through Flyway migrations (`db/migration`), never by this class.
 *
 * Uses plain JDBC via Spring's [JdbcTemplate] only — no ORM, no entity
 * annotations, no framework-specific mapping. [Driver] itself carries no
 * persistence knowledge whatsoever; this class owns database mapping and
 * storage operations exclusively, exactly as [DriverRepository]'s own
 * KDoc requires.
 *
 * Unlike Order Management's `Order` (private constructor, reachable only
 * through a factory), [Driver]'s constructor is public — `Driver(id,
 * availability)` — so reconstruction from a persisted row uses that
 * constructor directly. No reflection is needed here, and none is used.
 */
@Repository
class PostgreSQLDriverRepository(
    private val jdbcTemplate: JdbcTemplate
) : DriverRepository {

    override fun save(driver: Driver) {
        jdbcTemplate.update(
            """
            INSERT INTO drivers (id, availability) VALUES (?, ?)
            ON CONFLICT (id) DO UPDATE SET availability = EXCLUDED.availability
            """.trimIndent(),
            driver.id.value,
            driver.availability.name
        )
    }

    override fun findById(id: DriverId): Driver? {
        val rows = jdbcTemplate.query(
            "SELECT id, availability FROM drivers WHERE id = ?",
            { rs, _ ->
                Driver(
                    id = DriverId(rs.getString("id")),
                    availability = Availability.valueOf(rs.getString("availability"))
                )
            },
            id.value
        )
        return rows.firstOrNull()
    }

    override fun findAll(): List<Driver> =
        jdbcTemplate.query(
            "SELECT id, availability FROM drivers"
        ) { rs, _ ->
            Driver(
                id = DriverId(rs.getString("id")),
                availability = Availability.valueOf(rs.getString("availability"))
            )
        }
}

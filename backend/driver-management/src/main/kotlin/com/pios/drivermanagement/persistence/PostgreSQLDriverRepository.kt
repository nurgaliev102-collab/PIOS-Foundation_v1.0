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

    private val selectColumns =
        "id, availability, display_name, created_at, is_test, vehicle_make, vehicle_model, vehicle_color, vehicle_plate_number, vehicle_seat_count, accepts_long_distance_trips, invited_by_driver_id"

    override fun save(driver: Driver) {
        jdbcTemplate.update(
            """
            INSERT INTO drivers (id, availability, display_name, created_at, is_test, vehicle_make, vehicle_model, vehicle_color, vehicle_plate_number, vehicle_seat_count, accepts_long_distance_trips, invited_by_driver_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                availability = EXCLUDED.availability,
                vehicle_make = EXCLUDED.vehicle_make,
                vehicle_model = EXCLUDED.vehicle_model,
                vehicle_color = EXCLUDED.vehicle_color,
                vehicle_plate_number = EXCLUDED.vehicle_plate_number,
                vehicle_seat_count = EXCLUDED.vehicle_seat_count,
                accepts_long_distance_trips = EXCLUDED.accepts_long_distance_trips
            """.trimIndent(),
            driver.id.value,
            driver.availability.name,
            driver.displayName,
            driver.createdAt?.let { java.sql.Timestamp.from(it) },
            driver.isTest,
            driver.vehicleMake,
            driver.vehicleModel,
            driver.vehicleColor,
            driver.vehiclePlateNumber,
            driver.vehicleSeatCount,
            driver.acceptsLongDistanceTrips,
            driver.invitedByDriverId
        )
    }

    override fun findById(id: DriverId): Driver? {
        val rows = jdbcTemplate.query(
            "SELECT $selectColumns FROM drivers WHERE id = ?",
            { rs, _ -> reconstruct(rs) },
            id.value
        )
        return rows.firstOrNull()
    }

    override fun findAll(): List<Driver> =
        jdbcTemplate.query("SELECT $selectColumns FROM drivers") { rs, _ -> reconstruct(rs) }

    /**
     * ADR-073 Part 4: a direct `COUNT` over `drivers`, not a persisted
     * counter -- see [DriverRepository.countInvitedBy]'s own KDoc for why.
     * `is_test = FALSE` excludes technical verification drivers from a
     * real inviter's own count (ADR-073 Consequences).
     */
    override fun countInvitedBy(id: DriverId): Long =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM drivers WHERE invited_by_driver_id = ? AND is_test = FALSE",
            Long::class.java,
            id.value
        )

    private fun reconstruct(rs: java.sql.ResultSet): Driver = Driver(
        id = DriverId(rs.getString("id")),
        availability = Availability.valueOf(rs.getString("availability")),
        displayName = rs.getString("display_name"),
        createdAt = rs.getTimestamp("created_at")?.toInstant(),
        isTest = rs.getBoolean("is_test"),
        vehicleMake = rs.getString("vehicle_make"),
        vehicleModel = rs.getString("vehicle_model"),
        vehicleColor = rs.getString("vehicle_color"),
        vehiclePlateNumber = rs.getString("vehicle_plate_number"),
        vehicleSeatCount = rs.getInt("vehicle_seat_count").let { if (rs.wasNull()) null else it },
        acceptsLongDistanceTrips = rs.getBoolean("accepts_long_distance_trips"),
        invitedByDriverId = rs.getString("invited_by_driver_id")
    )
}

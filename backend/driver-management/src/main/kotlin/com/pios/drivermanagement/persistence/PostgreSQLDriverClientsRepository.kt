package com.pios.drivermanagement.persistence

import com.pios.drivermanagement.application.DriverClientsRepository
import com.pios.drivermanagement.domain.DriverId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

/**
 * Growth Loops TZ v1, Phase 2 extension (docs/PIOS_GROWTH_LOOPS_TZ_V1.md
 * Section 2.1's own disclosed gap, now closed): PostgreSQL-backed adapter
 * for [DriverClientsRepository]. Plain JDBC, no ORM.
 *
 * [recordRideForClient]'s `RETURNING ride_count` lets this method learn the
 * post-upsert count from the same statement that changed it, rather than a
 * separate read-after-write — the count this statement itself produced is
 * exactly the one the "did this just become a repeat client" check needs.
 */
@Repository
class PostgreSQLDriverClientsRepository(
    private val jdbcTemplate: JdbcTemplate
) : DriverClientsRepository {

    override fun recordRideForClient(driverId: DriverId, passengerReference: String): Boolean {
        val newRideCount = jdbcTemplate.queryForObject(
            """
            INSERT INTO driver_client_rides (driver_id, passenger_reference, ride_count)
            VALUES (?, ?, 1)
            ON CONFLICT (driver_id, passenger_reference) DO UPDATE SET
                ride_count = driver_client_rides.ride_count + 1
            RETURNING ride_count
            """.trimIndent(),
            Int::class.java,
            driverId.value,
            passengerReference
        )
        return newRideCount == REPEAT_CLIENT_THRESHOLD
    }

    companion object {
        private const val REPEAT_CLIENT_THRESHOLD = 2
    }
}

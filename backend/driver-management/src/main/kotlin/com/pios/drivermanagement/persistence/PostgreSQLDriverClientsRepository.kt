package com.pios.drivermanagement.persistence

import com.pios.drivermanagement.application.DriverClientsRepository
import com.pios.drivermanagement.domain.DriverClientRecord
import com.pios.drivermanagement.domain.DriverId
import java.sql.Timestamp
import java.time.Instant
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
 * `last_ride_at` is set unconditionally to [occurredAt] on every call (last
 * write wins, no `GREATEST` reconciliation), mirroring
 * [PostgreSQLDriverMilestonesRepository.recordCompletedRide]'s own
 * `last_completed_at` column and its documented rationale: this queue's
 * default listener concurrency processes one driver's events sequentially,
 * so two `AssignmentCompleted` events for the same driver are never
 * actually handled at the same instant in practice.
 *
 * [findAllForDriver] (server-side "Мой бизнес" read model) is a plain,
 * read-only query over the same table -- see [DriverClientRecord]'s own
 * KDoc for why [DriverClientRecord.isRepeat] is computed there, not stored.
 */
@Repository
class PostgreSQLDriverClientsRepository(
    private val jdbcTemplate: JdbcTemplate
) : DriverClientsRepository {

    override fun recordRideForClient(driverId: DriverId, passengerReference: String, occurredAt: Instant): Boolean {
        val newRideCount = jdbcTemplate.queryForObject(
            """
            INSERT INTO driver_client_rides (driver_id, passenger_reference, ride_count, last_ride_at)
            VALUES (?, ?, 1, ?)
            ON CONFLICT (driver_id, passenger_reference) DO UPDATE SET
                ride_count = driver_client_rides.ride_count + 1,
                last_ride_at = EXCLUDED.last_ride_at
            RETURNING ride_count
            """.trimIndent(),
            Int::class.java,
            driverId.value,
            passengerReference,
            Timestamp.from(occurredAt)
        )
        return newRideCount == REPEAT_CLIENT_THRESHOLD
    }

    override fun findAllForDriver(driverId: DriverId): List<DriverClientRecord> =
        jdbcTemplate.query(
            """
            SELECT driver_id, passenger_reference, ride_count, last_ride_at
            FROM driver_client_rides
            WHERE driver_id = ?
            ORDER BY last_ride_at DESC NULLS LAST
            """.trimIndent(),
            { rs, _ ->
                DriverClientRecord(
                    driverId = DriverId(rs.getString("driver_id")),
                    passengerReference = rs.getString("passenger_reference"),
                    rideCount = rs.getInt("ride_count"),
                    lastRideAt = rs.getTimestamp("last_ride_at")?.toInstant()
                )
            },
            driverId.value
        )

    companion object {
        private const val REPEAT_CLIENT_THRESHOLD = 2
    }
}

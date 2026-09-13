package com.pios.dispatch.persistence

import com.pios.dispatch.application.DriverAvailabilityRecord
import com.pios.dispatch.application.DriverAvailabilityRepository
import com.pios.dispatch.domain.DriverReference
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

/**
 * The PostgreSQL-backed adapter for [DriverAvailabilityRepository]
 * (ADR-025, ADR-032; Dispatch Consumer Foundation v1.0). Persists exactly
 * Dispatch's own local availability projection and idempotency ledger, in
 * this module's own database (`pios_dispatch`) -- no other module's
 * information, no shared schema. Uses plain JDBC via Spring's
 * [JdbcTemplate] only -- no ORM, no entity annotations.
 *
 * [markProcessed] relies on `driver_availability_processed_events`'s
 * primary key on `event_id` and `ON CONFLICT ... DO NOTHING`: the insert
 * affects exactly one row the first time a given event id is seen, and
 * zero rows on every subsequent redelivery of the same id -- the
 * PostgreSQL-enforced uniqueness this repository's idempotency guarantee
 * is actually built on, not merely an application-level check.
 */
@Repository
class PostgreSQLDriverAvailabilityRepository(
    private val jdbcTemplate: JdbcTemplate
) : DriverAvailabilityRepository {

    override fun markProcessed(eventId: String): Boolean {
        val rowsInserted = jdbcTemplate.update(
            "INSERT INTO driver_availability_processed_events (event_id) VALUES (?) ON CONFLICT (event_id) DO NOTHING",
            eventId
        )
        return rowsInserted > 0
    }

    override fun upsert(record: DriverAvailabilityRecord) {
        jdbcTemplate.update(
            """
            INSERT INTO driver_availability (driver_reference, available)
            VALUES (?, ?)
            ON CONFLICT (driver_reference) DO UPDATE SET available = EXCLUDED.available, updated_at = now()
            """.trimIndent(),
            record.driverReference.driverId,
            record.available
        )
    }

    override fun findByDriverReference(driverReference: DriverReference): DriverAvailabilityRecord? {
        val rows = jdbcTemplate.query(
            "SELECT driver_reference, available FROM driver_availability WHERE driver_reference = ?",
            { rs, _ ->
                DriverAvailabilityRecord(
                    driverReference = DriverReference(rs.getString("driver_reference")),
                    available = rs.getBoolean("available")
                )
            },
            driverReference.driverId
        )
        return rows.firstOrNull()
    }

    /**
     * FR-003A (Fallback Dispatch). `updated_at` is set to `now()` on every
     * [upsert] -- the row with the oldest `updated_at` among `available =
     * true` rows is the driver whose availability has stood unchanged the
     * longest, i.e. is currently the most idle. See [DriverAvailabilityRepository.findLongestIdleAvailable]'s
     * own KDoc for why [excluding] exists.
     */
    override fun findLongestIdleAvailable(excluding: Set<DriverReference>): DriverReference? {
        val sql = if (excluding.isEmpty()) {
            "SELECT driver_reference FROM driver_availability WHERE available = true ORDER BY updated_at ASC LIMIT 1"
        } else {
            val placeholders = excluding.joinToString(", ") { "?" }
            "SELECT driver_reference FROM driver_availability WHERE available = true AND driver_reference NOT IN ($placeholders) ORDER BY updated_at ASC LIMIT 1"
        }
        val args = excluding.map { it.driverId }.toTypedArray()
        val rows = jdbcTemplate.query(sql, { rs, _ -> DriverReference(rs.getString("driver_reference")) }, *args)
        return rows.firstOrNull()
    }
}

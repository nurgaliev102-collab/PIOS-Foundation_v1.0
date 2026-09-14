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

    /**
     * ADR-069 Part 2: `is_test` is carried through on **every** upsert,
     * including when [DriverAvailabilityRecord.isTest] is `null` -- never
     * `COALESCE`d against the previous value -- so a driver's
     * classification is refreshed on every availability change rather than
     * being written once and trusted forever. Passing a Kotlin `null`
     * through to `jdbcTemplate.update`'s vararg `args` writes SQL `NULL`,
     * the same pattern already used for nullable columns elsewhere in this
     * codebase (e.g. `PostgreSQLDriverRepository.save`'s `displayName`).
     */
    override fun upsert(record: DriverAvailabilityRecord) {
        jdbcTemplate.update(
            """
            INSERT INTO driver_availability (driver_reference, available, is_test)
            VALUES (?, ?, ?)
            ON CONFLICT (driver_reference) DO UPDATE SET available = EXCLUDED.available, is_test = EXCLUDED.is_test, updated_at = now()
            """.trimIndent(),
            record.driverReference.driverId,
            record.available,
            record.isTest
        )
    }

    override fun findByDriverReference(driverReference: DriverReference): DriverAvailabilityRecord? {
        val rows = jdbcTemplate.query(
            "SELECT driver_reference, available, is_test FROM driver_availability WHERE driver_reference = ?",
            { rs, _ ->
                DriverAvailabilityRecord(
                    driverReference = DriverReference(rs.getString("driver_reference")),
                    available = rs.getBoolean("available"),
                    isTest = rs.getObject("is_test") as Boolean?
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
     *
     * ADR-069 Part 3: `is_test IS NOT NULL AND is_test = ?` is the same
     * fail-closed, strict-equality predicate applied identically to
     * [com.pios.dispatch.persistence.PostgreSQLTrustedDriverRepository.findLongestIdleTrustedAvailable]'s
     * Tier 1 query -- a driver whose classification is still unknown
     * (`NULL`) is never returned for either a real or a test order.
     */
    override fun findLongestIdleAvailable(orderIsTest: Boolean, excluding: Set<DriverReference>): DriverReference? {
        val sql = if (excluding.isEmpty()) {
            """
            SELECT driver_reference FROM driver_availability
            WHERE available = true AND is_test IS NOT NULL AND is_test = ?
            ORDER BY updated_at ASC LIMIT 1
            """.trimIndent()
        } else {
            val placeholders = excluding.joinToString(", ") { "?" }
            """
            SELECT driver_reference FROM driver_availability
            WHERE available = true AND is_test IS NOT NULL AND is_test = ?
            AND driver_reference NOT IN ($placeholders)
            ORDER BY updated_at ASC LIMIT 1
            """.trimIndent()
        }
        val args = (listOf(orderIsTest) + excluding.map { it.driverId }).toTypedArray()
        val rows = jdbcTemplate.query(sql, { rs, _ -> DriverReference(rs.getString("driver_reference")) }, *args)
        return rows.firstOrNull()
    }
}

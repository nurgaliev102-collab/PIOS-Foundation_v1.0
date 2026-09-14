package com.pios.dispatch.persistence

import com.pios.dispatch.application.TrustedDriverRecord
import com.pios.dispatch.application.TrustedDriverRepository
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.PassengerReference
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

/**
 * The PostgreSQL-backed adapter for [TrustedDriverRepository] (ADR-068,
 * Relationship-Ordered Fallback Dispatch — Trusted → Network → Open
 * Marketplace, Part 1), mirroring [PostgreSQLPrimaryDriverRepository]'s
 * own exact shape. Persists exactly Dispatch's own local trusted-circle
 * projection and idempotency ledger, in this module's own database
 * (`pios_dispatch`) -- no other module's information, no shared schema.
 * Uses plain JDBC via Spring's [JdbcTemplate] only -- no ORM, no entity
 * annotations.
 *
 * [markProcessed] relies on `trusted_driver_processed_events`'s primary
 * key on `event_id` and `ON CONFLICT ... DO NOTHING`, identical reasoning
 * to [PostgreSQLPrimaryDriverRepository.markProcessed].
 *
 * [findLongestIdleTrustedAvailable] joins this module's own
 * `trusted_driver_records` against `driver_availability` -- both tables
 * live in this same module's own schema (`pios_dispatch`), so this join
 * crosses no module boundary; it is the same "availability remains the
 * sole eligibility gate, sourced only from Dispatch's own
 * `driver_availability` projection" rule
 * [PostgreSQLDriverAvailabilityRepository.findLongestIdleAvailable]
 * already applies, narrowed to one passenger's trusted set (ADR-068 Part
 * 2, property 3).
 */
@Repository
class PostgreSQLTrustedDriverRepository(
    private val jdbcTemplate: JdbcTemplate
) : TrustedDriverRepository {

    override fun markProcessed(eventId: String): Boolean {
        val rowsInserted = jdbcTemplate.update(
            "INSERT INTO trusted_driver_processed_events (event_id) VALUES (?) ON CONFLICT (event_id) DO NOTHING",
            eventId
        )
        return rowsInserted > 0
    }

    override fun add(record: TrustedDriverRecord) {
        jdbcTemplate.update(
            """
            INSERT INTO trusted_driver_records (passenger_reference, driver_reference)
            VALUES (?, ?)
            ON CONFLICT (passenger_reference, driver_reference) DO NOTHING
            """.trimIndent(),
            record.passengerReference.passengerId,
            record.driverId.driverId
        )
    }

    override fun remove(passengerReference: PassengerReference, driverId: DriverReference) {
        jdbcTemplate.update(
            "DELETE FROM trusted_driver_records WHERE passenger_reference = ? AND driver_reference = ?",
            passengerReference.passengerId,
            driverId.driverId
        )
    }

    /**
     * ADR-069 Part 3 and Part 5: `a.is_test IS NOT NULL AND a.is_test = ?`
     * is the identical predicate
     * [PostgreSQLDriverAvailabilityRepository.findLongestIdleAvailable]
     * applies to Tier 3 -- ratified as one change, not a follow-up,
     * since a real passenger with a test driver in their trusted circle
     * would otherwise hit ADR-069's defect *first and preferentially*.
     */
    override fun findLongestIdleTrustedAvailable(
        passengerReference: PassengerReference,
        orderIsTest: Boolean,
        excluding: Set<DriverReference>
    ): DriverReference? {
        val sql = if (excluding.isEmpty()) {
            """
            SELECT t.driver_reference
            FROM trusted_driver_records t
            JOIN driver_availability a ON a.driver_reference = t.driver_reference
            WHERE t.passenger_reference = ? AND a.available = true
            AND a.is_test IS NOT NULL AND a.is_test = ?
            ORDER BY a.updated_at ASC LIMIT 1
            """.trimIndent()
        } else {
            val placeholders = excluding.joinToString(", ") { "?" }
            """
            SELECT t.driver_reference
            FROM trusted_driver_records t
            JOIN driver_availability a ON a.driver_reference = t.driver_reference
            WHERE t.passenger_reference = ? AND a.available = true
            AND a.is_test IS NOT NULL AND a.is_test = ?
            AND t.driver_reference NOT IN ($placeholders)
            ORDER BY a.updated_at ASC LIMIT 1
            """.trimIndent()
        }
        val args = (listOf(passengerReference.passengerId, orderIsTest) + excluding.map { it.driverId }).toTypedArray()
        val rows = jdbcTemplate.query(sql, { rs, _ -> DriverReference(rs.getString("driver_reference")) }, *args)
        return rows.firstOrNull()
    }
}

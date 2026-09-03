package com.pios.dispatch.persistence

import com.pios.dispatch.application.PrimaryDriverRecord
import com.pios.dispatch.application.PrimaryDriverRepository
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.PassengerReference
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

/**
 * The PostgreSQL-backed adapter for [PrimaryDriverRepository] (ADR-062;
 * Task 14, First Refusal Foundation), mirroring
 * [PostgreSQLDriverAvailabilityRepository]'s own exact shape. Persists
 * exactly Dispatch's own local primary-driver projection and idempotency
 * ledger, in this module's own database (`pios_dispatch`) -- no other
 * module's information, no shared schema. Uses plain JDBC via Spring's
 * [JdbcTemplate] only -- no ORM, no entity annotations.
 *
 * [markProcessed] relies on `primary_driver_processed_events`'s primary
 * key on `event_id` and `ON CONFLICT ... DO NOTHING`: the insert affects
 * exactly one row the first time a given event id is seen, and zero rows
 * on every subsequent redelivery of the same id -- the
 * PostgreSQL-enforced uniqueness this repository's idempotency guarantee
 * is actually built on, not merely an application-level check.
 */
@Repository
class PostgreSQLPrimaryDriverRepository(
    private val jdbcTemplate: JdbcTemplate
) : PrimaryDriverRepository {

    override fun markProcessed(eventId: String): Boolean {
        val rowsInserted = jdbcTemplate.update(
            "INSERT INTO primary_driver_processed_events (event_id) VALUES (?) ON CONFLICT (event_id) DO NOTHING",
            eventId
        )
        return rowsInserted > 0
    }

    override fun upsert(record: PrimaryDriverRecord) {
        jdbcTemplate.update(
            """
            INSERT INTO primary_driver_records (passenger_reference, driver_reference)
            VALUES (?, ?)
            ON CONFLICT (passenger_reference) DO UPDATE SET driver_reference = EXCLUDED.driver_reference, updated_at = now()
            """.trimIndent(),
            record.passengerReference.passengerId,
            record.primaryDriverId.driverId
        )
    }

    override fun clear(passengerReference: PassengerReference) {
        jdbcTemplate.update(
            "DELETE FROM primary_driver_records WHERE passenger_reference = ?",
            passengerReference.passengerId
        )
    }

    override fun findByPassenger(passengerReference: PassengerReference): PrimaryDriverRecord? {
        val rows = jdbcTemplate.query(
            "SELECT passenger_reference, driver_reference FROM primary_driver_records WHERE passenger_reference = ?",
            { rs, _ ->
                PrimaryDriverRecord(
                    passengerReference = PassengerReference(rs.getString("passenger_reference")),
                    primaryDriverId = DriverReference(rs.getString("driver_reference"))
                )
            },
            passengerReference.passengerId
        )
        return rows.firstOrNull()
    }
}

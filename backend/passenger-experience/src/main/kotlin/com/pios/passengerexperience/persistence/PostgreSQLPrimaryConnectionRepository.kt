package com.pios.passengerexperience.persistence

import com.pios.passengerexperience.application.PrimaryConnectionRepository
import com.pios.passengerexperience.domain.ConnectionId
import com.pios.passengerexperience.domain.PassengerReference
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Instant

/**
 * The PostgreSQL-backed adapter for [PrimaryConnectionRepository]
 * (ADR-054, Circle of Trust), in this module's own database
 * (`pios_passenger_experience`). Plain JDBC via [JdbcTemplate] only, no
 * ORM, mirroring [PostgreSQLConnectionRepository]'s own style.
 *
 * [setPrimary] is a single `INSERT ... ON CONFLICT ... DO UPDATE` --
 * ADR-054 Part 2 chose the `primary_connections` table's
 * `PRIMARY KEY (passenger_reference)` specifically so this designation
 * never needs a clear-then-set two-statement sequence.
 */
@Repository
class PostgreSQLPrimaryConnectionRepository(
    private val jdbcTemplate: JdbcTemplate
) : PrimaryConnectionRepository {

    override fun findByPassenger(passengerReference: PassengerReference): ConnectionId? {
        val rows = jdbcTemplate.query(
            "SELECT connection_id FROM primary_connections WHERE passenger_reference = ?",
            { rs, _ -> ConnectionId(rs.getString("connection_id")) },
            passengerReference.passengerId
        )
        return rows.firstOrNull()
    }

    override fun setPrimary(passengerReference: PassengerReference, connectionId: ConnectionId, designatedAt: Instant) {
        jdbcTemplate.update(
            """
            INSERT INTO primary_connections (passenger_reference, connection_id, designated_at)
            VALUES (?, ?, ?)
            ON CONFLICT (passenger_reference)
            DO UPDATE SET connection_id = EXCLUDED.connection_id, designated_at = EXCLUDED.designated_at
            """.trimIndent(),
            passengerReference.passengerId,
            connectionId.value,
            java.sql.Timestamp.from(designatedAt)
        )
    }
}

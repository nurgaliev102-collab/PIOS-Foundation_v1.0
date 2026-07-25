package com.pios.passengerexperience.persistence

import com.pios.passengerexperience.application.ConnectionRepository
import com.pios.passengerexperience.domain.Connection
import com.pios.passengerexperience.domain.ConnectionId
import com.pios.passengerexperience.domain.DriverReference
import com.pios.passengerexperience.domain.PassengerReference
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

/**
 * The PostgreSQL-backed adapter for [ConnectionRepository] (Sprint 7B:
 * Personal Network Flow MVP), in this module's own database
 * (`pios_passenger_experience`) -- no other module's information. Plain
 * JDBC via [JdbcTemplate] only, no ORM, mirroring
 * `com.pios.drivermanagement.persistence.PostgreSQLDriverRepository`'s own
 * style.
 */
@Repository
class PostgreSQLConnectionRepository(
    private val jdbcTemplate: JdbcTemplate
) : ConnectionRepository {

    override fun save(connection: Connection) {
        jdbcTemplate.update(
            "INSERT INTO connections (id, driver_id, passenger_reference, created_at) VALUES (?, ?, ?, ?)",
            connection.id.value,
            connection.driverId.driverId,
            connection.passengerReference.passengerId,
            java.sql.Timestamp.from(connection.createdAt)
        )
    }

    override fun findByDriverAndPassenger(driverId: DriverReference, passengerReference: PassengerReference): Connection? {
        val rows = jdbcTemplate.query(
            "SELECT id, driver_id, passenger_reference, created_at FROM connections WHERE driver_id = ? AND passenger_reference = ?",
            { rs, _ -> rs.toConnection() },
            driverId.driverId,
            passengerReference.passengerId
        )
        return rows.firstOrNull()
    }

    override fun findByDriver(driverId: DriverReference): List<Connection> =
        jdbcTemplate.query(
            "SELECT id, driver_id, passenger_reference, created_at FROM connections WHERE driver_id = ?",
            { rs, _ -> rs.toConnection() },
            driverId.driverId
        )

    private fun java.sql.ResultSet.toConnection(): Connection = Connection(
        id = ConnectionId(getString("id")),
        driverId = DriverReference(getString("driver_id")),
        passengerReference = PassengerReference(getString("passenger_reference")),
        createdAt = getTimestamp("created_at").toInstant()
    )
}

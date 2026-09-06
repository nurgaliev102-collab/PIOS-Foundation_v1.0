package com.pios.drivermanagement.persistence

import com.pios.drivermanagement.application.OrderPassengerRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

/**
 * Growth Loops TZ v1, Phase 2 extension (docs/PIOS_GROWTH_LOOPS_TZ_V1.md
 * Section 2.1's own disclosed gap, now closed): PostgreSQL-backed adapter
 * for [OrderPassengerRepository]. Plain JDBC, no ORM.
 */
@Repository
class PostgreSQLOrderPassengerRepository(
    private val jdbcTemplate: JdbcTemplate
) : OrderPassengerRepository {

    override fun recordOrderPassenger(orderId: String, passengerReference: String) {
        jdbcTemplate.update(
            "INSERT INTO driver_management_order_passengers (order_id, passenger_reference) VALUES (?, ?) ON CONFLICT (order_id) DO NOTHING",
            orderId,
            passengerReference
        )
    }

    override fun findPassengerReference(orderId: String): String? {
        val rows = jdbcTemplate.query(
            "SELECT passenger_reference FROM driver_management_order_passengers WHERE order_id = ?",
            { rs, _ -> rs.getString("passenger_reference") },
            orderId
        )
        return rows.firstOrNull()
    }
}

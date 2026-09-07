package com.pios.drivermanagement.persistence

import com.pios.drivermanagement.application.DriverRideStatedPricesRepository
import com.pios.drivermanagement.domain.DriverId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

/**
 * ADR-065 (Driver Earnings from Self-Stated Prices): PostgreSQL-backed
 * adapter for [DriverRideStatedPricesRepository]. Plain JDBC, no ORM,
 * matching every other repository in this module.
 *
 * `event_id` has a `PRIMARY KEY` referencing
 * `driver_management_processed_events(event_id)` (`V8`) — the row that
 * [com.pios.drivermanagement.application.AssignmentCompletedApplicationService]'s
 * own `markProcessed` call already inserts, earlier in the same
 * transaction, before this method ever runs.
 */
@Repository
class PostgreSQLDriverRideStatedPricesRepository(
    private val jdbcTemplate: JdbcTemplate
) : DriverRideStatedPricesRepository {

    override fun record(eventId: String, driverId: DriverId, statedPriceRaw: String?, statedPriceParsed: Long?) {
        jdbcTemplate.update(
            """
            INSERT INTO driver_ride_stated_prices (event_id, driver_id, stated_price_raw, stated_price_parsed)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (event_id) DO NOTHING
            """.trimIndent(),
            eventId,
            driverId.value,
            statedPriceRaw,
            statedPriceParsed
        )
    }
}

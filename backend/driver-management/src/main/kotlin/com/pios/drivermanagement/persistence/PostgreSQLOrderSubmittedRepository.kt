package com.pios.drivermanagement.persistence

import com.pios.drivermanagement.application.OrderSubmittedRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

/**
 * Growth Loops TZ v1, Phase 2 extension (docs/PIOS_GROWTH_LOOPS_TZ_V1.md
 * Section 2.1's own disclosed gap, now closed): PostgreSQL-backed adapter
 * for [OrderSubmittedRepository]. Shares the same physical
 * `driver_management_processed_events` table [PostgreSQLAssignmentCompletedRepository]
 * writes to — see [OrderSubmittedRepository]'s own KDoc for why that
 * sharing is correct (event-type-agnostic ledger, keyed on the globally
 * unique `eventId`) even though the two Kotlin interfaces stay distinct.
 */
@Repository
class PostgreSQLOrderSubmittedRepository(
    private val jdbcTemplate: JdbcTemplate
) : OrderSubmittedRepository {

    override fun markProcessed(eventId: String): Boolean {
        val rowsInserted = jdbcTemplate.update(
            "INSERT INTO driver_management_processed_events (event_id) VALUES (?) ON CONFLICT (event_id) DO NOTHING",
            eventId
        )
        return rowsInserted > 0
    }

    override fun isProcessed(eventId: String): Boolean {
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM driver_management_processed_events WHERE event_id = ?",
            Long::class.java,
            eventId
        )
        return count > 0L
    }
}

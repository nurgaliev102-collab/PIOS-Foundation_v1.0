package com.pios.drivermanagement.persistence

import com.pios.drivermanagement.application.AssignmentCompletedRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

/**
 * Growth Loops TZ v1, Phase 2 (docs/PIOS_GROWTH_LOOPS_TZ_V1.md Section 2):
 * PostgreSQL-backed adapter for [AssignmentCompletedRepository], mirroring
 * Order Management's own `PostgreSQLAssignmentCompletedRepository` exactly
 * — plain JDBC, `ON CONFLICT (event_id) DO NOTHING` as the actual
 * uniqueness guarantee (PostgreSQL-enforced, not merely an
 * application-level check).
 */
@Repository
class PostgreSQLAssignmentCompletedRepository(
    private val jdbcTemplate: JdbcTemplate
) : AssignmentCompletedRepository {

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

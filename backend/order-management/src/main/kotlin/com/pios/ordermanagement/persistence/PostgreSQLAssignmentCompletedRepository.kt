package com.pios.ordermanagement.persistence

import com.pios.ordermanagement.application.AssignmentCompletedRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

/**
 * The PostgreSQL-backed adapter for [AssignmentCompletedRepository]
 * (ADR-041, ADR-025, ADR-032). Persists Order Management's own idempotency
 * ledger for AssignmentCompleted in the same `order_management_processed_events`
 * table [PostgreSQLAssignmentAcceptedRepository] already writes to — see
 * [AssignmentCompletedRepository]'s own KDoc for why sharing that table is
 * correct rather than a new mechanism. Uses plain JDBC via Spring's
 * [JdbcTemplate] only -- no ORM, no entity annotations.
 *
 * [markProcessed] relies on `order_management_processed_events`'s primary
 * key on `event_id` and `ON CONFLICT ... DO NOTHING`: the insert affects
 * exactly one row the first time a given event id is seen, and zero rows
 * on every subsequent redelivery of the same id -- the PostgreSQL-enforced
 * uniqueness this repository's idempotency guarantee is actually built
 * on, not merely an application-level check. Mirrors
 * [PostgreSQLAssignmentAcceptedRepository] exactly.
 */
@Repository
class PostgreSQLAssignmentCompletedRepository(
    private val jdbcTemplate: JdbcTemplate
) : AssignmentCompletedRepository {

    override fun markProcessed(eventId: String): Boolean {
        val rowsInserted = jdbcTemplate.update(
            "INSERT INTO order_management_processed_events (event_id) VALUES (?) ON CONFLICT (event_id) DO NOTHING",
            eventId
        )
        return rowsInserted > 0
    }

    override fun isProcessed(eventId: String): Boolean {
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM order_management_processed_events WHERE event_id = ?",
            Long::class.java,
            eventId
        )
        return count > 0L
    }
}

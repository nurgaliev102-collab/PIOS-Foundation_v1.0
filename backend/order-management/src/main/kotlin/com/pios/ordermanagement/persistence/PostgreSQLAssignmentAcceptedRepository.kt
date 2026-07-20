package com.pios.ordermanagement.persistence

import com.pios.ordermanagement.application.AssignmentAcceptedRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

/**
 * The PostgreSQL-backed adapter for [AssignmentAcceptedRepository]
 * (ADR-025, ADR-032; Tranche 1: Dispatch Event Publishing Completion).
 * Persists exactly Order Management's own idempotency ledger for
 * AssignmentAccepted, in this module's own database
 * (`pios_order_management`) -- no other module's information, no shared
 * schema. Uses plain JDBC via Spring's [JdbcTemplate] only -- no ORM, no
 * entity annotations.
 *
 * [markProcessed] relies on `order_management_processed_events`'s primary
 * key on `event_id` and `ON CONFLICT ... DO NOTHING`: the insert affects
 * exactly one row the first time a given event id is seen, and zero rows
 * on every subsequent redelivery of the same id -- the PostgreSQL-enforced
 * uniqueness this repository's idempotency guarantee is actually built
 * on, not merely an application-level check. Mirrors Dispatch's own
 * `PostgreSQLDriverAvailabilityRepository.markProcessed` exactly.
 */
@Repository
class PostgreSQLAssignmentAcceptedRepository(
    private val jdbcTemplate: JdbcTemplate
) : AssignmentAcceptedRepository {

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

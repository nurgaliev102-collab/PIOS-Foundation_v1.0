package com.pios.dispatch.persistence

import com.pios.dispatch.application.OrderCancelledRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

/**
 * The PostgreSQL-backed adapter for [OrderCancelledRepository] (ADR-053,
 * Proposal Resolution on Order Cancellation; ADR-031, ADR-032). Persists
 * exactly Dispatch's own idempotency ledger over consumed `OrderCancelled`
 * events, in this module's own database (`pios_dispatch`) — no other
 * module's information, no shared schema. Uses plain JDBC via Spring's
 * [JdbcTemplate] only — no ORM, no entity annotations. Mirrors
 * [PostgreSQLDriverAvailabilityRepository.markProcessed] exactly.
 *
 * [markProcessed] relies on `order_cancelled_processed_events`'s primary
 * key on `event_id` and `ON CONFLICT ... DO NOTHING`: the insert affects
 * exactly one row the first time a given event id is seen, and zero rows
 * on every subsequent redelivery of the same id — the PostgreSQL-enforced
 * uniqueness this repository's idempotency guarantee is actually built
 * on, not merely an application-level check.
 */
@Repository
class PostgreSQLOrderCancelledRepository(
    private val jdbcTemplate: JdbcTemplate
) : OrderCancelledRepository {

    override fun markProcessed(eventId: String): Boolean {
        val rowsInserted = jdbcTemplate.update(
            "INSERT INTO order_cancelled_processed_events (event_id) VALUES (?) ON CONFLICT (event_id) DO NOTHING",
            eventId
        )
        return rowsInserted > 0
    }
}

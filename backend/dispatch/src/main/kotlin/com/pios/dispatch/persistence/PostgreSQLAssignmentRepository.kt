package com.pios.dispatch.persistence

import com.pios.dispatch.application.AssignmentRepository
import com.pios.dispatch.domain.Assignment
import com.pios.dispatch.domain.AssignmentId
import com.pios.dispatch.domain.AssignmentStatus
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

/**
 * The PostgreSQL-backed adapter for [AssignmentRepository] (ADR-025;
 * PERSISTENCE_ARCHITECTURE.md Section 3, "Dispatch"). Persists exactly
 * the Assignment logical entity this module owns, in its own database
 * (`pios_dispatch`) — no other module's information, no shared schema.
 * [OrderReference] and [DriverReference] are persisted as plain
 * identifiers only (PERSISTENCE_ARCHITECTURE.md Section 5, "Allowed
 * references"): this table never holds a copy of Order Management's or
 * Driver Management's own information, only the reference Dispatch
 * itself already owns. Schema is version-controlled and applied
 * exclusively through Flyway migrations (`db/migration/dispatch`), never
 * by this class.
 *
 * Uses plain JDBC via Spring's [JdbcTemplate] only — no ORM, no entity
 * annotations, no framework-specific mapping. [Assignment] itself
 * carries no persistence knowledge whatsoever; this class owns database
 * mapping and storage operations exclusively, exactly as
 * [AssignmentRepository]'s own KDoc requires.
 *
 * ## Reconstruction and the private constructor
 *
 * Like Order Management's `Order`, [Assignment]'s constructor is
 * private, reachable only through [Assignment.create] — which always
 * starts a new assignment at [AssignmentStatus.CREATED] and does not
 * accept a status parameter at all (`status` is not part of the
 * constructor; it is fixed at construction and changed only by
 * [Assignment.accept]). Reconstructing a persisted, possibly-already-
 * accepted [Assignment] therefore takes two steps, both confined to this
 * one file:
 *
 * 1. Reflectively invoke the private constructor with the persisted id,
 *    order reference, and driver reference — confirmed empirically
 *    against the compiled class to take unboxed `String` parameters
 *    (`Assignment(String, String, String)`), the same value-class
 *    behavior already documented for `Order`. This always yields an
 *    assignment in its initial [AssignmentStatus.CREATED] state, since
 *    the constructor itself sets nothing else.
 * 2. If the persisted row's status is [AssignmentStatus.ACCEPTED], call
 *    the aggregate's own public [Assignment.accept] to advance it —
 *    no further reflection needed, since this is exactly the domain's
 *    own, already-public way to reach that state.
 *
 * This does not bypass any invariant: step 1's private constructor
 * performs no validation beyond what already-persisted, previously-valid
 * data satisfies by construction, and step 2 uses the aggregate's own
 * business logic unmodified.
 *
 * [findByOrder] (Sprint FR-004, Manual Assignment) reuses the same
 * [reconstruct] helper as [findById], row by row, filtered by
 * `order_reference` — no filtering by status, since [Assignment]'s own
 * invariant treats every assignment as active regardless of status (see
 * that class's own KDoc).
 *
 * ADR-040 (Assignment Ride Lifecycle) adds `status_changed_at`, read and
 * written below alongside `status`, and extends [reconstruct] to replay
 * however many transition methods are needed to reach a persisted
 * [AssignmentStatus.ARRIVED]/[AssignmentStatus.IN_PROGRESS]/[AssignmentStatus.COMPLETED] —
 * the same replay technique already established for [AssignmentStatus.ACCEPTED].
 *
 * ADR-043 (Owner Control Center — Observation Boundary) adds
 * `arrived_at`/`started_at`/`completed_at` beside `status_changed_at`
 * (kept, not replaced — that Decision's own binding constraint) and
 * changes how [reconstruct] replays a multi-step chain: **each** step is
 * now given its own real persisted timestamp (falling back to `now()`
 * only when a persisted value happens to be `null` — a row written before
 * this migration, per ADR-043's own disclosed limitation), rather than
 * every step but the last being replayed with an unobserved `now()`. This
 * is not merely cosmetic: those columns exist so the owner console can
 * show *when* a ride arrived or started, and a reconstructed
 * [AssignmentStatus.COMPLETED] assignment that fabricated its own
 * `arrivedAt`/`startedAt` on every read would silently defeat that.
 */
@Repository
class PostgreSQLAssignmentRepository(
    private val jdbcTemplate: JdbcTemplate
) : AssignmentRepository {

    override fun save(assignment: Assignment) {
        jdbcTemplate.update(
            """
            INSERT INTO assignments (
                id, order_reference, driver_reference, status, status_changed_at,
                arrived_at, started_at, completed_at, is_test
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                status = EXCLUDED.status,
                status_changed_at = EXCLUDED.status_changed_at,
                arrived_at = EXCLUDED.arrived_at,
                started_at = EXCLUDED.started_at,
                completed_at = EXCLUDED.completed_at
            """.trimIndent(),
            assignment.id.value,
            assignment.order.orderId,
            assignment.driver.driverId,
            assignment.status.name,
            assignment.statusChangedAt?.let { Timestamp.from(it) },
            assignment.arrivedAt?.let { Timestamp.from(it) },
            assignment.startedAt?.let { Timestamp.from(it) },
            assignment.completedAt?.let { Timestamp.from(it) },
            assignment.isTest
        )
    }

    override fun findById(id: AssignmentId): Assignment? {
        val rows = jdbcTemplate.query(
            """
            SELECT id, order_reference, driver_reference, status, status_changed_at,
                   arrived_at, started_at, completed_at, is_test
            FROM assignments WHERE id = ?
            """.trimIndent(),
            { rs, _ -> reconstruct(rs) },
            id.value
        )
        return rows.firstOrNull()
    }

    override fun findByOrder(order: OrderReference): List<Assignment> =
        jdbcTemplate.query(
            """
            SELECT id, order_reference, driver_reference, status, status_changed_at,
                   arrived_at, started_at, completed_at, is_test
            FROM assignments WHERE order_reference = ?
            """.trimIndent(),
            { rs, _ -> reconstruct(rs) },
            order.orderId
        )

    /**
     * [AssignmentRepository.findByOrders]'s own PostgreSQL adapter: the
     * same `IN (...)` batching technique
     * [PostgreSQLDriverAvailabilityRepository.findLongestIdleAvailable]
     * already uses for its `NOT IN` exclusion set, applied here to a
     * positive filter instead.
     */
    override fun findByOrders(orders: List<OrderReference>): List<Assignment> {
        if (orders.isEmpty()) return emptyList()
        val placeholders = orders.joinToString(", ") { "?" }
        return jdbcTemplate.query(
            """
            SELECT id, order_reference, driver_reference, status, status_changed_at,
                   arrived_at, started_at, completed_at, is_test
            FROM assignments WHERE order_reference IN ($placeholders)
            """.trimIndent(),
            { rs, _ -> reconstruct(rs) },
            *orders.map { it.orderId }.toTypedArray()
        )
    }

    private fun reconstruct(rs: ResultSet): Assignment {
        val constructor = Assignment::class.java.getDeclaredConstructor(
            String::class.java,
            String::class.java,
            String::class.java,
            Boolean::class.java
        )
        constructor.isAccessible = true
        val assignment = constructor.newInstance(
            rs.getString("id"),
            rs.getString("order_reference"),
            rs.getString("driver_reference"),
            rs.getBoolean("is_test")
        )
        val status = AssignmentStatus.valueOf(rs.getString("status"))
        val statusChangedAt: Instant = rs.getTimestamp("status_changed_at")?.toInstant() ?: Instant.now()
        val arrivedAt: Instant = rs.getTimestamp("arrived_at")?.toInstant() ?: Instant.now()
        val startedAt: Instant = rs.getTimestamp("started_at")?.toInstant() ?: Instant.now()
        val completedAt: Instant = rs.getTimestamp("completed_at")?.toInstant() ?: Instant.now()
        when (status) {
            AssignmentStatus.CREATED -> {}
            AssignmentStatus.ACCEPTED -> assignment.accept(statusChangedAt)
            AssignmentStatus.ARRIVED -> assignment.arrive(arrivedAt)
            AssignmentStatus.IN_PROGRESS -> {
                assignment.arrive(arrivedAt)
                assignment.start(startedAt)
            }
            AssignmentStatus.COMPLETED -> {
                assignment.arrive(arrivedAt)
                assignment.start(startedAt)
                assignment.complete(completedAt)
            }
        }
        return assignment
    }
}

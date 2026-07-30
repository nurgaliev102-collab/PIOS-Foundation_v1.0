package com.pios.dispatch.persistence

import com.pios.dispatch.application.AssignmentRepository
import com.pios.dispatch.domain.Assignment
import com.pios.dispatch.domain.AssignmentId
import com.pios.dispatch.domain.AssignmentStatus
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
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
 * the same replay technique already established for [AssignmentStatus.ACCEPTED],
 * extended along the same chain. Only the *last* replayed call is given
 * the real persisted `status_changed_at`; every earlier one in the chain
 * is immediately superseded by the next, so its own exact timestamp
 * (necessarily `now()`, since [Assignment.accept]/[Assignment.arrive]/
 * [Assignment.start] default that way) is never observed.
 */
@Repository
class PostgreSQLAssignmentRepository(
    private val jdbcTemplate: JdbcTemplate
) : AssignmentRepository {

    override fun save(assignment: Assignment) {
        jdbcTemplate.update(
            """
            INSERT INTO assignments (id, order_reference, driver_reference, status, status_changed_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status, status_changed_at = EXCLUDED.status_changed_at
            """.trimIndent(),
            assignment.id.value,
            assignment.order.orderId,
            assignment.driver.driverId,
            assignment.status.name,
            assignment.statusChangedAt?.let { Timestamp.from(it) }
        )
    }

    override fun findById(id: AssignmentId): Assignment? {
        val rows = jdbcTemplate.query(
            "SELECT id, order_reference, driver_reference, status, status_changed_at FROM assignments WHERE id = ?",
            { rs, _ ->
                reconstruct(
                    id = rs.getString("id"),
                    orderReference = rs.getString("order_reference"),
                    driverReference = rs.getString("driver_reference"),
                    status = AssignmentStatus.valueOf(rs.getString("status")),
                    statusChangedAt = rs.getTimestamp("status_changed_at")
                )
            },
            id.value
        )
        return rows.firstOrNull()
    }

    override fun findByOrder(order: OrderReference): List<Assignment> =
        jdbcTemplate.query(
            "SELECT id, order_reference, driver_reference, status, status_changed_at FROM assignments WHERE order_reference = ?",
            { rs, _ ->
                reconstruct(
                    id = rs.getString("id"),
                    orderReference = rs.getString("order_reference"),
                    driverReference = rs.getString("driver_reference"),
                    status = AssignmentStatus.valueOf(rs.getString("status")),
                    statusChangedAt = rs.getTimestamp("status_changed_at")
                )
            },
            order.orderId
        )

    private fun reconstruct(
        id: String,
        orderReference: String,
        driverReference: String,
        status: AssignmentStatus,
        statusChangedAt: Timestamp?
    ): Assignment {
        val constructor = Assignment::class.java.getDeclaredConstructor(
            String::class.java,
            String::class.java,
            String::class.java
        )
        constructor.isAccessible = true
        val assignment = constructor.newInstance(id, orderReference, driverReference)
        val at: Instant = statusChangedAt?.toInstant() ?: Instant.now()
        when (status) {
            AssignmentStatus.CREATED -> {}
            AssignmentStatus.ACCEPTED -> assignment.accept(at)
            AssignmentStatus.ARRIVED -> assignment.arrive(at)
            AssignmentStatus.IN_PROGRESS -> {
                assignment.arrive()
                assignment.start(at)
            }
            AssignmentStatus.COMPLETED -> {
                assignment.arrive()
                assignment.start()
                assignment.complete(at)
            }
        }
        return assignment
    }
}

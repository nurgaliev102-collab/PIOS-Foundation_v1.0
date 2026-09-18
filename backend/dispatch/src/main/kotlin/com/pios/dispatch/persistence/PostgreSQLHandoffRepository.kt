package com.pios.dispatch.persistence

import com.pios.dispatch.application.HandoffRepository
import com.pios.dispatch.domain.AssignmentId
import com.pios.dispatch.domain.Handoff
import com.pios.dispatch.domain.HandoffId
import com.pios.dispatch.domain.HandoffStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

/**
 * The PostgreSQL-backed adapter for [HandoffRepository] (D-07, Handoff
 * Protocol) — mirroring [PostgreSQLTripRepository]/[PostgreSQLProposalRepository]'s
 * own conventions exactly: plain JDBC via [JdbcTemplate], no ORM, no
 * entity annotations. Persists exactly the Handoff logical entity this
 * module owns, in its own database (`pios_dispatch`).
 *
 * ## Reconstruction and the private constructor
 *
 * [Handoff]'s constructor is private, reachable only through
 * [Handoff.propose], which always starts a new Handoff at
 * [HandoffStatus.PROPOSED]. Reconstructing a persisted, possibly-further-
 * resolved [Handoff] therefore takes two steps, mirroring
 * [PostgreSQLTripRepository.reconstruct] exactly:
 *
 * 1. Reflectively invoke the private constructor with the persisted id,
 *    assignment id, order reference, original driver, substitute driver,
 *    passenger reference (nullable, but still erases to
 *    `String::class.java` here — [PassengerReference] wraps an already-
 *    nullable `String`, the exact same empirically-verified behavior
 *    [PostgreSQLProposalRepository.reconstruct] already documents for its
 *    own identical field), proposed-at timestamp, and isTest flag.
 * 2. Replay however many of [Handoff.acceptSubstitute]/[Handoff.commit]/
 *    [Handoff.refuse]/[Handoff.withdraw] are needed to reach the
 *    persisted status, each given its own real persisted timestamp.
 */
@Repository
class PostgreSQLHandoffRepository(
    private val jdbcTemplate: JdbcTemplate
) : HandoffRepository {

    private val selectColumns = """
        id, assignment_id, order_reference, original_driver_reference, substitute_driver_reference,
        passenger_reference, status, proposed_at, substitute_accepted_at, resolved_at, is_test
    """.trimIndent()

    override fun save(handoff: Handoff) {
        jdbcTemplate.update(
            """
            INSERT INTO handoffs (
                id, assignment_id, order_reference, original_driver_reference, substitute_driver_reference,
                passenger_reference, status, proposed_at, substitute_accepted_at, resolved_at, is_test
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                status = EXCLUDED.status,
                substitute_accepted_at = EXCLUDED.substitute_accepted_at,
                resolved_at = EXCLUDED.resolved_at
            """.trimIndent(),
            handoff.id.value,
            handoff.assignmentId.value,
            handoff.order.orderId,
            handoff.originalDriver.driverId,
            handoff.substituteDriver.driverId,
            handoff.passenger?.passengerId,
            handoff.status.name,
            Timestamp.from(handoff.proposedAt),
            handoff.substituteAcceptedAt?.let { Timestamp.from(it) },
            handoff.resolvedAt?.let { Timestamp.from(it) },
            handoff.isTest
        )
    }

    override fun findById(id: HandoffId): Handoff? =
        jdbcTemplate.query(
            "SELECT $selectColumns FROM handoffs WHERE id = ?",
            { rs, _ -> reconstruct(rs) },
            id.value
        ).firstOrNull()

    override fun findByAssignmentId(assignmentId: AssignmentId): List<Handoff> =
        jdbcTemplate.query(
            "SELECT $selectColumns FROM handoffs WHERE assignment_id = ?",
            { rs, _ -> reconstruct(rs) },
            assignmentId.value
        )

    override fun findBySubstituteDriver(driverId: String): List<Handoff> =
        jdbcTemplate.query(
            "SELECT $selectColumns FROM handoffs WHERE substitute_driver_reference = ? " +
                "AND status IN ('PROPOSED', 'SUBSTITUTE_ACCEPTED') ORDER BY proposed_at DESC",
            { rs, _ -> reconstruct(rs) },
            driverId
        )

    override fun findByOriginalDriver(driverId: String): List<Handoff> =
        jdbcTemplate.query(
            "SELECT $selectColumns FROM handoffs WHERE original_driver_reference = ? ORDER BY proposed_at DESC",
            { rs, _ -> reconstruct(rs) },
            driverId
        )

    private fun reconstruct(rs: ResultSet): Handoff {
        val constructor = Handoff::class.java.getDeclaredConstructor(
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            Instant::class.java,
            Boolean::class.java
        )
        constructor.isAccessible = true
        val handoff = constructor.newInstance(
            rs.getString("id"),
            rs.getString("assignment_id"),
            rs.getString("order_reference"),
            rs.getString("original_driver_reference"),
            rs.getString("substitute_driver_reference"),
            rs.getString("passenger_reference"),
            rs.getTimestamp("proposed_at").toInstant(),
            rs.getBoolean("is_test")
        )
        val status = HandoffStatus.valueOf(rs.getString("status"))
        val substituteAcceptedAt: Instant? = rs.getTimestamp("substitute_accepted_at")?.toInstant()
        val resolvedAt: Instant = rs.getTimestamp("resolved_at")?.toInstant() ?: Instant.now()
        when (status) {
            HandoffStatus.PROPOSED -> {}
            HandoffStatus.SUBSTITUTE_ACCEPTED -> handoff.acceptSubstitute(substituteAcceptedAt ?: resolvedAt)
            HandoffStatus.COMMITTED -> {
                handoff.acceptSubstitute(substituteAcceptedAt ?: resolvedAt)
                handoff.commit(resolvedAt)
            }
            HandoffStatus.REFUSED -> {
                if (substituteAcceptedAt != null) handoff.acceptSubstitute(substituteAcceptedAt)
                handoff.refuse(resolvedAt)
            }
            HandoffStatus.WITHDRAWN -> {
                if (substituteAcceptedAt != null) handoff.acceptSubstitute(substituteAcceptedAt)
                handoff.withdraw(resolvedAt)
            }
        }
        return handoff
    }
}

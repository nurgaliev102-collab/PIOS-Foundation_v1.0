package com.pios.dispatch.persistence

import com.pios.dispatch.application.ProposalRepository
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.domain.Proposal
import com.pios.dispatch.domain.ProposalId
import com.pios.dispatch.domain.ProposalStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

/**
 * The PostgreSQL-backed adapter for [ProposalRepository], mirroring
 * [PostgreSQLAssignmentRepository] exactly. Persists exactly the Proposal
 * logical entity this module owns, in `pios_dispatch` — no other
 * module's information, no shared schema. [OrderReference] and
 * [DriverReference] are persisted as plain identifiers only, exactly as
 * for Assignment.
 *
 * Uses plain JDBC via Spring's [JdbcTemplate] only — no ORM, no entity
 * annotations. [Proposal] itself carries no persistence knowledge
 * whatsoever; this class owns database mapping and storage operations
 * exclusively.
 *
 * ## Reconstruction and the private constructor
 *
 * [Proposal]'s constructor is private, reachable only through
 * [Proposal.propose] — which always starts a new proposal at
 * [ProposalStatus.OPEN] and does not accept a status parameter.
 * Reconstructing a persisted, possibly-already-resolved [Proposal]
 * therefore takes the same two steps already proven for
 * [PostgreSQLAssignmentRepository]:
 *
 * 1. Reflectively invoke the private constructor with the persisted id,
 *    order reference, and driver reference, yielding a proposal in its
 *    initial [ProposalStatus.OPEN] state.
 * 2. If the persisted row's status is [ProposalStatus.ACCEPTED],
 *    [ProposalStatus.DECLINED], or [ProposalStatus.LAPSED], call the
 *    aggregate's own public [Proposal.accept], [Proposal.decline], or
 *    [Proposal.lapse] to advance it — no further reflection needed.
 *
 * This does not bypass any invariant: step 1's private constructor
 * performs no validation beyond what already-persisted, previously-valid
 * data satisfies by construction, and step 2 uses the aggregate's own
 * business logic unmodified.
 */
@Repository
class PostgreSQLProposalRepository(
    private val jdbcTemplate: JdbcTemplate
) : ProposalRepository {

    override fun save(proposal: Proposal) {
        jdbcTemplate.update(
            """
            INSERT INTO proposals (id, order_reference, driver_reference, status)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status
            """.trimIndent(),
            proposal.id.value,
            proposal.order.orderId,
            proposal.driver.driverId,
            proposal.status.name
        )
    }

    override fun findById(id: ProposalId): Proposal? {
        val rows = jdbcTemplate.query(
            "SELECT id, order_reference, driver_reference, status FROM proposals WHERE id = ?",
            { rs, _ ->
                reconstruct(
                    id = rs.getString("id"),
                    orderReference = rs.getString("order_reference"),
                    driverReference = rs.getString("driver_reference"),
                    status = ProposalStatus.valueOf(rs.getString("status"))
                )
            },
            id.value
        )
        return rows.firstOrNull()
    }

    override fun findByOrder(order: OrderReference): List<Proposal> =
        jdbcTemplate.query(
            "SELECT id, order_reference, driver_reference, status FROM proposals WHERE order_reference = ?",
            { rs, _ ->
                reconstruct(
                    id = rs.getString("id"),
                    orderReference = rs.getString("order_reference"),
                    driverReference = rs.getString("driver_reference"),
                    status = ProposalStatus.valueOf(rs.getString("status"))
                )
            },
            order.orderId
        )

    override fun findByDriver(driver: DriverReference): List<Proposal> =
        jdbcTemplate.query(
            "SELECT id, order_reference, driver_reference, status FROM proposals WHERE driver_reference = ?",
            { rs, _ ->
                reconstruct(
                    id = rs.getString("id"),
                    orderReference = rs.getString("order_reference"),
                    driverReference = rs.getString("driver_reference"),
                    status = ProposalStatus.valueOf(rs.getString("status"))
                )
            },
            driver.driverId
        )

    private fun reconstruct(id: String, orderReference: String, driverReference: String, status: ProposalStatus): Proposal {
        val constructor = Proposal::class.java.getDeclaredConstructor(
            String::class.java,
            String::class.java,
            String::class.java
        )
        constructor.isAccessible = true
        val proposal = constructor.newInstance(id, orderReference, driverReference)
        when (status) {
            ProposalStatus.ACCEPTED -> proposal.accept()
            ProposalStatus.DECLINED -> proposal.decline()
            ProposalStatus.LAPSED -> proposal.lapse()
            ProposalStatus.OPEN -> Unit
        }
        return proposal
    }
}

package com.pios.dispatch.application

import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.domain.Proposal
import com.pios.dispatch.domain.ProposalId

/**
 * A no-op [ProposalRepository]. Used only as
 * [DispatchAssignmentApplicationService]'s default (ADR-065, Decision item
 * 2) when no real Proposal persistence is wired in — for example, existing
 * tests exercising Assignment/Trip lifecycle logic unrelated to a stated
 * price, constructed before this ADR added the `findByOrder` lookup to
 * [DispatchAssignmentApplicationService.completeAssignment]. Mirrors
 * [NoOpTripRepository] exactly. Never used in a running application: Spring
 * always finds and injects the real
 * [com.pios.dispatch.persistence.PostgreSQLProposalRepository] bean there
 * instead.
 */
object NoOpProposalRepository : ProposalRepository {
    override fun save(proposal: Proposal) = Unit
    override fun findById(id: ProposalId): Proposal? = null
    override fun findByOrder(order: OrderReference): List<Proposal> = emptyList()
    override fun findByDriver(driver: DriverReference): List<Proposal> = emptyList()
    override fun findOpen(): List<Proposal> = emptyList()
}

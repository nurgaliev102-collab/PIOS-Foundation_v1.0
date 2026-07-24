package com.pios.dispatch.application

import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.domain.Proposal
import com.pios.dispatch.domain.ProposalId

/**
 * The persistence boundary for the Proposal aggregate (ADR-035;
 * Implementation Design — Proposal Aggregate, Section 4). Mirrors
 * [AssignmentRepository] exactly: it names only what Dispatch's own
 * domain needs — saving and loading a Proposal by its identity, and
 * finding every Proposal already recorded for a given order or driver —
 * and says nothing about how or where a Proposal is actually stored. No
 * storage technology, framework, or persistence implementation detail is
 * named here or anywhere in the domain layer.
 *
 * [findByOrder] is present from this interface's introduction, unlike
 * [AssignmentRepository]'s own [AssignmentRepository.findByOrder] (added
 * later, by Sprint FR-004): [ProposalApplicationService.handle] needs it
 * from the start to enforce the Root Invariant against a real persisted
 * store, per this sprint's own specified flow.
 *
 * [findByDriver] was added by Sprint IMPLEMENTATION-005 (Driver Proposal
 * MVP): the Driver-facing "list open proposals" capability needs to look
 * up a driver's own proposals by driver identity, a query no existing
 * caller needed before this sprint's UI.
 *
 * Only Dispatch persists or changes Proposal information
 * (PERSISTENCE_ARCHITECTURE.md Section 3's "Domain-Owned Persistence"
 * principle, applied identically to this second logical entity); no
 * other module implements or depends on this interface.
 */
interface ProposalRepository {
    fun save(proposal: Proposal)
    fun findById(id: ProposalId): Proposal?
    fun findByOrder(order: OrderReference): List<Proposal>
    fun findByDriver(driver: DriverReference): List<Proposal>
}

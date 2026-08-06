package com.pios.dispatch.persistence

import com.pios.dispatch.application.ProposalRepository
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.domain.Proposal
import com.pios.dispatch.domain.ProposalId
import com.pios.dispatch.domain.ProposalStatus
import java.util.concurrent.ConcurrentHashMap

/**
 * A minimal in-memory adapter for [ProposalRepository], mirroring
 * [InMemoryAssignmentRepository] exactly. Not a production storage
 * mechanism: state is held only in this process's memory and is lost
 * when it ends.
 */
class InMemoryProposalRepository : ProposalRepository {
    private val store = ConcurrentHashMap<ProposalId, Proposal>()

    override fun save(proposal: Proposal) {
        store[proposal.id] = proposal
    }

    override fun findById(id: ProposalId): Proposal? = store[id]

    override fun findByOrder(order: OrderReference): List<Proposal> = store.values.filter { it.order == order }

    override fun findByDriver(driver: DriverReference): List<Proposal> = store.values.filter { it.driver == driver }

    override fun findOpen(): List<Proposal> = store.values.filter { it.status == ProposalStatus.OPEN }
}

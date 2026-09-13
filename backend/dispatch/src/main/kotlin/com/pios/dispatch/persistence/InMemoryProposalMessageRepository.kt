package com.pios.dispatch.persistence

import com.pios.dispatch.application.ProposalMessageRepository
import com.pios.dispatch.domain.ProposalId
import com.pios.dispatch.domain.ProposalMessage
import java.util.concurrent.ConcurrentHashMap

/**
 * A minimal in-memory adapter for [ProposalMessageRepository], mirroring
 * [InMemoryProposalRepository] exactly. Not a production storage
 * mechanism: state is held only in this process's memory and is lost
 * when it ends.
 */
class InMemoryProposalMessageRepository : ProposalMessageRepository {
    private val store = ConcurrentHashMap<ProposalId, MutableList<ProposalMessage>>()

    override fun save(message: ProposalMessage) {
        store.computeIfAbsent(message.proposalId) { mutableListOf() }.add(message)
    }

    override fun findByProposal(proposalId: ProposalId): List<ProposalMessage> =
        store[proposalId]?.sortedBy { it.sentAt } ?: emptyList()
}

package com.pios.dispatch.application

import com.pios.dispatch.domain.ProposalId
import com.pios.dispatch.domain.ProposalMessage

/**
 * The persistence boundary for [ProposalMessage] (Minimal In-Ride
 * Messaging MVP). Mirrors [ProposalRepository]'s own shape: names only
 * what Dispatch's own domain needs — saving a message and finding every
 * message already recorded for one specific proposal — and says nothing
 * about how or where it is actually stored.
 *
 * [findByProposal] is the only read this MVP needs: a passenger's or
 * driver's own screen already knows which [ProposalId] it is showing
 * (the same one every other Proposal action on that screen already acts
 * on), so no order-level or driver-level enumeration exists here —
 * deliberately narrower than [ProposalRepository]'s own [findByOrder]/
 * [findByDriver], since a message thread is scoped to one proposal, not
 * an order's or driver's entire history (see [ProposalMessage]'s own
 * KDoc for why).
 */
interface ProposalMessageRepository {
    fun save(message: ProposalMessage)

    /** Every message for [proposalId], oldest first (chat-thread order) — never another proposal's own messages. */
    fun findByProposal(proposalId: ProposalId): List<ProposalMessage>
}

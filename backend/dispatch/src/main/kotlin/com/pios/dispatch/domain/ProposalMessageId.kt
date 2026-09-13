package com.pios.dispatch.domain

/**
 * Identity of a ProposalMessage (Minimal In-Ride Messaging MVP) — mirrors
 * [ProposalId]'s own shape exactly: an independent business identity, not
 * derived from the proposal it belongs to.
 */
@JvmInline
value class ProposalMessageId(val value: String) {
    init {
        require(value.isNotBlank()) { "ProposalMessageId must not be blank" }
    }
}

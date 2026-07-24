package com.pios.dispatch.domain

/**
 * Identity of a Proposal aggregate (Domain Design — Pre-Commitment
 * Aggregate, Step 3: an independent business identity, not derived from
 * the order or driver it references).
 */
@JvmInline
value class ProposalId(val value: String) {
    init {
        require(value.isNotBlank()) { "ProposalId must not be blank" }
    }
}

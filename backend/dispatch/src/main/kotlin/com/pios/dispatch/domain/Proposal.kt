package com.pios.dispatch.domain

import java.util.UUID

/**
 * The Proposal aggregate, within Dispatch's exclusive ownership
 * (ADR-002, ADR-005, ADR-019, ADR-035).
 *
 * Represents a single proposed pairing between an order and a driver,
 * awaiting that driver's own resolution (Product Decision: Opportunity
 * Before Assignment v1.0; ADR-035 Part 1). This is deliberately not
 * Assignment: per ADR-035 and DOMAIN_MODEL.md Section 4's own revised
 * Purpose, Assignment represents an already-settled outcome, created only
 * once this fact has resolved positively — never a pending one.
 *
 * Invariant: an order may have at most one open proposal at any time
 * (Domain Design — Pre-Commitment Aggregate, Root Invariant). Unlike
 * Assignment's own invariant, which treats every existing Assignment as
 * active regardless of status, this invariant considers only proposals
 * still in [ProposalStatus.OPEN] — a proposal already resolved (accepted,
 * declined, or lapsed) does not block a new one for the same order, since
 * its own resolution is exactly what frees the order for reconsideration
 * (Implementation Design — Proposal Aggregate, Section 2).
 *
 * [OrderReference] and [DriverReference] are reused unchanged from
 * Assignment's own module — narrow references, never another module's
 * domain type (ADR-005, ADR-009).
 */
class Proposal private constructor(
    val id: ProposalId,
    val order: OrderReference,
    val driver: DriverReference
) {
    var status: ProposalStatus = ProposalStatus.OPEN
        private set

    /**
     * Accepts this proposal, per the driver's own confirming act. Only an
     * [ProposalStatus.OPEN] proposal may be accepted; an already-resolved
     * proposal cannot be accepted again.
     */
    fun accept(): ProposalAccepted {
        check(status == ProposalStatus.OPEN) {
            "Proposal ${id.value} cannot be accepted from status $status"
        }
        status = ProposalStatus.ACCEPTED
        return ProposalAccepted(orderId = order, driverId = driver)
    }

    /**
     * Declines this proposal, per the driver's own refusing act. Only an
     * [ProposalStatus.OPEN] proposal may be declined; no obligation exists
     * before acceptance, so a decline violates nothing
     * (PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md Section 3).
     */
    fun decline(): ProposalDeclined {
        check(status == ProposalStatus.OPEN) {
            "Proposal ${id.value} cannot be declined from status $status"
        }
        status = ProposalStatus.DECLINED
        return ProposalDeclined(orderId = order, driverId = driver)
    }

    /**
     * Marks this proposal as lapsed: no response arrived while waiting
     * remained appropriate. Only an [ProposalStatus.OPEN] proposal may
     * lapse. The mechanism by which lapsing is recognized (a timeout, a
     * policy decision) is not this aggregate's concern (Implementation
     * Design — Proposal Aggregate, Section 6) — this method only records
     * the fact once it is already established elsewhere.
     */
    fun lapse(): ProposalLapsed {
        check(status == ProposalStatus.OPEN) {
            "Proposal ${id.value} cannot lapse from status $status"
        }
        status = ProposalStatus.LAPSED
        return ProposalLapsed(orderId = order, driverId = driver)
    }

    companion object {
        /**
         * Proposes [driver] for [order], per the Propose Driver command
         * (Domain Design — Pre-Commitment Aggregate, Step 6). Rejects the
         * proposal if [existingProposals] already contains an open
         * proposal for the same [order] (Root Invariant, Step 4).
         *
         * The specific criteria for selecting [driver] are decided
         * elsewhere (Assignment Policy, ADR-034) — this factory only
         * records a decision already made, exactly as `Assignment.create()`
         * already does for its own, later step.
         */
        fun propose(
            order: OrderReference,
            driver: DriverReference,
            existingProposals: Collection<Proposal> = emptyList()
        ): ProposalCreated {
            check(existingProposals.none { it.order == order && it.status == ProposalStatus.OPEN }) {
                "Order ${order.orderId} already has an open proposal"
            }
            val proposal = Proposal(
                id = ProposalId(UUID.randomUUID().toString()),
                order = order,
                driver = driver
            )
            val event = OrderProposed(orderId = order, driverId = driver)
            return ProposalCreated(proposal = proposal, event = event)
        }
    }
}

/**
 * The result of proposing a driver: the new [Proposal] and the
 * [OrderProposed] event recording its creation.
 */
data class ProposalCreated(val proposal: Proposal, val event: OrderProposed)

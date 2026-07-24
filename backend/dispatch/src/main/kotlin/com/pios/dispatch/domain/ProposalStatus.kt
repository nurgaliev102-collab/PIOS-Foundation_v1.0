package com.pios.dispatch.domain

/**
 * The current state of a Proposal within its lifecycle (Domain Design —
 * Pre-Commitment Aggregate, Step 5).
 *
 * [OPEN] — proposed, awaiting the driver's own resolution.
 * [ACCEPTED] — the driver confirmed; the precondition for Assignment's
 * own creation (ADR-035).
 * [DECLINED] — the driver actively refused, before any obligation existed
 * (PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md Section 3).
 * [LAPSED] — no response arrived while waiting remained appropriate;
 * distinct from [DECLINED] as a passive, not an actor-driven, outcome
 * (PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md Section 9).
 */
enum class ProposalStatus {
    OPEN,
    ACCEPTED,
    DECLINED,
    LAPSED
}

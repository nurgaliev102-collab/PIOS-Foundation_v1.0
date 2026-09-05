package com.pios.dispatch.domain

/**
 * The current state of a Proposal within its lifecycle (Domain Design —
 * Pre-Commitment Aggregate, Step 5).
 *
 * [OPEN] — proposed, awaiting the driver's own resolution.
 * [PRICE_PROPOSED] — the driver has stated a price for this ride and is
 * awaiting the passenger's own decision on it (Product Owner instruction,
 * 2026-09-05: a driver must name a price before a ride is confirmed, and
 * the passenger must agree to it). Distinct from [ACCEPTED]: nothing is
 * yet settled — no Assignment exists — until the passenger confirms.
 * [ACCEPTED] — both the driver's price and the passenger's agreement to
 * it are settled; the precondition for Assignment's own creation
 * (ADR-035).
 * [DECLINED] — the driver actively refused, before any obligation existed
 * (PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md Section 3).
 * [LAPSED] — no response arrived while waiting remained appropriate;
 * distinct from [DECLINED] as a passive, not an actor-driven, outcome
 * (PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md Section 9).
 * [WITHDRAWN] — the order this proposal was for has been cancelled before
 * any driver acceptance (ADR-053, Proposal Resolution on Order
 * Cancellation, Part 2). Deliberately not named `CANCELLED`, to keep
 * Proposal's own resolved fact conceptually distinct from
 * `Order.status`'s own `CANCELLED` value, even though the two are
 * causally linked (ADR-053 Part 2, citing ADR-041 Decision item 2: "two
 * facts owned by two different modules that happen to be causally linked,
 * not one fact stored twice"). The actor represented is Order Management,
 * on the order's own behalf — never the passenger or driver directly
 * (ADR-053 Part 3).
 */
enum class ProposalStatus {
    OPEN,
    PRICE_PROPOSED,
    ACCEPTED,
    DECLINED,
    LAPSED,
    WITHDRAWN
}

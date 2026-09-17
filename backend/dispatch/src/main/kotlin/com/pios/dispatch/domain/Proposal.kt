package com.pios.dispatch.domain

import java.time.Instant
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
 * declined, lapsed, or withdrawn — ADR-053) does not block a new one for the same order, since
 * its own resolution is exactly what frees the order for reconsideration
 * (Implementation Design — Proposal Aggregate, Section 2).
 *
 * [OrderReference] and [DriverReference] are reused unchanged from
 * Assignment's own module — narrow references, never another module's
 * domain type (ADR-005, ADR-009).
 *
 * [createdAt] (ADR-043, Owner Control Center — Observation Boundary) is
 * the moment this proposal was created — a genuine constructor parameter,
 * unlike [respondedAt] below, since it is fixed at construction and
 * independent of [status]. Set by [propose] for every new proposal; `null`
 * only for a proposal reconstructed from a row persisted before
 * `V7__proposal_timestamps.sql` existed
 * ([com.pios.dispatch.persistence.PostgreSQLProposalRepository]'s own
 * reflective constructor lookup is widened to match, per that class's own
 * KDoc on this constructor being reflectively invoked).
 *
 * [isTest] (Owner Control Center test/production data separation,
 * 2026-08-17) marks a proposal deliberately created by an automated or
 * manual technical verification rather than real dispatch activity.
 * Defaults to `false`. Set once, at [propose], and never changes
 * afterward. **Not** derived from whether [order]/[driver] happen to
 * reference a test order or test driver — Dispatch does not own that
 * information (reference-not-ownership, ADR-005/009/019) and structurally
 * cannot read it — it is only ever the caller's own explicit declaration
 * at proposal-creation time.
 *
 * [passengerReference] (ADR-066, Proposal Participant Authorization) is
 * this order's authenticated passenger, per the same reference-only
 * convention [PassengerReference] already established for First Refusal
 * (ADR-062) — a bare identifier only, never a name, phone, or address. A
 * genuine constructor parameter, like [createdAt], since it is fixed at
 * construction and independent of [status]: it is who this proposal was
 * created for, not a fact that changes as the proposal resolves. `null`
 * only for a proposal reconstructed from a row persisted before
 * `V15__proposal_passenger_reference.sql` existed — never an ordinary
 * permitted state for a newly created proposal, since [propose] and every
 * real caller of it now supply one
 * ([com.pios.dispatch.persistence.PostgreSQLProposalRepository]'s own
 * reflective constructor lookup is widened to match, per that class's own
 * KDoc on this constructor being reflectively invoked).
 */
class Proposal private constructor(
    val id: ProposalId,
    val order: OrderReference,
    val driver: DriverReference,
    val createdAt: Instant? = null,
    val isTest: Boolean = false,
    val passengerReference: PassengerReference? = null
) {
    var status: ProposalStatus = ProposalStatus.OPEN
        private set

    /**
     * The moment this proposal left [ProposalStatus.OPEN] — by
     * acceptance, decline, or lapse (ADR-043, Owner Control Center —
     * Observation Boundary). `null` until one of those three happens.
     * Deliberately **not** a primary-constructor parameter, the same
     * reasoning [statedPrice]'s own KDoc gives: it is set only later, by
     * [accept]/[decline]/[lapse], each of which now takes the same
     * optional `at` parameter [Assignment]'s own transition methods
     * already established (ADR-040 precedent) so
     * [com.pios.dispatch.persistence.PostgreSQLProposalRepository.reconstruct]
     * can replay a persisted `responded_at` instead of an unobserved
     * `now()`.
     */
    var respondedAt: Instant? = null
        private set

    /**
     * The price the driver stated when accepting this proposal (ADR-042,
     * Decision Revised R2/R5) — `null` for every proposal that has not
     * (yet, or ever) been accepted with one supplied. A plain, opaque,
     * unvalidated string: PIOS does not compute, parse, compare, or assign
     * any meaning to it beyond carrying whatever the driver typed, exactly
     * as [com.pios.ordermanagement.domain.Order.destination]'s own
     * documented reasoning already establishes for a different optional
     * field (ADR-042 R5, Open Question 2).
     *
     * Deliberately **not** a primary-constructor parameter — see this
     * class's own KDoc precedent in [Assignment.statusChangedAt]:
     * [com.pios.dispatch.persistence.PostgreSQLProposalRepository.reconstruct]
     * restores a persisted [Proposal] by reflectively invoking this class's
     * private constructor by parameter count and type; widening that
     * constructor would break that lookup at runtime, silently, in a path
     * no unit test that avoids the database would catch. Set exactly once,
     * by [accept], and never modified afterwards — [decline] and [lapse]
     * never touch it (ADR-042 Open Question 6: no amount on a refusal).
     */
    var statedPrice: String? = null
        private set

    /**
     * The whole number of minutes the driver stated it would take them to
     * reach the passenger, given at the moment of accepting this proposal
     * (ADR-057, Decision items 1-3) — `null` for every proposal that has
     * not (yet, or ever) been accepted with one supplied. PIOS records this
     * value verbatim; it computes, estimates, derives, compares, or ranks
     * nothing from it (ADR-057 Decision item 5).
     *
     * Deliberately **not** a primary-constructor parameter, for exactly the
     * reason [statedPrice]'s own KDoc gives:
     * [com.pios.dispatch.persistence.PostgreSQLProposalRepository.reconstruct]
     * restores a persisted [Proposal] by reflectively invoking this class's
     * private constructor by parameter count and type; widening that
     * constructor would break that lookup at runtime, silently. Set exactly
     * once, by [accept], and never modified afterwards — [decline] and
     * [lapse] never touch it, mirroring [statedPrice]'s own refusal
     * exclusion (ADR-057 Decision item 2).
     */
    var statedEtaMinutes: Int? = null
        private set

    /**
     * Accepts this proposal, per the driver's own confirming act. Only an
     * [ProposalStatus.OPEN] proposal may be accepted; an already-resolved
     * proposal cannot be accepted again.
     *
     * [statedPrice] is the amount the driver stated at the moment of
     * acceptance (ADR-042, Decision Revised R2) — optional, defaulting to
     * `null` so every existing caller that does not supply one keeps
     * working unchanged. Also accepted here (not only as a fresh value) so
     * [com.pios.dispatch.persistence.PostgreSQLProposalRepository.reconstruct]
     * can replay a previously persisted amount during reconstruction,
     * mirroring [Assignment.accept]'s own `at` parameter precedent
     * (ADR-040).
     *
     * [statedEtaMinutes] is the driver's stated time to pickup, in minutes
     * (ADR-057, Decision item 3) — optional, same reconstruction-replay
     * reasoning as [statedPrice].
     *
     * [at] defaults to the current time for real callers; overridable so
     * [com.pios.dispatch.persistence.PostgreSQLProposalRepository] can
     * replay this transition during reconstruction with the originally
     * persisted `responded_at` instead of the moment of the read (ADR-043,
     * mirroring [Assignment]'s own already-established `at` precedent).
     */
    fun accept(
        statedPrice: String? = null,
        statedEtaMinutes: Int? = null,
        at: Instant = Instant.now()
    ): ProposalAccepted {
        check(status == ProposalStatus.OPEN) {
            "Proposal ${id.value} cannot be accepted from status $status"
        }
        status = ProposalStatus.ACCEPTED
        this.statedPrice = statedPrice
        this.statedEtaMinutes = statedEtaMinutes
        this.respondedAt = at
        return ProposalAccepted(orderId = order, driverId = driver)
    }

    /**
     * States a price for this ride, per the driver's own act (Product
     * Owner instruction, 2026-09-05: a driver must name a price before a
     * ride is confirmed, and the passenger must separately agree to it).
     * Only an [ProposalStatus.OPEN] proposal may have a price proposed.
     * Unlike [accept]'s own optional [statedPrice], [statedPrice] here is
     * mandatory and must be non-blank — this method exists specifically
     * because a price is now required before a ride can proceed, not
     * merely offered.
     *
     * Does not create an Assignment and settles nothing by itself — only
     * [confirmPrice], the passenger's own separate act, does that
     * (mirroring why [Proposal] itself is not [Assignment]: ADR-035, "an
     * already-settled outcome, created only once this fact has resolved
     * positively — never a pending one").
     *
     * [at] defaults to the current time; overridable for reconstruction
     * replay, mirroring [accept]'s own `at` parameter (ADR-043).
     */
    fun proposePrice(
        statedPrice: String,
        statedEtaMinutes: Int? = null,
        at: Instant = Instant.now()
    ): ProposalPriceProposed {
        check(status == ProposalStatus.OPEN) {
            "Proposal ${id.value} cannot have a price proposed from status $status"
        }
        require(statedPrice.isNotBlank()) { "statedPrice must not be blank" }
        status = ProposalStatus.PRICE_PROPOSED
        this.statedPrice = statedPrice
        this.statedEtaMinutes = statedEtaMinutes
        this.respondedAt = at
        return ProposalPriceProposed(orderId = order, driverId = driver)
    }

    /**
     * Confirms the price already stated by [proposePrice], per the
     * passenger's own agreeing act — the precondition for Assignment's own
     * creation (ADR-035), replacing [accept]'s own role in the real
     * product flow now that a price must be agreed first. Only a
     * [ProposalStatus.PRICE_PROPOSED] proposal may be confirmed. Does not
     * itself touch [statedPrice]/[statedEtaMinutes] — both are already set
     * by [proposePrice] and are not renegotiated here.
     *
     * Returns [ProposalAccepted] rather than a distinct type: from this
     * point on the proposal is settled exactly as an [accept]ed one always
     * was, and [ProposalAssignmentOrchestrationService] reacts to the two
     * identically.
     *
     * [at] defaults to the current time; overridable for reconstruction
     * replay, mirroring [accept]'s own `at` parameter (ADR-043).
     */
    fun confirmPrice(at: Instant = Instant.now()): ProposalAccepted {
        check(status == ProposalStatus.PRICE_PROPOSED) {
            "Proposal ${id.value} cannot have its price confirmed from status $status"
        }
        status = ProposalStatus.ACCEPTED
        this.respondedAt = at
        return ProposalAccepted(orderId = order, driverId = driver)
    }

    /**
     * Refuses the price already stated by [proposePrice], per the
     * passenger's own refusing act (Product Owner instruction, 2026-09-05:
     * the request simply closes — no renegotiation, no automatic reroute
     * to another driver, matching this product's own "PIOS does not
     * substitute a random driver for the one a client came to" principle).
     * Only a [ProposalStatus.PRICE_PROPOSED] proposal may decline its
     * price.
     *
     * Returns [ProposalDeclined] rather than a distinct type: from the
     * passenger's and driver's own point of view this ride simply did not
     * happen, exactly like an outright [decline] — [statedPrice]/
     * [statedEtaMinutes] are left as already recorded (the historical fact
     * "this was quoted, then refused" is worth keeping, unlike [decline]'s
     * own case where no price was ever named at all).
     *
     * [at] defaults to the current time; overridable for reconstruction
     * replay, mirroring [accept]'s own `at` parameter (ADR-043).
     */
    fun declinePriceProposal(at: Instant = Instant.now()): ProposalDeclined {
        check(status == ProposalStatus.PRICE_PROPOSED) {
            "Proposal ${id.value} cannot decline a price from status $status"
        }
        status = ProposalStatus.DECLINED
        this.respondedAt = at
        return ProposalDeclined(orderId = order, driverId = driver)
    }

    /**
     * Declines this proposal, per the driver's own refusing act. Only an
     * [ProposalStatus.OPEN] proposal may be declined; no obligation exists
     * before acceptance, so a decline violates nothing
     * (PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md Section 3).
     *
     * [at] defaults to the current time; overridable for reconstruction
     * replay, mirroring [accept]'s own `at` parameter (ADR-043).
     */
    fun decline(at: Instant = Instant.now()): ProposalDeclined {
        check(status == ProposalStatus.OPEN) {
            "Proposal ${id.value} cannot be declined from status $status"
        }
        status = ProposalStatus.DECLINED
        this.respondedAt = at
        return ProposalDeclined(orderId = order, driverId = driver)
    }

    /**
     * Marks this proposal as lapsed: no response arrived while waiting
     * remained appropriate. Only an [ProposalStatus.OPEN] proposal may
     * lapse. The mechanism by which lapsing is recognized (a timeout, a
     * policy decision) is not this aggregate's concern (Implementation
     * Design — Proposal Aggregate, Section 6) — this method only records
     * the fact once it is already established elsewhere.
     *
     * [at] defaults to the current time; overridable for reconstruction
     * replay, mirroring [accept]'s own `at` parameter (ADR-043).
     */
    fun lapse(at: Instant = Instant.now()): ProposalLapsed {
        check(status == ProposalStatus.OPEN) {
            "Proposal ${id.value} cannot lapse from status $status"
        }
        status = ProposalStatus.LAPSED
        this.respondedAt = at
        return ProposalLapsed(orderId = order, driverId = driver)
    }

    /**
     * Withdraws this proposal because the order it was for has been
     * cancelled before any driver acceptance (ADR-053, Proposal
     * Resolution on Order Cancellation). Only an [ProposalStatus.OPEN]
     * proposal may be withdrawn — mirrors [lapse]'s own precondition
     * exactly, since both are non-actor-driven resolutions reaching this
     * aggregate from outside a driver's own act.
     *
     * [at] defaults to the current time; overridable for reconstruction
     * replay, mirroring [accept]/[decline]/[lapse]'s own `at` parameter
     * (ADR-043).
     */
    fun withdraw(at: Instant = Instant.now()): ProposalWithdrawn {
        check(status == ProposalStatus.OPEN || status == ProposalStatus.PRICE_PROPOSED) {
            "Proposal ${id.value} cannot be withdrawn from status $status"
        }
        status = ProposalStatus.WITHDRAWN
        this.respondedAt = at
        return ProposalWithdrawn(orderId = order, driverId = driver)
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
            existingProposals: Collection<Proposal> = emptyList(),
            isTest: Boolean = false,
            passengerReference: PassengerReference? = null
        ): ProposalCreated {
            check(existingProposals.none { it.order == order && it.status == ProposalStatus.OPEN }) {
                "Order ${order.orderId} already has an open proposal"
            }
            val proposal = Proposal(
                id = ProposalId(UUID.randomUUID().toString()),
                order = order,
                driver = driver,
                createdAt = Instant.now(),
                isTest = isTest,
                passengerReference = passengerReference
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

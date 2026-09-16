package com.pios.dispatch.application

import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.domain.PassengerReference
import java.time.Instant
import com.pios.dispatch.domain.Proposal
import org.springframework.stereotype.Service

/**
 * The smallest possible domain/application service determining whether a
 * newly created Proposal should first be offered to the passenger's
 * primary driver (ADR-062; Task 13's own runtime audit, Section 4/7/9;
 * Task 14, First Refusal Foundation).
 *
 * ## Why this is a new, separate service, not a change to [ProposalApplicationService.handle]
 *
 * At the time this service was introduced (Task 14), `ProposalApplicationService.handle`/
 * `ProposeDriverCommand` carried no passenger identity at all — every real
 * caller (`ProposalController.createProposal`, `Coordinator.tsx`,
 * `RideRequest.tsx`'s own direct-propose flow) supplied only
 * `{orderId, driverId}`. Determining "does this order's passenger have a
 * primary driver" structurally required a `PassengerReference` no existing
 * entry point provided. Task 14's own instructions forbade modifying
 * existing Proposal REST endpoints and forbade Coordinator/frontend
 * changes — so this service was additive: a new, separate, optional entry
 * point a *future* routing task could invoke once it had both a
 * `PassengerReference` and an `OrderReference` on hand. **ADR-066 (Proposal
 * Participant Authorization) is that future addition**: `ProposeDriverCommand`
 * now carries an optional `passengerReference`, and this service is its
 * strongest source — see [attempt]'s own call site below. This reasoning
 * paragraph is retained, corrected rather than deleted (`CLAUDE.md`: "Never
 * Delete Documentation"), so a future reader understands why this remained
 * a separate service even after the field it once lacked was added: nothing
 * about *why* First Refusal is its own service depended on
 * `ProposeDriverCommand` staying identity-free forever, only on Task 14's
 * own then-current constraints, which this later ADR lawfully supersedes.
 *
 * ## Reuses [ProposalApplicationService.handle]'s own existing availability gate
 *
 * Rather than duplicating driver-availability logic, [attempt] calls
 * straight into [ProposalApplicationService.handle] for the primary
 * driver — that method already rejects proposing an unavailable driver
 * (`IllegalStateException`, the pilot-readiness fix already in
 * production) and already enforces the Proposal aggregate's own
 * one-open-proposal-per-order invariant. [attempt] treats either failure
 * identically: First Refusal did not happen for this attempt, and no
 * Proposal was created — "preserve existing behavior"/"preserve the
 * ratified fallback behavior" (Task 14 Phase 3 items 4–5), satisfied by
 * changing nothing about what already exists, not by re-implementing it.
 * A pre-check against [driverAvailabilityRepository] is performed first,
 * purely so [FirstRefusalOutcome] can distinguish *why* First Refusal did
 * not happen (no primary vs. primary ineligible) for a caller/test that
 * cares — [handle]'s own check remains the sole authority actually
 * enforced, since availability could still change in the narrow window
 * between this pre-check and [handle]'s own atomic one; that race is
 * accepted exactly as it already is for `ProposalApplicationService`
 * itself (no new race is introduced here beyond what already exists).
 *
 * ## Duplicate attempts (Task 14 Phase 3 item 6)
 *
 * If [attempt] is called more than once for the same [order] while a
 * Proposal it created is still `OPEN`, the second call's own delegated
 * `ProposalApplicationService.handle` invocation fails on the Proposal
 * aggregate's own root invariant (at most one `OPEN` proposal per order)
 * — surfaced here as [FirstRefusalOutcome.AlreadyAttempted], never a
 * second Proposal. No new locking or deduplication mechanism is
 * introduced; the existing invariant is the mechanism.
 *
 * ## Explicitly out of this service's own scope
 *
 * This service does not decide *when* to call itself, does not create
 * any fallback/normal-dispatch Proposal on a negative outcome, and does
 * not know whether it has already been tried for a given order in the
 * past (only whether one of its own Proposals is still currently open).
 * Sequencing — calling this once, at the right moment, and reacting to
 * [FirstRefusalOutcome] by invoking (or not invoking) whatever the future
 * normal-dispatch trigger turns out to be — is the explicitly deferred
 * "future routing task" this class exists to be invoked by (Task 14's own
 * Phase 5 instruction: "establish the backend capability... the future
 * routing task can invoke").
 *
 * ## Explicit driver intent (Task 15C: First Refusal Contract Completion
 * and Concurrency Safety)
 *
 * [attempt]'s own [explicitDriverIntentDeclared] parameter is the
 * *deterministic*, non-racing half of "explicit passenger choice always
 * wins" (Task 15C's own authoritative product rule) — see
 * `com.pios.ordermanagement.domain.Order.explicitDriverIntent`'s own KDoc
 * for the full atomicity argument this parameter exists to carry forward.
 * When `true`, this method makes **no read** of [primaryDriverRepository]
 * and **no call** to [proposalApplicationService] at all — it returns
 * [FirstRefusalOutcome.ExplicitDriverIntentDeclared] immediately. This is
 * deliberately not merely "skip creating the Proposal" (which would still
 * leave open the question of whether *checking* eligibility first has any
 * observable side effect) — it is a complete no-op with respect to every
 * collaborator this service owns, so a caller who has already declared
 * explicit intent gets a guarantee that costs nothing to reason about:
 * this call touched nothing.
 *
 * This is deliberately **not** a race-avoidance heuristic layered on top
 * of the pre-existing Proposal Root Invariant (Task 14's own
 * [FirstRefusalOutcome.AlreadyAttempted] mechanism, unchanged, still the
 * backstop for every case this parameter does not cover — e.g., two
 * genuinely concurrent automatic attempts, or a caller who never declares
 * intent at all). It is a *different*, stronger guarantee: because the
 * flag this parameter carries is only ever set atomically at order
 * submission (strictly, causally prior to any possible invocation of this
 * method for that same order), a caller passing `true` is not asking this
 * service to *win* a race — there never was one to begin with.
 */
@Service
class FirstRefusalApplicationService(
    private val primaryDriverRepository: PrimaryDriverRepository,
    private val proposalApplicationService: ProposalApplicationService,
    private val driverAvailabilityRepository: DriverAvailabilityRepository? = null
) {

    /**
     * Attempts First Refusal for [order], on behalf of [passengerReference].
     * Never throws for the ordinary "no primary driver"/"primary
     * ineligible"/"already attempted" cases — each is a [FirstRefusalOutcome]
     * value, not an exception, since none of them is a failure of this
     * service's own contract.
     *
     * [explicitDriverIntentDeclared] defaults to `false` so every existing
     * caller/test (Task 14) continues to behave exactly as before this
     * parameter's own addition. See this class's own "Explicit driver
     * intent" KDoc for what passing `true` guarantees.
     *
     * [excludeDrivers] (ADR-078, Dispatch Recovery After Proposal Decline
     * or Lapse, Decision B) is an additive parameter, defaulting to
     * `emptySet()` so every existing caller/test continues to behave
     * exactly as before its addition. When the passenger's primary driver
     * is a member of [excludeDrivers], this attempt is skipped exactly as
     * if that primary driver were ineligible — a primary driver who has
     * already declined or lapsed on this same order must not be
     * re-offered it every retry for the rest of the routing window.
     */
    fun attempt(
        order: OrderReference,
        passengerReference: PassengerReference,
        isTest: Boolean = false,
        explicitDriverIntentDeclared: Boolean = false,
        requestedPickupAt: Instant? = null,
        excludeDrivers: Set<DriverReference> = emptySet()
    ): FirstRefusalOutcome {
        if (explicitDriverIntentDeclared) {
            return FirstRefusalOutcome.ExplicitDriverIntentDeclared
        }

        val primary = primaryDriverRepository.findByPassenger(passengerReference)
            ?: return FirstRefusalOutcome.NoPrimaryDriver

        if (primary.primaryDriverId in excludeDrivers) {
            return FirstRefusalOutcome.PrimaryDriverIneligible(primary.primaryDriverId)
        }

        if (!isEligible(primary.primaryDriverId, isTest, requestedPickupAt)) {
            return FirstRefusalOutcome.PrimaryDriverIneligible(primary.primaryDriverId)
        }

        return try {
            val created = proposalApplicationService.handle(
                ProposeDriverCommand(
                    order = order,
                    driver = primary.primaryDriverId,
                    isTest = isTest,
                    passengerReference = passengerReference,
                    requestedPickupAt = requestedPickupAt
                )
            )
            FirstRefusalOutcome.Proposed(created.proposal)
        } catch (ex: IllegalStateException) {
            // Either the primary driver turned out unavailable by the time
            // handle()'s own atomic check ran (a narrow race with the
            // pre-check above, accepted -- see this class's own KDoc), or
            // the order already has an OPEN proposal, detected by
            // Proposal.propose's own in-memory existingProposals check
            // (this attempt, or any other, already in flight). Both
            // collapse to the same signal: no new Proposal was created by
            // this call.
            FirstRefusalOutcome.AlreadyAttempted
        } catch (ex: org.springframework.dao.DataIntegrityViolationException) {
            // Task 15C: the same "order already has an OPEN proposal" fact
            // as above, but detected at the database level instead --
            // `proposals_one_open_per_order` (V14) rejecting a second OPEN
            // row that Proposal.propose's own in-memory check, racing a
            // genuinely concurrent transaction, did not catch (the
            // in-memory SELECT ran before the other transaction's own
            // INSERT committed). Collapses to the identical outcome: no
            // new Proposal was created by this call, and this is not a
            // failure of this service's own contract -- see this class's
            // own "Explicit driver intent" KDoc for why this backstop
            // exists alongside, not instead of, the flag-based guarantee.
            FirstRefusalOutcome.AlreadyAttempted
        }
    }

    private fun isEligible(driver: DriverReference, orderIsTest: Boolean, requestedPickupAt: Instant?): Boolean {
        val repository = driverAvailabilityRepository ?: return true
        val record = repository.findByDriverReference(driver)
        val advanceRequest = requestedPickupAt?.isAfter(Instant.now()) == true
        return record != null && record.isTest == orderIsTest && (record.available || advanceRequest)
    }
}

/**
 * The result of one [FirstRefusalApplicationService.attempt] call. Every
 * case is a legitimate, non-exceptional outcome — the caller decides what
 * happens next (out of this service's own scope, see its class KDoc).
 */
sealed class FirstRefusalOutcome {
    /** A First-Refusal Proposal was created for the passenger's primary driver. */
    data class Proposed(val proposal: Proposal) : FirstRefusalOutcome()

    /** The passenger has no currently designated primary driver. */
    object NoPrimaryDriver : FirstRefusalOutcome()

    /** A primary driver exists but is not currently available. */
    data class PrimaryDriverIneligible(val driver: DriverReference) : FirstRefusalOutcome()

    /** This order already has an open proposal (this attempt, or any other, already in flight). */
    object AlreadyAttempted : FirstRefusalOutcome()

    /**
     * The caller declared, via [FirstRefusalApplicationService.attempt]'s
     * own `explicitDriverIntentDeclared` parameter, that the passenger's
     * explicit driver choice already exists or is imminent for this
     * order — this call made no read of [PrimaryDriverRepository] and no
     * attempt at [ProposalApplicationService.handle] at all (Task 15C).
     */
    object ExplicitDriverIntentDeclared : FirstRefusalOutcome()
}

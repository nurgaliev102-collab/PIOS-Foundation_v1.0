package com.pios.dispatch.application

import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.domain.PassengerReference
import com.pios.dispatch.domain.Proposal
import org.springframework.stereotype.Service

/**
 * FR-003A (Fallback Dispatch) — the smallest possible closing of the gap
 * [FirstRefusalApplicationService]'s own KDoc names and explicitly defers:
 * *"This service does not decide when to call itself, does not create any
 * fallback/normal-dispatch Proposal on a negative outcome... Sequencing —
 * calling this once, at the right moment... is the explicitly deferred
 * 'future routing task'."* This service is that future routing task, kept
 * to the same minimal shape as [FirstRefusalApplicationService] itself: no
 * new state, no new event, no new table, no new endpoint — it creates
 * exactly the same kind of Proposal First Refusal already creates, for a
 * driver chosen by one simple, explicit rule instead of by primary-driver
 * lookup.
 *
 * ## Why this is a new, separate service, not a branch inside
 * [FirstRefusalApplicationService]
 *
 * Mirrors [FirstRefusalApplicationService]'s own "why a new service"
 * reasoning exactly: First Refusal's job is "does this passenger have an
 * eligible primary driver" — a complete, already-tested, already-proven
 * answer on its own. Fallback Dispatch's job is a different question ("is
 * there *any* available driver at all") with a different, unrelated
 * collaborator set. Folding the second question into the first would only
 * make [FirstRefusalApplicationService] responsible for two decisions
 * instead of one, for no benefit — the caller ([OrderSubmittedFirstRefusalListener],
 * unchanged apart from one new call) already sequences them.
 *
 * ## Driver selection (Product Owner instruction, FR-003A; extended by
 * ADR-068, Relationship-Ordered Fallback Dispatch — Trusted → Open only)
 *
 * ADR-068 Part 2 states the algorithm exactly: tiers are evaluated
 * strictly in order, stopping at the first that yields a driver.
 *
 * - **Tier 1 (Trusted).** Among [passengerReference]'s trusted drivers
 *   (Dispatch's own local [TrustedDriverRepository] projection, fed by
 *   Passenger Experience's `ConnectionEstablished`/`ConnectionRemoved`),
 *   excluding [excludeDrivers], the one whose availability record has
 *   stood `available = true` the longest
 *   ([TrustedDriverRepository.findLongestIdleTrustedAvailable]) — the
 *   identical tie-break Tier 3 already uses, applied to a narrower
 *   candidate set. No new comparison between drivers is introduced (ADR-068
 *   Part 2, property 1).
 * - **Tier 2 (Network) is not implemented.** ADR-068 Part 3: no candidate
 *   set is computable from any data PIOS holds today; this tier is skipped
 *   entirely, never guessed at.
 * - **Tier 3 (Open Marketplace), unchanged.** The single available driver
 *   whose local availability record has stood `available = true` the
 *   longest platform-wide
 *   ([DriverAvailabilityRepository.findLongestIdleAvailable]) —
 *   deliberately not geographic, not rated, not weighted: exactly the "not
 *   complex matching" boundary FR-003A's own scope draws.
 *
 * No driver in any tier is not an error — [FallbackDispatchOutcome.NoAvailableDriver],
 * mirroring how [FirstRefusalOutcome.NoPrimaryDriver] is not an error either.
 *
 * [trustedDriverRepository] defaults to `null` so every existing caller and
 * test that constructs this service with only its original two
 * constructor arguments continues to compile and behave exactly as
 * before ADR-068 — a `null` repository means "no Tier 1 candidate is ever
 * found," which degrades this service to its pre-ADR-068 Tier-3-only
 * behavior, exactly the "stale/absent projection degrades to today's
 * behavior" guarantee ADR-068 Part 1 requires.
 *
 * ## No retry on decline/lapse (FR-003A's own scope boundary)
 *
 * If the resulting Proposal is later declined or lapses, nothing further
 * happens automatically — the order simply returns to "unmatched," exactly
 * as an order with no primary driver and no available fallback driver
 * already does today. A coordinator can still manually propose a different
 * driver via the existing `ProposalController.createProposal`, unchanged.
 * Automatically retrying a second, third, ... driver is exactly the
 * "complex matching" FR-003A's own brief excludes — a candidate for a
 * later slice, not decided here.
 *
 * ## Races and duplicate attempts
 *
 * Identical handling to [FirstRefusalApplicationService.attempt]: both the
 * Proposal aggregate's own one-open-proposal-per-order invariant
 * (in-memory) and `proposals_one_open_per_order` (database) collapse to
 * [FallbackDispatchOutcome.AlreadyAttempted] — no new deduplication
 * mechanism, no new idempotency ledger.
 */
@Service
class FallbackDispatchApplicationService(
    private val driverAvailabilityRepository: DriverAvailabilityRepository,
    private val proposalApplicationService: ProposalApplicationService,
    private val trustedDriverRepository: TrustedDriverRepository? = null
) {

    /**
     * Attempts Fallback Dispatch for [order], on behalf of [passengerReference].
     * Called only when First Refusal itself produced no Proposal and none is
     * expected (see [OrderSubmittedFirstRefusalListener] for the exact
     * outcomes that trigger this call), or when a primary driver's own
     * Proposal for this order just declined or lapsed
     * ([ProposalController.declineProposal]/
     * [ProposalLapseApplicationService] respectively) — never throws for
     * the ordinary "no driver available"/"already attempted" cases, each a
     * [FallbackDispatchOutcome] value, not an exception.
     *
     * [excludeDrivers] is passed straight through to
     * [DriverAvailabilityRepository.findLongestIdleAvailable] — see that
     * method's own KDoc for why: the driver whose own decline/lapse just
     * triggered this specific call must never be re-selected for the same
     * order merely because their availability record still shows
     * `available = true`. Defaults to empty for the immediate,
     * no-primary-was-ever-proposed-to path, where no driver has yet said
     * no to this particular order.
     *
     * ADR-068 Part 2, property 2: [excludeDrivers] applies identically to
     * every tier, including Tier 1 -- a primary driver who just declined
     * or lapsed is, by definition, also a member of the passenger's own
     * trusted circle (ADR-054 Part 2's composite foreign key), so without
     * this exclusion reaching Tier 1, that same driver would be the
     * *first* candidate re-selected.
     */
    fun attempt(
        order: OrderReference,
        passengerReference: PassengerReference,
        isTest: Boolean = false,
        excludeDrivers: Set<DriverReference> = emptySet()
    ): FallbackDispatchOutcome {
        val trustedDriver = trustedDriverRepository?.findLongestIdleTrustedAvailable(passengerReference, excludeDrivers)
        val driver = trustedDriver
            ?: driverAvailabilityRepository.findLongestIdleAvailable(excludeDrivers)
            ?: return FallbackDispatchOutcome.NoAvailableDriver

        return try {
            val created = proposalApplicationService.handle(
                ProposeDriverCommand(
                    order = order,
                    driver = driver,
                    isTest = isTest,
                    passengerReference = passengerReference
                )
            )
            FallbackDispatchOutcome.Proposed(created.proposal)
        } catch (ex: IllegalStateException) {
            // Either the selected driver turned out unavailable by the time
            // handle()'s own atomic check ran (a narrow race, accepted --
            // see this class's own KDoc), or the order already has an OPEN
            // proposal (created concurrently by First Refusal or another
            // Fallback Dispatch attempt). Both collapse to the same signal.
            FallbackDispatchOutcome.AlreadyAttempted
        } catch (ex: org.springframework.dao.DataIntegrityViolationException) {
            // The same "order already has an OPEN proposal" fact, detected
            // at the database level instead -- identical reasoning to
            // FirstRefusalApplicationService.attempt's own identical catch.
            FallbackDispatchOutcome.AlreadyAttempted
        }
    }
}

/**
 * The result of one [FallbackDispatchApplicationService.attempt] call.
 * Every case is a legitimate, non-exceptional outcome.
 */
sealed class FallbackDispatchOutcome {
    /** A Fallback Dispatch Proposal was created for the selected driver. */
    data class Proposed(val proposal: Proposal) : FallbackDispatchOutcome()

    /** No driver is currently available at all. */
    object NoAvailableDriver : FallbackDispatchOutcome()

    /** This order already has an open proposal (First Refusal, or another attempt, already in flight). */
    object AlreadyAttempted : FallbackDispatchOutcome()
}

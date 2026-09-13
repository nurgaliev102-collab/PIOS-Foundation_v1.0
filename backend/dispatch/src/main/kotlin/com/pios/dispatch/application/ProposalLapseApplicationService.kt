package com.pios.dispatch.application

import com.pios.dispatch.domain.Proposal
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

/**
 * Detects `OPEN` Proposals whose waiting period has elapsed and resolves
 * them to `LAPSED`, closing P0-3 (`docs/SPRINT_PILOT_BLOCKERS.md`) per the
 * ownership and mechanism ADR-051 and ADR-052 (both Accepted) assign.
 *
 * ## Ownership and layering (ADR-051, not reopened here)
 *
 * This is the application-layer capability ADR-051 Part 2 assigns
 * detection and invocation to — never the [com.pios.dispatch.domain.Proposal]
 * aggregate itself, which [com.pios.dispatch.domain.Proposal.lapse]'s own
 * KDoc already, correctly, declines to be ("The mechanism by which lapsing
 * is recognized... is not this aggregate's concern"). Invocation goes
 * through [proposalApplicationService], the same path [accept]/[decline]
 * already use — this class never calls [com.pios.dispatch.domain.Proposal.lapse]
 * directly, and never touches [proposalRepository] for anything beyond the
 * read [findOpen] provides.
 *
 * ## Mechanism (ADR-052, not reopened here)
 *
 * This class *is* the "already-existing or newly-coordinated
 * application-layer capability" ADR-052's Decision diagram names — the
 * "what to do" a periodic timer ([ProposalLapseScheduler]) calls into. It
 * holds no timer of its own and is not itself infrastructure (ADR-052's
 * own "Terminology, fixed here" section draws that line).
 *
 * ## Timeout value — a configuration default, not a ratified business rule
 *
 * [timeoutMinutes] mirrors [OutboxRelayScheduler]'s own documented
 * precedent for [OutboxRelayScheduler.FIXED_DELAY_PROPERTY] exactly: a
 * concrete number is required for this class to run at all, but the
 * *value* is explicitly not ratified by ADR-051, ADR-052, or this class —
 * `PRODUCT_DECISION_ELECTRONIC_DISPATCHER_MVP_BLOCKERS.md` PD-2 requires
 * only that it be a single, configurable, non-adaptive parameter,
 * "corrected by real pilot data," never a value this class invents as a
 * business rule. The default below exists only so the application starts
 * with a working value; an operator changes it via configuration, not a
 * code change.
 *
 * ## Concurrent resolution (ADR-052's own disclosed, unresolved gap)
 *
 * A Proposal read as `OPEN` by [findOpen] may be resolved by a driver's own
 * `accept`/`decline` call before [lapseIfStillOpen] reaches it — a real,
 * if narrow, race in a single-instance deployment (an HTTP request and this
 * scheduled sweep run on different threads even though `@Scheduled`
 * executions themselves never overlap with each other, per
 * [OutboxRelayScheduler]'s own KDoc on `fixedDelay`). [proposalApplicationService.lapseProposal]
 * then throws [IllegalStateException] (`Proposal.lapse`'s own precondition).
 * ADR-052 Negative Consequences names this exact gap and suggests exactly
 * this resolution: *"treating that specific exception as an already-resolved
 * outcome rather than a failure."* That is what [lapseIfStillOpen] does —
 * ordinary implementation handling of an already-disclosed architectural
 * gap, not a new decision.
 *
 * ## FR-003A (Fallback Dispatch) after a primary driver's own lapse
 *
 * The product requirement [FallbackDispatchApplicationService]'s own KDoc
 * did not yet close: *"если primary/eligible driver не принял заказ в
 * предусмотренный срок — корректно запускать fallback"* — a primary
 * driver who never responds must produce the same fallback attempt as one
 * who was never eligible to begin with. [OrderSubmittedFirstRefusalListener]
 * already covers the *immediate* half (`NoPrimaryDriver`/
 * `PrimaryDriverIneligible`, decided the instant `OrderSubmitted` arrives);
 * this class is the only place the *delayed* half can be decided, since it
 * is the only place that already knows a Proposal has just, genuinely,
 * transitioned to `LAPSED`.
 *
 * [attemptFallbackAfterLapse] identifies "was this the primary driver's own
 * proposal" the same way [FirstRefusalApplicationService.attempt] itself
 * decides who the primary driver is — reading
 * [primaryDriverRepository.findByPassenger] fresh, right now, and comparing
 * its [PrimaryDriverRecord.primaryDriverId] against [Proposal.driver] —
 * rather than adding a new "how was this Proposal created" field to
 * [Proposal] itself, which no other case in this module needs and which
 * `CLAUDE.md`'s own "smallest correct change" principle rules out here.
 * This naturally, correctly excludes exactly the cases
 * [FallbackDispatchApplicationService]'s own "No retry on decline/lapse"
 * KDoc already declines to retry: a driver Fallback Dispatch itself
 * selected is never the passenger's primary driver (Fallback Dispatch only
 * ever runs when First Refusal already found no *eligible* primary), so
 * its own Proposal lapsing here never re-triggers [fallbackDispatchApplicationService] —
 * without this class needing to know that reasoning explicitly.
 *
 * [primaryDriverRepository]/[fallbackDispatchApplicationService] default to
 * `null`, mirroring [FirstRefusalApplicationService]'s own identical
 * `driverAvailabilityRepository: DriverAvailabilityRepository? = null`
 * precedent exactly: every existing caller/test of this class, none of
 * which has any interest in fallback-after-lapse, continues to compile and
 * behave exactly as before this parameter pair's own addition. A `null`
 * either collaborator makes [attemptFallbackAfterLapse] a no-op — the same
 * "missing collaborator disables the enhancement, never the base behavior"
 * shape already established.
 */
@Service
class ProposalLapseApplicationService(
    private val proposalRepository: ProposalRepository,
    private val proposalApplicationService: ProposalApplicationService,
    private val primaryDriverRepository: PrimaryDriverRepository? = null,
    private val fallbackDispatchApplicationService: FallbackDispatchApplicationService? = null,
    @Value("\${pios.proposal.lapse.timeout-minutes:5}") private val timeoutMinutes: Long
) {
    private val logger = LoggerFactory.getLogger(ProposalLapseApplicationService::class.java)

    /**
     * Sweeps every currently `OPEN` Proposal and lapses each whose
     * [com.pios.dispatch.domain.Proposal.createdAt] is older than
     * [timeoutMinutes]. [now] defaults to the real clock for
     * [ProposalLapseScheduler]'s own real invocation; overridable so a test
     * can simulate elapsed time without waiting for it, mirroring the same
     * `at: Instant = Instant.now()` idiom already established throughout
     * this module (`Assignment.accept`, `Proposal.accept`/`decline`/`lapse`).
     *
     * A Proposal with no [com.pios.dispatch.domain.Proposal.createdAt]
     * (a row persisted before `V7__proposal_timestamps.sql` existed,
     * `PostgreSQLProposalRepository`'s own KDoc) has no age to compare and
     * is left untouched rather than guessed at.
     */
    fun lapseStaleProposals(now: Instant = Instant.now()) {
        val cutoff = now.minus(Duration.ofMinutes(timeoutMinutes))
        proposalRepository.findOpen()
            .filter { proposal -> proposal.createdAt?.isBefore(cutoff) == true }
            .forEach { proposal -> lapseIfStillOpen(proposal) }
    }

    private fun lapseIfStillOpen(proposal: Proposal) {
        try {
            proposalApplicationService.lapseProposal(LapseProposalCommand(proposal.id))
            attemptFallbackAfterLapse(proposal)
        } catch (ex: IllegalStateException) {
            logger.debug(
                "Proposal {} was no longer OPEN when the lapse sweep reached it; " +
                    "already resolved by another actor (accept/decline), not a failure",
                proposal.id.value
            )
        }
    }

    /** See this class's own "FR-003A after a primary driver's own lapse" KDoc for the full reasoning. */
    private fun attemptFallbackAfterLapse(proposal: Proposal) {
        val fallback = fallbackDispatchApplicationService ?: return
        val primaryDriverRepository = this.primaryDriverRepository ?: return
        val passengerReference = proposal.passengerReference ?: return
        val primary = primaryDriverRepository.findByPassenger(passengerReference) ?: return
        if (primary.primaryDriverId != proposal.driver) {
            return
        }
        val outcome = fallback.attempt(proposal.order, passengerReference, proposal.isTest)
        logger.info(
            "Proposal {} (primary driver {}) lapsed without a response for order {}: Fallback Dispatch outcome {}",
            proposal.id.value,
            proposal.driver.driverId,
            proposal.order.orderId,
            outcome::class.simpleName
        )
    }
}

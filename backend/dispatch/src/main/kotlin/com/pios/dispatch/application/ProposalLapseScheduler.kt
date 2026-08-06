package com.pios.dispatch.application

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * The production trigger for [ProposalLapseApplicationService] (P0-3,
 * `docs/SPRINT_PILOT_BLOCKERS.md`; ADR-051, Proposal Lifecycle Resolution
 * Ownership; ADR-052, Proposal Lapse Resolution Mechanism — both Accepted).
 * Structurally mirrors [OutboxRelayScheduler] exactly, per ADR-052's own
 * selected mechanism shape: a thin `@Component` holding one `@Scheduled`
 * method that calls an already-existing, already-tested capability. This
 * class adds no detection or lapsing logic of its own.
 *
 * ## Terminology (ADR-052, "Terminology, fixed here" — not redecided by
 * this class)
 *
 * This class is the *mechanism* — an infrastructure-shaped periodic timer
 * — not the *responsibility*. The responsibility (deciding whether a
 * Proposal's waiting period has elapsed, and invoking the transition) lives
 * entirely in [ProposalLapseApplicationService], Dispatch's own application
 * layer, per ADR-051 Part 2. This class holds no authority of its own and
 * only calls into that responsibility, exactly as ADR-051 Part 2's own
 * infrastructure carve-out requires. Filed in the `application` Kotlin
 * package as a directory convention this codebase already uses for trigger
 * classes ([OutboxRelayScheduler] itself) — not a claim that the timer
 * mechanism performs application-layer decision-making.
 *
 * ## Why `@Scheduled`, not a new mechanism
 *
 * ADR-052 selected this shape specifically because it already exists,
 * proven, in this exact module (`OutboxRelayScheduler`) and in its sibling
 * producer modules — no new dependency, no new infrastructure. `@EnableScheduling`
 * is already present on `DispatchApplication` (added for `OutboxRelayScheduler`);
 * this class needs no further activation.
 *
 * [FIXED_DELAY_PROPERTY]'s default is a configuration default, not a
 * product invariant, mirroring [OutboxRelayScheduler.FIXED_DELAY_PROPERTY]'s
 * own documented reasoning exactly — overridable per deployment via
 * `application.yml` or an environment variable, without a code change.
 * `fixedDelayString` (not `fixedRateString`) is used deliberately, for the
 * identical reason: Spring's own `TaskScheduler` does not begin the next
 * execution until the current one returns, so a slow sweep lengthens the
 * interval between ticks rather than causing two ticks to run concurrently.
 */
@Component
class ProposalLapseScheduler(
    private val proposalLapseApplicationService: ProposalLapseApplicationService
) {

    @Scheduled(fixedDelayString = "\${pios.proposal.lapse.fixed-delay-ms:30000}")
    fun triggerLapseSweep() {
        proposalLapseApplicationService.lapseStaleProposals()
    }

    companion object {
        const val FIXED_DELAY_PROPERTY = "pios.proposal.lapse.fixed-delay-ms"
    }
}

package com.pios.dispatch.application

import com.pios.dispatch.domain.DriverReference
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

/**
 * D-08 (Handoff Observation Foundation,
 * `docs/PIOS_D08_HANDOFF_OBSERVATION_IMPLEMENTATION_SPEC.md` §2/§4/§6).
 * The one application-layer entry point for computing a committing
 * driver's Handoff observation. Read-only in every sense: it never calls
 * [HandoffApplicationService] or any other write path, is never called
 * *from* one either, and has no effect on Handoff/Order/Trip/dispatch —
 * it only shapes [HandoffObservationRepository]'s own raw, already-
 * computed facts (spec §5, the evidence gate: no call-site of any policy
 * or enforcement exists anywhere in this class or its callers).
 *
 * ## Window resolution (spec §4)
 *
 * The unit is the week (7 days) — the cadence
 * `docs/PIOS_TAXI_HANDOFF_OPEN_QUESTIONS.md` OQ-7 Option B's own
 * illustrative text already names ("N per driver **per week**"), the
 * only place in the evidentiary record naming any time unit for this
 * concept at all. [windowWeeks] is a configuration value, not a ratified
 * business threshold (spec §4): its default (4) is a non-binding
 * operational starting point, mirroring
 * [ProposalLapseApplicationService.timeoutMinutes]'s own explicit "not a
 * ratified business rule" treatment.
 *
 * [windowEnd] is the query's own current instant, not a calendar-week
 * boundary — a trailing window ending *now*, not aligned to Monday
 * 00:00 UTC. An earlier draft of this class aligned [windowEnd] to the
 * start of the current ISO week (mirroring `RideStreakCalculator`'s own
 * "complete weeks only" discipline for its own, different purpose —
 * consecutive-week streak counting, where a partial week must not count
 * as a full one). Direct testing against real data (this class's own
 * `HandoffObservationPostgreSQLTest`) surfaced why that does not transfer
 * to *this* metric: it would make every Assignment created since the most
 * recent Monday invisible to observation until the current week fully
 * elapses — hiding exactly the most recent activity an owner-facing
 * observation tool exists to surface, for no benefit no upstream document
 * required. [windowWeeks] remains the unit of measure (its default of 4
 * means a trailing 28 days); only the calendar-alignment of [windowEnd]
 * was removed.
 */
@Service
class HandoffObservationQueryService(
    private val handoffObservationRepository: HandoffObservationRepository = NoOpHandoffObservationRepository,
    @Value("\${pios.handoff.observation.window-weeks:4}") private val windowWeeks: Long = 4
) {
    init {
        require(windowWeeks in 1..52) {
            "pios.handoff.observation.window-weeks must be between 1 and 52 (input sanity only, not a business rule), was $windowWeeks"
        }
    }

    fun observe(committingDriver: DriverReference, at: Instant = Instant.now()): HandoffObservationResult {
        val windowEnd = at
        val windowStart = windowEnd.minus(Duration.ofDays(7L * windowWeeks))
        val facts = handoffObservationRepository.observe(committingDriver, windowStart, windowEnd)
        val insufficientData = facts.totalCommitments == 0
        val rate = if (insufficientData) {
            null
        } else {
            facts.handoffsWithSubstituteAcceptance.toDouble() / facts.totalCommitments
        }
        return HandoffObservationResult(
            driverId = committingDriver.driverId,
            windowStart = windowStart,
            windowEnd = windowEnd,
            windowWeeks = windowWeeks,
            facts = facts,
            rate = rate,
            insufficientData = insufficientData
        )
    }
}

/**
 * The fully-shaped result of one observation query — everything
 * [com.pios.dispatch.api.HandoffController]'s own owner-only endpoint
 * needs to build its response, and nothing more. [rate]/[insufficientData]
 * are the one piece of business interpretation this service adds beyond
 * [HandoffObservationRepository]'s own raw facts (spec §6's own edge
 * case: a zero denominator must never render as a misleading `0`).
 */
data class HandoffObservationResult(
    val driverId: String,
    val windowStart: Instant,
    val windowEnd: Instant,
    val windowWeeks: Long,
    val facts: HandoffObservationFacts,
    val rate: Double?,
    val insufficientData: Boolean
)

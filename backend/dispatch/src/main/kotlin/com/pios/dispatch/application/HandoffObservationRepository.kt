package com.pios.dispatch.application

import com.pios.dispatch.domain.DriverReference
import java.time.Instant

/**
 * D-08 (Handoff Observation Foundation,
 * `docs/PIOS_D08_HANDOFF_OBSERVATION_IMPLEMENTATION_SPEC.md`). The
 * read-only persistence boundary for the observation metric's own raw
 * facts — never a business decision, never a score, never a comparison
 * across drivers. Named separately from [AssignmentRepository]/
 * [HandoffRepository] because it answers a fundamentally different
 * question than either: not "what is this one Assignment/Handoff," but
 * "what did this one committing driver's own Handoff usage look like
 * over this window" — an aggregation, not a lookup.
 *
 * [observe] is the only method. It never mutates anything, is never
 * called from [HandoffApplicationService] or any other write path (spec
 * §5, the evidence gate: observation has no call-site inside enforcement
 * at all), and is read by exactly one caller,
 * [HandoffObservationQueryService], which is itself read by exactly one
 * owner-only API endpoint.
 */
interface HandoffObservationRepository {
    fun observe(committingDriver: DriverReference, windowStart: Instant, windowEnd: Instant): HandoffObservationFacts
}

/**
 * The raw, fully auditable facts behind one committing driver's
 * observation window (spec §6/§10). Every field here is a direct count
 * or a ratio of two such counts — nothing is smoothed, weighted, or
 * otherwise transformed, so every figure remains reconstructible by
 * re-running the defining query against `handoffs`/`assignments`
 * directly (spec §10).
 *
 * [totalCommitments] is the denominator (spec §6): every Assignment this
 * driver took on, created within the window, regardless of outcome.
 *
 * [handoffsWithSubstituteAcceptance] is the numerator (spec §6): the
 * count of *distinct Assignments* (not raw `handoffs` rows) that had at
 * least one Handoff reach `substitute_accepted_at IS NOT NULL` — the
 * governing predicate (spec §1). Counting by Assignment, not by Handoff
 * row, means a driver who is refused and legitimately re-proposes for
 * the same ride is never double-counted.
 *
 * The seven [eventBreakdown]-shaped fields below exist so the single
 * collapsed rate is never the only thing visible (spec §10's own
 * auditability requirement) — each counts *Handoff rows*, not
 * Assignments, since a breakdown by outcome is inherently row-level.
 */
data class HandoffObservationFacts(
    val totalCommitments: Int,
    val handoffsWithSubstituteAcceptance: Int,
    val proposedOnly: Int,
    val substituteAcceptedOngoing: Int,
    val committed: Int,
    val refusedAfterAcceptance: Int,
    val withdrawnAfterAcceptance: Int,
    val declinedBySubstitute: Int,
    val withdrawnBeforeAcceptance: Int,
    val distinctSubstitutesUsed: Int,
    val topSubstituteShare: Double?
)

/**
 * A no-op [HandoffObservationRepository]. Mirrors [NoOpHandoffRepository]
 * exactly — used only as a default for callers with no interest in
 * observation data; Spring always injects the real
 * [com.pios.dispatch.persistence.PostgreSQLHandoffObservationRepository]
 * bean in a running application.
 */
object NoOpHandoffObservationRepository : HandoffObservationRepository {
    override fun observe(committingDriver: DriverReference, windowStart: Instant, windowEnd: Instant) =
        HandoffObservationFacts(
            totalCommitments = 0,
            handoffsWithSubstituteAcceptance = 0,
            proposedOnly = 0,
            substituteAcceptedOngoing = 0,
            committed = 0,
            refusedAfterAcceptance = 0,
            withdrawnAfterAcceptance = 0,
            declinedBySubstitute = 0,
            withdrawnBeforeAcceptance = 0,
            distinctSubstitutesUsed = 0,
            topSubstituteShare = null
        )
}

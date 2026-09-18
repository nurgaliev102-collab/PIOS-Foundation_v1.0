package com.pios.dispatch.persistence

import com.pios.dispatch.application.AssignmentRepository
import com.pios.dispatch.application.HandoffObservationFacts
import com.pios.dispatch.application.HandoffObservationRepository
import com.pios.dispatch.application.HandoffRepository
import com.pios.dispatch.domain.AssignmentId
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.HandoffStatus
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * A minimal in-memory adapter for [HandoffObservationRepository] (D-08).
 * Not a production storage mechanism — mirrors [InMemoryHandoffRepository]/
 * [InMemoryAssignmentRepository] exactly in that respect.
 *
 * ## Why this class carries its own creation-time map
 *
 * [com.pios.dispatch.domain.Assignment] has no creation-timestamp field
 * of its own (confirmed by direct reading before this class was written —
 * only `statusChangedAt`/`arrivedAt`/`startedAt`/`completedAt` exist, all
 * `null` until a transition happens). The real, PostgreSQL-backed
 * adapter ([PostgreSQLHandoffObservationRepository]) sources an
 * Assignment's own creation instant from `dispatch_outbox`'s already-
 * permanent `OrderAssigned` row (`created_at`, keyed by `aggregate_id`) —
 * no schema change to `assignments` was made to support this feature (see
 * that class's own KDoc for the full reasoning). This in-memory adapter
 * has no equivalent outbox to read, so it keeps its own small record
 * instead, recorded explicitly via [recordAssignmentCreatedAt] by tests
 * that care about window-boundary behavior. An Assignment never recorded
 * this way defaults to just inside the *requested* window (one second
 * before [observe]'s own windowEnd, which
 * [com.pios.dispatch.application.HandoffObservationQueryService] itself
 * always resolves to the query's own current instant, spec Section 4) --
 * a small margin rather than [windowEnd] itself, so it is never excluded
 * by a half-open `< windowEnd` comparison. This is what a test that does
 * not care about timing actually expects: "this Assignment counts," not
 * "this Assignment silently falls outside the window for a reason the
 * test never intended to exercise."
 */
class InMemoryHandoffObservationRepository(
    private val assignmentRepository: AssignmentRepository,
    private val handoffRepository: HandoffRepository
) : HandoffObservationRepository {

    private val assignmentCreatedAt = ConcurrentHashMap<String, Instant>()

    fun recordAssignmentCreatedAt(assignmentId: AssignmentId, at: Instant) {
        assignmentCreatedAt[assignmentId.value] = at
    }

    override fun observe(committingDriver: DriverReference, windowStart: Instant, windowEnd: Instant): HandoffObservationFacts {
        val assignmentsInWindow = assignmentRepository.findByDriver(committingDriver).filter { assignment ->
            val createdAt = assignmentCreatedAt[assignment.id.value] ?: windowEnd.minusSeconds(1)
            !createdAt.isBefore(windowStart) && createdAt.isBefore(windowEnd)
        }

        var handoffsWithSubstituteAcceptance = 0
        var proposedOnly = 0
        var substituteAcceptedOngoing = 0
        var committed = 0
        var refusedAfterAcceptance = 0
        var withdrawnAfterAcceptance = 0
        var declinedBySubstitute = 0
        var withdrawnBeforeAcceptance = 0
        val substituteCounts = mutableMapOf<String, Int>()

        for (assignment in assignmentsInWindow) {
            val handoffs = handoffRepository.findByAssignmentId(assignment.id)

            val acceptedHandoffs = handoffs.filter { it.substituteAcceptedAt != null }
            if (acceptedHandoffs.isNotEmpty()) {
                handoffsWithSubstituteAcceptance++
                // One credit per Assignment, to the substitute of that
                // Assignment's own most recently accepted Handoff -- keeps
                // concentration counting consistent with the numerator's
                // own per-Assignment (not per-row) counting philosophy.
                val mostRecentlyAccepted = acceptedHandoffs.maxBy { it.substituteAcceptedAt!! }
                substituteCounts.merge(mostRecentlyAccepted.substituteDriver.driverId, 1, Int::plus)
            }

            for (handoff in handoffs) {
                val accepted = handoff.substituteAcceptedAt != null
                when (handoff.status) {
                    HandoffStatus.PROPOSED -> if (!accepted) proposedOnly++
                    HandoffStatus.SUBSTITUTE_ACCEPTED -> substituteAcceptedOngoing++
                    HandoffStatus.COMMITTED -> committed++
                    HandoffStatus.REFUSED -> if (accepted) refusedAfterAcceptance++ else declinedBySubstitute++
                    HandoffStatus.WITHDRAWN -> if (accepted) withdrawnAfterAcceptance++ else withdrawnBeforeAcceptance++
                }
            }
        }

        return HandoffObservationFacts(
            totalCommitments = assignmentsInWindow.size,
            handoffsWithSubstituteAcceptance = handoffsWithSubstituteAcceptance,
            proposedOnly = proposedOnly,
            substituteAcceptedOngoing = substituteAcceptedOngoing,
            committed = committed,
            refusedAfterAcceptance = refusedAfterAcceptance,
            withdrawnAfterAcceptance = withdrawnAfterAcceptance,
            declinedBySubstitute = declinedBySubstitute,
            withdrawnBeforeAcceptance = withdrawnBeforeAcceptance,
            distinctSubstitutesUsed = substituteCounts.size,
            topSubstituteShare = if (handoffsWithSubstituteAcceptance == 0) {
                null
            } else {
                substituteCounts.values.max().toDouble() / handoffsWithSubstituteAcceptance
            }
        )
    }
}

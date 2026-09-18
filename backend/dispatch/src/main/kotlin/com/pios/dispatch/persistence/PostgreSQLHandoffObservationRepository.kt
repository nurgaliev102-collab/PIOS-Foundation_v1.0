package com.pios.dispatch.persistence

import com.pios.dispatch.application.HandoffObservationFacts
import com.pios.dispatch.application.HandoffObservationRepository
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.HandoffStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

/**
 * The PostgreSQL-backed adapter for [HandoffObservationRepository] (D-08,
 * `docs/PIOS_D08_HANDOFF_OBSERVATION_IMPLEMENTATION_SPEC.md` §3/§6).
 * Plain JDBC via [JdbcTemplate], no ORM — same convention as every other
 * adapter in this package.
 *
 * ## Where an Assignment's own creation instant comes from
 *
 * `assignments` (confirmed by direct reading of every migration that
 * touches that table, `V1__initial_schema.sql` through
 * `V11__add_proposal_and_assignment_is_test.sql`) has no creation-
 * timestamp column at all — only `status_changed_at`/`arrived_at`/
 * `started_at`/`completed_at`, each `null` until its own transition.
 * Adding one would mean changing [com.pios.dispatch.domain.Assignment]'s
 * own primary constructor, a pre-D-07, pervasively-constructed aggregate
 * — a materially larger change than this feature's own scope, and not
 * something the implementation spec's own database section authorized.
 *
 * Instead, this class sources an Assignment's own creation instant from
 * `dispatch_outbox` (V3): every Assignment's creation publishes exactly
 * one `OrderAssigned` record, keyed by `aggregate_id = assignment.id`
 * (`DispatchAssignmentApplicationService`'s own outbox-record construction
 * confirms this), and that table's own rows are never deleted — only
 * `published_at` is ever set (confirmed: no `DELETE FROM dispatch_outbox`
 * exists anywhere in this module). Reusing this already-permanent,
 * already-populated data avoids any change to `assignments`' own schema
 * or to the widely-used [com.pios.dispatch.domain.Assignment] class
 * entirely. `DISTINCT` guards against a theoretical duplicate outbox row
 * for the same aggregate; in practice there is exactly one.
 */
@Repository
class PostgreSQLHandoffObservationRepository(
    private val jdbcTemplate: JdbcTemplate
) : HandoffObservationRepository {

    override fun observe(committingDriver: DriverReference, windowStart: Instant, windowEnd: Instant): HandoffObservationFacts {
        val assignmentIds = jdbcTemplate.query(
            """
            SELECT DISTINCT a.id
            FROM assignments a
            JOIN dispatch_outbox ob ON ob.aggregate_id = a.id AND ob.event_type = 'OrderAssigned'
            WHERE a.driver_reference = ? AND ob.created_at >= ? AND ob.created_at < ?
            """.trimIndent(),
            { rs, _ -> rs.getString("id") },
            committingDriver.driverId,
            Timestamp.from(windowStart),
            Timestamp.from(windowEnd)
        )

        val totalCommitments = assignmentIds.size
        if (assignmentIds.isEmpty()) {
            return emptyFacts(totalCommitments = 0)
        }

        val placeholders = assignmentIds.joinToString(", ") { "?" }
        val handoffRows = jdbcTemplate.query(
            """
            SELECT assignment_id, status, substitute_accepted_at, substitute_driver_reference
            FROM handoffs
            WHERE assignment_id IN ($placeholders)
            """.trimIndent(),
            { rs, _ -> HandoffRow(rs) },
            *assignmentIds.toTypedArray()
        )

        var handoffsWithSubstituteAcceptance = 0
        var proposedOnly = 0
        var substituteAcceptedOngoing = 0
        var committed = 0
        var refusedAfterAcceptance = 0
        var withdrawnAfterAcceptance = 0
        var declinedBySubstitute = 0
        var withdrawnBeforeAcceptance = 0
        val substituteCounts = mutableMapOf<String, Int>()

        for (rowsForAssignment in handoffRows.groupBy { it.assignmentId }.values) {
            val accepted = rowsForAssignment.filter { it.substituteAcceptedAt != null }
            if (accepted.isNotEmpty()) {
                handoffsWithSubstituteAcceptance++
                val mostRecentlyAccepted = accepted.maxBy { it.substituteAcceptedAt!! }
                substituteCounts.merge(mostRecentlyAccepted.substituteDriverId, 1, Int::plus)
            }
            for (row in rowsForAssignment) {
                val wasAccepted = row.substituteAcceptedAt != null
                when (row.status) {
                    HandoffStatus.PROPOSED -> if (!wasAccepted) proposedOnly++
                    HandoffStatus.SUBSTITUTE_ACCEPTED -> substituteAcceptedOngoing++
                    HandoffStatus.COMMITTED -> committed++
                    HandoffStatus.REFUSED -> if (wasAccepted) refusedAfterAcceptance++ else declinedBySubstitute++
                    HandoffStatus.WITHDRAWN -> if (wasAccepted) withdrawnAfterAcceptance++ else withdrawnBeforeAcceptance++
                }
            }
        }

        return HandoffObservationFacts(
            totalCommitments = totalCommitments,
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

    private fun emptyFacts(totalCommitments: Int) = HandoffObservationFacts(
        totalCommitments = totalCommitments,
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

    private class HandoffRow(rs: ResultSet) {
        val assignmentId: String = rs.getString("assignment_id")
        val status: HandoffStatus = HandoffStatus.valueOf(rs.getString("status"))
        val substituteAcceptedAt: Instant? = rs.getTimestamp("substitute_accepted_at")?.toInstant()
        val substituteDriverId: String = rs.getString("substitute_driver_reference")
    }
}

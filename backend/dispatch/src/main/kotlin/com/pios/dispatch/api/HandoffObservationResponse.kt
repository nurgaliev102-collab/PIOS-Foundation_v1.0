package com.pios.dispatch.api

/**
 * D-08's own owner-only response shape
 * (`docs/PIOS_D08_HANDOFF_OBSERVATION_IMPLEMENTATION_SPEC.md` §9/§10).
 * Every field is a direct count, a timestamp, or a ratio of two such
 * counts — nothing is normalized into a score, percentile, or tier
 * (spec §5's own "this does not become a rating" requirement). [rate]
 * is `null`, not `0`, when [insufficientData] is `true` (spec §6's own
 * empty-history edge case).
 */
data class HandoffObservationResponse(
    val driverId: String,
    val windowStart: String,
    val windowEnd: String,
    val windowWeeks: Long,
    val totalCommitments: Int,
    val handoffsWithSubstituteAcceptance: Int,
    val rate: Double?,
    val insufficientData: Boolean,
    val distinctSubstitutesUsed: Int,
    val topSubstituteShare: Double?,
    val eventBreakdown: HandoffObservationEventBreakdown
)

data class HandoffObservationEventBreakdown(
    val proposedOnly: Int,
    val substituteAccepted: Int,
    val committed: Int,
    val refusedAfterAcceptance: Int,
    val withdrawnAfterAcceptance: Int,
    val declinedBySubstitute: Int,
    val withdrawnBeforeAcceptance: Int
)

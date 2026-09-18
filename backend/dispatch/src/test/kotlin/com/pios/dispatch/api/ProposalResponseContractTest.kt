package com.pios.dispatch.api

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * D-09.1 (Tier 1 Visible) — Architect Review, "Preserve ADR-068 POST
 * /v1/proposals contract": a direct, structural proof that
 * [ProposalResponse]'s own shape was not touched by this feature.
 * `viaTrustedFallback` is deliberately exposed only on
 * [AssignmentResponse] (see [HandoffObservationResponse]-adjacent
 * precedent: `agreedAmount`/`executingDriverId` before it) — this test
 * fails immediately, at the field-name level, if that boundary is ever
 * crossed by a future change.
 */
class ProposalResponseContractTest {

    @Test
    fun `ProposalResponse's own field names are exactly the pre-D-09_1 set, in order -- POST v1 proposals contract is untouched`() {
        val fieldNames = ProposalResponse::class.java.declaredFields
            .filter { !it.isSynthetic }
            .map { it.name }

        assertEquals(
            listOf(
                "proposalId",
                "orderId",
                "driverId",
                "status",
                "statedPrice",
                "createdAt",
                "respondedAt",
                "statedEtaMinutes",
                "isTest"
            ),
            fieldNames
        )
    }
}

package com.pios.dispatch.domain

import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Domain-level unit tests for [Handoff] (D-07, Handoff Protocol),
 * entirely in memory — mirrors [TripTest]'s/[ProposalTest]'s own
 * "pure domain, no repository" style exactly.
 */
class HandoffTest {

    private val assignmentId = AssignmentId(UUID.randomUUID().toString())
    private val order = OrderReference("order-1")
    private val original = DriverReference("driver-original")
    private val substitute = DriverReference("driver-substitute")
    private val passenger = PassengerReference("passenger-1")

    private fun propose(existingActive: Handoff? = null) =
        Handoff.propose(assignmentId, order, original, substitute, passenger, existingActive)

    // --- Creation ---

    @Test
    fun `proposing a handoff starts it in PROPOSED, naming the original and substitute drivers`() {
        val created = propose()

        assertEquals(HandoffStatus.PROPOSED, created.handoff.status)
        assertEquals(original, created.handoff.originalDriver)
        assertEquals(substitute, created.handoff.substituteDriver)
        assertEquals(passenger, created.handoff.passenger)
        assertNull(created.handoff.substituteAcceptedAt)
        assertNull(created.handoff.resolvedAt)
    }

    @Test
    fun `proposing a handoff with the substitute equal to the original driver is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            Handoff.propose(assignmentId, order, original, original, passenger, null)
        }
    }

    @Test
    fun `proposing a handoff while an active handoff already exists for the same assignment is rejected -- single-hop, one-active-at-a-time`() {
        val existing = propose().handoff

        assertFailsWith<IllegalStateException> {
            propose(existingActive = existing)
        }
    }

    @Test
    fun `a passenger reference is optional -- a manual assignment with no Proposal still allows a proposed handoff`() {
        val created = Handoff.propose(assignmentId, order, original, substitute, null, null)

        assertNull(created.handoff.passenger)
    }

    // --- Substitute acceptance (D-07 locked precondition) ---

    @Test
    fun `the substitute explicitly accepting transitions PROPOSED to SUBSTITUTE_ACCEPTED`() {
        val handoff = propose().handoff

        val event = handoff.acceptSubstitute()

        assertEquals(HandoffStatus.SUBSTITUTE_ACCEPTED, handoff.status)
        assertEquals(substitute, event.driverId)
        assertEquals(assignmentId, event.assignmentId)
    }

    @Test
    fun `accepting an already-accepted handoff is rejected`() {
        val handoff = propose().handoff
        handoff.acceptSubstitute()

        assertFailsWith<IllegalStateException> {
            handoff.acceptSubstitute()
        }
    }

    @Test
    fun `accepting a refused handoff is rejected`() {
        val handoff = propose().handoff
        handoff.refuse()

        assertFailsWith<IllegalStateException> {
            handoff.acceptSubstitute()
        }
    }

    // --- Passenger consent (the one constitutive transition) ---

    @Test
    fun `committing requires SUBSTITUTE_ACCEPTED -- a merely PROPOSED handoff cannot be committed directly`() {
        val handoff = propose().handoff

        assertFailsWith<IllegalStateException> {
            handoff.commit()
        }
    }

    @Test
    fun `committing an already-substitute-accepted handoff transitions to COMMITTED and reports both drivers`() {
        val handoff = propose().handoff
        handoff.acceptSubstitute()

        val event = handoff.commit()

        assertEquals(HandoffStatus.COMMITTED, handoff.status)
        assertEquals(original, event.originalDriverId)
        assertEquals(substitute, event.executingDriverId)
    }

    @Test
    fun `committing an already-committed handoff is rejected`() {
        val handoff = propose().handoff
        handoff.acceptSubstitute()
        handoff.commit()

        assertFailsWith<IllegalStateException> {
            handoff.commit()
        }
    }

    // --- Passenger refusal ---

    @Test
    fun `refusing a merely PROPOSED handoff is allowed`() {
        val handoff = propose().handoff

        handoff.refuse()

        assertEquals(HandoffStatus.REFUSED, handoff.status)
    }

    @Test
    fun `refusing a SUBSTITUTE_ACCEPTED handoff is allowed`() {
        val handoff = propose().handoff
        handoff.acceptSubstitute()

        handoff.refuse()

        assertEquals(HandoffStatus.REFUSED, handoff.status)
    }

    @Test
    fun `refusing an already-committed handoff is rejected -- a passenger cannot un-consent`() {
        val handoff = propose().handoff
        handoff.acceptSubstitute()
        handoff.commit()

        assertFailsWith<IllegalStateException> {
            handoff.refuse()
        }
    }

    // --- Withdrawal (D-07: only before consent, never after) ---

    @Test
    fun `withdrawing a merely PROPOSED handoff is allowed`() {
        val handoff = propose().handoff

        handoff.withdraw()

        assertEquals(HandoffStatus.WITHDRAWN, handoff.status)
    }

    @Test
    fun `withdrawing a SUBSTITUTE_ACCEPTED handoff is allowed`() {
        val handoff = propose().handoff
        handoff.acceptSubstitute()

        handoff.withdraw()

        assertEquals(HandoffStatus.WITHDRAWN, handoff.status)
    }

    @Test
    fun `withdrawing an already-committed handoff is unconditionally rejected -- D-07's own locked invariant`() {
        val handoff = propose().handoff
        handoff.acceptSubstitute()
        handoff.commit()

        assertFailsWith<IllegalStateException> {
            handoff.withdraw()
        }
        assertEquals(HandoffStatus.COMMITTED, handoff.status, "withdrawal must never revert a committed handoff")
    }

    @Test
    fun `withdrawing an already-withdrawn handoff is rejected`() {
        val handoff = propose().handoff
        handoff.withdraw()

        assertFailsWith<IllegalStateException> {
            handoff.withdraw()
        }
    }

    // --- Timestamps ---

    @Test
    fun `substituteAcceptedAt and resolvedAt are null on a freshly proposed handoff`() {
        val handoff = propose().handoff

        assertNull(handoff.substituteAcceptedAt)
        assertNull(handoff.resolvedAt)
    }

    @Test
    fun `commit stamps resolvedAt with the moment given, and substituteAcceptedAt stays at its own earlier moment`() {
        val handoff = propose().handoff
        val acceptedAt = Instant.parse("2026-09-18T12:00:00Z")
        val committedAt = Instant.parse("2026-09-18T12:05:00Z")
        handoff.acceptSubstitute(acceptedAt)

        handoff.commit(committedAt)

        assertEquals(acceptedAt, handoff.substituteAcceptedAt)
        assertEquals(committedAt, handoff.resolvedAt)
    }
}

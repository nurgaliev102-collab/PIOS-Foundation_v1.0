package com.pios.dispatch.application

import com.pios.dispatch.domain.Assignment
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.Handoff
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.persistence.InMemoryAssignmentRepository
import com.pios.dispatch.persistence.InMemoryHandoffObservationRepository
import com.pios.dispatch.persistence.InMemoryHandoffRepository
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * D-08 (Handoff Observation Foundation,
 * `docs/PIOS_D08_HANDOFF_OBSERVATION_IMPLEMENTATION_SPEC.md` §12,
 * query/aggregation layer). Exercises [HandoffObservationQueryService]
 * against [InMemoryHandoffObservationRepository] directly -- no HTTP, no
 * authorization -- focused entirely on whether the metric itself (spec
 * §6) is computed correctly. [com.pios.dispatch.api.HandoffControllerTest]
 * covers the same governing predicate through the owner-only endpoint;
 * this file covers the aggregation edge cases that matter most at the
 * repository/service boundary: window arithmetic, re-proposal
 * denominator correctness, and recipient concentration.
 */
class HandoffObservationQueryServiceTest {

    private val assignmentRepository = InMemoryAssignmentRepository()
    private val handoffRepository = InMemoryHandoffRepository()
    private val observationRepository = InMemoryHandoffObservationRepository(assignmentRepository, handoffRepository)
    private val service = HandoffObservationQueryService(observationRepository, windowWeeks = 4)

    private val committingDriver = DriverReference("driver-committing")

    private fun createAssignment(order: String, driver: DriverReference = committingDriver): Assignment {
        val created = Assignment.create(OrderReference(order), driver)
        assignmentRepository.save(created.assignment)
        return created.assignment
    }

    private fun proposeHandoff(assignment: Assignment, substitute: DriverReference, at: Instant = Instant.now()): Handoff {
        val created = Handoff.propose(
            assignmentId = assignment.id,
            order = assignment.order,
            originalDriver = assignment.driver,
            substituteDriver = substitute,
            passenger = null,
            existingActiveHandoff = null,
            at = at
        )
        handoffRepository.save(created.handoff)
        return created.handoff
    }

    @Test
    fun `no Handoff history at all -- empty observation, insufficientData, never a misleading rate`() {
        val result = service.observe(DriverReference("driver-never-existed"))

        assertEquals(0, result.facts.totalCommitments)
        assertEquals(true, result.insufficientData)
        assertNull(result.rate)
    }

    @Test
    fun `a driver with commitments but zero Handoffs reports a real zero rate, not insufficientData`() {
        createAssignment("order-1")
        createAssignment("order-2")

        val result = service.observe(committingDriver)

        assertEquals(2, result.facts.totalCommitments)
        assertEquals(0, result.facts.handoffsWithSubstituteAcceptance)
        assertEquals(false, result.insufficientData)
        assertEquals(0.0, result.rate)
    }

    @Test
    fun `only proposed, never accepted -- does not count toward the numerator`() {
        val assignment = createAssignment("order-1")
        val handoff = proposeHandoff(assignment, DriverReference("substitute-1"))
        handoffRepository.save(handoff)

        val result = service.observe(committingDriver)

        assertEquals(1, result.facts.totalCommitments)
        assertEquals(0, result.facts.handoffsWithSubstituteAcceptance)
        assertEquals(1, result.facts.proposedOnly)
    }

    @Test
    fun `substitute accepted -- counts toward the numerator per the governing predicate`() {
        val assignment = createAssignment("order-1")
        val handoff = proposeHandoff(assignment, DriverReference("substitute-1"))
        handoff.acceptSubstitute()
        handoffRepository.save(handoff)

        val result = service.observe(committingDriver)

        assertEquals(1, result.facts.handoffsWithSubstituteAcceptance)
        assertEquals(1, result.facts.substituteAcceptedOngoing)
        assertEquals(1.0, result.rate)
    }

    @Test
    fun `committed Handoff -- counts, and is reported in its own eventBreakdown bucket`() {
        val assignment = createAssignment("order-1")
        val handoff = proposeHandoff(assignment, DriverReference("substitute-1"))
        handoff.acceptSubstitute()
        handoff.commit()
        handoffRepository.save(handoff)

        val result = service.observe(committingDriver)

        assertEquals(1, result.facts.handoffsWithSubstituteAcceptance)
        assertEquals(1, result.facts.committed)
    }

    @Test
    fun `substitute declined before ever accepting -- does not count`() {
        val assignment = createAssignment("order-1")
        val handoff = proposeHandoff(assignment, DriverReference("substitute-1"))
        handoff.refuse()
        handoffRepository.save(handoff)

        val result = service.observe(committingDriver)

        assertEquals(0, result.facts.handoffsWithSubstituteAcceptance)
        assertEquals(1, result.facts.declinedBySubstitute)
    }

    @Test
    fun `passenger refusal after substitute acceptance -- counts, distinct from a bare substitute decline`() {
        val assignment = createAssignment("order-1")
        val handoff = proposeHandoff(assignment, DriverReference("substitute-1"))
        handoff.acceptSubstitute()
        handoff.refuse()
        handoffRepository.save(handoff)

        val result = service.observe(committingDriver)

        assertEquals(1, result.facts.handoffsWithSubstituteAcceptance)
        assertEquals(1, result.facts.refusedAfterAcceptance)
        assertEquals(0, result.facts.declinedBySubstitute)
    }

    @Test
    fun `withdrawal before substitute acceptance -- does not count`() {
        val assignment = createAssignment("order-1")
        val handoff = proposeHandoff(assignment, DriverReference("substitute-1"))
        handoff.withdraw()
        handoffRepository.save(handoff)

        val result = service.observe(committingDriver)

        assertEquals(0, result.facts.handoffsWithSubstituteAcceptance)
        assertEquals(1, result.facts.withdrawnBeforeAcceptance)
    }

    @Test
    fun `withdrawal after substitute acceptance -- counts`() {
        val assignment = createAssignment("order-1")
        val handoff = proposeHandoff(assignment, DriverReference("substitute-1"))
        handoff.acceptSubstitute()
        handoff.withdraw()
        handoffRepository.save(handoff)

        val result = service.observe(committingDriver)

        assertEquals(1, result.facts.handoffsWithSubstituteAcceptance)
        assertEquals(1, result.facts.withdrawnAfterAcceptance)
    }

    @Test
    fun `an underlying Trip termination is invisible to this metric -- Handoff facts are unaffected`() {
        // Termination lives on Trip/Assignment, never on Handoff -- this
        // metric reads only handoffs/assignments, so nothing here can even
        // express a termination. Documented as a test precisely because
        // the spec (Section 1) calls this out as an easy point of
        // confusion, not because there is any code path to exercise.
        val assignment = createAssignment("order-1")
        val handoff = proposeHandoff(assignment, DriverReference("substitute-1"))
        handoff.acceptSubstitute()
        handoffRepository.save(handoff)

        val result = service.observe(committingDriver)

        assertEquals(1, result.facts.handoffsWithSubstituteAcceptance)
    }

    @Test
    fun `re-proposal after withdrawal counts once toward the numerator, by distinct Assignment, not by Handoff row`() {
        val assignment = createAssignment("order-1")
        val firstAttempt = proposeHandoff(assignment, DriverReference("substitute-1"))
        firstAttempt.acceptSubstitute()
        firstAttempt.withdraw()
        handoffRepository.save(firstAttempt)

        val secondAttempt = Handoff.propose(
            assignmentId = assignment.id,
            order = assignment.order,
            originalDriver = assignment.driver,
            substituteDriver = DriverReference("substitute-2"),
            passenger = null,
            existingActiveHandoff = null
        ).handoff
        secondAttempt.acceptSubstitute()
        secondAttempt.commit()
        handoffRepository.save(secondAttempt)

        val result = service.observe(committingDriver)

        assertEquals(1, result.facts.totalCommitments)
        assertEquals(1, result.facts.handoffsWithSubstituteAcceptance)
        assertEquals(1, result.facts.withdrawnAfterAcceptance)
        assertEquals(1, result.facts.committed)
    }

    @Test
    fun `recipient concentration -- distinct substitutes used and the top substitute's own share`() {
        val a1 = createAssignment("order-1")
        val h1 = proposeHandoff(a1, DriverReference("substitute-popular"))
        h1.acceptSubstitute()
        handoffRepository.save(h1)

        val a2 = createAssignment("order-2")
        val h2 = proposeHandoff(a2, DriverReference("substitute-popular"))
        h2.acceptSubstitute()
        handoffRepository.save(h2)

        val a3 = createAssignment("order-3")
        val h3 = proposeHandoff(a3, DriverReference("substitute-rare"))
        h3.acceptSubstitute()
        handoffRepository.save(h3)

        val result = service.observe(committingDriver)

        assertEquals(3, result.facts.handoffsWithSubstituteAcceptance)
        assertEquals(2, result.facts.distinctSubstitutesUsed)
        assertEquals(2.0 / 3.0, result.facts.topSubstituteShare)
    }

    @Test
    fun `window boundary -- an Assignment created before windowStart is excluded from the denominator`() {
        val assignment = createAssignment("order-1")
        val now = Instant.now()
        observationRepository.recordAssignmentCreatedAt(assignment.id, now.minusSeconds(60L * 24 * 3600))

        val result = service.observe(committingDriver, at = now)

        assertEquals(0, result.facts.totalCommitments)
        assertEquals(true, result.insufficientData)
    }

    @Test
    fun `window boundary -- an Assignment created just inside the window is included`() {
        val assignment = createAssignment("order-1")
        val now = Instant.now()
        // windowWeeks = 4 in this test's own service; 27 days ago is inside
        // a 28-day window measured back from the start of the current week.
        observationRepository.recordAssignmentCreatedAt(assignment.id, now.minusSeconds(27L * 24 * 3600))

        val result = service.observe(committingDriver, at = now)

        assertEquals(1, result.facts.totalCommitments)
    }

    @Test
    fun `another driver's Assignment and Handoff never contribute to this driver's own observation`() {
        val other = DriverReference("driver-other")
        val otherAssignment = createAssignment("order-other", driver = other)
        val handoff = proposeHandoff(otherAssignment, DriverReference("substitute-other"))
        handoff.acceptSubstitute()
        handoffRepository.save(handoff)

        val result = service.observe(committingDriver)

        assertEquals(0, result.facts.totalCommitments)
        assertEquals(true, result.insufficientData)
    }

    @Test
    fun `observation never mutates any Assignment or Handoff it reads`() {
        val assignment = createAssignment("order-1")
        val handoff = proposeHandoff(assignment, DriverReference("substitute-1"))
        handoff.acceptSubstitute()
        handoffRepository.save(handoff)

        service.observe(committingDriver)
        service.observe(committingDriver)
        service.observe(committingDriver)

        val reread = handoffRepository.findById(handoff.id)
        assertEquals("SUBSTITUTE_ACCEPTED", reread?.status?.name)
        assertEquals(1, assignmentRepository.findByDriver(committingDriver).size)
    }
}

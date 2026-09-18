package com.pios.dispatch.application

import com.pios.dispatch.domain.Assignment
import com.pios.dispatch.domain.AssignmentId
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.HandoffId
import com.pios.dispatch.domain.HandoffStatus
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.domain.PassengerReference
import com.pios.dispatch.domain.Proposal
import com.pios.dispatch.domain.Trip
import com.pios.dispatch.domain.TripStatus
import com.pios.dispatch.persistence.InMemoryAssignmentRepository
import com.pios.dispatch.persistence.InMemoryHandoffRepository
import com.pios.dispatch.persistence.InMemoryProposalRepository
import com.pios.dispatch.persistence.InMemoryTripRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Application-level tests for [HandoffApplicationService] (D-07, Handoff
 * Protocol), entirely in memory — mirrors
 * [com.pios.dispatch.application.ProposalAssignmentOrchestrationServiceTest]'s
 * own harness style. Authorization itself is a controller-level concern
 * (see `HandoffController`'s own KDoc) and is exercised by
 * `HandoffControllerTest`, not here — these tests cover state validation,
 * eligibility, and the one cross-aggregate write ([consent] changing the
 * connected Trip's own executing driver).
 */
class HandoffApplicationServiceTest {

    /** Mirrors `FallbackDispatchApplicationServiceTest`'s own identical private fake. */
    private class InMemoryDriverAvailabilityRepository : DriverAvailabilityRepository {
        private val records = mutableMapOf<DriverReference, DriverAvailabilityRecord>()
        override fun markProcessed(eventId: String): Boolean = throw UnsupportedOperationException("not used by this test")
        override fun upsert(record: DriverAvailabilityRecord) {
            records[record.driverReference] = record
        }
        override fun findByDriverReference(driverReference: DriverReference): DriverAvailabilityRecord? =
            records[driverReference]
    }

    private val assignmentRepository = InMemoryAssignmentRepository()
    private val tripRepository = InMemoryTripRepository()
    private val handoffRepository = InMemoryHandoffRepository()
    private val proposalRepository = InMemoryProposalRepository()
    private val driverAvailabilityRepository = InMemoryDriverAvailabilityRepository()
    private val service = HandoffApplicationService(
        assignmentRepository, tripRepository, handoffRepository, proposalRepository, driverAvailabilityRepository
    )

    private val order = OrderReference("order-1")
    private val originalDriver = DriverReference("driver-original")
    private val substituteDriver = DriverReference("driver-substitute")

    private fun seedCommittedAssignment(order: OrderReference = this.order, driver: DriverReference = originalDriver): Assignment {
        val assignment = Assignment.create(order, driver).assignment
        assignmentRepository.save(assignment)
        tripRepository.save(Trip.create(assignment).trip)
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(substituteDriver, available = true))
        return assignment
    }

    @Test
    fun `proposing a handoff for an unknown assignment throws AssignmentNotFoundException`() {
        assertFailsWith<AssignmentNotFoundException> {
            service.propose(ProposeHandoffCommand(AssignmentId("never-created"), substituteDriver.driverId))
        }
    }

    @Test
    fun `proposing a handoff succeeds while the Trip is CREATED`() {
        val assignment = seedCommittedAssignment()

        val created = service.propose(ProposeHandoffCommand(assignment.id, substituteDriver.driverId))

        assertEquals(HandoffStatus.PROPOSED, created.handoff.status)
        assertEquals(originalDriver, created.handoff.originalDriver)
        assertEquals(substituteDriver, created.handoff.substituteDriver)
    }

    @Test
    fun `proposing a handoff succeeds while the Trip is ARRIVED`() {
        val assignment = seedCommittedAssignment()
        tripRepository.findByAssignmentId(assignment.id)!!.arrive()
            .let { tripRepository.save(tripRepository.findByAssignmentId(assignment.id)!!) }

        val created = service.propose(ProposeHandoffCommand(assignment.id, substituteDriver.driverId))

        assertEquals(HandoffStatus.PROPOSED, created.handoff.status)
    }

    @Test
    fun `proposing a handoff once the Trip is IN_PROGRESS is rejected`() {
        val assignment = seedCommittedAssignment()
        val trip = tripRepository.findByAssignmentId(assignment.id)!!
        trip.arrive()
        trip.start()
        tripRepository.save(trip)

        assertFailsWith<IllegalStateException> {
            service.propose(ProposeHandoffCommand(assignment.id, substituteDriver.driverId))
        }
    }

    @Test
    fun `proposing a handoff naming an unregistered substitute is rejected -- eligibility check`() {
        val assignment = Assignment.create(order, originalDriver).assignment
        assignmentRepository.save(assignment)
        tripRepository.save(Trip.create(assignment).trip)
        // Deliberately no driverAvailabilityRepository.upsert for the substitute --
        // they have never been projected into Dispatch's own local view.

        assertFailsWith<IllegalStateException> {
            service.propose(ProposeHandoffCommand(assignment.id, substituteDriver.driverId))
        }
    }

    @Test
    fun `proposing a second handoff while one is already active for the same assignment is rejected`() {
        val assignment = seedCommittedAssignment()
        service.propose(ProposeHandoffCommand(assignment.id, substituteDriver.driverId))
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(DriverReference("driver-third"), available = true))

        assertFailsWith<IllegalStateException> {
            service.propose(ProposeHandoffCommand(assignment.id, "driver-third"))
        }
    }

    @Test
    fun `proposing a handoff denormalizes the order's own passenger from its ACCEPTED proposal`() {
        val assignment = seedCommittedAssignment()
        val passenger = PassengerReference("passenger-1")
        val proposal = Proposal.propose(order, originalDriver, passengerReference = passenger).proposal
        proposal.accept()
        proposalRepository.save(proposal)

        val created = service.propose(ProposeHandoffCommand(assignment.id, substituteDriver.driverId))

        assertEquals(passenger, created.handoff.passenger)
    }

    @Test
    fun `accepting a handoff's own substitute transitions it, and the trip is unaffected`() {
        val assignment = seedCommittedAssignment()
        val created = service.propose(ProposeHandoffCommand(assignment.id, substituteDriver.driverId))

        service.acceptSubstitute(AcceptHandoffSubstituteCommand(created.handoff.id))

        assertEquals(HandoffStatus.SUBSTITUTE_ACCEPTED, handoffRepository.findById(created.handoff.id)?.status)
        assertEquals(originalDriver, tripRepository.findByAssignmentId(assignment.id)?.executingDriver)
    }

    @Test
    fun `accepting an unknown handoff throws HandoffNotFoundException`() {
        assertFailsWith<HandoffNotFoundException> {
            service.acceptSubstitute(AcceptHandoffSubstituteCommand(HandoffId("never-created")))
        }
    }

    @Test
    fun `consenting requires SUBSTITUTE_ACCEPTED -- a merely PROPOSED handoff cannot be consented`() {
        val assignment = seedCommittedAssignment()
        val created = service.propose(ProposeHandoffCommand(assignment.id, substituteDriver.driverId))

        assertFailsWith<IllegalStateException> {
            service.consent(ConsentHandoffCommand(created.handoff.id))
        }
    }

    @Test
    fun `consenting a substitute-accepted handoff commits it and changes the trip's own executing driver`() {
        val assignment = seedCommittedAssignment()
        val created = service.propose(ProposeHandoffCommand(assignment.id, substituteDriver.driverId))
        service.acceptSubstitute(AcceptHandoffSubstituteCommand(created.handoff.id))

        service.consent(ConsentHandoffCommand(created.handoff.id))

        assertEquals(HandoffStatus.COMMITTED, handoffRepository.findById(created.handoff.id)?.status)
        assertEquals(substituteDriver, tripRepository.findByAssignmentId(assignment.id)?.executingDriver)
        assertEquals(originalDriver, tripRepository.findByAssignmentId(assignment.id)?.driver, "the original committing driver must never change")
    }

    @Test
    fun `consenting does not touch agreedAmount`() {
        val assignment = Assignment.create(order, originalDriver).assignment
        assignmentRepository.save(assignment)
        tripRepository.save(Trip.create(assignment, agreedAmount = "620").trip)
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(substituteDriver, available = true))
        val created = service.propose(ProposeHandoffCommand(assignment.id, substituteDriver.driverId))
        service.acceptSubstitute(AcceptHandoffSubstituteCommand(created.handoff.id))

        service.consent(ConsentHandoffCommand(created.handoff.id))

        assertEquals("620", tripRepository.findByAssignmentId(assignment.id)?.agreedAmount)
    }

    @Test
    fun `consenting once the trip has left CREATED-ARRIVED since substitute acceptance is rejected`() {
        val assignment = seedCommittedAssignment()
        val created = service.propose(ProposeHandoffCommand(assignment.id, substituteDriver.driverId))
        service.acceptSubstitute(AcceptHandoffSubstituteCommand(created.handoff.id))
        val trip = tripRepository.findByAssignmentId(assignment.id)!!
        trip.arrive()
        trip.start()
        tripRepository.save(trip)

        assertFailsWith<IllegalStateException> {
            service.consent(ConsentHandoffCommand(created.handoff.id))
        }
        assertEquals(originalDriver, trip.executingDriver, "a rejected consent must never change the executing driver")
    }

    @Test
    fun `refusing a handoff never touches the trip`() {
        val assignment = seedCommittedAssignment()
        val created = service.propose(ProposeHandoffCommand(assignment.id, substituteDriver.driverId))
        service.acceptSubstitute(AcceptHandoffSubstituteCommand(created.handoff.id))

        service.refuse(RefuseHandoffCommand(created.handoff.id))

        assertEquals(HandoffStatus.REFUSED, handoffRepository.findById(created.handoff.id)?.status)
        assertEquals(originalDriver, tripRepository.findByAssignmentId(assignment.id)?.executingDriver)
        assertEquals(TripStatus.CREATED, tripRepository.findByAssignmentId(assignment.id)?.status)
    }

    @Test
    fun `withdrawing before consent succeeds`() {
        val assignment = seedCommittedAssignment()
        val created = service.propose(ProposeHandoffCommand(assignment.id, substituteDriver.driverId))

        service.withdraw(WithdrawHandoffCommand(created.handoff.id))

        assertEquals(HandoffStatus.WITHDRAWN, handoffRepository.findById(created.handoff.id)?.status)
    }

    @Test
    fun `withdrawing after consent is rejected, and the trip's own executing driver stays the substitute`() {
        val assignment = seedCommittedAssignment()
        val created = service.propose(ProposeHandoffCommand(assignment.id, substituteDriver.driverId))
        service.acceptSubstitute(AcceptHandoffSubstituteCommand(created.handoff.id))
        service.consent(ConsentHandoffCommand(created.handoff.id))

        assertFailsWith<IllegalStateException> {
            service.withdraw(WithdrawHandoffCommand(created.handoff.id))
        }
        assertEquals(HandoffStatus.COMMITTED, handoffRepository.findById(created.handoff.id)?.status)
        assertEquals(substituteDriver, tripRepository.findByAssignmentId(assignment.id)?.executingDriver)
    }

    @Test
    fun `after a withdrawn handoff, a new handoff can be proposed for the same assignment`() {
        val assignment = seedCommittedAssignment()
        val first = service.propose(ProposeHandoffCommand(assignment.id, substituteDriver.driverId))
        service.withdraw(WithdrawHandoffCommand(first.handoff.id))
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(DriverReference("driver-third"), available = true))

        val second = service.propose(ProposeHandoffCommand(assignment.id, "driver-third"))

        assertEquals(HandoffStatus.PROPOSED, second.handoff.status)
        assertNull(second.handoff.substituteAcceptedAt)
    }
}

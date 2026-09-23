package com.pios.dispatch.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.dispatch.domain.Assignment
import com.pios.dispatch.domain.AssignmentStatus
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.domain.TripStatus
import com.pios.dispatch.persistence.InMemoryAssignmentRepository
import com.pios.dispatch.persistence.InMemoryTripRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DispatchAssignmentApplicationServiceTest {

    private val repository = InMemoryAssignmentRepository()
    private val tripRepository = InMemoryTripRepository()
    private val service = DispatchAssignmentApplicationService(repository, tripRepository = tripRepository)
    private val order = OrderReference("order-1")
    private val driver = DriverReference("driver-1")

    @Test
    fun `handling a command with no existing assignments creates a new assignment`() {
        val command = AssignOrderCommand(order, driver)

        val result = service.handle(command)

        assertEquals(order, result.assignment.order)
        assertEquals(driver, result.assignment.driver)
    }

    @Test
    fun `handling a command produces an OrderAssigned event matching the command`() {
        val command = AssignOrderCommand(order, driver)

        val result = service.handle(command)

        assertEquals(order, result.event.orderId)
        assertEquals(driver, result.event.driverId)
    }

    @Test
    fun `handling a command for an order that already has an assignment is rejected`() {
        val existing = Assignment.create(order, driver).assignment
        val command = AssignOrderCommand(order, DriverReference("driver-2"))

        assertFailsWith<IllegalStateException> {
            service.handle(command, existingAssignments = listOf(existing))
        }
    }

    @Test
    fun `handling a command persists the created assignment through the repository`() {
        val command = AssignOrderCommand(order, driver)

        val result = service.handle(command)

        assertEquals(order, repository.findById(result.assignment.id)?.order)
    }

    @Test
    fun `accepting an assignment matching the command produces an AssignmentAccepted event`() {
        val assignment = Assignment.create(order, driver).assignment
        val command = AcceptAssignmentCommand(assignment.id)

        val event = service.acceptAssignment(assignment, command)

        assertEquals(order, event.orderId)
        assertEquals(driver, event.driverId)
    }

    @Test
    fun `accepting an assignment whose id does not match the command is rejected`() {
        val assignment = Assignment.create(order, driver).assignment
        val otherAssignment = Assignment.create(OrderReference("order-2"), driver).assignment
        val command = AcceptAssignmentCommand(otherAssignment.id)

        assertFailsWith<IllegalArgumentException> {
            service.acceptAssignment(assignment, command)
        }
    }

    @Test
    fun `accepting an already-accepted assignment through the service is rejected`() {
        val assignment = Assignment.create(order, driver).assignment
        assignment.accept()
        val command = AcceptAssignmentCommand(assignment.id)

        assertFailsWith<IllegalStateException> {
            service.acceptAssignment(assignment, command)
        }
    }

    @Test
    fun `accepting an assignment persists its ACCEPTED status through the repository`() {
        val assignment = Assignment.create(order, driver).assignment

        service.acceptAssignment(assignment, AcceptAssignmentCommand(assignment.id))

        assertEquals(AssignmentStatus.ACCEPTED, repository.findById(assignment.id)?.status)
    }

    // --- Ride lifecycle (ADR-040: Assignment Ride Lifecycle; converged onto Trip, ADR-063/Task 12) ---
    //
    // Ride-progress is now validated and persisted by Trip, not Assignment
    // (Task 12, Trip Ride-Progress Convergence) -- these three tests assert
    // against `tripRepository`, not `repository` (Assignment's own repository),
    // for exactly the status/timestamp fields that moved. Assignment's own
    // status is proven to stay put (never reaches ARRIVED/IN_PROGRESS/COMPLETED)
    // in the dedicated convergence test below.

    @Test
    fun `arriving a saved assignment produces an AssignmentArrived event and persists ARRIVED on its trip`() {
        val created = service.handle(AssignOrderCommand(order, driver))

        val event = service.arriveAssignment(ArriveAssignmentCommand(created.assignment.id))

        assertEquals(order, event.orderId)
        assertEquals(driver, event.driverId)
        assertEquals(TripStatus.ARRIVED, tripRepository.findByAssignmentId(created.assignment.id)?.status)
    }

    @Test
    fun `starting a saved, arrived assignment produces an AssignmentStarted event and persists IN_PROGRESS on its trip`() {
        val created = service.handle(AssignOrderCommand(order, driver))
        service.arriveAssignment(ArriveAssignmentCommand(created.assignment.id))

        val event = service.startAssignment(StartAssignmentCommand(created.assignment.id))

        assertEquals(order, event.orderId)
        assertEquals(TripStatus.IN_PROGRESS, tripRepository.findByAssignmentId(created.assignment.id)?.status)
    }

    @Test
    fun `completing a saved, in-progress assignment produces an AssignmentCompleted event and persists COMPLETED on its trip`() {
        val created = service.handle(AssignOrderCommand(order, driver))
        service.arriveAssignment(ArriveAssignmentCommand(created.assignment.id))
        service.startAssignment(StartAssignmentCommand(created.assignment.id))

        val event = service.completeAssignment(CompleteAssignmentCommand(created.assignment.id))

        assertEquals(order, event.orderId)
        assertEquals(TripStatus.COMPLETED, tripRepository.findByAssignmentId(created.assignment.id)?.status)
    }

    @Test
    fun `starting an assignment that never arrived is rejected`() {
        val created = service.handle(AssignOrderCommand(order, driver))

        assertFailsWith<IllegalStateException> {
            service.startAssignment(StartAssignmentCommand(created.assignment.id))
        }
    }

    @Test
    fun `the full ride lifecycle never changes Assignment's own status -- Trip alone owns ride progress`() {
        val created = service.handle(AssignOrderCommand(order, driver))

        service.arriveAssignment(ArriveAssignmentCommand(created.assignment.id))
        service.startAssignment(StartAssignmentCommand(created.assignment.id))
        service.completeAssignment(CompleteAssignmentCommand(created.assignment.id))

        assertEquals(AssignmentStatus.CREATED, repository.findById(created.assignment.id)?.status)
    }

    // --- D-06 (Settlement as Evidence): completion forwards the completing Trip's own agreedAmount ---

    @Test
    fun `completing an assignment forwards the connected Trip's own agreedAmount on AssignmentCompleted`() {
        val recordingOutboxRepository = RecordingOutboxRepository()
        val priceOrder = OrderReference("order-price-1")
        val priceDriver = DriverReference("driver-price-1")
        val serviceWithAgreedAmount = DispatchAssignmentApplicationService(
            assignmentRepository = InMemoryAssignmentRepository(),
            outboxRepository = recordingOutboxRepository,
            tripRepository = InMemoryTripRepository()
        )
        val created = serviceWithAgreedAmount.handle(
            AssignOrderCommand(priceOrder, priceDriver, agreedAmount = "350")
        )
        serviceWithAgreedAmount.arriveAssignment(ArriveAssignmentCommand(created.assignment.id))
        serviceWithAgreedAmount.startAssignment(StartAssignmentCommand(created.assignment.id))

        serviceWithAgreedAmount.completeAssignment(CompleteAssignmentCommand(created.assignment.id))

        val record = recordingOutboxRepository.records.single { it.eventType == "AssignmentCompleted" }
        val payload = ObjectMapper().readTree(record.payload).get("payload")
        assertEquals("350", payload.get("statedPrice").asText())
        assertEquals(1, ObjectMapper().readTree(record.payload).get("eventVersion").asInt())
    }

    @Test
    fun `completing an assignment with no agreedAmount on its Trip forwards a null statedPrice on AssignmentCompleted`() {
        val recordingOutboxRepository = RecordingOutboxRepository()
        val serviceWithNoAgreedAmount = DispatchAssignmentApplicationService(
            assignmentRepository = InMemoryAssignmentRepository(),
            outboxRepository = recordingOutboxRepository,
            tripRepository = InMemoryTripRepository()
        )
        // No agreedAmount on the command -- mirrors the manual-assignment path
        // (POST /v1/assignments), which has no Proposal at all.
        val created = serviceWithNoAgreedAmount.handle(
            AssignOrderCommand(OrderReference("order-price-2"), DriverReference("driver-price-2"))
        )
        serviceWithNoAgreedAmount.arriveAssignment(ArriveAssignmentCommand(created.assignment.id))
        serviceWithNoAgreedAmount.startAssignment(StartAssignmentCommand(created.assignment.id))

        serviceWithNoAgreedAmount.completeAssignment(CompleteAssignmentCommand(created.assignment.id))

        val record = recordingOutboxRepository.records.single { it.eventType == "AssignmentCompleted" }
        val payload = ObjectMapper().readTree(record.payload).get("payload")
        assertTrue(payload.get("statedPrice").isNull)
    }

    @Test
    fun `AssignmentCompleted preserves committer and adds executing driver after Handoff`() {
        val recordingOutboxRepository = RecordingOutboxRepository()
        val assignmentRepository = InMemoryAssignmentRepository()
        val trips = InMemoryTripRepository()
        val service = DispatchAssignmentApplicationService(
            assignmentRepository = assignmentRepository,
            outboxRepository = recordingOutboxRepository,
            tripRepository = trips
        )
        val committer = DriverReference("driver-attribution-committer")
        val executor = DriverReference("driver-attribution-executor")
        val created = service.handle(AssignOrderCommand(OrderReference("order-attribution"), committer))
        val trip = trips.findByAssignmentId(created.assignment.id)!!
        trip.assignExecutingDriver(executor)
        trips.save(trip)
        service.arriveAssignment(ArriveAssignmentCommand(created.assignment.id))
        service.startAssignment(StartAssignmentCommand(created.assignment.id))

        service.completeAssignment(CompleteAssignmentCommand(created.assignment.id))

        val record = recordingOutboxRepository.records.single { it.eventType == "AssignmentCompleted" }
        val payload = ObjectMapper().readTree(record.payload).get("payload")
        assertEquals(committer.driverId, payload.get("driverId").asText())
        assertEquals(executor.driverId, payload.get("executingDriverId").asText())
        assertEquals(1, ObjectMapper().readTree(record.payload).get("eventVersion").asInt())
    }

    @Test
    fun `completing an assignment never queries a Proposal repository -- two ACCEPTED proposals for the same order do not affect the amount forwarded`() {
        // D-06: even if the order somehow has two ACCEPTED proposals on file
        // (the ADR-080 termination + redispatch scenario), completion must
        // forward only the completing Trip's own captured agreedAmount --
        // never a lookup into any Proposal store. This service is not even
        // given a ProposalRepository any more; there is nothing to query.
        val recordingOutboxRepository = RecordingOutboxRepository()
        val priceOrder = OrderReference("order-price-3")
        val priceDriver = DriverReference("driver-price-3")
        val service = DispatchAssignmentApplicationService(
            assignmentRepository = InMemoryAssignmentRepository(),
            outboxRepository = recordingOutboxRepository,
            tripRepository = InMemoryTripRepository()
        )
        val created = service.handle(AssignOrderCommand(priceOrder, priceDriver, agreedAmount = "900"))
        service.arriveAssignment(ArriveAssignmentCommand(created.assignment.id))
        service.startAssignment(StartAssignmentCommand(created.assignment.id))

        service.completeAssignment(CompleteAssignmentCommand(created.assignment.id))

        val record = recordingOutboxRepository.records.single { it.eventType == "AssignmentCompleted" }
        val payload = ObjectMapper().readTree(record.payload).get("payload")
        assertEquals("900", payload.get("statedPrice").asText())
    }

    /** Mirrors `DispatchAssignmentApplicationServiceRideProgressConvergenceTest`'s own identical fake. */
    private class RecordingOutboxRepository : OutboxRepository {
        val records = mutableListOf<OutboxRecord>()
        override fun save(record: OutboxRecord): OutboxRecord {
            records.add(record)
            return record.copy(id = records.size.toLong())
        }
        override fun findUnpublished(): List<OutboxRecord> = records
        override fun markPublished(id: Long) = Unit
        override fun countUnpublished(): OutboxBacklog = OutboxBacklog(pending = records.size.toLong(), oldestPendingCreatedAt = null)
    }
}

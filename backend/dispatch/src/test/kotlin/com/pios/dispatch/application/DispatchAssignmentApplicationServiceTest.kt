package com.pios.dispatch.application

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
}

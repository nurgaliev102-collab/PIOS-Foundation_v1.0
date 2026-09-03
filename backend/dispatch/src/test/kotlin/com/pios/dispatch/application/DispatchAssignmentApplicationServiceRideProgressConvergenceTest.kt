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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Task 12 (Trip Ride-Progress Convergence): proves the two guarantees
 * specific to this task that no other test file already covers --
 * self-healing for a pre-existing Assignment with no connected Trip yet,
 * and the dual-publish migration window (both the legacy `Assignment*`
 * and the new `Trip*` outbox events, for the same transition). Ordinary
 * Trip-authoritative-state assertions already live alongside their
 * original ADR-040 tests in [DispatchAssignmentApplicationServiceTest].
 */
class DispatchAssignmentApplicationServiceRideProgressConvergenceTest {

    private val assignmentRepository = InMemoryAssignmentRepository()
    private val tripRepository = InMemoryTripRepository()
    private val recordingOutboxRepository = RecordingOutboxRepository()
    private val service = DispatchAssignmentApplicationService(
        assignmentRepository = assignmentRepository,
        outboxRepository = recordingOutboxRepository,
        tripRepository = tripRepository
    )
    private val order = OrderReference("order-1")
    private val driver = DriverReference("driver-1")

    // --- Self-heal: an Assignment saved before Trip existed (or through any
    // path that bypassed DispatchAssignmentApplicationService.handle) has no
    // connected Trip -- a ride-progress action must still work, not throw. ---

    @Test
    fun `arriving a legacy assignment with no connected trip self-heals by creating one`() {
        // Saved directly through the repository, bypassing service.handle --
        // simulates an Assignment that predates Trip's own existence (Task 11).
        val legacyAssignment = Assignment.create(order, driver).assignment
        assignmentRepository.save(legacyAssignment)
        assertNull(tripRepository.findByAssignmentId(legacyAssignment.id))

        val event = service.arriveAssignment(ArriveAssignmentCommand(legacyAssignment.id))

        assertEquals(order, event.orderId)
        val trip = tripRepository.findByAssignmentId(legacyAssignment.id)
        assertNotNull(trip)
        assertEquals(TripStatus.ARRIVED, trip.status)
    }

    @Test
    fun `a self-healed trip can still be carried through start and complete`() {
        val legacyAssignment = Assignment.create(order, driver).assignment
        assignmentRepository.save(legacyAssignment)

        service.arriveAssignment(ArriveAssignmentCommand(legacyAssignment.id))
        service.startAssignment(StartAssignmentCommand(legacyAssignment.id))
        service.completeAssignment(CompleteAssignmentCommand(legacyAssignment.id))

        assertEquals(TripStatus.COMPLETED, tripRepository.findByAssignmentId(legacyAssignment.id)?.status)
    }

    @Test
    fun `self-healing never touches the legacy assignment's own status`() {
        val legacyAssignment = Assignment.create(order, driver).assignment
        assignmentRepository.save(legacyAssignment)

        service.arriveAssignment(ArriveAssignmentCommand(legacyAssignment.id))

        assertEquals(AssignmentStatus.CREATED, assignmentRepository.findById(legacyAssignment.id)?.status)
    }

    // --- Dual-publish migration window (ADR-063 Consequences) ---

    @Test
    fun `completing a trip publishes both the legacy AssignmentCompleted event and the new TripCompleted event`() {
        val created = service.handle(AssignOrderCommand(order, driver))
        service.arriveAssignment(ArriveAssignmentCommand(created.assignment.id))
        service.startAssignment(StartAssignmentCommand(created.assignment.id))
        recordingOutboxRepository.records.clear()

        service.completeAssignment(CompleteAssignmentCommand(created.assignment.id))

        val eventTypes = recordingOutboxRepository.records.map { it.eventType }
        assertTrue(eventTypes.contains("AssignmentCompleted"), "expected the legacy AssignmentCompleted record to still be published, got $eventTypes")
        assertTrue(eventTypes.contains("TripCompleted"), "expected the new TripCompleted record to be published, got $eventTypes")
    }

    @Test
    fun `AssignmentCompleted keeps its original routing key so Order Management's existing binding keeps working`() {
        val created = service.handle(AssignOrderCommand(order, driver))
        service.arriveAssignment(ArriveAssignmentCommand(created.assignment.id))
        service.startAssignment(StartAssignmentCommand(created.assignment.id))
        recordingOutboxRepository.records.clear()

        service.completeAssignment(CompleteAssignmentCommand(created.assignment.id))

        val legacyRecord = recordingOutboxRepository.records.single { it.eventType == "AssignmentCompleted" }
        assertEquals("assignment.completed", legacyRecord.routingKey)
        val newRecord = recordingOutboxRepository.records.single { it.eventType == "TripCompleted" }
        assertEquals("trip.completed", newRecord.routingKey)
    }

    @Test
    fun `arriving and starting also each dual-publish their legacy and new event pair`() {
        val created = service.handle(AssignOrderCommand(order, driver))
        recordingOutboxRepository.records.clear()

        service.arriveAssignment(ArriveAssignmentCommand(created.assignment.id))
        val afterArrive = recordingOutboxRepository.records.map { it.eventType }
        assertTrue(afterArrive.containsAll(listOf("AssignmentArrived", "TripArrived")), "got $afterArrive")

        recordingOutboxRepository.records.clear()
        service.startAssignment(StartAssignmentCommand(created.assignment.id))
        val afterStart = recordingOutboxRepository.records.map { it.eventType }
        assertTrue(afterStart.containsAll(listOf("AssignmentStarted", "TripStarted")), "got $afterStart")
    }

    /** A minimal [OutboxRepository] fake that simply records what was saved, in order -- for assertions only, mirrors the pattern already established in `TripCreationTransactionTest`'s own `failingOutboxRepository`. */
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

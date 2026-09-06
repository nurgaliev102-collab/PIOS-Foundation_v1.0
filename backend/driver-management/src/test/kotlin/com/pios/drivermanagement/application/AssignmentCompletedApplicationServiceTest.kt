package com.pios.drivermanagement.application

import com.pios.drivermanagement.domain.DriverId
import com.pios.drivermanagement.persistence.InMemoryAssignmentCompletedRepository
import com.pios.drivermanagement.persistence.InMemoryDriverClientsRepository
import com.pios.drivermanagement.persistence.InMemoryDriverMilestonesRepository
import com.pios.drivermanagement.persistence.InMemoryOrderPassengerRepository
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AssignmentCompletedApplicationServiceTest {

    private val assignmentCompletedRepository = InMemoryAssignmentCompletedRepository()
    private val driverMilestonesRepository = InMemoryDriverMilestonesRepository()
    private val orderPassengerRepository = InMemoryOrderPassengerRepository()
    private val driverClientsRepository = InMemoryDriverClientsRepository()
    private val service = AssignmentCompletedApplicationService(
        assignmentCompletedRepository,
        driverMilestonesRepository,
        orderPassengerRepository,
        driverClientsRepository
    )

    private fun command(eventId: String, driverId: String, orderId: String, occurredAt: String) =
        AssignmentCompletedUpdateCommand(eventId, driverId, orderId, Instant.parse(occurredAt))

    @Test
    fun `handling a new AssignmentCompleted increments the driver's completed ride count`() {
        service.handle(command("event-1", "driver-1", "order-1", "2026-08-03T09:00:00Z"))

        val milestones = driverMilestonesRepository.findByDriverId(DriverId("driver-1"))

        assertEquals(1L, milestones?.completedRidesCount)
        assertEquals(1, milestones?.currentStreakWeeks)
    }

    @Test
    fun `redelivering the same eventId does not double-count the ride`() {
        val cmd = command("event-2", "driver-2", "order-2", "2026-08-03T09:00:00Z")

        service.handle(cmd)
        service.handle(cmd)

        assertEquals(1L, driverMilestonesRepository.findByDriverId(DriverId("driver-2"))?.completedRidesCount)
    }

    @Test
    fun `two distinct events for the same driver in consecutive weeks grow the streak`() {
        service.handle(command("event-3", "driver-3", "order-3", "2026-08-03T09:00:00Z"))
        service.handle(command("event-4", "driver-3", "order-4", "2026-08-10T09:00:00Z"))

        val milestones = driverMilestonesRepository.findByDriverId(DriverId("driver-3"))

        assertEquals(2L, milestones?.completedRidesCount)
        assertEquals(2, milestones?.currentStreakWeeks)
    }

    @Test
    fun `an unrelated driver is unaffected by another driver's completed ride`() {
        service.handle(command("event-5", "driver-4", "order-5", "2026-08-03T09:00:00Z"))

        assertNull(driverMilestonesRepository.findByDriverId(DriverId("driver-5")))
    }

    // --- Phase 2 extension: repeat clients (docs/PIOS_GROWTH_LOOPS_TZ_V1.md Section 2.1) ---

    @Test
    fun `a passenger's first completed ride with a driver does not count as a repeat client yet`() {
        orderPassengerRepository.recordOrderPassenger("order-6", "passenger-a")

        service.handle(command("event-6", "driver-6", "order-6", "2026-08-03T09:00:00Z"))

        assertEquals(0, driverMilestonesRepository.findByDriverId(DriverId("driver-6"))?.repeatClientsCount)
    }

    @Test
    fun `a passenger's second completed ride with the same driver counts them as one repeat client`() {
        orderPassengerRepository.recordOrderPassenger("order-7a", "passenger-b")
        orderPassengerRepository.recordOrderPassenger("order-7b", "passenger-b")

        service.handle(command("event-7a", "driver-7", "order-7a", "2026-08-03T09:00:00Z"))
        service.handle(command("event-7b", "driver-7", "order-7b", "2026-08-10T09:00:00Z"))

        assertEquals(1, driverMilestonesRepository.findByDriverId(DriverId("driver-7"))?.repeatClientsCount)
    }

    @Test
    fun `a third ride from an already-repeat passenger does not double-count the repeat client`() {
        orderPassengerRepository.recordOrderPassenger("order-8a", "passenger-c")
        orderPassengerRepository.recordOrderPassenger("order-8b", "passenger-c")
        orderPassengerRepository.recordOrderPassenger("order-8c", "passenger-c")

        service.handle(command("event-8a", "driver-8", "order-8a", "2026-08-03T09:00:00Z"))
        service.handle(command("event-8b", "driver-8", "order-8b", "2026-08-10T09:00:00Z"))
        service.handle(command("event-8c", "driver-8", "order-8c", "2026-08-17T09:00:00Z"))

        assertEquals(1, driverMilestonesRepository.findByDriverId(DriverId("driver-8"))?.repeatClientsCount)
    }

    @Test
    fun `two different passengers each riding twice count as two repeat clients`() {
        orderPassengerRepository.recordOrderPassenger("order-9a", "passenger-d")
        orderPassengerRepository.recordOrderPassenger("order-9b", "passenger-d")
        orderPassengerRepository.recordOrderPassenger("order-9c", "passenger-e")
        orderPassengerRepository.recordOrderPassenger("order-9d", "passenger-e")

        service.handle(command("event-9a", "driver-9", "order-9a", "2026-08-03T09:00:00Z"))
        service.handle(command("event-9b", "driver-9", "order-9b", "2026-08-10T09:00:00Z"))
        service.handle(command("event-9c", "driver-9", "order-9c", "2026-08-03T09:00:00Z"))
        service.handle(command("event-9d", "driver-9", "order-9d", "2026-08-10T09:00:00Z"))

        assertEquals(2, driverMilestonesRepository.findByDriverId(DriverId("driver-9"))?.repeatClientsCount)
    }

    @Test
    fun `an AssignmentCompleted for an order with no known passenger still counts the ride, just not a repeat client`() {
        service.handle(command("event-10", "driver-10", "order-unknown", "2026-08-03T09:00:00Z"))

        val milestones = driverMilestonesRepository.findByDriverId(DriverId("driver-10"))

        assertEquals(1L, milestones?.completedRidesCount)
        assertEquals(0, milestones?.repeatClientsCount)
    }

    @Test
    fun `the same passenger riding with two different drivers counts as a repeat client for each driver independently`() {
        orderPassengerRepository.recordOrderPassenger("order-11a", "passenger-f")
        orderPassengerRepository.recordOrderPassenger("order-11b", "passenger-f")
        orderPassengerRepository.recordOrderPassenger("order-11c", "passenger-f")
        orderPassengerRepository.recordOrderPassenger("order-11d", "passenger-f")

        service.handle(command("event-11a", "driver-11a", "order-11a", "2026-08-03T09:00:00Z"))
        service.handle(command("event-11b", "driver-11a", "order-11b", "2026-08-10T09:00:00Z"))
        service.handle(command("event-11c", "driver-11b", "order-11c", "2026-08-03T09:00:00Z"))
        service.handle(command("event-11d", "driver-11b", "order-11d", "2026-08-10T09:00:00Z"))

        assertEquals(1, driverMilestonesRepository.findByDriverId(DriverId("driver-11a"))?.repeatClientsCount)
        assertEquals(1, driverMilestonesRepository.findByDriverId(DriverId("driver-11b"))?.repeatClientsCount)
    }
}

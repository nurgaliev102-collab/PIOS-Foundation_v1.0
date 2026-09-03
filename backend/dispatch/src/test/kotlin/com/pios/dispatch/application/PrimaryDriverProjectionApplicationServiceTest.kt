package com.pios.dispatch.application

import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.PassengerReference
import com.pios.dispatch.persistence.InMemoryPrimaryDriverRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Fast, DB-free unit test of [PrimaryDriverProjectionApplicationService]'s
 * own idempotency logic, using the in-memory
 * [com.pios.dispatch.persistence.InMemoryPrimaryDriverRepository]. Mirrors
 * [DriverAvailabilityProjectionApplicationServiceTest]'s own exact
 * structure. The real-transaction and real-broker guarantees are proven
 * separately, against real infrastructure, by
 * `com.pios.dispatch.persistence.PrimaryConnectionConsumerIntegrationTest`
 * and `com.pios.dispatch.persistence.PrimaryConnectionIdempotencyIntegrationTest`.
 */
class PrimaryDriverProjectionApplicationServiceTest {

    private val repository = InMemoryPrimaryDriverRepository()
    private val service = PrimaryDriverProjectionApplicationService(repository)

    @Test
    fun `a new designated event applies its effect`() {
        service.handleDesignated(PrimaryDriverDesignatedCommand("event-1", "passenger-1", "driver-1"))

        assertEquals(DriverReference("driver-1"), repository.findByPassenger(PassengerReference("passenger-1"))?.primaryDriverId)
    }

    @Test
    fun `a redelivered designated eventId does not re-apply its effect`() {
        service.handleDesignated(PrimaryDriverDesignatedCommand("event-2", "passenger-2", "driver-a"))
        service.handleDesignated(PrimaryDriverDesignatedCommand("event-2", "passenger-2", "driver-b"))

        assertEquals(DriverReference("driver-a"), repository.findByPassenger(PassengerReference("passenger-2"))?.primaryDriverId)
    }

    @Test
    fun `distinct eventIds for the same passenger each apply, in order`() {
        service.handleDesignated(PrimaryDriverDesignatedCommand("event-3", "passenger-3", "driver-a"))
        service.handleDesignated(PrimaryDriverDesignatedCommand("event-4", "passenger-3", "driver-b"))

        assertEquals(DriverReference("driver-b"), repository.findByPassenger(PassengerReference("passenger-3"))?.primaryDriverId)
    }

    @Test
    fun `a cleared event removes the projection entry`() {
        service.handleDesignated(PrimaryDriverDesignatedCommand("event-5", "passenger-4", "driver-a"))

        service.handleCleared(PrimaryDriverClearedCommand("event-6", "passenger-4"))

        assertNull(repository.findByPassenger(PassengerReference("passenger-4")))
    }

    @Test
    fun `a redelivered cleared eventId does not re-apply its effect`() {
        service.handleDesignated(PrimaryDriverDesignatedCommand("event-7", "passenger-5", "driver-a"))
        service.handleCleared(PrimaryDriverClearedCommand("event-8", "passenger-5"))
        // Re-designate, then redeliver the SAME clear eventId.
        service.handleDesignated(PrimaryDriverDesignatedCommand("event-9", "passenger-5", "driver-a"))

        service.handleCleared(PrimaryDriverClearedCommand("event-8", "passenger-5"))

        assertEquals(DriverReference("driver-a"), repository.findByPassenger(PassengerReference("passenger-5"))?.primaryDriverId)
    }

    @Test
    fun `clearing a passenger with no designation at all is a harmless no-op`() {
        service.handleCleared(PrimaryDriverClearedCommand("event-10", "passenger-never-designated"))

        assertNull(repository.findByPassenger(PassengerReference("passenger-never-designated")))
    }
}

package com.pios.dispatch.application

import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.PassengerReference
import com.pios.dispatch.persistence.InMemoryTrustedDriverRepository
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Fast, DB-free unit test of [TrustedDriverProjectionApplicationService]'s
 * own idempotency logic, using the in-memory
 * [com.pios.dispatch.persistence.InMemoryTrustedDriverRepository]. Mirrors
 * [PrimaryDriverProjectionApplicationServiceTest]'s own exact structure
 * (ADR-068, Relationship-Ordered Fallback Dispatch — Trusted → Network →
 * Open Marketplace, Part 1).
 */
class TrustedDriverProjectionApplicationServiceTest {

    private val repository = InMemoryTrustedDriverRepository()
    private val service = TrustedDriverProjectionApplicationService(repository)

    @Test
    fun `a new established event applies its effect`() {
        service.handleEstablished(TrustedDriverEstablishedCommand("event-1", "passenger-1", "driver-1"))

        assertTrue(repository.isTrusted(PassengerReference("passenger-1"), DriverReference("driver-1")))
    }

    @Test
    fun `a redelivered established eventId does not re-apply its effect`() {
        service.handleEstablished(TrustedDriverEstablishedCommand("event-2", "passenger-2", "driver-a"))
        // A redelivery of the same eventId is skipped entirely -- even
        // carrying a different driverId (which a real redelivery never
        // would) proves the skip, not merely a harmless repeat of the
        // identical effect.
        service.handleEstablished(TrustedDriverEstablishedCommand("event-2", "passenger-2", "driver-b"))

        assertTrue(repository.isTrusted(PassengerReference("passenger-2"), DriverReference("driver-a")))
        assertFalse(repository.isTrusted(PassengerReference("passenger-2"), DriverReference("driver-b")))
    }

    @Test
    fun `a passenger can have more than one trusted driver at once`() {
        service.handleEstablished(TrustedDriverEstablishedCommand("event-3", "passenger-3", "driver-a"))
        service.handleEstablished(TrustedDriverEstablishedCommand("event-4", "passenger-3", "driver-b"))

        assertTrue(repository.isTrusted(PassengerReference("passenger-3"), DriverReference("driver-a")))
        assertTrue(repository.isTrusted(PassengerReference("passenger-3"), DriverReference("driver-b")))
    }

    @Test
    fun `a removed event removes only the named pair from the projection`() {
        service.handleEstablished(TrustedDriverEstablishedCommand("event-5", "passenger-4", "driver-a"))
        service.handleEstablished(TrustedDriverEstablishedCommand("event-6", "passenger-4", "driver-b"))

        service.handleRemoved(TrustedDriverRemovedCommand("event-7", "passenger-4", "driver-a"))

        assertFalse(repository.isTrusted(PassengerReference("passenger-4"), DriverReference("driver-a")))
        assertTrue(repository.isTrusted(PassengerReference("passenger-4"), DriverReference("driver-b")))
    }

    @Test
    fun `a redelivered removed eventId does not re-apply its effect`() {
        service.handleEstablished(TrustedDriverEstablishedCommand("event-8", "passenger-5", "driver-a"))
        service.handleRemoved(TrustedDriverRemovedCommand("event-9", "passenger-5", "driver-a"))
        // Re-establish, then redeliver the SAME removed eventId.
        service.handleEstablished(TrustedDriverEstablishedCommand("event-10", "passenger-5", "driver-a"))

        service.handleRemoved(TrustedDriverRemovedCommand("event-9", "passenger-5", "driver-a"))

        assertTrue(repository.isTrusted(PassengerReference("passenger-5"), DriverReference("driver-a")))
    }

    @Test
    fun `removing a pair that was never trusted is a harmless no-op`() {
        service.handleRemoved(TrustedDriverRemovedCommand("event-11", "passenger-never-trusted", "driver-a"))

        assertFalse(repository.isTrusted(PassengerReference("passenger-never-trusted"), DriverReference("driver-a")))
    }
}

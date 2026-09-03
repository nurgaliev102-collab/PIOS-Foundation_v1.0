package com.pios.passengerexperience.application

import com.pios.passengerexperience.domain.Connection
import com.pios.passengerexperience.domain.ConnectionId
import com.pios.passengerexperience.domain.DriverReference
import com.pios.passengerexperience.domain.PassengerReference
import com.pios.passengerexperience.persistence.InMemoryConnectionRepository
import com.pios.passengerexperience.persistence.InMemoryPrimaryConnectionRepository
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Task 14 (First Refusal Foundation), Test B: primary driver clearing
 * publishes the correct event -- and only when the removed connection was
 * actually the passenger's own primary, never otherwise. Also proves the
 * pre-existing idempotent-DELETE behavior (ADR-054) is unaffected.
 */
class RemoveConnectionApplicationServiceTest {

    private val primaryConnectionRepository = InMemoryPrimaryConnectionRepository()
    private val connectionRepository = InMemoryConnectionRepository(
        onConnectionDeleted = primaryConnectionRepository::removeIfPointingAt
    )
    private val outboxRepository = RecordingOutboxRepository()
    private val service = RemoveConnectionApplicationService(
        connectionRepository,
        primaryConnectionRepository,
        outboxRepository = outboxRepository
    )
    private val setPrimaryService = SetPrimaryConnectionApplicationService(connectionRepository, primaryConnectionRepository)

    private val primary = Connection(
        id = ConnectionId("connection-primary"),
        driverId = DriverReference("driver-1"),
        passengerReference = PassengerReference("passenger-1"),
        createdAt = Instant.parse("2026-09-03T12:00:00Z")
    )
    private val nonPrimary = Connection(
        id = ConnectionId("connection-other"),
        driverId = DriverReference("driver-2"),
        passengerReference = PassengerReference("passenger-1"),
        createdAt = Instant.parse("2026-09-03T12:05:00Z")
    )

    @Test
    fun `removing the passenger's primary connection publishes a PrimaryConnectionCleared outbox record`() {
        connectionRepository.save(primary)
        setPrimaryService.handle(primary.id)

        service.handle(primary.id)

        val record = outboxRepository.records.single()
        assertEquals("PrimaryConnectionCleared", record.eventType)
        assertEquals("primary.connection.cleared", record.routingKey)
        assertTrue(record.payload.contains("\"passengerReference\":\"passenger-1\""))
    }

    @Test
    fun `removing the passenger's primary connection still clears primary_connections exactly as before`() {
        connectionRepository.save(primary)
        setPrimaryService.handle(primary.id)

        service.handle(primary.id)

        assertNull(primaryConnectionRepository.findByPassenger(primary.passengerReference))
    }

    @Test
    fun `removing a non-primary connection publishes no event at all`() {
        connectionRepository.save(primary)
        connectionRepository.save(nonPrimary)
        setPrimaryService.handle(primary.id)

        service.handle(nonPrimary.id)

        assertEquals(emptyList(), outboxRepository.records)
        // Unaffected -- the real primary is untouched.
        assertEquals(primary.id, primaryConnectionRepository.findByPassenger(primary.passengerReference))
    }

    @Test
    fun `removing a connection when the passenger never had any primary publishes no event, exactly as before`() {
        connectionRepository.save(nonPrimary)

        service.handle(nonPrimary.id)

        assertEquals(emptyList(), outboxRepository.records)
    }

    @Test
    fun `removing an unknown connection id is still a silent no-op, exactly as before`() {
        service.handle(ConnectionId("never-existed"))

        assertEquals(emptyList(), outboxRepository.records)
    }
}

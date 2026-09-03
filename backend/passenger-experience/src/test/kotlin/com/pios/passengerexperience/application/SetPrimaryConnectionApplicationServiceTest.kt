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
import kotlin.test.assertTrue

/**
 * Task 14 (First Refusal Foundation), Test A: primary driver designation
 * publishes the correct event. Also proves the pre-existing designation
 * behavior (ADR-054) is completely unaffected by this task's own outbox
 * addition (Phase 0's own "the existing primary connection behavior must
 * remain intact" requirement).
 */
class SetPrimaryConnectionApplicationServiceTest {

    private val connectionRepository = InMemoryConnectionRepository()
    private val primaryConnectionRepository = InMemoryPrimaryConnectionRepository()
    private val outboxRepository = RecordingOutboxRepository()
    private val service = SetPrimaryConnectionApplicationService(
        connectionRepository,
        primaryConnectionRepository,
        outboxRepository = outboxRepository
    )

    private val connection = Connection(
        id = ConnectionId("connection-1"),
        driverId = DriverReference("driver-1"),
        passengerReference = PassengerReference("passenger-1"),
        createdAt = Instant.parse("2026-09-03T12:00:00Z")
    )

    @Test
    fun `designating a primary still records it in primary_connections exactly as before`() {
        connectionRepository.save(connection)

        service.handle(connection.id)

        assertEquals(connection.id, primaryConnectionRepository.findByPassenger(connection.passengerReference))
    }

    @Test
    fun `designating a primary publishes a PrimaryConnectionDesignated outbox record`() {
        connectionRepository.save(connection)

        service.handle(connection.id)

        val record = outboxRepository.records.single()
        assertEquals("PrimaryConnectionDesignated", record.eventType)
        assertEquals("primary.connection.designated", record.routingKey)
        assertTrue(record.payload.contains("\"passengerReference\":\"passenger-1\""))
        assertTrue(record.payload.contains("\"driverId\":\"driver-1\""))
    }

    @Test
    fun `the published event carries no connection metadata beyond the two opaque identifiers`() {
        connectionRepository.save(connection)

        service.handle(connection.id)

        val record = outboxRepository.records.single()
        // ADR-062: "no passenger profile data, no phone numbers, no names,
        // no unrelated connection metadata" -- createdAt is Connection's
        // own metadata, not part of the ratified minimum data contract.
        assertTrue(!record.payload.contains(connection.createdAt.toString()))
    }

    @Test
    fun `re-designating a different connection as primary publishes a fresh event with the new driver`() {
        val other = Connection(
            id = ConnectionId("connection-2"),
            driverId = DriverReference("driver-2"),
            passengerReference = PassengerReference("passenger-1"),
            createdAt = Instant.parse("2026-09-03T12:05:00Z")
        )
        connectionRepository.save(connection)
        connectionRepository.save(other)

        service.handle(connection.id)
        service.handle(other.id)

        assertEquals(2, outboxRepository.records.size)
        assertEquals(other.id, primaryConnectionRepository.findByPassenger(connection.passengerReference))
        assertTrue(outboxRepository.records.last().payload.contains("\"driverId\":\"driver-2\""))
    }
}

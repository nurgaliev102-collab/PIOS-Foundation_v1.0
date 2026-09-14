package com.pios.passengerexperience.application

import com.pios.passengerexperience.domain.DriverReference
import com.pios.passengerexperience.domain.PassengerReference
import com.pios.passengerexperience.persistence.InMemoryConnectionRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The mandatory Sprint 7B scenario: driver=Артур, passenger=Регина.
 */
class CreateConnectionApplicationServiceTest {
    private val repository = InMemoryConnectionRepository()
    private val service = CreateConnectionApplicationService(repository)
    private val handler = RetrieveConnectionsForDriverHandler(repository)

    @Test
    fun `Artur connects to Regina, and the connection is retrievable from Artur's own side`() {
        val outcome = service.handle(CreateConnectionCommand(DriverReference("artur"), PassengerReference("regina")))

        assertTrue(outcome.created)
        val connections = handler.handle(DriverReference("artur"))
        assertTrue(connections.any { it.passengerReference == PassengerReference("regina") })
    }

    @Test
    fun `opening the same invitation link twice does not create a duplicate connection`() {
        val first = service.handle(CreateConnectionCommand(DriverReference("artur"), PassengerReference("regina")))
        val second = service.handle(CreateConnectionCommand(DriverReference("artur"), PassengerReference("regina")))

        assertTrue(first.created)
        assertEquals(false, second.created)
        assertEquals(first.connection.id, second.connection.id)
        assertEquals(1, handler.handle(DriverReference("artur")).size)
    }
}

/**
 * ADR-068 (Relationship-Ordered Fallback Dispatch, Part 1): Create
 * Connection now publishes [com.pios.passengerexperience.domain.ConnectionEstablished]
 * -- but only on an actual creation, never on the idempotent
 * already-exists path. A separate class, mirroring
 * [SetPrimaryConnectionApplicationServiceTest]'s own convention of keeping
 * event-publication assertions apart from the pre-existing behavioral
 * tests above.
 */
class CreateConnectionApplicationServiceEventPublicationTest {
    private val repository = InMemoryConnectionRepository()
    private val outboxRepository = RecordingOutboxRepository()
    private val service = CreateConnectionApplicationService(repository, outboxRepository = outboxRepository)

    @Test
    fun `creating a new connection publishes a ConnectionEstablished outbox record`() {
        service.handle(CreateConnectionCommand(DriverReference("artur"), PassengerReference("regina")))

        val record = outboxRepository.records.single()
        assertEquals("ConnectionEstablished", record.eventType)
        assertEquals("connection.established", record.routingKey)
        assertTrue(record.payload.contains("\"passengerReference\":\"regina\""))
        assertTrue(record.payload.contains("\"driverId\":\"artur\""))
    }

    @Test
    fun `opening the same invitation link twice publishes only one ConnectionEstablished event`() {
        service.handle(CreateConnectionCommand(DriverReference("artur"), PassengerReference("regina")))
        service.handle(CreateConnectionCommand(DriverReference("artur"), PassengerReference("regina")))

        assertEquals(1, outboxRepository.records.size)
    }
}

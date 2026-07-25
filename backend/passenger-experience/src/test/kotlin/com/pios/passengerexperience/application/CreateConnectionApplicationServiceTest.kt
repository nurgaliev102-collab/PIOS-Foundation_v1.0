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

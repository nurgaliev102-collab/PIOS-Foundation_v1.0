package com.pios.passengerexperience.persistence

import com.pios.passengerexperience.application.CreateConnectionApplicationService
import com.pios.passengerexperience.application.CreateConnectionCommand
import com.pios.passengerexperience.application.RetrieveConnectionsForDriverHandler
import com.pios.passengerexperience.domain.DriverReference
import com.pios.passengerexperience.domain.PassengerReference
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Proves Sprint 7B's own mandatory scenarios against a real PostgreSQL
 * database, not merely in memory: driver=Артур, passenger=Регина, and
 * idempotency of opening the same invitation link twice. Mirrors
 * `com.pios.drivermanagement.persistence.PostgreSQLCreateDriverTest`'s own
 * pattern. Uses a randomized driver id per test, consistent with this
 * project's own established fix for fixed-id test pollution.
 */
class PostgreSQLConnectionIntegrationTest {

    private val repository = PostgreSQLConnectionRepository(JdbcTemplate(PostgreSQLTestDatabase.dataSource))
    private val service = CreateConnectionApplicationService(repository)
    private val handler = RetrieveConnectionsForDriverHandler(repository)

    @Test
    fun `Artur connects to Regina through PostgreSQL, and Artur's own connections include Regina`() {
        val driverId = DriverReference("artur-${UUID.randomUUID()}")
        val passengerReference = PassengerReference("regina-${UUID.randomUUID()}")

        service.handle(CreateConnectionCommand(driverId, passengerReference))

        val connections = handler.handle(driverId)
        assertTrue(connections.any { it.passengerReference == passengerReference })
    }

    @Test
    fun `opening the same link twice through PostgreSQL does not create a duplicate row`() {
        val driverId = DriverReference("artur-${UUID.randomUUID()}")
        val passengerReference = PassengerReference("regina-${UUID.randomUUID()}")

        val first = service.handle(CreateConnectionCommand(driverId, passengerReference))
        val second = service.handle(CreateConnectionCommand(driverId, passengerReference))

        assertTrue(first.created)
        assertEquals(false, second.created)
        assertEquals(1, handler.handle(driverId).size)
    }
}

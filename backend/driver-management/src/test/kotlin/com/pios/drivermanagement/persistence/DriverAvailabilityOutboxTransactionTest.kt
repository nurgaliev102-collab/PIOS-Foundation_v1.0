package com.pios.drivermanagement.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.drivermanagement.application.DeclareAvailabilityCommand
import com.pios.drivermanagement.application.DriverAvailabilityApplicationService
import com.pios.drivermanagement.application.OutboxRecord
import com.pios.drivermanagement.application.OutboxRepository
import com.pios.drivermanagement.domain.Availability
import com.pios.drivermanagement.domain.Driver
import com.pios.drivermanagement.domain.DriverId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Proves the core guarantee this task exists to establish (ADR-032,
 * applying the pattern already proven in Order Management's Outbox
 * Foundation v1.0): a Driver's own availability change and its
 * corresponding outbox record are saved in one atomic PostgreSQL
 * transaction, against a real database -- not merely that each repository
 * works in isolation. Also proves that a no-op availability declaration
 * (no actual change) never creates a false outbox record.
 */
class DriverAvailabilityOutboxTransactionTest {

    private val dataSource = PostgreSQLTestDatabase.dataSource
    private val driverRepository = PostgreSQLDriverRepository(JdbcTemplate(dataSource))
    private val outboxRepository = PostgreSQLOutboxRepository(JdbcTemplate(dataSource))
    private val transactionRunner = SpringTransactionRunner(TransactionTemplate(DataSourceTransactionManager(dataSource)))
    private val objectMapper = ObjectMapper()
    private val service = DriverAvailabilityApplicationService(driverRepository, outboxRepository, transactionRunner, objectMapper)

    @Test
    fun `declaring a changed availability persists both the driver and a matching outbox record together`() {
        val driverId = DriverId("outbox-tx-driver-${java.util.UUID.randomUUID()}")
        val driver = Driver(id = driverId, availability = Availability.UNAVAILABLE)
        val command = DeclareAvailabilityCommand(driverId, Availability.AVAILABLE)

        service.handle(driver, command)

        assertEquals(Availability.AVAILABLE, driverRepository.findById(driverId)?.availability)
        val records = outboxRepository.findUnpublished().filter { it.aggregateId == driverId.value }
        assertEquals(1, records.size)
        assertEquals("DriverAvailabilityChanged", records.single().eventType)
        assertEquals("driver.availability.changed", records.single().routingKey)
    }

    @Test
    fun `declaring the same availability the driver already has creates no outbox record`() {
        val driverId = DriverId("outbox-tx-driver-${java.util.UUID.randomUUID()}")
        val driver = Driver(id = driverId, availability = Availability.AVAILABLE)
        val command = DeclareAvailabilityCommand(driverId, Availability.AVAILABLE)

        val event = service.handle(driver, command)

        assertNull(event)
        assertNull(driverRepository.findById(driverId))
        assertTrue(outboxRepository.findUnpublished().none { it.aggregateId == driverId.value })
    }

    @Test
    fun `if the outbox save fails, the driver's own availability change is rolled back too`() {
        val driverId = DriverId("outbox-tx-driver-${java.util.UUID.randomUUID()}")
        val driver = Driver(id = driverId, availability = Availability.UNAVAILABLE)
        val command = DeclareAvailabilityCommand(driverId, Availability.AVAILABLE)

        val failingOutboxRepository = object : OutboxRepository {
            override fun save(record: OutboxRecord): OutboxRecord = throw RuntimeException("simulated outbox failure")
            override fun findUnpublished(): List<OutboxRecord> = emptyList()
            override fun markPublished(id: Long) = Unit
        }
        val failingService = DriverAvailabilityApplicationService(driverRepository, failingOutboxRepository, transactionRunner, objectMapper)

        assertFailsWith<RuntimeException> {
            failingService.handle(driver, command)
        }

        // The driver's availability must still be absent/unchanged in the
        // database -- the failed outbox save must have rolled back the
        // driver's own persistence within the same transaction, not just
        // its own.
        assertNull(driverRepository.findById(driverId))
    }
}

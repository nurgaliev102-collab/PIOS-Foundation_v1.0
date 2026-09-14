package com.pios.drivermanagement.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.drivermanagement.application.DeclareAvailabilityCommand
import com.pios.drivermanagement.application.DriverAvailabilityApplicationService
import com.pios.drivermanagement.application.EventPublisher
import com.pios.drivermanagement.application.OutboxRelay
import com.pios.drivermanagement.domain.Availability
import com.pios.drivermanagement.domain.Driver
import com.pios.drivermanagement.domain.DriverId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Proves the transport envelope Driver Management Event Publishing v1.0
 * requires (applying the same hardening already proven for Order
 * Management): an event identity distinct from outbox persistence
 * bookkeeping, and stability of that identity across a retried publish.
 */
class DriverAvailabilityChangedEnvelopeTest {

    private val objectMapper = ObjectMapper()
    private val dataSource = PostgreSQLTestDatabase.dataSource
    private val driverRepository = PostgreSQLDriverRepository(JdbcTemplate(dataSource))
    private val outboxRepository = PostgreSQLOutboxRepository(JdbcTemplate(dataSource))
    private val transactionRunner = SpringTransactionRunner(TransactionTemplate(DataSourceTransactionManager(dataSource)))
    private val service = DriverAvailabilityApplicationService(driverRepository, outboxRepository, transactionRunner, objectMapper)

    @Test
    fun `the persisted envelope carries a stable eventId, an explicit eventVersion, and a business occurredAt distinct from outbox bookkeeping`() {
        val driverId = DriverId("envelope-driver-${java.util.UUID.randomUUID()}")
        val driver = Driver(id = driverId, availability = Availability.UNAVAILABLE)

        val event = service.handle(driver, DeclareAvailabilityCommand(driverId, Availability.AVAILABLE))
        requireNotNull(event)

        val record = outboxRepository.findUnpublished().single { it.aggregateId == driverId.value }
        val envelope = objectMapper.readTree(record.payload)

        assertTrue(envelope.get("eventId").asText().isNotBlank())
        assertEquals("DriverAvailabilityChanged", envelope.get("eventType").asText())
        assertEquals(1, envelope.get("eventVersion").asInt())
        assertEquals(event.occurredAt.toString(), envelope.get("occurredAt").asText())
        assertEquals(driverId.value, envelope.get("payload").get("driverId").asText())
        assertEquals("AVAILABLE", envelope.get("payload").get("availability").asText())
        // ADR-069 Part 1: additive payload field, eventVersion stays 1.
        assertEquals(false, envelope.get("payload").get("isTest").asBoolean())

        assertNotNull(record.createdAt)
        assertNotEquals(record.createdAt.toString(), envelope.get("occurredAt").asText())
    }

    @Test
    fun `a test driver's own declaration carries isTest true on the published envelope`() {
        val driverId = DriverId("envelope-driver-${java.util.UUID.randomUUID()}")
        val driver = Driver(id = driverId, availability = Availability.UNAVAILABLE, isTest = true)

        service.handle(driver, DeclareAvailabilityCommand(driverId, Availability.AVAILABLE))

        val record = outboxRepository.findUnpublished().single { it.aggregateId == driverId.value }
        val envelope = objectMapper.readTree(record.payload)

        assertEquals(true, envelope.get("payload").get("isTest").asBoolean())
    }

    @Test
    fun `two distinct availability declarations never share an eventId`() {
        val firstDriverId = DriverId("envelope-driver-${java.util.UUID.randomUUID()}")
        val secondDriverId = DriverId("envelope-driver-${java.util.UUID.randomUUID()}")

        service.handle(Driver(id = firstDriverId, availability = Availability.UNAVAILABLE), DeclareAvailabilityCommand(firstDriverId, Availability.AVAILABLE))
        service.handle(Driver(id = secondDriverId, availability = Availability.UNAVAILABLE), DeclareAvailabilityCommand(secondDriverId, Availability.AVAILABLE))

        val firstEventId = objectMapper.readTree(
            outboxRepository.findUnpublished().single { it.aggregateId == firstDriverId.value }.payload
        ).get("eventId").asText()
        val secondEventId = objectMapper.readTree(
            outboxRepository.findUnpublished().single { it.aggregateId == secondDriverId.value }.payload
        ).get("eventId").asText()

        assertNotEquals(firstEventId, secondEventId)
    }

    @Test
    fun `a failed publish leaves the record pending and a retried publish carries the identical eventId`() {
        val driverId = DriverId("envelope-driver-${java.util.UUID.randomUUID()}")
        val driver = Driver(id = driverId, availability = Availability.UNAVAILABLE)
        service.handle(driver, DeclareAvailabilityCommand(driverId, Availability.AVAILABLE))
        val marker = driverId.value
        val originalPayload = outboxRepository.findUnpublished()
            .single { it.aggregateId == marker }.payload

        // findUnpublished() returns every pending record in the shared
        // test database, including unrelated ones other test classes may
        // have left behind -- so the simulated failure must target only
        // this test's own record by its unique marker, not "whichever
        // record the relay happens to process first."
        val capturedPayloads = mutableListOf<String>()
        var failedOnce = false
        val flakyPublisher = object : EventPublisher {
            override fun publish(routingKey: String, payload: String) {
                if (!payload.contains(marker)) return
                capturedPayloads.add(payload)
                if (!failedOnce) {
                    failedOnce = true
                    throw RuntimeException("simulated broker confirm failure")
                }
            }
        }
        val relay = OutboxRelay(outboxRepository, flakyPublisher)

        relay.relay()
        assertTrue(outboxRepository.findUnpublished().any { it.aggregateId == marker })

        relay.relay()
        assertTrue(outboxRepository.findUnpublished().none { it.aggregateId == marker })

        assertEquals(2, capturedPayloads.size)
        assertEquals(originalPayload, capturedPayloads[0])
        assertEquals(originalPayload, capturedPayloads[1])
    }
}

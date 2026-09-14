package com.pios.dispatch.application

import com.pios.dispatch.domain.DriverReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Fast, DB-free unit test of [DriverAvailabilityProjectionApplicationService]'s
 * own idempotency logic, using an in-memory fake [DriverAvailabilityRepository].
 * The real-transaction and real-broker guarantees are proven separately,
 * against real infrastructure, by
 * `com.pios.dispatch.persistence.DriverAvailabilityProjectionTransactionTest`
 * and `com.pios.dispatch.persistence.DriverAvailabilityIdempotencyIntegrationTest`.
 */
class DriverAvailabilityProjectionApplicationServiceTest {

    private class InMemoryDriverAvailabilityRepository : DriverAvailabilityRepository {
        val processedEventIds = mutableSetOf<String>()
        val records = mutableMapOf<String, DriverAvailabilityRecord>()

        override fun markProcessed(eventId: String): Boolean = processedEventIds.add(eventId)
        override fun upsert(record: DriverAvailabilityRecord) {
            records[record.driverReference.driverId] = record
        }
        override fun findByDriverReference(driverReference: DriverReference): DriverAvailabilityRecord? =
            records[driverReference.driverId]
    }

    private val repository = InMemoryDriverAvailabilityRepository()
    private val service = DriverAvailabilityProjectionApplicationService(repository)

    @Test
    fun `a new event applies its availability effect`() {
        service.handle(DriverAvailabilityUpdateCommand(eventId = "event-1", driverReference = "driver-1", available = true))

        assertEquals(true, repository.findByDriverReference(DriverReference("driver-1"))?.available)
    }

    @Test
    fun `a redelivered eventId does not re-apply its effect`() {
        service.handle(DriverAvailabilityUpdateCommand(eventId = "event-2", driverReference = "driver-2", available = true))
        service.handle(DriverAvailabilityUpdateCommand(eventId = "event-2", driverReference = "driver-2", available = false))

        assertEquals(true, repository.findByDriverReference(DriverReference("driver-2"))?.available)
    }

    @Test
    fun `distinct eventIds for the same driver each apply`() {
        service.handle(DriverAvailabilityUpdateCommand(eventId = "event-3", driverReference = "driver-3", available = true))
        service.handle(DriverAvailabilityUpdateCommand(eventId = "event-4", driverReference = "driver-3", available = false))

        assertEquals(false, repository.findByDriverReference(DriverReference("driver-3"))?.available)
        assertTrue(repository.processedEventIds.containsAll(listOf("event-3", "event-4")))
    }

    // --- ADR-069: isTest projection ---

    @Test
    fun `an event with isTest omitted projects a null (unknown) classification`() {
        service.handle(DriverAvailabilityUpdateCommand(eventId = "event-5", driverReference = "driver-5", available = true))

        assertEquals(null, repository.findByDriverReference(DriverReference("driver-5"))?.isTest)
    }

    @Test
    fun `an event with isTest true projects true`() {
        service.handle(DriverAvailabilityUpdateCommand(eventId = "event-6", driverReference = "driver-6", available = true, isTest = true))

        assertEquals(true, repository.findByDriverReference(DriverReference("driver-6"))?.isTest)
    }

    @Test
    fun `a later event refreshes isTest rather than keeping the first-ever value`() {
        service.handle(DriverAvailabilityUpdateCommand(eventId = "event-7", driverReference = "driver-7", available = true, isTest = true))
        service.handle(DriverAvailabilityUpdateCommand(eventId = "event-8", driverReference = "driver-7", available = false, isTest = false))

        assertEquals(false, repository.findByDriverReference(DriverReference("driver-7"))?.isTest)
    }
}

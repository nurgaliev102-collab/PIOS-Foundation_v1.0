package com.pios.drivermanagement.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.drivermanagement.domain.DriverId
import com.pios.drivermanagement.persistence.InMemoryDriverRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * ADR-073 (Driver-to-Driver Referral -- Single-Hop Origin Fact), Part 2:
 * unit coverage for [CreateDriverApplicationService.handle]'s own
 * `invitedByDriverId` resolution, run in memory (no PostgreSQL required) --
 * a bad, self-referencing, or nonexistent inviter code must degrade to
 * `null` rather than fail the registering driver's own request; only a
 * valid, already-saved, distinct inviter is ever stored.
 */
class CreateDriverApplicationServiceTest {

    private val repository = InMemoryDriverRepository()
    private val service = CreateDriverApplicationService(repository)

    @Test
    fun `a valid, already-registered inviter is stored on the new driver`() {
        val inviter = service.handle(CreateDriverCommand(DriverId("inviter-valid")))

        val invited = service.handle(CreateDriverCommand(DriverId("invited-valid"), invitedByDriverId = inviter.id.value))

        assertEquals(inviter.id.value, invited.invitedByDriverId)
        assertEquals(inviter.id.value, repository.findById(invited.id)?.invitedByDriverId)
    }

    @Test
    fun `no invitedByDriverId at all results in null, the same as before this field existed`() {
        val driver = service.handle(CreateDriverCommand(DriverId("invited-missing")))

        assertNull(driver.invitedByDriverId)
    }

    @Test
    fun `a self-referencing invitedByDriverId degrades to null rather than failing registration`() {
        val driver = service.handle(CreateDriverCommand(DriverId("invited-self"), invitedByDriverId = "invited-self"))

        assertNull(driver.invitedByDriverId)
        assertEquals(true, repository.findById(DriverId("invited-self")) != null)
    }

    @Test
    fun `an invitedByDriverId naming no existing driver degrades to null rather than failing registration`() {
        val driver = service.handle(
            CreateDriverCommand(DriverId("invited-nonexistent"), invitedByDriverId = "no-such-driver-anywhere")
        )

        assertNull(driver.invitedByDriverId)
        assertEquals(true, repository.findById(DriverId("invited-nonexistent")) != null)
    }

    @Test
    fun `a blank invitedByDriverId degrades to null rather than failing registration`() {
        val driver = service.handle(CreateDriverCommand(DriverId("invited-blank"), invitedByDriverId = "   "))

        assertNull(driver.invitedByDriverId)
    }

    @Test
    fun `registration publishes an initial unavailable state with its test classification`() {
        val records = mutableListOf<OutboxRecord>()
        val outbox = object : OutboxRepository {
            override fun save(record: OutboxRecord): OutboxRecord {
                records += record
                return record
            }
            override fun findUnpublished(): List<OutboxRecord> = records
            override fun markPublished(id: Long) = Unit
            override fun countUnpublished(): OutboxBacklog = OutboxBacklog(records.size.toLong(), null)
        }
        val serviceWithOutbox = CreateDriverApplicationService(repository, outboxRepository = outbox)

        val driver = serviceWithOutbox.handle(CreateDriverCommand(DriverId("new-driver-with-projection"), isTest = true))

        val record = records.single()
        val envelope = ObjectMapper().readTree(record.payload)
        assertEquals(driver.id.value, record.aggregateId)
        assertEquals("DriverAvailabilityChanged", record.eventType)
        assertEquals("UNAVAILABLE", envelope.get("payload").get("availability").asText())
        assertEquals(true, envelope.get("payload").get("isTest").asBoolean())
    }
}

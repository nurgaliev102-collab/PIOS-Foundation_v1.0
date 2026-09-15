package com.pios.drivermanagement.application

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
}

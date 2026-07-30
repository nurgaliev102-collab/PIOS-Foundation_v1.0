package com.pios.identity.application

import com.pios.identity.persistence.InMemoryIdentityRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AssociateDriverApplicationServiceTest {
    private val repository = InMemoryIdentityRepository()
    private val createService = CreateIdentityApplicationService(repository)
    private val service = AssociateDriverApplicationService(repository)

    @Test
    fun `associating a driver updates and persists the identity`() {
        val identity = createService.handle(CreateIdentityCommand(null))
        assertNull(identity.driverId)

        val updated = service.handle(AssociateDriverCommand(identity.id.value, "ILDAR001"))

        assertEquals("ILDAR001", updated.driverId)
        assertEquals("ILDAR001", repository.findById(identity.id)?.driverId)
    }

    @Test
    fun `associating a driver for an unknown identity fails`() {
        assertFailsWith<IdentityNotFoundException> {
            service.handle(AssociateDriverCommand("unknown-identity", "ILDAR001"))
        }
    }

    @Test
    fun `associating a blank driver id fails`() {
        val identity = createService.handle(CreateIdentityCommand(null))

        assertFailsWith<IllegalArgumentException> {
            service.handle(AssociateDriverCommand(identity.id.value, ""))
        }
    }
}

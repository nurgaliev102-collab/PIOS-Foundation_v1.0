package com.pios.identity.application

import com.pios.identity.persistence.InMemoryIdentityRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CreateIdentityApplicationServiceTest {
    private val repository = InMemoryIdentityRepository()
    private val service = CreateIdentityApplicationService(repository)

    @Test
    fun `creating an identity with a phone saves it and returns it with a generated id`() {
        val identity = service.handle(CreateIdentityCommand("+79991234567"))

        assertEquals("+79991234567", identity.phone?.value)
        assertEquals(identity.phone?.value, repository.findById(identity.id)?.phone?.value)
    }

    @Test
    fun `creating an identity without a phone is allowed`() {
        val identity = service.handle(CreateIdentityCommand(null))

        assertNull(identity.phone)
    }

    @Test
    fun `two identities created with the same phone get different ids`() {
        val first = service.handle(CreateIdentityCommand("+79991234567"))
        val second = service.handle(CreateIdentityCommand("+79991234567"))

        assertEquals(false, first.id == second.id)
    }

    @Test
    fun `creating an identity with a malformed phone fails`() {
        assertFailsWith<IllegalArgumentException> {
            service.handle(CreateIdentityCommand("not-a-phone"))
        }
    }
}

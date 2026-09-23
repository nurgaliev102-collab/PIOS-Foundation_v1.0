package com.pios.networkmanagement.application

import com.pios.networkmanagement.persistence.InMemoryPersonRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class CreatePersonApplicationServiceTest {
    private val repository = InMemoryPersonRepository()
    private val service = CreatePersonApplicationService(repository)

    @Test
    fun `creating a person saves it and returns it with a generated id`() {
        val person = service.handle(CreatePersonCommand("Артур", "+79999999999", "test-" + java.util.UUID.randomUUID()))

        assertNotNull(person.id.value)
        assertEquals("Артур", person.name)
        assertEquals("+79999999999", person.phone)
        assertEquals(person.name, repository.findById(person.id)?.name)
    }

    @Test
    fun `two people created with the same name get different ids`() {
        val first = service.handle(CreatePersonCommand("Регина", null, "test-" + java.util.UUID.randomUUID()))
        val second = service.handle(CreatePersonCommand("Регина", null, "test-" + java.util.UUID.randomUUID()))

        assertEquals(false, first.id == second.id)
    }

    @Test
    fun `creating a person with a blank name fails`() {
        assertFailsWith<IllegalArgumentException> {
            service.handle(CreatePersonCommand("", null, "test-" + java.util.UUID.randomUUID()))
        }
    }
}

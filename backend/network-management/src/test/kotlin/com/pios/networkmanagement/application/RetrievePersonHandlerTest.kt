package com.pios.networkmanagement.application

import com.pios.networkmanagement.domain.PersonId
import com.pios.networkmanagement.persistence.InMemoryPersonRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RetrievePersonHandlerTest {
    private val repository = InMemoryPersonRepository()
    private val createService = CreatePersonApplicationService(repository)
    private val handler = RetrievePersonHandler(repository)

    @Test
    fun `retrieving a known person returns it`() {
        val created = createService.handle(CreatePersonCommand("Артур", null))

        val retrieved = handler.handle(created.id)

        assertEquals(created.id, retrieved.id)
        assertEquals("Артур", retrieved.name)
    }

    @Test
    fun `retrieving an unknown person fails`() {
        assertFailsWith<PersonNotFoundException> {
            handler.handle(PersonId("unknown-person"))
        }
    }
}

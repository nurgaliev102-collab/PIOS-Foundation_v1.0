package com.pios.identity.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PhoneTest {
    @Test
    fun `a well-formed phone is accepted`() {
        assertEquals("+79991234567", Phone("+79991234567").value)
    }

    @Test
    fun `a phone without a leading plus is rejected`() {
        assertFailsWith<IllegalArgumentException> { Phone("79991234567") }
    }

    @Test
    fun `a phone with non-digit characters is rejected`() {
        assertFailsWith<IllegalArgumentException> { Phone("+7999 123 4567") }
        assertFailsWith<IllegalArgumentException> { Phone("+7-999-123-4567") }
    }

    @Test
    fun `a blank phone is rejected`() {
        assertFailsWith<IllegalArgumentException> { Phone("") }
    }
}

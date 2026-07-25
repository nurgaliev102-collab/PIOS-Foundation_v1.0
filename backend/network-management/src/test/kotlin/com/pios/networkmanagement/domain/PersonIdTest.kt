package com.pios.networkmanagement.domain

import kotlin.test.Test
import kotlin.test.assertFailsWith

class PersonIdTest {
    @Test
    fun `a blank PersonId is rejected`() {
        assertFailsWith<IllegalArgumentException> { PersonId("") }
        assertFailsWith<IllegalArgumentException> { PersonId("   ") }
    }
}

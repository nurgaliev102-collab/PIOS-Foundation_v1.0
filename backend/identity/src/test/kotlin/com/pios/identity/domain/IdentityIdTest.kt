package com.pios.identity.domain

import kotlin.test.Test
import kotlin.test.assertFailsWith

class IdentityIdTest {
    @Test
    fun `a blank IdentityId is rejected`() {
        assertFailsWith<IllegalArgumentException> { IdentityId("") }
        assertFailsWith<IllegalArgumentException> { IdentityId("   ") }
    }
}

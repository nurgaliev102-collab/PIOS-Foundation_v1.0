package com.pios.dispatch.domain

import kotlin.test.Test
import kotlin.test.assertFailsWith

class OrderReferenceTest {

    @Test
    fun `a blank order reference is not valid`() {
        assertFailsWith<IllegalArgumentException> {
            OrderReference("")
        }
    }
}

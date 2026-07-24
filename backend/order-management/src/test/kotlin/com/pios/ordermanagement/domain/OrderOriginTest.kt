package com.pios.ordermanagement.domain

import kotlin.test.Test
import kotlin.test.assertFailsWith

class OrderOriginTest {

    @Test
    fun `a blank origin is not valid`() {
        assertFailsWith<IllegalArgumentException> {
            OrderOrigin("")
        }
    }
}

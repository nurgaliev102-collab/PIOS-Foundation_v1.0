package com.pios.ordermanagement.domain

import kotlin.test.Test
import kotlin.test.assertFailsWith

class OrderIdTest {

    @Test
    fun `a blank order id is not valid`() {
        assertFailsWith<IllegalArgumentException> {
            OrderId("")
        }
    }
}

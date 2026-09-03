package com.pios.dispatch.domain

import kotlin.test.Test
import kotlin.test.assertFailsWith

class TripIdTest {

    @Test
    fun `a blank trip id is not valid`() {
        assertFailsWith<IllegalArgumentException> {
            TripId("")
        }
    }
}

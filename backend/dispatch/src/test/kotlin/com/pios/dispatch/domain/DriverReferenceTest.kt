package com.pios.dispatch.domain

import kotlin.test.Test
import kotlin.test.assertFailsWith

class DriverReferenceTest {

    @Test
    fun `a blank driver reference is not valid`() {
        assertFailsWith<IllegalArgumentException> {
            DriverReference("")
        }
    }
}

package com.pios.drivermanagement.domain

import kotlin.test.Test
import kotlin.test.assertFailsWith

class DriverIdTest {

    @Test
    fun `a blank driver id is not valid`() {
        assertFailsWith<IllegalArgumentException> {
            DriverId("")
        }
    }
}

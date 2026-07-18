package com.pios.passengerexperience.domain

import kotlin.test.Test
import kotlin.test.assertFailsWith

class PassengerReferenceTest {

    @Test
    fun `a blank passenger reference is not valid`() {
        assertFailsWith<IllegalArgumentException> {
            PassengerReference("")
        }
    }
}

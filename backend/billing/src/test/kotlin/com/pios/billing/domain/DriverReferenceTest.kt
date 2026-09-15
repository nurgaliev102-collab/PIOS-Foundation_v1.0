package com.pios.billing.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DriverReferenceTest {

    @Test
    fun `a blank driverId is rejected`() {
        assertFailsWith<IllegalArgumentException> { DriverReference("") }
        assertFailsWith<IllegalArgumentException> { DriverReference("   ") }
    }

    @Test
    fun `a non-blank driverId is accepted and preserved`() {
        assertEquals("driver-1", DriverReference("driver-1").driverId)
    }
}

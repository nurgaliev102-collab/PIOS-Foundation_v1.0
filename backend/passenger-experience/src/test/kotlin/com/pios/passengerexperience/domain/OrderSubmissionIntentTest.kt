package com.pios.passengerexperience.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class OrderSubmissionIntentTest {

    private val passenger = PassengerReference("passenger-1")

    @Test
    fun `an order submission intent carries the originating passenger`() {
        val intent = OrderSubmissionIntent(passenger)

        assertEquals(passenger, intent.passenger)
    }

    @Test
    fun `raising an intent produces an OrderSubmissionRequested for the same passenger`() {
        val intent = OrderSubmissionIntent(passenger)

        val requested = intent.raise()

        assertEquals(passenger, requested.passenger)
    }
}

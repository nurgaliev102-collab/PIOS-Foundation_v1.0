package com.pios.passengerexperience.application

import com.pios.passengerexperience.domain.PassengerReference
import kotlin.test.Test
import kotlin.test.assertEquals

class PassengerOrderSubmissionApplicationServiceTest {

    private val service = PassengerOrderSubmissionApplicationService()
    private val passenger = PassengerReference("passenger-1")

    @Test
    fun `handling a command produces an OrderSubmissionRequested for the same passenger`() {
        val command = SubmitOrderCommand(passenger)

        val result = service.handle(command)

        assertEquals(passenger, result.passenger)
    }

    @Test
    fun `handling distinct commands produces independent requests`() {
        val other = PassengerReference("passenger-2")

        val first = service.handle(SubmitOrderCommand(passenger))
        val second = service.handle(SubmitOrderCommand(other))

        assertEquals(passenger, first.passenger)
        assertEquals(other, second.passenger)
    }
}

package com.pios.drivermanagement.application

import com.pios.drivermanagement.persistence.InMemoryOrderPassengerRepository
import com.pios.drivermanagement.persistence.InMemoryOrderSubmittedRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OrderSubmittedApplicationServiceTest {

    private val orderSubmittedRepository = InMemoryOrderSubmittedRepository()
    private val orderPassengerRepository = InMemoryOrderPassengerRepository()
    private val service = OrderSubmittedApplicationService(orderSubmittedRepository, orderPassengerRepository)

    @Test
    fun `handling a new OrderSubmitted records the order's passenger`() {
        service.handle(OrderSubmittedUpdateCommand("event-1", "order-1", "passenger-1"))

        assertEquals("passenger-1", orderPassengerRepository.findPassengerReference("order-1"))
    }

    @Test
    fun `redelivering the same eventId does not throw and leaves the mapping unchanged`() {
        val command = OrderSubmittedUpdateCommand("event-2", "order-2", "passenger-2")

        service.handle(command)
        service.handle(command)

        assertEquals("passenger-2", orderPassengerRepository.findPassengerReference("order-2"))
    }

    @Test
    fun `an order with no OrderSubmitted consumed yet has no known passenger`() {
        assertNull(orderPassengerRepository.findPassengerReference("order-unknown"))
    }
}

package com.pios.ordermanagement.persistence

import com.pios.ordermanagement.application.CompleteOrderCommand
import com.pios.ordermanagement.application.OrderLifecycleApplicationService
import com.pios.ordermanagement.application.SubmitOrderCommand
import com.pios.ordermanagement.domain.OrderId
import com.pios.ordermanagement.domain.OrderStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InMemoryOrderRepositoryTest {

    private val repository = InMemoryOrderRepository()
    private val service = OrderLifecycleApplicationService()

    @Test
    fun `submit, save, and load preserves an order's status`() {
        val submitted = service.submitOrder(SubmitOrderCommand())

        repository.save(submitted.order)
        val loaded = repository.findById(submitted.order.id)

        assertEquals(OrderStatus.SUBMITTED, loaded?.status)
    }

    @Test
    fun `loading an id that was never saved returns null`() {
        assertNull(repository.findById(OrderId("never-saved")))
    }

    @Test
    fun `an order's lifecycle progression to completion is preserved across save and load`() {
        val submitted = service.submitOrder(SubmitOrderCommand())
        repository.save(submitted.order)

        service.completeOrder(submitted.order, CompleteOrderCommand(submitted.order.id))
        repository.save(submitted.order)

        assertEquals(OrderStatus.COMPLETED, repository.findById(submitted.order.id)?.status)
    }
}

package com.pios.ordermanagement.persistence

import com.pios.ordermanagement.application.CompleteOrderCommand
import com.pios.ordermanagement.application.OrderLifecycleApplicationService
import com.pios.ordermanagement.application.SubmitOrderCommand
import com.pios.ordermanagement.domain.Order
import com.pios.ordermanagement.domain.OrderId
import com.pios.ordermanagement.domain.OrderStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Exercises [InMemoryOrderRepository] directly (repository-level
 * save/load, independent of any application service) as well as through
 * [OrderLifecycleApplicationService], which now persists automatically
 * after each lifecycle transition (Application Persistence Wiring v1.0).
 */
class InMemoryOrderRepositoryTest {

    private val repository = InMemoryOrderRepository()
    private val service = OrderLifecycleApplicationService(repository)

    @Test
    fun `submitting an order through the service persists it, loadable by id`() {
        val submitted = service.submitOrder(SubmitOrderCommand())

        val loaded = repository.findById(submitted.order.id)

        assertEquals(OrderStatus.SUBMITTED, loaded?.status)
    }

    @Test
    fun `loading an id that was never saved returns null`() {
        assertNull(repository.findById(OrderId("never-saved")))
    }

    @Test
    fun `an order's lifecycle progression to completion is preserved through the repository`() {
        val submitted = service.submitOrder(SubmitOrderCommand())

        service.completeOrder(submitted.order, CompleteOrderCommand(submitted.order.id))

        assertEquals(OrderStatus.COMPLETED, repository.findById(submitted.order.id)?.status)
    }

    @Test
    fun `directly saving and loading a domain-constructed order preserves its identity`() {
        val submitted = Order.submit()

        repository.save(submitted.order)

        assertEquals(submitted.order.id, repository.findById(submitted.order.id)?.id)
    }
}

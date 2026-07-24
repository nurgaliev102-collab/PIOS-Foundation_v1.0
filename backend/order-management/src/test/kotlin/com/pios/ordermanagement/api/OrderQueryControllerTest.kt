package com.pios.ordermanagement.api

import com.pios.ordermanagement.application.RetrieveOrdersHandler
import com.pios.ordermanagement.domain.Order
import com.pios.ordermanagement.domain.OrderOrigin
import com.pios.ordermanagement.persistence.InMemoryOrderRepository
import org.springframework.http.HttpStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Constructs [OrderQueryController] directly, with a real
 * [RetrieveOrdersHandler], no Spring MVC context -- mirroring this
 * project's own constructor-based testing convention (Tranche 2:
 * Passenger Experience REST Transport).
 */
class OrderQueryControllerTest {

    private val repository = InMemoryOrderRepository()
    private val handler = RetrieveOrdersHandler(repository)
    private val controller = OrderQueryController(handler)

    @Test
    fun `listing orders returns every saved order`() {
        val first = Order.submit(OrderOrigin("list-1"))
        val second = Order.submit(OrderOrigin("list-2"))
        repository.save(first.order)
        repository.save(second.order)

        val response = controller.listOrders()

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = assertNotNull(response.body)
        assertTrue(body.any { it.id == first.order.id.value && it.status == "SUBMITTED" && it.origin == "list-1" })
        assertTrue(body.any { it.id == second.order.id.value && it.status == "SUBMITTED" && it.origin == "list-2" })
    }

    @Test
    fun `listing orders when none exist returns an empty list`() {
        val response = controller.listOrders()

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(emptyList(), response.body)
    }
}

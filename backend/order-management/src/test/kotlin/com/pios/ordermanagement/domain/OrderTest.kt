package com.pios.ordermanagement.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class OrderTest {

    // --- Submission ---

    @Test
    fun `submitting an order creates it with SUBMITTED status`() {
        val submitted = Order.submit()

        assertEquals(OrderStatus.SUBMITTED, submitted.order.status)
    }

    @Test
    fun `submitting an order produces an OrderSubmitted event for that order`() {
        val submitted = Order.submit()

        assertEquals(submitted.order.id, submitted.event.orderId)
    }

    @Test
    fun `each submission produces a distinct order identity`() {
        val first = Order.submit()
        val second = Order.submit()

        assertNotEquals(first.order.id, second.order.id)
    }

    // --- Completion ---

    @Test
    fun `completing a submitted order transitions it to COMPLETED`() {
        val order = Order.submit().order

        order.complete()

        assertEquals(OrderStatus.COMPLETED, order.status)
    }

    @Test
    fun `completing a submitted order produces an OrderCompleted event for that order`() {
        val order = Order.submit().order

        val event = order.complete()

        assertEquals(order.id, event.orderId)
    }

    @Test
    fun `completing an already-completed order is rejected`() {
        val order = Order.submit().order
        order.complete()

        assertFailsWith<IllegalStateException> {
            order.complete()
        }
    }

    @Test
    fun `completing a cancelled order is rejected`() {
        val order = Order.submit().order
        order.cancel()

        assertFailsWith<IllegalStateException> {
            order.complete()
        }
    }

    // --- Cancellation ---

    @Test
    fun `cancelling a submitted order transitions it to CANCELLED`() {
        val order = Order.submit().order

        order.cancel()

        assertEquals(OrderStatus.CANCELLED, order.status)
    }

    @Test
    fun `cancelling a submitted order produces an OrderCancelled event for that order`() {
        val order = Order.submit().order

        val event = order.cancel()

        assertEquals(order.id, event.orderId)
    }

    @Test
    fun `cancelling an already-completed order is rejected`() {
        val order = Order.submit().order
        order.complete()

        assertFailsWith<IllegalStateException> {
            order.cancel()
        }
    }

    @Test
    fun `cancelling an already-cancelled order is rejected`() {
        val order = Order.submit().order
        order.cancel()

        assertFailsWith<IllegalStateException> {
            order.cancel()
        }
    }

    // --- Invariant: exactly one current status, and it never returns to active ---

    @Test
    fun `an order has exactly one current status at any time`() {
        val order = Order.submit().order

        assertEquals(OrderStatus.SUBMITTED, order.status)
        order.complete()
        assertEquals(OrderStatus.COMPLETED, order.status)
    }

    @Test
    fun `a completed order cannot return to an active state`() {
        val order = Order.submit().order
        order.complete()

        assertFailsWith<IllegalStateException> {
            order.cancel()
        }
        assertEquals(OrderStatus.COMPLETED, order.status)
    }
}

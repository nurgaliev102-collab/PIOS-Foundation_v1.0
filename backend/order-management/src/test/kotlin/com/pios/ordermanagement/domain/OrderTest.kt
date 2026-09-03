package com.pios.ordermanagement.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class OrderTest {

    private val origin = OrderOrigin("origin-1")

    // --- Submission ---

    @Test
    fun `submitting an order creates it with SUBMITTED status`() {
        val submitted = Order.submit(origin)

        assertEquals(OrderStatus.SUBMITTED, submitted.order.status)
    }

    @Test
    fun `submitting an order produces an OrderSubmitted event for that order`() {
        val submitted = Order.submit(origin)

        assertEquals(submitted.order.id, submitted.event.orderId)
    }

    @Test
    fun `each submission produces a distinct order identity`() {
        val first = Order.submit(origin)
        val second = Order.submit(origin)

        assertNotEquals(first.order.id, second.order.id)
    }

    // --- Origin (Sprint FND-006: Minimal Order Model) ---

    @Test
    fun `a submitted order carries the origin it was submitted with`() {
        val submitted = Order.submit(origin)

        assertEquals(origin, submitted.order.origin)
    }

    @Test
    fun `origin survives completion unchanged`() {
        val order = Order.submit(origin).order

        order.complete()

        assertEquals(origin, order.origin)
    }

    // --- Destination (Sprint 3B: MVR Pilot Enablement -- Optional Destination) ---

    @Test
    fun `a submitted order carries the destination it was submitted with`() {
        val submitted = Order.submit(origin, "Аэропорт")

        assertEquals("Аэропорт", submitted.order.destination)
    }

    @Test
    fun `a submitted order without a destination has a null destination`() {
        val submitted = Order.submit(origin)

        assertEquals(null, submitted.order.destination)
    }

    @Test
    fun `destination survives completion unchanged`() {
        val order = Order.submit(origin, "Аэропорт").order

        order.complete()

        assertEquals("Аэропорт", order.destination)
    }

    // --- Passenger name / created at (first-pilot feedback) ---

    @Test
    fun `a submitted order carries the passenger name it was submitted with`() {
        val submitted = Order.submit(origin, passengerName = "Мария")

        assertEquals("Мария", submitted.order.passengerName)
    }

    @Test
    fun `a submitted order without a passenger name has a null passenger name`() {
        val submitted = Order.submit(origin)

        assertEquals(null, submitted.order.passengerName)
    }

    @Test
    fun `a submitted order has a non-null createdAt`() {
        val submitted = Order.submit(origin)

        assertNotEquals(null, submitted.order.createdAt)
    }

    // --- Pickup address (Sprint H5: Entrepreneur Working Cycle Integrity) ---

    @Test
    fun `a submitted order carries the pickup address it was submitted with`() {
        val submitted = Order.submit(origin, pickupAddress = "ул. Ленина, 10")

        assertEquals("ул. Ленина, 10", submitted.order.pickupAddress)
    }

    @Test
    fun `a submitted order without a pickup address has a null pickup address`() {
        val submitted = Order.submit(origin)

        assertEquals(null, submitted.order.pickupAddress)
    }

    @Test
    fun `pickup address survives completion unchanged`() {
        val order = Order.submit(origin, pickupAddress = "ул. Ленина, 10").order

        order.complete()

        assertEquals("ул. Ленина, 10", order.pickupAddress)
    }

    // --- Requested pickup time (ADR-058, Scheduled Pickup Time) ---

    @Test
    fun `a submitted order carries the requested pickup instant it was submitted with`() {
        val requestedPickupAt = java.time.Instant.parse("2026-08-25T06:30:00Z")

        val submitted = Order.submit(origin, requestedPickupAt = requestedPickupAt)

        assertEquals(requestedPickupAt, submitted.order.requestedPickupAt)
    }

    @Test
    fun `a submitted order without a requested pickup instant has a null requested pickup instant`() {
        val submitted = Order.submit(origin)

        assertEquals(null, submitted.order.requestedPickupAt)
    }

    @Test
    fun `requested pickup instant survives completion unchanged`() {
        val requestedPickupAt = java.time.Instant.parse("2026-08-25T06:30:00Z")
        val order = Order.submit(origin, requestedPickupAt = requestedPickupAt).order

        order.complete()

        assertEquals(requestedPickupAt, order.requestedPickupAt)
    }

    @Test
    fun `requested pickup instant survives cancellation unchanged`() {
        val requestedPickupAt = java.time.Instant.parse("2026-08-25T06:30:00Z")
        val order = Order.submit(origin, requestedPickupAt = requestedPickupAt).order

        order.cancel()

        assertEquals(requestedPickupAt, order.requestedPickupAt)
    }

    // --- Completion ---

    @Test
    fun `completing a submitted order transitions it to COMPLETED`() {
        val order = Order.submit(origin).order

        order.complete()

        assertEquals(OrderStatus.COMPLETED, order.status)
    }

    @Test
    fun `completing a submitted order produces an OrderCompleted event for that order`() {
        val order = Order.submit(origin).order

        val event = order.complete()

        assertEquals(order.id, event.orderId)
    }

    @Test
    fun `completing an already-completed order is rejected`() {
        val order = Order.submit(origin).order
        order.complete()

        assertFailsWith<IllegalStateException> {
            order.complete()
        }
    }

    @Test
    fun `completing a cancelled order is rejected`() {
        val order = Order.submit(origin).order
        order.cancel()

        assertFailsWith<IllegalStateException> {
            order.complete()
        }
    }

    // --- Cancellation ---

    @Test
    fun `cancelling a submitted order transitions it to CANCELLED`() {
        val order = Order.submit(origin).order

        order.cancel()

        assertEquals(OrderStatus.CANCELLED, order.status)
    }

    @Test
    fun `cancelling a submitted order produces an OrderCancelled event for that order`() {
        val order = Order.submit(origin).order

        val event = order.cancel()

        assertEquals(order.id, event.orderId)
    }

    @Test
    fun `cancelling an already-completed order is rejected`() {
        val order = Order.submit(origin).order
        order.complete()

        assertFailsWith<IllegalStateException> {
            order.cancel()
        }
    }

    @Test
    fun `cancelling an already-cancelled order is rejected`() {
        val order = Order.submit(origin).order
        order.cancel()

        assertFailsWith<IllegalStateException> {
            order.cancel()
        }
    }

    // --- Invariant: exactly one current status, and it never returns to active ---

    @Test
    fun `an order has exactly one current status at any time`() {
        val order = Order.submit(origin).order

        assertEquals(OrderStatus.SUBMITTED, order.status)
        order.complete()
        assertEquals(OrderStatus.COMPLETED, order.status)
    }

    @Test
    fun `a completed order cannot return to an active state`() {
        val order = Order.submit(origin).order
        order.complete()

        assertFailsWith<IllegalStateException> {
            order.cancel()
        }
        assertEquals(OrderStatus.COMPLETED, order.status)
    }

    // --- Explicit driver intent (Task 15C: First Refusal Contract Completion and Concurrency Safety) ---

    @Test
    fun `an order submitted with no explicit driver intent defaults to false, exactly today's implicit behavior`() {
        val submitted = Order.submit(origin)

        assertEquals(false, submitted.order.explicitDriverIntent)
        assertEquals(false, submitted.event.explicitDriverIntent)
    }

    @Test
    fun `an order submitted with explicit driver intent carries it on both the order and its own event`() {
        val submitted = Order.submit(origin, explicitDriverIntent = true)

        assertEquals(true, submitted.order.explicitDriverIntent)
        assertEquals(true, submitted.event.explicitDriverIntent)
    }

    @Test
    fun `explicit driver intent is recorded atomically with submission -- it is a constructor-time fact, not settable afterward`() {
        val submitted = Order.submit(origin, explicitDriverIntent = true)

        // No method on Order exists to set this after construction --
        // completing or cancelling the order leaves it unchanged, mirroring
        // origin/destination/passengerName's own already-established
        // immutability (Order.kt's own KDoc).
        submitted.order.complete()

        assertEquals(true, submitted.order.explicitDriverIntent)
    }

    @Test
    fun `the event's own passenger reference matches the order's own origin`() {
        val submitted = Order.submit(origin)

        assertEquals(origin, submitted.event.origin)
    }

    @Test
    fun `Task 16 -- the event's own isTest matches the order's own isTest`() {
        val submitted = Order.submit(origin, isTest = true)

        assertEquals(true, submitted.order.isTest)
        assertEquals(true, submitted.event.isTest)
    }
}

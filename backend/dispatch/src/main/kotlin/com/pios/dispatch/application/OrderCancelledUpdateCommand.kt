package com.pios.dispatch.application

/**
 * The primitive-typed command this module's transport boundary
 * (`com.pios.dispatch.persistence.OrderCancelledListener`) translates a
 * received OrderCancelled message into, before calling
 * [OrderCancelledApplicationService]. Carries only [eventId] (for
 * idempotent processing, ADR-031) and [orderReference] — exactly what the
 * new Order Management -> Dispatch contract (ADR-053, Part 4) justifies,
 * nothing from Order Management's own domain types. Mirrors
 * [DriverAvailabilityUpdateCommand]'s own shape.
 */
data class OrderCancelledUpdateCommand(
    val eventId: String,
    val orderReference: String
)

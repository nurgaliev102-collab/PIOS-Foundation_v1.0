package com.pios.ordermanagement.domain

/**
 * The current state of an order within its lifecycle (DOMAIN_MODEL.md
 * Section 6, "Status"; Section 12, State Lifecycles).
 *
 * Only the states this capability's behavior (submit, complete, cancel)
 * actually reaches are represented here. The assigned, accepted, and ride
 * states an order passes through once Dispatch allocates a driver
 * (DOMAIN_MODEL.md Section 12) depend on Dispatch's own capability and are
 * outside this task's scope; they are not modeled here.
 */
enum class OrderStatus {
    SUBMITTED,
    COMPLETED,
    CANCELLED
}

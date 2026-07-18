package com.pios.ordermanagement.application

import org.springframework.stereotype.Service

/**
 * Consumer-side handler for the Dispatch -> Order Management contract
 * (INTERFACE_CONTRACTS.md Section 5; ADR-027). Accepts the assigned
 * order's and driver's references as plain primitives — never Dispatch's
 * own `OrderReference`/`DriverReference` domain types.
 *
 * Order Management's own `OrderStatus` (SUBMITTED, COMPLETED, CANCELLED)
 * does not model an "assigned" state — the assigned, accepted, and ride
 * states an order passes through once Dispatch allocates a driver
 * (DOMAIN_MODEL.md Section 12) remain outside this module's established
 * scope (see `Order`'s own KDoc). This handler therefore only recognizes
 * the fact that an assignment occurred, per INTERFACE_CONTRACTS.md
 * Section 5's "Contract: Dispatch -> Order Management" purpose; it does
 * not transition the Order aggregate, which has no corresponding state to
 * transition to, and it retains neither reference as its own information.
 *
 * This handler makes no call into any other module (ADR-027); its
 * compatibility with Dispatch's provider representation
 * (`OrderAssignedPublisher`) is verified only from test code holding a
 * test-scoped dependency on this module.
 */
@Service
class OrderAssignmentRecognitionHandler {
    fun handle(orderReference: String, driverReference: String): String {
        require(orderReference.isNotBlank()) {
            "Order reference must not be blank"
        }
        require(driverReference.isNotBlank()) {
            "Driver reference must not be blank"
        }
        return orderReference
    }
}

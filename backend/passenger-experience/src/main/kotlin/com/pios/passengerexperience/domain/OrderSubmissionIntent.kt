package com.pios.passengerexperience.domain

/**
 * The Order submission intent: a passenger's or corporate customer's own
 * expression that an order should be submitted on their behalf
 * (DOMAIN_MODEL.md Section 6, "Order Origin"; UC-001, "Request
 * Transportation"). This is not the Order aggregate and carries no
 * lifecycle or status of its own — Order Management alone owns Order
 * submission (EVENT_CATALOG.md Section 6). This intent is Passenger
 * Experience's own representation of the originating context that Order
 * Management relies on when an order is submitted (INTERFACE_CONTRACTS.md
 * Section 5, "Contract: Passenger Experience -> Order Management").
 */
data class OrderSubmissionIntent(val passenger: PassengerReference) {
    /**
     * Raises the [OrderSubmissionRequested] representation of this
     * intent, for Order Management to rely on through the Passenger
     * Experience -> Order Management contract.
     */
    fun raise(): OrderSubmissionRequested = OrderSubmissionRequested(passenger = passenger)
}

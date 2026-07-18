package com.pios.ordermanagement.application

import org.springframework.stereotype.Service

/**
 * Consumer-side handler for the Passenger Experience -> Order Management
 * contract (INTERFACE_CONTRACTS.md Section 5; ADR-027). Accepts the
 * originating passenger reference as a plain [String] — never Passenger
 * Experience's own `PassengerReference` type — and starts Order
 * Management's own Submit Order handling (DOMAIN_MODEL.md Section 9).
 *
 * Per INTERFACE_CONTRACTS.md Section 5, "no copy of that representation
 * becomes Order Management's own information": [passengerReference] is
 * validated at this boundary and then discarded. It is never attached to
 * the Order aggregate, which continues to exclude Order Origin, out of
 * this module's established scope.
 *
 * This handler makes no call into any other module (ADR-027); its
 * compatibility with Passenger Experience's provider representation
 * (`OrderSubmissionRequestedPublisher`) is verified only from test code
 * holding a test-scoped dependency on this module.
 */
@Service
class OrderSubmissionRequestHandler(
    private val orderLifecycleApplicationService: OrderLifecycleApplicationService
) {
    fun handle(passengerReference: String): String {
        require(passengerReference.isNotBlank()) {
            "Passenger reference must not be blank"
        }
        val submitted = orderLifecycleApplicationService.submitOrder(SubmitOrderCommand())
        return submitted.order.id.value
    }
}

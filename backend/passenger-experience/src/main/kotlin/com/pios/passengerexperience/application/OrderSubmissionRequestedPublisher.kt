package com.pios.passengerexperience.application

import com.pios.passengerexperience.domain.OrderSubmissionRequested
import org.springframework.stereotype.Service

/**
 * Provider-side representation of the Passenger Experience -> Order
 * Management contract (INTERFACE_CONTRACTS.md Section 5; ADR-027). Per
 * ADR-027, this crosses the module boundary only as primitive-typed
 * data — never Passenger Experience's own domain classes — so that Order
 * Management's consumer handler never needs to import anything from this
 * module, and this module's production code never needs to depend on
 * Order Management's.
 *
 * [OrderSubmissionContractPayload] belongs exclusively to Passenger
 * Experience: it expresses only this module's own understanding of what
 * it provides, and it is not a shared type. Compatibility with Order
 * Management's consumer handler (`OrderSubmissionRequestHandler`) is
 * verified only in test code, through a test-scoped module reference —
 * see `OrderSubmissionContractVerificationTest`.
 */
data class OrderSubmissionContractPayload(val passengerReference: String)

/**
 * Translates an [OrderSubmissionRequested] domain event into the minimal
 * primitive payload the Passenger Experience -> Order Management contract
 * describes (INTERFACE_CONTRACTS.md Section 5, "Provide the originating
 * passenger's or corporate customer's context for a submitted order").
 * This is the provider side of the contract boundary ADR-027
 * establishes; it makes no call into any other module.
 */
@Service
class OrderSubmissionRequestedPublisher {
    fun toContractPayload(event: OrderSubmissionRequested): OrderSubmissionContractPayload =
        OrderSubmissionContractPayload(passengerReference = event.passenger.passengerId)
}

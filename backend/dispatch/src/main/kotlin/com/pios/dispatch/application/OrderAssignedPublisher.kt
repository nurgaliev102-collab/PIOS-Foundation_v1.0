package com.pios.dispatch.application

import com.pios.dispatch.domain.OrderAssigned
import org.springframework.stereotype.Service

/**
 * Provider-side representation of the Dispatch -> Order Management
 * contract (INTERFACE_CONTRACTS.md Section 5; ADR-027). Per ADR-027,
 * this crosses the module boundary only as primitive-typed data — never
 * Dispatch's own domain classes — so that Order Management's consumer
 * handler never needs to import anything from this module, and this
 * module's production code never needs to depend on Order Management's.
 *
 * [OrderAssignedContractPayload] belongs exclusively to Dispatch and is
 * not a shared type. Compatibility with Order Management's consumer
 * handler (`OrderAssignmentRecognitionHandler`) is verified only in test
 * code, through a test-scoped module reference — see
 * `OrderAssignmentContractVerificationTest`.
 */
data class OrderAssignedContractPayload(
    val orderReference: String,
    val driverReference: String
)

/**
 * Translates an [OrderAssigned] domain event into the minimal primitive
 * payload the Dispatch -> Order Management contract describes
 * (INTERFACE_CONTRACTS.md Section 5, "Make an order's assignment outcome
 * known so Order Management can track the order's status"). This is the
 * provider side of the contract boundary ADR-027 establishes; it makes
 * no call into any other module.
 */
@Service
class OrderAssignedPublisher {
    fun toContractPayload(event: OrderAssigned): OrderAssignedContractPayload =
        OrderAssignedContractPayload(
            orderReference = event.orderId.orderId,
            driverReference = event.driverId.driverId
        )
}

package com.pios.drivermanagement.application

import com.pios.drivermanagement.domain.Availability
import com.pios.drivermanagement.domain.DriverAvailabilityChanged
import org.springframework.stereotype.Service

/**
 * Provider-side representation of the Driver Management -> Dispatch
 * contract (INTERFACE_CONTRACTS.md Section 5; ADR-027). Per ADR-027,
 * this crosses the module boundary only as primitive-typed data — never
 * Driver Management's own domain classes — so that Dispatch's consumer
 * handler never needs to import anything from this module, and this
 * module's production code never needs to depend on Dispatch's.
 *
 * [DriverAvailabilityContractPayload] belongs exclusively to Driver
 * Management and is not a shared type. Compatibility with Dispatch's
 * consumer handler (`DriverAvailabilityNotificationHandler`) is verified
 * only in test code, through a test-scoped module reference — see
 * `DriverAvailabilityContractVerificationTest`.
 */
data class DriverAvailabilityContractPayload(
    val driverReference: String,
    val available: Boolean
)

/**
 * Translates a [DriverAvailabilityChanged] domain event into the minimal
 * primitive payload the Driver Management -> Dispatch contract describes
 * (INTERFACE_CONTRACTS.md Section 5, "Make a driver's current
 * availability known so Dispatch can determine an assignment"). This is
 * the provider side of the contract boundary ADR-027 establishes; it
 * makes no call into any other module.
 */
@Service
class DriverAvailabilityChangedPublisher {
    fun toContractPayload(event: DriverAvailabilityChanged): DriverAvailabilityContractPayload =
        DriverAvailabilityContractPayload(
            driverReference = event.driverId.value,
            available = event.availability == Availability.AVAILABLE
        )
}

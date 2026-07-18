package com.pios.dispatch.application

import org.springframework.stereotype.Service

/**
 * Consumer-side handler for the Driver Management -> Dispatch contract
 * (INTERFACE_CONTRACTS.md Section 5; ADR-027). Accepts a driver's
 * reference and current availability as plain primitives — never Driver
 * Management's own `DriverId`/`Availability` domain types.
 *
 * Dispatch does not yet maintain a driver pool (no persistence exists,
 * out of this module's established scope; see `Assignment`'s own KDoc).
 * This handler therefore only confirms that the availability information
 * DOMAIN_MODEL.md Section 7 describes Dispatch as needing — "which
 * drivers are currently available in order to make an assignment" — has
 * been received; it does not record or retain it.
 *
 * This handler makes no call into any other module (ADR-027); its
 * compatibility with Driver Management's provider representation
 * (`DriverAvailabilityChangedPublisher`) is verified only from test code
 * holding a test-scoped dependency on this module.
 */
@Service
class DriverAvailabilityNotificationHandler {
    fun handle(driverReference: String, available: Boolean): String {
        require(driverReference.isNotBlank()) {
            "Driver reference must not be blank"
        }
        return driverReference
    }
}

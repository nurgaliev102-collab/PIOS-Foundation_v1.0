package com.pios.dispatch.domain

/**
 * Dispatch's own reference to a driver it connects to an order. Per the
 * module isolation rule (MODULE_STRUCTURE.md Section 2; ADR-005, ADR-009),
 * Dispatch does not own or import Driver Management's Driver type; it
 * holds only the identity value it needs to record which driver an
 * assignment concerns. Dispatch does not verify that this driver exists
 * or is available — that remains Driver Management's own responsibility.
 */
@JvmInline
value class DriverReference(val driverId: String) {
    init {
        require(driverId.isNotBlank()) { "DriverReference must not be blank" }
    }
}

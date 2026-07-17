package com.pios.drivermanagement.domain

/**
 * Identity of a Driver aggregate (DOMAIN_MODEL.md Section 4).
 */
@JvmInline
value class DriverId(val value: String) {
    init {
        require(value.isNotBlank()) { "DriverId must not be blank" }
    }
}

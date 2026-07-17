package com.pios.drivermanagement.domain

/**
 * A driver's own declaration of their readiness to receive an assignment
 * (DOMAIN_MODEL.md Section 6, "Availability"). No state beyond readiness
 * to receive an assignment is established anywhere in the approved
 * documentation, so this concept is represented as exactly that: available,
 * or not.
 */
enum class Availability {
    AVAILABLE,
    UNAVAILABLE
}

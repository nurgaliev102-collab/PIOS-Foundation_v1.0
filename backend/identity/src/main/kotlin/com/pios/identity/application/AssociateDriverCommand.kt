package com.pios.identity.application

/**
 * [driverId] is a plain string, not validated against
 * `driver-management` in any way (ADR-039: the reference is one-directional
 * and `identity` never calls `driver-management` to confirm it exists —
 * the frontend, which just created that driver, supplies it here trusted).
 */
data class AssociateDriverCommand(val identityId: String, val driverId: String)

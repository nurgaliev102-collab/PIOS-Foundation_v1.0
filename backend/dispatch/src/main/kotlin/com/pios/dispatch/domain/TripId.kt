package com.pios.dispatch.domain

/**
 * Identity of a Trip aggregate (ADR-063: Trip as a New Dispatch-Owned
 * Aggregate, Distinct from Assignment). Mirrors [AssignmentId] exactly.
 */
@JvmInline
value class TripId(val value: String) {
    init {
        require(value.isNotBlank()) { "TripId must not be blank" }
    }
}

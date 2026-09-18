package com.pios.dispatch.domain

/**
 * Identity of a Handoff aggregate (D-07, Handoff Protocol). Mirrors
 * [TripId]/[AssignmentId] exactly.
 */
@JvmInline
value class HandoffId(val value: String) {
    init {
        require(value.isNotBlank()) { "HandoffId must not be blank" }
    }
}

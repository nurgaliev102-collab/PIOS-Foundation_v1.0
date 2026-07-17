package com.pios.dispatch.domain

/**
 * Identity of an Assignment aggregate (DOMAIN_MODEL.md Section 4).
 */
@JvmInline
value class AssignmentId(val value: String) {
    init {
        require(value.isNotBlank()) { "AssignmentId must not be blank" }
    }
}

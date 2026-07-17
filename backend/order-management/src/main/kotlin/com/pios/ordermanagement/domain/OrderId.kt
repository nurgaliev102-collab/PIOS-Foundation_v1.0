package com.pios.ordermanagement.domain

/**
 * Identity of an Order aggregate (DOMAIN_MODEL.md Section 4).
 */
@JvmInline
value class OrderId(val value: String) {
    init {
        require(value.isNotBlank()) { "OrderId must not be blank" }
    }
}

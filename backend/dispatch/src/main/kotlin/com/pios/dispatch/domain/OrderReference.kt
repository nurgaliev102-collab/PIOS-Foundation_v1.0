package com.pios.dispatch.domain

/**
 * Dispatch's own reference to an order it connects to a driver. Per the
 * module isolation rule (MODULE_STRUCTURE.md Section 2; ADR-005, ADR-009),
 * Dispatch does not own or import Order Management's Order type; it holds
 * only the identity value it needs to record which order an assignment
 * concerns. Dispatch does not verify that this order exists or is
 * submitted — that remains Order Management's own responsibility.
 */
@JvmInline
value class OrderReference(val orderId: String) {
    init {
        require(orderId.isNotBlank()) { "OrderReference must not be blank" }
    }
}

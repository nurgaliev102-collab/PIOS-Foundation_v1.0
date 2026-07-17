package com.pios.ordermanagement.application

import com.pios.ordermanagement.domain.OrderId

/**
 * The Cancel Order command (DOMAIN_MODEL.md Section 9;
 * MODULE_STRUCTURE.md, "Order Management Module", Owned capabilities).
 */
data class CancelOrderCommand(val orderId: OrderId)

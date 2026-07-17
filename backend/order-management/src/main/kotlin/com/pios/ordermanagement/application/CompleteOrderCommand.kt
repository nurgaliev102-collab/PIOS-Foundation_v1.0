package com.pios.ordermanagement.application

import com.pios.ordermanagement.domain.OrderId

/**
 * The Complete Order command. DOMAIN_MODEL.md Section 9 does not name a
 * "Complete Order" command explicitly — only Submit Order and Cancel Order
 * are named there. This task explicitly requires Complete Order behavior,
 * so this command is introduced as the natural trigger for the
 * already-established OrderCompleted event (EVENT_CATALOG.md Section 5),
 * named consistently with Submit Order and Cancel Order's existing
 * convention.
 */
data class CompleteOrderCommand(val orderId: OrderId)

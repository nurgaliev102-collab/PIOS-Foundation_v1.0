package com.pios.ordermanagement.domain

import java.time.Instant

/** Order Management's own terminal fact; distinct from DispatchExhausted. */
data class OrderUnfulfilled(val orderId: OrderId, val occurredAt: Instant = Instant.now())

package com.pios.ordermanagement.application

import com.pios.ordermanagement.domain.OrderId

/**
 * Thrown when an application-layer operation needs the Order identified
 * by [orderId], but [OrderRepository.findById] could not locate one
 * (PERSISTENCE_ARCHITECTURE.md Section 3, "Order Management").
 *
 * This is an application-layer error, not a domain error: the Order
 * aggregate itself has no notion of "not found" — that only exists at
 * the point something tries to look one up, which is an application
 * concern (APPLICATION_ARCHITECTURE.md Section 2). It is also not a
 * persistence error: the repository itself never throws for a miss
 * ([com.pios.ordermanagement.persistence.InMemoryOrderRepository.findById]
 * simply returns null); this exception is raised exactly once, here, at
 * the point the application decides it cannot proceed without the
 * aggregate.
 */
class OrderNotFoundException(val orderId: OrderId) :
    RuntimeException("Order ${orderId.value} was not found")

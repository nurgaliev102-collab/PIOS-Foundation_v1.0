package com.pios.ordermanagement.application

import com.pios.ordermanagement.domain.Order
import com.pios.ordermanagement.domain.OrderCancelled
import com.pios.ordermanagement.domain.OrderCompleted
import com.pios.ordermanagement.domain.SubmittedOrder
import org.springframework.stereotype.Service

/**
 * Application-layer coordination for Order Management's lifecycle commands
 * (APPLICATION_ARCHITECTURE.md Section 6). This service sequences each
 * command into the Order aggregate's own behavior; it does not decide an
 * order's status itself (APPLICATION_ARCHITECTURE.md Section 2, "Domain
 * Decides Business Meaning") and contains no persistence, since that
 * remains a separate, later concern (PERSISTENCE_ARCHITECTURE.md), out of
 * scope for this capability. [completeOrder] and [cancelOrder] operate on
 * an [Order] instance supplied by the caller, since no repository exists
 * yet to look one up.
 */
@Service
class OrderLifecycleApplicationService {

    /**
     * Coordinates a [SubmitOrderCommand], producing a new order and the
     * [com.pios.ordermanagement.domain.OrderSubmitted] event recording it.
     */
    fun submitOrder(command: SubmitOrderCommand): SubmittedOrder {
        return Order.submit()
    }

    /**
     * Coordinates a [CompleteOrderCommand] against the given [order],
     * returning the resulting [OrderCompleted] event.
     */
    fun completeOrder(order: Order, command: CompleteOrderCommand): OrderCompleted {
        require(order.id == command.orderId) {
            "Command targets order ${command.orderId.value} but was handled against order ${order.id.value}"
        }
        return order.complete()
    }

    /**
     * Coordinates a [CancelOrderCommand] against the given [order],
     * returning the resulting [OrderCancelled] event.
     */
    fun cancelOrder(order: Order, command: CancelOrderCommand): OrderCancelled {
        require(order.id == command.orderId) {
            "Command targets order ${command.orderId.value} but was handled against order ${order.id.value}"
        }
        return order.cancel()
    }
}

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
 * Decides Business Meaning"). After each transition, the service persists
 * the affected [Order] through [orderRepository]
 * (PERSISTENCE_ARCHITECTURE.md Section 3, "Order Management") — the only
 * point in this module where persistence is invoked; the [Order] aggregate
 * itself remains entirely unaware that a repository exists. [completeOrder]
 * and [cancelOrder] still operate on an [Order] instance supplied by the
 * caller, since [OrderRepository] offers lookup by id but no query
 * capability beyond that.
 */
@Service
class OrderLifecycleApplicationService(
    private val orderRepository: OrderRepository
) {

    /**
     * Coordinates a [SubmitOrderCommand], producing a new order and the
     * [com.pios.ordermanagement.domain.OrderSubmitted] event recording it,
     * and persisting the new order.
     */
    fun submitOrder(command: SubmitOrderCommand): SubmittedOrder {
        val submitted = Order.submit()
        orderRepository.save(submitted.order)
        return submitted
    }

    /**
     * Coordinates a [CompleteOrderCommand] against the given [order],
     * returning the resulting [OrderCompleted] event and persisting the
     * order's new status.
     */
    fun completeOrder(order: Order, command: CompleteOrderCommand): OrderCompleted {
        require(order.id == command.orderId) {
            "Command targets order ${command.orderId.value} but was handled against order ${order.id.value}"
        }
        val event = order.complete()
        orderRepository.save(order)
        return event
    }

    /**
     * Coordinates a [CancelOrderCommand] against the given [order],
     * returning the resulting [OrderCancelled] event and persisting the
     * order's new status.
     */
    fun cancelOrder(order: Order, command: CancelOrderCommand): OrderCancelled {
        require(order.id == command.orderId) {
            "Command targets order ${command.orderId.value} but was handled against order ${order.id.value}"
        }
        val event = order.cancel()
        orderRepository.save(order)
        return event
    }
}

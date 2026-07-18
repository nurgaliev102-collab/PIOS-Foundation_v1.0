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
 * and [cancelOrder] are each overloaded: one form still operates on an
 * [Order] instance supplied by the caller, the other restores the
 * [Order] from [orderRepository] by id first, proving the aggregate can
 * be saved, loaded back, and continue its own domain operation exactly
 * as it would if it had never left memory.
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
     * Coordinates a [CompleteOrderCommand] by first restoring the
     * targeted Order through [orderRepository]. Throws
     * [OrderNotFoundException] — an application-layer error, never a
     * persistence or domain one (see that class's own KDoc) — if no
     * Order identified by [CompleteOrderCommand.orderId] has been saved.
     */
    fun completeOrder(command: CompleteOrderCommand): OrderCompleted {
        val order = orderRepository.findById(command.orderId) ?: throw OrderNotFoundException(command.orderId)
        return completeOrder(order, command)
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

    /**
     * Coordinates a [CancelOrderCommand] by first restoring the targeted
     * Order through [orderRepository]. Throws [OrderNotFoundException] —
     * an application-layer error, never a persistence or domain one (see
     * that class's own KDoc) — if no Order identified by
     * [CancelOrderCommand.orderId] has been saved.
     */
    fun cancelOrder(command: CancelOrderCommand): OrderCancelled {
        val order = orderRepository.findById(command.orderId) ?: throw OrderNotFoundException(command.orderId)
        return cancelOrder(order, command)
    }
}

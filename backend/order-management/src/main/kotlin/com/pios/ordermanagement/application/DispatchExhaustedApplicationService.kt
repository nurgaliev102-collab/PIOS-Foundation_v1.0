package com.pios.ordermanagement.application

import com.pios.ordermanagement.domain.OrderId
import com.pios.ordermanagement.domain.OrderStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/** Converts Dispatch's no-offer fact into Order Management's terminal status. */
@Service
class DispatchExhaustedApplicationService(
    private val orders: OrderRepository,
    private val lifecycle: OrderLifecycleApplicationService,
    private val transactionRunner: TransactionRunner
) {
    private val logger = LoggerFactory.getLogger(DispatchExhaustedApplicationService::class.java)

    fun handle(orderReference: String) = transactionRunner.run {
        val orderId = OrderId(orderReference)
        val order = orders.findByIdForUpdate(orderId) ?: throw OrderNotFoundException(orderId)
        if (order.status == OrderStatus.SUBMITTED) {
            lifecycle.markUnfulfilled(order)
        } else {
            // A redelivery, cancellation, or completed ride cannot undo a
            // terminal outcome. The row lock makes this check atomic.
            logger.info("Dispatch exhausted for order {} already in status {}", orderReference, order.status)
        }
    }
}

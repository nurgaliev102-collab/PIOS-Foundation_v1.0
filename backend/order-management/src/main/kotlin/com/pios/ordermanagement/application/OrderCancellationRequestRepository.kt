package com.pios.ordermanagement.application

import java.time.Instant

data class OrderCancellationRequestRecord(
    val requestId: String,
    val orderId: String,
    val reasonCode: String?,
    val note: String?,
    val outcome: String,
    val requestedAt: Instant,
    val resolvedAt: Instant? = null
)

data class OrderTerminationRecord(
    val orderId: String,
    val requestId: String,
    val assignmentId: String,
    val driverId: String,
    val initiator: String,
    val reasonCode: String,
    val note: String?,
    val terminatedAt: Instant
)

interface OrderCancellationRequestRepository {
    fun findById(requestId: String): OrderCancellationRequestRecord?
    fun findPendingByOrder(orderId: String): OrderCancellationRequestRecord?
    fun findLatestByOrder(orderId: String): OrderCancellationRequestRecord?
    fun save(record: OrderCancellationRequestRecord)
    fun resolve(requestId: String, outcome: String, at: Instant)
    fun findTermination(orderId: String): OrderTerminationRecord?
    fun saveTermination(record: OrderTerminationRecord)
}

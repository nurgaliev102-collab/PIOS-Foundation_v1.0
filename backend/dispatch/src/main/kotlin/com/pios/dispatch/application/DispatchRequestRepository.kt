package com.pios.dispatch.application

import java.time.Instant

enum class DispatchRequestState { PENDING, OFFERED, UNFULFILLED, CANCELLED }

data class DispatchRequestRecord(
    val orderId: String,
    val passengerReference: String,
    val isTest: Boolean,
    val explicitDriverIntent: Boolean,
    val requestedDriverId: String?,
    val requestedPickupAt: Instant?,
    val submittedAt: Instant,
    val expiresAt: Instant,
    val nextAttemptAt: Instant,
    val state: DispatchRequestState = DispatchRequestState.PENDING
)

/** Every call except [findDueOrderIds] must participate in the caller's transaction. */
interface DispatchRequestRepository {
    fun insertIfAbsent(record: DispatchRequestRecord)
    fun findForUpdate(orderId: String): DispatchRequestRecord?
    fun findDueOrderIds(now: Instant, limit: Int): List<String>
    fun markOffered(orderId: String)
    fun scheduleRetry(orderId: String, nextAttemptAt: Instant)
    fun markUnfulfilled(orderId: String)
    fun markCancelled(orderId: String)
}

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

    /**
     * Reopens [orderId]'s routing obligation from `OFFERED` back to
     * `PENDING` with `next_attempt_at` set to [nextAttemptAt] (ADR-078,
     * Dispatch Recovery After Proposal Decline or Lapse, Decision A) — so a
     * resumed sweep can retry with the refusing driver excluded (Decision
     * B). Conditional in SQL (`WHERE state = 'OFFERED'`), never
     * read-then-write, so a row already `CANCELLED` or `UNFULFILLED` is
     * left untouched (both are terminal and must never be revived) and a
     * row already `PENDING` is not disturbed either. Deliberately does not
     * touch `submitted_at`/`expires_at` — the routing window does not
     * restart (ADR-078 Decision B, "the window continues from the
     * original `OrderSubmitted.occurredAt`").
     */
    fun reopenIfOffered(orderId: String, nextAttemptAt: Instant)

    /**
     * Batch-reads [DispatchRequestRecord.requestedPickupAt] for [orderIds],
     * keyed by order id (D-11.B, Driver Calendar — read-only,
     * informational view; `ADR-084` Part 2: "a read joining `assignments`
     * to `dispatch_requests.requested_pickup_at`"). An order with no row,
     * or a row whose `requested_pickup_at` is `null`, is simply absent
     * from the returned map — never a `null` value — so a caller can
     * treat "absent" as one condition throughout. A plain, unlocked read
     * (unlike [findForUpdate]): the driver calendar only displays this
     * fact, it never acts on it, so no row lock is needed or taken.
     * [orderIds] empty returns an empty map, mirroring
     * [com.pios.dispatch.application.AssignmentRepository.findByOrders]'s
     * own "empty in, empty out" convention.
     */
    fun findPickupTimesForOrders(orderIds: List<String>): Map<String, Instant>
}

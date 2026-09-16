package com.pios.dispatch.application

import java.time.Instant

internal class InMemoryDispatchRequestRepository : DispatchRequestRepository {
    private val records: MutableMap<String, DispatchRequestRecord> = mutableMapOf()
    val cancelledOrderIds: MutableSet<String> = mutableSetOf()

    override fun insertIfAbsent(record: DispatchRequestRecord) {
        if (record.orderId !in cancelledOrderIds) records.putIfAbsent(record.orderId, record)
    }

    override fun findForUpdate(orderId: String): DispatchRequestRecord? = records[orderId]

    override fun findDueOrderIds(now: Instant, limit: Int): List<String> = records.values
        .filter { it.state == DispatchRequestState.PENDING && !it.nextAttemptAt.isAfter(now) }
        .sortedWith(compareBy<DispatchRequestRecord> { it.nextAttemptAt }.thenBy { it.orderId })
        .take(limit)
        .map { it.orderId }

    override fun markOffered(orderId: String) = update(orderId, DispatchRequestState.OFFERED)
    override fun markUnfulfilled(orderId: String) = update(orderId, DispatchRequestState.UNFULFILLED)
    override fun scheduleRetry(orderId: String, nextAttemptAt: Instant) {
        records.computeIfPresent(orderId) { _, value -> value.copy(nextAttemptAt = nextAttemptAt) }
    }

    override fun markCancelled(orderId: String) {
        cancelledOrderIds.add(orderId)
        records.computeIfPresent(orderId) { _, value -> value.copy(state = DispatchRequestState.CANCELLED) }
    }

    override fun reopenIfOffered(orderId: String, nextAttemptAt: Instant) {
        records.computeIfPresent(orderId) { _, value ->
            if (value.state == DispatchRequestState.OFFERED) {
                value.copy(state = DispatchRequestState.PENDING, nextAttemptAt = nextAttemptAt)
            } else {
                value
            }
        }
    }

    private fun update(orderId: String, state: DispatchRequestState) {
        records.computeIfPresent(orderId) { _, value -> value.copy(state = state) }
    }
}

package com.pios.ordermanagement.persistence

import com.pios.ordermanagement.application.OrderCancellationRequestRecord
import com.pios.ordermanagement.application.OrderCancellationRequestRepository
import com.pios.ordermanagement.application.OrderTerminationRecord
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class InMemoryOrderCancellationRequestRepository : OrderCancellationRequestRepository {
    private val requests = ConcurrentHashMap<String, OrderCancellationRequestRecord>()
    private val terminations = ConcurrentHashMap<String, OrderTerminationRecord>()

    override fun findById(requestId: String): OrderCancellationRequestRecord? = requests[requestId]
    override fun findPendingByOrder(orderId: String): OrderCancellationRequestRecord? =
        requests.values.firstOrNull { it.orderId == orderId && it.outcome == "PENDING" }
    override fun findLatestByOrder(orderId: String): OrderCancellationRequestRecord? =
        requests.values.filter { it.orderId == orderId }.maxByOrNull { it.requestedAt }
    override fun save(record: OrderCancellationRequestRecord) {
        check(requests.putIfAbsent(record.requestId, record) == null)
    }
    override fun resolve(requestId: String, outcome: String, at: Instant) {
        requests.computeIfPresent(requestId) { _, current ->
            if (current.outcome == "PENDING") current.copy(outcome = outcome, resolvedAt = at) else current
        }
    }
    override fun findTermination(orderId: String): OrderTerminationRecord? = terminations[orderId]
    override fun saveTermination(record: OrderTerminationRecord) {
        check(terminations.putIfAbsent(record.orderId, record) == null)
    }
}

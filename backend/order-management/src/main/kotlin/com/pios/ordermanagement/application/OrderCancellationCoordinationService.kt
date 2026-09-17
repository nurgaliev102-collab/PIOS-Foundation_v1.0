package com.pios.ordermanagement.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.ordermanagement.domain.OrderId
import com.pios.ordermanagement.domain.OrderStatus
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.util.UUID

data class RequestOrderCancellationCommand(
    val requestId: String,
    val orderId: String,
    val reasonCode: String?,
    val note: String?
)

@Service
class OrderCancellationCoordinationService(
    private val orders: OrderRepository,
    private val requests: OrderCancellationRequestRepository,
    private val lifecycle: OrderLifecycleApplicationService,
    private val outbox: OutboxRepository,
    private val transactions: TransactionRunner,
    private val mapper: ObjectMapper,
    private val clock: Clock = Clock.systemUTC()
) {
    fun request(command: RequestOrderCancellationCommand): OrderCancellationRequestRecord = transactions.run {
        UUID.fromString(command.requestId)
        require(command.reasonCode == null || command.reasonCode in PASSENGER_REASONS)
        require(command.note == null || (command.reasonCode != null && command.note.length <= 2000))
        val order = orders.findByIdForUpdate(OrderId(command.orderId)) ?: throw OrderNotFoundException(OrderId(command.orderId))
        val existing = requests.findById(command.requestId)
        if (existing != null) {
            check(existing.orderId == command.orderId && existing.reasonCode == command.reasonCode && existing.note == command.note) {
                "requestId is already bound to another cancellation command"
            }
            return@run existing
        }
        check(order.status == OrderStatus.SUBMITTED) { "Order is already terminal" }
        check(requests.findPendingByOrder(command.orderId) == null) { "Order already has a pending cancellation" }
        val record = OrderCancellationRequestRecord(
            command.requestId, command.orderId, command.reasonCode, command.note, "PENDING", clock.instant()
        )
        requests.save(record)
        outbox.save(event(
            command.orderId,
            "OrderCancellationRequested",
            "order.cancellation-requested",
            mapOf(
                "requestId" to command.requestId,
                "orderId" to command.orderId,
                "reasonCode" to command.reasonCode,
                "note" to command.note
            )
        ))
        record
    }

    fun resolveNoCommitment(requestId: String, orderId: String, outcome: String) = transactions.run {
        require(outcome in RESOLUTION_OUTCOMES)
        val order = orders.findByIdForUpdate(OrderId(orderId)) ?: throw OrderNotFoundException(OrderId(orderId))
        val record = requests.findById(requestId) ?: error("Unknown cancellation request $requestId")
        check(record.orderId == orderId)
        if (record.outcome != "PENDING") {
            check(record.outcome == outcome) { "Cancellation result cannot be changed" }
            return@run
        }
        if (outcome == "NO_COMMITMENT") {
            check(order.status == OrderStatus.SUBMITTED || order.status == OrderStatus.CANCELLED)
            if (order.status == OrderStatus.SUBMITTED) {
                lifecycle.cancelOrder(order, CancelOrderCommand(order.id))
            }
        }
        requests.resolve(requestId, outcome, clock.instant())
    }

    fun resolveTermination(fact: OrderTerminationRecord) = transactions.run {
        require(fact.reasonCode in when (fact.initiator) {
            "PASSENGER" -> PASSENGER_REASONS
            "DRIVER" -> DRIVER_REASONS
            "SYSTEM" -> setOf("SYSTEM_TIMEOUT")
            else -> emptySet()
        })
        require(fact.note == null || fact.note.length <= 2000)
        val order = orders.findByIdForUpdate(OrderId(fact.orderId)) ?: throw OrderNotFoundException(OrderId(fact.orderId))
        val existing = requests.findTermination(fact.orderId)
        if (existing != null) {
            check(existing == fact) { "Order termination fact cannot be changed" }
            return@run
        }
        check(order.status == OrderStatus.SUBMITTED) { "A completed or cancelled order cannot be terminated" }
        requests.saveTermination(fact)
        requests.findPendingByOrder(fact.orderId)?.let { pending ->
            val outcome = if (pending.requestId == fact.requestId) "TERMINATED" else "ALREADY_TERMINATED"
            requests.resolve(pending.requestId, outcome, fact.terminatedAt)
        }
        lifecycle.cancelOrder(order, CancelOrderCommand(order.id))
    }

    fun findLatest(orderId: String): OrderCancellationRequestRecord? = requests.findLatestByOrder(orderId)
    fun findTermination(orderId: String): OrderTerminationRecord? = requests.findTermination(orderId)

    private fun event(orderId: String, type: String, key: String, payload: Map<String, String?>): OutboxRecord =
        OutboxRecord(
            aggregateId = orderId,
            eventType = type,
            routingKey = key,
            payload = mapper.writeValueAsString(
                mapOf(
                    "eventId" to UUID.randomUUID().toString(),
                    "eventType" to type,
                    "eventVersion" to 1,
                    "occurredAt" to clock.instant().toString(),
                    "payload" to payload
                )
            )
        )

    companion object {
        val PASSENGER_REASONS: Set<String> = setOf(
            "PLANS_CHANGED", "FOUND_ANOTHER_DRIVER", "DRIVER_UNRESPONSIVE", "CANNOT_CONTINUE", "OTHER"
        )
        val DRIVER_REASONS: Set<String> = setOf(
            "CANNOT_FULFILL", "PASSENGER_UNRESPONSIVE", "PASSENGER_NO_SHOW", "TRIP_CONDITIONS_CHANGED", "OTHER"
        )
        val RESOLUTION_OUTCOMES: Set<String> = setOf(
            "NO_COMMITMENT", "REASON_REQUIRED", "ALREADY_COMPLETED", "ALREADY_TERMINATED"
        )
    }
}

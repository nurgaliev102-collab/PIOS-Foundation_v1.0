package com.pios.ordermanagement.application

import com.pios.ordermanagement.domain.OrderId
import com.pios.ordermanagement.domain.OrderStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Application-layer coordination for consuming AssignmentCompleted
 * (ADR-041, Order Lifecycle Synchronization with Assignment Completion).
 * Unlike [OrderAssignmentRecognitionHandler] (AssignmentAccepted's own
 * consumer path, which deliberately does not transition the Order — see
 * its own KDoc), this service *does* transition the Order: ADR-041
 * Decision item 1 makes `AssignmentCompleted` the single trigger that
 * completes an Order.
 *
 * [handle] mirrors [AssignmentAcceptedProjectionApplicationService]'s own
 * idempotency shape (mark the eventId processed, skip the business effect
 * on a redelivery) but adds the state-dependent branch ADR-041 Decision
 * item 3 requires: the Order referenced by
 * [AssignmentCompletedUpdateCommand.orderReference] is resolved locally
 * through [orderRepository] and
 * - if it is [OrderStatus.SUBMITTED], the transition goes through
 *   [orderLifecycleApplicationService]'s own `completeOrder` — never
 *   through direct aggregate or repository manipulation (ADR-041 Decision
 *   item 6), since that is what writes the `OrderCompleted` outbox record;
 * - if it is not (already [OrderStatus.COMPLETED], a redelivery or a
 *   second assignment for the same order; or [OrderStatus.CANCELLED], the
 *   existing status wins per ADR-041 Q3), `Order.complete()` is never
 *   called and the status is never overwritten — the conflict is only
 *   logged (ADR-012) at warning level, never thrown, since it is a
 *   business conflict, not a transient failure.
 *
 * An [orderReference] that resolves to no persisted Order at all is *not*
 * a business conflict — it is a data or integration fault (ADR-041
 * Decision item 3, "Distinguish anomaly from failure") — so
 * [OrderNotFoundException] is left to propagate, letting the existing
 * bounded retry-then-dead-letter policy apply exactly as it does for
 * AssignmentAccepted today. Because this whole method runs inside one
 * [transactionRunner] boundary (ADR-032), an uncaught exception also rolls
 * back the eventId's own idempotency mark, so a genuinely failed attempt
 * is never mistaken for an already-handled duplicate on retry.
 *
 * [eventId] is recorded in the idempotency ledger *before* the status
 * branch runs and regardless of which branch is taken (ADR-041 Q3's own
 * ruling: "the idempotency-ledger row is written anyway, so that a
 * redelivery does not re-process the same event") — a redelivered
 * `AssignmentCompleted` for an already-`CANCELLED` Order must not log the
 * same anomaly a second time.
 *
 * [assignmentCompletedRepository] and [orderRepository] have no default,
 * for the same silent-data-loss reason already established for
 * [AssignmentAcceptedProjectionApplicationService]: a production context
 * missing a real bean must fail to start, not silently discard events.
 */
@Service
class AssignmentCompletedApplicationService(
    private val assignmentCompletedRepository: AssignmentCompletedRepository,
    private val orderRepository: OrderRepository,
    private val orderLifecycleApplicationService: OrderLifecycleApplicationService,
    private val transactionRunner: TransactionRunner = NoOpTransactionRunner
) {
    private val logger = LoggerFactory.getLogger(AssignmentCompletedApplicationService::class.java)

    fun handle(command: AssignmentCompletedUpdateCommand) = transactionRunner.run {
        val isNewEvent = assignmentCompletedRepository.markProcessed(command.eventId)
        if (isNewEvent) {
            val orderId = OrderId(command.orderReference)
            val order = orderRepository.findByIdForUpdate(orderId) ?: throw OrderNotFoundException(orderId)
            if (order.status == OrderStatus.SUBMITTED) {
                orderLifecycleApplicationService.completeOrder(order, CompleteOrderCommand(order.id))
            } else {
                logger.warn(
                    "AssignmentCompleted (eventId {}) received for order {} in status {}; not transitioning",
                    command.eventId,
                    orderId.value,
                    order.status
                )
            }
        }
    }
}

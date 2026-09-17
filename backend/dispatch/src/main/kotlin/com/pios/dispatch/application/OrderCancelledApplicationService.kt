package com.pios.dispatch.application

import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.domain.ProposalStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Application-layer coordination for consuming Order Management's
 * `OrderCancelled` (ADR-053, Proposal Resolution on Order Cancellation —
 * Accepted, Part 1: the triggering fact originates in Order Management;
 * the transition it causes belongs exclusively to Dispatch). Mirrors
 * [DriverAvailabilityProjectionApplicationService]'s own idempotency shape
 * exactly: [handle] records [OrderCancelledUpdateCommand.eventId] and
 * applies the local effect together, inside one [transactionRunner]
 * boundary (ADR-032), so a redelivered event (RabbitMQ being an
 * at-least-once broker, ADR-029/ADR-031) never re-applies its effect.
 *
 * The effect itself, per ADR-053 Part 1's own illustrative diagram: find
 * the cancelled order's own `OPEN` Proposal, if one exists, via
 * [proposalRepository.findByOrder] — the same lookup
 * [ProposalApplicationService.handle] already performs to enforce the
 * Root Invariant — and invoke [ProposalApplicationService.withdrawProposal]
 * through the same application-layer path `accept`/`decline`/`lapse`
 * already use, never [com.pios.dispatch.domain.Proposal.withdraw]
 * directly.
 *
 * **No Proposal found, or the order's Proposal is already resolved, is
 * not an error.** An Order may be cancelled before any Proposal was ever
 * created for it, or after its Proposal already resolved another way
 * (accepted, declined, or lapsed) — `DOMAIN_MODEL.md` §11's own invariant
 * only ever guarantees at most one *open* Proposal per order, never that
 * one currently exists. [handle] finds nothing to withdraw in either
 * case and does nothing further, silently and correctly.
 *
 * [orderCancelledRepository] and [proposalRepository] have no default,
 * for the same silent-data-loss reason already established for
 * [DriverAvailabilityProjectionApplicationService] — a production context
 * missing a real bean must fail to start, not silently discard events.
 */
@Service
class OrderCancelledApplicationService(
    private val orderCancelledRepository: OrderCancelledRepository,
    private val proposalRepository: ProposalRepository,
    private val proposalApplicationService: ProposalApplicationService,
    private val dispatchRequestRepository: DispatchRequestRepository,
    private val transactionRunner: TransactionRunner = NoOpTransactionRunner,
    private val assignmentRepository: AssignmentRepository? = null,
    private val tripRepository: TripRepository? = null,
    private val orderGuard: OrderGuard = NoOpOrderGuard
) {
    private val logger = LoggerFactory.getLogger(OrderCancelledApplicationService::class.java)

    fun handle(command: OrderCancelledUpdateCommand) = transactionRunner.run {
        val order = OrderReference(command.orderReference)
        orderGuard.lock(order)
        val assignment = assignmentRepository?.findByOrder(order)?.firstOrNull()
        val trip = assignment?.let { tripRepository?.findByAssignmentId(it.id) }
        check(assignment == null || trip?.status == com.pios.dispatch.domain.TripStatus.TERMINATED) {
            "Legacy OrderCancelled cannot stop an active or completed Trip for ${order.orderId}"
        }
        val isNewEvent = orderCancelledRepository.markProcessed(command.eventId)
        if (isNewEvent) {
            // The cancellation tombstone must exist even when this event
            // arrives before OrderSubmitted on its independent queue.
            dispatchRequestRepository.markCancelled(order.orderId)
            val openProposal = proposalRepository.findByOrder(order).firstOrNull {
                it.status == ProposalStatus.OPEN || it.status == ProposalStatus.PRICE_PROPOSED
            }
            if (openProposal != null) {
                proposalApplicationService.withdrawProposal(openProposal, WithdrawProposalCommand(openProposal.id))
            } else {
                logger.debug(
                    "OrderCancelled (eventId {}) received for order {}; no OPEN proposal to withdraw",
                    command.eventId,
                    command.orderReference
                )
            }
        }
    }
}

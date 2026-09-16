package com.pios.dispatch.application

import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.domain.ProposalStatus
import com.pios.dispatch.persistence.InMemoryProposalRepository
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Fast, DB-free unit test of [OrderCancelledApplicationService]'s own
 * idempotency and orchestration logic, using an in-memory fake
 * [OrderCancelledRepository] and the real [InMemoryProposalRepository] /
 * [ProposalApplicationService] (ADR-053, Proposal Resolution on Order
 * Cancellation — Accepted). The real-transaction and real-broker
 * guarantees are proven separately, against real infrastructure, by
 * `com.pios.dispatch.persistence.OrderCancelledConsumerIntegrationTest`,
 * mirroring [DriverAvailabilityProjectionApplicationServiceTest]'s own
 * split exactly.
 */
class OrderCancelledApplicationServiceTest {

    private class InMemoryOrderCancelledRepository : OrderCancelledRepository {
        val processedEventIds = mutableSetOf<String>()
        override fun markProcessed(eventId: String): Boolean = processedEventIds.add(eventId)
    }

    private val orderCancelledRepository = InMemoryOrderCancelledRepository()
    private val proposalRepository = InMemoryProposalRepository()
    private val dispatchRequests = InMemoryDispatchRequestRepository()

    // ADR-078: dispatchRequestRepository is wired here (unlike this class's
    // pre-ADR-078 shape) specifically so `an order cancellation withdraws...`
    // below can prove the withdraw path never reopens the CANCELLED
    // tombstone it shares this exact repository instance with -- every
    // other existing test in this class is unaffected, since none of them
    // ever inserts a dispatch_requests row for the orders they use, so
    // ProposalApplicationService.handle's own routing-state check
    // (`routingState != CANCELLED && != UNFULFILLED`) still sees `null` and
    // behaves exactly as before this wiring.
    private val proposalApplicationService = ProposalApplicationService(proposalRepository, dispatchRequestRepository = dispatchRequests)
    private val service = OrderCancelledApplicationService(
        orderCancelledRepository, proposalRepository, proposalApplicationService, dispatchRequests
    )

    @Test
    fun `a new event withdraws the order's own OPEN proposal`() {
        val order = OrderReference("order-1")
        val proposal = proposalApplicationService.handle(ProposeDriverCommand(order, DriverReference("driver-1"))).proposal

        service.handle(OrderCancelledUpdateCommand(eventId = "event-1", orderReference = order.orderId))

        assertEquals(ProposalStatus.WITHDRAWN, proposalRepository.findById(proposal.id)?.status)
    }

    @Test
    fun `an order with no proposal at all is handled without error`() {
        service.handle(OrderCancelledUpdateCommand(eventId = "event-2", orderReference = "order-never-proposed"))
        assertTrue(dispatchRequests.cancelledOrderIds.contains("order-never-proposed"))
    }

    @Test
    fun `an order whose only proposal already resolved another way is left untouched`() {
        val order = OrderReference("order-3")
        val proposal = proposalApplicationService.handle(ProposeDriverCommand(order, DriverReference("driver-3"))).proposal
        proposalApplicationService.acceptProposal(proposal, AcceptProposalCommand(proposal.id))

        service.handle(OrderCancelledUpdateCommand(eventId = "event-3", orderReference = order.orderId))

        assertEquals(ProposalStatus.ACCEPTED, proposalRepository.findById(proposal.id)?.status)
    }

    @Test
    fun `a redelivered eventId does not re-apply its effect`() {
        val order = OrderReference("order-4")
        val proposal = proposalApplicationService.handle(ProposeDriverCommand(order, DriverReference("driver-4"))).proposal
        service.handle(OrderCancelledUpdateCommand(eventId = "event-4", orderReference = order.orderId))
        val respondedAtAfterFirstHandle = proposalRepository.findById(proposal.id)?.respondedAt

        // A second delivery of the same eventId must not attempt to
        // withdraw an already-WITHDRAWN proposal again (which would throw,
        // per Proposal.withdraw()'s own OPEN-only precondition) -- must
        // not throw, and must not change respondedAt.
        service.handle(OrderCancelledUpdateCommand(eventId = "event-4", orderReference = order.orderId))

        assertEquals(ProposalStatus.WITHDRAWN, proposalRepository.findById(proposal.id)?.status)
        assertEquals(respondedAtAfterFirstHandle, proposalRepository.findById(proposal.id)?.respondedAt)
    }

    @Test
    fun `distinct orders' own proposals are each withdrawn independently`() {
        val orderA = OrderReference("order-5a")
        val orderB = OrderReference("order-5b")
        val proposalA = proposalApplicationService.handle(ProposeDriverCommand(orderA, DriverReference("driver-5a"))).proposal
        val proposalB = proposalApplicationService.handle(ProposeDriverCommand(orderB, DriverReference("driver-5b"))).proposal

        service.handle(OrderCancelledUpdateCommand(eventId = "event-5a", orderReference = orderA.orderId))

        assertEquals(ProposalStatus.WITHDRAWN, proposalRepository.findById(proposalA.id)?.status)
        assertEquals(ProposalStatus.OPEN, proposalRepository.findById(proposalB.id)?.status)
    }

    // --- ADR-078 (Dispatch Recovery After Proposal Decline or Lapse), Decision A requirement 2 ---

    @Test
    fun `an order cancellation withdraws the proposal but never reopens the CANCELLED tombstone, even though withdraw resolves a proposal in the same flow`() {
        val order = OrderReference("order-6")
        val proposal = proposalApplicationService.handle(ProposeDriverCommand(order, DriverReference("driver-6"))).proposal
        val now = Instant.now()
        dispatchRequests.insertIfAbsent(
            DispatchRequestRecord(
                orderId = order.orderId,
                passengerReference = "passenger-6",
                isTest = false,
                explicitDriverIntent = false,
                requestedDriverId = null,
                requestedPickupAt = null,
                submittedAt = now,
                expiresAt = now.plusSeconds(120),
                nextAttemptAt = now
            )
        )
        dispatchRequests.markOffered(order.orderId)

        service.handle(OrderCancelledUpdateCommand(eventId = "event-6", orderReference = order.orderId))

        // Proposal.withdraw (ADR-053) resolves the proposal in the same
        // handle() call that writes the CANCELLED tombstone -- proving
        // ADR-078's reopen (wired only into decline/lapse, never withdraw)
        // does not fire here, and the tombstone is never disturbed.
        assertEquals(ProposalStatus.WITHDRAWN, proposalRepository.findById(proposal.id)?.status)
        assertEquals(DispatchRequestState.CANCELLED, dispatchRequests.findForUpdate(order.orderId)?.state)
    }
}

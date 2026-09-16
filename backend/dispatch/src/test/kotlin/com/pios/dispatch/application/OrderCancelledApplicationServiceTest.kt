package com.pios.dispatch.application

import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.domain.ProposalStatus
import com.pios.dispatch.persistence.InMemoryProposalRepository
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
    private val proposalApplicationService = ProposalApplicationService(proposalRepository)
    private val dispatchRequests = InMemoryDispatchRequestRepository()
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
}

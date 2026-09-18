package com.pios.dispatch.application

import com.pios.dispatch.domain.Assignment
import com.pios.dispatch.domain.AssignmentStatus
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.domain.ProposalId
import com.pios.dispatch.domain.ProposalStatus
import com.pios.dispatch.persistence.InMemoryAssignmentRepository
import com.pios.dispatch.persistence.InMemoryProposalRepository
import com.pios.dispatch.persistence.InMemoryTripRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers Sprint IMPLEMENTATION-004's own minimum required scenarios for
 * [ProposalAssignmentOrchestrationService], entirely in memory, at the
 * application layer directly (independent of REST -- [ProposalController]
 * and its own tests exercise the same behavior one layer up).
 */
class ProposalAssignmentOrchestrationServiceTest {

    private val proposalRepository = InMemoryProposalRepository()
    private val proposalApplicationService = ProposalApplicationService(proposalRepository)
    private val assignmentRepository = InMemoryAssignmentRepository()
    private val tripRepository = InMemoryTripRepository()
    private val dispatchAssignmentApplicationService =
        DispatchAssignmentApplicationService(assignmentRepository, tripRepository = tripRepository)
    private val orchestrationService = ProposalAssignmentOrchestrationService(
        proposalRepository,
        proposalApplicationService,
        dispatchAssignmentApplicationService
    )

    private val order = OrderReference("order-1")
    private val driver = DriverReference("driver-1")

    @Test
    fun `accepting a proposal creates an Assignment automatically for the same order and driver`() {
        val proposal = proposalApplicationService.handle(ProposeDriverCommand(order, driver)).proposal

        val outcome = orchestrationService.acceptProposal(AcceptProposalCommand(proposal.id))

        assertEquals(order, outcome.assignmentCreated.assignment.order)
        assertEquals(driver, outcome.assignmentCreated.assignment.driver)
        assertEquals(AssignmentStatus.CREATED, outcome.assignmentCreated.assignment.status)
    }

    @Test
    fun `accepting a proposal marks it ACCEPTED and persists the Assignment through the repository`() {
        val proposal = proposalApplicationService.handle(ProposeDriverCommand(order, driver)).proposal

        val outcome = orchestrationService.acceptProposal(AcceptProposalCommand(proposal.id))

        assertEquals(ProposalStatus.ACCEPTED, proposalRepository.findById(proposal.id)?.status)
        assertEquals(order, assignmentRepository.findById(outcome.assignmentCreated.assignment.id)?.order)
    }

    @Test
    fun `accepting an already-accepted proposal is rejected and does not create a second Assignment`() {
        val proposal = proposalApplicationService.handle(ProposeDriverCommand(order, driver)).proposal
        orchestrationService.acceptProposal(AcceptProposalCommand(proposal.id))

        assertFailsWith<IllegalStateException> {
            orchestrationService.acceptProposal(AcceptProposalCommand(proposal.id))
        }

        assertEquals(1, assignmentRepository.findByOrder(order).size)
    }

    @Test
    fun `accepting a proposal twice never produces two assignments for the same order`() {
        val first = proposalApplicationService.handle(ProposeDriverCommand(order, driver)).proposal
        orchestrationService.acceptProposal(AcceptProposalCommand(first.id))

        val second = proposalApplicationService.handle(ProposeDriverCommand(order, DriverReference("driver-2")))
            .proposal
        assertFailsWith<IllegalStateException> {
            // The order already has an Assignment from the first proposal's acceptance,
            // so a second proposal for the same order cannot itself be accepted into one.
            orchestrationService.acceptProposal(AcceptProposalCommand(second.id))
        }

        assertEquals(1, assignmentRepository.findByOrder(order).size)
    }

    @Test
    fun `a declined proposal never creates an Assignment`() {
        val proposal = proposalApplicationService.handle(ProposeDriverCommand(order, driver)).proposal

        proposalApplicationService.declineProposal(proposal, DeclineProposalCommand(proposal.id))

        assertTrue(assignmentRepository.findByOrder(order).isEmpty())
    }

    @Test
    fun `a lapsed proposal never creates an Assignment`() {
        val proposal = proposalApplicationService.handle(ProposeDriverCommand(order, driver)).proposal

        proposalApplicationService.lapseProposal(proposal, LapseProposalCommand(proposal.id))

        assertTrue(assignmentRepository.findByOrder(order).isEmpty())
    }

    @Test
    fun `an order that already has an Assignment blocks acceptance of a Proposal for it`() {
        assignmentRepository.save(Assignment.create(order, DriverReference("driver-preexisting")).assignment)
        // Proposal.propose's own Root Invariant only blocks another OPEN proposal for the
        // same order, not a pre-existing Assignment -- the two invariants are independent
        // (Domain Analysis -- Is Assignment the Correct Aggregate Before Driver Acceptance?),
        // so creating this proposal itself succeeds; only its later acceptance is blocked.
        val proposal = proposalApplicationService.handle(ProposeDriverCommand(order, driver)).proposal

        assertFailsWith<IllegalStateException> {
            orchestrationService.acceptProposal(AcceptProposalCommand(proposal.id))
        }

        // This class's own harness uses NoOpTransactionRunner (this test never
        // supplies a real one), so it has no rollback mechanism at all -- the
        // Proposal's in-memory save from before the throw simply stays applied.
        // That is why this assertion still reads ACCEPTED here: it reflects what
        // this in-memory harness is capable of showing, not ADR-036's actual
        // guarantee. The real guarantee -- that a real transaction rolls the
        // Proposal back to OPEN together with the rejected Assignment -- is
        // proven against real PostgreSQL in
        // ProposalAssignmentOrchestrationTransactionTest, not here.
        assertEquals(ProposalStatus.ACCEPTED, proposalRepository.findById(proposal.id)?.status)
        assertEquals(1, assignmentRepository.findByOrder(order).size)
    }

    @Test
    fun `accepting a proposal for an unknown id throws ProposalNotFoundException`() {
        assertFailsWith<ProposalNotFoundException> {
            orchestrationService.acceptProposal(AcceptProposalCommand(ProposalId("never-created")))
        }
    }

    // --- isTest (Owner Control Center test/production data separation, 2026-08-17) ---

    @Test
    fun `accepting an isTest proposal creates an isTest assignment -- same-module derivation, not a cross-module read`() {
        val proposal = proposalApplicationService.handle(ProposeDriverCommand(order, driver, isTest = true)).proposal

        val outcome = orchestrationService.acceptProposal(AcceptProposalCommand(proposal.id))

        assertEquals(true, outcome.assignmentCreated.assignment.isTest)
        assertEquals(true, assignmentRepository.findById(outcome.assignmentCreated.assignment.id)?.isTest)
    }

    @Test
    fun `accepting a real (non-test) proposal creates a real assignment, even while other test data exists in the same repositories`() {
        // A test order/driver/proposal already exist elsewhere in these same
        // repositories -- proving the real proposal below is never "infected"
        // by their presence, since Dispatch never cross-references isTest
        // across unrelated aggregates, only carries it from the one Proposal
        // each Assignment is actually created from.
        proposalApplicationService.handle(ProposeDriverCommand(OrderReference("test-order"), DriverReference("test-driver"), isTest = true))

        val realProposal = proposalApplicationService.handle(ProposeDriverCommand(order, driver, isTest = false)).proposal

        val outcome = orchestrationService.acceptProposal(AcceptProposalCommand(realProposal.id))

        assertEquals(false, outcome.assignmentCreated.assignment.isTest)
    }

    // --- D-09.1 (Tier 1 Visible): viaTrustedFallback propagated exactly like isTest ---

    @Test
    fun `accepting a viaTrustedFallback proposal creates a viaTrustedFallback assignment -- same propagation as isTest`() {
        val proposal = proposalApplicationService.handle(
            ProposeDriverCommand(order, driver, viaTrustedFallback = true)
        ).proposal

        val outcome = orchestrationService.acceptProposal(AcceptProposalCommand(proposal.id))

        assertEquals(true, outcome.assignmentCreated.assignment.viaTrustedFallback)
        assertEquals(true, assignmentRepository.findById(outcome.assignmentCreated.assignment.id)?.viaTrustedFallback)
    }

    @Test
    fun `accepting an ordinary proposal (no Tier 1) creates an assignment with viaTrustedFallback false`() {
        val proposal = proposalApplicationService.handle(ProposeDriverCommand(order, driver)).proposal

        val outcome = orchestrationService.acceptProposal(AcceptProposalCommand(proposal.id))

        assertEquals(false, outcome.assignmentCreated.assignment.viaTrustedFallback)
    }

    // --- D-06 (Settlement as Evidence): agreed amount captured at Trip creation ---

    @Test
    fun `accepting a proposal with a stated price captures it as the new Trip's agreedAmount`() {
        val proposal = proposalApplicationService.handle(ProposeDriverCommand(order, driver)).proposal

        val outcome = orchestrationService.acceptProposal(AcceptProposalCommand(proposal.id, statedPrice = "350"))

        val trip = tripRepository.findByAssignmentId(outcome.assignmentCreated.assignment.id)
        assertEquals("350", trip?.agreedAmount)
    }

    @Test
    fun `accepting a proposal with no stated price leaves the new Trip's agreedAmount null`() {
        val proposal = proposalApplicationService.handle(ProposeDriverCommand(order, driver)).proposal

        val outcome = orchestrationService.acceptProposal(AcceptProposalCommand(proposal.id))

        val trip = tripRepository.findByAssignmentId(outcome.assignmentCreated.assignment.id)
        assertNull(trip?.agreedAmount)
    }

    @Test
    fun `confirming a proposed price captures the driver's own stated price as the new Trip's agreedAmount`() {
        val proposal = proposalApplicationService.handle(ProposeDriverCommand(order, driver)).proposal
        proposalApplicationService.proposePrice(proposal, ProposePriceCommand(proposal.id, statedPrice = "620"))

        val outcome = orchestrationService.confirmPrice(ConfirmPriceCommand(proposal.id))

        val trip = tripRepository.findByAssignmentId(outcome.assignmentCreated.assignment.id)
        assertEquals("620", trip?.agreedAmount)
    }
}

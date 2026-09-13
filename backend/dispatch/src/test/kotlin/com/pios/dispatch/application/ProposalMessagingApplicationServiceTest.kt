package com.pios.dispatch.application

import com.pios.dispatch.domain.Assignment
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.MessageSenderRole
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.domain.Proposal
import com.pios.dispatch.persistence.InMemoryAssignmentRepository
import com.pios.dispatch.persistence.InMemoryProposalMessageRepository
import com.pios.dispatch.persistence.InMemoryProposalRepository
import com.pios.dispatch.persistence.InMemoryTripRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Exercises [ProposalMessagingApplicationService] directly against the
 * domain layer (no controller, no HTTP) -- in particular [isMessagingOpen]
 * against every real lifecycle combination this MVP names
 * (OPEN/PRICE_PROPOSED/ACCEPTED/DECLINED/LAPSED/WITHDRAWN).
 *
 * Corrected (live HTTP E2E finding, 2026-09-13): the ACCEPTED-branch cases
 * below now drive ride progress through the real
 * [DispatchAssignmentApplicationService] (`arriveAssignment`/
 * `startAssignment`/`completeAssignment`), the same real production path
 * `AssignmentController`'s own REST endpoints call -- never by mutating
 * [Assignment]'s own `arrive`/`start`/`complete` directly and saving that.
 * The two are not equivalent: since ADR-063/Task 12 ("Ride-progress
 * convergence onto Trip"), the real service transitions [Trip], not
 * [Assignment] -- [Assignment.status] is a frozen, read-only remnant of
 * pre-Trip history (see [DispatchAssignmentApplicationService]'s own KDoc).
 * A test that manually completed an [Assignment] and asserted messaging
 * closed used to pass while the real HTTP path stayed open the whole time,
 * exactly this gap this file's own earlier version had. Mirrors
 * [ProposalApplicationServiceTest]'s own constructor-based convention.
 */
class ProposalMessagingApplicationServiceTest {

    private val proposalRepository = InMemoryProposalRepository()
    private val assignmentRepository = InMemoryAssignmentRepository()
    private val tripRepository = InMemoryTripRepository()
    private val messageRepository = InMemoryProposalMessageRepository()
    private val service = ProposalMessagingApplicationService(proposalRepository, assignmentRepository, tripRepository, messageRepository)
    private val dispatchAssignmentApplicationService = DispatchAssignmentApplicationService(assignmentRepository, tripRepository = tripRepository)
    private val order = OrderReference("order-1")
    private val driver = DriverReference("driver-1")

    private fun freshProposal(): Proposal = Proposal.propose(order, driver).proposal

    /** Creates the real Assignment *and* its connected Trip, exactly as [DispatchAssignmentApplicationService.handle] does for a real accepted Proposal. */
    private fun createRealAssignment(): Assignment = dispatchAssignmentApplicationService.handle(AssignOrderCommand(order, driver)).assignment

    @Test
    fun `messaging is open while OPEN`() {
        assertTrue(service.isMessagingOpen(freshProposal()))
    }

    @Test
    fun `messaging is open while PRICE_PROPOSED`() {
        val proposal = freshProposal()
        proposal.proposePrice("500")

        assertTrue(service.isMessagingOpen(proposal))
    }

    @Test
    fun `messaging is open once ACCEPTED with no assignment yet`() {
        val proposal = freshProposal()
        proposal.accept()

        assertTrue(service.isMessagingOpen(proposal))
    }

    /**
     * The self-healing case [DispatchAssignmentApplicationService.tripFor]'s
     * own KDoc names: an [Assignment] that exists but has no connected
     * [com.pios.dispatch.domain.Trip] yet (a legacy row from before Trip
     * existed, or a brief race). Still treated as open -- the permissive
     * default this MVP's own tests name explicitly.
     */
    @Test
    fun `messaging is open when an assignment exists but has no Trip yet`() {
        val proposal = freshProposal()
        proposal.accept()
        assignmentRepository.save(Assignment.create(order, driver).assignment)

        assertTrue(service.isMessagingOpen(proposal))
    }

    @Test
    fun `messaging stays open through ARRIVED and IN_PROGRESS, driven by the real DispatchAssignmentApplicationService`() {
        val proposal = freshProposal()
        proposal.accept()
        val assignment = createRealAssignment()

        dispatchAssignmentApplicationService.arriveAssignment(ArriveAssignmentCommand(assignment.id))
        assertTrue(service.isMessagingOpen(proposal))

        dispatchAssignmentApplicationService.startAssignment(StartAssignmentCommand(assignment.id))
        assertTrue(service.isMessagingOpen(proposal))
    }

    @Test
    fun `messaging closes once completeAssignment (real service, real Trip) reports COMPLETED`() {
        val proposal = freshProposal()
        proposal.accept()
        val assignment = createRealAssignment()

        dispatchAssignmentApplicationService.arriveAssignment(ArriveAssignmentCommand(assignment.id))
        dispatchAssignmentApplicationService.startAssignment(StartAssignmentCommand(assignment.id))
        dispatchAssignmentApplicationService.completeAssignment(CompleteAssignmentCommand(assignment.id))

        assertEquals(false, service.isMessagingOpen(proposal))
    }

    /**
     * The exact regression this file's own earlier version could not catch:
     * completing the real Assignment aggregate directly (bypassing
     * [DispatchAssignmentApplicationService]) never touches the connected
     * Trip, so it must **not** be mistaken for a real completion by
     * [isMessagingOpen] -- messaging must stay open, since no real ride
     * progress has actually happened. Kept precisely to guard the fix in
     * this file: reverting [ProposalMessagingApplicationService.isMessagingOpen]
     * back to reading [Assignment.status] would make this test fail.
     */
    @Test
    fun `manually completing Assignment directly, bypassing the real service, does not close messaging`() {
        val proposal = freshProposal()
        proposal.accept()
        val assignment = createRealAssignment()
        assignment.accept()
        assignment.arrive()
        assignment.start()
        assignment.complete()
        assignmentRepository.save(assignment)

        assertTrue(service.isMessagingOpen(proposal))
    }

    @Test
    fun `listMessages still returns full history after the real Trip reaches COMPLETED`() {
        val proposal = freshProposal()
        proposal.accept()
        proposalRepository.save(proposal)
        val assignment = createRealAssignment()
        service.sendMessage(proposal.id, MessageSenderRole.PASSENGER, "До поездки")

        dispatchAssignmentApplicationService.arriveAssignment(ArriveAssignmentCommand(assignment.id))
        dispatchAssignmentApplicationService.startAssignment(StartAssignmentCommand(assignment.id))
        dispatchAssignmentApplicationService.completeAssignment(CompleteAssignmentCommand(assignment.id))

        assertEquals(listOf("До поездки"), service.listMessages(proposal.id).map { it.body })
    }

    @Test
    fun `messaging is closed for a declined proposal`() {
        val proposal = freshProposal()
        proposal.decline()

        assertEquals(false, service.isMessagingOpen(proposal))
    }

    @Test
    fun `messaging is closed for a lapsed proposal`() {
        val proposal = freshProposal()
        proposal.lapse()

        assertEquals(false, service.isMessagingOpen(proposal))
    }

    @Test
    fun `messaging is closed for a withdrawn proposal`() {
        val proposal = freshProposal()
        proposal.withdraw()

        assertEquals(false, service.isMessagingOpen(proposal))
    }

    @Test
    fun `sendMessage persists the message and returns it`() {
        val proposal = freshProposal()
        proposalRepository.save(proposal)

        val sent = service.sendMessage(proposal.id, MessageSenderRole.PASSENGER, "Буду через 5 минут")

        assertEquals("Буду через 5 минут", sent.body)
        assertEquals(listOf(sent), messageRepository.findByProposal(proposal.id))
    }

    @Test
    fun `sendMessage on an unknown proposal throws ProposalNotFoundException`() {
        assertFailsWith<ProposalNotFoundException> {
            service.sendMessage(com.pios.dispatch.domain.ProposalId("does-not-exist"), MessageSenderRole.PASSENGER, "Привет")
        }
    }

    @Test
    fun `sendMessage on a closed proposal throws MessagingClosedException and saves nothing`() {
        val proposal = freshProposal()
        proposal.decline()
        proposalRepository.save(proposal)

        assertFailsWith<MessagingClosedException> {
            service.sendMessage(proposal.id, MessageSenderRole.PASSENGER, "Ещё здесь?")
        }
        assertEquals(emptyList(), messageRepository.findByProposal(proposal.id))
    }

    @Test
    fun `listMessages on an unknown proposal throws ProposalNotFoundException`() {
        assertFailsWith<ProposalNotFoundException> {
            service.listMessages(com.pios.dispatch.domain.ProposalId("does-not-exist"))
        }
    }
}

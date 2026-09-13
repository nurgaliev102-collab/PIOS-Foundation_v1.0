package com.pios.dispatch.application

import com.pios.dispatch.domain.Assignment
import com.pios.dispatch.domain.MessageSenderRole
import com.pios.dispatch.domain.Proposal
import com.pios.dispatch.domain.ProposalId
import com.pios.dispatch.domain.ProposalMessage
import com.pios.dispatch.domain.ProposalStatus
import com.pios.dispatch.domain.Trip
import com.pios.dispatch.domain.TripStatus
import org.springframework.stereotype.Service

/**
 * Application-layer coordination for Minimal In-Ride Messaging (Product
 * Cycle) — sequences [ProposalMessage.send] against the two aggregates
 * whose combined state decides whether messaging is still permitted right
 * now ([isMessagingOpen]), mirroring [ProposalAssignmentOrchestrationService]'s
 * own reason for existing as an Application Service rather than living
 * inside either aggregate: neither [Proposal] nor [Assignment] should know
 * about the other (APPLICATION_ARCHITECTURE.md Section 2's own "no cross-
 * aggregate reference" discipline), so a decision that genuinely depends
 * on both belongs one layer up.
 *
 * ## Lifecycle: which statuses permit messaging
 *
 * OPEN → PRICE_PROPOSED → ACCEPTED → ARRIVED → IN_PROGRESS → COMPLETED
 * (Product Cycle's own stated lifecycle) maps onto this module's two real
 * aggregates as: OPEN/PRICE_PROPOSED/ACCEPTED are [Proposal]'s own status
 * values; ARRIVED/IN_PROGRESS/COMPLETED are [Assignment]'s own, reached
 * only once a [Proposal] has already settled at `ACCEPTED` (its own status
 * never advances further — see [ProposalMessage]'s own KDoc). [isMessagingOpen]
 * therefore checks:
 *
 * - [ProposalStatus.OPEN] / [ProposalStatus.PRICE_PROPOSED] → open. A
 *   passenger may message "после создания заказа" — before the driver has
 *   even named a price, exactly this cycle's own requirement, not gated
 *   behind acceptance.
 * - [ProposalStatus.ACCEPTED] → open **unless** the [Trip] connected to
 *   this same proposal's own [Assignment] already reached
 *   [TripStatus.COMPLETED] — **not** a read of [Assignment.status].
 *   Corrected (live HTTP E2E finding, 2026-09-13): [Assignment.status] is
 *   frozen, read-only pre-Trip history (see
 *   [DispatchAssignmentApplicationService]'s own "Ride-progress
 *   convergence onto Trip" KDoc, ADR-063/Task 12) — `arrive`/`start`/
 *   `complete` transition [Trip] exclusively, never [Assignment] itself,
 *   so a check against [Assignment.status] can never observe a real
 *   completion at all. [Trip] is therefore the only correct source of
 *   truth here, reached the same way [AssignmentController]'s own
 *   response projection already does: [AssignmentRepository.findByOrder]
 *   to find this order's [Assignment](s), then
 *   [TripRepository.findByAssignmentId] for each. No assignment, or an
 *   assignment with no [Trip] yet (a brief race just after acceptance —
 *   [DispatchAssignmentApplicationService]'s own [Trip] is normally
 *   created in the same call as the [Assignment], but nothing here
 *   assumes that), is treated as still open, the same permissive default
 *   this MVP's own tests name explicitly — a passenger/driver mid-ride has
 *   every reason to still coordinate.
 * - [ProposalStatus.DECLINED] / [ProposalStatus.LAPSED] / [ProposalStatus.WITHDRAWN]
 *   → closed. None of these three ever produced a real ride — nothing left
 *   to coordinate, mirroring every other terminal-status convention this
 *   codebase already applies elsewhere (e.g. `RideRequest.tsx`'s own
 *   [handleCancelOrder] gating).
 *
 * ## After COMPLETED
 *
 * Once the connected [Trip] reaches [TripStatus.COMPLETED],
 * [isMessagingOpen] returns `false` — no new message may be sent
 * ([sendMessage] throws [MessagingClosedException], mapped by
 * `ProposalMessageController` to HTTP 409, the same status code this
 * module already uses for every other "wrong state" rejection). Existing
 * messages are **not** deleted, hidden, or otherwise made unreadable —
 * [listMessages] never checks [isMessagingOpen] at all, since a completed
 * ride's own conversation remains a legitimate record of it, exactly like
 * [Proposal.statedPrice] itself stays visible forever after acceptance.
 */
@Service
class ProposalMessagingApplicationService(
    private val proposalRepository: ProposalRepository,
    private val assignmentRepository: AssignmentRepository,
    private val tripRepository: TripRepository,
    private val proposalMessageRepository: ProposalMessageRepository
) {

    fun listMessages(proposalId: ProposalId): List<ProposalMessage> {
        proposalRepository.findById(proposalId) ?: throw ProposalNotFoundException(proposalId)
        return proposalMessageRepository.findByProposal(proposalId)
    }

    /**
     * Sends [body] as [senderRole] on [proposalId]. Throws
     * [ProposalNotFoundException] (404) if the proposal does not exist, or
     * [MessagingClosedException] (409) if [isMessagingOpen] says this
     * proposal's own ride is already over or never happened. [body]'s own
     * blank/length validation is [ProposalMessage.send]'s own concern, not
     * duplicated here (APPLICATION_ARCHITECTURE.md Section 2, "No Business
     * Logic Duplication") — a blank or over-length [body] surfaces as that
     * factory's own [IllegalArgumentException], mapped by the controller to
     * the same 400 every other invalid input on this module already
     * produces.
     */
    fun sendMessage(proposalId: ProposalId, senderRole: MessageSenderRole, body: String): ProposalMessage {
        val proposal = proposalRepository.findById(proposalId) ?: throw ProposalNotFoundException(proposalId)
        if (!isMessagingOpen(proposal)) {
            throw MessagingClosedException(proposalId)
        }
        val message = ProposalMessage.send(proposalId, senderRole, body)
        proposalMessageRepository.save(message)
        return message
    }

    /** See this class's own KDoc for the full lifecycle/COMPLETED decision this implements. */
    fun isMessagingOpen(proposal: Proposal): Boolean =
        when (proposal.status) {
            ProposalStatus.OPEN, ProposalStatus.PRICE_PROPOSED -> true
            ProposalStatus.ACCEPTED ->
                assignmentRepository.findByOrder(proposal.order)
                    .mapNotNull { tripRepository.findByAssignmentId(it.id) }
                    .none { it.status == TripStatus.COMPLETED }
            ProposalStatus.DECLINED, ProposalStatus.LAPSED, ProposalStatus.WITHDRAWN -> false
        }
}

/**
 * Thrown by [ProposalMessagingApplicationService.sendMessage] when
 * [ProposalMessagingApplicationService.isMessagingOpen] is false for the
 * named proposal — an application-layer error, never a persistence or
 * domain one, mirroring [ProposalNotFoundException]'s own placement and
 * reasoning exactly.
 */
class MessagingClosedException(val proposalId: ProposalId) :
    RuntimeException("Messaging is closed for proposal ${proposalId.value}")

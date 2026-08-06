package com.pios.dispatch.application

import com.pios.dispatch.domain.Proposal
import com.pios.dispatch.domain.ProposalAccepted
import com.pios.dispatch.domain.ProposalCreated
import com.pios.dispatch.domain.ProposalDeclined
import com.pios.dispatch.domain.ProposalLapsed
import com.pios.dispatch.domain.ProposalWithdrawn
import org.springframework.stereotype.Service

/**
 * Application-layer coordination for the Propose Driver, Accept Proposal,
 * Decline Proposal, Lapse Proposal, and Withdraw Proposal commands. This
 * service sequences each command into the Proposal aggregate's own
 * behavior; it does not decide the proposal or its resolution itself
 * (APPLICATION_ARCHITECTURE.md Section 2, "Domain Decides Business
 * Meaning").
 *
 * [withdrawProposal] (ADR-053, Proposal Resolution on Order Cancellation)
 * is invoked only by [OrderCancelledApplicationService], Dispatch's own
 * application-layer consumer of Order Management's `OrderCancelled` —
 * never directly by a controller, since ADR-053 authorizes no manual
 * withdraw endpoint (Part 4: "Not authorized by this ADR: a manual
 * `POST /v1/proposals/{id}/withdraw` endpoint").
 *
 * [handle] loads [ProposalRepository.findByOrder] itself, rather than
 * requiring the caller to supply the order's existing proposals as
 * [DispatchAssignmentApplicationService.handle] still does for Assignment
 * — this is a deliberate difference, not an oversight: Sprint
 * IMPLEMENTATION-002 specified this flow explicitly ("load existing
 * proposals by order" as this service's own first step), and
 * [ProposalRepository] carries [ProposalRepository.findByOrder] from its
 * introduction, unlike [AssignmentRepository], which only gained it later
 * as an additive Sprint FR-004 change after
 * [DispatchAssignmentApplicationService] was already written to accept
 * the collection as a parameter.
 *
 * ## Outbox integration deliberately not wired in
 *
 * Unlike [DispatchAssignmentApplicationService], this service does not
 * write to [OutboxRepository]. [OutboxRelay] relays every unpublished
 * record in `dispatch_outbox` to Dispatch's real exchange
 * unconditionally, by event type, with no per-event allow-list — so
 * writing a [OutboxRecord] for `OrderProposed`, `ProposalAccepted`,
 * `ProposalDeclined`, or `ProposalLapsed` would make them real,
 * externally-consumable published events the moment this service runs in
 * a wired application. As of Sprint 1 (Foundation Stabilization),
 * EVENT_CATALOG.md Section 6 catalogues these four as Dispatch's own
 * domain events (bringing the catalog into agreement with the
 * implementation), but explicitly *not* as business/cross-domain events
 * under ADR-003 — no other domain relies on any of them, and
 * INTERFACE_CONTRACTS.md (Sections 5, 8) still ratifies only
 * `OrderAssigned`/`AssignmentAccepted` as Dispatch-outbound contracts,
 * naming no consumer for any Proposal event. ADR-035 itself left the
 * Proposal aggregate's "internal model" undesigned, and Domain Design
 * (Sprint DOMAIN-001) defined these four events strictly in
 * business/domain language, without deciding whether any of them cross
 * Dispatch's own boundary.
 *
 * Wiring an outbox write here would therefore silently create a new
 * external contract nobody has ratified — exactly the kind of
 * architectural change Sprint IMPLEMENTATION-002's own instructions say
 * to stop on rather than invent. This is that stop: [handle],
 * [acceptProposal], [declineProposal], and [lapseProposal] persist the
 * aggregate only. Wiring outbox publication for some or all of these
 * events is a decision for a future sprint, once EVENT_CATALOG.md and
 * INTERFACE_CONTRACTS.md have been updated to ratify it (or have
 * explicitly ratified that these events remain internal-only).
 *
 * ## Transaction boundary (Milestone 14A)
 *
 * [handle], [acceptProposal], [declineProposal], and [lapseProposal] each
 * run inside [transactionRunner], mirroring
 * [DispatchAssignmentApplicationService]'s own established pattern exactly
 * (ADR-032): the existing-proposals read and the resulting save happen
 * inside the same boundary. [transactionRunner] defaults to
 * [NoOpTransactionRunner] for the same reason [DispatchAssignmentApplicationService]'s
 * own defaults do — existing tests exercising only Proposal lifecycle
 * behavior continue to work unchanged; a real, Spring-wired instance
 * always receives [com.pios.dispatch.persistence.SpringTransactionRunner]
 * instead.
 */
@Service
class ProposalApplicationService(
    private val proposalRepository: ProposalRepository,
    private val transactionRunner: TransactionRunner = NoOpTransactionRunner
) {

    fun handle(command: ProposeDriverCommand): ProposalCreated = transactionRunner.run {
        val existingProposals = proposalRepository.findByOrder(command.order)
        val created = Proposal.propose(
            order = command.order,
            driver = command.driver,
            existingProposals = existingProposals
        )
        proposalRepository.save(created.proposal)
        created
    }

    /**
     * Confirms the given [proposal], per the Accept Proposal command.
     * [proposal] must be the one referenced by [command] — the caller is
     * responsible for finding it, mirroring
     * [DispatchAssignmentApplicationService.acceptAssignment]'s own
     * instance-supplied overload.
     */
    fun acceptProposal(proposal: Proposal, command: AcceptProposalCommand): ProposalAccepted = transactionRunner.run {
        acceptProposalWithinCallerTransaction(proposal, command)
    }

    /**
     * Identical to [acceptProposal] (the instance-supplied overload) except
     * that it does not open its own [transactionRunner] boundary — for
     * [ProposalAssignmentOrchestrationService] to call from inside the one
     * shared transaction it now owns for Accept Proposal (ADR-036), so this
     * write and the Assignment's own write commit or roll back together.
     * Calling this from outside an already-open transaction would leave the
     * Proposal write uncommitted-atomic only by accident; every other
     * caller must keep using [acceptProposal] instead.
     */
    fun acceptProposalWithinCallerTransaction(proposal: Proposal, command: AcceptProposalCommand): ProposalAccepted {
        require(proposal.id == command.proposalId) {
            "Proposal ${proposal.id.value} does not match command target ${command.proposalId.value}"
        }
        val event = proposal.accept(command.statedPrice)
        proposalRepository.save(proposal)
        return event
    }

    /**
     * Confirms the proposal referenced by [command] by first restoring it
     * through [proposalRepository]. Throws [ProposalNotFoundException] if
     * no Proposal identified by [AcceptProposalCommand.proposalId] has
     * been saved.
     */
    fun acceptProposal(command: AcceptProposalCommand): ProposalAccepted = transactionRunner.run {
        val proposal = proposalRepository.findById(command.proposalId)
            ?: throw ProposalNotFoundException(command.proposalId)
        acceptProposal(proposal, command)
    }

    /**
     * Declines the given [proposal], per the Decline Proposal command.
     * [proposal] must be the one referenced by [command].
     */
    fun declineProposal(proposal: Proposal, command: DeclineProposalCommand): ProposalDeclined = transactionRunner.run {
        require(proposal.id == command.proposalId) {
            "Proposal ${proposal.id.value} does not match command target ${command.proposalId.value}"
        }
        val event = proposal.decline()
        proposalRepository.save(proposal)
        event
    }

    /**
     * Declines the proposal referenced by [command] by first restoring it
     * through [proposalRepository]. Throws [ProposalNotFoundException] if
     * no Proposal identified by [DeclineProposalCommand.proposalId] has
     * been saved.
     */
    fun declineProposal(command: DeclineProposalCommand): ProposalDeclined = transactionRunner.run {
        val proposal = proposalRepository.findById(command.proposalId)
            ?: throw ProposalNotFoundException(command.proposalId)
        declineProposal(proposal, command)
    }

    /**
     * Lapses the given [proposal], per the Lapse Proposal command.
     * [proposal] must be the one referenced by [command].
     */
    fun lapseProposal(proposal: Proposal, command: LapseProposalCommand): ProposalLapsed = transactionRunner.run {
        require(proposal.id == command.proposalId) {
            "Proposal ${proposal.id.value} does not match command target ${command.proposalId.value}"
        }
        val event = proposal.lapse()
        proposalRepository.save(proposal)
        event
    }

    /**
     * Lapses the proposal referenced by [command] by first restoring it
     * through [proposalRepository]. Throws [ProposalNotFoundException] if
     * no Proposal identified by [LapseProposalCommand.proposalId] has been
     * saved.
     */
    fun lapseProposal(command: LapseProposalCommand): ProposalLapsed = transactionRunner.run {
        val proposal = proposalRepository.findById(command.proposalId)
            ?: throw ProposalNotFoundException(command.proposalId)
        lapseProposal(proposal, command)
    }

    /**
     * Withdraws the given [proposal], per the Withdraw Proposal command
     * (ADR-053). [proposal] must be the one referenced by [command].
     */
    fun withdrawProposal(proposal: Proposal, command: WithdrawProposalCommand): ProposalWithdrawn = transactionRunner.run {
        require(proposal.id == command.proposalId) {
            "Proposal ${proposal.id.value} does not match command target ${command.proposalId.value}"
        }
        val event = proposal.withdraw()
        proposalRepository.save(proposal)
        event
    }

    /**
     * Withdraws the proposal referenced by [command] by first restoring it
     * through [proposalRepository]. Throws [ProposalNotFoundException] if
     * no Proposal identified by [WithdrawProposalCommand.proposalId] has
     * been saved.
     */
    fun withdrawProposal(command: WithdrawProposalCommand): ProposalWithdrawn = transactionRunner.run {
        val proposal = proposalRepository.findById(command.proposalId)
            ?: throw ProposalNotFoundException(command.proposalId)
        withdrawProposal(proposal, command)
    }
}

package com.pios.dispatch.application

import com.pios.dispatch.domain.AssignmentCreated
import com.pios.dispatch.domain.Proposal
import org.springframework.stereotype.Service

/**
 * Orchestrates the transition from an accepted Proposal to a created
 * Assignment (Sprint IMPLEMENTATION-004, Proposal → Assignment
 * Orchestration; ADR-035).
 *
 * ## Why an Application Service, not a Domain Event Handler or a Process
 * Manager
 *
 * Sprint IMPLEMENTATION-004 required evaluating exactly three mechanisms
 * before writing any code:
 *
 * - **A Domain Event Handler** (a listener reacting to [ProposalAccepted]
 *   wherever it is raised) would add an in-process publish/subscribe
 *   indirection for a single, mandatory, synchronous reaction that has
 *   exactly one subscriber today. Nothing in this module currently
 *   raises [ProposalAccepted] anywhere but this one call site
 *   ([ProposalApplicationService.acceptProposal], invoked here), and
 *   nothing else in the ratified design needs to react to it
 *   independently. An event-handler layer would be built entirely for a
 *   decoupling need that does not exist yet — the same premature
 *   abstraction this project's own engineering discipline rejects.
 * - **A Process Manager** (a persistent, stateful coordinator tracking a
 *   Proposal's journey across multiple transactions, with its own
 *   recorded state, retries, timeouts, or compensating actions) solves a
 *   problem this flow does not have: both aggregates are owned by this
 *   same module, in the same process, and the transition is a single,
 *   immediate, synchronous step with no waiting, no external system
 *   crossing a boundary, and no compensation path the ratified design
 *   calls for. Introducing a Process Manager's own persisted state here
 *   would be new architecture invented to solve a problem that does not
 *   exist, which Sprint IMPLEMENTATION-004 explicitly asked to reject if
 *   unnecessarily complex.
 * - **Application Service orchestration** — a plain, stateless service
 *   coordinating two already-existing application services in one
 *   synchronous call — is the simplest mechanism that satisfies every
 *   required behavior: validate the Proposal (reusing
 *   [ProposalApplicationService.acceptProposal]'s own business logic,
 *   via [ProposalApplicationService.acceptProposalWithinCallerTransaction]
 *   since ADR-036), ensure no Assignment already exists and create one
 *   (reusing [DispatchAssignmentApplicationService.handle]'s own business
 *   logic, via [DispatchAssignmentApplicationService.handleWithinCallerTransaction]
 *   since ADR-036, which itself reuses
 *   [com.pios.dispatch.domain.Assignment.create]'s own invariant
 *   unchanged). No new domain concept, no new persisted state, no new
 *   ADR — this is a coordination detail of the application layer
 *   (APPLICATION_ARCHITECTURE.md Section 2), not an architectural
 *   decision requiring one.
 *
 * ## What this service does not do
 *
 * It does not decide anything: [Proposal.accept]'s own invariant decides
 * whether acceptance is valid, and
 * [com.pios.dispatch.domain.Assignment.create]'s own invariant decides
 * whether a new Assignment may be created for this order. This service
 * only sequences the two, exactly once, in the order the business flow
 * requires — mirroring [DispatchAssignmentApplicationService]'s and
 * [ProposalApplicationService]'s own "Domain Decides Business Meaning"
 * discipline.
 *
 * ## Shared transaction, single Unit of Work owner (ADR-036)
 *
 * [acceptProposal] is this class's own [TransactionRunner] boundary: the
 * Proposal read, the Proposal write, the Assignment's existing-assignments
 * read, the Assignment write, and the Assignment's own outbox write all
 * happen inside the one transaction [transactionRunner] opens here. Either
 * all of it commits or none of it does. This is a deliberate, narrow
 * exception to "one transaction per aggregate" — see ADR-036's own "Why
 * This Is Consistent with DDD" section for the three conditions that
 * justify it — not a general license for cross-aggregate transactions
 * elsewhere.
 *
 * To make this possible, [proposalApplicationService] and
 * [dispatchAssignmentApplicationService] each expose a
 * `*WithinCallerTransaction` method that performs the same write as their
 * own public, self-transacting method, without opening a transaction of
 * its own — this class calls those, not the public overloads, so the two
 * aggregates' writes participate in exactly one physical transaction
 * instead of each independently opening (or implicitly joining) one.
 *
 * This also closes the TOCTOU window disclosed in prior work: the initial
 * [proposalRepository.findById] read now happens inside the same
 * transaction as every write that follows it, instead of before the
 * transaction that follows opens.
 *
 * A consequence worth naming explicitly (ADR-036, Operational
 * Consequences): if [dispatchAssignmentApplicationService]'s step fails —
 * whether because [com.pios.dispatch.domain.Assignment.create]'s own
 * invariant rejects a duplicate Assignment, or because of a transient
 * infrastructure failure — the whole transaction rolls back, including the
 * Proposal's own ACCEPTED write. The Proposal is left exactly as it was
 * before this call (typically OPEN), not ACCEPTED-with-no-Assignment. This
 * replaces the previously disclosed "Proposal ACCEPTED with no
 * corresponding Assignment" failure window entirely, by construction.
 */
@Service
class ProposalAssignmentOrchestrationService(
    private val proposalRepository: ProposalRepository,
    private val proposalApplicationService: ProposalApplicationService,
    private val dispatchAssignmentApplicationService: DispatchAssignmentApplicationService,
    private val transactionRunner: TransactionRunner = NoOpTransactionRunner
) {

    /**
     * Accepts the Proposal identified by [command], then creates the
     * Assignment it precedes, inside one shared transaction (ADR-036; see
     * this class's own KDoc). Throws [ProposalNotFoundException] if no such
     * Proposal exists; propagates [Proposal.accept]'s own
     * [IllegalStateException] if it is not [com.pios.dispatch.domain.ProposalStatus.OPEN],
     * and [com.pios.dispatch.domain.Assignment.create]'s own
     * [IllegalStateException] if the order already has an Assignment — in
     * both failure cases, the whole transaction rolls back, so the
     * Proposal's ACCEPTED write is rolled back along with it rather than
     * left committed on its own.
     */
    fun acceptProposal(command: AcceptProposalCommand): ProposalAcceptanceOutcome = transactionRunner.run {
        val proposal = proposalRepository.findById(command.proposalId)
            ?: throw ProposalNotFoundException(command.proposalId)

        proposalApplicationService.acceptProposalWithinCallerTransaction(proposal, command)

        val assignmentCreated = dispatchAssignmentApplicationService.handleWithinCallerTransaction(
            AssignOrderCommand(proposal.order, proposal.driver, isTest = proposal.isTest)
        )

        ProposalAcceptanceOutcome(proposal, assignmentCreated)
    }

    /**
     * Confirms the price already stated on the Proposal identified by
     * [command] (the passenger's own agreeing act, Product Owner
     * instruction 2026-09-05), then creates the Assignment it precedes,
     * inside one shared transaction -- identical in every respect to
     * [acceptProposal] except which Proposal-aggregate transition it
     * calls, since [com.pios.dispatch.domain.Proposal.confirmPrice] and
     * [com.pios.dispatch.domain.Proposal.accept] both resolve to the same
     * [com.pios.dispatch.domain.ProposalAccepted] outcome this
     * orchestration reacts to.
     */
    fun confirmPrice(command: ConfirmPriceCommand): ProposalAcceptanceOutcome = transactionRunner.run {
        val proposal = proposalRepository.findById(command.proposalId)
            ?: throw ProposalNotFoundException(command.proposalId)

        proposalApplicationService.confirmPriceWithinCallerTransaction(proposal, command)

        val assignmentCreated = dispatchAssignmentApplicationService.handleWithinCallerTransaction(
            AssignOrderCommand(proposal.order, proposal.driver, isTest = proposal.isTest)
        )

        ProposalAcceptanceOutcome(proposal, assignmentCreated)
    }
}

/**
 * The result of accepting a Proposal through
 * [ProposalAssignmentOrchestrationService]: the now-ACCEPTED [proposal]
 * and the [AssignmentCreated] outcome of the Assignment it precedes.
 */
data class ProposalAcceptanceOutcome(
    val proposal: Proposal,
    val assignmentCreated: AssignmentCreated
)

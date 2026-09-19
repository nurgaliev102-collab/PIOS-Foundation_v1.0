package com.pios.dispatch.application

import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.domain.Proposal
import com.pios.dispatch.domain.ProposalAccepted
import com.pios.dispatch.domain.ProposalCreated
import com.pios.dispatch.domain.ProposalDeclined
import com.pios.dispatch.domain.ProposalLapsed
import com.pios.dispatch.domain.ProposalPriceProposed
import com.pios.dispatch.domain.ProposalWithdrawn
import org.springframework.stereotype.Service
import java.time.Instant

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
 *
 * ## Availability gate on [handle] (pilot-readiness fix)
 *
 * [handle] now rejects proposing a driver who is not currently `AVAILABLE`,
 * read from [driverAvailabilityRepository] — Dispatch's own local,
 * event-sourced projection of Driver Management's `DriverAvailabilityChanged`
 * (see [DriverAvailabilityRepository]'s own KDoc). This closes a real gap:
 * before this fix, [handle] created a proposal for whatever driver a caller
 * named, with no availability check at all. The check fails closed,
 * matching this codebase's existing convention for a check with no
 * confirmed positive (`OwnerCredentialGate.isConfigured()` does the same
 * for a different concern): no projection record yet for a given driver
 * (e.g. they have never toggled availability since this event pipeline
 * started) is treated as not available, never as a bypass. Rejection
 * surfaces as [IllegalStateException] — the exact exception type
 * [Proposal.propose]'s own duplicate-open-proposal check already throws,
 * deliberately reused rather than introducing a new one, since
 * `ProposalController.createProposal` already maps [IllegalStateException]
 * to HTTP 409 and this rejection belongs in the same "conflicting proposal
 * request" family as that one.
 *
 * [driverAvailabilityRepository] defaults to `null`, not to a permissive
 * always-available fake: the many existing tests of this service's other
 * commands (decline/lapse/withdraw/accept, the Proposal→Assignment
 * orchestration, the Order-cancellation consumer) construct
 * [ProposalApplicationService] directly with no interest in availability at
 * all, and forcing every one of them to wire an availability double would
 * be an unrelated, out-of-scope change to each of them. A `null` repository
 * simply skips the check entirely, identical to this service's behavior
 * before this fix — safe here because Spring's real, production wiring
 * always supplies the real
 * [com.pios.dispatch.persistence.PostgreSQLDriverAvailabilityRepository]
 * bean by type regardless of this default (Kotlin default constructor
 * values only apply when Spring cannot resolve a bean of that type), so
 * production behavior is unaffected by this default existing.
 *
 * ## Driver Web Push (ADR-083, D-10)
 *
 * [driverPushNotifier] fires N1 (a new `OPEN` proposal) at the end of
 * [handle], and N2 (`PRICE_PROPOSED -> ACCEPTED`) at the end of
 * [confirmPriceWithinCallerTransaction] -- both calls just register the
 * notifier's own after-commit hook; the actual Spring transaction
 * synchronization and the Web Push send itself happen inside
 * [com.pios.dispatch.persistence.WebPushDriverPushNotifier], never here.
 * Deliberately **not** called from [acceptProposalWithinCallerTransaction]
 * (`OPEN -> ACCEPTED`, `Proposal.accept()`) -- ADR-083 names this
 * distinction as the single most important trigger boundary in the whole
 * design, and no other method in this class may call [driverPushNotifier]
 * at all. Defaults to [NoOpDriverPushNotifier], mirroring every other
 * optional collaborator in this class, so every existing direct-construction
 * test/call site continues to compile and behave unchanged.
 */
@Service
class ProposalApplicationService(
    private val proposalRepository: ProposalRepository,
    private val transactionRunner: TransactionRunner = NoOpTransactionRunner,
    private val driverAvailabilityRepository: DriverAvailabilityRepository? = null,
    private val dispatchRequestRepository: DispatchRequestRepository? = null,
    private val orderGuard: OrderGuard = NoOpOrderGuard,
    private val driverPushNotifier: DriverPushNotifier = NoOpDriverPushNotifier
) {

    fun handle(command: ProposeDriverCommand): ProposalCreated = transactionRunner.run {
        orderGuard.lock(command.order)
        val routingState: DispatchRequestState? = dispatchRequestRepository
            ?.findForUpdate(command.order.orderId)?.state
        check(routingState != DispatchRequestState.CANCELLED && routingState != DispatchRequestState.UNFULFILLED) {
            "Order ${command.order.orderId} is no longer eligible for a proposal"
        }
        if (driverAvailabilityRepository != null) {
            val record = driverAvailabilityRepository.findByDriverReference(command.driver)
            // An advance request to a named driver can be considered while
            // that driver is off-line now; it is not an immediate pickup.
            // A known driver projection and test/real match remain mandatory.
            val advanceRequest = command.requestedPickupAt?.isAfter(Instant.now()) == true
            check(record != null && (record.available || advanceRequest)) {
                "Driver ${command.driver.driverId} is not available"
            }
            check(record.isTest == command.isTest) {
                "Driver ${command.driver.driverId} is not eligible for this order population"
            }
        }
        val existingProposals = proposalRepository.findByOrder(command.order)
        val created = Proposal.propose(
            order = command.order,
            driver = command.driver,
            existingProposals = existingProposals,
            isTest = command.isTest,
            passengerReference = command.passengerReference,
            viaTrustedFallback = command.viaTrustedFallback
        )
        proposalRepository.save(created.proposal)
        driverPushNotifier.offerCreated(created.proposal.id, created.proposal.driver)
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
        orderGuard.lock(proposal.order)
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
        val event = proposal.accept(command.statedPrice, command.statedEtaMinutes)
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
        orderGuard.lock(proposal.order)
        acceptProposal(proposal, command)
    }

    /**
     * States a price for the given [proposal], per the Propose Price
     * command (Product Owner instruction, 2026-09-05). [proposal] must be
     * the one referenced by [command] — the caller is responsible for
     * finding it, mirroring [acceptProposal]'s own instance-supplied
     * overload.
     */
    fun proposePrice(proposal: Proposal, command: ProposePriceCommand): ProposalPriceProposed = transactionRunner.run {
        orderGuard.lock(proposal.order)
        check(proposalRepository.findById(proposal.id)?.status == proposal.status) { "Proposal changed concurrently" }
        proposePriceWithinCallerTransaction(proposal, command)
    }

    /**
     * Identical to [proposePrice] (the instance-supplied overload) except
     * that it does not open its own [transactionRunner] boundary -- for
     * symmetry with [acceptProposalWithinCallerTransaction], even though
     * today's only caller ([ProposalController.proposePrice]) does not
     * need a shared transaction (no Assignment is created at this step).
     */
    fun proposePriceWithinCallerTransaction(proposal: Proposal, command: ProposePriceCommand): ProposalPriceProposed {
        require(proposal.id == command.proposalId) {
            "Proposal ${proposal.id.value} does not match command target ${command.proposalId.value}"
        }
        val event = proposal.proposePrice(command.statedPrice, command.statedEtaMinutes)
        proposalRepository.save(proposal)
        return event
    }

    /**
     * States a price for the proposal referenced by [command] by first
     * restoring it through [proposalRepository]. Throws
     * [ProposalNotFoundException] if no Proposal identified by
     * [ProposePriceCommand.proposalId] has been saved.
     */
    fun proposePrice(command: ProposePriceCommand): ProposalPriceProposed = transactionRunner.run {
        val proposal = proposalRepository.findById(command.proposalId)
            ?: throw ProposalNotFoundException(command.proposalId)
        proposePriceWithinCallerTransaction(proposal, command)
    }

    /**
     * Confirms the price already stated on the given [proposal], per the
     * Confirm Price command -- the passenger's own agreeing act. [proposal]
     * must be the one referenced by [command].
     */
    fun confirmPrice(proposal: Proposal, command: ConfirmPriceCommand): ProposalAccepted = transactionRunner.run {
        orderGuard.lock(proposal.order)
        confirmPriceWithinCallerTransaction(proposal, command)
    }

    /**
     * Identical to [confirmPrice] (the instance-supplied overload) except
     * that it does not open its own [transactionRunner] boundary -- for
     * [ProposalAssignmentOrchestrationService] to call from inside the one
     * shared transaction it owns for Confirm Price, mirroring
     * [acceptProposalWithinCallerTransaction]'s own reasoning exactly: this
     * write and the Assignment's own write must commit or roll back
     * together.
     */
    fun confirmPriceWithinCallerTransaction(proposal: Proposal, command: ConfirmPriceCommand): ProposalAccepted {
        require(proposal.id == command.proposalId) {
            "Proposal ${proposal.id.value} does not match command target ${command.proposalId.value}"
        }
        val event = proposal.confirmPrice()
        proposalRepository.save(proposal)
        driverPushNotifier.priceConfirmed(proposal.id, proposal.driver)
        return event
    }

    /**
     * Declines the price already stated on the given [proposal], per the
     * Decline Price command -- the passenger's own refusing act.
     * [proposal] must be the one referenced by [command].
     */
    fun declinePriceProposal(proposal: Proposal, command: DeclinePriceCommand): ProposalDeclined = transactionRunner.run {
        orderGuard.lock(proposal.order)
        check(proposalRepository.findById(proposal.id)?.status == proposal.status) { "Proposal changed concurrently" }
        require(proposal.id == command.proposalId) {
            "Proposal ${proposal.id.value} does not match command target ${command.proposalId.value}"
        }
        val event = proposal.declinePriceProposal()
        proposalRepository.save(proposal)
        event
    }

    /**
     * Declines the price on the proposal referenced by [command] by first
     * restoring it through [proposalRepository]. Throws
     * [ProposalNotFoundException] if no Proposal identified by
     * [DeclinePriceCommand.proposalId] has been saved.
     */
    fun declinePriceProposal(command: DeclinePriceCommand): ProposalDeclined = transactionRunner.run {
        val proposal = proposalRepository.findById(command.proposalId)
            ?: throw ProposalNotFoundException(command.proposalId)
        declinePriceProposal(proposal, command)
    }

    /**
     * Declines the given [proposal], per the Decline Proposal command.
     * [proposal] must be the one referenced by [command].
     */
    fun declineProposal(proposal: Proposal, command: DeclineProposalCommand): ProposalDeclined = transactionRunner.run {
        orderGuard.lock(proposal.order)
        check(proposalRepository.findById(proposal.id)?.status == proposal.status) { "Proposal changed concurrently" }
        require(proposal.id == command.proposalId) {
            "Proposal ${proposal.id.value} does not match command target ${command.proposalId.value}"
        }
        val event = proposal.decline()
        proposalRepository.save(proposal)
        reopenRoutingObligation(event.orderId)
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
        orderGuard.lock(proposal.order)
        check(proposalRepository.findById(proposal.id)?.status == proposal.status) { "Proposal changed concurrently" }
        require(proposal.id == command.proposalId) {
            "Proposal ${proposal.id.value} does not match command target ${command.proposalId.value}"
        }
        val event = proposal.lapse()
        proposalRepository.save(proposal)
        reopenRoutingObligation(event.orderId)
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

    /**
     * Reopens [order]'s routing obligation from `OFFERED` back to
     * `PENDING` (ADR-078, Dispatch Recovery After Proposal Decline or
     * Lapse, Decision A) — called only from [declineProposal] (the
     * driver's own refusal, `Proposal.decline`) and [lapseProposal]
     * (`Proposal.lapse`), inside the same [transactionRunner] boundary
     * those methods already open, so the reopen and the proposal's own
     * resolution commit or roll back together (ADR-078: wiring this
     * outside that transaction would silently fail to fix the defect on a
     * rollback).
     *
     * Deliberately **not** called from [declinePriceProposal] (the
     * passenger's own price refusal — ADR-078 Decision C, preserving the
     * 2026-09-05 "no automatic reroute to another driver" instruction) or
     * from [withdrawProposal] (ADR-053's order-cancellation path, which
     * already produces a `CANCELLED` tombstone this reopen must not
     * fight).
     *
     * [dispatchRequestRepository] defaults to `null`, identical to every
     * other optional collaborator in this class — a test/caller that
     * supplies none continues to behave exactly as before this method's
     * addition, and [DispatchRequestRepository.reopenIfOffered] is itself
     * a no-op for any state other than `OFFERED`, so calling this after an
     * order has already reached `CANCELLED`/`UNFULFILLED` is safe.
     */
    private fun reopenRoutingObligation(order: OrderReference) {
        dispatchRequestRepository?.reopenIfOffered(order.orderId, Instant.now())
    }
}

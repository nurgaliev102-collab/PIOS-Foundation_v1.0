package com.pios.dispatch.api

import com.pios.dispatch.application.AcceptProposalCommand
import com.pios.dispatch.application.DeclineProposalCommand
import com.pios.dispatch.application.LapseProposalCommand
import com.pios.dispatch.application.ProposalApplicationService
import com.pios.dispatch.application.ProposalAssignmentOrchestrationService
import com.pios.dispatch.application.ProposalNotFoundException
import com.pios.dispatch.application.ProposalRepository
import com.pios.dispatch.application.ProposeDriverCommand
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.domain.Proposal
import com.pios.dispatch.domain.ProposalId
import com.pios.dispatch.domain.ProposalStatus
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Dispatch's Proposal REST entry points (Sprint IMPLEMENTATION-003,
 * Proposal Vertical Slice; ADR-004, Resource-Oriented API Style;
 * ADR-028, Production Integration Transport Decision), mirroring
 * [AssignmentController]'s own conventions exactly: no business logic
 * beyond transport-level conversion, no `@ControllerAdvice` -- each
 * outcome is mapped to an HTTP status directly in the method that
 * produces it.
 *
 * Status code mapping, consistent throughout: a blank `orderId`/`driverId`
 * or malformed `proposalId` surfaces as the domain reference type's own
 * [IllegalArgumentException], mapped to HTTP 400. A `proposalId` that does
 * not identify any known proposal surfaces as [ProposalNotFoundException],
 * mapped to HTTP 404 (mirroring [DriverController]'s own
 * [com.pios.drivermanagement.application.DriverNotFoundException]
 * convention). An order that already has an open proposal, or a
 * proposal accepted/declined/lapsed from a status that no longer permits
 * it, surfaces as the aggregate's own [IllegalStateException], mapped to
 * HTTP 409 -- the same choice [AssignmentController] already fixed for
 * its own conflicting-assignment case.
 *
 * Neither the order nor the driver referenced is verified to exist --
 * Dispatch does not own that information, exactly as
 * [AssignmentController]'s own KDoc explains for
 * [OrderReference]/[DriverReference].
 *
 * `acceptProposal` now also creates the Assignment the accepted Proposal
 * precedes (Sprint IMPLEMENTATION-004, Proposal → Assignment
 * Orchestration), delegating to
 * [ProposalAssignmentOrchestrationService.acceptProposal] -- see that
 * class's own KDoc for why an Application Service coordinates this rather
 * than a Domain Event Handler or a Process Manager, and for the two
 * disclosed limitations of this approach (no shared transaction between
 * the Proposal and Assignment writes; an already-accepted Proposal whose
 * order already has an Assignment surfaces the same 409 Assignment's own
 * invariant has always produced). The response body remains
 * [ProposalResponse] only -- the Proposal's own state -- unchanged in
 * shape from Sprint IMPLEMENTATION-003; it does not surface the created
 * Assignment's id (see that orchestration service's own KDoc; tracked as
 * remaining technical debt, not silently dropped).
 *
 * This controller still does not publish any Proposal event externally --
 * unchanged from Sprint IMPLEMENTATION-003 (see [ProposalApplicationService]'s
 * own KDoc for why outbox publication specifically is not wired in yet);
 * the orchestration added by this sprint is entirely internal, in-process.
 *
 * `declineProposal` and `lapseProposal` call the self-fetching
 * [ProposalApplicationService.declineProposal] / [ProposalApplicationService.lapseProposal]
 * overloads (Milestone 14B) rather than reading the Proposal here first --
 * the read now happens inside the same transaction boundary as the write.
 * The response is built from the path variable's own `proposalId` and the
 * returned domain event's `orderId`/`driverId`, plus the terminal status
 * the domain guarantees on success, since [ProposalDeclined]/[ProposalLapsed]
 * do not themselves carry the proposal's id or status.
 */
@RestController
@RequestMapping("/v1/proposals")
class ProposalController(
    private val proposalApplicationService: ProposalApplicationService,
    private val proposalAssignmentOrchestrationService: ProposalAssignmentOrchestrationService,
    private val proposalRepository: ProposalRepository
) {

    @PostMapping
    fun createProposal(@RequestBody request: ProposeDriverRequest): ResponseEntity<ProposalResponse> =
        try {
            val order = OrderReference(request.orderId)
            val driver = DriverReference(request.driverId)
            val created = proposalApplicationService.handle(ProposeDriverCommand(order, driver))
            ResponseEntity.status(HttpStatus.CREATED).body(created.proposal.toResponse())
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        } catch (ex: IllegalStateException) {
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        }

    @PostMapping("/{proposalId}/accept")
    fun acceptProposal(
        @PathVariable proposalId: String,
        @RequestBody(required = false) request: AcceptProposalRequest? = null
    ): ResponseEntity<ProposalResponse> =
        try {
            val id = ProposalId(proposalId)
            val outcome = proposalAssignmentOrchestrationService.acceptProposal(
                AcceptProposalCommand(id, request?.statedPrice)
            )
            ResponseEntity.ok(outcome.proposal.toResponse())
        } catch (ex: ProposalNotFoundException) {
            ResponseEntity.notFound().build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        } catch (ex: IllegalStateException) {
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        }

    @PostMapping("/{proposalId}/decline")
    fun declineProposal(@PathVariable proposalId: String): ResponseEntity<ProposalResponse> =
        try {
            val id = ProposalId(proposalId)
            val event = proposalApplicationService.declineProposal(DeclineProposalCommand(id))
            ResponseEntity.ok(
                ProposalResponse(id.value, event.orderId.orderId, event.driverId.driverId, ProposalStatus.DECLINED.name)
            )
        } catch (ex: ProposalNotFoundException) {
            ResponseEntity.notFound().build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        } catch (ex: IllegalStateException) {
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        }

    @PostMapping("/{proposalId}/lapse")
    fun lapseProposal(@PathVariable proposalId: String): ResponseEntity<ProposalResponse> =
        try {
            val id = ProposalId(proposalId)
            val event = proposalApplicationService.lapseProposal(LapseProposalCommand(id))
            ResponseEntity.ok(
                ProposalResponse(id.value, event.orderId.orderId, event.driverId.driverId, ProposalStatus.LAPSED.name)
            )
        } catch (ex: ProposalNotFoundException) {
            ResponseEntity.notFound().build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        } catch (ex: IllegalStateException) {
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        }

    @GetMapping("/{proposalId}")
    fun getProposal(@PathVariable proposalId: String): ResponseEntity<ProposalResponse> =
        try {
            val proposal = proposalRepository.findById(ProposalId(proposalId))
            if (proposal != null) ResponseEntity.ok(proposal.toResponse()) else ResponseEntity.notFound().build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }

    /**
     * List Proposals for Order (`?orderId=...`, since Sprint
     * IMPLEMENTATION-003) or List Proposals for Driver (`?driverId=...`,
     * added by Sprint IMPLEMENTATION-005 for the Driver-facing "list open
     * proposals" capability — [ProposalRepository.findByDriver] did not
     * exist before this sprint's UI needed it). Exactly one of the two
     * must be supplied; neither, or both, is a malformed request, mapped
     * to HTTP 400 like every other invalid input this controller handles.
     * Returns every proposal for that order/driver regardless of status
     * (unfiltered, same convention as [findByOrder] already had) — the
     * caller decides which ones are actionable, exactly as the Driver UI
     * itself does by only offering Accept/Decline on `OPEN` ones.
     */
    @GetMapping
    fun listProposals(
        @RequestParam(required = false) orderId: String?,
        @RequestParam(required = false) driverId: String?
    ): ResponseEntity<List<ProposalResponse>> =
        try {
            when {
                orderId != null && driverId == null ->
                    ResponseEntity.ok(proposalRepository.findByOrder(OrderReference(orderId)).map { it.toResponse() })
                driverId != null && orderId == null ->
                    ResponseEntity.ok(proposalRepository.findByDriver(DriverReference(driverId)).map { it.toResponse() })
                else -> ResponseEntity.badRequest().build()
            }
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }

    private fun Proposal.toResponse(): ProposalResponse =
        ProposalResponse(id.value, order.orderId, driver.driverId, status.name, statedPrice)
}

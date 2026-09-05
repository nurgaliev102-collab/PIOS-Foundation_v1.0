package com.pios.dispatch.api

import com.pios.dispatch.application.AcceptProposalCommand
import com.pios.dispatch.application.ConfirmPriceCommand
import com.pios.dispatch.application.DeclinePriceCommand
import com.pios.dispatch.application.DeclineProposalCommand
import com.pios.dispatch.application.LapseProposalCommand
import com.pios.dispatch.application.ProposalApplicationService
import com.pios.dispatch.application.ProposalAssignmentOrchestrationService
import com.pios.dispatch.application.ProposalNotFoundException
import com.pios.dispatch.application.ProposalRepository
import com.pios.dispatch.application.ProposeDriverCommand
import com.pios.dispatch.application.ProposePriceCommand
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
import org.springframework.web.bind.annotation.RequestHeader
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
 *
 * ## Task 21 (Proposal API Security Remediation)
 *
 * `createProposal`, `acceptProposal`, `declineProposal`, and
 * `lapseProposal` each now require an `Authorization` header, closing the
 * previously fully public, unauthenticated write surface Task 19/20
 * documented and audited. No new authentication mechanism was introduced
 * -- [sessionTokenVerifier] and [ownerCredentialGate], both already
 * constructor-injected into this class (previously used only by
 * `listProposalsForDriver`), are the only collaborators this change adds
 * to any method. See each method's own KDoc for the exact check; see
 * `docs/PIOS_TAXI_TASK_20_PROPOSAL_SECURITY_AUDIT.md` and
 * `docs/PIOS_TAXI_TASK_21_PROPOSAL_SECURITY_REMEDIATION_REPORT.md` for
 * the full audit and remediation record, including what this change
 * deliberately does not attempt to close (verifying that an authenticated
 * passenger actually owns the order they are proposing on — `Proposal`
 * carries no `passengerReference`, and closing that gap is a named,
 * separate architectural decision, not part of this remediation).
 */
@RestController
@RequestMapping("/v1/proposals")
class ProposalController(
    private val proposalApplicationService: ProposalApplicationService,
    private val proposalAssignmentOrchestrationService: ProposalAssignmentOrchestrationService,
    private val proposalRepository: ProposalRepository,
    private val sessionTokenVerifier: SessionTokenVerifier,
    private val ownerCredentialGate: OwnerCredentialGate
) {

    @PostMapping
    fun createProposal(
        @RequestBody request: ProposeDriverRequest,
        @RequestHeader("Authorization", required = false) authorization: String? = null
    ): ResponseEntity<ProposalResponse> {
        return try {
            val order = OrderReference(request.orderId)
            val driver = DriverReference(request.driverId)
            // Task 21 (Proposal API Security Remediation): anonymous
            // creation is no longer permitted -- any authenticated caller
            // (any passenger's own verified session token, `sub` alone,
            // no further check) or the owner/coordinator credential is
            // sufficient. Deliberately does NOT verify the token's `sub`
            // against the order's own passenger, and deliberately does NOT
            // restrict which `driverId` may be named -- both are Task 20's
            // own named, out-of-scope residual gap (Proposal carries no
            // `passengerReference`; naming any driver is this product's
            // own existing, legitimate behavior for both real callers).
            val authorized = sessionTokenVerifier.verify(authorization) != null || ownerCredentialGate.verify(authorization)
            if (!authorized) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            }
            val created = proposalApplicationService.handle(ProposeDriverCommand(order, driver, request.isTest))
            ResponseEntity.status(HttpStatus.CREATED).body(created.proposal.toResponse())
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        } catch (ex: IllegalStateException) {
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        } catch (ex: org.springframework.dao.DataIntegrityViolationException) {
            // Task 15C (First Refusal Contract Completion and Concurrency
            // Safety): `proposals_one_open_per_order` (V14) now enforces,
            // at the database level, the same "order already has an OPEN
            // proposal" fact `Proposal.propose`'s own in-memory check
            // already maps to 409 above -- a genuinely concurrent request
            // racing another can reach this constraint instead of that
            // check (the in-memory SELECT ran before the other request's
            // own INSERT committed). Mapped to the identical status code
            // this endpoint already documents for "an order that already
            // has an active proposal" (this class's own KDoc), so this
            // endpoint's own observable contract is unchanged by the new
            // constraint existing -- only which of two equivalent causes
            // produced it.
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        }
    }

    /**
     * Task 21 (Proposal API Security Remediation): requires a `Bearer`
     * session token verifying as the exact driver named on this proposal
     * -- mirrors `listProposalsForDriver`'s own already-established
     * `?driverId=` pattern (ADR-060 Decision 4/Answer 2) exactly, the
     * first precedent for this same identity check inside this same
     * controller. The proposal is read once, directly
     * ([proposalRepository.findById]), before delegating to the existing
     * self-fetching orchestration -- 404 if it does not exist, checked
     * *before* the 403 comparison so a fully anonymous/unauthenticated
     * caller never learns whether a given id exists at all (401, before
     * any lookup), and an authenticated-but-wrong-driver caller sees the
     * same 404/403 split this controller already gives every other
     * not-found/conflict case. Does not touch
     * [ProposalAssignmentOrchestrationService.acceptProposal]'s own
     * internal self-fetch -- that re-read, moments later in the same
     * request, is redundant but harmless, and keeps this change entirely
     * inside the transport boundary, exactly as this controller's own
     * KDoc already states its own scope to be ("no business logic beyond
     * transport-level conversion").
     */
    @PostMapping("/{proposalId}/accept")
    fun acceptProposal(
        @PathVariable proposalId: String,
        @RequestBody(required = false) request: AcceptProposalRequest? = null,
        @RequestHeader("Authorization", required = false) authorization: String? = null
    ): ResponseEntity<ProposalResponse> {
        return try {
            val id = ProposalId(proposalId)
            val verified = sessionTokenVerifier.verify(authorization)
                ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            val proposal = proposalRepository.findById(id)
                ?: return ResponseEntity.notFound().build()
            if (verified.drv != proposal.driver.driverId) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
            }
            val outcome = proposalAssignmentOrchestrationService.acceptProposal(
                AcceptProposalCommand(id, request?.statedPrice, request?.statedEtaMinutes)
            )
            ResponseEntity.ok(outcome.proposal.toResponse())
        } catch (ex: ProposalNotFoundException) {
            ResponseEntity.notFound().build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        } catch (ex: IllegalStateException) {
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        }
    }

    /**
     * States a price for this proposal (Product Owner instruction,
     * 2026-09-05: a driver must name a price before a ride is confirmed,
     * and the passenger must separately agree to it) -- this is now the
     * real product flow's own entry point for a driver taking on a ride;
     * `DriverHome.tsx`'s own "Принять" action calls this, not
     * [acceptProposal], which stays exactly as it was (Coordinator's own
     * deprecated manual-override path). Identical identity check to
     * [acceptProposal]'s own — see that method's own KDoc: 401 before any
     * lookup, then 404, then 403 on a wrong-driver token.
     *
     * Does not create an Assignment -- only [confirmPrice], the
     * passenger's own separate act, does that.
     */
    @PostMapping("/{proposalId}/propose-price")
    fun proposePrice(
        @PathVariable proposalId: String,
        @RequestBody request: ProposePriceRequest,
        @RequestHeader("Authorization", required = false) authorization: String? = null
    ): ResponseEntity<ProposalResponse> {
        return try {
            val id = ProposalId(proposalId)
            val verified = sessionTokenVerifier.verify(authorization)
                ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            val proposal = proposalRepository.findById(id)
                ?: return ResponseEntity.notFound().build()
            if (verified.drv != proposal.driver.driverId) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
            }
            val event = proposalApplicationService.proposePrice(
                proposal,
                ProposePriceCommand(id, request.statedPrice, request.statedEtaMinutes)
            )
            ResponseEntity.ok(
                ProposalResponse(
                    id.value,
                    event.orderId.orderId,
                    event.driverId.driverId,
                    ProposalStatus.PRICE_PROPOSED.name,
                    proposal.statedPrice,
                    proposal.createdAt?.toString(),
                    proposal.respondedAt?.toString(),
                    proposal.statedEtaMinutes,
                    proposal.isTest
                )
            )
        } catch (ex: ProposalNotFoundException) {
            ResponseEntity.notFound().build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        } catch (ex: IllegalStateException) {
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        }
    }

    /**
     * Confirms the price already stated on this proposal ([proposePrice])
     * -- the passenger's own agreeing act (Product Owner instruction,
     * 2026-09-05) -- then creates the Assignment it precedes, via
     * [ProposalAssignmentOrchestrationService.confirmPrice]. Same
     * authorization bar as [createProposal]: any authenticated caller
     * (a verified session token, `sub` alone, or the owner/coordinator
     * credential) is sufficient -- deliberately does not verify the
     * caller is the specific passenger who placed this order, the exact
     * same named, out-of-scope residual gap [createProposal]'s own KDoc
     * already discloses (`Proposal` carries no `passengerReference`).
     */
    @PostMapping("/{proposalId}/confirm-price")
    fun confirmPrice(
        @PathVariable proposalId: String,
        @RequestHeader("Authorization", required = false) authorization: String? = null
    ): ResponseEntity<ProposalResponse> {
        return try {
            val id = ProposalId(proposalId)
            val authorized = sessionTokenVerifier.verify(authorization) != null || ownerCredentialGate.verify(authorization)
            if (!authorized) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            }
            val outcome = proposalAssignmentOrchestrationService.confirmPrice(ConfirmPriceCommand(id))
            ResponseEntity.ok(outcome.proposal.toResponse())
        } catch (ex: ProposalNotFoundException) {
            ResponseEntity.notFound().build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        } catch (ex: IllegalStateException) {
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        }
    }

    /**
     * Declines the price already stated on this proposal ([proposePrice])
     * -- the passenger's own refusing act (Product Owner instruction,
     * 2026-09-05: the request simply closes, no renegotiation, no
     * automatic reroute to another driver). Same authorization bar as
     * [confirmPrice]'s own — see that method's own KDoc.
     */
    @PostMapping("/{proposalId}/decline-price")
    fun declinePrice(
        @PathVariable proposalId: String,
        @RequestHeader("Authorization", required = false) authorization: String? = null
    ): ResponseEntity<ProposalResponse> {
        return try {
            val id = ProposalId(proposalId)
            val authorized = sessionTokenVerifier.verify(authorization) != null || ownerCredentialGate.verify(authorization)
            if (!authorized) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            }
            val event = proposalApplicationService.declinePriceProposal(DeclinePriceCommand(id))
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
    }

    /** Task 21: identical identity check to [acceptProposal]'s own — see that method's own KDoc. */
    @PostMapping("/{proposalId}/decline")
    fun declineProposal(
        @PathVariable proposalId: String,
        @RequestHeader("Authorization", required = false) authorization: String? = null
    ): ResponseEntity<ProposalResponse> {
        return try {
            val id = ProposalId(proposalId)
            val verified = sessionTokenVerifier.verify(authorization)
                ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            val proposal = proposalRepository.findById(id)
                ?: return ResponseEntity.notFound().build()
            if (verified.drv != proposal.driver.driverId) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
            }
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
    }

    /**
     * Task 21: restricted to the owner/coordinator credential only --
     * unlike [acceptProposal]/[declineProposal], no driver or passenger
     * has a legitimate reason to call this endpoint directly.
     * [com.pios.dispatch.application.ProposalLapseScheduler] (the real,
     * periodic production trigger for this transition) calls
     * [ProposalApplicationService.lapseProposal] directly, in-process --
     * never through this HTTP endpoint, never through this
     * `Authorization` check -- so it is entirely unaffected by this
     * change.
     */
    @PostMapping("/{proposalId}/lapse")
    fun lapseProposal(
        @PathVariable proposalId: String,
        @RequestHeader("Authorization", required = false) authorization: String? = null
    ): ResponseEntity<ProposalResponse> {
        return try {
            val id = ProposalId(proposalId)
            if (!ownerCredentialGate.verify(authorization)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            }
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
     *
     * ## Authorization on `?driverId=` only (ADR-060 Decision 4)
     *
     * `GET /v1/drivers` → `?driverId=` used to let any anonymous caller
     * enumerate every proposal (and, through it, every order id) for any
     * driver they named — the enumeration path that made Order
     * Management's own `GET /v1/orders` vulnerability exploitable even
     * without calling it directly. `?driverId=` now requires either a
     * `Bearer` session token whose own `drv` equals the named driver (401
     * if the token does not verify at all, 403 if it verifies but names a
     * different driver, including a passenger-only token with `drv ==
     * null`), or a valid `Authorization: Basic` owner credential naming
     * any driver — the owner already reads this unauthenticated today
     * (`OwnerControlCenter/todayData.ts`'s own event-feed fan-out) and
     * gains no new capability, mirroring ADR-060 Decision 5's identical
     * reasoning for `GET /v1/orders`.
     *
     * `?orderId=` is deliberately left exactly as unauthenticated as
     * before (ADR-060 Decision 4: it is an enumeration *sink*, not a
     * *source* — locking it needs a `passengerReference` on `Proposal`,
     * not authorized by this change).
     */
    @GetMapping
    fun listProposals(
        @RequestHeader("Authorization", required = false) authorization: String? = null,
        @RequestParam(required = false) orderId: String?,
        @RequestParam(required = false) driverId: String?
    ): ResponseEntity<List<ProposalResponse>> =
        try {
            when {
                orderId != null && driverId == null ->
                    ResponseEntity.ok(proposalRepository.findByOrder(OrderReference(orderId)).map { it.toResponse() })
                driverId != null && orderId == null -> listProposalsForDriver(authorization, driverId)
                else -> ResponseEntity.badRequest().build()
            }
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }

    private fun listProposalsForDriver(authorization: String?, driverId: String): ResponseEntity<List<ProposalResponse>> {
        if (authorization != null && authorization.startsWith("Basic ")) {
            if (!ownerCredentialGate.verify(authorization)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            }
            return ResponseEntity.ok(proposalRepository.findByDriver(DriverReference(driverId)).map { it.toResponse() })
        }
        val verified = sessionTokenVerifier.verify(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (verified.drv != driverId) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        return ResponseEntity.ok(proposalRepository.findByDriver(DriverReference(driverId)).map { it.toResponse() })
    }

    private fun Proposal.toResponse(): ProposalResponse =
        ProposalResponse(
            id.value,
            order.orderId,
            driver.driverId,
            status.name,
            statedPrice,
            createdAt?.toString(),
            respondedAt?.toString(),
            statedEtaMinutes,
            isTest
        )
}

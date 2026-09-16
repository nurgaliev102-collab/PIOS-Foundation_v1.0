package com.pios.dispatch.api

import com.pios.dispatch.application.ArriveAssignmentCommand
import com.pios.dispatch.application.AssignOrderCommand
import com.pios.dispatch.application.AssignmentNotFoundException
import com.pios.dispatch.application.AssignmentRepository
import com.pios.dispatch.application.CompleteAssignmentCommand
import com.pios.dispatch.application.DispatchAssignmentApplicationService
import com.pios.dispatch.application.NoOpTripRepository
import com.pios.dispatch.application.ProposalRepository
import com.pios.dispatch.application.StartAssignmentCommand
import com.pios.dispatch.application.TripRepository
import com.pios.dispatch.domain.Assignment
import com.pios.dispatch.domain.AssignmentId
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.domain.Trip
import com.pios.dispatch.domain.TripStatus
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
 * Dispatch's Assign Order REST entry point (Sprint FR-004, Manual
 * Assignment; ADR-004, Resource-Oriented API Style; ADR-028, Production
 * Integration Transport Decision). Closes the coordinator's own manual
 * pairing scenario: an order and a driver, both already visible to the
 * coordinator through Order Management's `GET /v1/orders` (Sprint FR-003)
 * and Driver Management's `GET /v1/drivers` (Sprint FR-002), connected by
 * a human decision this endpoint only records — no selection criteria of
 * any kind are evaluated here (ADR-002: the specific criteria for
 * selecting a driver are never Dispatch's own architectural or domain
 * concern; [com.pios.dispatch.domain.Assignment.create]'s own KDoc
 * restates this for the aggregate specifically).
 *
 * Delegates to the self-fetching
 * [DispatchAssignmentApplicationService.handle] overload (Milestone 14B) —
 * this controller adds no business logic of its own beyond the
 * transport-level conversion from request strings to
 * [com.pios.dispatch.domain.OrderReference]/[com.pios.dispatch.domain.DriverReference].
 * The order's existing assignments are looked up by [DispatchAssignmentApplicationService.handle]
 * itself, inside its own transaction boundary, rather than by this
 * controller beforehand — so [com.pios.dispatch.domain.Assignment.create]'s
 * one-active-assignment-per-order invariant is checked against the actual
 * persisted store from inside the same transaction that then writes the
 * new Assignment, closing the window in which this controller's own
 * pre-fetched read could have gone stale before that write.
 *
 * Neither the order nor the driver referenced is verified to exist, be
 * submitted, or be available — Dispatch does not own that information
 * (`OrderReference`/`DriverReference`'s own KDoc; module isolation,
 * ADR-005/ADR-009). A blank `orderId`/`driverId` surfaces as the domain
 * reference type's own [IllegalArgumentException], mapped here to HTTP
 * 400. An order that already has an active assignment surfaces as
 * [Assignment.create]'s own [IllegalStateException], mapped here to HTTP
 * 409 — the first document to fix a concrete status code for this
 * contract (API_SPECIFICATION.md leaves the choice to the
 * implementation, per this project's own established convention; see
 * `OrderSubmissionController`'s own KDoc).
 *
 * ## `assignOrder` deprecated (Sprint IMPLEMENTATION-004)
 *
 * Now that `POST /v1/proposals` and its accept endpoint
 * (`com.pios.dispatch.api.ProposalController`) create an Assignment
 * automatically once a driver accepts, through
 * `com.pios.dispatch.application.ProposalAssignmentOrchestrationService`,
 * this endpoint is no longer the normal path into Assignment creation --
 * a coordinator using it bypasses the Proposal a real driver would
 * otherwise need to confirm. It is marked deprecated, not removed: per
 * Sprint IMPLEMENTATION-004's own explicit instruction ("If an endpoint
 * becomes obsolete: mark it deprecated. Do not remove it yet.") and
 * ADR-015's evolution discipline (no removal without a stated migration
 * window); no ratified decision yet retires manual assignment as a
 * capability outright. Still fully functional; not otherwise touched by
 * this sprint.
 *
 * ## Ride lifecycle (ADR-040, Assignment Ride Lifecycle)
 *
 * `listAssignments`/`arrive`/`start`/`complete` are this ADR's own REST
 * surface — deliberately on `dispatch`'s `/v1/assignments`, not
 * `order-management`'s `/v1/orders`, per that ADR's own placement
 * decision. `listAssignments` reads [assignmentRepository] directly,
 * mirroring [ProposalController.listProposals]'s own precedent of a
 * controller-direct repository query for a plain, unfiltered-beyond-one-
 * parameter listing — no dedicated query handler class exists for this
 * for the same reason none exists there. `orderId` is required (unlike
 * Proposal's own `orderId`-or-`driverId` choice): every known caller of
 * this endpoint (a driver's own screen, a passenger's own screen) already
 * has an order id on hand and nothing else Assignment could be queried by
 * yet.
 *
 * `orderIds` (plural — added for the Client CRM/N+1 fix task) is a purely
 * additive batch mode, following the exact same comma-separated-ids
 * convention `OrderQueryController.listOrders`'s own `?ids=` already
 * established (ADR-060 Mode 2): `?orderIds=<id>,<id>,...` returns every
 * Assignment across the named orders in one response. `orderId` and
 * `orderIds` are mutually exclusive (400 if both are present, same as
 * `OrderQueryController`'s own `passengerReference`/`ids` check); passing
 * neither is still 400, unchanged from before this parameter existed. No
 * new authorization is introduced -- `listAssignments` was already a
 * deliberately open read (Task 22/23's own scope note above) and stays
 * exactly that for both parameters.
 *
 * Each transition endpoint maps [AssignmentNotFoundException] to 404 and
 * an aggregate-rejected transition (wrong current status) to 409 — the
 * same two-status convention [ProposalController]'s own `accept`/`decline`
 * endpoints already established for the identical shape of failure.
 *
 * ## Ride-progress convergence onto Trip (ADR-063; Task 12)
 *
 * `arrive`/`start`/`complete` still call
 * [DispatchAssignmentApplicationService.arriveAssignment]/`startAssignment`/`completeAssignment`
 * exactly as before — that service now transitions the connected [Trip]
 * rather than the [Assignment] itself (see that class's own KDoc). This
 * controller's own [toResponse] therefore now projects from **both**:
 * [Assignment] for identity/order/driver/isTest (unaffected by this
 * convergence), [Trip] for `status`/`statusChangedAt`/`arrivedAt`/
 * `startedAt`/`completedAt` once the connected Trip has moved past
 * [TripStatus.CREATED] — otherwise falling back to [Assignment]'s own
 * `status`/`statusChangedAt` exactly as before (still `CREATED` or
 * `ACCEPTED`, the only two states Trip does not itself model). The wire
 * shape ([AssignmentResponse]'s own fields, this endpoint's own URLs) is
 * completely unchanged — no frontend caller needs to know Trip exists.
 *
 * ## Task 23 (Assignment API Security Remediation)
 *
 * `arrive`/`start`/`complete` now require an `Authorization` header
 * naming, via a verified `Bearer` session token, the exact driver this
 * Assignment's own [Assignment.driver] names — closing the gap Task 22's
 * own audit found (`docs/PIOS_TAXI_TASK_22_ASSIGNMENT_SECURITY_AUDIT.md`),
 * using no new mechanism: [sessionTokenVerifier] is the same class
 * [ProposalController]'s own `acceptProposal`/`declineProposal` already
 * use for the identical check (Task 21), and [Assignment.driver] already
 * carried the field this check reads — nothing new was added to the
 * domain model. `listAssignments` and the deprecated `assignOrder` are
 * deliberately **not** touched by this task (Task 22's own Section 6/9:
 * the former is an already-ratified, deliberately open read; the latter
 * has no real caller today and Task 23's own scope names only
 * `arrive`/`start`/`complete`).
 */
@RestController
@RequestMapping("/v1/assignments")
class AssignmentController(
    private val dispatchAssignmentApplicationService: DispatchAssignmentApplicationService,
    private val assignmentRepository: AssignmentRepository,
    private val tripRepository: TripRepository = NoOpTripRepository,
    private val sessionTokenVerifier: SessionTokenVerifier,
    private val ownerCredentialGate: OwnerCredentialGate? = null,
    private val proposalRepository: ProposalRepository? = null
) {

    @Deprecated(
        message = "Superseded as the normal path by Proposal -> Assignment orchestration " +
            "(Sprint IMPLEMENTATION-004, ProposalAssignmentOrchestrationService). Retained for manual override; not removed."
    )
    @PostMapping
    fun assignOrderHttp(
        @RequestBody request: AssignOrderRequest,
        @RequestHeader("Authorization", required = false) authorization: String? = null
    ): ResponseEntity<AssignOrderResponse> {
        if (ownerCredentialGate?.verify(authorization) != true) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        return assignOrder(request)
    }

    /** Direct application seam for lifecycle tests; not mapped to HTTP. */
    internal fun assignOrder(request: AssignOrderRequest): ResponseEntity<AssignOrderResponse> =
        try {
            val order = OrderReference(request.orderId)
            val driver = DriverReference(request.driverId)
            val created = dispatchAssignmentApplicationService.handle(AssignOrderCommand(order, driver, request.isTest))
            ResponseEntity.status(HttpStatus.CREATED).body(
                AssignOrderResponse(created.assignment.id.value, created.assignment.status.name)
            )
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        } catch (ex: IllegalStateException) {
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        }

    @GetMapping
    fun listAssignmentsHttp(
        @RequestHeader("Authorization", required = false) authorization: String? = null,
        @RequestParam(required = false) orderId: String? = null,
        @RequestParam(required = false) orderIds: String? = null
    ): ResponseEntity<List<AssignmentResponse>> {
        if (ownerCredentialGate?.verify(authorization) == true) {
            return listAssignments(orderId, orderIds)
        }
        val verified = sessionTokenVerifier.verify(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val response = listAssignments(orderId, orderIds)
        if (!response.statusCode.is2xxSuccessful) {
            return response
        }
        val authorized = response.body.orEmpty().filter { assignment ->
            assignment.driverId == verified.drv ||
                proposalRepository?.findByOrder(OrderReference(assignment.orderId))
                    ?.any { it.passengerReference?.passengerId == verified.sub } == true
        }
        return ResponseEntity.ok(authorized)
    }

    /** Direct application seam for repository/lifecycle tests; not mapped to HTTP. */
    internal fun listAssignments(
        orderId: String? = null,
        orderIds: String? = null
    ): ResponseEntity<List<AssignmentResponse>> = try {
        when {
            orderId != null && orderIds != null -> ResponseEntity.badRequest().build()
            orderId != null -> ResponseEntity.ok(
                assignmentRepository.findByOrder(OrderReference(orderId))
                    .map { it.toResponse(tripRepository.findByAssignmentId(it.id)) }
            )
            orderIds != null -> {
                val idList = orderIds.split(",")
                if (idList.isEmpty() || idList.any { it.isBlank() } || idList.size > MAX_ORDER_IDS) {
                    ResponseEntity.badRequest().build()
                } else {
                    ResponseEntity.ok(
                        assignmentRepository.findByOrders(idList.map { OrderReference(it) })
                            .map { it.toResponse(tripRepository.findByAssignmentId(it.id)) }
                    )
                }
            }
            else -> ResponseEntity.badRequest().build()
        }
    } catch (ex: IllegalArgumentException) {
        ResponseEntity.badRequest().build()
    }

    /**
     * Task 23: requires a `Bearer` session token verifying as the exact
     * driver named on this Assignment -- mirrors
     * [ProposalController.acceptProposal]'s own identical check (Task 21)
     * exactly, down to the ordering: the token is verified first (401,
     * before any lookup, so a fully anonymous caller never learns whether
     * a given id exists at all); the Assignment is then looked up (404 if
     * it does not exist); only then is the verified driver compared
     * against [Assignment.driver] (403 on mismatch, including a
     * passenger-only token with `drv == null`).
     */
    @PostMapping("/{assignmentId}/arrive")
    fun arrive(
        @PathVariable assignmentId: String,
        @RequestHeader("Authorization", required = false) authorization: String? = null
    ): ResponseEntity<AssignmentResponse> {
        return try {
            val id = AssignmentId(assignmentId)
            val verified = sessionTokenVerifier.verify(authorization)
                ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            val assignment = assignmentRepository.findById(id)
                ?: return ResponseEntity.notFound().build()
            if (verified.drv != assignment.driver.driverId) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
            }
            dispatchAssignmentApplicationService.arriveAssignment(ArriveAssignmentCommand(id))
            ResponseEntity.ok(assignmentRepository.findById(id)!!.let {
                it.toResponse(tripRepository.findByAssignmentId(it.id))
            })
        } catch (ex: AssignmentNotFoundException) {
            ResponseEntity.notFound().build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        } catch (ex: IllegalStateException) {
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        }
    }

    /** Task 23: identical identity check to [arrive]'s own — see that method's own KDoc. */
    @PostMapping("/{assignmentId}/start")
    fun start(
        @PathVariable assignmentId: String,
        @RequestHeader("Authorization", required = false) authorization: String? = null
    ): ResponseEntity<AssignmentResponse> {
        return try {
            val id = AssignmentId(assignmentId)
            val verified = sessionTokenVerifier.verify(authorization)
                ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            val assignment = assignmentRepository.findById(id)
                ?: return ResponseEntity.notFound().build()
            if (verified.drv != assignment.driver.driverId) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
            }
            dispatchAssignmentApplicationService.startAssignment(StartAssignmentCommand(id))
            ResponseEntity.ok(assignmentRepository.findById(id)!!.let {
                it.toResponse(tripRepository.findByAssignmentId(it.id))
            })
        } catch (ex: AssignmentNotFoundException) {
            ResponseEntity.notFound().build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        } catch (ex: IllegalStateException) {
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        }
    }

    /** Task 23: identical identity check to [arrive]'s own — see that method's own KDoc. */
    @PostMapping("/{assignmentId}/complete")
    fun complete(
        @PathVariable assignmentId: String,
        @RequestHeader("Authorization", required = false) authorization: String? = null
    ): ResponseEntity<AssignmentResponse> {
        return try {
            val id = AssignmentId(assignmentId)
            val verified = sessionTokenVerifier.verify(authorization)
                ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            val assignment = assignmentRepository.findById(id)
                ?: return ResponseEntity.notFound().build()
            if (verified.drv != assignment.driver.driverId) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
            }
            dispatchAssignmentApplicationService.completeAssignment(CompleteAssignmentCommand(id))
            ResponseEntity.ok(assignmentRepository.findById(id)!!.let {
                it.toResponse(tripRepository.findByAssignmentId(it.id))
            })
        } catch (ex: AssignmentNotFoundException) {
            ResponseEntity.notFound().build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        } catch (ex: IllegalStateException) {
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        }
    }

    /**
     * Projects [this] Assignment merged with its connected [trip] (`null`
     * for an Assignment with no Trip yet — the same as a Trip still at
     * [TripStatus.CREATED]: no ride-progress fact exists). See this
     * class's own "Ride-progress convergence" KDoc.
     */
    private fun Assignment.toResponse(trip: Trip?): AssignmentResponse {
        val rideProgressed = trip != null && trip.status != TripStatus.CREATED
        return AssignmentResponse(
            id.value,
            order.orderId,
            driver.driverId,
            if (rideProgressed) trip!!.status.name else status.name,
            if (rideProgressed) trip!!.statusChangedAt?.toString() else statusChangedAt?.toString(),
            trip?.arrivedAt?.toString(),
            trip?.startedAt?.toString(),
            trip?.completedAt?.toString(),
            isTest
        )
    }

    companion object {
        // Same shape guard as OrderQueryController.MAX_IDS, applied to the
        // same 100-id bound -- no business meaning attaches to the number.
        private const val MAX_ORDER_IDS = 100
    }
}

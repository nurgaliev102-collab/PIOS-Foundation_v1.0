package com.pios.dispatch.api

import com.pios.dispatch.application.ArriveAssignmentCommand
import com.pios.dispatch.application.AssignOrderCommand
import com.pios.dispatch.application.AssignmentNotFoundException
import com.pios.dispatch.application.AssignmentRepository
import com.pios.dispatch.application.CompleteAssignmentCommand
import com.pios.dispatch.application.DispatchAssignmentApplicationService
import com.pios.dispatch.application.StartAssignmentCommand
import com.pios.dispatch.domain.Assignment
import com.pios.dispatch.domain.AssignmentId
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
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
 * Each transition endpoint maps [AssignmentNotFoundException] to 404 and
 * an aggregate-rejected transition (wrong current status) to 409 — the
 * same two-status convention [ProposalController]'s own `accept`/`decline`
 * endpoints already established for the identical shape of failure.
 */
@RestController
@RequestMapping("/v1/assignments")
class AssignmentController(
    private val dispatchAssignmentApplicationService: DispatchAssignmentApplicationService,
    private val assignmentRepository: AssignmentRepository
) {

    @Deprecated(
        message = "Superseded as the normal path by Proposal -> Assignment orchestration " +
            "(Sprint IMPLEMENTATION-004, ProposalAssignmentOrchestrationService). Retained for manual override; not removed."
    )
    @PostMapping
    fun assignOrder(@RequestBody request: AssignOrderRequest): ResponseEntity<AssignOrderResponse> =
        try {
            val order = OrderReference(request.orderId)
            val driver = DriverReference(request.driverId)
            val created = dispatchAssignmentApplicationService.handle(AssignOrderCommand(order, driver))
            ResponseEntity.status(HttpStatus.CREATED).body(
                AssignOrderResponse(created.assignment.id.value, created.assignment.status.name)
            )
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        } catch (ex: IllegalStateException) {
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        }

    @GetMapping
    fun listAssignments(@RequestParam(required = false) orderId: String?): ResponseEntity<List<AssignmentResponse>> =
        try {
            if (orderId == null) {
                ResponseEntity.badRequest().build()
            } else {
                ResponseEntity.ok(assignmentRepository.findByOrder(OrderReference(orderId)).map { it.toResponse() })
            }
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }

    @PostMapping("/{assignmentId}/arrive")
    fun arrive(@PathVariable assignmentId: String): ResponseEntity<AssignmentResponse> =
        try {
            dispatchAssignmentApplicationService.arriveAssignment(ArriveAssignmentCommand(AssignmentId(assignmentId)))
            ResponseEntity.ok(assignmentRepository.findById(AssignmentId(assignmentId))!!.toResponse())
        } catch (ex: AssignmentNotFoundException) {
            ResponseEntity.notFound().build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        } catch (ex: IllegalStateException) {
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        }

    @PostMapping("/{assignmentId}/start")
    fun start(@PathVariable assignmentId: String): ResponseEntity<AssignmentResponse> =
        try {
            dispatchAssignmentApplicationService.startAssignment(StartAssignmentCommand(AssignmentId(assignmentId)))
            ResponseEntity.ok(assignmentRepository.findById(AssignmentId(assignmentId))!!.toResponse())
        } catch (ex: AssignmentNotFoundException) {
            ResponseEntity.notFound().build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        } catch (ex: IllegalStateException) {
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        }

    @PostMapping("/{assignmentId}/complete")
    fun complete(@PathVariable assignmentId: String): ResponseEntity<AssignmentResponse> =
        try {
            dispatchAssignmentApplicationService.completeAssignment(CompleteAssignmentCommand(AssignmentId(assignmentId)))
            ResponseEntity.ok(assignmentRepository.findById(AssignmentId(assignmentId))!!.toResponse())
        } catch (ex: AssignmentNotFoundException) {
            ResponseEntity.notFound().build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        } catch (ex: IllegalStateException) {
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        }

    private fun Assignment.toResponse() =
        AssignmentResponse(
            id.value,
            order.orderId,
            driver.driverId,
            status.name,
            statusChangedAt?.toString(),
            arrivedAt?.toString(),
            startedAt?.toString(),
            completedAt?.toString()
        )
}

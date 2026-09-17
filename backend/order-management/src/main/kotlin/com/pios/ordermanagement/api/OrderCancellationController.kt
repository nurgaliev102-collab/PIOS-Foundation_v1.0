package com.pios.ordermanagement.api

import com.pios.ordermanagement.application.OrderCancellationCoordinationService
import com.pios.ordermanagement.application.RequestOrderCancellationCommand
import com.pios.ordermanagement.application.OrderLifecycleApplicationService
import com.pios.ordermanagement.application.OrderNotFoundException
import com.pios.ordermanagement.application.OrderRepository
import com.pios.ordermanagement.domain.OrderId
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class CancelOrderRequest(
    val requestId: String? = null,
    val reasonCode: String? = null,
    val note: String? = null,
    val initiator: String? = null
)

data class OrderCancellationStatusResponse(
    val orderId: String,
    val requestId: String?,
    val outcome: String?,
    val initiator: String?,
    val reasonCode: String?,
    val note: String?,
    val terminatedAt: String?
)

/**
 * Order Management's Cancel Order REST entry point (P0-2 Tier 1;
 * `docs/SPRINT_PILOT_BLOCKERS.md`; ADR-053, Proposal Resolution on Order
 * Cancellation — Accepted). Connects the already-existing, already-tested
 * [OrderLifecycleApplicationService.cancelOrder] to a real endpoint for
 * the first time — no new domain logic, no new status
 * ([OrderStatus.CANCELLED] already exists), consistent with ADR-053's own
 * Context: *"Connecting Order Management's already-existing `Order.cancel()`
 * to a real endpoint... is safe and fully authorized on its own."*
 *
 * A separate, dedicated resource-oriented sub-path
 * (`/v1/orders/{orderId}/cancel`), mirroring [AssignmentController]'s own
 * convention of one sub-path per lifecycle transition (`/arrive`,
 * `/start`, `/complete`) — not added to [OrderSubmissionController],
 * whose own KDoc scopes it specifically to Submit Order, or to
 * [OrderQueryController], which is read-only.
 *
 * Status-code mapping mirrors [com.pios.dispatch.api.ProposalController]'s
 * own already-established convention for the identical shape of failure:
 * [OrderNotFoundException] (no such order) maps to 404;
 * [Order.cancel]'s own [IllegalStateException] (the order is not
 * currently `SUBMITTED` — already `COMPLETED` or already `CANCELLED`)
 * maps to 409, the same status [ProposalController]'s own conflicting-
 * transition cases already use.
 *
 * The response is built directly from the returned
 * [com.pios.ordermanagement.domain.OrderCancelled] event, never from a
 * fresh repository read, mirroring [ProposalController.lapseProposal]'s
 * own convention of trusting the event the domain transition itself
 * already returned.
 *
 * ## Task 25 (Orders Cancellation & Driver Availability Security Remediation)
 *
 * `cancelOrder` now requires a `Bearer` session token whose own `sub`
 * equals the order's own [com.pios.ordermanagement.domain.Order.origin]
 * reference -- closing the gap Task 24's own audit found
 * (`docs/PIOS_TAXI_TASK_24_REMAINING_MUTATION_API_SECURITY_AUDIT.md`).
 * No new ownership model: ADR-060 Decision 2 already establishes that
 * `Order.origin` **is** the authenticated passenger's own identity
 * (`OrderQueryController`'s own `?passengerReference=` mode already
 * compares it against `verified.sub` the identical way); this endpoint
 * reuses that same, already-ratified comparison. [orderRepository] is
 * read directly, once, for this check -- mirroring
 * [com.pios.dispatch.api.ProposalController.acceptProposal]'s own
 * identical shape (Task 21) -- before delegating to the existing
 * self-fetching [OrderLifecycleApplicationService.cancelOrder] overload;
 * the resulting redundant second read is the same accepted cost that
 * precedent already carries. No owner/admin `Basic`-credential branch is
 * added: unlike `OrderQueryController`'s own read side, no owner/coordinator
 * cancellation flow exists anywhere in this codebase today (verified this
 * session by re-reading `Coordinator.tsx`) -- adding one would be
 * inventing a capability this task's own scope does not ask for, not
 * preserving an existing one.
 */
@RestController
@RequestMapping("/v1/orders")
class OrderCancellationController(
    private val coordination: OrderCancellationCoordinationService,
    private val orderRepository: OrderRepository,
    private val sessionTokenVerifier: SessionTokenVerifier
) {

    @PostMapping("/{orderId}/cancel")
    fun cancelOrder(
        @PathVariable orderId: String,
        @RequestHeader("Authorization", required = false) authorization: String? = null,
        @RequestBody(required = false) request: CancelOrderRequest? = null
    ): ResponseEntity<CancelOrderResponse> {
        return try {
            val id = OrderId(orderId)
            val verified = sessionTokenVerifier.verify(authorization)
                ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            val order = orderRepository.findById(id)
                ?: return ResponseEntity.notFound().build()
            if (order.origin.reference != verified.sub) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
            }
            if (request?.initiator != null) return ResponseEntity.badRequest().build()
            val record = coordination.request(
                RequestOrderCancellationCommand(
                    requestId = request?.requestId ?: UUID.randomUUID().toString(),
                    orderId = id.value,
                    reasonCode = request?.reasonCode,
                    note = request?.note
                )
            )
            ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(CancelOrderResponse(id.value, record.outcome, record.requestId))
        } catch (ex: OrderNotFoundException) {
            ResponseEntity.notFound().build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        } catch (ex: IllegalStateException) {
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        }
    }

    @GetMapping("/{orderId}/cancellation")
    fun cancellationStatus(
        @PathVariable orderId: String,
        @RequestHeader("Authorization", required = false) authorization: String? = null
    ): ResponseEntity<OrderCancellationStatusResponse> {
        val verified = sessionTokenVerifier.verify(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val order = try { orderRepository.findById(OrderId(orderId)) } catch (_: IllegalArgumentException) { null }
            ?: return ResponseEntity.notFound().build()
        if (order.origin.reference != verified.sub) return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        val request = coordination.findLatest(orderId)
        val fact = coordination.findTermination(orderId)
        return ResponseEntity.ok(
            OrderCancellationStatusResponse(
                orderId,
                request?.requestId ?: fact?.requestId,
                request?.outcome,
                fact?.initiator,
                fact?.reasonCode,
                fact?.note,
                fact?.terminatedAt?.toString()
            )
        )
    }
}

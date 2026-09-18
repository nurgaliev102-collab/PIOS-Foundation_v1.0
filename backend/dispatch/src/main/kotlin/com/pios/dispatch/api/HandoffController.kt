package com.pios.dispatch.api

import com.pios.dispatch.application.AcceptHandoffSubstituteCommand
import com.pios.dispatch.application.AssignmentRepository
import com.pios.dispatch.application.ConsentHandoffCommand
import com.pios.dispatch.application.HandoffApplicationService
import com.pios.dispatch.application.HandoffNotFoundException
import com.pios.dispatch.application.HandoffRepository
import com.pios.dispatch.application.ProposalRepository
import com.pios.dispatch.application.ProposeHandoffCommand
import com.pios.dispatch.application.RefuseHandoffCommand
import com.pios.dispatch.application.WithdrawHandoffCommand
import com.pios.dispatch.domain.AssignmentId
import com.pios.dispatch.domain.Handoff
import com.pios.dispatch.domain.HandoffId
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

data class ProposeHandoffRequest(val assignmentId: String, val substituteDriverId: String)

/**
 * D-07's REST entry point (`docs/PIOS_D07_HANDOFF_IMPLEMENTATION_SPEC.md`
 * §7). Every operation's own authorization check happens **here**, before
 * [handoffApplicationService] is ever called — the same
 * controller-authorizes / service-trusts-the-caller split already
 * established by [AssignmentTerminationController.terminate] and
 * [AssignmentController.arrive]/`start`/`complete`, and the same reason:
 * [sessionTokenVerifier] verifies the caller's own identity first (401
 * before any lookup, so a fully anonymous caller never learns whether a
 * given id exists); the target aggregate is then looked up (404 if
 * absent); only then is the verified identity compared against the
 * aggregate's own relevant field (403 on mismatch).
 *
 * Every request body accepts only *content* (which substitute is being
 * named), never *identity* (who is asking) — the acting driver/passenger
 * is always [SessionTokenVerifier.VerifiedToken.drv]/`.sub`, never a
 * request field, per D-07's own "server derives every actor identity"
 * invariant.
 */
@RestController
@RequestMapping("/v1/handoffs")
class HandoffController(
    private val handoffApplicationService: HandoffApplicationService,
    private val handoffRepository: HandoffRepository,
    private val assignmentRepository: AssignmentRepository,
    private val proposalRepository: ProposalRepository,
    private val sessionTokenVerifier: SessionTokenVerifier
) {

    /**
     * The original committing driver proposes a Handoff. Authorization:
     * caller's own `drv` must equal the target Assignment's own
     * `driver.driverId` — identical to [AssignmentTerminationController.terminate]'s
     * own check, for the same reason (D-07 invariant #9: only the
     * original driver may propose).
     */
    @PostMapping
    fun propose(
        @RequestBody request: ProposeHandoffRequest,
        @RequestHeader("Authorization", required = false) authorization: String? = null
    ): ResponseEntity<HandoffResponse> {
        val verified = sessionTokenVerifier.verify(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        return try {
            val assignmentId = AssignmentId(request.assignmentId)
            val assignment = assignmentRepository.findById(assignmentId)
                ?: return ResponseEntity.notFound().build()
            if (verified.drv != assignment.driver.driverId) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
            }
            val created = handoffApplicationService.propose(
                ProposeHandoffCommand(assignmentId, request.substituteDriverId)
            )
            ResponseEntity.status(HttpStatus.CREATED).body(created.handoff.toResponse())
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        } catch (ex: IllegalStateException) {
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        }
    }

    /**
     * The named substitute explicitly accepts. Authorization: caller's
     * own `drv` must equal the Handoff's own `substituteDriver.driverId`
     * — the one authorization shape this endpoint introduces with no
     * direct precedent elsewhere in this module (spec §11's own "genuinely
     * new pattern" note): no existing check compares a caller against
     * "the substitute named on someone else's proposal."
     */
    @PostMapping("/{handoffId}/accept-substitute")
    fun acceptSubstitute(
        @PathVariable handoffId: String,
        @RequestHeader("Authorization", required = false) authorization: String? = null
    ): ResponseEntity<HandoffResponse> {
        val verified = sessionTokenVerifier.verify(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        return try {
            val id = HandoffId(handoffId)
            val handoff = handoffRepository.findById(id) ?: return ResponseEntity.notFound().build()
            if (verified.drv != handoff.substituteDriver.driverId) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
            }
            handoffApplicationService.acceptSubstitute(AcceptHandoffSubstituteCommand(id))
            respondWithCurrent(id)
        } catch (ex: HandoffNotFoundException) {
            ResponseEntity.notFound().build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        } catch (ex: IllegalStateException) {
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        }
    }

    /**
     * The passenger's own constitutive consent. Authorization: caller's
     * own `sub` must equal the Handoff's own `passenger.passengerId` —
     * the same passenger-check pattern [AssignmentController.listAssignmentsHttp]
     * already established, reused unmodified. Works identically for a
     * guest passenger session (D-07 invariant #31/#32): the check
     * compares identities, never whether the identity is phone-verified.
     */
    @PostMapping("/{handoffId}/consent")
    fun consent(
        @PathVariable handoffId: String,
        @RequestHeader("Authorization", required = false) authorization: String? = null
    ): ResponseEntity<HandoffResponse> {
        val verified = sessionTokenVerifier.verify(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        return try {
            val id = HandoffId(handoffId)
            val handoff = handoffRepository.findById(id) ?: return ResponseEntity.notFound().build()
            if (handoff.passenger == null || verified.sub != handoff.passenger.passengerId) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
            }
            handoffApplicationService.consent(ConsentHandoffCommand(id))
            respondWithCurrent(id)
        } catch (ex: HandoffNotFoundException) {
            ResponseEntity.notFound().build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        } catch (ex: IllegalStateException) {
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        }
    }

    /** The passenger declines. Same authorization pattern as [consent]. */
    @PostMapping("/{handoffId}/refuse")
    fun refuse(
        @PathVariable handoffId: String,
        @RequestHeader("Authorization", required = false) authorization: String? = null
    ): ResponseEntity<HandoffResponse> {
        val verified = sessionTokenVerifier.verify(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        return try {
            val id = HandoffId(handoffId)
            val handoff = handoffRepository.findById(id) ?: return ResponseEntity.notFound().build()
            if (handoff.passenger == null || verified.sub != handoff.passenger.passengerId) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
            }
            handoffApplicationService.refuse(RefuseHandoffCommand(id))
            respondWithCurrent(id)
        } catch (ex: HandoffNotFoundException) {
            ResponseEntity.notFound().build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        } catch (ex: IllegalStateException) {
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        }
    }

    /**
     * The named substitute declines this specific Handoff — reuses the
     * exact same [Handoff.refuse] domain transition [refuse] (the
     * passenger's own equivalent act) already calls: `refuse()` itself is
     * state-based (`PROPOSED`/`SUBSTITUTE_ACCEPTED` → `REFUSED`), not
     * actor-based, so it correctly serves both "the substitute never
     * wanted this Handoff" and "the passenger declined it" without a
     * sixth [HandoffStatus] value the locked specification does not
     * authorize inventing. What differs is authorization only: here,
     * caller's own `drv` must equal the Handoff's own
     * `substituteDriver.driverId`, never the passenger's own identity.
     */
    @PostMapping("/{handoffId}/decline-substitute")
    fun declineSubstitute(
        @PathVariable handoffId: String,
        @RequestHeader("Authorization", required = false) authorization: String? = null
    ): ResponseEntity<HandoffResponse> {
        val verified = sessionTokenVerifier.verify(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        return try {
            val id = HandoffId(handoffId)
            val handoff = handoffRepository.findById(id) ?: return ResponseEntity.notFound().build()
            if (verified.drv != handoff.substituteDriver.driverId) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
            }
            handoffApplicationService.refuse(RefuseHandoffCommand(id))
            respondWithCurrent(id)
        } catch (ex: HandoffNotFoundException) {
            ResponseEntity.notFound().build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        } catch (ex: IllegalStateException) {
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        }
    }

    /**
     * The original driver withdraws. Authorization: caller's own `drv`
     * must equal the Handoff's own `originalDriver.driverId`. Returns
     * `409 CONFLICT` (via [Handoff.withdraw]'s own [IllegalStateException])
     * if the Handoff is already `COMMITTED` — D-07's own unconditional
     * "no withdrawal after passenger consent" rule.
     */
    @PostMapping("/{handoffId}/withdraw")
    fun withdraw(
        @PathVariable handoffId: String,
        @RequestHeader("Authorization", required = false) authorization: String? = null
    ): ResponseEntity<HandoffResponse> {
        val verified = sessionTokenVerifier.verify(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        return try {
            val id = HandoffId(handoffId)
            val handoff = handoffRepository.findById(id) ?: return ResponseEntity.notFound().build()
            if (verified.drv != handoff.originalDriver.driverId) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
            }
            handoffApplicationService.withdraw(WithdrawHandoffCommand(id))
            respondWithCurrent(id)
        } catch (ex: HandoffNotFoundException) {
            ResponseEntity.notFound().build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        } catch (ex: IllegalStateException) {
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        }
    }

    /**
     * Lists Handoffs for a given Assignment. Authorization mirrors
     * [AssignmentController.listAssignmentsHttp] exactly: the Assignment's
     * own driver, or the order's own passenger (via its `ACCEPTED`
     * Proposal's `passengerReference`), may read — never a bare,
     * unauthenticated `assignmentId`. Used by the original driver's own
     * screen and the passenger's own screen alike.
     */
    @GetMapping
    fun list(
        @RequestParam(required = false) assignmentId: String? = null,
        @RequestParam(required = false) substituteDriverId: String? = null,
        @RequestHeader("Authorization", required = false) authorization: String? = null
    ): ResponseEntity<List<HandoffResponse>> {
        val verified = sessionTokenVerifier.verify(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        return try {
            when {
                assignmentId != null && substituteDriverId != null -> ResponseEntity.badRequest().build()
                assignmentId != null -> {
                    val id = AssignmentId(assignmentId)
                    val assignment = assignmentRepository.findById(id) ?: return ResponseEntity.notFound().build()
                    val handoffsForAssignment = handoffRepository.findByAssignmentId(id)
                    val isOwnDriver = verified.drv == assignment.driver.driverId
                    // A driver who was ever named as this Assignment's own
                    // substitute (pending, accepted, or already resolved) may
                    // also read -- this is how a substitute learns their own
                    // Handoff's final outcome (COMMITTED/REFUSED) once it
                    // drops out of the non-terminal `?substituteDriverId=`
                    // listing below.
                    val isOwnSubstitute = !isOwnDriver && handoffsForAssignment.any { it.substituteDriver.driverId == verified.drv }
                    val isOwnPassenger = !isOwnDriver && !isOwnSubstitute && proposalRepository.findByOrder(assignment.order)
                        .any { it.status == ProposalStatus.ACCEPTED && it.passengerReference?.passengerId == verified.sub }
                    if (!isOwnDriver && !isOwnSubstitute && !isOwnPassenger) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
                    }
                    ResponseEntity.ok(handoffsForAssignment.map { it.toResponse() })
                }
                substituteDriverId != null -> {
                    // Self-only: a driver may list Handoffs naming *themselves*
                    // as substitute, never another driver's own inbox.
                    if (verified.drv != substituteDriverId) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
                    }
                    ResponseEntity.ok(handoffRepository.findBySubstituteDriver(substituteDriverId).map { it.toResponse() })
                }
                else -> ResponseEntity.badRequest().build()
            }
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
    }

    private fun respondWithCurrent(id: HandoffId): ResponseEntity<HandoffResponse> =
        ResponseEntity.ok(handoffRepository.findById(id)!!.toResponse())

    private fun Handoff.toResponse(): HandoffResponse = HandoffResponse(
        handoffId = id.value,
        assignmentId = assignmentId.value,
        orderId = order.orderId,
        originalDriverId = originalDriver.driverId,
        substituteDriverId = substituteDriver.driverId,
        status = status.name,
        proposedAt = proposedAt.toString(),
        substituteAcceptedAt = substituteAcceptedAt?.toString(),
        resolvedAt = resolvedAt?.toString()
    )
}

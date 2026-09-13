package com.pios.dispatch.api

import com.pios.dispatch.application.MessagingClosedException
import com.pios.dispatch.application.ProposalMessagingApplicationService
import com.pios.dispatch.application.ProposalNotFoundException
import com.pios.dispatch.application.ProposalRepository
import com.pios.dispatch.domain.MessageSenderRole
import com.pios.dispatch.domain.ProposalId
import com.pios.dispatch.domain.ProposalMessage
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Minimal In-Ride Messaging MVP (Product Cycle) — Dispatch's own REST
 * entry points for a short passenger↔driver message thread scoped to one
 * [com.pios.dispatch.domain.Proposal]. A new, small controller class
 * within this same module (not a new service) — mirrors
 * [ProposalController]/[AssignmentController]'s own convention of
 * splitting controllers by concern within one module rather than growing
 * an existing class to also mean "messaging."
 *
 * Authorization mirrors [ProposalController.getProposal] and
 * [ProposalController.acceptProposal] exactly, since this endpoint's own
 * participant set is identical to theirs — this proposal's own passenger
 * and driver, nothing wider:
 *
 * - `sendMessage`: `Bearer`-only (401 without one) — a driver or passenger
 *   sending a message is the only legitimate case; unlike `createProposal`,
 *   there is no owner/coordinator role to claim here (no
 *   [MessageSenderRole] fits "the platform itself"). 404 if the proposal
 *   is unknown. [MessageSenderRole] is derived, never a caller-supplied
 *   field: the token's own `sub` matching [com.pios.dispatch.domain.Proposal.passengerReference]
 *   sends as `PASSENGER`; `drv` matching [com.pios.dispatch.domain.Proposal.driver]
 *   sends as `DRIVER`; neither is 403 — the same three-way split
 *   [ProposalController.getProposal] already established for reading.
 * - `listMessages`: `Basic` owner credential (unrestricted, mirrors
 *   [ProposalController.getProposal]'s own support/ops allowance) or
 *   `Bearer` naming either participant (403 otherwise); 401 with neither.
 *
 * Status code mapping: [MessagingClosedException] (this proposal's own
 * ride is over, or never happened — see
 * [ProposalMessagingApplicationService]'s own KDoc for the full lifecycle
 * decision) maps to HTTP 409, the same status this module already uses
 * for every other "wrong state" rejection
 * ([ProposalController]'s own KDoc). A blank or over-length
 * [SendProposalMessageRequest.body] surfaces as
 * [com.pios.dispatch.domain.ProposalMessage]'s own [IllegalArgumentException],
 * mapped to HTTP 400 like every other invalid input this module handles.
 */
@RestController
@RequestMapping("/v1/proposals")
class ProposalMessageController(
    private val proposalMessagingApplicationService: ProposalMessagingApplicationService,
    private val proposalRepository: ProposalRepository,
    private val sessionTokenVerifier: SessionTokenVerifier,
    private val ownerCredentialGate: OwnerCredentialGate
) {

    @PostMapping("/{proposalId}/messages")
    fun sendMessage(
        @PathVariable proposalId: String,
        @RequestBody request: SendProposalMessageRequest,
        @RequestHeader("Authorization", required = false) authorization: String? = null
    ): ResponseEntity<ProposalMessageResponse> {
        return try {
            val id = ProposalId(proposalId)
            val verified = sessionTokenVerifier.verify(authorization)
                ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            val proposal = proposalRepository.findById(id) ?: return ResponseEntity.notFound().build()
            val senderRole = when {
                proposal.passengerReference?.passengerId == verified.sub -> MessageSenderRole.PASSENGER
                proposal.driver.driverId == verified.drv -> MessageSenderRole.DRIVER
                else -> return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
            }
            val message = proposalMessagingApplicationService.sendMessage(id, senderRole, request.body)
            ResponseEntity.status(HttpStatus.CREATED).body(message.toResponse())
        } catch (ex: ProposalNotFoundException) {
            ResponseEntity.notFound().build()
        } catch (ex: MessagingClosedException) {
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
    }

    @GetMapping("/{proposalId}/messages")
    fun listMessages(
        @PathVariable proposalId: String,
        @RequestHeader("Authorization", required = false) authorization: String? = null
    ): ResponseEntity<List<ProposalMessageResponse>> {
        return try {
            val id = ProposalId(proposalId)
            if (authorization != null && authorization.startsWith("Basic ")) {
                if (!ownerCredentialGate.verify(authorization)) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
                }
                proposalRepository.findById(id) ?: return ResponseEntity.notFound().build()
                return ResponseEntity.ok(proposalMessagingApplicationService.listMessages(id).map { it.toResponse() })
            }
            val verified = sessionTokenVerifier.verify(authorization)
                ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            val proposal = proposalRepository.findById(id) ?: return ResponseEntity.notFound().build()
            if (proposal.passengerReference?.passengerId != verified.sub && proposal.driver.driverId != verified.drv) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
            }
            ResponseEntity.ok(proposalMessagingApplicationService.listMessages(id).map { it.toResponse() })
        } catch (ex: ProposalNotFoundException) {
            ResponseEntity.notFound().build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
    }

    private fun ProposalMessage.toResponse(): ProposalMessageResponse =
        ProposalMessageResponse(id.value, senderRole.name, body, sentAt.toString())
}

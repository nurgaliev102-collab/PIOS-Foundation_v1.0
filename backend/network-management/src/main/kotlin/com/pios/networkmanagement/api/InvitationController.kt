package com.pios.networkmanagement.api

import com.pios.networkmanagement.application.AcceptInvitationApplicationService
import com.pios.networkmanagement.application.AcceptInvitationCommand
import com.pios.networkmanagement.application.CreateInvitationApplicationService
import com.pios.networkmanagement.application.CreateInvitationCommand
import com.pios.networkmanagement.application.InvitationNotFoundException
import com.pios.networkmanagement.application.PersonNotFoundException
import com.pios.networkmanagement.domain.PersonId
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Network Management's Invitation REST entry points (Sprint 7A: PIOS
 * Network Foundation). An unrecognized `creatorPersonId`/`personId`
 * surfaces as [PersonNotFoundException] (HTTP 404); an unrecognized
 * invitation `code` surfaces as [InvitationNotFoundException] (HTTP 404);
 * an already-used (or otherwise not-`CREATED`) invitation surfaces as the
 * domain's own [IllegalStateException] (HTTP 409), mirroring Dispatch's
 * own `ProposalController` convention for a conflicting-state transition; a
 * blank id surfaces as [IllegalArgumentException] (HTTP 400).
 */
@RestController
@RequestMapping("/v1/invitations")
class InvitationController(
    private val createInvitationApplicationService: CreateInvitationApplicationService,
    private val acceptInvitationApplicationService: AcceptInvitationApplicationService
) {

    @PostMapping
    fun createInvitation(@RequestBody request: CreateInvitationRequest): ResponseEntity<InvitationResponse> =
        try {
            val invitation = createInvitationApplicationService.handle(
                CreateInvitationCommand(PersonId(request.creatorPersonId))
            )
            ResponseEntity.status(201).body(InvitationResponse(invitation.code, "pios/invite/${invitation.code}"))
        } catch (ex: PersonNotFoundException) {
            ResponseEntity.notFound().build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }

    @PostMapping("/{code}/accept")
    fun acceptInvitation(
        @PathVariable code: String,
        @RequestBody request: AcceptInvitationRequest
    ): ResponseEntity<ConnectionResponse> =
        try {
            val connection = acceptInvitationApplicationService.handle(
                AcceptInvitationCommand(code, PersonId(request.personId))
            )
            ResponseEntity.ok(ConnectionResponse(connection.id.value, connection.type.name))
        } catch (ex: InvitationNotFoundException) {
            ResponseEntity.notFound().build()
        } catch (ex: PersonNotFoundException) {
            ResponseEntity.notFound().build()
        } catch (ex: IllegalStateException) {
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
}

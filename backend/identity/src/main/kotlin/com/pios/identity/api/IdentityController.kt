package com.pios.identity.api

import com.pios.identity.application.AssociateDriverApplicationService
import com.pios.identity.application.AssociateDriverCommand
import com.pios.identity.application.CreateIdentityApplicationService
import com.pios.identity.application.CreateIdentityCommand
import com.pios.identity.application.IdentityNotFoundException
import com.pios.identity.application.RetrieveIdentityHandler
import com.pios.identity.domain.Identity
import com.pios.identity.domain.IdentityId
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Identity's REST entry points (ADR-038, ADR-039) — Create/Retrieve Identity
 * plus Associate Driver, the same "no business logic beyond transport-level
 * conversion" convention as every other module's own controller
 * (`com.pios.networkmanagement.api.PersonController`'s own KDoc).
 *
 * A malformed phone surfaces as [com.pios.identity.domain.Phone]'s own
 * `require` check, mapped here to HTTP 400. An unrecognized identity id
 * surfaces as [IdentityNotFoundException], mapped to HTTP 404. No other
 * endpoint exists on this controller — no verification, no login, no
 * session issuance (ADR-038's own explicit scope boundary, unchanged by
 * ADR-039).
 */
@RestController
@RequestMapping("/v1/identities")
class IdentityController(
    private val createIdentityApplicationService: CreateIdentityApplicationService,
    private val retrieveIdentityHandler: RetrieveIdentityHandler,
    private val associateDriverApplicationService: AssociateDriverApplicationService
) {

    @PostMapping
    fun createIdentity(@RequestBody request: CreateIdentityRequest): ResponseEntity<IdentityResponse> =
        try {
            val identity = createIdentityApplicationService.handle(CreateIdentityCommand(request.phone))
            ResponseEntity.status(201).body(identity.toResponse())
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }

    @GetMapping("/{id}")
    fun getIdentity(@PathVariable id: String): ResponseEntity<IdentityResponse> =
        try {
            val identity = retrieveIdentityHandler.handle(IdentityId(id))
            ResponseEntity.ok(identity.toResponse())
        } catch (ex: IdentityNotFoundException) {
            ResponseEntity.notFound().build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }

    @PostMapping("/{id}/driver")
    fun associateDriver(
        @PathVariable id: String,
        @RequestBody request: AssociateDriverRequest
    ): ResponseEntity<IdentityResponse> =
        try {
            val identity = associateDriverApplicationService.handle(AssociateDriverCommand(id, request.driverId))
            ResponseEntity.ok(identity.toResponse())
        } catch (ex: IdentityNotFoundException) {
            ResponseEntity.notFound().build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }

    private fun Identity.toResponse() = IdentityResponse(id.value, phone?.value, driverId)
}

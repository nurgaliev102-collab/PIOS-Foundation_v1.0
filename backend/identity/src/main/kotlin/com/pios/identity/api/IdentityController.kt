package com.pios.identity.api

import com.pios.identity.application.AssociateDriverApplicationService
import com.pios.identity.application.AssociateDriverCommand
import com.pios.identity.application.IdentityNotFoundException
import com.pios.identity.application.LoginApplicationService
import com.pios.identity.application.LoginCommand
import com.pios.identity.application.LoginOutcome
import com.pios.identity.application.PhoneAlreadyRegisteredException
import com.pios.identity.application.RegisterIdentityApplicationService
import com.pios.identity.application.RegisterIdentityCommand
import com.pios.identity.application.RegisterIdentityOutcome
import com.pios.identity.application.RetrieveIdentityHandler
import com.pios.identity.domain.Identity
import com.pios.identity.domain.IdentityId
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
 * Identity's REST entry points (ADR-038, ADR-039, ADR-055) — Register,
 * Login, Retrieve Own Identity, Retrieve Identity, and Associate Driver,
 * the same "no business logic beyond transport-level conversion"
 * convention as every other module's own controller
 * (`com.pios.networkmanagement.api.PersonController`'s own KDoc).
 *
 * ADR-055 Decision 3 removes the old credential-less `POST /v1/identities`
 * entirely — leaving it would let anyone mint a credential-less identity
 * and attach a driver reference to it, the exact hole that ADR closes.
 * `CreateIdentityApplicationService` is no longer a dependency of this
 * controller for that reason; the class itself is untouched and still
 * exercised directly (bypassing HTTP) by tests that predate credentials.
 *
 * `GET /v1/identities/{id}` and `POST /v1/identities/{id}/driver` now
 * require a valid `Bearer` session token whose `sub` claim equals `{id}`
 * (ADR-055 Decision 3): a missing/invalid/expired token is 401, a valid
 * token for a *different* subject is 403. `GET /v1/identities/me` reads
 * the caller's own identity from the token's `sub` claim alone, so no id
 * is ever supplied by the caller for that endpoint.
 *
 * A malformed phone or a blank password on `register` surfaces as
 * [IllegalArgumentException], mapped here to HTTP 400. An unrecognized
 * identity id surfaces as [IdentityNotFoundException], mapped to HTTP
 * 404. `login` never distinguishes *why* it failed — every failure is the
 * same [LoginOutcome.Failure], mapped to the same 401 (ADR-055 Decision
 * 3, reusing ADR-044 Decision 3's own "never distinguish" discipline).
 */
@RestController
@RequestMapping("/v1/identities")
class IdentityController(
    private val registerIdentityApplicationService: RegisterIdentityApplicationService,
    private val loginApplicationService: LoginApplicationService,
    private val retrieveIdentityHandler: RetrieveIdentityHandler,
    private val associateDriverApplicationService: AssociateDriverApplicationService,
    private val sessionTokenVerifier: SessionTokenVerifier
) {

    @PostMapping("/register")
    fun register(@RequestBody request: RegisterIdentityRequest): ResponseEntity<AuthResponse> =
        try {
            val outcome = registerIdentityApplicationService.handle(
                RegisterIdentityCommand(request.phone, request.password)
            )
            ResponseEntity.status(HttpStatus.CREATED).body(outcome.toResponse())
        } catch (ex: PhoneAlreadyRegisteredException) {
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<AuthResponse> =
        when (val outcome = loginApplicationService.handle(LoginCommand(request.phone, request.password))) {
            is LoginOutcome.Success -> ResponseEntity.ok(
                AuthResponse(outcome.identity.id.value, outcome.identity.driverId, outcome.token, outcome.expiresAt.toString())
            )
            LoginOutcome.Failure -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

    @GetMapping("/me")
    fun getOwnIdentity(
        @RequestHeader("Authorization", required = false) authorization: String?
    ): ResponseEntity<IdentityResponse> {
        val verified = sessionTokenVerifier.verify(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        return try {
            ResponseEntity.ok(retrieveIdentityHandler.handle(IdentityId(verified.sub)).toResponse())
        } catch (ex: IdentityNotFoundException) {
            // The token was correctly signed and unexpired, but names an
            // identity that no longer resolves -- treated as unauthenticated
            // rather than 404, since nothing about "who is calling" was
            // ever verified for a subject this module cannot find.
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
    }

    @GetMapping("/{id}")
    fun getIdentity(
        @PathVariable id: String,
        @RequestHeader("Authorization", required = false) authorization: String?
    ): ResponseEntity<IdentityResponse> {
        val verified = sessionTokenVerifier.verify(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (verified.sub != id) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        return try {
            ResponseEntity.ok(retrieveIdentityHandler.handle(IdentityId(id)).toResponse())
        } catch (ex: IdentityNotFoundException) {
            ResponseEntity.notFound().build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
    }

    @PostMapping("/{id}/driver")
    fun associateDriver(
        @PathVariable id: String,
        @RequestBody request: AssociateDriverRequest,
        @RequestHeader("Authorization", required = false) authorization: String?
    ): ResponseEntity<IdentityResponse> {
        val verified = sessionTokenVerifier.verify(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (verified.sub != id) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        return try {
            val identity = associateDriverApplicationService.handle(AssociateDriverCommand(id, request.driverId))
            ResponseEntity.ok(identity.toResponse())
        } catch (ex: IdentityNotFoundException) {
            ResponseEntity.notFound().build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
    }

    private fun Identity.toResponse() = IdentityResponse(id.value, phone?.value, driverId)

    private fun RegisterIdentityOutcome.toResponse() =
        AuthResponse(identity.id.value, identity.driverId, token, expiresAt.toString())
}

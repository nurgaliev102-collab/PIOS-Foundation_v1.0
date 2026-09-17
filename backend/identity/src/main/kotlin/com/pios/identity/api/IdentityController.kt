package com.pios.identity.api

import com.pios.identity.application.AssociateDriverApplicationService
import com.pios.identity.application.AssociateDriverCommand
import com.pios.identity.application.AssociateDriverOutcome
import com.pios.identity.application.ConfirmPhoneVerificationApplicationService
import com.pios.identity.application.ConfirmPhoneVerificationCommand
import com.pios.identity.application.ConfirmPhoneVerificationOutcome
import com.pios.identity.application.ConfirmRecoveryApplicationService
import com.pios.identity.application.ConfirmRecoveryCommand
import com.pios.identity.application.ConfirmRecoveryOutcome
import com.pios.identity.application.CreateGuestIdentityApplicationService
import com.pios.identity.application.GuestIdentityOutcome
import com.pios.identity.application.GuestIdentityRateLimiter
import com.pios.identity.application.IdentityNotFoundException
import com.pios.identity.application.LoginApplicationService
import com.pios.identity.application.LoginCommand
import com.pios.identity.application.LoginOutcome
import com.pios.identity.application.OtpClientKeyRateLimiter
import com.pios.identity.application.PhoneAlreadyRegisteredException
import com.pios.identity.application.PhoneAlreadyVerifiedException
import com.pios.identity.application.RegisterIdentityApplicationService
import com.pios.identity.application.RegisterIdentityCommand
import com.pios.identity.application.RegisterIdentityOutcome
import com.pios.identity.application.RequestPhoneVerificationApplicationService
import com.pios.identity.application.RequestRecoveryApplicationService
import com.pios.identity.application.RetrieveIdentityHandler
import com.pios.identity.application.StaleSessionException
import com.pios.identity.application.UpgradeGuestIdentityApplicationService
import com.pios.identity.application.UpgradeGuestIdentityCommand
import com.pios.identity.domain.Identity
import com.pios.identity.domain.IdentityId
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.InetAddress

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
 * `POST /v1/identities/{id}/driver` returns [AuthResponse], not
 * [IdentityResponse] (ADR-055 Decision 6 addendum) — associating a driver
 * changes what the caller's own token *should* assert (`drv`), so a fresh
 * token carrying it is issued and returned immediately, the same "mutate,
 * then re-mint" shape `register`/`login` already use. The token the caller
 * presented to make this call keeps verifying successfully until it
 * expires — ADR-055's stateless model has no server-side revocation — but
 * the caller has no reason to keep sending it once it has this response's
 * own fresh one.
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
    private val sessionTokenVerifier: SessionTokenVerifier,
    private val createGuestIdentityApplicationService: CreateGuestIdentityApplicationService,
    private val guestIdentityRateLimiter: GuestIdentityRateLimiter,
    private val upgradeGuestIdentityApplicationService: UpgradeGuestIdentityApplicationService,
    private val requestRecoveryApplicationService: RequestRecoveryApplicationService,
    private val confirmRecoveryApplicationService: ConfirmRecoveryApplicationService,
    private val requestPhoneVerificationApplicationService: RequestPhoneVerificationApplicationService,
    private val confirmPhoneVerificationApplicationService: ConfirmPhoneVerificationApplicationService,
    private val otpClientKeyRateLimiter: OtpClientKeyRateLimiter
) {
    private val logger = LoggerFactory.getLogger(IdentityController::class.java)

    @PostMapping("/guest")
    fun createGuest(
        request: HttpServletRequest,
        @RequestHeader("X-PIOS-Client-IP", required = false) proxiedClientIp: String?
    ): ResponseEntity<AuthResponse> = createGuestFromAddress(request.remoteAddr, proxiedClientIp)

    /** Transport-independent seam for the socket/proxy trust decision. */
    internal fun createGuestFromAddress(remoteAddress: String, proxiedClientIp: String?): ResponseEntity<AuthResponse> {
        // A public client can supply CF-Connecting-IP or X-Forwarded-For itself.
        // Only the same-host frontend proxy may override the socket address,
        // and that proxy replaces X-PIOS-Client-IP instead of forwarding it.
        val clientKey = if (isLoopbackAddress(remoteAddress)) {
            proxiedClientIp?.takeIf(::isLiteralIpAddress) ?: remoteAddress
        } else {
            remoteAddress
        }
        if (!guestIdentityRateLimiter.tryAcquire(clientKey)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build()
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(createGuestIdentityApplicationService.handle().toResponse())
    }

    private fun isLiteralIpAddress(value: String): Boolean =
        value.length in 2..45 && value.matches(Regex("[0-9A-Fa-f:.]+")) &&
            runCatching { InetAddress.getByName(value) }.isSuccess

    private fun isLoopbackAddress(value: String): Boolean =
        isLiteralIpAddress(value) && InetAddress.getByName(value).isLoopbackAddress

    /**
     * Observability fix (`docs/PIOS_PATH_TO_PUBLIC_LAUNCH.md` Part B6,
     * `docs/PIOS_PRODUCT_EVIDENCE.md` E-001): both catch branches below
     * previously logged nothing at all, so a real registration failure —
     * exactly what happened on 2026-09-05 — left zero trace to diagnose
     * from. Never logs the phone number itself: [PhoneAlreadyRegisteredException]'s
     * own `message` embeds it (see that class's own constructor), so this
     * logs a fixed line instead; [IllegalArgumentException]'s message here
     * is always [com.pios.identity.domain.Phone]'s own fixed format-hint
     * string, which carries no PII, so it is safe to log as-is.
     */
    @PostMapping("/register")
    fun register(@RequestBody request: RegisterIdentityRequest): ResponseEntity<AuthResponse> =
        try {
            val outcome = registerIdentityApplicationService.handle(
                RegisterIdentityCommand(request.phone, request.password)
            )
            ResponseEntity.status(HttpStatus.CREATED).body(outcome.toResponse())
        } catch (ex: PhoneAlreadyRegisteredException) {
            logger.info("Registration rejected: phone already registered")
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        } catch (ex: IllegalArgumentException) {
            logger.warn("Registration rejected: invalid input ({})", ex.message)
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

    @PostMapping("/me/register")
    fun upgradeGuest(
        @RequestBody request: RegisterIdentityRequest,
        @RequestHeader("Authorization", required = false) authorization: String?
    ): ResponseEntity<AuthResponse> {
        val verified = sessionTokenVerifier.verify(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!verified.guest) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build()
        }
        return try {
            ResponseEntity.ok(
                upgradeGuestIdentityApplicationService.handle(
                    UpgradeGuestIdentityCommand(verified.sub, request.phone, request.password)
                ).toResponse()
            )
        } catch (ex: PhoneAlreadyRegisteredException) {
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        } catch (ex: IdentityNotFoundException) {
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        } catch (ex: IllegalStateException) {
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        }
    }

    @GetMapping("/me")
    fun getOwnIdentity(
        @RequestHeader("Authorization", required = false) authorization: String?
    ): ResponseEntity<IdentityResponse> {
        val verified = sessionTokenVerifier.verify(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        return try {
            val identity = retrieveIdentityHandler.handle(IdentityId(verified.sub))
            if (identity.sessionGeneration != verified.sgen) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            }
            ResponseEntity.ok(identity.toResponse())
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
            val identity = retrieveIdentityHandler.handle(IdentityId(id))
            if (identity.sessionGeneration != verified.sgen) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            }
            ResponseEntity.ok(identity.toResponse())
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
    ): ResponseEntity<AuthResponse> {
        val verified = sessionTokenVerifier.verify(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (verified.sub != id) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        if (verified.guest) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        return try {
            val outcome = associateDriverApplicationService.handle(AssociateDriverCommand(id, request.driverId, verified.sgen))
            ResponseEntity.ok(outcome.toResponse())
        } catch (ex: IdentityNotFoundException) {
            ResponseEntity.notFound().build()
        } catch (ex: StaleSessionException) {
            // ADR-082 §8/D-03.3: this token was minted before a successful
            // recovery bumped the generation -- same status an
            // invalid/expired token already produces.
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
    }

    /**
     * ADR-082 (D-03) — `POST /v1/identities/recovery/request`. Always the
     * same generic response (D-03.7): whether the phone is unknown, a
     * guest's, unregistered, unverified-legacy, or genuinely
     * recovery-eligible is never observable from this endpoint's own
     * response. Rate limiting happens inside
     * [requestRecoveryApplicationService] itself (per-phone) precisely so
     * its own outcome never leaks into this response either.
     */
    @PostMapping("/recovery/request")
    fun requestRecovery(@RequestBody request: RecoveryRequestRequest): ResponseEntity<Map<String, Nothing>> {
        requestRecoveryApplicationService.handle(request.phone)
        // A real, empty JSON object, not a bodiless 202: apiClient.ts's
        // shared `request()` helper only special-cases 204 for a body-less
        // response and otherwise always attempts `response.json()` -- a
        // truly empty body on 202 would throw there. `{}` also matches
        // ADR-082 §6's own literal response shape ("202 {} -- always,
        // generic").
        return ResponseEntity.accepted().body(emptyMap())
    }

    /**
     * ADR-082 (D-03) — `POST /v1/identities/recovery/confirm`. A malformed
     * or too-short [RecoveryConfirmRequest.newPassword] surfaces as 400
     * (input validation on the caller's own supplied field, not a
     * brute-forceable secret -- see [ConfirmRecoveryApplicationService]'s
     * own KDoc). Every other failure reason -- unknown phone, no live
     * challenge, expired, exhausted attempts, wrong code -- collapses to
     * the same 401, never distinguished.
     */
    @PostMapping("/recovery/confirm")
    fun confirmRecovery(@RequestBody request: RecoveryConfirmRequest): ResponseEntity<AuthResponse> =
        try {
            when (val outcome = confirmRecoveryApplicationService.handle(
                ConfirmRecoveryCommand(request.phone, request.code, request.newPassword)
            )) {
                is ConfirmRecoveryOutcome.Success -> ResponseEntity.ok(
                    AuthResponse(outcome.identity.id.value, outcome.identity.driverId, outcome.token, outcome.expiresAt.toString())
                )
                ConfirmRecoveryOutcome.Failure -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            }
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }

    /**
     * ADR-082 Part 2 (D-03.2) — `POST /v1/identities/me/phone/verify/request`,
     * the legacy-enrolment request step. Bearer-gated, self-only, guest
     * tokens rejected -- the same three checks `associateDriver` already
     * performs, in the same order. Rate-limited by the same
     * socket-address/loopback-proxy-override derivation
     * `createGuestFromAddress` already established (`ADR-075` Decision 6),
     * reused here via [otpClientKeyRateLimiter] rather than
     * [guestIdentityRateLimiter] -- a separate budget, so exhausting one
     * never starves the other.
     */
    @PostMapping("/me/phone/verify/request")
    fun requestPhoneVerification(
        request: HttpServletRequest,
        @RequestHeader("X-PIOS-Client-IP", required = false) proxiedClientIp: String?,
        @RequestHeader("Authorization", required = false) authorization: String?
    ): ResponseEntity<Map<String, Nothing>> = requestPhoneVerificationFromAddress(request.remoteAddr, proxiedClientIp, authorization)

    /** Transport-independent seam for the socket/proxy trust decision, mirroring [createGuestFromAddress]'s own shape. */
    internal fun requestPhoneVerificationFromAddress(
        remoteAddress: String,
        proxiedClientIp: String?,
        authorization: String?
    ): ResponseEntity<Map<String, Nothing>> {
        val verified = sessionTokenVerifier.verify(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (verified.guest) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        val clientKey = clientKeyFrom(remoteAddress, proxiedClientIp)
        if (!otpClientKeyRateLimiter.tryAcquire(clientKey)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build()
        }
        return try {
            requestPhoneVerificationApplicationService.handle(IdentityId(verified.sub))
            // A real, empty JSON object on the success path -- see
            // requestRecovery's own comment for why apiClient.ts's shared
            // request() helper needs this rather than a bodiless 202.
            ResponseEntity.accepted().body(emptyMap())
        } catch (ex: IdentityNotFoundException) {
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        } catch (ex: PhoneAlreadyVerifiedException) {
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        }
    }

    /**
     * ADR-082 Part 2 (D-03.2) — `POST /v1/identities/me/phone/verify/confirm`.
     * Bearer-gated, self-only, guest tokens rejected. On a correct code,
     * `phoneVerifiedAt` is set; no credential change, no fresh token (the
     * caller's own `sub`/`drv`/`sgen` are unchanged by this action).
     */
    @PostMapping("/me/phone/verify/confirm")
    fun confirmPhoneVerification(
        @RequestBody request: PhoneVerifyConfirmRequest,
        @RequestHeader("Authorization", required = false) authorization: String?
    ): ResponseEntity<IdentityResponse> {
        val verified = sessionTokenVerifier.verify(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (verified.guest) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        return try {
            when (
                val outcome = confirmPhoneVerificationApplicationService.handle(
                    ConfirmPhoneVerificationCommand(verified.sub, request.code, verified.sgen)
                )
            ) {
                is ConfirmPhoneVerificationOutcome.Success -> ResponseEntity.ok(outcome.identity.toResponse())
                ConfirmPhoneVerificationOutcome.Failure -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            }
        } catch (ex: StaleSessionException) {
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
    }

    private fun clientKeyFrom(remoteAddress: String, proxiedClientIp: String?): String =
        if (isLoopbackAddress(remoteAddress)) {
            proxiedClientIp?.takeIf(::isLiteralIpAddress) ?: remoteAddress
        } else {
            remoteAddress
        }

    private fun Identity.toResponse() = IdentityResponse(id.value, phone?.value, driverId, phoneVerifiedAt != null)

    private fun RegisterIdentityOutcome.toResponse() =
        AuthResponse(identity.id.value, identity.driverId, token, expiresAt.toString())

    private fun AssociateDriverOutcome.toResponse() =
        AuthResponse(identity.id.value, identity.driverId, token, expiresAt.toString())

    private fun GuestIdentityOutcome.toResponse() =
        AuthResponse(identity.id.value, identity.driverId, token, expiresAt.toString(), guest = true)
}

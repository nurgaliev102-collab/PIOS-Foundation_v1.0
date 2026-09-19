package com.pios.dispatch.api

import com.pios.dispatch.application.DriverPushSubscription
import com.pios.dispatch.application.DriverPushSubscriptionRepository
import com.pios.dispatch.domain.DriverReference
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Driver Web Push subscription REST entry points (ADR-083, D-10),
 * mirroring [ProposalController]'s own conventions: no business logic
 * beyond transport-level conversion, [IllegalArgumentException] mapped to
 * HTTP 400, each outcome mapped directly in the method that produces it --
 * no `@ControllerAdvice`.
 *
 * Driver identity is taken only from [sessionTokenVerifier]'s own verified
 * `drv` claim, never from the request body -- a body carrying any
 * driver-identifying field is rejected 400, never silently ignored
 * (ADR-083 Part 8: "One driver cannot register or delete under another
 * driver's identity"). A token that verifies but names no driver at all
 * (`drv == null` -- a passenger-only token) is rejected 403 on every
 * endpoint here, mirroring [ProposalController]'s own wrong-driver 403
 * convention.
 *
 * No `Authorization: Basic` owner credential is accepted anywhere in this
 * controller, unlike most of [ProposalController] -- a push subscription is
 * a strictly per-device, per-driver concern the owner/coordinator has no
 * ratified reason to read, write, or delete on a driver's behalf (ADR-083
 * names no such use case).
 */
@RestController
@RequestMapping("/v1/driver-push-subscriptions")
class DriverPushSubscriptionController(
    private val subscriptionRepository: DriverPushSubscriptionRepository,
    private val sessionTokenVerifier: SessionTokenVerifier,
    @Value("\${pios.push.vapid.public-key:}") private val vapidPublicKey: String
) {

    /**
     * `404` when VAPID is not configured -- fail-closed (ADR-083 Part 8),
     * so `DriverHome.tsx`'s own opt-in control never renders in that case.
     */
    @GetMapping("/public-key")
    fun getPublicKey(
        @RequestHeader("Authorization", required = false) authorization: String? = null
    ): ResponseEntity<DriverPushPublicKeyResponse> {
        val verified = sessionTokenVerifier.verify(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (verified.drv == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        if (vapidPublicKey.isBlank()) {
            return ResponseEntity.notFound().build()
        }
        return ResponseEntity.ok(DriverPushPublicKeyResponse(vapidPublicKey))
    }

    /** UPSERT on [RegisterDriverPushSubscriptionRequest.endpoint] -- re-registering an existing endpoint under a different driver reassigns it (ADR-083 Part 6). */
    @PostMapping
    fun register(
        @RequestBody request: RegisterDriverPushSubscriptionRequest,
        @RequestHeader("Authorization", required = false) authorization: String? = null
    ): ResponseEntity<Void> {
        return try {
            val verified = sessionTokenVerifier.verify(authorization)
                ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            if (verified.drv == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
            }
            if (request.driverId != null || request.driverReference != null) {
                return ResponseEntity.badRequest().build()
            }
            val endpoint = request.endpoint
            val p256dh = request.keys?.p256dh
            val auth = request.keys?.auth
            if (endpoint.isNullOrBlank() || p256dh.isNullOrBlank() || auth.isNullOrBlank()) {
                return ResponseEntity.badRequest().build()
            }
            subscriptionRepository.upsert(
                DriverPushSubscription(
                    endpoint = endpoint,
                    driverReference = DriverReference(verified.drv),
                    p256dh = p256dh,
                    auth = auth
                )
            )
            ResponseEntity.noContent().build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
    }

    /**
     * Deletes only a row matching both [endpoint] and the caller's own
     * verified driver -- always `204`, whether or not a row matched
     * (ADR-083 Part 6: "no existence disclosure"). Another driver's
     * endpoint is never affected.
     */
    @DeleteMapping
    fun unregister(
        @RequestParam endpoint: String,
        @RequestHeader("Authorization", required = false) authorization: String? = null
    ): ResponseEntity<Void> {
        return try {
            val verified = sessionTokenVerifier.verify(authorization)
                ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            if (verified.drv == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
            }
            subscriptionRepository.deleteByDriverAndEndpoint(DriverReference(verified.drv), endpoint)
            ResponseEntity.noContent().build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
    }
}

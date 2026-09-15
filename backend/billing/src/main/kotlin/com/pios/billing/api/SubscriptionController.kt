package com.pios.billing.api

import com.pios.billing.application.RecordSubscriptionPeriodApplicationService
import com.pios.billing.application.RecordSubscriptionPeriodCommand
import com.pios.billing.application.RetrieveSubscriptionHandler
import com.pios.billing.domain.DriverReference
import com.pios.billing.domain.SubscriptionStatus
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * Billing's own two REST entry points (ADR-074 Part 3/Part 4) -- the only
 * two this module exposes, and the only two ADR-074 authorizes.
 *
 * `recordPeriod` (`POST /v1/subscriptions/{driverId}/periods`, ADR-074
 * Part 3 Decision 2) is the one write path: "A subscription period is
 * recorded by an authorized human operator, not by a provider callback."
 * Gated by [ownerCredentialGate] alone -- no `Bearer` session-token
 * alternative, unlike every other owner-gated endpoint in this codebase
 * that also accepts a driver's or passenger's own token, because no actor
 * other than an authorized operator is permitted to record a subscription
 * period at all today. A missing or incorrect credential is `401`,
 * mirroring [HealthController]'s own gate. `status` naming
 * [SubscriptionStatus.FREE] is rejected as `400` here, before the command
 * even reaches the application service, since `FREE` can never be a write
 * target (see that enum's own KDoc) -- a `IllegalArgumentException`/
 * `IllegalStateException` from deeper in the stack (an unknown enum name,
 * a malformed `currentPeriodEnd`, or a transition the domain's own table
 * forbids) is mapped to `400`/`409` respectively, the same split this
 * codebase already uses for `driver-management`/`dispatch`'s own
 * mutating endpoints.
 *
 * `getSubscription` (`GET /v1/subscriptions/{driverId}`, ADR-074 Part 4's
 * one authorized future seam) is the one read path: session-token-gated
 * to that same driver, mirroring
 * `com.pios.drivermanagement.api.DriverController.getMilestones`'s own
 * `401`/`403` shape exactly -- `401` with no valid token, `403` for a
 * token naming any driver other than the one in the path (including a
 * passenger-only token, whose own `drv` is `null`). Returns `FREE` with a
 * `null` `currentPeriodEnd` for a driver with no persisted record --
 * never a `404`, since "no record" is itself the correct, meaningful
 * answer (ADR-074 Part 2).
 *
 * Neither method is called by any other backend module, and neither is
 * wired into any Dispatch input, registration, availability, proposal,
 * assignment, or trip endpoint (ADR-074 Part 4's own forbidden-list) --
 * this controller is Billing's entire API surface beyond [HealthController].
 */
@RestController
@RequestMapping("/v1/subscriptions")
class SubscriptionController(
    private val recordSubscriptionPeriodApplicationService: RecordSubscriptionPeriodApplicationService,
    private val retrieveSubscriptionHandler: RetrieveSubscriptionHandler,
    private val ownerCredentialGate: OwnerCredentialGate,
    private val sessionTokenVerifier: SessionTokenVerifier
) {

    @PostMapping("/{driverId}/periods")
    fun recordPeriod(
        @PathVariable driverId: String,
        @RequestBody request: RecordSubscriptionPeriodRequest,
        @RequestHeader("Authorization", required = false) authorization: String? = null
    ): ResponseEntity<SubscriptionResponse> {
        if (!ownerCredentialGate.verify(authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        return try {
            val reference = DriverReference(driverId)
            val targetStatus = SubscriptionStatus.valueOf(request.status)
            if (targetStatus == SubscriptionStatus.FREE) {
                return ResponseEntity.badRequest().build()
            }
            val currentPeriodEnd = parseCurrentPeriodEnd(request.currentPeriodEnd)
            val subscription = recordSubscriptionPeriodApplicationService.handle(
                RecordSubscriptionPeriodCommand(reference, targetStatus, currentPeriodEnd, request.isTest)
            )
            ResponseEntity.ok(SubscriptionResponse(subscription.status.name, subscription.currentPeriodEnd?.toString()))
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        } catch (ex: IllegalStateException) {
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        }
    }

    @GetMapping("/{driverId}")
    fun getSubscription(
        @PathVariable driverId: String,
        @RequestHeader("Authorization", required = false) authorization: String? = null
    ): ResponseEntity<SubscriptionResponse> {
        return try {
            val reference = DriverReference(driverId)
            val verified = sessionTokenVerifier.verify(authorization)
                ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            if (verified.drv != driverId) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
            }
            val view = retrieveSubscriptionHandler.handle(reference)
            ResponseEntity.ok(SubscriptionResponse(view.status.name, view.currentPeriodEnd?.toString()))
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
    }

    private fun parseCurrentPeriodEnd(value: String?): Instant? {
        if (value == null) return null
        return try {
            Instant.parse(value)
        } catch (ex: DateTimeParseException) {
            throw IllegalArgumentException("currentPeriodEnd must be a valid ISO-8601 instant", ex)
        }
    }
}

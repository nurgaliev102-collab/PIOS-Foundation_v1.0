package com.pios.passengerexperience.api

import com.pios.passengerexperience.application.ConnectionNotFoundException
import com.pios.passengerexperience.application.CreateConnectionApplicationService
import com.pios.passengerexperience.application.CreateConnectionCommand
import com.pios.passengerexperience.application.PassengerConnectionView
import com.pios.passengerexperience.application.RemoveConnectionApplicationService
import com.pios.passengerexperience.application.RetrieveConnectionHandler
import com.pios.passengerexperience.application.RetrieveConnectionsForDriverHandler
import com.pios.passengerexperience.application.RetrieveConnectionsForPassengerHandler
import com.pios.passengerexperience.application.SetPrimaryConnectionApplicationService
import com.pios.passengerexperience.domain.Connection
import com.pios.passengerexperience.domain.ConnectionId
import com.pios.passengerexperience.domain.DriverReference
import com.pios.passengerexperience.domain.PassengerReference
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Passenger Experience's Connection REST entry points (Sprint 7B: Personal
 * Network Flow MVP; ADR-054, Circle of Trust; ADR-055, Session
 * Authentication), following the same "no business logic beyond
 * transport-level conversion" convention as every other module's own
 * controller.
 *
 * ADR-055 Decision 4 requires a valid `Bearer` session token
 * ([sessionTokenVerifier]) on all five endpoints — a missing, malformed,
 * or expired token is 401 everywhere, checked first, before any other
 * validation. Beyond that:
 * - `createConnection` additionally requires `request.passengerReference`
 *   to equal the token's `sub` claim (403 otherwise) — `driverId` stays
 *   an unverified reference, since this module does not own drivers.
 * - `getConnectionsForPassenger` requires the `passengerReference` query
 *   parameter to equal `sub` (403 otherwise).
 * - `getConnectionsForDriver` requires the `driverId` query parameter to
 *   equal the token's `drv` claim (403 otherwise) — a passenger-only
 *   token (`drv == null`) can never match any `driverId` string.
 * - `setPrimaryConnection` and `removeConnection` look the connection up
 *   first ([retrieveConnectionHandler]) and compare its stored
 *   `passengerReference` against `sub`. A mismatch (or a connection that
 *   does not exist) is 404 for `setPrimaryConnection`, deliberately not
 *   403 — a 403 would confirm a `connectionId` exists to a non-owner
 *   (ADR-055 Decision 4's own note). `removeConnection` still returns 204
 *   for a mismatch, deleting nothing, preserving ADR-054's unconditional
 *   idempotent-DELETE contract.
 *
 * `createConnection` returns 201 the first time a given
 * `(driverId, passengerReference)` pair is recorded, and 200 (not 409) on
 * every subsequent call with the same pair -- opening an invitation link
 * more than once must stay idempotent, per this sprint's own explicit
 * requirement (see [CreateConnectionApplicationService]'s own KDoc).
 *
 * A blank `driverId`/`passengerReference` surfaces as
 * [IllegalArgumentException] (HTTP 400) -- neither reference is verified
 * to exist against Driver Management, mirroring Dispatch's own
 * `DriverReference`/`OrderReference` convention: this module does not own
 * that information.
 *
 * The passenger-side surface (ADR-054, Circle of Trust) is additive
 * throughout: `getConnectionsForDriver` and `getConnectionsForPassenger`
 * are disambiguated as siblings on the same collection via each
 * `@GetMapping`'s own `params` filter (Spring routes on the presence of
 * `driverId` vs. `passengerReference`), and `getConnectionsForDriver`'s
 * own response shape (`ConnectionResponse`, no `isPrimary`) is unchanged
 * -- ADR-054 Part 4: "the driver-facing response gains nothing". A
 * `connectionId` that does not identify any known connection surfaces as
 * [ConnectionNotFoundException], mapped to HTTP 404, mirroring
 * `com.pios.dispatch.api.ProposalController`'s own
 * `ProposalNotFoundException` convention. `removeConnection` is
 * idempotent by design and always returns 204, matching standard DELETE
 * semantics ([RemoveConnectionApplicationService]'s own KDoc).
 */
@RestController
@RequestMapping("/v1/connections")
class ConnectionController(
    private val createConnectionApplicationService: CreateConnectionApplicationService,
    private val retrieveConnectionsForDriverHandler: RetrieveConnectionsForDriverHandler,
    private val retrieveConnectionsForPassengerHandler: RetrieveConnectionsForPassengerHandler,
    private val setPrimaryConnectionApplicationService: SetPrimaryConnectionApplicationService,
    private val removeConnectionApplicationService: RemoveConnectionApplicationService,
    private val retrieveConnectionHandler: RetrieveConnectionHandler,
    private val sessionTokenVerifier: SessionTokenVerifier
) {

    @PostMapping
    fun createConnection(
        @RequestBody request: CreateConnectionRequest,
        @RequestHeader("Authorization", required = false) authorization: String?
    ): ResponseEntity<CreateConnectionResponse> {
        val verified = sessionTokenVerifier.verify(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (request.passengerReference != verified.sub) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        return try {
            val outcome = createConnectionApplicationService.handle(
                CreateConnectionCommand(DriverReference(request.driverId), PassengerReference(request.passengerReference))
            )
            val body = CreateConnectionResponse(
                outcome.connection.driverId.driverId,
                outcome.connection.passengerReference.passengerId,
                outcome.connection.id.value
            )
            ResponseEntity.status(if (outcome.created) HttpStatus.CREATED else HttpStatus.OK).body(body)
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
    }

    @GetMapping(params = ["driverId"])
    fun getConnectionsForDriver(
        @RequestParam driverId: String,
        @RequestHeader("Authorization", required = false) authorization: String?
    ): ResponseEntity<List<ConnectionResponse>> {
        val verified = sessionTokenVerifier.verify(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (driverId != verified.drv) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        return try {
            val connections = retrieveConnectionsForDriverHandler.handle(DriverReference(driverId))
                .map { ConnectionResponse(it.passengerReference.passengerId, it.createdAt.toString()) }
            ResponseEntity.ok(connections)
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
    }

    @GetMapping(params = ["passengerReference"])
    fun getConnectionsForPassenger(
        @RequestParam passengerReference: String,
        @RequestHeader("Authorization", required = false) authorization: String?
    ): ResponseEntity<List<PassengerConnectionResponse>> {
        val verified = sessionTokenVerifier.verify(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (passengerReference != verified.sub) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        return try {
            val views = retrieveConnectionsForPassengerHandler.handle(PassengerReference(passengerReference))
                .map { it.toResponse() }
            ResponseEntity.ok(views)
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
    }

    @PostMapping("/{connectionId}/primary")
    fun setPrimaryConnection(
        @PathVariable connectionId: String,
        @RequestHeader("Authorization", required = false) authorization: String?
    ): ResponseEntity<PassengerConnectionResponse> {
        val verified = sessionTokenVerifier.verify(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        return try {
            val id = ConnectionId(connectionId)
            val existing = retrieveConnectionHandler.handle(id)
            if (existing == null || existing.passengerReference.passengerId != verified.sub) {
                return ResponseEntity.notFound().build()
            }
            val connection = setPrimaryConnectionApplicationService.handle(id)
            ResponseEntity.ok(connection.toResponse(isPrimary = true))
        } catch (ex: ConnectionNotFoundException) {
            ResponseEntity.notFound().build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
    }

    @DeleteMapping("/{connectionId}")
    fun removeConnection(
        @PathVariable connectionId: String,
        @RequestHeader("Authorization", required = false) authorization: String?
    ): ResponseEntity<Void> {
        val verified = sessionTokenVerifier.verify(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        return try {
            val id = ConnectionId(connectionId)
            val existing = retrieveConnectionHandler.handle(id)
            // A non-owner's DELETE still returns 204 and deletes nothing --
            // ADR-055 Decision 4 preserves ADR-054's unconditional
            // idempotent-DELETE contract deliberately, not as an oversight.
            if (existing != null && existing.passengerReference.passengerId == verified.sub) {
                removeConnectionApplicationService.handle(id)
            }
            ResponseEntity.noContent().build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
    }

    private fun PassengerConnectionView.toResponse(): PassengerConnectionResponse =
        connection.toResponse(isPrimary)

    private fun Connection.toResponse(isPrimary: Boolean): PassengerConnectionResponse =
        PassengerConnectionResponse(id.value, driverId.driverId, createdAt.toString(), isPrimary)
}

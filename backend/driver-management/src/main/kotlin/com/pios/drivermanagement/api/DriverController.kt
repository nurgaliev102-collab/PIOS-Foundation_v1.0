package com.pios.drivermanagement.api

import com.pios.drivermanagement.application.CreateDriverApplicationService
import com.pios.drivermanagement.application.CreateDriverCommand
import com.pios.drivermanagement.application.DeclareAvailabilityCommand
import com.pios.drivermanagement.application.DriverAlreadyExistsException
import com.pios.drivermanagement.application.DriverAvailabilityApplicationService
import com.pios.drivermanagement.application.DriverNotFoundException
import com.pios.drivermanagement.application.DriverRepository
import com.pios.drivermanagement.application.RetrieveDriverAvailabilityHandler
import com.pios.drivermanagement.application.RetrieveDriverClientsHandler
import com.pios.drivermanagement.application.RetrieveDriverMilestonesHandler
import com.pios.drivermanagement.application.UpdateLongDistancePreferenceApplicationService
import com.pios.drivermanagement.application.UpdateLongDistancePreferenceCommand
import com.pios.drivermanagement.application.UpdateVehicleApplicationService
import com.pios.drivermanagement.application.UpdateVehicleCommand
import com.pios.drivermanagement.domain.Availability
import com.pios.drivermanagement.domain.Driver
import com.pios.drivermanagement.domain.DriverId
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
 * Driver Management's Retrieve Driver Availability / Declare Availability
 * REST entry points (ADR-004, Resource-Oriented API Style; ADR-028,
 * Production Integration Transport Decision -- direct, request-driven
 * interaction goes through REST).
 *
 * `getDriver`/`listDrivers` are read-only: delegate to the already-existing
 * [RetrieveDriverAvailabilityHandler] and add no business logic of their
 * own, mirroring Order Management's `OrderSubmissionController` own
 * convention of mapping an application-layer outcome directly to an HTTP
 * status in this method, not through a `@ControllerAdvice`, so it stays
 * visible and testable without a Spring MVC test context.
 *
 * `declareAvailability` (Sprint FR-002: Driver Availability) is the first
 * write path this controller exposes: delegates to the already-existing
 * [DriverAvailabilityApplicationService.handle], adding no business logic
 * of its own either. Only the driver identified by the path may change
 * their own availability — this endpoint carries no notion of any other
 * actor doing so on their behalf (ADR-005/019: Driver Management alone
 * records a driver's availability state).
 *
 * A blank [driverId] surfaces as [DriverId]'s own `require` check; an
 * unrecognized `availability` value surfaces as [Availability.valueOf]'s
 * own `IllegalArgumentException` -- both mapped here to HTTP 400. A
 * [driverId] that does not identify any known driver surfaces as
 * [DriverNotFoundException], mapped to HTTP 404 -- the first document to
 * fix a concrete status code for this contract (API_SPECIFICATION.md
 * Section 10 leaves the choice to the implementation).
 *
 * `listDrivers` returns every driver, unfiltered and unordered -- Sprint
 * FR-002 explicitly excludes filtering, search, and pagination.
 *
 * `createDriver` (Sprint 3A: MVR Pilot Enablement) is the first way to
 * bring a new Driver record into existence at all -- `declareAvailability`
 * has always required one to already exist. Delegates to
 * [CreateDriverApplicationService.handle], adding no business logic of its
 * own. Returns 201 Created with the new driver's id and its (unconditional)
 * default availability; a [driverId] that already identifies a saved
 * Driver surfaces as [DriverAlreadyExistsException], mapped to 409
 * Conflict, the same status Dispatch's own `ProposalController` uses for
 * its own conflicting-creation cases.
 *
 * As of `8206ff3` (2026-09-16, the ADR-076 hardening pass), this endpoint
 * is **no longer unauthenticated** -- the paragraph that used to say so is
 * corrected here rather than left to contradict the code below it. Three
 * credential schemes are accepted, checked in this order:
 *
 * 1. A `PiosTest <token>` credential (ADR-079: Test-Data Credential for
 *    Synthetic (`isTest`) Driver Creation), checked via
 *    [testDataCredentialGate] and **never** routed through
 *    [ownerCredentialGate] (its own independent failure window matters --
 *    see that gate's own KDoc). Authorizes exactly one thing: creating a
 *    driver with [CreateDriverRequest.isTest] `true`; `isTest == false`
 *    under this credential is rejected with 403, and an unconfigured or
 *    invalid credential is rejected with 401. An
 *    [CreateDriverRequest.invitedByDriverId] naming a driver that is not
 *    itself `isTest` is confined to the test lane -- see
 *    [confineInvitedByToTestLane]'s own KDoc (ADR-079 Decision 5).
 * 2. An owner `Basic` credential (checked via [ownerCredentialGate]) --
 *    unchanged from before this ADR, and may create an `isTest` driver.
 * 3. Otherwise, a `Bearer` session token whose own `sub` names this exact
 *    [driverId] and whose `drv`, if present, also matches -- i.e. an
 *    already-authenticated identity self-registering as this driver, the
 *    same self-only shape `declareAvailability` below already requires. A
 *    guest token (`verified.guest`) is rejected with 403. **Creating an
 *    `isTest` driver through this path is forbidden regardless of which
 *    `Bearer` token is presented** -- a self-registering, non-owner,
 *    non-test-credential caller cannot set [CreateDriverRequest.isTest] at
 *    all, closing the gap Task 24's LOW/informational classification of
 *    this endpoint had left open once `isTest` existed as a
 *    caller-controlled flag.
 *
 * No unauthenticated path remains.
 *
 * ## Task 25 (Orders Cancellation & Driver Availability Security Remediation)
 *
 * `declareAvailability` now requires a `Bearer` session token whose own
 * `drv` equals the path's own [driverId] -- closing the gap Task 24's own
 * audit found (`docs/PIOS_TAXI_TASK_24_REMAINING_MUTATION_API_SECURITY_AUDIT.md`).
 * [sessionTokenVerifier] is a new file in this module (no copy existed
 * before this task), but not a new mechanism -- the fifth replica of the
 * identical class already present in `dispatch`/`order-management`/
 * `passenger-experience`/`identity` (ADR-055 Decision 1's own "replicate,
 * don't share" rule, `MODULE_STRUCTURE.md` Section 4). The check itself
 * mirrors [com.pios.dispatch.api.ProposalController.acceptProposal]'s own
 * identical shape (Task 21): 401 with no valid token, 403 for a
 * differently-named or passenger-only (`drv == null`) token, checked
 * before the existing 404/400 mapping below, exactly the same ordering
 * that controller already established.
 *
 * ## Growth Loops TZ v1, Phase 2 (docs/PIOS_GROWTH_LOOPS_TZ_V1.md Section 2)
 *
 * `getMilestones` exposes a driver's own completed-ride count and
 * week-streak — private business data about that driver, not a public
 * fact the way `displayName` (read by `getDriver` for the link-preview
 * feature, Phase 1) is. Requires the same `Bearer` session-token check as
 * `declareAvailability`: 401 with no valid token, 403 for any token not
 * naming this exact [driverId].
 *
 * `getMilestones` also carries [DriverMilestonesResponse.invitedDriversCount]
 * (ADR-073 Part 4) -- computed live from [driverRepository], the same
 * private, `Bearer`-gated surface this endpoint already is. No other
 * endpoint on this controller, and no field on [DriverResponse], ever
 * exposes this count -- ADR-073 Part 4's own "never passenger-facing"
 * constraint.
 *
 * ## Server-side "Мой бизнес" read model (`docs/PIOS_TAXI_RELATIONSHIP_MODEL_EVALUATION.md` Part 5)
 *
 * `getClients` exposes, per passenger, this driver's own ride count,
 * last-ride timestamp, and repeat-client flag — private business data about
 * this driver's own passengers, not a public fact. Requires the identical
 * `Bearer`/`drv == driverId` check `getMilestones` already uses (401 with no
 * valid token, 403 for any token not naming this exact [driverId]) — this
 * read model lives entirely in Driver Management, is derived only from
 * `AssignmentCompleted`/`OrderSubmitted` (already consumed by this module),
 * and adds no `Connection` lifecycle of any kind (that half of Relationship
 * depth remains out of scope, per the evaluation doc's own Part 5).
 *
 * ## Update Vehicle (PIOS Group and Long-Distance Rides Roadmap, Stage 1)
 *
 * `updateVehicle` is a driver's own declaration of their car's details --
 * requires the identical `Bearer`/`drv == driverId` check as
 * `declareAvailability` (401/403 in the same order), since only the driver
 * identified by the path may change their own vehicle record. Unlike
 * `getMilestones`, this fact is public: `getDriver`/`listDrivers` already
 * surface it unauthenticated, alongside `displayName`, for the same
 * invite-preview reason (see [DriverResponse]'s own KDoc).
 *
 * ## Update Long-Distance Preference (PIOS Group and Long-Distance Rides
 * Roadmap, Stage 3)
 *
 * `updateLongDistancePreference` is a driver's own declaration of
 * willingness to take long-distance trips -- requires the identical
 * `Bearer`/`drv == driverId` check as `updateVehicle` (401/403 in the same
 * order), since only the driver identified by the path may change their
 * own preference. Public on `getDriver`/`listDrivers`, the same reasoning
 * as `updateVehicle`.
 *
 * ## ADR-085 (D-11.C1): `getDriver` no longer discloses the plate to an
 * anonymous or non-matching caller
 *
 * `getDriver` remains reachable with **no credential at all** -- the
 * public invite-preview flow (`invitationSource.ts`) structurally depends
 * on that staying true, and this change does not add any general
 * authentication requirement to this endpoint (ADR-085 Part 5). It now
 * optionally inspects an `Authorization` header, if present, purely to
 * decide whether [DriverResponse.vehiclePlateNumber] is included: a
 * `Bearer` token whose own verified `drv` equals the requested
 * [driverId] gets the plate, exactly as the `Bearer`-gated write paths
 * already do; anyone else (no header, an invalid/expired token, a guest
 * token, or a token naming a *different* driver) gets every other field
 * unchanged but `vehiclePlateNumber` as `null`. Make/model/colour/
 * `acceptsLongDistanceTrips`/`displayName`/`availability` are unaffected --
 * only the plate is caller-aware. This closes a live, credential-free
 * disclosure of a real person's vehicle registration number without
 * breaking the driver's own vehicle-edit screen (`DriverHome.tsx`), which
 * reads its current vehicle fields from this same endpoint and would
 * otherwise silently erase its own stored plate on the next unrelated
 * vehicle edit (`Driver.updateVehicle` replaces all five fields together)
 * -- see ADR-085's own "defect this ADR's implementation must not
 * introduce" section.
 */
@RestController
@RequestMapping("/v1/drivers")
class DriverController(
    private val retrieveDriverAvailabilityHandler: RetrieveDriverAvailabilityHandler,
    private val driverAvailabilityApplicationService: DriverAvailabilityApplicationService,
    private val createDriverApplicationService: CreateDriverApplicationService,
    private val retrieveDriverMilestonesHandler: RetrieveDriverMilestonesHandler,
    private val retrieveDriverClientsHandler: RetrieveDriverClientsHandler,
    private val updateVehicleApplicationService: UpdateVehicleApplicationService,
    private val updateLongDistancePreferenceApplicationService: UpdateLongDistancePreferenceApplicationService,
    private val sessionTokenVerifier: SessionTokenVerifier,
    private val ownerCredentialGate: OwnerCredentialGate,
    private val driverRepository: DriverRepository,
    private val testDataCredentialGate: TestDataCredentialGate
) {

    /** Direct application seam retained for focused unit tests; it is not an HTTP endpoint. */
    internal fun createDriver(request: CreateDriverRequest): ResponseEntity<DriverResponse> = persistDriver(request)

    @PostMapping
    fun createDriver(
        @RequestBody request: CreateDriverRequest,
        @RequestHeader("Authorization", required = false) authorization: String?
    ): ResponseEntity<DriverResponse> {
        return try {
            val testDataRequest = authorization?.startsWith("PiosTest ") == true
            val ownerRequest = authorization?.startsWith("Basic ") == true
            if (testDataRequest) {
                if (!testDataCredentialGate.verify(authorization)) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
                }
                if (!request.isTest) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
                }
                return persistDriver(request.copy(invitedByDriverId = confineInvitedByToTestLane(request.invitedByDriverId)))
            } else if (ownerRequest) {
                if (!ownerCredentialGate.verify(authorization)) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
                }
            } else {
                val verified = sessionTokenVerifier.verify(authorization)
                    ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
                if (verified.guest) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
                }
                if (verified.sub != request.driverId || (verified.drv != null && verified.drv != request.driverId)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
                }
                if (request.isTest) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
                }
            }
            persistDriver(request)
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
    }

    /**
     * ADR-079 Decision 5: under the `PiosTest` credential, an
     * [CreateDriverRequest.invitedByDriverId] naming a driver that is not
     * itself `isTest` degrades to `null` rather than failing the
     * registration -- `countInvitedBy` (`PostgreSQLDriverRepository`) has no
     * `is_test` filter, so a test-lane row must never inflate a real
     * driver's own private count. A blank or self-referencing value is left
     * unchanged here; [CreateDriverApplicationService] already degrades
     * both to `null` on every credential path (ADR-073 Part 2).
     */
    private fun confineInvitedByToTestLane(invitedByDriverId: String?): String? {
        if (invitedByDriverId.isNullOrBlank()) {
            return invitedByDriverId
        }
        val inviterIsTest = try {
            driverRepository.findById(DriverId(invitedByDriverId))?.isTest == true
        } catch (ex: IllegalArgumentException) {
            false
        }
        return if (inviterIsTest) invitedByDriverId else null
    }

    @GetMapping("/{driverId}")
    fun getDriver(
        @PathVariable driverId: String,
        @RequestHeader("Authorization", required = false) authorization: String? = null
    ): ResponseEntity<DriverResponse> =
        try {
            val driver = retrieveDriverAvailabilityHandler.handle(DriverId(driverId))
            val verified = sessionTokenVerifier.verify(authorization)
            val isOwnRecord = verified != null && verified.drv == driverId
            ResponseEntity.ok(driver.toResponse(includePlate = isOwnRecord))
        } catch (ex: DriverNotFoundException) {
            ResponseEntity.notFound().build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }

    @GetMapping
    fun listDrivers(
        @RequestHeader("Authorization", required = false) authorization: String?
    ): ResponseEntity<List<DriverResponse>> =
        if (ownerCredentialGate.verify(authorization)) {
            ResponseEntity.ok(retrieveDriverAvailabilityHandler.handleAll().map { it.toResponse() })
        } else {
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

    /** Direct application seam retained for focused unit tests; it is not an HTTP endpoint. */
    internal fun listDrivers(): ResponseEntity<List<DriverResponse>> =
        ResponseEntity.ok(retrieveDriverAvailabilityHandler.handleAll().map { it.toResponse() })

    private fun persistDriver(request: CreateDriverRequest): ResponseEntity<DriverResponse> =
        try {
            val driver = createDriverApplicationService.handle(
                CreateDriverCommand(DriverId(request.driverId), request.displayName, request.isTest, request.invitedByDriverId)
            )
            ResponseEntity.status(HttpStatus.CREATED).body(driver.toResponse())
        } catch (ex: DriverAlreadyExistsException) {
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }

    @PostMapping("/{driverId}/vehicle")
    fun updateVehicle(
        @PathVariable driverId: String,
        @RequestBody request: UpdateVehicleRequest,
        @RequestHeader("Authorization", required = false) authorization: String? = null
    ): ResponseEntity<DriverResponse> {
        return try {
            val id = DriverId(driverId)
            val verified = sessionTokenVerifier.verify(authorization)
                ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            if (verified.drv != driverId) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
            }
            val driver = updateVehicleApplicationService.handle(
                UpdateVehicleCommand(id, request.make, request.model, request.color, request.plateNumber, request.seatCount)
            )
            ResponseEntity.ok(driver.toResponse())
        } catch (ex: DriverNotFoundException) {
            ResponseEntity.notFound().build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
    }

    @PostMapping("/{driverId}/long-distance-preference")
    fun updateLongDistancePreference(
        @PathVariable driverId: String,
        @RequestBody request: UpdateLongDistancePreferenceRequest,
        @RequestHeader("Authorization", required = false) authorization: String? = null
    ): ResponseEntity<DriverResponse> {
        return try {
            val id = DriverId(driverId)
            val verified = sessionTokenVerifier.verify(authorization)
                ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            if (verified.drv != driverId) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
            }
            val driver = updateLongDistancePreferenceApplicationService.handle(
                UpdateLongDistancePreferenceCommand(id, request.accepts)
            )
            ResponseEntity.ok(driver.toResponse())
        } catch (ex: DriverNotFoundException) {
            ResponseEntity.notFound().build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
    }

    /**
     * ADR-085 (D-11.C1, Driver Vehicle Plate Visibility): [includePlate]
     * defaults to `true` -- every call site except [getDriver]'s
     * unauthenticated/public path always wants the full record (the owner-
     * `Basic` list, every `Bearer`-gated self-write response, and
     * `getDriver` itself when the caller's own verified session names this
     * exact driver). Only [getDriver] passes `false`, for a caller that is
     * either anonymous or presenting a token that does not name this
     * `driverId` -- see that method's own KDoc.
     */
    private fun Driver.toResponse(includePlate: Boolean = true): DriverResponse = DriverResponse(
        id.value,
        availability.name,
        displayName,
        createdAt?.toString(),
        isTest,
        vehicleMake,
        vehicleModel,
        vehicleColor,
        if (includePlate) vehiclePlateNumber else null,
        vehicleSeatCount,
        acceptsLongDistanceTrips
    )

    @GetMapping("/{driverId}/milestones")
    fun getMilestones(
        @PathVariable driverId: String,
        @RequestHeader("Authorization", required = false) authorization: String? = null
    ): ResponseEntity<DriverMilestonesResponse> {
        return try {
            val id = DriverId(driverId)
            val verified = sessionTokenVerifier.verify(authorization)
                ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            if (verified.drv != driverId) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
            }
            val milestones = retrieveDriverMilestonesHandler.handle(id)
            ResponseEntity.ok(
                DriverMilestonesResponse(
                    milestones.driverId.value,
                    milestones.completedRidesCount,
                    milestones.currentStreakWeeks,
                    milestones.repeatClientsCount,
                    milestones.totalStatedEarnings,
                    milestones.unpricedRidesCount,
                    driverRepository.countInvitedBy(id)
                )
            )
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
    }

    @GetMapping("/{driverId}/clients")
    fun getClients(
        @PathVariable driverId: String,
        @RequestHeader("Authorization", required = false) authorization: String? = null
    ): ResponseEntity<List<DriverClientResponse>> {
        return try {
            val id = DriverId(driverId)
            val verified = sessionTokenVerifier.verify(authorization)
                ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            if (verified.drv != driverId) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
            }
            val clients = retrieveDriverClientsHandler.handle(id)
            ResponseEntity.ok(
                clients.map {
                    DriverClientResponse(
                        it.passengerReference,
                        it.rideCount,
                        it.lastRideAt?.toString(),
                        it.isRepeat
                    )
                }
            )
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
    }

    @PostMapping("/{driverId}/availability")
    fun declareAvailability(
        @PathVariable driverId: String,
        @RequestBody request: DeclareAvailabilityRequest,
        @RequestHeader("Authorization", required = false) authorization: String? = null
    ): ResponseEntity<DriverResponse> {
        return try {
            val id = DriverId(driverId)
            val verified = sessionTokenVerifier.verify(authorization)
                ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            if (verified.drv != driverId) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
            }
            val availability = Availability.valueOf(request.availability)
            driverAvailabilityApplicationService.handle(DeclareAvailabilityCommand(id, availability))
            ResponseEntity.ok(DriverResponse(id.value, availability.name))
        } catch (ex: DriverNotFoundException) {
            ResponseEntity.notFound().build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
    }
}

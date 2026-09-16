package com.pios.drivermanagement.api

import com.pios.drivermanagement.application.CreateDriverApplicationService
import com.pios.drivermanagement.application.CreateDriverCommand
import com.pios.drivermanagement.application.DeclareAvailabilityCommand
import com.pios.drivermanagement.application.DriverAlreadyExistsException
import com.pios.drivermanagement.application.DriverAvailabilityApplicationService
import com.pios.drivermanagement.application.DriverNotFoundException
import com.pios.drivermanagement.application.DriverRepository
import com.pios.drivermanagement.application.RetrieveDriverAvailabilityHandler
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
 * its own conflicting-creation cases. Left unauthenticated by this
 * controller, including after Task 25 (below) -- Task 24's own audit
 * classified this endpoint LOW/informational, not a remediation target:
 * it is a pre-authentication "first contact" action creating a brand-new
 * resource named by a caller-generated, unguessable UUID, the same
 * legitimate shape `IdentityController.register` already has, not a
 * mutation of an existing, already-owned resource.
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
 */
@RestController
@RequestMapping("/v1/drivers")
class DriverController(
    private val retrieveDriverAvailabilityHandler: RetrieveDriverAvailabilityHandler,
    private val driverAvailabilityApplicationService: DriverAvailabilityApplicationService,
    private val createDriverApplicationService: CreateDriverApplicationService,
    private val retrieveDriverMilestonesHandler: RetrieveDriverMilestonesHandler,
    private val updateVehicleApplicationService: UpdateVehicleApplicationService,
    private val updateLongDistancePreferenceApplicationService: UpdateLongDistancePreferenceApplicationService,
    private val sessionTokenVerifier: SessionTokenVerifier,
    private val ownerCredentialGate: OwnerCredentialGate,
    private val driverRepository: DriverRepository
) {

    /** Direct application seam retained for focused unit tests; it is not an HTTP endpoint. */
    internal fun createDriver(request: CreateDriverRequest): ResponseEntity<DriverResponse> = persistDriver(request)

    @PostMapping
    fun createDriver(
        @RequestBody request: CreateDriverRequest,
        @RequestHeader("Authorization", required = false) authorization: String?
    ): ResponseEntity<DriverResponse> {
        return try {
            val ownerRequest = authorization?.startsWith("Basic ") == true
            if (ownerRequest) {
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

    @GetMapping("/{driverId}")
    fun getDriver(@PathVariable driverId: String): ResponseEntity<DriverResponse> =
        try {
            val driver = retrieveDriverAvailabilityHandler.handle(DriverId(driverId))
            ResponseEntity.ok(driver.toResponse())
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

    private fun Driver.toResponse(): DriverResponse = DriverResponse(
        id.value,
        availability.name,
        displayName,
        createdAt?.toString(),
        isTest,
        vehicleMake,
        vehicleModel,
        vehicleColor,
        vehiclePlateNumber,
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

package com.pios.drivermanagement.api

import com.pios.drivermanagement.application.CreateDriverApplicationService
import com.pios.drivermanagement.application.CreateDriverCommand
import com.pios.drivermanagement.application.DeclareAvailabilityCommand
import com.pios.drivermanagement.application.DriverAlreadyExistsException
import com.pios.drivermanagement.application.DriverAvailabilityApplicationService
import com.pios.drivermanagement.application.DriverNotFoundException
import com.pios.drivermanagement.application.RetrieveDriverAvailabilityHandler
import com.pios.drivermanagement.domain.Availability
import com.pios.drivermanagement.domain.DriverId
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
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
 */
@RestController
@RequestMapping("/v1/drivers")
class DriverController(
    private val retrieveDriverAvailabilityHandler: RetrieveDriverAvailabilityHandler,
    private val driverAvailabilityApplicationService: DriverAvailabilityApplicationService,
    private val createDriverApplicationService: CreateDriverApplicationService
) {

    @PostMapping
    fun createDriver(@RequestBody request: CreateDriverRequest): ResponseEntity<DriverResponse> =
        try {
            val driver = createDriverApplicationService.handle(
                CreateDriverCommand(DriverId(request.driverId), request.displayName, request.isTest)
            )
            ResponseEntity.status(HttpStatus.CREATED)
                .body(DriverResponse(driver.id.value, driver.availability.name, driver.displayName, driver.createdAt?.toString(), driver.isTest))
        } catch (ex: DriverAlreadyExistsException) {
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }

    @GetMapping("/{driverId}")
    fun getDriver(@PathVariable driverId: String): ResponseEntity<DriverResponse> =
        try {
            val driver = retrieveDriverAvailabilityHandler.handle(DriverId(driverId))
            ResponseEntity.ok(DriverResponse(driver.id.value, driver.availability.name, driver.displayName, driver.createdAt?.toString(), driver.isTest))
        } catch (ex: DriverNotFoundException) {
            ResponseEntity.notFound().build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }

    @GetMapping
    fun listDrivers(): ResponseEntity<List<DriverResponse>> =
        ResponseEntity.ok(
            retrieveDriverAvailabilityHandler.handleAll()
                .map { DriverResponse(it.id.value, it.availability.name, it.displayName, it.createdAt?.toString(), it.isTest) }
        )

    @PostMapping("/{driverId}/availability")
    fun declareAvailability(
        @PathVariable driverId: String,
        @RequestBody request: DeclareAvailabilityRequest
    ): ResponseEntity<DriverResponse> =
        try {
            val id = DriverId(driverId)
            val availability = Availability.valueOf(request.availability)
            driverAvailabilityApplicationService.handle(DeclareAvailabilityCommand(id, availability))
            ResponseEntity.ok(DriverResponse(id.value, availability.name))
        } catch (ex: DriverNotFoundException) {
            ResponseEntity.notFound().build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
}

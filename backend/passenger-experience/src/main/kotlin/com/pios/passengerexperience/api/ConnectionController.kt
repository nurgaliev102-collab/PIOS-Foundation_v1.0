package com.pios.passengerexperience.api

import com.pios.passengerexperience.application.CreateConnectionApplicationService
import com.pios.passengerexperience.application.CreateConnectionCommand
import com.pios.passengerexperience.application.RetrieveConnectionsForDriverHandler
import com.pios.passengerexperience.domain.DriverReference
import com.pios.passengerexperience.domain.PassengerReference
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Passenger Experience's Connection REST entry points (Sprint 7B: Personal
 * Network Flow MVP), following the same "no business logic beyond
 * transport-level conversion" convention as every other module's own
 * controller.
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
 */
@RestController
@RequestMapping("/v1/connections")
class ConnectionController(
    private val createConnectionApplicationService: CreateConnectionApplicationService,
    private val retrieveConnectionsForDriverHandler: RetrieveConnectionsForDriverHandler
) {

    @PostMapping
    fun createConnection(@RequestBody request: CreateConnectionRequest): ResponseEntity<CreateConnectionResponse> =
        try {
            val outcome = createConnectionApplicationService.handle(
                CreateConnectionCommand(DriverReference(request.driverId), PassengerReference(request.passengerReference))
            )
            val body = CreateConnectionResponse(
                outcome.connection.driverId.driverId,
                outcome.connection.passengerReference.passengerId
            )
            ResponseEntity.status(if (outcome.created) HttpStatus.CREATED else HttpStatus.OK).body(body)
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }

    @GetMapping
    fun getConnectionsForDriver(@RequestParam driverId: String): ResponseEntity<List<ConnectionResponse>> =
        try {
            val connections = retrieveConnectionsForDriverHandler.handle(DriverReference(driverId))
                .map { ConnectionResponse(it.passengerReference.passengerId) }
            ResponseEntity.ok(connections)
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
}

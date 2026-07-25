package com.pios.networkmanagement.api

import com.pios.networkmanagement.application.CreateConnectionApplicationService
import com.pios.networkmanagement.application.CreateConnectionCommand
import com.pios.networkmanagement.application.PersonNotFoundException
import com.pios.networkmanagement.domain.ConnectionType
import com.pios.networkmanagement.domain.PersonId
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Network Management's Connection REST entry point (Sprint 7A: PIOS
 * Network Foundation) — the direct creation path; `GET /v1/persons/{id}/connections`
 * lives on [PersonController] instead, per the founder's own specified URL
 * shape (see that controller's own KDoc). An unrecognized `fromPersonId`/
 * `toPersonId` surfaces as [PersonNotFoundException], mapped to HTTP 404;
 * a blank id, unrecognized `type`, or a `fromPersonId` equal to
 * `toPersonId` surfaces as [IllegalArgumentException], mapped to HTTP 400.
 */
@RestController
@RequestMapping("/v1/connections")
class ConnectionController(
    private val createConnectionApplicationService: CreateConnectionApplicationService
) {

    @PostMapping
    fun createConnection(@RequestBody request: CreateConnectionRequest): ResponseEntity<ConnectionResponse> =
        try {
            val command = CreateConnectionCommand(
                fromPersonId = PersonId(request.fromPersonId),
                toPersonId = PersonId(request.toPersonId),
                type = ConnectionType.valueOf(request.type)
            )
            val connection = createConnectionApplicationService.handle(command)
            ResponseEntity.status(201).body(ConnectionResponse(connection.id.value, connection.type.name))
        } catch (ex: PersonNotFoundException) {
            ResponseEntity.notFound().build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
}

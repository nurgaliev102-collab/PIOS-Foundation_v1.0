package com.pios.networkmanagement.api

import com.pios.networkmanagement.application.CreatePersonApplicationService
import com.pios.networkmanagement.application.CreatePersonCommand
import com.pios.networkmanagement.application.PersonNotFoundException
import com.pios.networkmanagement.application.RetrievePersonConnectionsHandler
import com.pios.networkmanagement.application.RetrievePersonHandler
import com.pios.networkmanagement.application.RetrievePersonProfilesHandler
import com.pios.networkmanagement.domain.PersonId
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Network Management's Person REST entry points (Sprint 7A: PIOS Network
 * Foundation), following the same "no business logic beyond transport-level
 * conversion" convention as every other module's own controller (for
 * example, `com.pios.dispatch.api.ProposalController`'s own KDoc).
 *
 * `getPersonProfiles`/`getPersonConnections` live here, not on
 * `ProfileController`/`ConnectionController`, because the founder's own
 * specification names them as nested under `/v1/persons/{id}` — the same
 * literal URL shape is preserved here.
 *
 * A blank/malformed id surfaces as [PersonId]'s own `require` check,
 * mapped to HTTP 400. An unrecognized person id surfaces as
 * [PersonNotFoundException], mapped to HTTP 404.
 */
@RestController
@RequestMapping("/v1/persons")
class PersonController(
    private val createPersonApplicationService: CreatePersonApplicationService,
    private val retrievePersonHandler: RetrievePersonHandler,
    private val retrievePersonProfilesHandler: RetrievePersonProfilesHandler,
    private val retrievePersonConnectionsHandler: RetrievePersonConnectionsHandler
) {

    @PostMapping
    fun createPerson(@RequestBody request: CreatePersonRequest): ResponseEntity<PersonResponse> =
        try {
            val person = createPersonApplicationService.handle(CreatePersonCommand(request.name, request.phone))
            ResponseEntity.status(201).body(PersonResponse(person.id.value, person.name, person.phone))
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }

    @GetMapping("/{id}")
    fun getPerson(@PathVariable id: String): ResponseEntity<PersonResponse> =
        try {
            val person = retrievePersonHandler.handle(PersonId(id))
            ResponseEntity.ok(PersonResponse(person.id.value, person.name, person.phone))
        } catch (ex: PersonNotFoundException) {
            ResponseEntity.notFound().build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }

    @GetMapping("/{id}/profiles")
    fun getPersonProfiles(@PathVariable id: String): ResponseEntity<List<ProfileResponse>> =
        try {
            val personId = PersonId(id)
            ResponseEntity.ok(
                retrievePersonProfilesHandler.handle(personId)
                    .map { ProfileResponse(it.id.value, it.personId.value, it.type.name) }
            )
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }

    @GetMapping("/{id}/connections")
    fun getPersonConnections(@PathVariable id: String): ResponseEntity<List<PersonConnectionResponse>> =
        try {
            val personId = PersonId(id)
            val connections = retrievePersonConnectionsHandler.handle(personId).map { connection ->
                val toPerson = try {
                    retrievePersonHandler.handle(connection.toPersonId)
                } catch (ex: PersonNotFoundException) {
                    null
                }
                PersonConnectionResponse(person = toPerson?.name ?: connection.toPersonId.value, type = connection.type.name)
            }
            ResponseEntity.ok(connections)
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
}

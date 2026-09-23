package com.pios.networkmanagement.api

import com.pios.networkmanagement.application.CreatePersonApplicationService
import com.pios.networkmanagement.application.CreatePersonCommand
import com.pios.networkmanagement.application.PersonNotFoundException
import com.pios.networkmanagement.application.RetrievePersonConnectionsHandler
import com.pios.networkmanagement.application.RetrievePersonHandler
import com.pios.networkmanagement.application.RetrievePersonProfilesHandler
import com.pios.networkmanagement.domain.PersonId
import org.springframework.http.ResponseEntity
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
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
 * D-12 resolves the caller from the verified token subject. A supplied path
 * id is only a self-resource selector and never establishes caller identity.
 */
@RestController
@RequestMapping("/v1/persons")
class PersonController(
    private val createPersonApplicationService: CreatePersonApplicationService,
    @Suppress("unused") private val retrievePersonHandler: RetrievePersonHandler,
    private val retrievePersonProfilesHandler: RetrievePersonProfilesHandler,
    private val retrievePersonConnectionsHandler: RetrievePersonConnectionsHandler,
    private val currentPerson: CurrentPerson
) {

    @PostMapping
    fun createPerson(@RequestBody request: CreatePersonRequest): ResponseEntity<PersonResponse> =
        try {
            if (request.identityId != null) throw ResponseStatusException(HttpStatus.FORBIDDEN)
            val outcome = createPersonApplicationService.createOrGet(CreatePersonCommand(request.name, request.phone, currentPerson.identityId()))
            ResponseEntity.status(if (outcome.created) 201 else 200)
                .body(PersonResponse(outcome.person.id.value, outcome.person.name))
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }

    @GetMapping("/{id}")
    fun getPerson(@PathVariable id: String): ResponseEntity<PersonResponse> =
        try {
            val person = currentPerson.requireSelf(id)
            ResponseEntity.ok(PersonResponse(person.id.value, person.name))
        } catch (ex: PersonNotFoundException) {
            ResponseEntity.notFound().build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }

    @GetMapping("/{id}/profiles")
    fun getPersonProfiles(@PathVariable id: String): ResponseEntity<List<ProfileResponse>> =
        try {
            val personId = currentPerson.requireSelf(id).id
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
            val personId = currentPerson.requireSelf(id).id
            val connections = retrievePersonConnectionsHandler.handle(personId).map { connection ->
                val toPerson = runCatching { currentPerson.requireBoundTarget(connection.toPersonId) }.getOrNull()
                toPerson?.let { PersonConnectionResponse(person = it.name, type = connection.type.name) }
            }.filterNotNull()
            ResponseEntity.ok(connections)
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
}

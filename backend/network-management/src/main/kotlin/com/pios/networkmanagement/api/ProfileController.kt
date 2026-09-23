package com.pios.networkmanagement.api

import com.pios.networkmanagement.application.CreatePersonProfileApplicationService
import com.pios.networkmanagement.application.CreatePersonProfileCommand
import com.pios.networkmanagement.application.PersonNotFoundException
import com.pios.networkmanagement.domain.PersonId
import com.pios.networkmanagement.domain.ProfileType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Network Management's Profile REST entry point (Sprint 7A: PIOS Network
 * Foundation). D-12 derives the owner from the verified token. The optional
 * legacy `personId` is only a consistency assertion: mismatch is 403 and it
 * is never passed downstream as caller identity.
 */
@RestController
@RequestMapping("/v1/profiles")
class ProfileController(
    private val createPersonProfileApplicationService: CreatePersonProfileApplicationService,
    private val currentPerson: CurrentPerson
) {

    @PostMapping
    fun createProfile(@RequestBody request: CreateProfileRequest): ResponseEntity<ProfileResponse> =
        try {
            val caller = currentPerson.requireBound()
            currentPerson.assertCaller(request.personId)
            val command = CreatePersonProfileCommand(caller.id, ProfileType.valueOf(request.type))
            val profile = createPersonProfileApplicationService.handle(command)
            ResponseEntity.status(201).body(ProfileResponse(profile.id.value, profile.personId.value, profile.type.name))
        } catch (ex: PersonNotFoundException) {
            ResponseEntity.notFound().build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
}

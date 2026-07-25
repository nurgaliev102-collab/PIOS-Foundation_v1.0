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
 * Foundation). An unrecognized `personId` surfaces as
 * [PersonNotFoundException], mapped to HTTP 404; a blank id or
 * unrecognized `type` surfaces as [IllegalArgumentException], mapped to
 * HTTP 400.
 */
@RestController
@RequestMapping("/v1/profiles")
class ProfileController(
    private val createPersonProfileApplicationService: CreatePersonProfileApplicationService
) {

    @PostMapping
    fun createProfile(@RequestBody request: CreateProfileRequest): ResponseEntity<ProfileResponse> =
        try {
            val command = CreatePersonProfileCommand(PersonId(request.personId), ProfileType.valueOf(request.type))
            val profile = createPersonProfileApplicationService.handle(command)
            ResponseEntity.status(201).body(ProfileResponse(profile.id.value, profile.personId.value, profile.type.name))
        } catch (ex: PersonNotFoundException) {
            ResponseEntity.notFound().build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
}

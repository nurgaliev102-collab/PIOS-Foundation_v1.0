package com.pios.networkmanagement.api

import com.pios.networkmanagement.application.PersonRepository
import com.pios.networkmanagement.domain.Person
import com.pios.networkmanagement.domain.PersonId
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException

/** Single source for the token subject and its locally owned network Person. */
@Component
class CurrentPerson(private val persons: PersonRepository) {
    fun identityId(): String =
        NetworkSecurityContext.identityId() ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)

    fun boundOrNull(): Person? = persons.findByIdentityId(identityId())

    fun requireBound(): Person = boundOrNull() ?: throw ResponseStatusException(HttpStatus.FORBIDDEN)

    fun requireSelf(id: String): Person {
        val person = requireBound()
        if (person.id.value != id) throw ResponseStatusException(HttpStatus.FORBIDDEN)
        return person
    }

    fun assertCaller(id: String?) {
        if (id != null && id != requireBound().id.value) throw ResponseStatusException(HttpStatus.FORBIDDEN)
    }

    fun requireBoundTarget(id: PersonId): Person = persons.findById(id)
        ?.takeIf { it.identityId != null }
        ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
}

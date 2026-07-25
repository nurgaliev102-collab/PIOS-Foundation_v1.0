package com.pios.networkmanagement.application

import com.pios.networkmanagement.domain.Person
import com.pios.networkmanagement.domain.PersonId
import org.springframework.stereotype.Service

/**
 * Application-layer coordination for Retrieve Person (Sprint 7A: PIOS
 * Network Foundation) — read-only, mirroring
 * `com.pios.drivermanagement.application.RetrieveDriverAvailabilityHandler`'s
 * own shape.
 */
@Service
class RetrievePersonHandler(private val personRepository: PersonRepository) {
    fun handle(id: PersonId): Person = personRepository.findById(id) ?: throw PersonNotFoundException(id)
}

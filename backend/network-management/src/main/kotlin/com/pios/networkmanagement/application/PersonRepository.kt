package com.pios.networkmanagement.application

import com.pios.networkmanagement.domain.Person
import com.pios.networkmanagement.domain.PersonId

/**
 * The persistence boundary for the Person aggregate (Sprint 7A: PIOS
 * Network Foundation), following the same Domain-Owned Persistence
 * discipline as every other module's own repository interface
 * (PERSISTENCE_ARCHITECTURE.md Section 2): names only what this module's
 * domain needs, says nothing about storage technology.
 */
interface PersonRepository {
    fun save(person: Person)
    fun findById(id: PersonId): Person?
}

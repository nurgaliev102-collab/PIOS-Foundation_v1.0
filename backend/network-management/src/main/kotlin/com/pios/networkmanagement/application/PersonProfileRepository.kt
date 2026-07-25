package com.pios.networkmanagement.application

import com.pios.networkmanagement.domain.PersonId
import com.pios.networkmanagement.domain.PersonProfile

/**
 * The persistence boundary for the PersonProfile entity (Sprint 7A: PIOS
 * Network Foundation).
 */
interface PersonProfileRepository {
    fun save(profile: PersonProfile)
    fun findByPersonId(personId: PersonId): List<PersonProfile>
}

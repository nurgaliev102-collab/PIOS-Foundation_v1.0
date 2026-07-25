package com.pios.networkmanagement.application

import com.pios.networkmanagement.domain.PersonId
import com.pios.networkmanagement.domain.PersonProfile
import org.springframework.stereotype.Service

/**
 * Application-layer coordination for Retrieve Profiles for Person (Sprint
 * 7A: PIOS Network Foundation). Does not itself verify the person exists —
 * an unknown [PersonId] simply yields an empty list, since "no profiles"
 * and "no such person" are indistinguishable from this query alone and
 * this sprint's own scope does not require telling them apart.
 */
@Service
class RetrievePersonProfilesHandler(private val personProfileRepository: PersonProfileRepository) {
    fun handle(personId: PersonId): List<PersonProfile> = personProfileRepository.findByPersonId(personId)
}

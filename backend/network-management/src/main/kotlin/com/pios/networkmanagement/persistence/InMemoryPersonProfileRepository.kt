package com.pios.networkmanagement.persistence

import com.pios.networkmanagement.application.PersonProfileRepository
import com.pios.networkmanagement.domain.PersonId
import com.pios.networkmanagement.domain.PersonProfile
import java.util.concurrent.ConcurrentHashMap

/**
 * A minimal in-memory adapter for [PersonProfileRepository] — a test
 * double only, mirroring [InMemoryPersonRepository]'s own role.
 */
class InMemoryPersonProfileRepository : PersonProfileRepository {
    private val store = ConcurrentHashMap<String, PersonProfile>()

    override fun save(profile: PersonProfile) {
        store[profile.id.value] = profile
    }

    override fun findByPersonId(personId: PersonId): List<PersonProfile> =
        store.values.filter { it.personId == personId }
}

package com.pios.networkmanagement.persistence

import com.pios.networkmanagement.application.PersonRepository
import com.pios.networkmanagement.domain.Person
import com.pios.networkmanagement.domain.PersonId
import java.util.concurrent.ConcurrentHashMap

/**
 * A minimal in-memory adapter for [PersonRepository] — a fast,
 * dependency-free test double, mirroring
 * `com.pios.drivermanagement.persistence.InMemoryDriverRepository`'s own
 * role exactly. Not a production storage mechanism.
 */
class InMemoryPersonRepository : PersonRepository {
    private val store = ConcurrentHashMap<PersonId, Person>()

    override fun save(person: Person) {
        store[person.id] = person
    }

    override fun findById(id: PersonId): Person? = store[id]
}

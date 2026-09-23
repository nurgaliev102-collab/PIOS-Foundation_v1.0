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
        val existing = store[person.id]
        require(existing == null || existing.identityId == person.identityId) { "Person identity binding is immutable" }
        require(existing == null || existing.identityBoundAt == person.identityBoundAt) { "Person identity binding is immutable" }
        store[person.id] = person
    }

    override fun findById(id: PersonId): Person? = store[id]

    @Synchronized
    override fun insertBoundIfAbsent(person: Person): Boolean {
        requireNotNull(person.identityId)
        if (findByIdentityId(person.identityId) != null) return false
        save(person)
        return true
    }

    override fun findByIdentityId(identityId: String): Person? =
        store.values.firstOrNull { it.identityId == identityId }
}

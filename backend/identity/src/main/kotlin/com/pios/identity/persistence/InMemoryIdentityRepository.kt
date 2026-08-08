package com.pios.identity.persistence

import com.pios.identity.application.IdentityRepository
import com.pios.identity.domain.Identity
import com.pios.identity.domain.IdentityId
import com.pios.identity.domain.Phone
import java.util.concurrent.ConcurrentHashMap

/**
 * A minimal in-memory adapter for [IdentityRepository] — a fast,
 * dependency-free test double, mirroring
 * `com.pios.networkmanagement.persistence.InMemoryPersonRepository`'s own
 * role exactly. Not a production storage mechanism.
 */
class InMemoryIdentityRepository : IdentityRepository {
    private val store = ConcurrentHashMap<IdentityId, Identity>()

    override fun save(identity: Identity) {
        store[identity.id] = identity
    }

    override fun findById(id: IdentityId): Identity? = store[id]

    override fun findByPhone(phone: Phone): Identity? = store.values.firstOrNull { it.phone == phone }
}

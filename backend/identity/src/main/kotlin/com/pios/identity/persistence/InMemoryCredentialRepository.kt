package com.pios.identity.persistence

import com.pios.identity.application.CredentialRepository
import com.pios.identity.domain.IdentityId
import com.pios.identity.domain.PasswordCredential
import java.util.concurrent.ConcurrentHashMap

/**
 * A minimal in-memory adapter for [CredentialRepository] — a fast,
 * dependency-free test double, mirroring [InMemoryIdentityRepository]'s
 * own role exactly. Not a production storage mechanism.
 */
class InMemoryCredentialRepository : CredentialRepository {
    private val store = ConcurrentHashMap<IdentityId, PasswordCredential>()

    override fun save(credential: PasswordCredential) {
        store[credential.identityId] = credential
    }

    override fun findByIdentityId(identityId: IdentityId): PasswordCredential? = store[identityId]

    override fun replace(credential: PasswordCredential) {
        store[credential.identityId] = credential
    }
}

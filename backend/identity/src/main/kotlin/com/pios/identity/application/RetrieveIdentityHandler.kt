package com.pios.identity.application

import com.pios.identity.domain.Identity
import com.pios.identity.domain.IdentityId
import org.springframework.stereotype.Service

/**
 * Read-only lookup for [Identity] (ADR-038), mirroring
 * `com.pios.networkmanagement.application.RetrievePersonHandler`'s own
 * convention: throws [IdentityNotFoundException] for the caller to map to
 * a transport-level status, rather than returning null past this layer.
 */
@Service
class RetrieveIdentityHandler(
    private val identityRepository: IdentityRepository
) {
    fun handle(id: IdentityId): Identity = identityRepository.findById(id) ?: throw IdentityNotFoundException(id)
}

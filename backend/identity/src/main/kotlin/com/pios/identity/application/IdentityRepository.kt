package com.pios.identity.application

import com.pios.identity.domain.Identity
import com.pios.identity.domain.IdentityId

/**
 * The persistence boundary for the [Identity] aggregate (ADR-038),
 * following the same Domain-Owned Persistence discipline as every other
 * module's own repository interface (PERSISTENCE_ARCHITECTURE.md Section
 * 2): names only what this module's domain needs, says nothing about
 * storage technology.
 */
interface IdentityRepository {
    fun save(identity: Identity)
    fun findById(id: IdentityId): Identity?
}

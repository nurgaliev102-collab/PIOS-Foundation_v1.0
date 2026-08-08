package com.pios.identity.application

import com.pios.identity.domain.Identity
import com.pios.identity.domain.IdentityId
import com.pios.identity.domain.Phone

/**
 * The persistence boundary for the [Identity] aggregate (ADR-038),
 * following the same Domain-Owned Persistence discipline as every other
 * module's own repository interface (PERSISTENCE_ARCHITECTURE.md Section
 * 2): names only what this module's domain needs, says nothing about
 * storage technology.
 *
 * [findByPhone] was added by ADR-055 (Session Authentication and Password
 * Credential) to serve Register/Login's own phone-uniqueness check and
 * phone-based lookup — additive only, mirroring how ADR-054 added lookup
 * methods to `com.pios.passengerexperience.application.ConnectionRepository`
 * without changing the existing two operations' signatures.
 */
interface IdentityRepository {
    fun save(identity: Identity)
    fun findById(id: IdentityId): Identity?
    fun findByPhone(phone: Phone): Identity?
}

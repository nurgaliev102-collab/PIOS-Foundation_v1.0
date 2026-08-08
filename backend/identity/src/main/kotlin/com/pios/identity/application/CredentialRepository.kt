package com.pios.identity.application

import com.pios.identity.domain.IdentityId
import com.pios.identity.domain.PasswordCredential

/**
 * The persistence boundary for [PasswordCredential] (ADR-055 Decision 2),
 * following the same Domain-Owned Persistence discipline as
 * [IdentityRepository]. Deliberately a separate repository, not a method
 * added to [IdentityRepository] — a credential is evidence proving
 * control of an Identity, not a property of the Identity itself, the same
 * claim-versus-evidence distinction [com.pios.identity.domain.Phone]'s own
 * KDoc already draws.
 */
interface CredentialRepository {
    fun save(credential: PasswordCredential)
    fun findByIdentityId(identityId: IdentityId): PasswordCredential?
}

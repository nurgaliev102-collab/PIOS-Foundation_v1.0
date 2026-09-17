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

    /**
     * ADR-082 (D-03) — replaces an existing identity's credential, the one
     * write [save] deliberately refuses (see [save]'s own KDoc: "a second
     * `save` for the same `identity_id`... is `identity_credentials`'s own
     * primary key rejecting an unexpected write"). Used only by
     * `ConfirmRecoveryApplicationService`, after OTP proof of phone
     * ownership — the sole authorized path to change a credential once one
     * exists. Requires a credential to already exist for [PasswordCredential.identityId];
     * callers must have already verified the identity is registered.
     */
    fun replace(credential: PasswordCredential)
}

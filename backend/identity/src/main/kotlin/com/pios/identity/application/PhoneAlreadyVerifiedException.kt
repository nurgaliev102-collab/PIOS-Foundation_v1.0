package com.pios.identity.application

import com.pios.identity.domain.IdentityId

/**
 * Thrown by [RequestPhoneVerificationApplicationService] when the caller's
 * own phone is already verified (ADR-082 Part 2) — mapped by
 * [com.pios.identity.api.IdentityController] to 409, mirroring
 * [PhoneAlreadyRegisteredException]'s own convention.
 */
class PhoneAlreadyVerifiedException(identityId: IdentityId) : RuntimeException("Identity ${identityId.value} phone is already verified")

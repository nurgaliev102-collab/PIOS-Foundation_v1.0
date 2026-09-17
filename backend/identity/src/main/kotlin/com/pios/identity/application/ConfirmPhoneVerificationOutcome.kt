package com.pios.identity.application

import com.pios.identity.domain.Identity

sealed interface ConfirmPhoneVerificationOutcome {
    data class Success(val identity: Identity) : ConfirmPhoneVerificationOutcome
    object Failure : ConfirmPhoneVerificationOutcome
}

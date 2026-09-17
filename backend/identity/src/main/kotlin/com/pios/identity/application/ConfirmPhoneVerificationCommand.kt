package com.pios.identity.application

data class ConfirmPhoneVerificationCommand(val identityId: String, val code: String, val presentedGeneration: Int = 0)

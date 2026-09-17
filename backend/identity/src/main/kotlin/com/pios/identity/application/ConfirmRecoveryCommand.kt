package com.pios.identity.application

/**
 * ADR-082 (D-03.6 items 3/4): deliberately carries no `driverId` field at
 * all — recovery can never let the requester choose or influence which
 * driver an identity is associated with, and the absence of the field
 * from this type is the compile-time half of that guarantee (the runtime
 * half is [ConfirmRecoveryApplicationService] never reading `driverId`
 * from anywhere but the already-loaded [com.pios.identity.domain.Identity]).
 */
data class ConfirmRecoveryCommand(val phone: String, val code: String, val newPassword: String)
